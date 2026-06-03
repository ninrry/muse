package luzzr.muse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.di.ThemeManager
import luzzr.muse.domain.usecase.ScanAllSongsUseCase
import luzzr.muse.player.PlayerState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    val playerState: PlayerState,
    val repository: MusicRepositoryFacade,
    val themeManager: ThemeManager,
    private val scanAllSongsUseCase: ScanAllSongsUseCase
) : ViewModel() {

    val currentSong: StateFlow<Song?> = playerState.currentSong
    val isPlaying: StateFlow<Boolean> = playerState.isPlaying
    val progress: StateFlow<Long> = playerState.progress
    val duration: StateFlow<Long> = playerState.duration
    val shuffleMode: StateFlow<Boolean> = playerState.shuffleMode
    val isDarkTheme = themeManager.isDarkTheme

    fun togglePlayPause() {
        playerState.togglePlayPause()
    }

    fun loadLibrary() {
        viewModelScope.launch {
            val loadedSongs = repository.loadFromDatabase()
            if (loadedSongs.isEmpty()) {
                scanAllSongsUseCase()
            } else {
                repository.generateMissingCovers()
            }
        }
    }
}
