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
import androidx.compose.ui.unit.dp
import luzzr.muse.data.model.Song
import luzzr.muse.ui.components.SongListItem
import luzzr.muse.ui.components.SongMenuItem
import luzzr.muse.ui.screens.library.LibraryViewModel
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

@Composable
fun SongListTab(
    songs: List<Song>,
    viewModel: LibraryViewModel,
    currentSongId: Long?
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌曲，请先扫描音乐库", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = AppSpacing.xs, vertical = AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxxs)
    ) {
        item(key = "shuffle_header") {
            ElevatedCard(
                onClick = { viewModel.playShuffled(songs) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppSpacing.xs, start = AppSpacing.xs, end = AppSpacing.xs),
                shape = MuseShapeTokens.Card
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(
                        "随机播放全部 (${songs.size}首)",
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
                onClick = { viewModel.playSongs(songs, index) },
                menuItems = listOf(
                    SongMenuItem("网络搜索元数据", onClick = { viewModel.requestSearchMetadata(song) }),
                    SongMenuItem("编辑元数据", onClick = { viewModel.requestRenameSong(song) }),
                    SongMenuItem("删除", isDestructive = true, onClick = { viewModel.requestDeleteSong(song) })
                )
            )
        }
        item { Spacer(Modifier.height(MuseDimens.MiniPlayerClearance)) }
    }
}
