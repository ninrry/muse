package luzzr.muse.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.library.R
import luzzr.muse.ui.components.SongListItem
import luzzr.muse.ui.components.SongMenuItem
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

@Composable
fun SongListTab(
    songs: List<Song>,
    currentSongId: Long?,
    onPlayShuffled: (List<Song>) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onSearchMetadata: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.library_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = AppSpacing.xs, vertical = AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxxs)
    ) {
        item(key = "shuffle_header") {
            ElevatedCard(
                onClick = { onPlayShuffled(songs) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppSpacing.xs, start = AppSpacing.xs, end = AppSpacing.xs),
                shape = MuseShapeTokens.Card
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MuseDimens.CardSpacing, vertical = MuseDimens.SpacingLarge),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(MuseDimens.IconSizeNormal),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(
                        stringResource(R.string.library_shuffle_all_count, songs.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongListItem(
                song = song,
                isPlaying = currentSongId == song.id,
                onClick = { onPlaySongs(songs, index) },
                menuItems = listOf(
                    SongMenuItem(
                        stringResource(R.string.player_search_metadata),
                        onClick = { onSearchMetadata(song) }
                    ),
                    SongMenuItem(
                        stringResource(R.string.player_edit_metadata),
                        onClick = { onEditMetadata(song) }
                    ),
                    SongMenuItem(
                        stringResource(R.string.action_delete),
                        isDestructive = true,
                        onClick = { onDeleteSong(song) }
                    )
                )
            )
        }
        item { Spacer(Modifier.height(MuseDimens.MiniPlayerClearance)) }
    }
}
