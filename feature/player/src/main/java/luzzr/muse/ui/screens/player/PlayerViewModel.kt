package luzzr.muse.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackRepeatMode
import luzzr.muse.media.SleepTimerMode
import luzzr.muse.ui.state.PlayerLyricsController
import luzzr.muse.ui.state.SessionRestoreController
import luzzr.muse.ui.state.UiText
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val artworkRepository: ArtworkRepository,
    private val lyricsHolder: PlayerLyricsController,
    private val sessionRestoreManager: SessionRestoreController,
    private val playbackActionController: PlaybackActionController
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
    val repeatMode: StateFlow<PlaybackRepeatMode> = playbackController.state
        .map { it.repeatMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value.repeatMode)
    val shuffleMode: StateFlow<Boolean> = playbackController.state
        .map { it.shuffleEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value.shuffleEnabled)
    val currentPlaylist: StateFlow<List<Song>> = playbackController.state
        .map { it.playlist }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value.playlist)
    val sleepTimer = playbackController.sleepTimer

    val lyrics: StateFlow<List<LrcLine>> = lyricsHolder.lyrics
    val currentLyricLine: StateFlow<Int> = lyricsHolder.currentLyricLine
    val positionMs: StateFlow<Long> = lyricsHolder.positionMs
    val lyricsLoading: StateFlow<Boolean> = lyricsHolder.lyricsLoading
    val lyricsError: StateFlow<UiText?> = lyricsHolder.lyricsError
    val lyricsOffsetMs: StateFlow<Long> = lyricsHolder.lyricsOffsetMs

    data class LyricsSearchUi(
        val visible: Boolean = false,
        val isLoading: Boolean = false,
        val isApplying: Boolean = false,
        val results: List<LyricsResult> = emptyList(),
        val error: String? = null
    )

    private val _lyricsSearch = MutableStateFlow(LyricsSearchUi())
    val lyricsSearch: StateFlow<LyricsSearchUi> = _lyricsSearch.asStateFlow()
    private var lyricsSearchJob: Job? = null
    private var lyricsApplyJob: Job? = null

    init {
        lyricsHolder.bind(viewModelScope, progress)

        viewModelScope.launch {
            delay(800)
            if (currentPlaylist.value.isEmpty() && playbackController.hasSavedSession()) {
                MuseLog.w("PlayerViewModel", "init: playlist empty, restoring saved session")
                sessionRestoreManager.restoreIfNeeded()
            }
        }

        viewModelScope.launch {
            currentSong.collectLatest { song ->
                lyricsSearchJob?.cancel()
                lyricsApplyJob?.cancel()
                _lyricsSearch.value = LyricsSearchUi()
                if (song != null) {
                    lyricsHolder.loadLyrics(song)
                    ensureArtwork(song)
                } else {
                    lyricsHolder.clear()
                }
            }
        }

    }

    private suspend fun ensureArtwork(song: Song) {
        MuseLog.d("PlayerViewModel", "ensureArtwork: generating default cover for song=${song.id}")
        artworkRepository.generateDefaultCoverForSong(song)
    }

    fun resetLyrics() {
        val song = currentSong.value ?: return
        searchLyrics()
    }

    fun searchLyrics() {
        val song = currentSong.value ?: return
        val existing = _lyricsSearch.value
        if (existing.isLoading || existing.isApplying) return
        lyricsSearchJob?.cancel()
        lyricsSearchJob = viewModelScope.launch {
            _lyricsSearch.value = LyricsSearchUi(visible = true, isLoading = true)
            try {
                val results = lyricsHolder.searchLyricsCandidates(song)
                if (currentSong.value?.id != song.id) return@launch
                _lyricsSearch.value = LyricsSearchUi(
                    visible = true,
                    isLoading = false,
                    results = results,
                    error = if (results.isEmpty()) "empty" else null
                )
            } catch (e: Exception) {
                if (currentSong.value?.id != song.id) return@launch
                MuseLog.w("PlayerViewModel", "searchLyrics failed", e)
                _lyricsSearch.value = LyricsSearchUi(
                    visible = true,
                    isLoading = false,
                    error = e.message ?: "error"
                )
            }
        }
    }

    fun dismissLyricsSearch() {
        lyricsSearchJob?.cancel()
        lyricsSearchJob = null
        _lyricsSearch.value = LyricsSearchUi()
    }

    fun applyLyricsResult(result: LyricsResult) {
        val song = currentSong.value ?: return
        if (_lyricsSearch.value.isApplying) return
        lyricsApplyJob?.cancel()
        lyricsApplyJob = viewModelScope.launch {
            _lyricsSearch.update { it.copy(isApplying = true) }
            try {
                lyricsHolder.applyLyricsResult(song, result)
                if (currentSong.value?.id != song.id) return@launch
                _lyricsSearch.value = LyricsSearchUi()
            } catch (e: Exception) {
                if (currentSong.value?.id != song.id) return@launch
                MuseLog.w("PlayerViewModel", "applyLyricsResult failed", e)
                _lyricsSearch.update {
                    it.copy(isApplying = false, error = e.message)
                }
            }
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        val song = currentSong.value ?: return
        lyricsHolder.adjustLyricsOffset(viewModelScope, song.id, deltaMs)
    }

    fun calibrateLyricsOffset(lyricTimestamp: Long) {
        val song = currentSong.value ?: return
        val currentProgress = progress.value
        val calculatedOffset = lyricTimestamp - currentProgress
        // 仅改内存+防抖落库，完成校正时再写入文件
        lyricsHolder.saveLyricsOffset(viewModelScope, song.id, calculatedOffset, bakeToFile = false)
    }

    fun resetLyricsOffset() {
        val song = currentSong.value ?: return
        lyricsHolder.resetLyricsOffset(viewModelScope, song.id)
    }

    fun commitLyricsOffset() {
        val song = currentSong.value ?: return
        lyricsHolder.commitLyricsOffset(viewModelScope, song)
    }

    fun togglePlayPause() {
        MuseLog.d("PlayerViewModel", "togglePlayPause: request")
        if (playbackController.state.value.playlist.isEmpty() && playbackController.hasSavedSession()) {
            MuseLog.w("PlayerViewModel", "togglePlayPause: playlist empty, triggering restore")
            viewModelScope.launch { sessionRestoreManager.restoreIfNeeded() }
            return
        }
        playbackActionController.togglePlayPause()
    }

    fun skipToNext() {
        if (playbackController.state.value.playlist.isEmpty() && playbackController.hasSavedSession()) {
            viewModelScope.launch { sessionRestoreManager.restoreIfNeeded() }
            return
        }
        playbackActionController.skipToNext()
    }

    fun skipToPrevious() {
        if (playbackController.state.value.playlist.isEmpty() && playbackController.hasSavedSession()) {
            viewModelScope.launch { sessionRestoreManager.restoreIfNeeded() }
            return
        }
        playbackActionController.skipToPrevious()
    }

    fun seekTo(position: Long) {
        if (playbackController.state.value.playlist.isEmpty() && playbackController.hasSavedSession()) {
            viewModelScope.launch { sessionRestoreManager.restoreIfNeeded() }
            return
        }
        playbackActionController.seekTo(position)
    }

    fun setRepeatMode(mode: PlaybackRepeatMode) {
        playbackController.setRepeatMode(mode)
    }

    fun toggleShuffle() {
        playbackController.toggleShuffle()
    }

    /**
     * 播放模式三态循环：顺序(列表循环) → 乱序(随机) → 单曲循环 → 顺序
     */
    fun cyclePlayMode() {
        val isShuffle = shuffleMode.value
        val isRepeatOne = repeatMode.value == PlaybackRepeatMode.ONE

        when {
            // 顺序 → 乱序
            !isShuffle && !isRepeatOne -> {
                setRepeatMode(PlaybackRepeatMode.ALL)
                if (!shuffleMode.value) toggleShuffle()
            }
            // 乱序 → 单曲循环
            isShuffle -> {
                if (shuffleMode.value) toggleShuffle()
                setRepeatMode(PlaybackRepeatMode.ONE)
            }
            // 单曲循环 → 顺序
            else -> {
                if (shuffleMode.value) toggleShuffle()
                setRepeatMode(PlaybackRepeatMode.ALL)
            }
        }
    }

    fun playSongAtIndex(index: Int) {
        playbackActionController.playSongAtIndex(playbackController.state.value.playlist, index)
    }

    fun startSleepTimer(mode: SleepTimerMode, customMinutes: Int? = null) {
        val state = playbackController.state.value
        val trackRemaining = if (state.durationMs > 0 && state.positionMs < state.durationMs) {
            state.durationMs - state.positionMs
        } else {
            null
        }
        val customMs = customMinutes?.takeIf { it > 0 }?.let { it * 60_000L }
        sleepTimer.start(mode, trackRemaining, customMs)
    }

    fun stopSleepTimer() {
        sleepTimer.stop()
    }

}
