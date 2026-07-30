package luzzr.muse.ui.screens.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import luzzr.muse.domain.model.Playlist
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.PlaylistRepository
import luzzr.muse.feature.library.R
import luzzr.muse.ui.screens.library.components.LibraryContent
import luzzr.muse.ui.screens.library.components.LibraryDetailPanel
import luzzr.muse.ui.screens.library.components.LibrarySearchBar
import luzzr.muse.ui.screens.library.components.LibraryTabs
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.WindowSize
import luzzr.muse.ui.theme.currentWindowSize
import luzzr.muse.ui.components.SongListItem
import luzzr.muse.ui.components.SongMenuItem
import luzzr.muse.ui.state.asString
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LibraryRoute(viewModel: LibraryViewModel, showSearch: Boolean, scaffoldPadding: PaddingValues, innerPadding: PaddingValues) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val currentSortType by viewModel.sortType.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Selection mode state
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val lyricsFetchProgress by viewModel.lyricsFetchProgress.collectAsStateWithLifecycle()
    val batchMessage by viewModel.batchMessage.collectAsStateWithLifecycle()
    val selectedSongIds by viewModel.selectedSongIds.collectAsStateWithLifecycle()

    var subTab by remember { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, context) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is LibraryUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message.asString(context))
            }
        }
    }

    LaunchedEffect(batchMessage) {
        val message = batchMessage ?: return@LaunchedEffect
        val displayMessage = when {
            message.startsWith("done:") -> {
                val values = message.removePrefix("done:").split(':').mapNotNull { it.toIntOrNull() }
                if (values.size == 3) {
                    context.getString(R.string.lyrics_batch_done, values[0], values[1], values[2])
                } else {
                    message
                }
            }
            message.startsWith("skip_all:") -> {
                val skipped = message.removePrefix("skip_all:").toIntOrNull() ?: 0
                context.getString(R.string.lyrics_batch_done, 0, skipped, 0)
            }
            else -> message
        }
        snackbarHostState.showSnackbar(displayMessage)
        viewModel.clearBatchMessage()
    }

    val windowSize = currentWindowSize()

    Box(modifier = Modifier.fillMaxSize()) {
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
                        onSortChange = viewModel::cycleSortType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = luzzr.muse.ui.theme.MuseDimens.ScreenPaddingH, end = luzzr.muse.ui.theme.MuseDimens.ScreenPaddingH, top = AppSpacing.xs, bottom = AppSpacing.xxs)
                    )

                    LibrarySearchBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = {
                            searchQuery = it
                            if (subTab == 0) viewModel.search(it)
                        },
                        showSearch = true
                    )

                    LibraryContent(
                        selectedTab = subTab,
                        songs = if (searchQuery.isBlank()) songs else searchResults,
                        albums = albums,
                        artists = artists,
                        currentSongId = currentSong?.id,
                        onPlaySongs = viewModel::playSongs,
                        onShareSong = { shareSongFile(context, it) },
                        onSearchMetadata = viewModel::requestSearchMetadata,
                        onEditMetadata = viewModel::requestRenameSong,
                        onDeleteSong = viewModel::requestDeleteSong,
                        onAddToPlaylist = viewModel::requestAddToPlaylist,
                        onShowAlbumSongs = viewModel::showAlbumSongs,
                        onShowArtistSongs = viewModel::showArtistSongs,
                        isSelectionMode = isSelectionMode,
                        selectedSongIds = selectedSongIds,
                        onEnterSelectionMode = viewModel::enterSelectionMode,
                        onToggleSelection = viewModel::toggleSongSelection,
                        onExitSelectionMode = viewModel::exitSelectionMode,
                         onAddSelectedToPlaylist = viewModel::requestAddSelectedToPlaylist,
                         onSelectAll = viewModel::selectAllSongs,
                         onFetchLyricsForSelected = viewModel::fetchLyricsForSelected,
                         lyricsFetchProgress = lyricsFetchProgress,
                         sortType = currentSortType,
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
                    .padding(bottom = scaffoldPadding.calculateBottomPadding())
            ) {
                LibrarySearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = {
                        searchQuery = it
                        if (subTab == 0) viewModel.search(it)
                    },
                    showSearch = true,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = AppSpacing.xs)
                )

                Spacer(Modifier.height(AppSpacing.xs))

                LibraryTabs(
                    selectedTab = subTab,
                    onTabSelected = { index ->
                        subTab = index
                        if (index == 1 || index == 2) viewModel.refreshStats()
                    },
                    currentSortType = currentSortType,
                    onSortChange = viewModel::cycleSortType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = luzzr.muse.ui.theme.MuseDimens.ScreenPaddingH)
                )

                Spacer(Modifier.height(AppSpacing.xxs))

                LibraryContent(
                    selectedTab = subTab,
                    songs = if (searchQuery.isBlank()) songs else searchResults,
                    albums = albums,
                    artists = artists,
                    currentSongId = currentSong?.id,
                    onPlaySongs = viewModel::playSongs,
                    onShareSong = { shareSongFile(context, it) },
                    onSearchMetadata = viewModel::requestSearchMetadata,
                    onEditMetadata = viewModel::requestRenameSong,
                    onDeleteSong = viewModel::requestDeleteSong,
                    onAddToPlaylist = viewModel::requestAddToPlaylist,
                    onShowAlbumSongs = viewModel::showAlbumSongs,
                    onShowArtistSongs = viewModel::showArtistSongs,
                    isSelectionMode = isSelectionMode,
                    selectedSongIds = selectedSongIds,
                    onEnterSelectionMode = viewModel::enterSelectionMode,
                    onToggleSelection = viewModel::toggleSongSelection,
                    onExitSelectionMode = viewModel::exitSelectionMode,
                    onAddSelectedToPlaylist = viewModel::requestAddSelectedToPlaylist,
                    onSelectAll = viewModel::selectAllSongs,
                    onFetchLyricsForSelected = viewModel::fetchLyricsForSelected,
                    lyricsFetchProgress = lyricsFetchProgress,
                    sortType = currentSortType,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = innerPadding.calculateBottomPadding())
        )
    }

    // Single song add to playlist dialog
    val songToAddToPlaylist by viewModel.songToAddToPlaylist.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    if (songToAddToPlaylist != null) {
        AddToPlaylistDialog(
            playlists = playlists,
            selectedCount = if (isSelectionMode) selectedSongIds.size else 1,
            isBatchMode = isSelectionMode,
            onDismiss = {
                if (isSelectionMode) {
                    viewModel.cancelAddToPlaylist()
                } else {
                    viewModel.cancelAddToPlaylist()
                }
            },
            onPlaylistSelected = { playlistId ->
                if (isSelectionMode && selectedSongIds.isNotEmpty()) {
                    viewModel.addSelectedSongsToPlaylist(playlistId)
                } else {
                    viewModel.addSongToPlaylist(playlistId)
                }
            }
        )
    }

    LibraryDialogs(viewModel)
}

private fun shareSongFile(context: Context, song: Song) {
    val songUri = song.uri.toUri()
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, songUri)
        clipData = ClipData.newUri(context.contentResolver, song.title, songUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.action_share_song)))
}
