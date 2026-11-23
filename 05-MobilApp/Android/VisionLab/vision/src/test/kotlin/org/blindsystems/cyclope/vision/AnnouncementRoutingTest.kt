package org.blindsystems.cyclope.vision

import org.junit.Assert.*
import org.junit.Test

class AnnouncementRoutingTest {
    @Test fun normalUseRoutesToTts() {
        assertEquals(AnnouncementRoute.TTS, announcementRoute(false, false))
    }

    @Test fun talkBackRoutesToAccessibilityNotTts() {
        assertEquals(AnnouncementRoute.ACCESSIBILITY, announcementRoute(false, true))
    }

    @Test fun muteSuppressesNormalTts() {
        assertEquals(AnnouncementRoute.MUTED, announcementRoute(true, false))
    }

    @Test fun muteAlsoSuppressesAutomaticObjectAccessibilityAnnouncements() {
        assertEquals(AnnouncementRoute.MUTED, announcementRoute(true, true))
    }

    @Test fun routeCanChangeBackWithoutRecreatingThePolicy() {
        assertEquals(AnnouncementRoute.ACCESSIBILITY, announcementRoute(false, true))
        assertEquals(AnnouncementRoute.TTS, announcementRoute(false, false))
    }

    @Test fun firstLiveRegionUpdateIsImmediate() {
        assertTrue(UpdateGate(15000).mayPublish(0))
    }

    @Test fun repeatedFrameCannotSpamLiveRegion() {
        val p = UpdateGate(15000)
        p.mayPublish(0)
        assertFalse(p.mayPublish(14999))
    }

    @Test fun boundaryUpdateIsAllowed() {
        val p = UpdateGate(15000)
        p.mayPublish(0)
        assertTrue(p.mayPublish(15000))
    }

    @Test fun rejectedUpdateDoesNotPostponeNextAllowedUpdate() {
        val p = UpdateGate(10000)
        p.mayPublish(0)
        p.mayPublish(9000)
        assertTrue(p.mayPublish(10000))
    }

    @Test fun backwardClockCannotCauseABurst() {
        val p = UpdateGate(10000)
        p.mayPublish(100)
        assertFalse(p.mayPublish(50))
    }

    @Test fun resetAllowsImmediateUpdateAfterRestart() {
        val p = UpdateGate(15000)
        p.mayPublish(0)
        p.reset()
        assertTrue(p.mayPublish(1))
    }

    @Test fun zeroIntervalAllowsConsecutiveExplicitUpdates() {
        val p = UpdateGate(0)
        assertTrue(p.mayPublish(0))
        assertTrue(p.mayPublish(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeIntervalRejected() {
        UpdateGate(-1)
    }
}
