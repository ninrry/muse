package luzzr.muse.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchedUsageTrackerTest {
    @Test
    fun `periodic samples preserve a long session`() {
        var nowMs = 0L
        val tracker = BatchedUsageTracker(
            tracker = MonotonicUsageTracker { nowMs },
            batchThresholdMs = 5_000L
        )

        assertTrue(tracker.start())
        var recordedMs = 0L
        repeat(60) {
            nowMs += 1_000L
            recordedMs += tracker.takeBatch()
        }

        assertEquals(60_000L, recordedMs)
        assertTrue(tracker.isActive)
    }

    @Test
    fun `pause emits the remaining partial batch`() {
        var nowMs = 10_000L
        val tracker = BatchedUsageTracker(
            tracker = MonotonicUsageTracker { nowMs },
            batchThresholdMs = 5_000L
        )

        tracker.start()
        nowMs += 2_400L

        assertEquals(2_400L, tracker.pause())
        assertFalse(tracker.isActive)
        assertEquals(0L, tracker.pause())
    }
}
