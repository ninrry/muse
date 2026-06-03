package luzzr.muse.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [HomeStats] computed formatting properties.
 */
class HomeStatsTest {

    @Test
    fun `totalDurationFormatted minutes only`() {
        val stats = HomeStats(10, 5, 3, 1_800_000, 0) // 30 min
        assertEquals("30分钟", stats.totalDurationFormatted)
    }

    @Test
    fun `totalDurationFormatted hours and minutes`() {
        val stats = HomeStats(10, 5, 3, 7_200_000 + 1_800_000, 0) // 2h 30m
        assertEquals("2小时30分钟", stats.totalDurationFormatted)
    }

    @Test
    fun `totalDurationFormatted zero duration`() {
        val stats = HomeStats(0, 0, 0, 0, 0)
        assertEquals("0分钟", stats.totalDurationFormatted)
    }

    @Test
    fun `totalStorageFormatted in MB`() {
        val stats = HomeStats(10, 5, 3, 0, 104_857_600) // ~100 MB
        assertEquals("100.0 MB", stats.totalStorageFormatted)
    }

    @Test
    fun `totalStorageFormatted in GB`() {
        val stats = HomeStats(10, 5, 3, 0, 2_147_483_648) // 2 GB
        assertEquals("2.0 GB", stats.totalStorageFormatted)
    }

    @Test
    fun `totalStorageFormatted in KB`() {
        val stats = HomeStats(10, 5, 3, 0, 51_200) // 50 KB
        assertEquals("50 KB", stats.totalStorageFormatted)
    }
}
