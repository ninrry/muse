package luzzr.muse.ui.screens.player

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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.player.R
import luzzr.muse.media.PlaybackRepeatMode
import luzzr.muse.media.SleepTimerMode
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
    lineProgressProvider: () -> Float,
    lyricsLoading: Boolean,
    lyricsError: String?,
    lyricsOffsetMs: Long,
    sleepTimerMode: SleepTimerMode?,
    sleepTimerRemaining: Long?,
    showLyrics: Boolean,
    dialogState: PlayerDialogState,
    floatingLyricsEnabled: Boolean = false,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {},
    onToggleLyrics: () -> Unit = {},
    onRefreshLyrics: () -> Unit = {},
    onShowQueue: () -> Unit = {},
    onToggleFloatingLyrics: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    onDismissDialog: () -> Unit = {},
    onSetSleepTimer: (SleepTimerMode, Int?) -> Unit = { _, _ -> },
    onClearSleepTimer: () -> Unit = {},
    onAdjustLyricsOffset: (Long) -> Unit = {},
    onCalibrateLyricsOffset: (Long) -> Unit = {},
    onResetLyricsOffset: () -> Unit = {},
    onPlaySongAtIndex: (Int) -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PlayerTopBar(
                showLyrics = showLyrics,
                currentSong = currentSong,
                floatingLyricsEnabled = floatingLyricsEnabled,
                onBack = onBack,
                onToggleLyrics = onToggleLyrics,
                onRefreshLyrics = onRefreshLyrics,
                onShowQueue = onShowQueue,
                onToggleFloatingLyrics = onToggleFloatingLyrics
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
                                lineProgressProvider = lineProgressProvider,
                                lyricsLoading = lyricsLoading,
                                lyricsError = lyricsError,
                                lyricsOffsetMs = lyricsOffsetMs,
                                onAdjustLyricsOffset = onAdjustLyricsOffset,
                                onCalibrateLyricsOffset = onCalibrateLyricsOffset,
                                onResetLyricsOffset = onResetLyricsOffset,
                                onSeek = onSeek,
                                modifier = Modifier.weight(1f),
                                showSongHeader = false
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
                    if (showLyrics) {
                        LyricsPanel(
                            song = currentSong,
                            lyrics = lyrics,
                            currentLyricLineProvider = currentLyricLineProvider,
                            lineProgressProvider = lineProgressProvider,
                            lyricsLoading = lyricsLoading,
                            lyricsError = lyricsError,
                            lyricsOffsetMs = lyricsOffsetMs,
                            onAdjustLyricsOffset = onAdjustLyricsOffset,
                            onCalibrateLyricsOffset = onCalibrateLyricsOffset,
                            onResetLyricsOffset = onResetLyricsOffset,
                            onSeek = onSeek,
                            modifier = Modifier.weight(1f),
                            showSongHeader = false
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
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = scaleIn(tween(400)) + fadeIn(tween(500, delayMillis = 120))
            ) {
                Icon(
                    Icons.Default.MusicNote,
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
                enter = scaleIn(tween(400)) + fadeIn(tween(500, delayMillis = 220))
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
                enter = scaleIn(tween(400)) + fadeIn(tween(500, delayMillis = 320))
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
