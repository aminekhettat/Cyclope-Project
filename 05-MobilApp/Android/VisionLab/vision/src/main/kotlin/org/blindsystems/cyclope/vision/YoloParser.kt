package org.blindsystems.cyclope.vision

import kotlin.math.abs
import kotlin.math.roundToInt

/** Explicit contracts avoid guessing coordinate units from the values of one frame. */
enum class OutputLayout { END_TO_END, CHANNELS_FIRST }

/** Parses COCO detection only. Depth/segmentation tensors are deliberately rejected. */
class YoloParser(private val threshold: Float = 0.35f) {
    init {
        require(threshold in 0f..1f)
    }

    fun parse(
        values: FloatArray,
        shape: IntArray,
    ): List<Detection> {
        require(shape.size == 3 && shape[0] == 1) { "Expected a batch-one detection tensor" }
        require(shape.all { it > 0 } && shape.fold(1L) { a, b -> a * b } == values.size.toLong())
        return when {
            shape[2] == 6 -> parseRows(values, shape[1])
            shape[1] == 84 -> suppress(parseChannels(values, shape[2]))
            else -> error("Expected [1,N,6] or [1,84,N]; export COCO detection, not depth/segmentation")
        }
    }

    private fun parseRows(
        v: FloatArray,
        count: Int,
    ): List<Detection> =
        (0 until count).mapNotNull { row ->
            val i = row * 6
            val id = v[i + 5]
            if (!id.isFinite() || id !in 0f..79f || abs(id - id.roundToInt()) > 0.001f) {
                null
            } else {
                detection(id.toInt(), v[i + 4], Box(v[i], v[i + 1], v[i + 2], v[i + 3]))
            }
        }.sortedByDescending { it.confidence }

    private fun parseChannels(
        v: FloatArray,
        count: Int,
    ): List<Detection> =
        (0 until count).mapNotNull { i ->
            val id = (0 until 80).maxBy { v[(it + 4) * count + i].takeIf(Float::isFinite) ?: -1f }
            val x = v[i]
            val y = v[count + i]
            val w = v[2 * count + i]
            val h = v[3 * count + i]
            detection(id, v[(id + 4) * count + i], Box(x - w / 2, y - h / 2, x + w / 2, y + h / 2))
        }

    private fun detection(
        id: Int,
        score: Float,
        b: Box,
    ): Detection? {
        if (!score.isFinite() || score !in threshold..1f) return null
        if (!listOf(b.left, b.top, b.right, b.bottom).all(Float::isFinite)) return null
        val clipped = Box(b.left.coerceIn(0f, 1f), b.top.coerceIn(0f, 1f), b.right.coerceIn(0f, 1f), b.bottom.coerceIn(0f, 1f))
        return if (clipped.area > 0f) Detection(id, score, clipped) else null
    }

    /** Legacy one-to-many exports need class-aware NMS; YOLO26 end-to-end exports do not. */
    private fun suppress(input: List<Detection>): List<Detection> {
        val kept = mutableListOf<Detection>()
        for (d in input.sortedByDescending { it.confidence }) {
            if (kept.none { it.classId == d.classId && intersectionOverUnion(it.box, d.box) > 0.45f }) kept += d
        }
        return kept
    }
}

fun intersectionOverUnion(
    a: Box,
    b: Box,
): Float {
    val intersection = Box(maxOf(a.left, b.left), maxOf(a.top, b.top), minOf(a.right, b.right), minOf(a.bottom, b.bottom)).area
    val union = a.area + b.area - intersection
    return if (union > 0) intersection / union else 0f
}
