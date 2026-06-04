package luzzr.muse.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import luzzr.muse.R
import luzzr.muse.data.model.Song
import luzzr.muse.data.network.LrcLine
import luzzr.muse.ui.components.DefaultAlbumCover
import luzzr.muse.ui.components.LyricsEmptyState
import luzzr.muse.ui.components.LyricsLoadingState
import luzzr.muse.ui.components.LyricsView
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilterChip(
                selected = isCalibrationMode,
                onClick = { isCalibrationMode = !isCalibrationMode },
                label = {
                    Text(
                        text = if (isCalibrationMode) "校正模式 (开启中)" else "校正时间轴",
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

            // Current offset display & fine-tuning buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (lyricsOffsetMs != 0L) {
                    Text(
                        text = "%+.2fs".format(lyricsOffsetMs / 1000f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = AppSpacing.xs)
                    )
                }

                if (isCalibrationMode) {
                    // Fine-tuning back 0.1s
                    IconButton(
                        onClick = { onAdjustLyricsOffset(-100L) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = "-.1s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(AppSpacing.xxs))
                    // Fine-tuning forward 0.1s
                    IconButton(
                        onClick = { onAdjustLyricsOffset(100L) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = "+.1s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (lyricsOffsetMs != 0L) {
                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                        TextButton(
                            onClick = onResetLyricsOffset,
                            contentPadding = PaddingValues(horizontal = AppSpacing.xxs),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "重置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
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
                        onCalibrate = onCalibrateLyricsOffset
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

        Spacer(Modifier.height(AppSpacing.xxs))
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
