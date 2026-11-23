package org.blindsystems.cyclope.navlab

import org.blindsystems.cyclope.navigation.core.Priority
import org.blindsystems.cyclope.voicekit.core.AnnouncementQueue
import org.blindsystems.cyclope.voicekit.core.VoiceAnnouncement

/** Navigation-only adapter. TalkBack detection, cooldown and delivery belong to shared voicekit. */
class NavigationVoiceBridge(
    private val deliver: (List<VoiceAnnouncement>) -> Unit,
    private val interrupt: () -> Unit,
    private val log: (String) -> Unit,
) {
    // Keep the last startup warning and following messages while Android initializes its TTS service.
    private val startup = AnnouncementQueue(intervalMs = 0, capacity = 3)
    private var initialized = false

    fun speak(
        text: String,
        priority: Priority,
    ) {
        if (text.isBlank()) return
        log("[${priority.name}] $text")
        if (priority == Priority.WARNING) stop()
        val message = VoiceAnnouncement("navigation:${priority.name}:$text", text)
        if (initialized) deliver(listOf(message)) else startup.offer(message, 0, muted = false)
    }

    /** Called after voicekit reports initialization, including failure (TalkBack still works). */
    fun onInitialized() {
        if (initialized) return
        initialized = true
        val messages = mutableListOf<VoiceAnnouncement>()
        while (startup.peek() != null) messages += startup.poll()!!
        if (messages.isNotEmpty()) deliver(messages)
    }

    /** Drain voicekit's paced accessibility queue even if no new guidance event arrives. */
    fun tick() {
        if (initialized) deliver(emptyList())
    }

    fun stop() {
        startup.clear()
        interrupt()
    }
}
