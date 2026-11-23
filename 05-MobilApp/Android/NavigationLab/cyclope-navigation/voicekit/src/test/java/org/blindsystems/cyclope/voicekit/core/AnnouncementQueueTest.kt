package org.blindsystems.cyclope.voicekit.core

import org.junit.Assert.*
import org.junit.Test

class AnnouncementQueueTest {
    private fun AnnouncementQueue.offer(
        id: Int,
        time: Long = 0,
        mute: Boolean = false,
    ) = offer(VoiceAnnouncement("test-$id", "message $id"), time, mute)

    @Test fun firstDetectionAtTimeZeroIsAccepted() {
        assertTrue(AnnouncementQueue().offer(0))
    }

    @Test fun repeatWithinCooldownIsRejected() {
        val p = AnnouncementQueue()
        p.offer(0)
        p.poll()
        assertFalse(p.offer(0, 4999))
    }

    @Test fun repeatExactlyAtCooldownIsAccepted() {
        val p = AnnouncementQueue()
        p.offer(0)
        p.poll()
        assertTrue(p.offer(0, 5000))
    }

    @Test fun differentClassHasIndependentCooldown() {
        val p = AnnouncementQueue()
        p.offer(0)
        assertTrue(p.offer(1, 1))
    }

    @Test fun duplicatePendingClassIsNotQueuedTwice() {
        val p = AnnouncementQueue()
        p.offer(0)
        assertFalse(p.offer(0, 6000))
    }

    @Test fun fifoPreservesClassOrder() {
        val p = AnnouncementQueue()
        p.offer(2)
        p.offer(1)
        assertEquals("test-2", p.poll()?.key)
        assertEquals("test-1", p.poll()?.key)
        assertNull(p.poll())
    }

    @Test fun queueIsBounded() {
        val p = AnnouncementQueue(capacity = 2)
        p.offer(0)
        p.offer(1)
        assertFalse(p.offer(2))
    }

    @Test fun rejectedCapacityDoesNotConsumeCooldown() {
        val p = AnnouncementQueue(capacity = 1)
        p.offer(0)
        p.offer(1)
        p.poll()
        assertTrue(p.offer(1, 1))
    }

    @Test fun mutedSpeechIsNotQueued() {
        assertFalse(AnnouncementQueue().offer(0, mute = true))
    }

    @Test fun accessibilityRoutingDoesNotDiscardTheObjectQueue() {
        val policy = AnnouncementQueue()
        assertTrue(policy.offer(0))
        assertEquals("test-0", policy.peek()?.key)
        assertEquals("test-0", policy.poll()?.key)
        assertNull(policy.peek())
    }

    @Test fun rejectedMutedDoesNotConsumeCooldown() {
        val p = AnnouncementQueue()
        p.offer(0, mute = true)
        assertTrue(p.offer(0, 1))
    }

    @Test fun clearDropsPendingSpeech() {
        val p = AnnouncementQueue()
        p.offer(0)
        p.clear()
        assertNull(p.poll())
    }

    @Test fun clearDoesNotResetAntispam() {
        val p = AnnouncementQueue()
        p.offer(0)
        p.clear()
        assertFalse(p.offer(0, 1))
    }

    @Test fun backwardClockDoesNotCauseSpeechBurst() {
        val p = AnnouncementQueue()
        p.offer(0, 100)
        p.poll()
        assertFalse(p.offer(0, 50))
    }

    @Test fun emptyKeyIsRejected() {
        assertFalse(AnnouncementQueue().offer(VoiceAnnouncement("", "turn left"), 0, false))
    }

    @Test fun emptyTextIsRejected() {
        assertFalse(AnnouncementQueue().offer(VoiceAnnouncement("turn-left", "  "), 0, false))
    }

    @Test fun navigationKeysWorkWithoutAClassId() {
        val queue = AnnouncementQueue()
        assertTrue(queue.offer(VoiceAnnouncement("route-step-42", "Turn left in 20 meters"), 0, false))
        assertEquals("Turn left in 20 meters", queue.poll()?.text)
    }

    @Test fun messageIsPreservedWithoutDomainTranslation() {
        val announcement = VoiceAnnouncement("arrival", "You have arrived")
        val queue = AnnouncementQueue()
        queue.offer(announcement, 0, false)
        assertEquals(announcement, queue.peek())
    }

    @Test fun sameKeyWithChangedWordingStillDeduplicates() {
        val queue = AnnouncementQueue()
        queue.offer(VoiceAnnouncement("turn-left", "Turn left"), 0, false)
        assertFalse(queue.offer(VoiceAnnouncement("turn-left", "Now turn left"), 6000, false))
    }

    @Test fun invalidTextDoesNotConsumeCooldown() {
        val queue = AnnouncementQueue()
        queue.offer(VoiceAnnouncement("arrival", ""), 0, false)
        assertTrue(queue.offer(VoiceAnnouncement("arrival", "You have arrived"), 1, false))
    }

    @Test fun independentKeysMayHaveTheSameText() {
        val queue = AnnouncementQueue()
        assertTrue(queue.offer(VoiceAnnouncement("step-1", "Continue"), 0, false))
        assertTrue(queue.offer(VoiceAnnouncement("step-2", "Continue"), 1, false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeIntervalRejected() {
        AnnouncementQueue(intervalMs = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroCapacityRejected() {
        AnnouncementQueue(capacity = 0)
    }
}
