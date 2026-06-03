package luzzr.muse.data.network

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [LyricsFetcher] cache management.
 * Network-dependent methods (fetchSync, search) are excluded —
 * only pure in-memory cache logic is tested.
 */
class LyricsFetcherTest {

    private lateinit var fetcher: LyricsFetcher

    @Before
    fun setUp() {
        fetcher = LyricsFetcher()
    }

    // -- Cache restore and retrieval -----------------------------

    @Test
    fun `restoreToCache stores result retrievable by getCachedResult`() {
        val result = makeLyricsResult(id = 1L, trackName = "Test")
        fetcher.restoreToCache(1L, result)

        val cached = fetcher.getCachedResult(1L)
        assertNotNull(cached)
        assertEquals("Test", cached?.trackName)
    }

    @Test
    fun `restoreToCache does not overwrite existing cache entry`() {
        val original = makeLyricsResult(id = 1L, trackName = "Original")
        val replacement = makeLyricsResult(id = 1L, trackName = "Replacement")

        fetcher.restoreToCache(1L, original)
        fetcher.restoreToCache(1L, replacement)

        val cached = fetcher.getCachedResult(1L)
        assertEquals("Original", cached?.trackName)
    }

    @Test
    fun `getCachedResult returns null for uncached song`() {
        assertNull(fetcher.getCachedResult(999L))
    }

    // -- getRawSyncedLyrics --------------------------------------

    @Test
    fun `getRawSyncedLyrics returns raw LRC text when cached`() {
        val result = makeLyricsResult(
            id = 1L,
            rawSyncedLyrics = "[00:00.00]Hello\n[00:05.00]World"
        )
        fetcher.restoreToCache(1L, result)

        val raw = fetcher.getRawSyncedLyrics(1L)
        assertEquals("[00:00.00]Hello\n[00:05.00]World", raw)
    }

    @Test
    fun `getRawSyncedLyrics returns null when not cached`() {
        assertNull(fetcher.getRawSyncedLyrics(42L))
    }

    @Test
    fun `getRawSyncedLyrics returns null when rawSyncedLyrics is null`() {
        val result = makeLyricsResult(id = 1L, rawSyncedLyrics = null)
        fetcher.restoreToCache(1L, result)

        assertNull(fetcher.getRawSyncedLyrics(1L))
    }

    // -- clearCache ----------------------------------------------

    @Test
    fun `clearCache removes all entries`() {
        fetcher.restoreToCache(1L, makeLyricsResult(id = 1L))
        fetcher.restoreToCache(2L, makeLyricsResult(id = 2L))
        fetcher.restoreToCache(3L, makeLyricsResult(id = 3L))

        fetcher.clearCache()

        assertNull(fetcher.getCachedResult(1L))
        assertNull(fetcher.getCachedResult(2L))
        assertNull(fetcher.getCachedResult(3L))
    }

    @Test
    fun `clearCache on empty cache does not throw`() {
        fetcher.clearCache()
        // No assertion — verifying no exception
    }

    // -- Multiple songs in cache ---------------------------------

    @Test
    fun `cache stores multiple songs independently`() {
        val result1 = makeLyricsResult(id = 1L, trackName = "Song One")
        val result2 = makeLyricsResult(id = 2L, trackName = "Song Two")

        fetcher.restoreToCache(1L, result1)
        fetcher.restoreToCache(2L, result2)

        assertEquals("Song One", fetcher.getCachedResult(1L)?.trackName)
        assertEquals("Song Two", fetcher.getCachedResult(2L)?.trackName)
    }

    @Test
    fun `clearCache then restore works correctly`() {
        fetcher.restoreToCache(1L, makeLyricsResult(id = 1L, trackName = "Before"))
        fetcher.clearCache()

        // After clear, restore should work (no existing entry)
        fetcher.restoreToCache(1L, makeLyricsResult(id = 1L, trackName = "After"))
        assertEquals("After", fetcher.getCachedResult(1L)?.trackName)
    }

    // -- Helpers -------------------------------------------------

    private fun makeLyricsResult(
        id: Long? = null,
        trackName: String = "Test Track",
        artistName: String = "Test Artist",
        rawSyncedLyrics: String? = "[00:00.00]Test"
    ) = LyricsResult(
        id = id,
        trackName = trackName,
        artistName = artistName,
        albumName = null,
        duration = 180.0,
        syncedLines = listOf(LrcLine(0L, "Test")),
        plainText = "Test",
        rawSyncedLyrics = rawSyncedLyrics
    )
}
