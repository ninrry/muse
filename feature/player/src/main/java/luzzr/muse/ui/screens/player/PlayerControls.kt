package luzzr.muse.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import luzzr.muse.feature.player.R
import luzzr.muse.media.PlaybackRepeatMode
import luzzr.muse.media.SleepTimerMode
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.haptic.HapticUtil
import luzzr.muse.ui.haptic.pressScale
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackControls(
    modifier: Modifier = Modifier,
    duration: Long,
    progressProvider: () -> Long,
    isPlaying: Boolean,
    repeatMode: PlaybackRepeatMode,
    shuffleMode: Boolean,
    sleepTimerMode: SleepTimerMode?,
    sleepTimerRemaining: Long? = null,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onShowSleepTimer: () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val progress = progressProvider()
        val sliderValue = if (duration > 0) (progress.toFloat() / duration).coerceIn(0f, 1f) else 0f
        var sliderDragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(sliderValue) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(MuseDimens.TimeBubbleWidth)
        ) {
            val currentValue = if (sliderDragging) dragValue else sliderValue
            val sliderTrackPadding = AppSpacing.sm
            val trackWidth = maxWidth - sliderTrackPadding * 2
            val thumbCenterX = sliderTrackPadding + trackWidth * currentValue

            // Time bubble overlay – visible only while dragging
            androidx.compose.animation.AnimatedVisibility(
                visible = sliderDragging,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(100)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = (thumbCenterX - MuseDimens.TimeBubbleHeight).roundToPx(),
                            y = (-32).dp.roundToPx()
                        )
                    }
            ) {
                val timeMs = (dragValue * duration).toLong()
                Surface(
                    shape = RoundedCornerShape(MuseDimens.TimeBubbleCornerRadius),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = AppSpacing.xxs
                ) {
                    Text(
                        text = formatTime(timeMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = MuseDimens.SpacingMedium, vertical = MuseDimens.SpacingTiny),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Enlarged thumb when dragging
            val thumbSize by animateDpAsState(
                targetValue = if (sliderDragging) MuseDimens.SliderThumbRadius else 0.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "thumb_size"
            )
            if (sliderDragging && thumbSize > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                x = (thumbCenterX - thumbSize / 2).roundToPx(),
                                y = 0
                            )
                        }
                        .size(thumbSize),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ) {}
                }
            }

            Slider(
                value = if (sliderDragging) dragValue else sliderValue,
                onValueChange = {
                    sliderDragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    sliderDragging = false
                    onSeek((dragValue * duration).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xxs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(progress), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(AppSpacing.xs))

        val configuration = LocalConfiguration.current
        val isLargeFont = configuration.fontScale > 1.3f

        // Transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isLargeFont) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val playModeIcon = when {
                shuffleMode -> Icons.Default.Shuffle
                repeatMode == PlaybackRepeatMode.ONE -> Icons.Default.RepeatOneOn
                else -> Icons.Default.Repeat
            }
            val playModeDesc = when {
                shuffleMode -> stringResource(R.string.player_shuffle)
                repeatMode == PlaybackRepeatMode.ONE -> stringResource(R.string.player_repeat_one)
                else -> stringResource(R.string.player_repeat_all)
            }
            IconButton(onClick = onCyclePlayMode) {
                Crossfade(
                    targetState = playModeIcon,
                    animationSpec = tween(300),
                    label = "play_mode"
                ) { icon ->
                    Icon(
                        icon,
                        contentDescription = playModeDesc,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onSkipPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.player_prev),
                    modifier = Modifier.size(MuseDimens.ArtworkSizeSmall)
                )
            }

            val playInteractionSource = remember { MutableInteractionSource() }
            val context = LocalContext.current
            Surface(
                onClick = {
                    HapticUtil.vibrate(context)
                    onTogglePlayPause()
                },
                interactionSource = playInteractionSource,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(AppSpacing.xxxlg)
                    .pressScale(playInteractionSource, 0.92f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(MotionDuration.medium1),
                        label = "play_pause"
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) {
                                stringResource(R.string.player_pause)
                            } else {
                                stringResource(R.string.player_play)
                            },
                            modifier = Modifier.size(MuseDimens.ArtworkSizeSmall),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            IconButton(onClick = onSkipNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    modifier = Modifier.size(MuseDimens.ArtworkSizeSmall)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onShowSleepTimer) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = sleepTimerRemaining?.let {
                            stringResource(R.string.player_sleep_timer_active, formatTime(it))
                        } ?: stringResource(R.string.player_sleep_timer),
                        tint = if (sleepTimerMode != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                AnimatedVisibility(visible = sleepTimerRemaining != null) {
                    Text(
                        text = formatTime(sleepTimerRemaining ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
