package luzzr.muse.player

import luzzr.muse.domain.model.LrcLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Singleton

@Singleton
class FloatingLyricsStateHolder @javax.inject.Inject constructor() {

    private val _floatingLyricsEnabled = MutableStateFlow(false)
    val floatingLyricsEnabled: StateFlow<Boolean> = _floatingLyricsEnabled.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val currentLyrics: StateFlow<List<LrcLine>> = _currentLyrics.asStateFlow()

    private val _currentLyricLine = MutableStateFlow(-1)
    val currentLyricLine: StateFlow<Int> = _currentLyricLine.asStateFlow()

    private val _lyricsOffsetMs = MutableStateFlow(0L)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    private val _controlsVisible = MutableStateFlow(false)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    fun updateFloatingLyricsEnabled(enabled: Boolean) {
        _floatingLyricsEnabled.value = enabled
        if (!enabled) {
            _isLocked.value = false
            _controlsVisible.value = false
        }
    }

    fun toggleFloatingLyrics() {
        updateFloatingLyricsEnabled(!_floatingLyricsEnabled.value)
    }

    fun updateLocked(locked: Boolean) {
        _isLocked.value = locked
    }

    fun updateCurrentLyrics(lyrics: List<LrcLine>) {
        _currentLyrics.value = lyrics
        if (lyrics.isEmpty()) _currentLyricLine.value = -1
    }

    fun updateCurrentLyricLine(line: Int) {
        _currentLyricLine.value = line
    }

    fun updateLyricsOffset(offsetMs: Long) {
        _lyricsOffsetMs.value = offsetMs
    }

    fun updateControlsVisible(visible: Boolean) {
        _controlsVisible.value = visible
    }
}
