package luzzr.muse.ui.state

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import luzzr.muse.data.model.Song
import luzzr.muse.data.network.LrcLine
import luzzr.muse.data.network.LyricsFetcher
import luzzr.muse.data.repository.MusicRepositoryFacade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsStateHolderTest {

    private lateinit var holder: LyricsStateHolder
    private val musicRepo: MusicRepositoryFacade = mockk(relaxed = true)
    private val lyricsFetcher: LyricsFetcher = mockk(relaxed = true)
    private val mockUri: android.net.Uri = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testSong = Song(id = 1, title = "歌", artist = "歌手", album = "专辑", uri = mockUri, duration = 240000)

    private val sampleLines = listOf(
        LrcLine(0L, "第一行"),
        LrcLine(5000L, "第二行"),
        LrcLine(10000L, "第三行")
    )

    @Before
    fun setup() {
        holder = LyricsStateHolder(musicRepo, lyricsFetcher)
    }

    @Test
    fun `trackLineProgress sets correct line index and progress`() {
        holder.trackLineProgress(sampleLines, 7000L, 0L)
        assertEquals(1, holder.currentLyricLine.value)
        assertEquals(0.4f, holder.lineProgress.value, 0.01f)
    }

    @Test
    fun `trackLineProgress does nothing for empty lines`() {
        holder.trackLineProgress(emptyList(), 5000L, 0L)
        assertEquals(-1, holder.currentLyricLine.value)
    }

    @Test
    fun `trackLineProgress applies offset`() {
        holder.trackLineProgress(sampleLines, 3000L, 2000L)
        assertEquals(1, holder.currentLyricLine.value)
    }

    @Test
    fun `trackLineProgress sets 1f for last line`() {
        holder.trackLineProgress(sampleLines, 12000L, 0L)
        assertEquals(1f, holder.lineProgress.value, 0.01f)
    }

    @Test
    fun `loadLyrics loads from DB when synced available`() = testScope.runTest {
        coEvery { musicRepo.loadLyrics(1L) } returns ("[00:05.000]歌词行" to "歌词行")
        coEvery { musicRepo.loadLyricsOffset(1L) } returns 0L
        every { lyricsFetcher.restoreToCache(any(), any()) } returns Unit

        holder.loadLyrics(testSong)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(holder.lyrics.value.isNotEmpty())
        assertNull(holder.lyricsError.value)
    }

    @Test
    fun `loadLyrics falls back to fetch when DB returns null`() = testScope.runTest {
        coEvery { musicRepo.loadLyrics(1L) } returns null
        coEvery { lyricsFetcher.fetchSync(any(), any(), any(), any()) } returns null

        holder.loadLyrics(testSong)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("未找到同步歌词", holder.lyricsError.value)
    }

    @Test
    fun `clear resets all state`() {
        holder.clear()
        assertTrue(holder.lyrics.value.isEmpty())
        assertEquals(-1, holder.currentLyricLine.value)
        assertNull(holder.lyricsError.value)
    }
}
