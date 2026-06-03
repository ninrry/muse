package luzzr.muse.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import luzzr.muse.R
import luzzr.muse.player.SleepTimerMode
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
    repeatMode: Int,
    shuffleMode: Boolean,
    sleepTimerMode: SleepTimerMode?,
    sleepTimerRemaining: Long?,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowQueue: () -> Unit,
    sleepTimerFormat: () -> String
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val progress = progressProvider()
        val sliderValue = if (duration > 0) (progress.toFloat() / duration).coerceIn(0f, 1f) else 0f
        var sliderDragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableStateOf(sliderValue) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(MuseDimens.TimeBubbleWidth)
        ) {
            val currentValue = if (sliderDragging) dragValue else sliderValue
            // MD3 Slider: AppSpacing.lg total horizontal padding (AppSpacing.sm each side)
            val sliderTrackPadding = AppSpacing.sm
            val trackWidth = maxWidth - sliderTrackPadding * 2
            val thumbCenterX = sliderTrackPadding + trackWidth * currentValue

            // Time bubble overlay �?visible only while dragging
            androidx.compose.animation.AnimatedVisibility(
                visible = sliderDragging,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(100)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = thumbCenterX - MuseDimens.TimeBubbleHeight,
                        y = (-32).dp
                    )
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
                        .offset(x = thumbCenterX - thumbSize / 2)
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
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatTransition = updateTransition(targetState = repeatActive, label = "repeat")
            val repeatTint by repeatTransition.animateColor(
                transitionSpec = { tween(MotionDuration.medium1) },
                label = "repeat_tint"
            ) { active ->
                if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOneOn
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = stringResource(R.string.player_repeat),
                    tint = repeatTint
                )
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

            val shuffleTransition = updateTransition(targetState = shuffleMode, label = "shuffle")
            val shuffleTint by shuffleTransition.animateColor(
                transitionSpec = { tween(MotionDuration.medium1) },
                label = "shuffle_tint"
            ) { enabled ->
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            }
            val shuffleRotation by shuffleTransition.animateFloat(
                transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) },
                label = "shuffle_rotation"
            ) { enabled -> if (enabled) 360f else 0f }
            IconButton(
                onClick = {
                    HapticUtil.vibrate(context)
                    onToggleShuffle()
                }
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = stringResource(R.string.player_shuffle),
                    tint = shuffleTint,
                    modifier = Modifier.rotate(shuffleRotation)
                )
            }
        }

        Spacer(Modifier.height(if (isLargeFont) AppSpacing.xxs else AppSpacing.xs))

        // Bottom row: sleep timer + queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isLargeFont) Arrangement.SpaceBetween else Arrangement.Center
        ) {
            TextButton(onClick = onShowSleepTimer) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(MuseDimens.IconSizeNormal),
                    tint = if (sleepTimerMode != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(AppSpacing.xxs))
                Text(
                    if (sleepTimerMode != null && sleepTimerRemaining != null) {
                        stringResource(R.string.player_sleep_timer_active, sleepTimerFormat())
                    } else {
                        stringResource(R.string.player_sleep_timer)
                    }
                )
            }

            Spacer(Modifier.width(AppSpacing.md))

            TextButton(onClick = onShowQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(MuseDimens.IconSizeNormal))
                Spacer(Modifier.width(AppSpacing.xxs))
                Text(stringResource(R.string.player_queue))
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
