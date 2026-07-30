package luzzr.muse.ui.screens.player

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackState
import luzzr.muse.media.SleepTimerController
import luzzr.muse.ui.state.LyricsFileApplyResult
import luzzr.muse.ui.state.PlayerLyricsController
import luzzr.muse.ui.state.SessionRestoreController
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private lateinit var viewModel: PlayerViewModel
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val artworkRepository: ArtworkRepository = mockk(relaxed = true)
    private val lyricsHolder: PlayerLyricsController = mockk(relaxed = true)
    private val sessionRestoreManager: SessionRestoreController = mockk(relaxed = true)
    private val playbackActionController: PlaybackActionController = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val playbackState = MutableStateFlow(PlaybackState())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { playbackController.state } returns playbackState
        every { playbackController.sleepTimer } returns mockk<SleepTimerController>(relaxed = true)
        every { lyricsHolder.lyrics } returns MutableStateFlow(emptyList())
        every { lyricsHolder.currentLyricLine } returns MutableStateFlow(-1)
        every { lyricsHolder.positionMs } returns MutableStateFlow(0L)
        every { lyricsHolder.lyricsLoading } returns MutableStateFlow(false)
        every { lyricsHolder.lyricsError } returns MutableStateFlow(null)
        every { lyricsHolder.lyricsOffsetMs } returns MutableStateFlow(0L)
        coEvery { sessionRestoreManager.restoreIfNeeded() } returns Unit

        viewModel = PlayerViewModel(
            playbackController,
            artworkRepository,
            lyricsHolder,
            sessionRestoreManager,
            playbackActionController
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `togglePlayPause calls playback action controller when playlist not empty`() = runTest {
        playbackState.value = playbackState.value.copy(playlist = listOf(mockk(relaxed = true)))

        viewModel.togglePlayPause()

        verify { playbackActionController.togglePlayPause() }
    }

    @Test
    fun `skipToNext calls playback action controller when playlist not empty`() = runTest {
        playbackState.value = playbackState.value.copy(playlist = listOf(mockk(relaxed = true)))

        viewModel.skipToNext()

        verify { playbackActionController.skipToNext() }
    }

    @Test
    fun `skipToPrevious calls playback action controller when playlist not empty`() = runTest {
        playbackState.value = playbackState.value.copy(playlist = listOf(mockk(relaxed = true)))

        viewModel.skipToPrevious()

        verify { playbackActionController.skipToPrevious() }
    }

    @Test
    fun `playSongAtIndex calls playback action controller with correct index`() = runTest {
        val songs = listOf<Song>(mockk(relaxed = true), mockk(relaxed = true))
        playbackState.value = playbackState.value.copy(playlist = songs)

        viewModel.playSongAtIndex(1)

        verify { playbackActionController.playSongAtIndex(songs, 1) }
    }

    @Test
    fun `togglePlayPause triggers restore when playlist empty and session exists`() = runTest {
        playbackState.value = playbackState.value.copy(playlist = emptyList())
        every { playbackController.hasSavedSession() } returns true

        viewModel.togglePlayPause()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sessionRestoreManager.restoreIfNeeded() }
    }

    @Test
    fun `skipToNext triggers restore when playlist empty and session exists`() = runTest {
        playbackState.value = playbackState.value.copy(playlist = emptyList())
        every { playbackController.hasSavedSession() } returns true

        viewModel.skipToNext()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sessionRestoreManager.restoreIfNeeded() }
    }

    @Test
    fun `manual lyrics file is applied to current song and closes sheet`() = runTest {
        val song = Song(id = 42L, title = "Track", artist = "Artist")
        coEvery {
            lyricsHolder.applyLyricsFile(song, "content://lyrics/manual")
        } returns LyricsFileApplyResult.APPLIED
        playbackState.value = playbackState.value.copy(
            currentSong = song,
            playlist = listOf(song)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.applyLyricsFile("content://lyrics/manual")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            lyricsHolder.applyLyricsFile(song, "content://lyrics/manual")
        }
        assertFalse(viewModel.lyricsSearch.value.visible)
    }
}
