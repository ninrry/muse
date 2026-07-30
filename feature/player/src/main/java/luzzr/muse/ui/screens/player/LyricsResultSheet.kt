package luzzr.muse.ui.screens.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.player.R
import luzzr.muse.ui.components.MuseBottomSheet
import luzzr.muse.ui.haptic.pressScale
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseIcons
import luzzr.muse.ui.theme.MuseShapeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsResultSheet(
    song: Song,
    results: List<LyricsResult>,
    isLoading: Boolean,
    isApplying: Boolean,
    error: String?,
    onApply: (LyricsResult) -> Unit,
    onSelectFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = isLoading || isApplying
    MuseBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.lyrics_search_title)
    ) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(AppSpacing.sm))
        OutlinedButton(
            onClick = onSelectFile,
            enabled = !isApplying,
            modifier = Modifier.fillMaxWidth(),
            shape = MuseShapeTokens.Pill
        ) {
            Icon(
                imageVector = MuseIcons.FolderOpen,
                contentDescription = null
            )
            Spacer(Modifier.width(AppSpacing.xs))
            Text(stringResource(R.string.lyrics_file_pick))
        }
        Spacer(Modifier.height(AppSpacing.xxs))
        Text(
            text = stringResource(R.string.lyrics_file_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppSpacing.md))

        if (error != null && results.isNotEmpty() && !busy) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(AppSpacing.sm))
        }

        when {
            busy -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MuseDimens.MetadataSheetEmptyHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(AppSpacing.sm))
                        Text(
                            stringResource(
                                if (isApplying) R.string.lyrics_applying else R.string.lyrics_searching
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            error != null && results.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MuseDimens.MetadataSheetEmptyHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            results.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MuseDimens.MetadataSheetEmptyHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.lyrics_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    itemsIndexed(results, key = { i, r -> "${r.source}_${r.id}_$i" }) { _, result ->
                        LyricsResultItem(
                            result = result,
                            fallbackTitle = song.title,
                            fallbackArtist = song.artist,
                            enabled = !isApplying,
                            onApply = { onApply(result) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsResultItem(
    result: LyricsResult,
    fallbackTitle: String,
    fallbackArtist: String,
    enabled: Boolean,
    onApply: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f),
        shape = MuseShapeTokens.Item,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.trackName.ifBlank { fallbackTitle },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(result.artistName.ifBlank { fallbackArtist })
                        result.albumName?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        val hasWordTiming = result.syncedLines.any { !it.words.isNullOrEmpty() }
                        if (result.source.isNotBlank()) append(result.source)
                        if (result.syncedLines.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(
                                stringResource(
                                    if (hasWordTiming) {
                                        R.string.lyrics_badge_word_synced
                                    } else {
                                        R.string.lyrics_badge_synced
                                    }
                                )
                            )
                            append(" · ")
                            append(result.syncedLines.size)
                            append(stringResource(R.string.lyrics_lines_unit))
                        } else {
                            if (isNotEmpty()) append(" · ")
                            append(stringResource(R.string.lyrics_badge_plain))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(AppSpacing.xs))
            Button(
                onClick = onApply,
                enabled = enabled,
                modifier = Modifier.height(MuseDimens.ButtonHeightSmall),
                shape = MuseShapeTokens.Pill,
                interactionSource = interaction
            ) {
                Text(
                    stringResource(R.string.lyrics_apply),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
