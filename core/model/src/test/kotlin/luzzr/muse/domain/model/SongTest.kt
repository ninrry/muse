package luzzr.muse.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [Song] data class computed properties.
 */
class SongTest {

    // -- formattedDuration ------------------------------------------

    private val mockUri = "content://test/song"

    @Test
    fun `formattedDuration for zero duration`() {
        assertEquals("0:00", Song(duration = 0, uri = mockUri).formattedDuration)
    }

    @Test
    fun `formattedDuration for seconds only`() {
        assertEquals("0:30", Song(duration = 30_000, uri = mockUri).formattedDuration)
    }

    @Test
    fun `formattedDuration for minutes and seconds`() {
        assertEquals("3:45", Song(duration = 225_000, uri = mockUri).formattedDuration)
    }

    @Test
    fun `formattedDuration pads seconds with zero`() {
        assertEquals("1:05", Song(duration = 65_000, uri = mockUri).formattedDuration)
    }

    @Test
    fun `formattedDuration includes hours when over 3600s`() {
        // 1h 5m 30s = 3_930_000ms
        assertEquals("1:05:30", Song(duration = 3_930_000, uri = mockUri).formattedDuration)
    }

    @Test
    fun `formattedDuration hours pads minutes and seconds`() {
        // 2h 0m 5s = 7_205_000ms
        assertEquals("2:00:05", Song(duration = 7_205_000, uri = mockUri).formattedDuration)
    }

    // -- formattedSize ----------------------------------------------

    @Test
    fun `formattedSize for zero or negative`() {
        assertEquals("", Song(size = 0, uri = mockUri).formattedSize)
        assertEquals("", Song(size = -1, uri = mockUri).formattedSize)
    }

    @Test
    fun `formattedSize in bytes`() {
        assertEquals("512 B", Song(size = 512, uri = mockUri).formattedSize)
    }

    @Test
    fun `formattedSize in KB`() {
        assertEquals("1.0 KB", Song(size = 1_024, uri = mockUri).formattedSize)
        assertEquals("1.5 KB", Song(size = 1_536, uri = mockUri).formattedSize)
    }

    @Test
    fun `formattedSize in MB`() {
        assertEquals("5.0 MB", Song(size = 5_242_880, uri = mockUri).formattedSize)
    }

    @Test
    fun `formattedSize in GB`() {
        assertEquals("1.0 GB", Song(size = 1_073_741_824, uri = mockUri).formattedSize)
    }
}
