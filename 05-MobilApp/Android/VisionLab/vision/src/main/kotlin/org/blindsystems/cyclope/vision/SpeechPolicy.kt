package org.blindsystems.cyclope.vision

/** A bounded FIFO. Only successfully queued speech starts a class cooldown. Clock is monotonic. */
class SpeechPolicy(private val intervalMs: Long = 5000, private val capacity: Int = 3) {
    init {
        require(intervalMs >= 0 && capacity > 0)
    }

    private val lastQueued = mutableMapOf<Int, Long>()
    private val queue = ArrayDeque<Int>()

    fun offer(
        classId: Int,
        nowMs: Long,
        muted: Boolean,
        screenReader: Boolean,
    ): Boolean {
        if (muted || screenReader || classId !in 0..79 || classId in queue || queue.size >= capacity) return false
        val last = lastQueued[classId]
        if (last != null && nowMs - last < intervalMs) return false
        queue.addLast(classId)
        lastQueued[classId] = nowMs
        return true
    }

    fun poll(): Int? = queue.removeFirstOrNull()

    fun clear() {
        queue.clear()
    }
}
