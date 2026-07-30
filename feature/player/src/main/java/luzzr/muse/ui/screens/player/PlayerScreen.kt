package luzzr.muse.ui.screens.player

import androidx.compose.animation.AnimatedContent
import luzzr.muse.ui.theme.MuseIcons
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.player.R
import luzzr.muse.media.PlaybackRepeatMode
import luzzr.muse.media.SleepTimerMode
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.animation.MotionEasing
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.WindowSize
import luzzr.muse.ui.theme.currentWindowSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    progressProvider: () -> Long,
    duration: Long,
    repeatMode: PlaybackRepeatMode,
    shuffleMode: Boolean,
    playlist: List<Song>,
    lyrics: List<LrcLine>,
    currentLyricLineProvider: () -> Int,
    positionProvider: () -> Long,
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
    onShowQueue: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    onDismissDialog: () -> Unit = {},
    /** second arg = custom minutes when mode is CUSTOM */
    onSetSleepTimer: (SleepTimerMode, Int?) -> Unit = { _, _ -> },
    onClearSleepTimer: () -> Unit = {},
    onAdjustLyricsOffset: (Long) -> Unit = {},
    onCalibrateLyricsOffset: (Long) -> Unit = {},
    onResetLyricsOffset: () -> Unit = {},
    onCommitLyricsOffset: () -> Unit = {},
    onSearchLyrics: () -> Unit = {},
    onPlaySongAtIndex: (Int) -> Unit = {},
    reduceMotion: Boolean = false
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PlayerTopBar(
                showLyrics = showLyrics,
                currentSong = currentSong,
                onBack = onBack,
                onToggleLyrics = onToggleLyrics,
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
                                currentLyricLineProvider = currentLyricLineProvider,
                                positionProvider = positionProvider,
                                lyricsLoading = lyricsLoading,
                                lyricsError = lyricsError,
                                lyricsOffsetMs = lyricsOffsetMs,
                                onAdjustLyricsOffset = onAdjustLyricsOffset,
                                onCalibrateLyricsOffset = onCalibrateLyricsOffset,
                                onResetLyricsOffset = onResetLyricsOffset,
                                onCommitLyricsOffset = onCommitLyricsOffset,
                                onSearchLyrics = onSearchLyrics,
                                onSeek = onSeek,
                                modifier = Modifier.weight(1f),
                                showSongHeader = false,
                                reduceMotion = reduceMotion,
                                isPlaying = isPlaying,
                                durationMs = duration
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        PlaybackControls(
                            modifier = Modifier.padding(horizontal = AppSpacing.lg),
                            duration = duration,
                            progressProvider = progressProvider,
                            isPlaying = isPlaying,
                            repeatMode = repeatMode,
                            shuffleMode = shuffleMode,
                            sleepTimerMode = sleepTimerMode,
                            sleepTimerRemaining = sleepTimerRemaining,
                            onSeek = onSeek,
                            onTogglePlayPause = onTogglePlayPause,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onCyclePlayMode = onToggleRepeat,
                            onShowSleepTimer = onShowSleepTimer
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
                    AnimatedContent(
                        targetState = showLyrics,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            if (reduceMotion) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else if (targetState) {
                                (
                                    slideInVertically(
                                        animationSpec = tween(MotionDuration.medium2, easing = MotionEasing.emphasizedDecelerate),
                                        initialOffsetY = { it / 8 }
                                    ) + fadeIn(tween(MotionDuration.medium2))
                                ).togetherWith(
                                    fadeOut(tween(MotionDuration.short)) +
                                        scaleOutCompat()
                                )
                            } else {
                                (
                                    fadeIn(tween(MotionDuration.medium2)) +
                                        scaleIn(
                                            initialScale = 0.96f,
                                            animationSpec = tween(MotionDuration.medium2, easing = MotionEasing.emphasizedDecelerate)
                                        )
                                ).togetherWith(
                                    slideOutVertically(
                                        animationSpec = tween(MotionDuration.medium1, easing = MotionEasing.accelerate),
                                        targetOffsetY = { it / 10 }
                                    ) + fadeOut(tween(MotionDuration.medium1))
                                )
                            }
                        },
                        label = "player_lyrics_toggle"
                    ) { lyricsMode ->
                        if (lyricsMode) {
                            LyricsPanel(
                                song = currentSong,
                                lyrics = lyrics,
                                currentLyricLineProvider = currentLyricLineProvider,
                                positionProvider = positionProvider,
                                lyricsLoading = lyricsLoading,
                                lyricsError = lyricsError,
                                lyricsOffsetMs = lyricsOffsetMs,
                                onAdjustLyricsOffset = onAdjustLyricsOffset,
                                onCalibrateLyricsOffset = onCalibrateLyricsOffset,
                                onResetLyricsOffset = onResetLyricsOffset,
                                onCommitLyricsOffset = onCommitLyricsOffset,
                                onSearchLyrics = onSearchLyrics,
                                onSeek = onSeek,
                                modifier = Modifier.fillMaxSize(),
                                showSongHeader = false,
                                reduceMotion = reduceMotion,
                                isPlaying = isPlaying,
                                durationMs = duration
                            )
                        } else {
                            Column(
                                modifier = Modifier
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
                        }
                    }

                    PlaybackControls(
                        modifier = Modifier.padding(horizontal = AppSpacing.lg),
                        duration = duration,
                        progressProvider = progressProvider,
                        isPlaying = isPlaying,
                        repeatMode = repeatMode,
                        shuffleMode = shuffleMode,
                        sleepTimerMode = sleepTimerMode,
                        sleepTimerRemaining = sleepTimerRemaining,
                        onSeek = onSeek,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onCyclePlayMode = onToggleRepeat,
                        onShowSleepTimer = onShowSleepTimer
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
                onPlaySong = { index ->
                    onPlaySongAtIndex(index)
                    onDismissDialog()
                },
                onDismiss = onDismissDialog
            )
        }
        PlayerDialogState.SleepTimer -> {
            SleepTimerDialog(
                currentMode = sleepTimerMode,
                remainingMs = sleepTimerRemaining,
                onSelect = { mode, minutes ->
                    if (mode == SleepTimerMode.OFF) {
                        onClearSleepTimer()
                    } else {
                        onSetSleepTimer(mode, minutes)
                    }
                    onDismissDialog()
                },
                onDismiss = onDismissDialog
            )
        }
        PlayerDialogState.None -> { /* nothing */ }
    }
}

private fun scaleOutCompat() = scaleOut(
    targetScale = 0.96f,
    animationSpec = tween(MotionDuration.short)
)

@Composable
private fun EmptyPlayerState(modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val instantEnter = EnterTransition.None
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = if (reduceMotion) instantEnter else scaleIn(tween(400)) + fadeIn(tween(500, delayMillis = 120))
            ) {
                Icon(
                    MuseIcons.MusicNote,
                    contentDescription = null,
                    modifier = Modifier
                        .size(MuseDimens.ArtworkSizePlayer)
                        .graphicsLayer { alpha = 0.45f },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(AppSpacing.md))
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = if (reduceMotion) instantEnter else scaleIn(tween(400)) + fadeIn(tween(500, delayMillis = 220))
            ) {
                Text(
                    stringResource(R.string.player_not_playing),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(AppSpacing.xs))
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = if (reduceMotion) instantEnter else scaleIn(tween(400)) + fadeIn(tween(500, delayMillis = 320))
            ) {
                Text(
                    stringResource(R.string.player_select_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}
