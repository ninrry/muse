package luzzr.muse.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import luzzr.muse.data.database.LyricsDao
import luzzr.muse.data.database.LyricsEntity
import luzzr.muse.data.database.LyricsOffsetDao
import luzzr.muse.data.database.LyricsOffsetEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for [LyricsRepository] — covers lyrics and offset
 * persistence via mocked DAO layer.
 */
class LyricsRepositoryTest {

    private lateinit var lyricsDao: LyricsDao
    private lateinit var lyricsOffsetDao: LyricsOffsetDao
    private lateinit var repository: LyricsRepository

    @Before
    fun setUp() {
        lyricsDao = mockk(relaxed = true)
        lyricsOffsetDao = mockk(relaxed = true)
        repository = LyricsRepository(lyricsDao, lyricsOffsetDao)
    }

    // -- Lyrics persistence --------------------------------------

    @Test
    fun `saveLyrics delegates to DAO with correct entity`() = runTest {
        repository.saveLyrics(1L, "[00:00.00]Hello", "Hello")

        coVerify {
            lyricsDao.insertLyrics(
                match {
                    it.songId == 1L && it.syncedLyrics == "[00:00.00]Hello" && it.plainText == "Hello"
                }
            )
        }
    }

    @Test
    fun `saveLyrics with null values passes nulls to DAO`() = runTest {
        repository.saveLyrics(2L, null, null)

        coVerify {
            lyricsDao.insertLyrics(
                match {
                    it.songId == 2L && it.syncedLyrics == null && it.plainText == null
                }
            )
        }
    }

    @Test
    fun `loadLyrics returns pair when DB has data`() = runTest {
        coEvery { lyricsDao.getLyrics(1L) } returns LyricsEntity(
            songId = 1L,
            syncedLyrics = "[00:00.00]synced",
            plainText = "plain"
        )

        val result = repository.loadLyrics(1L)
        assertNotNull(result)
        assertEquals("[00:00.00]synced", result?.first)
        assertEquals("plain", result?.second)
    }

    @Test
    fun `loadLyrics returns null when DB has no data`() = runTest {
        coEvery { lyricsDao.getLyrics(999L) } returns null

        val result = repository.loadLyrics(999L)
        assertNull(result)
    }

    @Test
    fun `loadLyrics handles null syncedLyrics with plainText only`() = runTest {
        coEvery { lyricsDao.getLyrics(5L) } returns LyricsEntity(
            songId = 5L,
            syncedLyrics = null,
            plainText = "only plain text"
        )

        val result = repository.loadLyrics(5L)
        assertNotNull(result)
        assertNull(result?.first)
        assertEquals("only plain text", result?.second)
    }

    @Test
    fun `deleteLyrics delegates to DAO`() = runTest {
        repository.deleteLyrics(42L)
        coVerify { lyricsDao.deleteLyrics(42L) }
    }

    // -- Offset persistence --------------------------------------

    @Test
    fun `loadLyricsOffset returns saved offset`() = runTest {
        coEvery { lyricsOffsetDao.getOffset(1L) } returns LyricsOffsetEntity(1L, 500L)

        val offset = repository.loadLyricsOffset(1L)
        assertEquals(500L, offset)
    }

    @Test
    fun `loadLyricsOffset returns 0 when no offset saved`() = runTest {
        coEvery { lyricsOffsetDao.getOffset(2L) } returns null

        val offset = repository.loadLyricsOffset(2L)
        assertEquals(0L, offset)
    }

    @Test
    fun `loadLyricsOffset returns negative offset correctly`() = runTest {
        coEvery { lyricsOffsetDao.getOffset(3L) } returns LyricsOffsetEntity(3L, -2500L)

        val offset = repository.loadLyricsOffset(3L)
        assertEquals(-2500L, offset)
    }

    @Test
    fun `saveLyricsOffset delegates to DAO with correct entity`() = runTest {
        repository.saveLyricsOffset(1L, 1000L)

        coVerify {
            lyricsOffsetDao.setOffset(LyricsOffsetEntity(1L, 1000L))
        }
    }

    @Test
    fun `saveLyricsOffset with negative value`() = runTest {
        repository.saveLyricsOffset(1L, -3000L)

        coVerify {
            lyricsOffsetDao.setOffset(LyricsOffsetEntity(1L, -3000L))
        }
    }

    @Test
    fun `saveLyricsOffset with zero value`() = runTest {
        repository.saveLyricsOffset(1L, 0L)

        coVerify {
            lyricsOffsetDao.setOffset(LyricsOffsetEntity(1L, 0L))
        }
    }
}
