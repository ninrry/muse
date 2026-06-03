package luzzr.muse.ui.screens.home

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.domain.usecase.PlayerControlUseCase
import luzzr.muse.domain.usecase.ScanAllSongsUseCase
import luzzr.muse.player.PlayerState
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
    private val application: Application = mockk(relaxed = true)
    private val repository: MusicRepositoryFacade = mockk(relaxed = true)
    private val playerState: PlayerState = mockk(relaxed = true)
    private val playerControlUseCase: PlayerControlUseCase = mockk(relaxed = true)
    private val scanAllSongsUseCase: ScanAllSongsUseCase = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val mockSongs = MutableStateFlow<List<Song>>(emptyList())
    private val mockIsScanning = MutableStateFlow(false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.songs } returns mockSongs
        every { repository.isScanning } returns mockIsScanning
        every { repository.scanProgress } returns MutableStateFlow(0)
        every { playerState.currentSong } returns MutableStateFlow(null)
        every { playerState.isPlaying } returns MutableStateFlow(false)

        viewModel = HomeViewModel(application, repository, playerState, playerControlUseCase, scanAllSongsUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onEvent PlaySong calls playerControlUseCase`() = runTest {
        val songs = listOf<Song>(mockk(relaxed = true))
        every { repository.songs } returns MutableStateFlow(songs)

        viewModel.onEvent(HomeUiEvent.PlaySong(0))

        io.mockk.verify { playerControlUseCase.playSongAtIndex(songs, 0) }
    }
}
