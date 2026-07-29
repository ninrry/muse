package luzzr.muse.ui.screens.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.Album
import luzzr.muse.domain.model.Artist
import luzzr.muse.domain.model.Song
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.animation.MotionEasing
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.components.MuseEditorialCard
import luzzr.muse.ui.components.MuseStatusPill
import luzzr.muse.ui.screens.library.tabs.AlbumListTab
import luzzr.muse.ui.screens.library.tabs.ArtistListTab
import luzzr.muse.ui.screens.library.tabs.SongListTab
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens

@Composable
fun LibraryContent(
    selectedTab: Int,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    currentSongId: Long?,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShareSong: (Song) -> Unit,
    onSearchMetadata: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShowAlbumSongs: (Album) -> Unit,
    onShowArtistSongs: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    // Selection mode parameters
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<Long> = emptySet(),
    onEnterSelectionMode: () -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onExitSelectionMode: () -> Unit = {},
    onAddSelectedToPlaylist: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onFetchLyricsForSelected: () -> Unit = {},
    lyricsFetchProgress: Pair<Int, Int>? = null,
    sortType: luzzr.muse.domain.model.SortType = luzzr.muse.domain.model.SortType.TITLE_ASC
) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = selectedTab,
        modifier = modifier,
        transitionSpec = if (reduceMotion) {
            { EnterTransition.None togetherWith ExitTransition.None }
        } else {
            {
                val enterMs = MotionDuration.medium2
                val exitMs = MotionDuration.medium1
                if (targetState > initialState) {
                    (
                        slideInHorizontally(
                            initialOffsetX = { it / 4 },
                            animationSpec = tween(enterMs, easing = MotionEasing.emphasizedDecelerate)
                        ) + fadeIn(tween(enterMs))
                        ).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { -it / 5 },
                            animationSpec = tween(exitMs, easing = MotionEasing.accelerate)
                        ) + fadeOut(tween(exitMs))
                    )
                } else {
                    (
                        slideInHorizontally(
                            initialOffsetX = { -it / 4 },
                            animationSpec = tween(enterMs, easing = MotionEasing.emphasizedDecelerate)
                        ) + fadeIn(tween(enterMs))
                        ).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { it / 5 },
                            animationSpec = tween(exitMs, easing = MotionEasing.accelerate)
                        ) + fadeOut(tween(exitMs))
                    )
                }
            }
        },
        label = "tab_content"
    ) { tab ->
        when (tab) {
            0 -> SongListTab(
                songs = songs,
                currentSongId = currentSongId,
                onPlaySongs = onPlaySongs,
                onShareSong = onShareSong,
                onSearchMetadata = onSearchMetadata,
                onEditMetadata = onEditMetadata,
                onDeleteSong = onDeleteSong,
                onAddToPlaylist = onAddToPlaylist,
                isSelectionMode = isSelectionMode,
                selectedSongIds = selectedSongIds,
                onEnterSelectionMode = onEnterSelectionMode,
                onToggleSelection = onToggleSelection,
                onExitSelectionMode = onExitSelectionMode,
                onAddSelectedToPlaylist = onAddSelectedToPlaylist,
                onSelectAll = onSelectAll,
                onFetchLyricsForSelected = onFetchLyricsForSelected,
                lyricsFetchProgress = lyricsFetchProgress,
                sortType = sortType
            )
            1 -> AlbumListTab(albums, onShowAlbumSongs)
            2 -> ArtistListTab(artists, onShowArtistSongs)
        }
    }
}

@Composable
fun LibraryDetailPanel(modifier: Modifier = Modifier, songCount: Int = 0, albumCount: Int = 0, artistCount: Int = 0) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(AppSpacing.lg),
        contentAlignment = Alignment.Center
    ) {
        MuseEditorialCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                MuseStatusPill(text = "MUSE INDEX")
                Text(
                    text = "本地唱片索引",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "从左侧挑选歌曲、专辑或艺术家。你的文件仍留在设备中，Muse 只负责把它们编排成一座随身唱片馆。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    ArchiveStat(Icons.Default.LibraryMusic, songCount, "歌曲", Modifier.weight(1f))
                    ArchiveStat(Icons.Default.Album, albumCount, "专辑", Modifier.weight(1f))
                    ArchiveStat(Icons.Default.Person, artistCount, "艺术家", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ArchiveStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int, label: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = MuseShapeTokens.Item,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(text = value.toString(), style = MaterialTheme.typography.titleLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
