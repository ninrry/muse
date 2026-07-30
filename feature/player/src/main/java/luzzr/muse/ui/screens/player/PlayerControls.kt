package luzzr.muse.ui.screens.player

import androidx.compose.animation.AnimatedContent
import luzzr.muse.ui.theme.MuseIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import luzzr.muse.feature.player.R
import luzzr.muse.media.PlaybackRepeatMode
import luzzr.muse.media.SleepTimerMode
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.components.MuseProgressSlider
import luzzr.muse.ui.haptic.HapticUtil
import luzzr.muse.ui.haptic.pressScale
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import kotlin.math.abs

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
    val reduceMotion = LocalReduceMotion.current
    Column(modifier = modifier.fillMaxWidth()) {
        // 叶子节点帧同步读进度，不拖累 PlayerScreen
        var progress by remember { mutableLongStateOf(progressProvider()) }
        LaunchedEffect(isPlaying) {
            while (true) {
                withFrameNanos {
                    val p = progressProvider()
                    if (p != progress) progress = p
                }
                if (!isPlaying) {
                    // 暂停时降频
                    kotlinx.coroutines.delay(200)
                }
            }
        }
        val sliderValue = if (duration > 0) (progress.toFloat() / duration).coerceIn(0f, 1f) else 0f
        var dragValue by remember { mutableFloatStateOf(sliderValue) }
        var sliderDragging by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxWidth()) {
            MuseProgressSlider(
                value = if (sliderDragging) dragValue else sliderValue,
                onValueChange = {
                    sliderDragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    sliderDragging = false
                    onSeek((dragValue * duration).toLong())
                },
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = sliderDragging,
                enter = fadeIn(animationSpec = tween(if (reduceMotion) 0 else 150)),
                exit = fadeOut(animationSpec = tween(if (reduceMotion) 0 else 100)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = AppSpacing.xxs
                ) {
                    Text(
                        text = formatTime((dragValue * duration).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = MuseDimens.SpacingMedium, vertical = MuseDimens.SpacingTiny),
                        textAlign = TextAlign.Center
                    )
                }
            }
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
            // 顺序 / 乱序 / 单曲 三态
            val playModeKey = when {
                shuffleMode -> "shuffle"
                repeatMode == PlaybackRepeatMode.ONE -> "one"
                else -> "all"
            }
            val playModeDesc = when (playModeKey) {
                "shuffle" -> stringResource(R.string.player_shuffle)
                "one" -> stringResource(R.string.player_repeat_one)
                else -> stringResource(R.string.player_repeat_all)
            }
            val modeInteraction = remember { MutableInteractionSource() }
            val modeContext = LocalContext.current
            IconButton(
                onClick = {
                    HapticUtil.vibrate(modeContext, durationMs = 16)
                    onCyclePlayMode()
                },
                modifier = Modifier.pressScale(modeInteraction, 0.88f),
                interactionSource = modeInteraction
            ) {
                AnimatedContent(
                    targetState = playModeKey,
                    transitionSpec = if (reduceMotion) {
                        { EnterTransition.None togetherWith ExitTransition.None }
                    } else {
                        {
                        (
                            fadeIn(tween(MotionDuration.medium1)) +
                                scaleIn(initialScale = 0.6f, animationSpec = tween(MotionDuration.medium1))
                        ).togetherWith(
                            fadeOut(tween(MotionDuration.short)) +
                                scaleOut(targetScale = 0.6f, animationSpec = tween(MotionDuration.short))
                        )
                        }
                    },
                    label = "play_mode"
                ) { key ->
                    val icon = when (key) {
                        "shuffle" -> MuseIcons.Shuffle
                        "one" -> MuseIcons.RepeatOne
                        else -> MuseIcons.Repeat
                    }
                    Icon(
                        icon,
                        contentDescription = playModeDesc,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            IconButton(onClick = onSkipPrevious) {
                Icon(
                    MuseIcons.SkipPrevious,
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
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .size(72.dp)
                    .pressScale(playInteractionSource, 0.9f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(if (reduceMotion) 0 else MotionDuration.medium1),
                        label = "play_pause"
                    ) { playing ->
                        Icon(
                            if (playing) MuseIcons.Pause else MuseIcons.Play,
                            contentDescription = if (playing) {
                                stringResource(R.string.player_pause)
                            } else {
                                stringResource(R.string.player_play)
                            },
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            IconButton(onClick = onSkipNext) {
                Icon(
                    MuseIcons.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    modifier = Modifier.size(MuseDimens.ArtworkSizeSmall)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onShowSleepTimer) {
                    Icon(
                        MuseIcons.Speed,
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
                AnimatedVisibility(
                    visible = sleepTimerRemaining != null,
                    enter = if (reduceMotion) EnterTransition.None else fadeIn(),
                    exit = if (reduceMotion) ExitTransition.None else fadeOut()
                ) {
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
