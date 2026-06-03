package luzzr.muse.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
 * Contains compact song header, offset controls, and synced lyrics.
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
    showLyricsOffset: Boolean,
    onToggleLyricsOffset: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        CompactSongHeader(
            song = song,
            modifier = Modifier.padding(horizontal = AppSpacing.lg)
        )

        // Offset toggle row: shows current offset as chip, toggles control panel
        LyricsOffsetToggle(
            offsetMs = lyricsOffsetMs,
            isExpanded = showLyricsOffset,
            onToggle = onToggleLyricsOffset,
            modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xxxs)
        )

        // Collapsible offset control panel
        AnimatedVisibility(
            visible = showLyricsOffset,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            LyricsOffsetControl(
                offsetMs = lyricsOffsetMs,
                onAdjust = onAdjustLyricsOffset,
                onReset = onResetLyricsOffset,
                modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xxxs)
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
                        modifier = Modifier.fillMaxSize()
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

/**
 * Compact toggle indicator for lyrics offset.
 * - Offset = 0: shows faint "未调整" chip to reveal controls
 * - Offset ≠ 0: shows current offset (±Xs) pill, tap to reveal controls
 */
@Composable
private fun LyricsOffsetToggle(offsetMs: Long, isExpanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val isAdjusted = offsetMs != 0L
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(AppSpacing.xs),
            color = if (isAdjusted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
            tonalElevation = AppSpacing.None
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MuseDimens.SpacingMedium, vertical = MuseDimens.SpacingTiny),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isAdjusted) "%+.2fs".format(offsetMs / 1000f) else stringResource(R.string.player_lyrics_offset_unadjusted),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAdjusted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
                if (!isExpanded) {
                    Text(
                        text = " ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAdjusted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                } else {
                    Text(
                        text = " ▲",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsOffsetControl(offsetMs: Long, onAdjust: (Long) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val isAdjusted = offsetMs != 0L
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            OffsetButton("-1s", onClick = { onAdjust(-1000L) })
            Spacer(Modifier.width(AppSpacing.xxs))
            OffsetButton("-0.5s", onClick = { onAdjust(-500L) })
            Spacer(Modifier.width(AppSpacing.xxs))
            OffsetButton("-0.1s", onClick = { onAdjust(-100L) })

            if (isAdjusted) {
                Spacer(Modifier.width(MuseDimens.SpacingSmall))
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = AppSpacing.xs, vertical = AppSpacing.None),
                    modifier = Modifier.height(AppSpacing.xxxlg)
                ) {
                    Text(stringResource(R.string.player_lyrics_offset_reset), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(MuseDimens.SpacingSmall))
            } else {
                Spacer(Modifier.width(AppSpacing.xxs))
            }

            OffsetButton("+0.1s", onClick = { onAdjust(100L) })
            Spacer(Modifier.width(AppSpacing.xxs))
            OffsetButton("+0.5s", onClick = { onAdjust(500L) })
            Spacer(Modifier.width(AppSpacing.xxs))
            OffsetButton("+1s", onClick = { onAdjust(1000L) })
        }
    }
}

/**
 * Small label button for a single offset adjustment.
 */
@Composable
private fun OffsetButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(MuseDimens.SpacingSmall),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = AppSpacing.None
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = MuseDimens.SpacingTiny),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
