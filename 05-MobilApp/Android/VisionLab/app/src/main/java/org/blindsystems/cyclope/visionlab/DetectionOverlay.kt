package org.blindsystems.cyclope.visionlab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import org.blindsystems.cyclope.vision.CocoLabels
import org.blindsystems.cyclope.vision.Detection
import org.blindsystems.cyclope.vision.mapToScreen
import org.blindsystems.cyclope.voicekit.android.VoiceAccessibility

/** Visual output has a text equivalent in MainActivity. It never captures accessibility focus. */
class DetectionOverlay(context: Context) : View(context) {
    private val line =
        Paint().apply {
            color = Color.YELLOW
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f * resources.displayMetrics.scaledDensity
        }
    private val background = Paint().apply { color = Color.BLACK }
    var detections: List<Detection> = emptyList()
    var sourceWidth: Int = 1
    var sourceHeight: Int = 1

    init {
        VoiceAccessibility.excludeFromTraversal(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        for (d in detections) {
            val b = mapToScreen(d.box, sourceWidth, sourceHeight, width, height)
            canvas.drawRect(b.left, b.top, b.right, b.bottom, line)
            val label = "${CocoLabels.english[d.classId]} ${(d.confidence * 100).toInt()}%"
            val y = b.top.coerceAtLeast(text.textSize)
            canvas.drawRect(b.left, y - text.textSize, (b.left + text.measureText(label) + 8).coerceAtMost(width.toFloat()), y + 4, background)
            canvas.drawText(label, b.left + 4, y, text)
        }
    }
}
