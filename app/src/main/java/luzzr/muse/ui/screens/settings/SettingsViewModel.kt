package luzzr.muse.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.CoverGenState
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.data.repository.ScanStats
import luzzr.muse.di.ThemeManager
import luzzr.muse.domain.usecase.ScanAllSongsUseCase
import luzzr.muse.domain.usecase.ScanFolderUseCase
import luzzr.muse.player.PlayerState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val repository: MusicRepositoryFacade,
    private val playerState: PlayerState,
    private val themeManager: ThemeManager,
    private val scanAllSongsUseCase: ScanAllSongsUseCase,
    private val scanFolderUseCase: ScanFolderUseCase
) : AndroidViewModel(application) {

    val songs: StateFlow<List<Song>> = repository.songs
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scanProgress: StateFlow<Int> = repository.scanProgress
    val scanStats: StateFlow<ScanStats?> = repository.scanStats

    val isDarkTheme: StateFlow<Boolean> = themeManager.isDarkTheme
    val isDarkThemeSupported: Boolean = true

    val coverGenState: StateFlow<CoverGenState> = repository.coverGenState

    fun toggleTheme() {
        themeManager.toggleTheme()
    }

    fun scanAll() {
        viewModelScope.launch { scanAllSongsUseCase() }
    }

    fun scanFolder(path: String) {
        viewModelScope.launch { scanFolderUseCase(path) }
    }

    fun generateAllDefaultCovers() {
        viewModelScope.launch {
            repository.generateDefaultCoversForAll()
            // Refresh current song's artwork in MiniPlayer/PlayerScreen
            val current = playerState.currentSong.value
            if (current != null) {
                val refreshed = repository.songs.value.find { it.id == current.id }
                if (refreshed != null && refreshed.artworkUri != null) {
                    playerState.updateCurrentSong(refreshed)
                }
            }
        }
    }
}
