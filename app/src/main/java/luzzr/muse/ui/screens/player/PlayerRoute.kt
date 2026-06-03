package luzzr.muse.ui.screens.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PlayerRoute(
    viewModel: PlayerViewModel = hiltViewModel(),
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {}
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progressState = viewModel.progress.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()

    // Lyrics state
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val currentLyricLineState = viewModel.currentLyricLine.collectAsStateWithLifecycle()
    val lineProgressState = viewModel.lineProgress.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by viewModel.lyricsError.collectAsStateWithLifecycle()
    val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsStateWithLifecycle()

    // Sleep timer state
    val sleepTimerMode by viewModel.sleepTimer.activeMode.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimer.remainingMs.collectAsStateWithLifecycle()

    // UI state
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var dialogState by rememberSaveable { mutableStateOf<PlayerDialogState>(PlayerDialogState.None) }

    // Convert types for PlayerScreen
    val progressFloat = if (duration > 0) (progressState.value.toFloat() / duration).coerceIn(0f, 1f) else 0f

    PlayerScreen(
        currentSong = currentSong,
        isPlaying = isPlaying,
        progress = progressFloat,
        duration = duration,
        repeatMode = repeatMode,
        shuffleMode = shuffleMode,
        playlist = playlist,
        lyrics = lyrics,
        currentLyricLine = currentLyricLineState.value,
        lineProgress = lineProgressState.value,
        lyricsLoading = lyricsLoading,
        lyricsError = lyricsError,
        lyricsOffsetMs = lyricsOffsetMs,
        sleepTimerMode = sleepTimerMode,
        sleepTimerRemaining = sleepTimerRemaining,
        showLyrics = showLyrics,
        dialogState = dialogState,
        innerPadding = innerPadding,
        onBack = onBack,
        onToggleLyrics = { showLyrics = !showLyrics },
        onRefreshLyrics = { viewModel.resetLyrics() },
        onShowQueue = { dialogState = PlayerDialogState.Queue },
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSkipNext = { viewModel.skipToNext() },
        onSkipPrevious = { viewModel.skipToPrevious() },
        onSeek = { viewModel.seekTo(it) },
        onToggleRepeat = { viewModel.cycleRepeatMode() },
        onToggleShuffle = { viewModel.toggleShuffle() },
        onShowSleepTimer = { dialogState = PlayerDialogState.SleepTimer },
        onDismissDialog = { dialogState = PlayerDialogState.None },
        onSetSleepTimer = { mode, _ -> viewModel.startSleepTimer(mode) },
        onClearSleepTimer = { viewModel.stopSleepTimer() },
        onAdjustLyricsOffset = { viewModel.adjustLyricsOffset(it) },
        onResetLyricsOffset = { viewModel.resetLyricsOffset() }
    )
}
