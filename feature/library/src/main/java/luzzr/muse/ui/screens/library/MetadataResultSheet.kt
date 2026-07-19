package luzzr.muse.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.library.R
import luzzr.muse.ui.components.AlbumArtThumbnail
import luzzr.muse.ui.components.MuseBottomSheet
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

/**
 * Bottom sheet displaying metadata search results for a song.
 * User can tap "应用" to apply metadata to the song.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataResultSheet(
    song: Song,
    results: List<MetadataResult>,
    isLoading: Boolean,
    isApplying: Boolean,
    error: String?,
    onApply: (MetadataResult) -> Unit,
    onDismiss: () -> Unit
) {
    val isBusy = isLoading || isApplying

    MuseBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.metadata_search_results)
    ) {
        Text(
            stringResource(
                if (isApplying) R.string.metadata_applying else R.string.metadata_searching,
                song.title
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppSpacing.md))

        if (isBusy) {
            Box(
                modifier = Modifier.fillMaxWidth().height(MuseDimens.MetadataSheetEmptyHeight),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        stringResource(
                            if (isApplying) {
                                R.string.metadata_applying_status
                            } else {
                                R.string.metadata_searching_status
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (error != null && results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(MuseDimens.MetadataSheetEmptyHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(AppSpacing.sm))
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                modifier = Modifier.heightIn(max = 600.dp)
            ) {
                itemsIndexed(
                    results,
                    key = { index, result -> metadataResultKey(result) + "#$index" },
                    contentType = { _, _ -> "metadata_result" }
                ) { _, result ->
                    MetadataResultItem(
                        result = result,
                        enabled = !isBusy,
                        onApply = { onApply(result) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataResultItem(result: MetadataResult, enabled: Boolean, onApply: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
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
            Box(modifier = Modifier.size(AppSpacing.xxlg)) {
                AlbumArtThumbnail(
                    artworkUri = result.coverUrl,
                    placeholder = result.title.take(1).uppercase(),
                    modifier = Modifier.size(AppSpacing.xxlg)
                )
            }
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    result.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    if (result.album.isNotBlank()) {
                        Text(
                            result.album,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    result.year?.let {
                        Text(
                            "$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        result.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(AppSpacing.xs))
            Button(
                onClick = onApply,
                enabled = enabled,
                modifier = Modifier.height(MuseDimens.ButtonHeight),
                shape = MuseShapeTokens.Pill
            ) {
                Text(stringResource(R.string.metadata_apply), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun metadataResultKey(result: MetadataResult): String {
    return listOf(
        result.source,
        result.title,
        result.artist,
        result.album,
        result.year,
        result.coverUrl
    ).joinToString("|")
}
