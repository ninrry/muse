package luzzr.muse.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.domain.healthcheck.AudioFileHealthCheckUseCase
import luzzr.muse.domain.healthcheck.AudioHealthProgress
import luzzr.muse.domain.model.MusicUsageStats
import luzzr.muse.domain.model.ReadAlongUsageStats
import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.preferences.NavigationPreferenceController
import luzzr.muse.domain.preferences.ThemePreferenceController
import luzzr.muse.domain.repository.MediaUsageRepository
import luzzr.muse.domain.scanner.LibraryScanController
import luzzr.muse.ui.state.StoragePermissionController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val libraryScanController: LibraryScanController,
    private val themePreferenceController: ThemePreferenceController,
    private val navigationPreferenceController: NavigationPreferenceController,
    private val storagePermissionController: StoragePermissionController,
    private val audioFileHealthCheckUseCase: AudioFileHealthCheckUseCase,
    mediaUsageRepository: MediaUsageRepository
) : ViewModel() {

    val songs: StateFlow<List<Song>> = libraryScanController.songs
    val isScanning: StateFlow<Boolean> = libraryScanController.isScanning
    val scanProgress: StateFlow<Int> = libraryScanController.scanProgress
    val scanStats: StateFlow<ScanStats?> = libraryScanController.scanStats
    val musicStats: StateFlow<MusicUsageStats> = mediaUsageRepository.observeMusicStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicUsageStats())
    val readAlongStats: StateFlow<ReadAlongUsageStats> = mediaUsageRepository.observeReadAlongStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadAlongUsageStats())

    val isDarkTheme: StateFlow<Boolean> = themePreferenceController.isDarkTheme
    val themeMode: StateFlow<luzzr.muse.domain.preferences.ThemeMode> = themePreferenceController.themeMode
    val isDarkThemeSupported: Boolean = true
    val isAudiobookVisible: StateFlow<Boolean> = navigationPreferenceController.isAudiobookVisible

    private val _hasFullFileAccess = MutableStateFlow(storagePermissionController.hasFullFileAccess())
    val hasFullFileAccess: StateFlow<Boolean> = _hasFullFileAccess.asStateFlow()

    fun toggleTheme() {
        themePreferenceController.toggleTheme()
    }

    fun setThemeMode(mode: luzzr.muse.domain.preferences.ThemeMode) {
        themePreferenceController.setThemeMode(mode)
    }

    fun scanAll() {
        viewModelScope.launch { libraryScanController.scanAll() }
    }

    fun toggleAudiobookVisibility() {
        navigationPreferenceController.setAudiobookVisible(!isAudiobookVisible.value)
    }

    fun refreshPermissionState() {
        _hasFullFileAccess.value = storagePermissionController.hasFullFileAccess()
    }

    fun requestFullFileAccess() {
        storagePermissionController.requestFullFileAccess()
    }

    private val _audioHealthProgress = MutableStateFlow<AudioHealthProgress?>(null)
    val audioHealthProgress: StateFlow<AudioHealthProgress?> = _audioHealthProgress.asStateFlow()

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
