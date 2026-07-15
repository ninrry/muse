package luzzr.muse.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.domain.model.Playlist
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.PlaylistRepository
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val currentSong: Song? = null
)

sealed interface PlaylistDetailUiEvent {
    data object PlayAll : PlaylistDetailUiEvent
    data object Shuffle : PlaylistDetailUiEvent
    data class PlaySong(val index: Int) : PlaylistDetailUiEvent
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackController,
    private val playbackActionController: PlaybackActionController
) : ViewModel() {

    private val _playlistId = MutableStateFlow<Long?>(null)
    private val playlistId: Long get() = _playlistId.value ?: 0L

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    fun loadPlaylist(id: Long) {
        _playlistId.value = id
        loadPlaylistData()
        observeCurrentSong()
    }

    private fun loadPlaylistData() {
        val id = _playlistId.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val playlist = playlistRepository.getPlaylist(id)
            _uiState.update { it.copy(playlist = playlist) }

            playlistRepository.getPlaylistSongs(id).collect { songs ->
                _uiState.update { it.copy(songs = songs, isLoading = false) }
            }
        }
    }

    private fun observeCurrentSong() {
        viewModelScope.launch {
            playbackController.state.collect { state ->
                _uiState.update { it.copy(currentSong = state.currentSong) }
            }
        }
    }

    fun onEvent(event: PlaylistDetailUiEvent) {
        when (event) {
            PlaylistDetailUiEvent.PlayAll -> playAll()
            PlaylistDetailUiEvent.Shuffle -> shuffle()
            is PlaylistDetailUiEvent.PlaySong -> playSong(event.index)
        }
    }

    private fun playAll() {
        val songs = _uiState.value.songs
        if (songs.isNotEmpty()) {
            playbackActionController.playSongAtIndex(songs, 0)
        }
    }

    private fun shuffle() {
        val songs = _uiState.value.songs
        if (songs.isNotEmpty()) {
            playbackActionController.playShuffled(songs)
        }
    }

    private fun playSong(index: Int) {
        val songs = _uiState.value.songs
        if (songs.isNotEmpty()) {
            playbackActionController.playSongAtIndex(songs, index)
        }
    }
}