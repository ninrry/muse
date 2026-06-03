package luzzr.muse.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.model.Song
import luzzr.muse.data.network.LrcLine
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.player.PlayerState
import luzzr.muse.player.SleepTimerMode
import luzzr.muse.ui.state.LyricsStateHolder
import luzzr.muse.ui.state.SessionRestoreManager
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerState: PlayerState,
    private val musicRepo: MusicRepositoryFacade,
    private val lyricsHolder: LyricsStateHolder,
    private val sessionRestoreManager: SessionRestoreManager,
    private val playerControlUseCase: luzzr.muse.domain.usecase.PlayerControlUseCase
) : ViewModel() {

    val currentSong: StateFlow<Song?> = playerState.currentSong
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isPlaying: StateFlow<Boolean> = playerState.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val progress: StateFlow<Long> = playerState.progress
    val duration: StateFlow<Long> = playerState.duration
    val repeatMode: StateFlow<Int> = playerState.repeatMode
    val shuffleMode: StateFlow<Boolean> = playerState.shuffleMode
    val currentPlaylist: StateFlow<List<Song>> = playerState.currentPlaylist

    val sleepTimer = playerState.sleepTimer

    val lyrics: StateFlow<List<LrcLine>> = lyricsHolder.lyrics
    val currentLyricLine: StateFlow<Int> = lyricsHolder.currentLyricLine
    val lineProgress: StateFlow<Float> = lyricsHolder.lineProgress
    val lyricsLoading: StateFlow<Boolean> = lyricsHolder.lyricsLoading
    val lyricsError: StateFlow<String?> = lyricsHolder.lyricsError
    val lyricsOffsetMs: StateFlow<Long> = lyricsHolder.lyricsOffsetMs

    init {
        lyricsHolder.bind(viewModelScope, playerState.progress)

        viewModelScope.launch {
            delay(800)
            if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
                MuseLog.w("PlayerViewModel", "init: playlist empty, restoring saved session")
                sessionRestoreManager.restoreIfNeeded()
            }
        }

        viewModelScope.launch {
            playerState.currentSong.collect { song ->
                if (song != null) {
                    lyricsHolder.loadLyrics(song)
                    ensureArtwork(song)
                } else {
                    lyricsHolder.clear()
                    playerState.updateCurrentLyrics(emptyList())
                }
            }
        }

        viewModelScope.launch {
            lyricsHolder.lyrics.collect { lyrics ->
                playerState.updateCurrentLyrics(lyrics)
            }
        }
        viewModelScope.launch {
            lyricsHolder.currentLyricLine.collect { line ->
                playerState.updateCurrentLyricLine(line)
            }
        }
    }

    private suspend fun ensureArtwork(song: Song) {
        MuseLog.d("PlayerViewModel", "ensureArtwork: generating default cover for song=${song.id}")
        musicRepo.generateDefaultCoverForSong(song)
    }

    fun resetLyrics() {
        val song = currentSong.value ?: return
        lyricsHolder.resetLyrics(viewModelScope, song)
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        val song = currentSong.value ?: return
        lyricsHolder.adjustLyricsOffset(viewModelScope, song.id, deltaMs)
    }

    fun resetLyricsOffset() {
        val song = currentSong.value ?: return
        lyricsHolder.resetLyricsOffset(viewModelScope, song.id)
    }

    fun togglePlayPause() {
        MuseLog.d("PlayerViewModel", "togglePlayPause: request")
        if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
            MuseLog.w("PlayerViewModel", "togglePlayPause: playlist empty, triggering restore")
            viewModelScope.launch { sessionRestoreManager.restoreIfNeeded() }
            return
        }
        playerControlUseCase.togglePlayPause()
    }

    fun skipToNext() {
        if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
            viewModelScope.launch { sessionRestoreManager.restoreIfNeeded() }
            return
        }
        playerControlUseCase.skipToNext()
    }

    fun skipToPrevious() {
        if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
            viewModelScope.launch { sessionRestoreManager.restoreIfNeeded() }
            return
        }
        playerControlUseCase.skipToPrevious()
    }

    fun seekTo(position: Long) {
        if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
            viewModelScope.launch { sessionRestoreManager.restoreIfNeeded() }
            return
        }
        playerControlUseCase.seekTo(position)
    }

    fun setRepeatMode(mode: Int) {
        playerState.setRepeatMode(mode)
    }

    fun toggleShuffle() {
        playerState.toggleShuffle()
    }

    fun cyclePlayMode() {
        val isShuffle = shuffleMode.value
        val isRepeatOne = repeatMode.value == Player.REPEAT_MODE_ONE
        val isRepeatAll = repeatMode.value == Player.REPEAT_MODE_ALL && !isShuffle

        when {
            isRepeatAll -> {
                // 列表循环 -> 单曲循环
                setRepeatMode(Player.REPEAT_MODE_ONE)
                if (shuffleMode.value) toggleShuffle()
            }
            isRepeatOne -> {
                // 单曲循环 -> 随机播放
                if (!shuffleMode.value) toggleShuffle()
                setRepeatMode(Player.REPEAT_MODE_ALL)
            }
            else -> {
                // 随机播放 / 其他 -> 列表循环
                if (shuffleMode.value) toggleShuffle()
                setRepeatMode(Player.REPEAT_MODE_ALL)
            }
        }
    }

    fun playSongAtIndex(index: Int) {
        playerControlUseCase.playSongAtIndex(currentPlaylist.value, index)
    }

    fun startSleepTimer(mode: SleepTimerMode) {
        val currentDuration = duration.value
        val trackRemaining = if (currentDuration > 0 && progress.value < currentDuration) {
            currentDuration - progress.value
        } else {
            null
        }
        sleepTimer.start(mode, trackRemaining)
    }

    fun stopSleepTimer() {
        sleepTimer.stop()
    }
}
