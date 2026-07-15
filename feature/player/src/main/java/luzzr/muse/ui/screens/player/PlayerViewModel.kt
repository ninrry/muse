package luzzr.muse.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.LrcLine
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val artworkRepository: ArtworkRepository,
    private val lyricsHolder: PlayerLyricsController,
    private val sessionRestoreManager: SessionRestoreController,
    private val playbackActionController: PlaybackActionController,
    private val playbackServiceStarter: luzzr.muse.media.PlaybackServiceStarter
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
    val floatingLyricsEnabled: StateFlow<Boolean> = playbackController.state
        .map { it.floatingLyricsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value.floatingLyricsEnabled)

    val sleepTimer = playbackController.sleepTimer

    val lyrics: StateFlow<List<LrcLine>> = lyricsHolder.lyrics
    val currentLyricLine: StateFlow<Int> = lyricsHolder.currentLyricLine
    val lineProgress: StateFlow<Float> = lyricsHolder.lineProgress
    val lyricsLoading: StateFlow<Boolean> = lyricsHolder.lyricsLoading
    val lyricsError: StateFlow<UiText?> = lyricsHolder.lyricsError
    val lyricsOffsetMs: StateFlow<Long> = lyricsHolder.lyricsOffsetMs

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
            currentSong.collect { song ->
                if (song != null) {
                    lyricsHolder.loadLyrics(song)
                    ensureArtwork(song)
                } else {
                    lyricsHolder.clear()
                    playbackController.publishLyrics(emptyList())
                }
            }
        }

        viewModelScope.launch {
            lyricsHolder.lyrics.collect { lyrics ->
                playbackController.publishLyrics(lyrics)
            }
        }
        viewModelScope.launch {
            lyricsHolder.currentLyricLine.collect { line ->
                playbackController.publishCurrentLyricLine(line)
            }
        }
    }

    private suspend fun ensureArtwork(song: Song) {
        MuseLog.d("PlayerViewModel", "ensureArtwork: generating default cover for song=${song.id}")
        artworkRepository.generateDefaultCoverForSong(song)
    }

    fun resetLyrics() {
        val song = currentSong.value ?: return
        lyricsHolder.resetLyrics(viewModelScope, song)
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        val song = currentSong.value ?: return
        lyricsHolder.adjustLyricsOffset(viewModelScope, song.id, deltaMs)
    }

    fun calibrateLyricsOffset(lyricTimestamp: Long) {
        val song = currentSong.value ?: return
        val currentProgress = progress.value
        // 修正符号：让 progress + offset = timestamp，即歌词时间戳 + 偏移 = 播放位置时显示该歌词
        val calculatedOffset = lyricTimestamp - currentProgress
        lyricsHolder.saveLyricsOffset(viewModelScope, song.id, calculatedOffset)
    }

    fun resetLyricsOffset() {
        val song = currentSong.value ?: return
        lyricsHolder.resetLyricsOffset(viewModelScope, song.id)
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

    fun cyclePlayMode() {
        val isShuffle = shuffleMode.value
        val isRepeatOne = repeatMode.value == PlaybackRepeatMode.ONE

        when {
            // 列表循环 -> 单曲循环
            !isShuffle && !isRepeatOne -> {
                setRepeatMode(PlaybackRepeatMode.ONE)
                if (shuffleMode.value) toggleShuffle()
            }
            // 单曲循环 -> 随机播放
            !isShuffle && isRepeatOne -> {
                if (!shuffleMode.value) toggleShuffle()
                setRepeatMode(PlaybackRepeatMode.ALL)
            }
            // 随机播放 / 其他 -> 列表循环
            else -> {
                if (shuffleMode.value) toggleShuffle()
                setRepeatMode(PlaybackRepeatMode.ALL)
            }
        }
    }

    fun playSongAtIndex(index: Int) {
        playbackActionController.playSongAtIndex(playbackController.state.value.playlist, index)
    }

    fun startSleepTimer(mode: SleepTimerMode) {
        val state = playbackController.state.value
        val trackRemaining = if (state.durationMs > 0 && state.positionMs < state.durationMs) {
            state.durationMs - state.positionMs
        } else {
            null
        }
        sleepTimer.start(mode, trackRemaining)
    }

    fun stopSleepTimer() {
        sleepTimer.stop()
    }

    fun toggleFloatingLyrics() {
        playbackServiceStarter.toggleFloatingLyrics()
    }
}
