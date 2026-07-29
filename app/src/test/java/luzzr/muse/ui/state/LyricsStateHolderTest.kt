package luzzr.muse.ui.state

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import luzzr.muse.R
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.LyricsRepository
import luzzr.muse.domain.text.TextNormalizer
import luzzr.muse.domain.usecase.ClearLyricsCacheUseCase
import luzzr.muse.domain.usecase.FetchLyricsUseCase
import luzzr.muse.domain.usecase.RestoreLyricsCacheUseCase
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
    private val lyricsRepository: LyricsRepository = mockk(relaxed = true)
    private val fetchLyricsUseCase: FetchLyricsUseCase = mockk(relaxed = true)
    private val restoreLyricsCacheUseCase: RestoreLyricsCacheUseCase = mockk(relaxed = true)
    private val clearLyricsCacheUseCase: ClearLyricsCacheUseCase = mockk(relaxed = true)
    private val textNormalizer: TextNormalizer = mockk()
    private val metadataFileWriter: luzzr.muse.data.tag.MetadataFileWriter = mockk(relaxed = true)
    private val mockUri = "content://test/song"
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
        every { textNormalizer.toSimplified(any()) } answers { firstArg<String>() }
        holder = LyricsStateHolder(
            lyricsRepository = lyricsRepository,
            fetchLyricsUseCase = fetchLyricsUseCase,
            restoreLyricsCacheUseCase = restoreLyricsCacheUseCase,
            clearLyricsCacheUseCase = clearLyricsCacheUseCase,
            textNormalizer = textNormalizer,
            metadataFileWriter = metadataFileWriter
        )
    }

    @Test
    fun `trackLineProgress sets correct line index and position`() {
        holder.trackLineProgress(sampleLines, 7000L, 0L)
        assertEquals(1, holder.currentLyricLine.value)
        assertEquals(7000L, holder.positionMs.value)
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
    fun `trackLineProgress writes positionMs even on last line`() {
        holder.trackLineProgress(sampleLines, 12000L, 0L)
        assertEquals(2, holder.currentLyricLine.value)
        assertEquals(12000L, holder.positionMs.value)
    }

    @Test
    fun `loadLyrics loads from DB when synced available`() = testScope.runTest {
        coEvery { lyricsRepository.loadLyrics(1L) } returns ("[00:05.000]歌词行" to "歌词行")
        coEvery { lyricsRepository.loadLyricsOffset(1L) } returns 0L
        every { restoreLyricsCacheUseCase(any(), any()) } returns Unit

        holder.loadLyrics(testSong)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(holder.lyrics.value.isNotEmpty())
        assertNull(holder.lyricsError.value)
    }

    @Test
    fun `loadLyrics falls back to fetch when DB returns null`() = testScope.runTest {
        coEvery { lyricsRepository.loadLyrics(1L) } returns null
        coEvery { fetchLyricsUseCase(any(), any(), any(), any()) } returns null

        holder.loadLyrics(testSong)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { fetchLyricsUseCase(1L, "歌", "歌手", "专辑") }
        assertEquals(UiText.Resource(R.string.player_lyrics_not_found), holder.lyricsError.value)
    }

    @Test
    fun `clear resets all state`() {
        holder.clear()
        assertTrue(holder.lyrics.value.isEmpty())
        assertEquals(-1, holder.currentLyricLine.value)
        assertNull(holder.lyricsError.value)
        assertEquals(0L, holder.lyricsOffsetMs.value)
    }

    @Test
    fun `adjustLyricsOffset updates offset without rewriting timestamps`() = testScope.runTest {
        // Seed lyrics via reflection-free path: load from DB
        coEvery { lyricsRepository.loadLyrics(1L) } returns (
            "[00:00.000]第一行\n[00:05.000]第二行\n[00:10.000]第三行" to null
            )
        coEvery { lyricsRepository.loadLyricsOffset(1L) } returns 0L
        every { restoreLyricsCacheUseCase(any(), any()) } returns Unit
        holder.loadLyrics(testSong)
        testDispatcher.scheduler.advanceUntilIdle()

        val originalTimestamps = holder.lyrics.value.map { it.timestamp }
        holder.adjustLyricsOffset(testScope, 1L, 500L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(500L, holder.lyricsOffsetMs.value)
        assertEquals(originalTimestamps, holder.lyrics.value.map { it.timestamp })
        coVerify { lyricsRepository.saveLyricsOffset(1L, 500L) }
    }
}
