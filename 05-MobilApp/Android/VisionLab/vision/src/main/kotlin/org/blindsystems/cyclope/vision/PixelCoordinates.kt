package org.blindsystems.cyclope.vision

/** Current NCHW end-to-end exports use pixel xyxy, unlike legacy NHWC TFLite exports. */
fun normalizePixelRows(
    rows: FloatArray,
    width: Int,
    height: Int,
) {
    require(rows.size % 6 == 0 && width > 0 && height > 0)
    for (i in rows.indices step 6) {
        rows[i] /= width
        rows[i + 1] /= height
        rows[i + 2] /= width
        rows[i + 3] /= height
    }
}
