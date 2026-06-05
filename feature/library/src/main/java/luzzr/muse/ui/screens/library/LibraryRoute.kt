package luzzr.muse.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.ui.screens.library.components.LibraryContent
import luzzr.muse.ui.screens.library.components.LibraryDetailPanel
import luzzr.muse.ui.screens.library.components.LibrarySearchBar
import luzzr.muse.ui.screens.library.components.LibraryTabs
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.WindowSize
import luzzr.muse.ui.theme.currentWindowSize

@Composable
fun LibraryRoute(viewModel: LibraryViewModel, showSearch: Boolean, scaffoldPadding: PaddingValues, innerPadding: PaddingValues) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val currentSortType by viewModel.sortType.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()

    var subTab by remember { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val windowSize = currentWindowSize()

    when (windowSize) {
        WindowSize.Medium, WindowSize.Expanded -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                Column(modifier = Modifier.weight(0.4f).fillMaxSize()) {
                    LibraryTabs(
                        selectedTab = subTab,
                        onTabSelected = { index ->
                            subTab = index
                            if (index == 1 || index == 2) viewModel.refreshStats()
                        },
                        currentSortType = currentSortType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = AppSpacing.md, end = AppSpacing.md, top = AppSpacing.xs, bottom = AppSpacing.xxs)
                    )

                    LibrarySearchBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = {
                            searchQuery = it
                            if (subTab == 0) viewModel.search(it)
                        },
                        showSearch = showSearch
                    )

                    LibraryContent(
                        selectedTab = subTab,
                        songs = songs,
                        albums = albums,
                        artists = artists,
                        currentSongId = currentSong?.id,
                        onPlayShuffled = viewModel::playShuffled,
                        onPlaySongs = viewModel::playSongs,
                        onSearchMetadata = viewModel::requestSearchMetadata,
                        onEditMetadata = viewModel::requestRenameSong,
                        onDeleteSong = viewModel::requestDeleteSong,
                        onShowAlbumSongs = viewModel::showAlbumSongs,
                        onShowArtistSongs = viewModel::showArtistSongs,
                        modifier = Modifier.weight(1f)
                    )
                }
                LibraryDetailPanel(modifier = Modifier.weight(0.6f))
            }
        }
        WindowSize.Compact -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                LibraryTabs(
                    selectedTab = subTab,
                    onTabSelected = { index ->
                        subTab = index
                        if (index == 1 || index == 2) viewModel.refreshStats()
                    },
                    currentSortType = currentSortType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppSpacing.md, end = AppSpacing.md, top = AppSpacing.xs, bottom = AppSpacing.xxs)
                )

                LibrarySearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = {
                        searchQuery = it
                        if (subTab == 0) viewModel.search(it)
                    },
                    showSearch = showSearch
                )

                LibraryContent(
                    selectedTab = subTab,
                    songs = songs,
                    albums = albums,
                    artists = artists,
                    currentSongId = currentSong?.id,
                    onPlayShuffled = viewModel::playShuffled,
                    onPlaySongs = viewModel::playSongs,
                    onSearchMetadata = viewModel::requestSearchMetadata,
                    onEditMetadata = viewModel::requestRenameSong,
                    onDeleteSong = viewModel::requestDeleteSong,
                    onShowAlbumSongs = viewModel::showAlbumSongs,
                    onShowArtistSongs = viewModel::showArtistSongs,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    LibraryDialogs(viewModel)
}
