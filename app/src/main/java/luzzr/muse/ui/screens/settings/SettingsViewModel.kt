package luzzr.muse.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import luzzr.muse.MuseApp
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.MusicRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository = (application as MuseApp).repository

    private val app = application as MuseApp

    val songs: StateFlow<List<Song>> = repository.songs
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scanProgress: StateFlow<Int> = repository.scanProgress
    val scanStats: StateFlow<MusicRepository.ScanStats?> = repository.scanStats

    val isDarkTheme: StateFlow<Boolean> = app.isDarkTheme
    val isDarkThemeSupported: Boolean = true

    val coverGenState: StateFlow<MusicRepository.CoverGenState> = repository.coverGenState

    fun toggleTheme() { app.toggleTheme() }

    fun scanAll() {
        viewModelScope.launch { repository.scanAll() }
    }

    fun scanFolder(path: String) {
        viewModelScope.launch { repository.scanFolder(path) }
    }

    fun generateAllDefaultCovers() {
        viewModelScope.launch {
            repository.generateDefaultCoversForAll()
            // Refresh current song's artwork in MiniPlayer/PlayerScreen
            val current = app.playerState.currentSong.value
            if (current != null) {
                val refreshed = repository.songs.value.find { it.id == current.id }
                if (refreshed != null && refreshed.artworkUri != null) {
                    app.playerState.updateCurrentSong(refreshed)
                }
            }
        }
    }
}
