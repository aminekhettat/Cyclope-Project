package org.blindsystems.cyclope.visionlab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import org.blindsystems.cyclope.vision.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Create, invoke, and close on ONE analysis thread: GPU delegates require thread affinity. */
class YoloEngine(context: Context, size: ModelSize, requested: DelegateChoice) : AutoCloseable {
    private var delegate: Delegate? = null
    private val interpreter: Interpreter
    val status: String
    private val inputShape: IntArray
    private val nhwc: Boolean
    private val inputWidth: Int
    private val inputHeight: Int
    private val outputShape: IntArray
    private val input: ByteBuffer
    private val output: ByteBuffer
    private val pixels: IntArray
    private val modelBitmap: Bitmap
    private val parser = YoloParser()

    init {
        // noCompress in Gradle lets us map weights without a second full copy in Java memory.
        val model =
            context.assets.openFd(size.assetName).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { stream ->
                    stream.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
                }
            }
        val gpu = runCatching { CompatibilityList().use { it.isDelegateSupportedOnThisDevice } }.getOrDefault(false)
        val selection = selectDelegate(requested, gpu, android.os.Build.VERSION.SDK_INT >= 27)
        var explanation = selection.explanation

        fun create(choice: DelegateChoice): Interpreter {
            val options = Interpreter.Options().setNumThreads(2)
            delegate =
                when (choice) {
                    DelegateChoice.CPU -> null
                    DelegateChoice.GPU -> GpuDelegate()
                    DelegateChoice.NNAPI -> NnApiDelegate()
                }
            delegate?.let(options::addDelegate)
            return Interpreter(model, options)
        }
        interpreter =
            try {
                create(selection.actual)
            } catch (e: Exception) {
                delegate?.close()
                delegate = null
                if (selection.actual == DelegateChoice.CPU) throw e
                explanation = "${selection.actual} initialization failed; using CPU: ${e.message}"
                create(DelegateChoice.CPU)
            }
        try {
            inputShape = interpreter.getInputTensor(0).shape()
            require(inputShape.size == 4 && inputShape[0] == 1) { "Expected one RGB image" }
            nhwc = inputShape[3] == 3
            require(nhwc || inputShape[1] == 3) { "Expected NHWC or NCHW RGB input" }
            inputHeight = inputShape[if (nhwc) 1 else 2]
            inputWidth = inputShape[if (nhwc) 2 else 3]
            require(inputWidth in 32..1280 && inputHeight in 32..1280) { "Use a static 640px export" }
            require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1) { "Use COCO detection, not depth or segmentation" }
            require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32 && interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32) {
                "Use float16 weights with float32 image/output tensors; INT8 is not supported in this lesson"
            }
            outputShape = interpreter.getOutputTensor(0).shape()
            // Validate the output contract once before the camera starts sending frames.
            parser.parse(FloatArray(interpreter.getOutputTensor(0).numElements()), outputShape)
            input = ByteBuffer.allocateDirect(interpreter.getInputTensor(0).numBytes()).order(ByteOrder.nativeOrder())
            output = ByteBuffer.allocateDirect(interpreter.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder())
            pixels = IntArray(inputWidth * inputHeight)
            modelBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
            status = "${size.name}; $explanation; input ${inputWidth}x$inputHeight; ${if (nhwc) "NHWC" else "NCHW"}"
        } catch (e: Exception) {
            interpreter.close()
            delegate?.close()
            throw e
        }
    }

    data class Result(val detections: List<Detection>, val inferenceMs: Float, val pipelineMs: Float)

    fun detect(source: Bitmap): Result {
        val start = SystemClock.elapsedRealtimeNanos()
        val letterbox = Letterbox(source.width, source.height, inputWidth, inputHeight)
        val canvas = Canvas(modelBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(source, null, Rect(letterbox.left, letterbox.top, letterbox.left + letterbox.resizedWidth, letterbox.top + letterbox.resizedHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        modelBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        input.rewind()
        if (nhwc) {
            for (p in pixels) {
                input.putFloat((p shr 16 and 255) / 255f)
                input.putFloat((p shr 8 and 255) / 255f)
                input.putFloat((p and 255) / 255f)
            }
        } else {
            for (shift in listOf(16, 8, 0)) for (p in pixels) input.putFloat((p shr shift and 255) / 255f)
        }
        input.rewind()
        output.rewind()
        val inferenceStart = SystemClock.elapsedRealtimeNanos()
        interpreter.run(input, output)
        val inferenceEnd = SystemClock.elapsedRealtimeNanos()
        output.rewind()
        val raw = FloatArray(output.capacity() / 4)
        output.asFloatBuffer().get(raw)
        // Current litert-torch NCHW end-to-end exports use PIXEL xyxy. Legacy NHWC uses NORMALIZED xyxy.
        if (!nhwc && outputShape[2] == 6) normalizePixelRows(raw, inputWidth, inputHeight)
        val detections = parser.parse(raw, outputShape).map { it.copy(box = letterbox.toSource(it.box)) }.filter { it.box.area > 0 }
        return Result(detections, (inferenceEnd - inferenceStart) / 1e6f, (SystemClock.elapsedRealtimeNanos() - start) / 1e6f)
    }

    override fun close() {
        interpreter.close()
        delegate?.close()
        modelBitmap.recycle()
    }
}
