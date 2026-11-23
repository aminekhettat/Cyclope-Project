package org.blindsystems.cyclope.navlab

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ScrollView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.blindsystems.cyclope.voicekit.android.VoiceAccessibility
import org.blindsystems.cyclope.navigation.core.AddressSelector
import org.blindsystems.cyclope.navigation.core.ButtonEvent
import org.blindsystems.cyclope.navigation.core.GeoPoint
import org.blindsystems.cyclope.navigation.core.GuidanceEvent
import org.blindsystems.cyclope.navigation.core.NominatimClient
import org.blindsystems.cyclope.navigation.core.OsrmClient
import org.blindsystems.cyclope.navigation.core.Place
import org.blindsystems.cyclope.navigation.core.Priority
import org.blindsystems.cyclope.navigation.core.RouteFollower
import org.blindsystems.cyclope.navigation.core.RouteSimulator
import org.blindsystems.cyclope.navigation.positioning.PositionRecorder
import org.blindsystems.cyclope.navigation.positioning.PositionRecorder.Source
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Test harness for the navigation block (not the final UI: the final app is used by blind people
 * through the glasses buttons + voice). Two parts:
 *  1. Positioning study: live view + CSV log of the 5 position sources.
 *  2. Navigation demo: typed address (stand-in for speech-to-text) -> Nominatim -> choice with
 *     simulated glasses buttons -> OSRM foot route -> guidance (live GPS or simulated walk).
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMS = 42

        /** Nominatim & OSRM usage policies require an identifying User-Agent. */
        const val USER_AGENT =
            "CyclopeGEN3-NavLab/0.1 (Polytech Paris-Saclay student project; contact@blindsystems.org)"

        /** Simulated walk: one position every second, 6 m apart (about 4x walking speed). */
        private const val SIM_STEP_M = 6.0
        private const val SIM_PERIOD_MS = 1000L
    }

    private lateinit var recorder: PositionRecorder
    private lateinit var voice: AndroidVoiceOutput
    private val main = Handler(Looper.getMainLooper())
    private val nominatim = NominatimClient(USER_AGENT)
    private val osrm = OsrmClient(USER_AGENT)

    private lateinit var tvSources: TextView
    private lateinit var tvNav: TextView
    private lateinit var etAddress: EditText
    private lateinit var btnLog: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvAnnouncements: TextView

    private var selector: AddressSelector? = null
    private var destination: Place? = null
    private var follower: RouteFollower? = null
    private var rerouting = false
    private var simulating = false
    private var simPoints: List<GeoPoint> = emptyList()
    private var simIndex = 0
    private var markCount = 0
    private val navLog = ArrayDeque<String>()
    private var latestGuidance = "No guidance announcement yet."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // logging needs the app in foreground

        recorder = PositionRecorder(this)
        recorder.addListener { sample -> onSample(sample) }

        tvSources = findViewById(R.id.tvSources)
        tvNav = findViewById(R.id.tvNav)
        etAddress = findViewById(R.id.etAddress)
        btnLog = findViewById(R.id.btnLog)
        tvStatus = findViewById(R.id.tvStatus)
        tvAnnouncements = findViewById(R.id.tvAnnouncements)
        voice = AndroidVoiceOutput(
            this,
            announceAccessibility = { VoiceAccessibility.publishPolite(tvAnnouncements, it) },
            onSpoken = { appendNav(it) },
            onStatus = { VoiceAccessibility.publishPolite(tvStatus, it) },
        )
        VoiceAccessibility.heading(findViewById(R.id.headingPositioning))
        VoiceAccessibility.heading(findViewById(R.id.headingNavigation))
        findViewById<Button>(R.id.btnSkipNavigation).setOnClickListener {
            etAddress.requestFocus()
            etAddress.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            findViewById<ScrollView>(R.id.mainScroll).smoothScrollTo(0, findViewById<android.view.View>(R.id.headingNavigation).top)
        }
        findViewById<Button>(R.id.btnMute).setOnClickListener {
            voice.muted = !voice.muted
            val button = it as Button
            button.setText(if (voice.muted) R.string.unmute_voice else R.string.mute_voice)
            VoiceAccessibility.label(button, button.text)
            VoiceAccessibility.announceRequested(button, button.text)
        }
        findViewById<Button>(R.id.btnReadGuidance).setOnClickListener {
            VoiceAccessibility.announceRequested(tvAnnouncements, latestGuidance)
        }

        btnLog.setOnClickListener { toggleLogging() }
        findViewById<Button>(R.id.btnMark).setOnClickListener {
            markCount++
            recorder.mark("M$markCount")
            toast("Mark M$markCount saved")
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareCsv() }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { searchAddress() }
        findViewById<Button>(R.id.btnShort).setOnClickListener { onGlassesButton(ButtonEvent.SHORT_PRESS) }
        findViewById<Button>(R.id.btnLong).setOnClickListener { onGlassesButton(ButtonEvent.LONG_PRESS) }
        findViewById<Button>(R.id.btnDouble).setOnClickListener { onGlassesButton(ButtonEvent.DOUBLE_PRESS) }
        findViewById<Button>(R.id.btnSimulate).setOnClickListener { startSimulation() }
        findViewById<Button>(R.id.btnStopNav).setOnClickListener { stopNavigation() }
    }

    override fun onStart() {
        super.onStart()
        if (hasLocationPermission()) recorder.start() else requestPerms()
        main.post(uiTick)
    }

    override fun onStop() {
        super.onStop()
        main.removeCallbacks(uiTick)
        voice.stop() // Do not keep guidance speech running over another app.
        if (!recorder.isLogging) recorder.stop()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        recorder.stop()
        voice.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ permissions

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestPerms() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms += Manifest.permission.ACTIVITY_RECOGNITION
        requestPermissions(perms.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        if (hasLocationPermission()) recorder.start() else toast("Precise location permission is required")
    }

    // ------------------------------------------------------------------ 1. positioning study

    private fun toggleLogging() {
        if (recorder.isLogging) {
            recorder.stopCsv()
            btnLog.setText(R.string.start_logging)
            VoiceAccessibility.label(btnLog, getString(R.string.start_logging_description))
            toast("Saved ${recorder.csvFile?.name}")
            return
        }
        if (!hasLocationPermission()) {
            requestPerms()
            return
        }
        recorder.start()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val model = Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "")
        val dir = getExternalFilesDir(null) ?: filesDir
        recorder.startCsv(File(dir, "poslog_${stamp}_$model.csv"))
        markCount = 0
        btnLog.setText(R.string.stop_logging)
        VoiceAccessibility.label(btnLog, getString(R.string.stop_logging_description))
    }

    private fun shareCsv() {
        val file = recorder.csvFile ?: run { toast("No CSV yet: start logging first"); return }
        recorder.flush()
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share ${file.name}"))
    }

    private val uiTick = object : Runnable {
        override fun run() {
            refreshSources()
            main.postDelayed(this, 1000L)
        }
    }

    private fun fmt(v: Double?, digits: Int = 1) =
        if (v == null || v.isNaN()) "-" else String.format(Locale.US, "%.${digits}f", v)

    private fun refreshSources() {
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        for (src in listOf(Source.GNSS, Source.NETWORK, Source.FUSED, Source.GNSS_STATUS, Source.PDR)) {
            val s = recorder.latest[src]
            sb.append(String.format(Locale.US, "%-12s", src.name))
            if (s == null) {
                sb.append("no data yet\n")
                continue
            }
            when (src) {
                Source.GNSS_STATUS -> sb.append("sats ${s.satsUsed}/${s.satsVisible}  C/N0 ${fmt(s.meanCn0DbHz)} dB-Hz")
                Source.PDR -> sb.append("±${fmt(s.accuracyM)} m  heading ${fmt(s.headingDeg, 0)}°  steps ${s.steps}")
                else -> sb.append("±${fmt(s.accuracyM)} m  ${fmt(s.lat, 6)}, ${fmt(s.lon, 6)}")
            }
            sb.append("  (${(now - s.timeMs) / 1000}s ago)\n")
        }
        sb.append("Providers on this phone: ${recorder.availableProviders.joinToString()}\n")
        sb.append("Navigation uses: ${navSource().name}\n")
        if (recorder.isLogging) sb.append("● Logging to ${recorder.csvFile?.name}\n")
        follower?.state?.let { st ->
            sb.append("Route: ${st.remainingM.toInt()} m left, next manoeuvre in ${st.distanceToNextStepM.toInt()} m, ")
            sb.append("${fmt(st.crossTrackM)} m from route${if (st.offRoute) ", OFF ROUTE" else ""}")
            if (simulating) sb.append(" [SIMULATION]")
        }
        tvSources.text = sb
    }

    // ------------------------------------------------------------------ 2. navigation demo

    /** FUSED if the phone has it (Android 12+), else pure GNSS. To be confirmed by the positioning study. */
    private fun navSource(): Source =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && "fused" in recorder.availableProviders) Source.FUSED
        else Source.GNSS

    private fun bestPosition(): PositionRecorder.Sample? {
        val now = System.currentTimeMillis()
        return listOf(navSource(), Source.GNSS, Source.FUSED, Source.NETWORK)
            .mapNotNull { recorder.latest[it] }
            .firstOrNull { it.point != null && now - it.timeMs < 30_000 }
    }

    private fun onSample(s: PositionRecorder.Sample) {
        if (simulating || s.source != navSource()) return
        val f = follower ?: return
        val p = s.point ?: return
        handleGuidance(f.update(p, s.accuracyM ?: 99.0), p)
    }

    private fun handleGuidance(events: List<GuidanceEvent>, current: GeoPoint) {
        for (e in events) {
            voice.speak(e.message, e.priority)
            when (e) {
                is GuidanceEvent.OffRoute -> reroute(current)
                is GuidanceEvent.Arrived -> {
                    follower = null
                    simulating = false
                }
                else -> Unit
            }
        }
    }

    /** Typed text = stand-in for the speech-to-text block. */
    private fun searchAddress() {
        val text = etAddress.text.toString().trim()
        if (text.isEmpty()) {
            toast("Type an address (stand-in for speech-to-text)")
            return
        }
        // A new search replaces the current trip: stop the guidance / simulated walk of the old route.
        if (follower != null || simulating) stopNavigation()
        val near = bestPosition()?.point
        appendNav("> Nominatim search: \"$text\"${if (near == null) " (user position unknown)" else ""}")
        thread {
            try {
                val places = nominatim.search(text, near)
                runOnUiThread {
                    val sel = AddressSelector(places)
                    selector = sel
                    handleSelection(sel.start())
                }
            } catch (e: Exception) {
                runOnUiThread { appendNav("! Nominatim error: ${e.message}") }
            }
        }
    }

    private fun onGlassesButton(e: ButtonEvent) {
        val sel = selector
        if (sel != null && !sel.finished) {
            handleSelection(sel.onButton(e))
            return
        }
        if (e == ButtonEvent.LONG_PRESS) {
            appendNav("(Long press outside a choice = start dictation -> speech-to-text block. Here: type the address.)")
            etAddress.requestFocus()
        } else {
            appendNav("(No address list to browse: search an address first.)")
        }
    }

    private fun handleSelection(r: AddressSelector.Result) {
        when (r) {
            is AddressSelector.Result.Speak -> voice.speak(r.message, Priority.INFO)
            is AddressSelector.Result.NoneMatches -> {
                voice.speak(r.message, Priority.INFO)
                selector = null
            }
            is AddressSelector.Result.Selected -> {
                voice.speak(r.message, Priority.INFO)
                selector = null
                destination = r.place
                computeRoute()
            }
        }
    }

    private fun computeRoute() {
        val dest = destination ?: return
        val from = bestPosition()?.point
        if (from == null) {
            appendNav("! No position yet: wait for a GPS / Wi-Fi fix (go near a window), then long-press again.")
            return
        }
        appendNav("> OSRM foot route request")
        thread {
            try {
                val route = osrm.route(from, dest.point)
                runOnUiThread {
                    val f = RouteFollower(route)
                    follower = f
                    appendNav("  route: ${route.steps.size} steps, ${route.distanceM.toInt()} m. Walk, or press \"Simulate walk\".")
                    f.start().forEach { voice.speak(it.message, it.priority) }
                }
            } catch (e: Exception) {
                runOnUiThread { appendNav("! OSRM error: ${e.message}") }
            }
        }
    }

    private fun reroute(from: GeoPoint) {
        val dest = destination ?: return
        if (rerouting || simulating) return
        rerouting = true
        thread {
            try {
                val route = osrm.route(from, dest.point)
                runOnUiThread {
                    val f = RouteFollower(route)
                    follower = f
                    f.start().forEach { voice.speak(it.message, it.priority) }
                }
            } catch (e: Exception) {
                runOnUiThread { appendNav("! Reroute error: ${e.message}") }
            } finally {
                runOnUiThread { rerouting = false }
            }
        }
    }

    private fun startSimulation() {
        val f = follower ?: run { toast("Compute a route first (search, then Long press to choose)"); return }
        simPoints = RouteSimulator.walk(f.route.geometry, SIM_STEP_M)
        simIndex = 0
        simulating = true
        val minutes = simPoints.size / 60
        appendNav("> Simulated walk: ${simPoints.size} positions, 1 per second (about $minutes min)")
        if (minutes > 3) appendNav("  Tip: for a short demo choose a destination less than 1 km away.")
        main.removeCallbacks(simTick)
        main.post(simTick)
    }

    private val simTick = object : Runnable {
        override fun run() {
            val f = follower
            if (!simulating || f == null || simIndex >= simPoints.size) {
                simulating = false
                return
            }
            val p = simPoints[simIndex++]
            handleGuidance(f.update(p, 5.0), p)
            if (simulating) main.postDelayed(this, SIM_PERIOD_MS)
        }
    }

    private fun stopNavigation() {
        simulating = false
        main.removeCallbacks(simTick)
        follower = null
        selector = null
        destination = null
        voice.stop()
        appendNav("> Navigation stopped")
    }

    // ------------------------------------------------------------------ helpers

    private fun appendNav(line: String) {
        if (line.startsWith("[")) latestGuidance = line.substringAfter("] ")
        navLog.addLast(line)
        while (navLog.size > 60) navLog.removeFirst()
        tvNav.text = navLog.joinToString("\n")
        // The full log remains manually readable, not a 60-line live-region announcement.
        if (line.startsWith(">") || line.startsWith("!")) VoiceAccessibility.publishPolite(tvStatus, line)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
