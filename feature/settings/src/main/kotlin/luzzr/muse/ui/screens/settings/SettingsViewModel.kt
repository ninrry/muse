package luzzr.muse.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.domain.artwork.DefaultCoverGenerationController
import luzzr.muse.domain.model.CoverGenState
import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.preferences.ThemePreferenceController
import luzzr.muse.domain.scanner.LibraryScanController
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val libraryScanController: LibraryScanController,
    private val themePreferenceController: ThemePreferenceController,
    private val defaultCoverGenerationController: DefaultCoverGenerationController
) : ViewModel() {

    val songs: StateFlow<List<Song>> = libraryScanController.songs
    val isScanning: StateFlow<Boolean> = libraryScanController.isScanning
    val scanProgress: StateFlow<Int> = libraryScanController.scanProgress
    val scanStats: StateFlow<ScanStats?> = libraryScanController.scanStats

    val isDarkTheme: StateFlow<Boolean> = themePreferenceController.isDarkTheme
    val themeMode: StateFlow<luzzr.muse.domain.preferences.ThemeMode> = themePreferenceController.themeMode
    val isDarkThemeSupported: Boolean = true

    val coverGenState: StateFlow<CoverGenState> = defaultCoverGenerationController.coverGenState

    fun toggleTheme() {
        themePreferenceController.toggleTheme()
    }

    fun scanAll() {
        viewModelScope.launch { libraryScanController.scanAll() }
    }

    fun scanFolder(path: String) {
        viewModelScope.launch { libraryScanController.scanFolder(path) }
    }

    fun generateAllDefaultCovers() {
        viewModelScope.launch {
            defaultCoverGenerationController.generateAll()
        }
    }
}
