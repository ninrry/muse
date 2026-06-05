package luzzr.muse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.preferences.ThemePreferenceController
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.domain.usecase.ScanAllSongsUseCase
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val songRepository: SongRepository,
    private val artworkRepository: ArtworkRepository,
    themePreferenceController: ThemePreferenceController,
    private val playbackActionController: PlaybackActionController,
    private val scanAllSongsUseCase: ScanAllSongsUseCase
) : ViewModel() {

    val currentSong: StateFlow<Song?> = playbackController.state
        .map { it.currentSong }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value.currentSong)
    val isPlaying: StateFlow<Boolean> = playbackController.state
        .map { it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value.isPlaying)
    val progress: StateFlow<Long> = playbackController.state
        .map { it.positionMs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value.positionMs)
    val duration: StateFlow<Long> = playbackController.state
        .map { it.durationMs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value.durationMs)
    val shuffleMode: StateFlow<Boolean> = playbackController.state
        .map { it.shuffleEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value.shuffleEnabled)
    val isDarkTheme = themePreferenceController.isDarkTheme

    fun togglePlayPause() {
        playbackActionController.togglePlayPause()
    }

    fun loadLibrary() {
        viewModelScope.launch {
            val loadedSongs = songRepository.loadFromDatabase()
            if (loadedSongs.isEmpty()) {
                scanAllSongsUseCase()
            } else {
                artworkRepository.generateMissingCovers()
            }
        }
    }
}
