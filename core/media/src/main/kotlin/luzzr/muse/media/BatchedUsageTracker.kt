package luzzr.muse.media

/**
 * Samples a monotonic usage clock frequently while emitting database-sized batches.
 *
 * [MonotonicUsageTracker] intentionally caps a single sample to protect statistics
 * from lifecycle stalls and clock jumps. Long-lived consumers must therefore sample
 * it periodically instead of waiting until the session ends.
 */
class BatchedUsageTracker(
    private val tracker: MonotonicUsageTracker = MonotonicUsageTracker(),
    private val batchThresholdMs: Long = DEFAULT_BATCH_THRESHOLD_MS
) {
    private var pendingMs = 0L

    init {
        require(batchThresholdMs > 0L) { "batchThresholdMs must be positive" }
    }

    val isActive: Boolean
        get() = tracker.isActive

    fun start(): Boolean = tracker.start()

    fun takeBatch(): Long {
        if (!tracker.isActive) return 0L
        pendingMs += tracker.takeDelta()
        return if (pendingMs >= batchThresholdMs) drain() else 0L
    }

    fun pause(): Long {
        if (tracker.isActive) {
            pendingMs += tracker.pause()
        }
        return drain()
    }

    fun reset() {
        tracker.reset()
        pendingMs = 0L
    }

    private fun drain(): Long = pendingMs.also { pendingMs = 0L }

    private companion object {
        const val DEFAULT_BATCH_THRESHOLD_MS = 5_000L
    }
}
