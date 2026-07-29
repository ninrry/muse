package luzzr.muse.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.Song
import luzzr.muse.ui.components.AlbumArtThumbnail
import luzzr.muse.ui.components.MuseBottomSheet
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens

/**
 * 专辑/艺人歌曲列表底部弹层（统一 MuseBottomSheet 样式）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListSheet(title: String, subtitle: String, songs: List<Song>, onSongClick: (Int) -> Unit, onDismiss: () -> Unit) {
    MuseBottomSheet(onDismiss = onDismiss, title = title) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(AppSpacing.md))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            modifier = Modifier.heightIn(max = 500.dp)
        ) {
            itemsIndexed(
                songs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "sheet_song" }
            ) { index, song ->
                Surface(
                    onClick = { onSongClick(index) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MuseShapeTokens.Item,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtThumbnail(
                            artworkUri = song.artworkUri,
                            placeholder = song.title.take(1).uppercase(),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text(
                            song.formattedDuration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
