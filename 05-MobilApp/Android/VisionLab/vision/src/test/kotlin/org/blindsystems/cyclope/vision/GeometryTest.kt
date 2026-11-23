package org.blindsystems.cyclope.vision

import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    private val full = Box(0f, 0f, 1f, 1f)

    @Test fun squareHasNoPadding() {
        val l = Letterbox(640, 640, 640, 640)
        assertEquals(0, l.left)
        assertEquals(0, l.top)
        assertEquals(full, l.toSource(full))
    }

    @Test fun landscapeRemovesVerticalPadding() {
        val l = Letterbox(640, 480, 640, 640)
        assertEquals(80, l.top)
        assertEquals(full, l.toSource(Box(0f, 0.125f, 1f, 0.875f)))
    }

    @Test fun portraitRemovesHorizontalPadding() {
        val l = Letterbox(480, 640, 640, 640)
        assertEquals(80, l.left)
        assertEquals(full, l.toSource(Box(0.125f, 0f, 0.875f, 1f)))
    }

    @Test fun boxInPaddingClipsToZeroArea() {
        assertEquals(0f, Letterbox(640, 480, 640, 640).toSource(Box(0f, 0f, 1f, 0.1f)).area, 0f)
    }

    @Test fun oddPaddingUsesIntegerOffsets() {
        val l = Letterbox(641, 480, 640, 640)
        assertEquals(479, l.resizedHeight)
        assertEquals(80, l.top)
    }

    @Test fun nonSquareModelSupported() {
        val l = Letterbox(640, 480, 320, 240)
        assertEquals(320, l.resizedWidth)
        assertEquals(240, l.resizedHeight)
        assertEquals(full, l.toSource(full))
    }

    @Test fun fitCenterAddsSideBars() {
        assertEquals(Box(160f, 0f, 480f, 320f), mapToScreen(full, 640, 640, 640, 320))
    }

    @Test fun fitCenterAddsTopBars() {
        assertEquals(Box(0f, 160f, 320f, 480f), mapToScreen(full, 640, 640, 320, 640))
    }

    @Test fun fitCenterMapsInnerBox() {
        assertEquals(Box(80f, 80f, 240f, 240f), mapToScreen(Box(0.25f, 0.25f, 0.75f, 0.75f), 640, 640, 320, 320))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSourceRejected() {
        Letterbox(0, 480, 640, 640)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidViewRejected() {
        mapToScreen(full, 640, 480, 0, 480)
    }

    @Test fun identicalBoxesHaveUnitIou() {
        assertEquals(1f, intersectionOverUnion(full, full), 0f)
    }

    @Test fun disjointBoxesHaveZeroIou() {
        assertEquals(0f, intersectionOverUnion(Box(0f, 0f, 0.2f, 0.2f), Box(0.8f, 0.8f, 1f, 1f)), 0f)
    }

    @Test fun degenerateBoxesHaveZeroIou() {
        assertEquals(0f, intersectionOverUnion(Box(0f, 0f, 0f, 0f), Box(0f, 0f, 0f, 0f)), 0f)
    }

    @Test fun overlappingBoxesHaveExpectedIou() {
        assertEquals(1f / 7, intersectionOverUnion(Box(0f, 0f, 2f, 2f), Box(1f, 1f, 3f, 3f)), 0.0001f)
    }
}
