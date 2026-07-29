package luzzr.muse.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.PlaylistRepository
import luzzr.muse.domain.scanner.LibraryScanController
import luzzr.muse.domain.usecase.GetDailyRecommendationsUseCase
import luzzr.muse.domain.usecase.GetGreetingUseCase
import luzzr.muse.feature.home.R
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import luzzr.muse.ui.state.UiText
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryScanController: LibraryScanController,
    private val playbackController: PlaybackController,
    private val playbackActionController: PlaybackActionController,
    private val playlistRepository: PlaylistRepository,
    private val getGreetingUseCase: GetGreetingUseCase,
    private val getDailyRecommendationsUseCase: GetDailyRecommendationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<HomeUiEffect>()
    val uiEffect: SharedFlow<HomeUiEffect> = _uiEffect.asSharedFlow()

    init {
        _uiState.update { it.copy(greeting = getGreetingUseCase()) }

        viewModelScope.launch {
            libraryScanController.songs.collect { songs ->
                val recommendation = getDailyRecommendationsUseCase(songs)
                _uiState.update { currentState ->
                    currentState.copy(
                        songs = songs,
                        dailyRecommendation = recommendation
                    )
                }
            }
        }

        viewModelScope.launch {
            libraryScanController.isScanning.collect { isScanning ->
                _uiState.update { it.copy(isScanning = isScanning) }
            }
        }

        viewModelScope.launch {
            libraryScanController.scanProgress.collect { progress ->
                _uiState.update { it.copy(scanProgress = progress) }
            }
        }

        viewModelScope.launch {
            playbackController.state.collect { state ->
                _uiState.update { it.copy(currentSong = state.currentSong) }
            }
        }

        // 加载歌单
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.ScanAll -> scanAll()
            HomeUiEvent.PlayAll -> playAll()
            HomeUiEvent.PlayShuffled -> playShuffled()
            is HomeUiEvent.PlaySong -> playSong(event.song)
            HomeUiEvent.RequestPermission -> requestPermission()
            is HomeUiEvent.CreatePlaylist -> createPlaylist(event.name)
            is HomeUiEvent.PlayPlaylist -> playPlaylist(event.playlistId)
        }
    }

    private fun scanAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                libraryScanController.scanAll()
            } catch (e: Exception) {
                MuseLog.e("HomeViewModel", "Full scan failed", e)
                _uiEffect.emit(HomeUiEffect.ShowSnackbar(UiText.Resource(R.string.home_scan_failed)))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun playAll(startIndex: Int = 0) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        startServiceAndPlay(songs, startIndex)
    }

    private fun playSong(song: Song) {
        val state = _uiState.value
        val queue = state.dailyRecommendation.ifEmpty { state.songs }
        val index = queue.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            startServiceAndPlay(queue, index)
        } else {
            val fullIndex = state.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            startServiceAndPlay(state.songs, fullIndex)
        }
    }

    private fun playShuffled() {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        playbackActionController.playShuffled(songs)
    }

    private fun playPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val songs = playlistRepository.getPlaylistSongsSync(playlistId)
            if (songs.isNotEmpty()) {
                playbackActionController.playShuffled(songs)
            }
        }
    }

    private fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistRepository.createPlaylist(name)
                _uiEffect.emit(HomeUiEffect.ShowSnackbar(UiText.Resource(R.string.playlist_created)))
            } catch (e: Exception) {
                MuseLog.e("HomeViewModel", "Create playlist failed", e)
                _uiEffect.emit(HomeUiEffect.ShowSnackbar(UiText.Resource(R.string.playlist_create_failed)))
            }
        }
    }

    private fun requestPermission() {
        // This is handled by the route owner so permission prompts stay app-scoped.
    }

    private fun startServiceAndPlay(songs: List<Song>, startIndex: Int = 0) {
        playbackActionController.playSongAtIndex(songs, startIndex)
    }
}
