package org.blindsystems.cyclope.vision

import org.junit.Assert.*
import org.junit.Test

class YoloParserTest {
    private fun row(
        score: Float = 0.8f,
        id: Float = 0f,
        box: Box = Box(0.1f, 0.2f, 0.7f, 0.9f),
    ) = floatArrayOf(box.left, box.top, box.right, box.bottom, score, id)

    private fun parse(v: FloatArray) = YoloParser().parse(v, intArrayOf(1, v.size / 6, 6))

    @Test fun endToEndRowPreservesClassScoreAndCorners() {
        val d = parse(row(id = 79f)).single()
        assertEquals(79, d.classId)
        assertEquals(0.8f, d.confidence, 0f)
        assertEquals(Box(0.1f, 0.2f, 0.7f, 0.9f), d.box)
    }

    @Test fun paddingZeroRowsAreNotDetections() {
        assertTrue(parse(FloatArray(12)).isEmpty())
    }

    @Test fun confidenceAtThresholdIsKept() {
        assertEquals(1, parse(row(score = 0.35f)).size)
    }

    @Test fun confidenceBelowThresholdIsRemoved() {
        assertTrue(parse(row(score = 0.349f)).isEmpty())
    }

    @Test fun impossibleConfidenceIsRemoved() {
        assertTrue(parse(row(score = 1.1f)).isEmpty())
    }

    @Test fun nanConfidenceIsRemoved() {
        assertTrue(parse(row(score = Float.NaN)).isEmpty())
    }

    @Test fun infiniteConfidenceIsRemoved() {
        assertTrue(parse(row(score = Float.POSITIVE_INFINITY)).isEmpty())
    }

    @Test fun unknownClassIsRemoved() {
        assertTrue(parse(row(id = 80f)).isEmpty())
    }

    @Test fun negativeClassIsRemoved() {
        assertTrue(parse(row(id = -1f)).isEmpty())
    }

    @Test fun fractionalClassIsNotSilentlyTruncated() {
        assertTrue(parse(row(id = 1.5f)).isEmpty())
    }

    @Test fun nanClassDoesNotCrash() {
        assertTrue(parse(row(id = Float.NaN)).isEmpty())
    }

    @Test fun infiniteCoordinateIsRemoved() {
        assertTrue(parse(row(box = Box(0f, 0f, Float.POSITIVE_INFINITY, 1f))).isEmpty())
    }

    @Test fun nanCoordinateIsRemoved() {
        assertTrue(parse(row(box = Box(Float.NaN, 0f, 1f, 1f))).isEmpty())
    }

    @Test fun reversedBoxIsRemoved() {
        assertTrue(parse(row(box = Box(0.8f, 0f, 0.2f, 1f))).isEmpty())
    }

    @Test fun zeroAreaBoxIsRemoved() {
        assertTrue(parse(row(box = Box(0.5f, 0f, 0.5f, 1f))).isEmpty())
    }

    @Test fun boxesAreClippedToImage() {
        assertEquals(Box(0f, 0f, 1f, 1f), parse(row(box = Box(-1f, -2f, 2f, 3f))).single().box)
    }

    @Test fun endToEndDoesNotApplySecondNms() {
        assertEquals(2, parse(row() + row(score = 0.7f)).size)
    }

    @Test fun resultsAreConfidenceOrdered() {
        assertEquals(listOf(0.9f, 0.5f), parse(row(score = 0.5f) + row(score = 0.9f)).map { it.confidence })
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongBatchRejected() {
        YoloParser().parse(FloatArray(12), intArrayOf(2, 1, 6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongElementCountRejected() {
        YoloParser().parse(FloatArray(6), intArrayOf(1, 2, 6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDimensionRejected() {
        YoloParser().parse(FloatArray(0), intArrayOf(1, 0, 6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongRankRejected() {
        YoloParser().parse(FloatArray(6), intArrayOf(1, 6))
    }

    @Test(expected = IllegalStateException::class)
    fun segmentationShapeRejected() {
        YoloParser().parse(FloatArray(116), intArrayOf(1, 116, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidThresholdRejected() {
        YoloParser(1.1f)
    }

    private fun channels(ids: List<Int>): FloatArray {
        val n = ids.size
        return FloatArray(84 * n).apply {
            for ((i, id) in ids.withIndex()) {
                this[i] = 0.5f
                this[n + i] = 0.5f
                this[2 * n + i] = 0.4f
                this[3 * n + i] = 0.2f
                this[(4 + id) * n + i] = 0.9f - 0.1f * i
            }
        }
    }

    @Test fun rawChannelsConvertCenterToCorners() {
        val b = YoloParser().parse(channels(listOf(2)), intArrayOf(1, 84, 1)).single().box
        assertEquals(0.3f, b.left, 0.0001f)
        assertEquals(0.6f, b.bottom, 0.0001f)
    }

    @Test fun rawChannelsApplyNmsWithinClass() {
        assertEquals(1, YoloParser().parse(channels(listOf(0, 0)), intArrayOf(1, 84, 2)).size)
    }

    @Test fun rawChannelsDoNotSuppressDifferentClasses() {
        assertEquals(2, YoloParser().parse(channels(listOf(0, 1)), intArrayOf(1, 84, 2)).size)
    }

    @Test fun rawChannelsIgnoreNanClassScores() {
        val v = channels(listOf(0))
        v[5] = Float.NaN
        assertEquals(0, YoloParser().parse(v, intArrayOf(1, 84, 1)).single().classId)
    }

    @Test fun blankRawChannelsHaveNoDetections() {
        assertTrue(YoloParser().parse(FloatArray(84), intArrayOf(1, 84, 1)).isEmpty())
    }

    @Test fun nchwPixelRowsNormalizeOnlyCoordinates() {
        val v = floatArrayOf(64f, 32f, 320f, 160f, 0.9f, 2f)
        normalizePixelRows(v, 640, 320)
        assertArrayEquals(floatArrayOf(0.1f, 0.1f, 0.5f, 0.5f, 0.9f, 2f), v, 0.00001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedPixelRowsRejected() {
        normalizePixelRows(FloatArray(5), 640, 640)
    }
}
