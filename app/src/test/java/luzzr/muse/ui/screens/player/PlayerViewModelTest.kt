package luzzr.muse.ui.screens.player

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.domain.usecase.PlayerControlUseCase
import luzzr.muse.player.PlayerState
import luzzr.muse.ui.state.LyricsStateHolder
import luzzr.muse.ui.state.SessionRestoreManager
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
    private val playerState: PlayerState = mockk(relaxed = true)
    private val musicRepo: MusicRepositoryFacade = mockk(relaxed = true)
    private val lyricsHolder: LyricsStateHolder = mockk(relaxed = true)
    private val sessionRestoreManager: SessionRestoreManager = mockk(relaxed = true)
    private val playerControlUseCase: PlayerControlUseCase = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val mockCurrentSong = MutableStateFlow<Song?>(null)
    private val mockIsPlaying = MutableStateFlow(false)
    private val mockCurrentPlaylist = MutableStateFlow<List<Song>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { playerState.currentSong } returns mockCurrentSong
        every { playerState.isPlaying } returns mockIsPlaying
        every { playerState.currentPlaylist } returns mockCurrentPlaylist
        every { playerState.progress } returns MutableStateFlow(0L)
        every { playerState.duration } returns MutableStateFlow(0L)
        every { playerState.repeatMode } returns MutableStateFlow(0)
        every { playerState.shuffleMode } returns MutableStateFlow(false)
        every { lyricsHolder.lyrics } returns MutableStateFlow(emptyList())
        every { lyricsHolder.currentLyricLine } returns MutableStateFlow(-1)
        every { lyricsHolder.lineProgress } returns MutableStateFlow(0f)
        every { lyricsHolder.lyricsLoading } returns MutableStateFlow(false)
        every { lyricsHolder.lyricsError } returns MutableStateFlow(null)
        every { lyricsHolder.lyricsOffsetMs } returns MutableStateFlow(0L)
        coEvery { sessionRestoreManager.restoreIfNeeded() } returns Unit

        viewModel = PlayerViewModel(
            playerState, musicRepo, lyricsHolder, sessionRestoreManager, playerControlUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `togglePlayPause calls playerControlUseCase when playlist not empty`() = runTest {
        mockCurrentPlaylist.value = listOf(mockk(relaxed = true))

        viewModel.togglePlayPause()

        verify { playerControlUseCase.togglePlayPause() }
    }

    @Test
    fun `skipToNext calls playerControlUseCase when playlist not empty`() = runTest {
        mockCurrentPlaylist.value = listOf(mockk(relaxed = true))

        viewModel.skipToNext()

        verify { playerControlUseCase.skipToNext() }
    }

    @Test
    fun `skipToPrevious calls playerControlUseCase when playlist not empty`() = runTest {
        mockCurrentPlaylist.value = listOf(mockk(relaxed = true))

        viewModel.skipToPrevious()

        verify { playerControlUseCase.skipToPrevious() }
    }

    @Test
    fun `playSongAtIndex calls playerControlUseCase with correct index`() = runTest {
        val songs = listOf<Song>(mockk(relaxed = true), mockk(relaxed = true))
        mockCurrentPlaylist.value = songs

        viewModel.playSongAtIndex(1)

        verify { playerControlUseCase.playSongAtIndex(songs, 1) }
    }

    @Test
    fun `togglePlayPause triggers restore when playlist empty and session exists`() = runTest {
        mockCurrentPlaylist.value = emptyList()
        every { playerState.hasSavedSession() } returns true

        viewModel.togglePlayPause()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sessionRestoreManager.restoreIfNeeded() }
    }

    @Test
    fun `skipToNext triggers restore when playlist empty and session exists`() = runTest {
        mockCurrentPlaylist.value = emptyList()
        every { playerState.hasSavedSession() } returns true

        viewModel.skipToNext()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sessionRestoreManager.restoreIfNeeded() }
    }
}
