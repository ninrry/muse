package luzzr.muse.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.Playlist
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.PlaylistRepository
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    data class EditPlaylist(val name: String, val description: String) : PlaylistDetailUiEvent
    data object DeletePlaylist : PlaylistDetailUiEvent
    data class UpdateArtwork(val artworkUri: String?) : PlaylistDetailUiEvent
    data class RemoveSong(val songId: Long) : PlaylistDetailUiEvent
}

sealed interface PlaylistDetailUiEffect {
    data object NavigateBack : PlaylistDetailUiEffect
    data class ShowMessage(val message: String) : PlaylistDetailUiEffect
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

    private val _uiEffect = MutableSharedFlow<PlaylistDetailUiEffect>(extraBufferCapacity = 1)
    val uiEffect: SharedFlow<PlaylistDetailUiEffect> = _uiEffect.asSharedFlow()

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
            is PlaylistDetailUiEvent.EditPlaylist -> editPlaylist(event.name, event.description)
            PlaylistDetailUiEvent.DeletePlaylist -> deletePlaylist()
            is PlaylistDetailUiEvent.UpdateArtwork -> updateArtwork(event.artworkUri)
            is PlaylistDetailUiEvent.RemoveSong -> removeSong(event.songId)
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

    private fun editPlaylist(name: String, description: String) {
        val playlist = _uiState.value.playlist ?: return
        viewModelScope.launch {
            try {
                val updated = playlist.copy(
                    name = name,
                    description = description,
                    updatedAt = System.currentTimeMillis()
                )
                val result = playlistRepository.updatePlaylist(updated)
                if (result is luzzr.muse.core.result.OperationResult.Success) {
                    _uiState.update { it.copy(playlist = updated) }
                    _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("歌单已更新"))
                } else {
                    _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("更新失败"))
                }
            } catch (e: Exception) {
                MuseLog.e(TAG, "Failed to update playlist", e)
                _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("更新失败"))
            }
        }
    }

    private fun deletePlaylist() {
        val playlistId = _playlistId.value ?: return
        viewModelScope.launch {
            try {
                val result = playlistRepository.deletePlaylist(playlistId)
                if (result is luzzr.muse.core.result.OperationResult.Success) {
                    _uiEffect.emit(PlaylistDetailUiEffect.NavigateBack)
                } else {
                    _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("删除失败"))
                }
            } catch (e: Exception) {
                MuseLog.e(TAG, "Failed to delete playlist", e)
                _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("删除失败"))
            }
        }
    }

    private fun updateArtwork(artworkUri: String?) {
        val playlist = _uiState.value.playlist ?: return
        viewModelScope.launch {
            try {
                val updated = playlist.copy(
                    artworkUri = artworkUri,
                    updatedAt = System.currentTimeMillis()
                )
                val result = playlistRepository.updatePlaylist(updated)
                if (result is luzzr.muse.core.result.OperationResult.Success) {
                    _uiState.update { it.copy(playlist = updated) }
                    _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("封面已更新"))
                } else {
                    _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("封面更新失败"))
                }
            } catch (e: Exception) {
                MuseLog.e(TAG, "Failed to update playlist artwork", e)
                _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("封面更新失败"))
            }
        }
    }

    private fun removeSong(songId: Long) {
        val playlistId = _playlistId.value ?: return
        viewModelScope.launch {
            try {
                playlistRepository.removeSongFromPlaylist(playlistId, songId)
                _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("已从歌单移除"))
            } catch (e: Exception) {
                MuseLog.e(TAG, "Failed to remove song from playlist", e)
                _uiEffect.emit(PlaylistDetailUiEffect.ShowMessage("移除失败"))
            }
        }
    }

    private companion object {
        const val TAG = "PlaylistDetailVM"
    }
}
