package org.blindsystems.cyclope.visionlab

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.blindsystems.cyclope.vision.*
import java.util.Locale
import java.util.concurrent.Executors

/** A small learning screen. Camera/Android plumbing stays outside the pure :vision module. */
class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var preview: PreviewView
    private lateinit var overlay: DetectionOverlay
    private lateinit var status: TextView
    private lateinit var metrics: TextView
    private lateinit var summary: TextView
    private lateinit var startButton: Button
    private lateinit var speech: AndroidSpeech
    private var provider: ProcessCameraProvider? = null
    private var engine: YoloEngine? = null // Access only on worker.

    @Volatile private var selected = setOf(0, 1, 2, 9, 11)
    private var modelSize = ModelSize.SMALL
    private var delegateChoice = DelegateChoice.CPU

    @Volatile private var generation = 0

    @Volatile private var running = false
    private var lastUpdateMs = 0L
    private var fpsStartMs = 0L
    private var frameCount = 0
    private var measuredFps = 0f
    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else setStatus("Camera permission denied. Tap Start camera to retry, or allow Camera in Android Settings. Device info and class selection still work.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = getPreferences(MODE_PRIVATE)
        modelSize = runCatching { ModelSize.valueOf(saved.getString("size", "SMALL")!!) }.getOrDefault(ModelSize.SMALL)
        delegateChoice = runCatching { DelegateChoice.valueOf(saved.getString("delegate", "CPU")!!) }.getOrDefault(DelegateChoice.CPU)
        selected = saved.getStringSet("classes", null)?.mapNotNull(String::toIntOrNull)?.filter { it in 0..79 }?.toSet() ?: selected
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(24))
            }
        scroll.addView(column)
        val title = text("Cyclope VisionLab — learn object detection", 24f)
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true)
        column.addView(title)
        column.addView(text("Phone camera only. Small model is the starting point. This is a learning tool, not a navigation or safety aid."))
        column.addView(button("Device info") { deviceInfo() })
        column.addView(text("Model size"))
        column.addView(
            selector(ModelSize.entries.map { it.name.lowercase() }, modelSize.ordinal, "Model size. Small is the default") { index ->
                val next = ModelSize.entries[index]
                if (next != modelSize) {
                    modelSize = next
                    saveSettings()
                    reloadIfRunning()
                }
            },
        )
        column.addView(text("Inference delegate"))
        column.addView(
            selector(DelegateChoice.entries.map { it.name }, delegateChoice.ordinal, "Inference delegate. CPU, GPU or NNAPI") { index ->
                val next = DelegateChoice.entries[index]
                if (next != delegateChoice) {
                    delegateChoice = next
                    saveSettings()
                    reloadIfRunning()
                }
            },
        )
        column.addView(button("Choose object classes") { chooseClasses() })
        val mute = button("Mute speech") { }
        mute.setOnClickListener {
            speech.muted = !speech.muted
            mute.text = if (speech.muted) "Unmute speech" else "Mute speech"
            mute.contentDescription = mute.text
            saveSettings()
            setStatus(if (speech.muted) "Speech muted. Text detections remain available." else "Speech enabled. When TalkBack is active, read the text summary instead.")
        }
        column.addView(mute)
        startButton =
            button("Start camera") {
                if (running) {
                    stopCamera()
                } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    permission.launch(Manifest.permission.CAMERA)
                }
            }
        column.addView(startButton)
        status = text("Camera stopped. Select classes, then start the camera.").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        column.addView(status)
        val speechStatus = text("Speech: initializing. TalkBack has priority.")
        column.addView(speechStatus)
        metrics = text("Performance: no measurements yet. FPS counts processed frames, not camera preview frames.")
        column.addView(metrics)
        summary = text("Detections: none. The summary is the text equivalent of the boxes.").apply { isFocusable = true }
        column.addView(summary)
        column.addView(button("Read current detections") { summary.announceForAccessibility(summary.text) })
        val frame = FrameLayout(this)
        preview =
            PreviewView(this).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        overlay = DetectionOverlay(this)
        frame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        column.addView(frame, LinearLayout.LayoutParams(-1, dp(300)))
        column.addView(text("Depth — NOT IMPLEMENTED. Monocular depth is experimental. This app does not measure distance. See README: depth evaluation."))
        setContentView(scroll)
        // Android 15 enforces edge-to-edge. Keep controls clear of system bars and cutouts.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        speech = AndroidSpeech(this) { message -> if (!isDestroyed) speechStatus.text = message }
        speech.muted = saved.getBoolean("muted", false)
        mute.text = if (speech.muted) "Unmute speech" else "Mute speech"
        mute.contentDescription = mute.text
        showModelAvailability()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(
        value: String,
        size: Float = 18f,
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(android.graphics.Color.BLACK)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun button(
        label: String,
        action: () -> Unit,
    ) = Button(this).apply {
        text = label
        contentDescription = label
        minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun selector(
        items: List<String>,
        selectedIndex: Int,
        label: String,
        changed: (Int) -> Unit,
    ) =
        Spinner(this).apply {
            contentDescription = label
            minimumHeight = dp(48)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(selectedIndex)
            onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        changed(position)
                    }
                }
        }

    private fun setStatus(message: String) {
        status.text = message
    }

    private fun clearDetections() {
        overlay.detections = emptyList()
        overlay.invalidate()
        summary.text = "Detections: none. ${selected.size} of 80 classes selected."
        speech.stop()
    }

    private fun deviceInfo() {
        val detail =
            TextView(this).apply {
                text = DeviceInfo.describe(this@MainActivity)
                textSize = 18f
                setTextColor(android.graphics.Color.BLACK)
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
        AlertDialog.Builder(this).setTitle("Device info — capabilities, not promises").setView(ScrollView(this).apply { addView(detail) }).setPositiveButton("Close", null).show()
    }

    private fun chooseClasses() {
        val draft = selected.toMutableSet()
        val labels = CocoLabels.english.toTypedArray()
        val checked = BooleanArray(80) { it in draft }
        AlertDialog.Builder(this).setTitle("Classes to display AND announce")
            .setMultiChoiceItems(labels, checked) { _, which, enabled -> if (enabled) draft.add(which) else draft.remove(which) }
            .setPositiveButton("Apply") { _, _ ->
                selected = draft.toSet()
                clearDetections()
                saveSettings()
                setStatus("${selected.size} classes selected. ${if (selected.isEmpty()) "No objects will be displayed or spoken." else "Only these classes are displayed and spoken."}")
            }
            .setNeutralButton("Select none") { _, _ ->
                selected = emptySet()
                clearDetections()
                saveSettings()
                setStatus("No classes selected. No boxes or speech.")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun saveSettings() {
        getPreferences(MODE_PRIVATE).edit().putString("size", modelSize.name).putString("delegate", delegateChoice.name).putStringSet("classes", selected.map(Int::toString).toSet()).apply {
            if (::speech.isInitialized) putBoolean("muted", speech.muted)
        }.apply()
    }

    private fun showModelAvailability(): Boolean {
        val exists = assets.list("")?.contains(modelSize.assetName) == true
        if (!exists) setStatus("Model missing: ${modelSize.assetName}. Camera can preview, but detection cannot start. Export the COCO YOLO26 float16 model on a Linux x86 computer or Colab and place it in app/src/main/assets. See README, Model setup. No download or cloud inference occurs in this app.")
        return exists
    }

    private fun reloadIfRunning() {
        if (running) {
            stopCamera()
            startCamera()
        } else {
            showModelAvailability()
        }
    }

    private fun startCamera() {
        if (running) return
        running = true
        val token = ++generation
        startButton.text = "Stop camera"
        startButton.contentDescription = startButton.text
        clearDetections()
        fpsStartMs = SystemClock.elapsedRealtime()
        frameCount = 0
        measuredFps = 0f
        lastUpdateMs = 0
        metrics.text = "Performance: waiting for the first processed frame."
        val available = showModelAvailability()
        if (available) setStatus("Loading ${modelSize.name.lowercase()} model on ${delegateChoice.name}…")
        val chosenSize = modelSize
        val chosenDelegate = delegateChoice
        worker.execute {
            engine?.close()
            engine = null
            if (available) {
                try {
                    engine = YoloEngine(this, chosenSize, chosenDelegate)
                    val message = engine!!.status
                    runOnUiThread { if (generation == token && running) setStatus(message) }
                } catch (e: Exception) {
                    runOnUiThread { if (generation == token && running) setStatus("Model could not load: ${e.message}. Camera preview only. Check README export contract or select CPU.") }
                }
            }
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!running || generation != token) return@addListener
            try {
                provider = future.get()
                preview.post {
                    if (!running || generation != token) return@post
                    try {
                        val cameraPreview = Preview.Builder().setTargetRotation(preview.display.rotation).build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                        val analysis =
                            ImageAnalysis.Builder().setTargetRotation(preview.display.rotation)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                        analysis.setAnalyzer(worker) { image -> analyze(image, token) }
                        val group = UseCaseGroup.Builder().addUseCase(cameraPreview).addUseCase(analysis)
                        preview.viewPort?.let(group::setViewPort)
                        provider!!.unbindAll()
                        provider!!.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                    } catch (e: Exception) {
                        stopCamera()
                        setStatus("Camera unavailable: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                stopCamera()
                setStatus("Camera service unavailable: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(
        image: ImageProxy,
        token: Int,
    ) {
        var bitmap: Bitmap? = null
        try {
            val detector = engine ?: return
            if (!running || generation != token) return
            val raw = image.toBitmap()
            val crop = image.cropRect
            val cropped = Bitmap.createBitmap(raw, crop.left, crop.top, crop.width(), crop.height())
            if (raw !== cropped) raw.recycle()
            val rotation = image.imageInfo.rotationDegrees
            bitmap = if (rotation == 0) cropped else Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, Matrix().apply { postRotate(rotation.toFloat()) }, true).also { if (it !== cropped) cropped.recycle() }
            val result = detector.detect(bitmap)
            val w = bitmap.width
            val h = bitmap.height
            val now = SystemClock.elapsedRealtime()
            frameCount++
            if (now - fpsStartMs >= 1000) {
                measuredFps = frameCount * 1000f / (now - fpsStartMs)
                frameCount = 0
                fpsStartMs = now
            }
            val fps = measuredFps
            runOnUiThread {
                if (generation != token || !running) return@runOnUiThread
                val visible = filterClasses(result.detections, selected)
                overlay.sourceWidth = w
                overlay.sourceHeight = h
                overlay.detections = visible
                overlay.invalidate()
                speech.offer(visible)
                if (now - lastUpdateMs >= 1500) {
                    lastUpdateMs = now
                    metrics.text = String.format(Locale.US, "Processed FPS %.1f; inference %.1f ms; pipeline %.1f ms. Pipeline excludes camera conversion and drawing.", fps, result.inferenceMs, result.pipelineMs)
                    summary.text = if (visible.isEmpty()) "Detections: none among ${selected.size} selected classes." else "Detections: " + visible.take(12).joinToString { "${CocoLabels.english[it.classId]} ${(it.confidence * 100).toInt()} percent" } + if (visible.size > 12) "; ${visible.size - 12} more" else ""
                }
            }
        } catch (e: Exception) {
            // A delegate can fail on invoke, not only on construction. Stop once; do not loop failures.
            engine?.close()
            engine = null
            runOnUiThread {
                if (generation == token) {
                    clearDetections()
                    setStatus("Inference stopped: ${e.message}. Select CPU or check the export contract; camera preview remains available.")
                }
            }
        } finally {
            bitmap?.recycle()
            image.close()
        }
    }

    private fun stopCamera() {
        running = false
        generation++
        provider?.unbindAll()
        worker.execute {
            engine?.close()
            engine = null
        }
        clearDetections()
        startButton.text = "Start camera"
        startButton.contentDescription = startButton.text
        metrics.text = "Performance: camera stopped."
        setStatus("Camera stopped. No images are stored or transmitted.")
    }

    override fun onStop() {
        stopCamera()
        super.onStop()
    }

    override fun onDestroy() {
        speech.close()
        worker.shutdown() // Already queued close runs before the executor terminates.
        super.onDestroy()
    }
}
