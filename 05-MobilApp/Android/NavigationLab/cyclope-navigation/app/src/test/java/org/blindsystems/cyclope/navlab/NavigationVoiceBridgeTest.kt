package org.blindsystems.cyclope.navlab

import org.blindsystems.cyclope.navigation.core.Priority
import org.blindsystems.cyclope.voicekit.core.AnnouncementRoute
import org.blindsystems.cyclope.voicekit.core.VoiceAnnouncement
import org.blindsystems.cyclope.voicekit.core.announcementRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationVoiceBridgeTest {
    private val delivered = mutableListOf<List<VoiceAnnouncement>>()
    private val log = mutableListOf<String>()
    private var interrupts = 0

    private fun bridge() = NavigationVoiceBridge({ delivered += it }, { interrupts++ }, log::add)

    @Test fun initializationFlushesOriginalTextAndNavigationKey() {
        val b = bridge()
        b.speak("Tournez à droite", Priority.INSTRUCTION)
        assertTrue(delivered.isEmpty())
        b.onInitialized()
        assertEquals(listOf(VoiceAnnouncement("navigation:INSTRUCTION:Tournez à droite", "Tournez à droite")), delivered.single())
    }

    @Test fun initializedMessageIsDeliveredImmediately() {
        val b = bridge()
        b.onInitialized()
        b.speak("Continuez", Priority.INFO)
        assertEquals("Continuez", delivered.single().single().text)
    }

    @Test fun blankTextIsNeitherLoggedNorQueued() {
        val b = bridge()
        b.speak("  ", Priority.WARNING)
        b.onInitialized()
        assertTrue(log.isEmpty())
        assertTrue(delivered.isEmpty())
        assertEquals(0, interrupts)
    }

    @Test fun startupQueueIsBoundedToThree() {
        val b = bridge()
        repeat(5) { b.speak("Message $it", Priority.INFO) }
        b.onInitialized()
        assertEquals(listOf("Message 0", "Message 1", "Message 2"), delivered.single().map { it.text })
    }

    @Test fun duplicateStartupKeysAreNotSpokenTwice() {
        val b = bridge()
        repeat(2) { b.speak("Même adresse", Priority.INFO) }
        b.onInitialized()
        assertEquals(1, delivered.single().size)
    }

    @Test fun warningDiscardsObsoleteStartupGuidance() {
        val b = bridge()
        b.speak("Continuez", Priority.INSTRUCTION)
        b.speak("Hors itinéraire", Priority.WARNING)
        b.onInitialized()
        assertEquals(listOf("Hors itinéraire"), delivered.single().map { it.text })
        assertEquals(1, interrupts)
    }

    @Test fun warningInterruptsBeforeDelivery() {
        val events = mutableListOf<String>()
        val b = NavigationVoiceBridge({ events += "deliver" }, { events += "stop" }, {})
        b.onInitialized()
        b.speak("Attention", Priority.WARNING)
        assertEquals(listOf("stop", "deliver"), events)
    }

    @Test fun normalMessageDoesNotInterrupt() {
        val b = bridge()
        b.onInitialized()
        b.speak("Adresse suivante", Priority.INFO)
        assertEquals(0, interrupts)
        assertEquals("[INFO] Adresse suivante", log.single())
    }

    @Test fun stopClearsPendingStartupMessages() {
        val b = bridge()
        b.speak("Ancien trajet", Priority.INFO)
        b.stop()
        b.onInitialized()
        assertTrue(delivered.isEmpty())
        assertEquals(1, interrupts)
    }

    @Test fun repeatedInitializationDoesNotReplayMessages() {
        val b = bridge()
        b.speak("Départ", Priority.INFO)
        repeat(2) { b.onInitialized() }
        assertEquals(1, delivered.size)
    }

    @Test fun drainTickIsInactiveBeforeInitialization() {
        bridge().tick()
        assertTrue(delivered.isEmpty())
    }

    @Test fun drainTickLetsSharedQueuePublishWithoutAnotherGuidanceEvent() {
        val b = bridge()
        b.onInitialized()
        b.tick()
        assertEquals(listOf(emptyList<VoiceAnnouncement>()), delivered)
    }

    private fun routedMessage(
        touchExploration: Boolean,
        muted: Boolean,
    ): Pair<List<String>, List<String>> {
        val tts = mutableListOf<String>()
        val accessibility = mutableListOf<String>()
        // Model only the shared pure routing boundary: the host itself selects no Android engine.
        val b =
            NavigationVoiceBridge({ messages ->
                when (announcementRoute(muted, touchExploration)) {
                    AnnouncementRoute.TTS -> tts += messages.map { it.text }
                    AnnouncementRoute.ACCESSIBILITY -> accessibility += messages.map { it.text }
                    AnnouncementRoute.MUTED -> Unit
                }
            }, {}, {})
        b.onInitialized()
        b.speak("Arrivée", Priority.INSTRUCTION)
        return tts to accessibility
    }

    @Test fun talkBackUsesAccessibilityInsteadOfRawTts() {
        val (tts, a11y) = routedMessage(touchExploration = true, muted = false)
        assertTrue(tts.isEmpty())
        assertEquals(listOf("Arrivée"), a11y)
    }

    @Test fun noTouchExplorationUsesSharedTts() {
        val (tts, a11y) = routedMessage(touchExploration = false, muted = false)
        assertEquals(listOf("Arrivée"), tts)
        assertTrue(a11y.isEmpty())
    }

    @Test fun muteSuppressesBothCustomRoutes() {
        for (talkBack in listOf(false, true)) {
            val (tts, a11y) = routedMessage(touchExploration = talkBack, muted = true)
            assertTrue(tts.isEmpty() && a11y.isEmpty())
        }
    }
}
