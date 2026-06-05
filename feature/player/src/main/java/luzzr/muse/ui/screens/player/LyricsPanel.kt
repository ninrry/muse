package luzzr.muse.ui.screens.player

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.player.R
import luzzr.muse.ui.components.DefaultAlbumCover
import luzzr.muse.ui.components.LyricsEmptyState
import luzzr.muse.ui.components.LyricsLoadingState
import luzzr.muse.ui.components.LyricsView
import luzzr.muse.ui.state.UiText
import luzzr.muse.ui.state.asString
import luzzr.muse.ui.theme.AppSpacing
import kotlinx.coroutines.delay

/**
 * Lyrics mode panel displayed when the user toggles to lyrics view.
 * Contains compact song header, inline timeline calibration controller, and synced lyrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            delay(2000)
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

        // Inline calibration control row (takes minimal vertical space)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xxxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
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
                        imageVector = if (isCalibrationMode) Icons.Default.CheckCircle else Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                border = null
            )
        }

        // Lyrics content fills remaining space
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                lyricsLoading -> {
                    LyricsLoadingState()
                }
                else -> {
                    LyricsEmptyState(
                        message = lyricsError ?: stringResource(R.string.player_lyrics_empty)
                    )
                }
            }
        }

        if (isCalibrationMode) {
            CalibrationPanel(
                statusMessage = statusMessage,
                currentOffsetText = if (lyricsOffsetMs == 0L) {
                    stringResource(R.string.player_lyrics_offset_unadjusted)
                } else {
                    stringResource(R.string.player_lyrics_offset_adjusted, lyricsOffsetMs / 1000.0)
                },
                onAdjust = { delta ->
                    onAdjustLyricsOffset(delta)
                    statusMessage = UiText.Resource(
                        R.string.player_lyrics_offset_saved,
                        listOf(delta / 1000.0)
                    )
                    lastActionTime = SystemClock.elapsedRealtime()
                },
                onReset = {
                    onResetLyricsOffset()
                    statusMessage = UiText.Resource(R.string.player_lyrics_reset_saved)
                    lastActionTime = SystemClock.elapsedRealtime()
                },
                onClose = {
                    isCalibrationMode = false
                }
            )
        }

        Spacer(Modifier.height(AppSpacing.xxs))
    }
}

@Composable
private fun CalibrationPanel(
    statusMessage: UiText?,
    currentOffsetText: String,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 状态/提示信息展示
            Crossfade(
                targetState = statusMessage,
                label = "status_crossfade"
            ) { message ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                ) {
                    if (message != null) {
                        Text(
                            text = message.asString(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.player_lyrics_offset_current, currentOffsetText),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 粗调按钮行 (±2.0s)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onAdjust(-2000L) },
                    contentPadding = PaddingValues(horizontal = AppSpacing.sm),
                    modifier = Modifier.height(36.dp).weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("-2.0s", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(AppSpacing.md))

                OutlinedButton(
                    onClick = { onAdjust(2000L) },
                    contentPadding = PaddingValues(horizontal = AppSpacing.sm),
                    modifier = Modifier.height(36.dp).weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+2.0s", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 精调与中调按钮行 (±0.5s, ±0.1s)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -0.5s
                OutlinedButton(
                    onClick = { onAdjust(-500L) },
                    contentPadding = PaddingValues(horizontal = AppSpacing.sm),
                    modifier = Modifier.height(36.dp).weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("-0.5s", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(AppSpacing.xs))

                // -0.1s
                OutlinedButton(
                    onClick = { onAdjust(-100L) },
                    contentPadding = PaddingValues(horizontal = AppSpacing.sm),
                    modifier = Modifier.height(36.dp).weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("-0.1s", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(AppSpacing.sm))

                // +0.1s
                OutlinedButton(
                    onClick = { onAdjust(100L) },
                    contentPadding = PaddingValues(horizontal = AppSpacing.sm),
                    modifier = Modifier.height(36.dp).weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+0.1s", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(AppSpacing.xs))

                // +0.5s
                OutlinedButton(
                    onClick = { onAdjust(500L) },
                    contentPadding = PaddingValues(horizontal = AppSpacing.sm),
                    modifier = Modifier.height(36.dp).weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+0.5s", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮行：恢复原版 与 退出校正
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onReset,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = AppSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.player_lyrics_restore_original),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = AppSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.player_lyrics_exit_calibration),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
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
                        contentDescription = null,
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
