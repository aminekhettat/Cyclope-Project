package org.blindsystems.cyclope.vision

import org.junit.Assert.*
import org.junit.Test

class SpeechPolicyTest {
    private fun SpeechPolicy.offer(
        id: Int,
        time: Long = 0,
        mute: Boolean = false,
        reader: Boolean = false,
    ) = offer(id, time, mute, reader)

    @Test fun firstDetectionAtTimeZeroIsAccepted() {
        assertTrue(SpeechPolicy().offer(0))
    }

    @Test fun repeatWithinCooldownIsRejected() {
        val p = SpeechPolicy()
        p.offer(0)
        p.poll()
        assertFalse(p.offer(0, 4999))
    }

    @Test fun repeatExactlyAtCooldownIsAccepted() {
        val p = SpeechPolicy()
        p.offer(0)
        p.poll()
        assertTrue(p.offer(0, 5000))
    }

    @Test fun differentClassHasIndependentCooldown() {
        val p = SpeechPolicy()
        p.offer(0)
        assertTrue(p.offer(1, 1))
    }

    @Test fun duplicatePendingClassIsNotQueuedTwice() {
        val p = SpeechPolicy()
        p.offer(0)
        assertFalse(p.offer(0, 6000))
    }

    @Test fun fifoPreservesClassOrder() {
        val p = SpeechPolicy()
        p.offer(2)
        p.offer(1)
        assertEquals(2, p.poll())
        assertEquals(1, p.poll())
        assertNull(p.poll())
    }

    @Test fun queueIsBounded() {
        val p = SpeechPolicy(capacity = 2)
        p.offer(0)
        p.offer(1)
        assertFalse(p.offer(2))
    }

    @Test fun rejectedCapacityDoesNotConsumeCooldown() {
        val p = SpeechPolicy(capacity = 1)
        p.offer(0)
        p.offer(1)
        p.poll()
        assertTrue(p.offer(1, 1))
    }

    @Test fun mutedSpeechIsNotQueued() {
        assertFalse(SpeechPolicy().offer(0, mute = true))
    }

    @Test fun screenReaderPreventsSpeech() {
        assertFalse(SpeechPolicy().offer(0, reader = true))
    }

    @Test fun rejectedMutedDoesNotConsumeCooldown() {
        val p = SpeechPolicy()
        p.offer(0, mute = true)
        assertTrue(p.offer(0, 1))
    }

    @Test fun clearDropsPendingSpeech() {
        val p = SpeechPolicy()
        p.offer(0)
        p.clear()
        assertNull(p.poll())
    }

    @Test fun clearDoesNotResetAntispam() {
        val p = SpeechPolicy()
        p.offer(0)
        p.clear()
        assertFalse(p.offer(0, 1))
    }

    @Test fun backwardClockDoesNotCauseSpeechBurst() {
        val p = SpeechPolicy()
        p.offer(0, 100)
        p.poll()
        assertFalse(p.offer(0, 50))
    }

    @Test fun unknownClassRejected() {
        assertFalse(SpeechPolicy().offer(80))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeIntervalRejected() {
        SpeechPolicy(intervalMs = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroCapacityRejected() {
        SpeechPolicy(capacity = 0)
    }
}
