package luzzr.muse.ui.screens.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.player.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.components.DefaultAlbumCover
import luzzr.muse.ui.components.LyricsEmptyState
import luzzr.muse.ui.components.LyricsLoadingState
import luzzr.muse.ui.components.LyricsView
import luzzr.muse.ui.state.UiText
import luzzr.muse.ui.state.asString
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens

/**
 * 歌词面板：校正为底部轻量浮层，不挤压、不遮挡中心实时歌词。
 */
@Composable
fun LyricsPanel(
    song: Song,
    lyrics: List<LrcLine>,
    currentLyricLineProvider: () -> Int,
    lineProgressProvider: () -> Float,
    lyricsLoading: Boolean,
    lyricsError: String?,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onCalibrateLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showSongHeader: Boolean = true
) {
    var isCalibrationMode by rememberSaveable { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<UiText?>(null) }
    var lastActionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lastActionTime) {
        if (lastActionTime > 0L) {
            delay(1800)
            statusMessage = null
        }
    }

    Column(modifier = modifier) {
        if (showSongHeader) {
            CompactSongHeader(
                song = song,
                modifier = Modifier.padding(horizontal = AppSpacing.lg)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xxxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isCalibrationMode,
                onClick = { isCalibrationMode = !isCalibrationMode },
                label = {
                    Text(
                        text = stringResource(
                            if (isCalibrationMode) {
                                R.string.player_lyrics_calibration_active
                            } else {
                                R.string.player_lyrics_calibration
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(
                            if (isCalibrationMode) {
                                R.string.player_calibration_active
                            } else {
                                R.string.player_calibration_adjust
                            }
                        ),
                        modifier = Modifier.size(14.dp)
                    )
                },
                shape = MuseShapeTokens.Pill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null
            )

            if (lyricsOffsetMs != 0L) {
                Spacer(modifier = Modifier.width(AppSpacing.sm))
                Text(
                    text = stringResource(
                        R.string.player_lyrics_offset_adjusted,
                        lyricsOffsetMs / 1000.0
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 歌词区始终占满；校正/状态为浮层，不占用列表高度
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                lyrics.isNotEmpty() -> {
                    LyricsView(
                        lyrics = lyrics,
                        currentLineIndexProvider = currentLyricLineProvider,
                        lineProgressProvider = lineProgressProvider,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxSize(),
                        isCalibrationMode = isCalibrationMode,
                        onCalibrate = { timestamp ->
                            onCalibrateLyricsOffset(timestamp)
                            statusMessage = UiText.Resource(R.string.player_lyrics_calibrated_saved)
                            lastActionTime = SystemClock.elapsedRealtime()
                        }
                    )
                }
                lyricsLoading -> LyricsLoadingState()
                else -> {
                    LyricsEmptyState(
                        message = lyricsError ?: stringResource(R.string.player_lyrics_empty)
                    )
                }
            }

            // 顶部轻量状态条：不挡中心行（显式 animation 包，避免 ColumnScope 重载）
            androidx.compose.animation.AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn(tween(MotionDuration.medium1)) +
                    slideInVertically(tween(MotionDuration.medium1)) { -it / 2 },
                exit = fadeOut(tween(MotionDuration.short)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = AppSpacing.sm)
            ) {
                statusMessage?.let { msg ->
                    Surface(
                        shape = MuseShapeTokens.Pill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        tonalElevation = 1.dp,
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = msg.asString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                horizontal = AppSpacing.md,
                                vertical = AppSpacing.xs
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 底部校正坞：浮于歌词上方，带渐变过渡，中心跟唱区保持可见
            androidx.compose.animation.AnimatedVisibility(
                visible = isCalibrationMode,
                enter = fadeIn(tween(MotionDuration.medium1)) +
                    slideInVertically(tween(MotionDuration.medium2)) { it / 3 },
                exit = fadeOut(tween(MotionDuration.short)) +
                    slideOutVertically(tween(MotionDuration.medium1)) { it / 3 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
                                    )
                                )
                            )
                    )
                    CompactCalibrationDock(
                        currentOffsetMs = lyricsOffsetMs,
                        onAdjust = { deltaMs ->
                            onAdjustLyricsOffset(deltaMs)
                            statusMessage = UiText.Resource(
                                R.string.player_lyrics_offset_saved,
                                listOf(deltaMs / 1000.0)
                            )
                            lastActionTime = SystemClock.elapsedRealtime()
                        },
                        onReset = {
                            onResetLyricsOffset()
                            statusMessage = UiText.Resource(R.string.player_lyrics_reset_saved)
                            lastActionTime = SystemClock.elapsedRealtime()
                        },
                        onClose = { isCalibrationMode = false }
                    )
                }
            }
        }
    }
}

/**
 * 紧凑校正坞：单行滑条 + 快捷步进 + 重置/完成，高度约 88dp。
 */
@Composable
private fun CompactCalibrationDock(
    currentOffsetMs: Long,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentOffsetMs.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentOffsetMs) {
        if (!isDragging) {
            sliderValue = currentOffsetMs.toFloat()
        }
    }

    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        ) {
            // 偏移读数 + 步进
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StepChip(label = "−2s", onClick = { onAdjust(-2000L) })
                StepChip(label = "−0.5s", onClick = { onAdjust(-500L) })

                Text(
                    text = if (currentOffsetMs == 0L) {
                        stringResource(R.string.player_lyrics_offset_unadjusted)
                    } else {
                        stringResource(
                            R.string.player_lyrics_offset_adjusted,
                            currentOffsetMs / 1000.0
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (currentOffsetMs == 0L) onVariant else primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AppSpacing.xs),
                    maxLines = 1
                )

                StepChip(label = "+0.5s", onClick = { onAdjust(500L) })
                StepChip(label = "+2s", onClick = { onAdjust(2000L) })
            }

            // 细滑条
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    isDragging = true
                },
                onValueChangeFinished = {
                    isDragging = false
                    val delta = (sliderValue - currentOffsetMs).toLong()
                    if (delta != 0L) onAdjust(delta)
                },
                valueRange = -2000f..2000f,
                steps = 39,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = primary,
                    activeTrackColor = primary.copy(alpha = 0.75f),
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            // 操作行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = AppSpacing.sm),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.player_reset_offset),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.player_lyrics_restore_original),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = AppSpacing.md),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.player_confirm_close),
                        modifier = Modifier.size(16.dp),
                        tint = primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.player_lyrics_exit_calibration),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MuseShapeTokens.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        modifier = Modifier
            .height(30.dp)
            .widthIn(min = 48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CompactSongHeader(song: Song, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(AppSpacing.xxxlg),
            shape = RoundedCornerShape(AppSpacing.sm),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = AppSpacing.xxxs
        ) {
            Box {
                DefaultAlbumCover(
                    placeholder = song.title.take(1).uppercase(),
                    modifier = Modifier.fillMaxSize()
                )
                if (song.artworkUri != null) {
                    AsyncImage(
                        model = song.artworkUri,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Spacer(Modifier.width(AppSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
