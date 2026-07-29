package luzzr.muse.media

import android.os.SystemClock

class MonotonicUsageTracker(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var lastElapsedMs: Long? = null

    val isActive: Boolean
        get() = lastElapsedMs != null

    fun start(): Boolean {
        if (isActive) return false
        lastElapsedMs = clock()
        return true
    }

    fun takeDelta(): Long {
        val previous = lastElapsedMs ?: return 0L
        val now = clock()
        lastElapsedMs = now
        return (now - previous).coerceIn(0L, MAX_DELTA_MS)
    }

    fun pause(): Long {
        val delta = takeDelta()
        lastElapsedMs = null
        return delta
    }

    fun reset() {
        lastElapsedMs = null
    }

    private companion object {
        const val MAX_DELTA_MS = 5_000L
    }
}
