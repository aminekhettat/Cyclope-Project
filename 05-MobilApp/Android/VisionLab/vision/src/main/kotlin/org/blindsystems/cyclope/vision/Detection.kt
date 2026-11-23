package org.blindsystems.cyclope.vision

/** Coordinates are normalized to the model image, including letterbox padding. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
}

/** COCO class ID, probability (not a guarantee), and bounding box. */
data class Detection(val classId: Int, val confidence: Float, val box: Box)

/** Both overlay and speech must use the same filtered list. An empty set means none. */
fun filterClasses(
    detections: List<Detection>,
    selected: Set<Int>,
): List<Detection> =
    detections.filter { it.classId in selected }
