package luzzr.muse.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for sort ordering logic used by LibraryViewModel.
 * Since sortSongs is private, this test replicates the sorting
 * behavior for each [SortType] and verifies correct ordering.
 */
class SortTypeTest {

    private val mockUri: android.net.Uri = io.mockk.mockk(relaxed = true)
    private val songs = listOf(
        Song(id = 1, title = "Bravo", artist = "Zack", album = "AlbumB", duration = 200_000, dateAdded = 1000, uri = mockUri),
        Song(id = 2, title = "Alpha", artist = "Amy", album = "AlbumA", duration = 300_000, dateAdded = 2000, uri = mockUri),
        Song(id = 3, title = "Charlie", artist = "Bob", album = "AlbumC", duration = 100_000, dateAdded = 3000, uri = mockUri)
    )

    private fun sort(list: List<Song>, type: SortType): List<Song> = when (type) {
        SortType.TITLE_ASC -> list.sortedBy { it.title }
        SortType.TITLE_DESC -> list.sortedByDescending { it.title }
        SortType.ARTIST_ASC -> list.sortedBy { it.artist }
        SortType.ARTIST_DESC -> list.sortedByDescending { it.artist }
        SortType.ALBUM_ASC -> list.sortedBy { it.album }
        SortType.ALBUM_DESC -> list.sortedByDescending { it.album }
        SortType.DURATION_ASC -> list.sortedBy { it.duration }
        SortType.DURATION_DESC -> list.sortedByDescending { it.duration }
        SortType.DATE_ADDED_DESC -> list.sortedByDescending { it.dateAdded }
        SortType.DATE_ADDED_ASC -> list.sortedBy { it.dateAdded }
    }

    @Test
    fun `TITLE_ASC sorts alphabetically by title`() {
        val sorted = sort(songs, SortType.TITLE_ASC)
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun `TITLE_DESC sorts reverse alphabetically by title`() {
        val sorted = sort(songs, SortType.TITLE_DESC)
        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.id })
    }

    @Test
    fun `ARTIST_ASC sorts alphabetically by artist`() {
        val sorted = sort(songs, SortType.ARTIST_ASC)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id }) // Amy, Bob, Zack
    }

    @Test
    fun `ARTIST_DESC sorts reverse alphabetically by artist`() {
        val sorted = sort(songs, SortType.ARTIST_DESC)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id }) // Zack, Bob, Amy
    }

    @Test
    fun `ALBUM_ASC sorts alphabetically by album`() {
        val sorted = sort(songs, SortType.ALBUM_ASC)
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id }) // AlbumA, AlbumB, AlbumC
    }

    @Test
    fun `DURATION_ASC sorts shortest first`() {
        val sorted = sort(songs, SortType.DURATION_ASC)
        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.id }) // 100k, 200k, 300k
    }

    @Test
    fun `DURATION_DESC sorts longest first`() {
        val sorted = sort(songs, SortType.DURATION_DESC)
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id }) // 300k, 200k, 100k
    }

    @Test
    fun `DATE_ADDED_DESC sorts newest first`() {
        val sorted = sort(songs, SortType.DATE_ADDED_DESC)
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id }) // 3000, 2000, 1000
    }

    @Test
    fun `DATE_ADDED_ASC sorts oldest first`() {
        val sorted = sort(songs, SortType.DATE_ADDED_ASC)
        assertEquals(listOf(1L, 2L, 3L), sorted.map { it.id }) // 1000, 2000, 3000
    }

    @Test
    fun `SortType entries are complete`() {
        assertEquals(10, SortType.entries.size)
    }

    @Test
    fun `SortType labels are non-blank`() {
        SortType.entries.forEach { type ->
            assertTrue("Label for $type should not be blank", type.label.isNotBlank())
        }
    }

    @Test
    fun `sort on empty list returns empty`() {
        SortType.entries.forEach { type ->
            val sorted = sort(emptyList(), type)
            assertTrue("Sort $type on empty list should return empty", sorted.isEmpty())
        }
    }
}
