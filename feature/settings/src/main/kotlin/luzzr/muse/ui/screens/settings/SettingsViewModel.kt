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
import luzzr.muse.data.tag.AudioFileHealthCheckUseCase
import luzzr.muse.data.tag.AudioHealthProgress
import luzzr.muse.data.tag.RenameProgress
import luzzr.muse.data.tag.RenameSongsToTagUseCase
import luzzr.muse.ui.state.ShizukuPermissionController
import luzzr.muse.ui.state.StoragePermissionController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val libraryScanController: LibraryScanController,
    private val themePreferenceController: ThemePreferenceController,
    private val defaultCoverGenerationController: DefaultCoverGenerationController,
    private val storagePermissionController: StoragePermissionController,
    private val shizukuPermissionController: ShizukuPermissionController,
    private val renameSongsToTagUseCase: RenameSongsToTagUseCase,
    private val audioFileHealthCheckUseCase: AudioFileHealthCheckUseCase
) : ViewModel() {

    val songs: StateFlow<List<Song>> = libraryScanController.songs
    val isScanning: StateFlow<Boolean> = libraryScanController.isScanning
    val scanProgress: StateFlow<Int> = libraryScanController.scanProgress
    val scanStats: StateFlow<ScanStats?> = libraryScanController.scanStats

    val isDarkTheme: StateFlow<Boolean> = themePreferenceController.isDarkTheme
    val themeMode: StateFlow<luzzr.muse.domain.preferences.ThemeMode> = themePreferenceController.themeMode
    val isDarkThemeSupported: Boolean = true

    val coverGenState: StateFlow<CoverGenState> = defaultCoverGenerationController.coverGenState
    private val _hasFullFileAccess = MutableStateFlow(storagePermissionController.hasFullFileAccess())
    val hasFullFileAccess: StateFlow<Boolean> = _hasFullFileAccess.asStateFlow()

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

    fun refreshPermissionState() {
        _hasFullFileAccess.value = storagePermissionController.hasFullFileAccess()
    }

    fun requestFullFileAccess() {
        storagePermissionController.requestFullFileAccess()
    }

    private val _shizukuAvailable = MutableStateFlow(shizukuPermissionController.isAvailable())
    val shizukuAvailable: StateFlow<Boolean> = _shizukuAvailable.asStateFlow()

    private val _shizukuGranted = MutableStateFlow(shizukuPermissionController.isGranted())
    val shizukuGranted: StateFlow<Boolean> = _shizukuGranted.asStateFlow()

    fun requestShizukuPermission() {
        shizukuPermissionController.requestGrant()
    }

    fun refreshShizukuState() {
        _shizukuAvailable.value = shizukuPermissionController.isAvailable()
        _shizukuGranted.value = shizukuPermissionController.isGranted()
    }

    private val _renameProgress = MutableStateFlow<RenameProgress?>(null)
    val renameProgress: StateFlow<RenameProgress?> = _renameProgress.asStateFlow()

    private val _audioHealthProgress = MutableStateFlow<AudioHealthProgress?>(null)
    val audioHealthProgress: StateFlow<AudioHealthProgress?> = _audioHealthProgress.asStateFlow()

    fun renameAllSongsToTags() {
        viewModelScope.launch {
            val list = libraryScanController.songs.value
            if (list.isEmpty()) return@launch
            renameSongsToTagUseCase(list) { progress ->
                _renameProgress.value = progress
            }
        }
    }

    fun dismissRenameResult() {
        _renameProgress.value = null
    }

    fun checkAudioFileHealth() {
        viewModelScope.launch {
            val list = libraryScanController.songs.value
            if (list.isEmpty()) return@launch
            val result = audioFileHealthCheckUseCase(list) { progress ->
                _audioHealthProgress.value = progress
            }
            _audioHealthProgress.value = result
        }
    }

    fun dismissAudioHealthResult() {
        _audioHealthProgress.value = null
    }
}
