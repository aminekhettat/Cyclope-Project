package org.blindsystems.cyclope.vision

import kotlin.math.min
import kotlin.math.roundToInt

/** Same rounded letterbox dimensions are used by Android preprocessing and inverse mapping. */
class Letterbox(val sourceWidth: Int, val sourceHeight: Int, val inputWidth: Int, val inputHeight: Int) {
    init {
        require(listOf(sourceWidth, sourceHeight, inputWidth, inputHeight).all { it > 0 })
    }

    private val ratio = min(inputWidth.toFloat() / sourceWidth, inputHeight.toFloat() / sourceHeight)
    val resizedWidth = (sourceWidth * ratio).roundToInt().coerceIn(1, inputWidth)
    val resizedHeight = (sourceHeight * ratio).roundToInt().coerceIn(1, inputHeight)
    val left = (inputWidth - resizedWidth) / 2
    val top = (inputHeight - resizedHeight) / 2

    fun toSource(b: Box): Box =
        Box(
            ((b.left * inputWidth - left) / resizedWidth).coerceIn(0f, 1f),
            ((b.top * inputHeight - top) / resizedHeight).coerceIn(0f, 1f),
            ((b.right * inputWidth - left) / resizedWidth).coerceIn(0f, 1f),
            ((b.bottom * inputHeight - top) / resizedHeight).coerceIn(0f, 1f),
        )
}

/** FIT_CENTER matches PreviewView. Back camera only: there is no mirror transform. */
fun mapToScreen(
    b: Box,
    sourceWidth: Int,
    sourceHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
): Box {
    require(listOf(sourceWidth, sourceHeight, viewWidth, viewHeight).all { it > 0 })
    val scale = min(viewWidth.toFloat() / sourceWidth, viewHeight.toFloat() / sourceHeight)
    val w = sourceWidth * scale
    val h = sourceHeight * scale
    val x = (viewWidth - w) / 2
    val y = (viewHeight - h) / 2
    return Box(x + b.left * w, y + b.top * h, x + b.right * w, y + b.bottom * h)
}
