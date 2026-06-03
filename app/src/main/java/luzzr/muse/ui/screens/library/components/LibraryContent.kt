package luzzr.muse.ui.screens.library.components

import androidx.compose.animation.AnimatedContent
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
import luzzr.muse.R
import luzzr.muse.data.model.Album
import luzzr.muse.data.model.Artist
import luzzr.muse.data.model.Song
import luzzr.muse.ui.screens.library.LibraryViewModel
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
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = selectedTab,
        modifier = modifier,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally(initialOffsetX = { it }) + fadeIn())
                    .togetherWith(slideOutHorizontally(targetOffsetX = { -it }) + fadeOut())
            } else {
                (slideInHorizontally(initialOffsetX = { -it }) + fadeIn())
                    .togetherWith(slideOutHorizontally(targetOffsetX = { it }) + fadeOut())
            }
        },
        label = "tab_content"
    ) { tab ->
        when (tab) {
            0 -> SongListTab(songs, viewModel, currentSongId)
            1 -> AlbumListTab(albums) { viewModel.showAlbumSongs(it) }
            2 -> ArtistListTab(artists) { viewModel.showArtistSongs(it) }
        }
    }
}

@Composable
fun LibraryDetailPanel(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.library_detail_panel), style = MaterialTheme.typography.titleLarge)
    }
}
