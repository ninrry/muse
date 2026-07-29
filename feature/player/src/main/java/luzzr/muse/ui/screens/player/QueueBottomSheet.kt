package luzzr.muse.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.player.R
import luzzr.muse.ui.components.MuseBottomSheet
import luzzr.muse.ui.components.MuseSheetSpacer
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

/**
 * Bottom sheet displaying the current playback queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(playlist: List<Song>, currentSong: Song?, onPlaySong: (Int) -> Unit, onDismiss: () -> Unit) {
    MuseBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.player_queue)
    ) {
        if (playlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MuseDimens.QueueSheetEmptyHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.player_queue_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxxs)) {
                itemsIndexed(
                    items = playlist,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "queue_item" }
                ) { index, item ->
                    val isCurrent = item.id == currentSong?.id
                    Surface(
                        onClick = { onPlaySong(index) },
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = MuseShapeTokens.Item
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isCurrent) {
                                    stringResource(R.string.player_queue_current_item, item.title, item.artist)
                                } else {
                                    stringResource(R.string.player_queue_item, item.title, item.artist)
                                },
                                style = if (isCurrent) {
                                    MaterialTheme.typography.bodyMedium
                                } else {
                                    MaterialTheme.typography.bodySmall
                                },
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                item.formattedDuration,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        MuseSheetSpacer()
    }
}
