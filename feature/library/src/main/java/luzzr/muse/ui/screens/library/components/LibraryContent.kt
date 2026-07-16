package luzzr.muse.ui.screens.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import luzzr.muse.domain.model.Album
import luzzr.muse.domain.model.Artist
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.library.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.animation.MotionEasing
import luzzr.muse.ui.screens.library.tabs.AlbumListTab
import luzzr.muse.ui.screens.library.tabs.ArtistListTab
import luzzr.muse.ui.screens.library.tabs.SongListTab

@Composable
fun LibraryContent(
    selectedTab: Int,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    currentSongId: Long?,
    onPlayShuffled: (List<Song>) -> Unit,
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
    AnimatedContent(
        targetState = selectedTab,
        modifier = modifier,
        transitionSpec = {
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
        },
        label = "tab_content"
    ) { tab ->
        when (tab) {
            0 -> SongListTab(
                songs = songs,
                currentSongId = currentSongId,
                onPlayShuffled = onPlayShuffled,
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
fun LibraryDetailPanel(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.library_detail_panel), style = MaterialTheme.typography.titleLarge)
    }
}