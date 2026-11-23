package org.blindsystems.cyclope.voicekit.core

/** Bounded FIFO with per-key cooldowns. Successfully queued messages alone consume a cooldown. */
class AnnouncementQueue(private val intervalMs: Long = 5000, private val capacity: Int = 3) {
    init {
        require(intervalMs >= 0 && capacity > 0)
    }

    private val lastQueued = mutableMapOf<String, Long>()
    private val queue = ArrayDeque<VoiceAnnouncement>()

    fun offer(
        announcement: VoiceAnnouncement,
        nowMs: Long,
        muted: Boolean,
    ): Boolean {
        if (muted || announcement.key.isBlank() || announcement.text.isBlank()) return false
        if (queue.any { it.key == announcement.key } || queue.size >= capacity) return false
        val last = lastQueued[announcement.key]
        if (last != null && nowMs - last < intervalMs) return false
        queue.addLast(announcement)
        lastQueued[announcement.key] = nowMs
        return true
    }

    fun peek(): VoiceAnnouncement? = queue.firstOrNull()

    fun poll(): VoiceAnnouncement? = queue.removeFirstOrNull()

    fun clear() {
        queue.clear()
    }
}
