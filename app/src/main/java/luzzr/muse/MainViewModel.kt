package luzzr.muse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.preferences.ThemePreferenceController
import luzzr.muse.domain.preferences.NavigationPreferenceController
import luzzr.muse.domain.usecase.LoadLibraryUseCase
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Main ViewModel that handles high-level app state and navigation.
 *
 * Responsibilities:
 * - Playback state observation (currentSong, isPlaying, progress, duration, shuffleMode)
 * - Theme preference
 * - Library loading orchestration (via LoadLibraryUseCase)
 *
 * Note: Playback actions are delegated to PlaybackActionController.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val playbackActionController: PlaybackActionController,
    private val themePreferenceController: ThemePreferenceController,
    private val navigationPreferenceController: NavigationPreferenceController,
    private val loadLibraryUseCase: LoadLibraryUseCase
) : ViewModel() {

    // Playback state - derived from PlaybackController
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

    /** Leaf-only progress ratio — read without collecting to avoid scaffold recomposition. */
    fun progressRatio(): Float {
        val state = playbackController.state.value
        val d = state.durationMs
        return if (d > 0L) (state.positionMs.toFloat() / d).coerceIn(0f, 1f) else 0f
    }

    // Theme preference
    val isDarkTheme = themePreferenceController.isDarkTheme
    val isAudiobookVisible: StateFlow<Boolean> = navigationPreferenceController.isAudiobookVisible

    // Playback actions
    fun togglePlayPause() {
        playbackActionController.togglePlayPause()
    }

    /**
     * Load the music library.
     * Uses LoadLibraryUseCase which handles the logic:
     * - If database is empty, scan for songs
     * - Otherwise, generate missing album artwork
     */
    fun loadLibrary() {
        viewModelScope.launch {
            loadLibraryUseCase()
        }
    }
}
