package luzzr.muse.ui.screens.home

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.domain.model.GreetingPeriod
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.PlaylistRepository
import luzzr.muse.domain.scanner.LibraryScanController
import luzzr.muse.domain.usecase.GetDailyRecommendationsUseCase
import luzzr.muse.domain.usecase.GetGreetingUseCase
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackState
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
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private val libraryScanController: LibraryScanController = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val playbackActionController: PlaybackActionController = mockk(relaxed = true)
    private val getGreetingUseCase: GetGreetingUseCase = mockk(relaxed = true)
    private val getDailyRecommendationsUseCase: GetDailyRecommendationsUseCase = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val songs = MutableStateFlow<List<Song>>(emptyList())
    private val isScanning = MutableStateFlow(false)
    private val scanProgress = MutableStateFlow(0)
    private val playbackState = MutableStateFlow(PlaybackState())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { libraryScanController.songs } returns songs
        every { libraryScanController.isScanning } returns isScanning
        every { libraryScanController.scanProgress } returns scanProgress
        every { playbackController.state } returns playbackState
        every { getGreetingUseCase() } returns GreetingPeriod.MORNING
        every { getDailyRecommendationsUseCase(any()) } returns emptyList()

        viewModel = HomeViewModel(
            libraryScanController = libraryScanController,
            playbackController = playbackController,
            playbackActionController = playbackActionController,
            getGreetingUseCase = getGreetingUseCase,
            getDailyRecommendationsUseCase = getDailyRecommendationsUseCase,
            playlistRepository = playlistRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onEvent PlaySong calls playback action controller`() = runTest(testDispatcher) {
        val loadedSongs = listOf(Song(id = 1L, title = "Song"))
        songs.value = loadedSongs
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(HomeUiEvent.PlaySong(loadedSongs[0]))

        verify { playbackActionController.playSongAtIndex(loadedSongs, 0) }
    }

    @Test
    fun `onEvent PlayShuffled calls playback action controller`() = runTest(testDispatcher) {
        val loadedSongs = listOf(Song(id = 1L, title = "Song"))
        songs.value = loadedSongs
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(HomeUiEvent.PlayShuffled)

        verify { playbackActionController.playShuffled(loadedSongs) }
    }

    @Test
    fun `onEvent ScanAll delegates to library scan controller`() = runTest(testDispatcher) {
        coEvery { libraryScanController.scanAll() } returns emptyList()

        viewModel.onEvent(HomeUiEvent.ScanAll)
        testScheduler.advanceUntilIdle()

        coVerify { libraryScanController.scanAll() }
    }
}
