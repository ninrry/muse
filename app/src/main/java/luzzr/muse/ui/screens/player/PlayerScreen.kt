package luzzr.muse.ui.screens.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import luzzr.muse.R
import luzzr.muse.data.model.Song
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.player.SleepTimerMode
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.WindowSize
import luzzr.muse.ui.theme.currentWindowSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    progress: Float,
    duration: Long,
    repeatMode: Int,
    shuffleMode: Boolean,
    playlist: List<Song>,
    lyrics: List<LrcLine>,
    currentLyricLine: Int,
    lineProgress: Float,
    lyricsLoading: Boolean,
    lyricsError: String?,
    lyricsOffsetMs: Long,
    sleepTimerMode: SleepTimerMode?,
    sleepTimerRemaining: Long?,
    showLyrics: Boolean,
    dialogState: PlayerDialogState,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {},
    onToggleLyrics: () -> Unit = {},
    onRefreshLyrics: () -> Unit = {},
    onShowQueue: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    onDismissDialog: () -> Unit = {},
    onSetSleepTimer: (SleepTimerMode, Int?) -> Unit = { _, _ -> },
    onClearSleepTimer: () -> Unit = {},
    onAdjustLyricsOffset: (Long) -> Unit = {},
    onResetLyricsOffset: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PlayerTopBar(
                showLyrics = showLyrics,
                onBack = onBack,
                onToggleLyrics = onToggleLyrics,
                onRefreshLyrics = onRefreshLyrics,
                onShowQueue = onShowQueue
            )
        }
    ) { padding ->
        if (currentSong == null) {
            EmptyPlayerState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val windowSize = currentWindowSize()

        when (windowSize) {
            WindowSize.Medium, WindowSize.Expanded -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = AppSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(AppSpacing.md))
                        AlbumArtSection(song = currentSong, isPlaying = isPlaying)
                        Spacer(Modifier.height(AppSpacing.md))
                        SongInfoSection(song = currentSong)
                        Spacer(Modifier.height(AppSpacing.xs))
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxSize()
                            .padding(horizontal = AppSpacing.lg)
                    ) {
                        if (showLyrics) {
                            LyricsPanel(
                                song = currentSong,
                                lyrics = lyrics,
                                currentLyricLineProvider = { currentLyricLine },
                                lineProgressProvider = { lineProgress },
                                lyricsLoading = lyricsLoading,
                                lyricsError = lyricsError,
                                lyricsOffsetMs = lyricsOffsetMs,
                                showLyricsOffset = dialogState == PlayerDialogState.LyricsOffset,
                                onToggleLyricsOffset = {
                                    if (dialogState == PlayerDialogState.LyricsOffset) {
                                        onDismissDialog()
                                    } else {
                                        // This would need to be handled by the caller
                                    }
                                },
                                onAdjustLyricsOffset = onAdjustLyricsOffset,
                                onResetLyricsOffset = onResetLyricsOffset,
                                onSeek = onSeek,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        PlaybackControls(
                            modifier = Modifier.padding(horizontal = AppSpacing.lg),
                            duration = duration,
                            progressProvider = { (progress * duration).toLong() },
                            isPlaying = isPlaying,
                            repeatMode = repeatMode,
                            shuffleMode = shuffleMode,
                            sleepTimerMode = sleepTimerMode,
                            sleepTimerRemaining = sleepTimerRemaining,
                            onSeek = onSeek,
                            onTogglePlayPause = onTogglePlayPause,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onCycleRepeat = onToggleRepeat,
                            onToggleShuffle = onToggleShuffle,
                            onShowSleepTimer = onShowSleepTimer,
                            onShowQueue = onShowQueue,
                            sleepTimerFormat = { /* This would need to be provided */ "" }
                        )
                        Spacer(Modifier.height(AppSpacing.sm))
                    }
                }
            }
            WindowSize.Compact -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    if (showLyrics) {
                        LyricsPanel(
                            song = currentSong,
                            lyrics = lyrics,
                            currentLyricLineProvider = { currentLyricLine },
                            lineProgressProvider = { lineProgress },
                            lyricsLoading = lyricsLoading,
                            lyricsError = lyricsError,
                            lyricsOffsetMs = lyricsOffsetMs,
                            showLyricsOffset = dialogState == PlayerDialogState.LyricsOffset,
                            onToggleLyricsOffset = {
                                if (dialogState == PlayerDialogState.LyricsOffset) {
                                    onDismissDialog()
                                } else {
                                    // This would need to be handled by the caller
                                }
                            },
                            onAdjustLyricsOffset = onAdjustLyricsOffset,
                            onResetLyricsOffset = onResetLyricsOffset,
                            onSeek = onSeek,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = AppSpacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(AppSpacing.md))
                            AlbumArtSection(song = currentSong, isPlaying = isPlaying)
                            Spacer(Modifier.height(AppSpacing.md))
                            SongInfoSection(song = currentSong)
                            Spacer(Modifier.height(AppSpacing.xs))
                        }
                    }

                    PlaybackControls(
                        modifier = Modifier.padding(horizontal = AppSpacing.lg),
                        duration = duration,
                        progressProvider = { (progress * duration).toLong() },
                        isPlaying = isPlaying,
                        repeatMode = repeatMode,
                        shuffleMode = shuffleMode,
                        sleepTimerMode = sleepTimerMode,
                        sleepTimerRemaining = sleepTimerRemaining,
                        onSeek = onSeek,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onCycleRepeat = onToggleRepeat,
                        onToggleShuffle = onToggleShuffle,
                        onShowSleepTimer = onShowSleepTimer,
                        onShowQueue = onShowQueue,
                        sleepTimerFormat = { /* This would need to be provided */ "" }
                    )

                    Spacer(Modifier.height(AppSpacing.sm))
                }
            }
        }
    }

    // Dialog overlays - managed by PlayerDialogState
    when (dialogState) {
        PlayerDialogState.Queue -> {
            QueueBottomSheet(
                playlist = playlist,
                currentSong = currentSong,
                onPlaySong = { /* This would need to be handled */ },
                onDismiss = onDismissDialog
            )
        }
        PlayerDialogState.SleepTimer -> {
            SleepTimerDialog(
                currentMode = sleepTimerMode,
                onSelect = { mode ->
                    if (mode == SleepTimerMode.OFF) {
                        onClearSleepTimer()
                    } else {
                        onSetSleepTimer(mode, null)
                    }
                    onDismissDialog()
                },
                onDismiss = onDismissDialog
            )
        }
        PlayerDialogState.LyricsOffset -> { /* handled inline in LyricsPanel */ }
        PlayerDialogState.None -> { /* nothing */ }
    }
}

@Composable
private fun EmptyPlayerState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Breathing animation on empty player icon
            val infiniteTransition = rememberInfiniteTransition(label = "empty_breathe")
            val breatheScale by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathe_scale"
            )
            val breatheAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.65f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathe_alpha"
            )
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier
                    .size(MuseDimens.ArtworkSizePlayer)
                    .graphicsLayer {
                        scaleX = breatheScale
                        scaleY = breatheScale
                        alpha = breatheAlpha
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                stringResource(R.string.player_not_playing),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                stringResource(R.string.player_select_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}
