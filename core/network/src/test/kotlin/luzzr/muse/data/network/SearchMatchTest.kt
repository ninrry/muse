package luzzr.muse.data.network

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SearchMatch] — covers normalization, scoring,
 * and threshold logic used by metadata and lyrics matching.
 */
class SearchMatchTest {

    // -- cleanOptional ---------------------------------------------

    @Test
    fun `cleanOptional returns null for blank input`() {
        assertNull(SearchMatch.cleanOptional(""))
        assertNull(SearchMatch.cleanOptional("   "))
        assertNull(SearchMatch.cleanOptional(null))
    }

    @Test
    fun `cleanOptional returns null for unknown markers`() {
        assertNull(SearchMatch.cleanOptional("Unknown"))
        assertNull(SearchMatch.cleanOptional("<unknown>"))
        assertNull(SearchMatch.cleanOptional("未知艺术家"))
    }

    @Test
    fun `cleanOptional returns trimmed value for valid input`() {
        assertEquals("Taylor Swift", SearchMatch.cleanOptional("  Taylor Swift  "))
    }

    // -- normalize --------------------------------------------------

    @Test
    fun `normalize strips noise words and special chars`() {
        val result = SearchMatch.normalize("Hello World [Official MV]")
        assertFalse(result.contains("official"))
        assertFalse(result.contains("mv"))
        assertTrue(result.contains("hello"))
    }

    @Test
    fun `normalize returns empty for null or blank`() {
        assertEquals("", SearchMatch.normalize(null))
        assertEquals("", SearchMatch.normalize(""))
        assertEquals("", SearchMatch.normalize("   "))
    }

    // -- titleScore -------------------------------------------------

    @Test
    fun `titleScore exact match returns 60`() {
        assertEquals(60, SearchMatch.titleScore("Shape of You", "Shape of You"))
    }

    @Test
    fun `titleScore candidate contains query returns 54`() {
        assertEquals(54, SearchMatch.titleScore("Shape", "Shape of You"))
    }

    @Test
    fun `titleScore query contains candidate returns 42`() {
        val score = SearchMatch.titleScore("Shape of You Remix", "Shape of You")
        assertEquals(42, score)
    }

    @Test
    fun `titleScore returns 0 for blank inputs`() {
        assertEquals(0, SearchMatch.titleScore("", "candidate"))
        assertEquals(0, SearchMatch.titleScore("query", ""))
    }

    @Test
    fun `titleScore partial overlap returns positive score`() {
        val score = SearchMatch.titleScore("Shape of You", "Shape")
        assertTrue("Expected positive score, got $score", score > 0)
    }

    // -- artistScore ------------------------------------------------

    @Test
    fun `artistScore exact match returns 32`() {
        assertEquals(32, SearchMatch.artistScore("Ed Sheeran", "Ed Sheeran"))
    }

    @Test
    fun `artistScore handles Chinese artist aliases`() {
        assertEquals(32, SearchMatch.artistScore("周杰伦", "Jay Chou"))
        assertEquals(32, SearchMatch.artistScore("Jay Chou", "周杰伦"))
        assertEquals(32, SearchMatch.artistScore("周杰伦", "jaychou"))
        assertEquals(32, SearchMatch.artistScore("周杰倫", "Jay Chou"))
    }

    @Test
    fun `artistScore returns 24 when query is unknown`() {
        assertEquals(24, SearchMatch.artistScore(null, "Ed Sheeran"))
        assertEquals(24, SearchMatch.artistScore("Unknown", "Ed Sheeran"))
    }

    @Test
    fun `artistScore substring match returns 26`() {
        assertEquals(26, SearchMatch.artistScore("Ed", "Ed Sheeran"))
    }

    @Test
    fun `artistScore returns 0 for blank candidate`() {
        assertEquals(0, SearchMatch.artistScore("Ed Sheeran", ""))
    }

    // -- trackScore -------------------------------------------------

    @Test
    fun `trackScore combines title and artist scores`() {
        val score = SearchMatch.trackScore(
            "Shape of You",
            "Ed Sheeran",
            "Shape of You",
            "Ed Sheeran"
        )
        // Title exact (60) + Artist exact (32) = 92
        assertEquals(92, score)
    }

    @Test
    fun `trackScore returns low title score alone when title below 18`() {
        val score = SearchMatch.trackScore(
            "ABC",
            "Artist",
            "XYZ",
            "Different"
        )
        assertTrue("Expected low score, got $score", score < 40)
    }

    @Test
    fun `trackScore is capped at 100`() {
        val score = SearchMatch.trackScore(
            "Shape of You",
            "Ed Sheeran",
            "Shape of You",
            "Ed Sheeran"
        )
        assertTrue("Score should be <= 100, got $score", score <= 100)
    }

    // -- minimumAcceptableScore -------------------------------------

    @Test
    fun `minimumAcceptableScore is lower when artist is unknown`() {
        assertEquals(34, SearchMatch.minimumAcceptableScore(null))
        assertEquals(34, SearchMatch.minimumAcceptableScore("Unknown"))
    }

    @Test
    fun `minimumAcceptableScore is higher when artist is known`() {
        assertEquals(46, SearchMatch.minimumAcceptableScore("Ed Sheeran"))
    }

    @Test
    fun `metadataQualityScore penalizes unrequested live or remix variants`() {
        val original = SearchMatch.metadataQualityScore(
            queryTitle = "青花瓷",
            queryArtist = "周杰伦",
            candidateTitle = "青花瓷",
            candidateArtist = "Jay Chou",
            sourceScore = 80,
            hasCover = true,
            hasYear = true
        )
        val live = SearchMatch.metadataQualityScore(
            queryTitle = "青花瓷",
            queryArtist = "周杰伦",
            candidateTitle = "青花瓷 Live Remix",
            candidateArtist = "Jay Chou",
            sourceScore = 100,
            hasCover = true,
            hasYear = true
        )

        assertTrue("Original score $original should beat variant score $live", original > live)
    }
}
