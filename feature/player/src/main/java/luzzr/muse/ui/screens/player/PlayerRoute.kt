package luzzr.muse.ui.screens.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.ui.components.rememberReduceMotion
import luzzr.muse.ui.state.asString

@Composable
fun PlayerRoute(
    viewModel: PlayerViewModel = hiltViewModel(),
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {},
    openQueueOnStart: Boolean = false
) {
    // 低频状态：切歌/播放态/列表等
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val floatingLyricsEnabled by viewModel.floatingLyricsEnabled.collectAsStateWithLifecycle()

    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by viewModel.lyricsError.collectAsStateWithLifecycle()
    val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsStateWithLifecycle()

    val sleepTimerMode by viewModel.sleepTimer.activeMode.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimer.remainingMs.collectAsStateWithLifecycle()

    val reduceMotion = rememberReduceMotion()

    // 高频状态绝不在此 collect：progress / lyricLine / positionMs
    // 叶子组件用 provider 每帧/按需读取，避免整页 120 次重组
    val progressProvider = remember(viewModel) {
        { viewModel.progress.value }
    }
    val currentLyricLineProvider = remember(viewModel) {
        { viewModel.currentLyricLine.value }
    }
    val positionProvider = remember(viewModel) {
        { viewModel.positionMs.value }
    }

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var dialogState by remember {
        mutableStateOf(
            if (openQueueOnStart) PlayerDialogState.Queue else PlayerDialogState.None
        )
    }

    PlayerScreen(
        currentSong = currentSong,
        isPlaying = isPlaying,
        progressProvider = progressProvider,
        duration = duration,
        repeatMode = repeatMode,
        shuffleMode = shuffleMode,
        playlist = playlist,
        lyrics = lyrics,
        currentLyricLineProvider = currentLyricLineProvider,
        positionProvider = positionProvider,
        lyricsLoading = lyricsLoading,
        lyricsError = lyricsError?.asString(),
        lyricsOffsetMs = lyricsOffsetMs,
        sleepTimerMode = sleepTimerMode,
        sleepTimerRemaining = sleepTimerRemaining,
        showLyrics = showLyrics,
        dialogState = dialogState,
        floatingLyricsEnabled = floatingLyricsEnabled,
        innerPadding = innerPadding,
        onBack = onBack,
        onToggleLyrics = { showLyrics = !showLyrics },
        onRefreshLyrics = { viewModel.resetLyrics() },
        onShowQueue = { dialogState = PlayerDialogState.Queue },
        onToggleFloatingLyrics = { viewModel.toggleFloatingLyrics() },
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSkipNext = { viewModel.skipToNext() },
        onSkipPrevious = { viewModel.skipToPrevious() },
        onSeek = { viewModel.seekTo(it) },
        onToggleRepeat = { viewModel.cyclePlayMode() },
        onShowSleepTimer = { dialogState = PlayerDialogState.SleepTimer },
        onDismissDialog = { dialogState = PlayerDialogState.None },
        onSetSleepTimer = { mode, _ -> viewModel.startSleepTimer(mode) },
        onClearSleepTimer = { viewModel.stopSleepTimer() },
        onAdjustLyricsOffset = { viewModel.adjustLyricsOffset(it) },
        onCalibrateLyricsOffset = { viewModel.calibrateLyricsOffset(it) },
        onResetLyricsOffset = { viewModel.resetLyricsOffset() },
        onPlaySongAtIndex = { viewModel.playSongAtIndex(it) },
        reduceMotion = reduceMotion
    )
}
