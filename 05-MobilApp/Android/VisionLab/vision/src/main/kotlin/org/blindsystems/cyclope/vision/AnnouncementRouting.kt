package org.blindsystems.cyclope.vision

/** Accessibility services own their speech queue. Never run raw TTS on top of touch exploration. */
enum class AnnouncementRoute { MUTED, TTS, ACCESSIBILITY }

fun announcementRoute(
    muted: Boolean,
    touchExploration: Boolean,
): AnnouncementRoute =
    when {
        muted -> AnnouncementRoute.MUTED
        touchExploration -> AnnouncementRoute.ACCESSIBILITY
        else -> AnnouncementRoute.TTS
    }

/** Monotonic pacing shared by live regions and object announcements. A first update is immediate. */
class UpdateGate(private val intervalMs: Long) {
    init {
        require(intervalMs >= 0)
    }

    private var lastPublished: Long? = null

    fun mayPublish(nowMs: Long): Boolean {
        val last = lastPublished
        if (last != null && nowMs - last < intervalMs) return false
        lastPublished = nowMs
        return true
    }

    fun reset() {
        lastPublished = null
    }
}
