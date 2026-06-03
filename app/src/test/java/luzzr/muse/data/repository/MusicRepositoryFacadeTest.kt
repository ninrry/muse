package luzzr.muse.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import luzzr.muse.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class MusicRepositoryFacadeTest {

    private lateinit var facade: MusicRepositoryFacade
    private val songRepository: SongRepositoryImpl = mockk(relaxed = true)
    private val lyricsRepository: LyricsRepository = mockk(relaxed = true)
    private val artworkRepository: ArtworkRepository = mockk(relaxed = true)
    private val mockUri: android.net.Uri = mockk(relaxed = true)

    private val testSong = Song(id = 1, title = "测试歌", artist = "歌手", uri = mockUri, filePath = "/a/1.mp3")

    @Before
    fun setup() {
        every { songRepository.songs } returns MutableStateFlow(listOf(testSong))
        every { songRepository.isScanning } returns MutableStateFlow(false)
        every { songRepository.scanProgress } returns MutableStateFlow(0)
        every { songRepository.scanStats } returns MutableStateFlow(null)
        val songDelegate = SongRepositoryDelegate(songRepository)
        val lyricsDelegate = LyricsRepositoryDelegate(lyricsRepository)
        val artworkDelegate = ArtworkRepositoryDelegate(artworkRepository)
        facade = MusicRepositoryFacade(songDelegate, lyricsDelegate, artworkDelegate)
    }

    @Test
    fun `scanAll delegates to songRepository`() = runTest {
        coEvery { songRepository.scanAll() } returns listOf(testSong)
        val result = facade.scanAll()
        assertEquals(1, result.size)
        coVerify { songRepository.scanAll() }
    }

    @Test
    fun `scanFolder delegates to songRepository`() = runTest {
        coEvery { songRepository.scanFolder(any()) } returns listOf(testSong)
        val result = facade.scanFolder("/music")
        assertEquals(1, result.size)
        coVerify { songRepository.scanFolder("/music") }
    }

    @Test
    fun `deleteSong delegates to songRepository`() = runTest {
        coEvery { songRepository.deleteSong(any()) } returns true
        assertTrue(facade.deleteSong(testSong))
        coVerify { songRepository.deleteSong(testSong) }
    }

    @Test
    fun `search delegates to songRepository`() = runTest {
        coEvery { songRepository.search(any()) } returns listOf(testSong)
        val result = facade.search("测试")
        assertEquals(1, result.size)
        coVerify { songRepository.search("测试") }
    }

    @Test
    fun `updateSongTags delegates to songRepository`() = runTest {
        coEvery { songRepository.updateSongTags(any(), any(), any(), any(), any(), any()) } returns true
        assertTrue(facade.updateSongTags(testSong, "新标题", "新歌手", "专辑", 2024, "流行"))
        coVerify { songRepository.updateSongTags(testSong, "新标题", "新歌手", "专辑", 2024, "流行") }
    }

    @Test
    fun `loadLyrics delegates to lyricsRepository`() = runTest {
        coEvery { lyricsRepository.loadLyrics(any()) } returns ("lrc" to "text")
        val result = facade.loadLyrics(1L)
        assertEquals("lrc", result?.first)
        coVerify { lyricsRepository.loadLyrics(1L) }
    }

    @Test
    fun `saveLyrics delegates to lyricsRepository`() = runTest {
        coEvery { lyricsRepository.saveLyrics(any(), any(), any()) } returns Unit
        facade.saveLyrics(1L, "lrc", "text")
        coVerify { lyricsRepository.saveLyrics(1L, "lrc", "text") }
    }

    @Test
    fun `generateDefaultCoverForSong delegates to artworkRepository`() = runTest {
        coEvery { artworkRepository.generateDefaultCoverForSong(any()) } returns true
        assertTrue(facade.generateDefaultCoverForSong(testSong))
        coVerify { artworkRepository.generateDefaultCoverForSong(testSong) }
    }

    @Test
    fun `songs flow is exposed from songRepository`() {
        assertEquals(1, facade.songs.value.size)
    }
}
