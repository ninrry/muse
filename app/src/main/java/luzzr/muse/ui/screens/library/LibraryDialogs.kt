package luzzr.muse.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.ui.screens.library.dialogs.DeleteSongDialog
import luzzr.muse.ui.screens.library.dialogs.StoragePermissionDialog

@Composable
fun LibraryDialogs(viewModel: LibraryViewModel) {
    val songToDelete by viewModel.songToDelete.collectAsStateWithLifecycle()
    val songToRename by viewModel.songToRename.collectAsStateWithLifecycle()
    val songToEdit by viewModel.songToEdit.collectAsStateWithLifecycle()
    val editError by viewModel.editError.collectAsStateWithLifecycle()
    val needsStoragePermission by viewModel.needsStoragePermission.collectAsStateWithLifecycle()
    val showSearchTerms by viewModel.showSearchTermsDialog.collectAsStateWithLifecycle()
    val metadataResults by viewModel.metadataResults.collectAsStateWithLifecycle()
    val metadataLoading by viewModel.metadataLoading.collectAsStateWithLifecycle()
    val metadataError by viewModel.metadataError.collectAsStateWithLifecycle()
    val metadataSong by viewModel.metadataSong.collectAsStateWithLifecycle()
    val albumDetail by viewModel.albumDetail.collectAsStateWithLifecycle()
    val artistDetail by viewModel.artistDetail.collectAsStateWithLifecycle()

    songToDelete?.let { song ->
        DeleteSongDialog(
            song = song,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }

    songToRename?.let { song ->
        viewModel.requestEditMetadata(song)
        viewModel.cancelRename()
    }

    songToEdit?.let { song ->
        MetadataEditDialog(
            song = song,
            error = editError,
            onSave = { title, artist, album, yearStr, genre, artworkBytes ->
                viewModel.saveEditedMetadata(title, artist, album, yearStr, genre, artworkBytes)
            },
            onDismiss = { viewModel.cancelEditMetadata() }
        )
    }

    if (needsStoragePermission) {
        StoragePermissionDialog(
            onGrant = { viewModel.requestStoragePermission() },
            onDismiss = { viewModel.dismissPermissionDialog() }
        )
    }

    showSearchTerms?.let { song ->
        SearchTermsDialog(
            song = song,
            onConfirm = { title, artist ->
                viewModel.searchMetadataExact(title, artist)
            },
            onDismiss = { viewModel.cancelSearchTerms() }
        )
    }

    if (metadataSong != null) {
        MetadataResultSheet(
            song = metadataSong!!,
            results = metadataResults,
            isLoading = metadataLoading,
            error = metadataError,
            onApply = { viewModel.applyMetadataResult(it) },
            onDismiss = { viewModel.closeMetadataSheet() }
        )
    }

    albumDetail?.let { (album, albumSongs) ->
        SongListSheet(
            title = "专辑: ${album.title}",
            subtitle = "${albumSongs.size}首歌曲",
            songs = albumSongs,
            onSongClick = { index -> viewModel.playSongs(albumSongs, index) },
            onDismiss = { viewModel.dismissAlbumDetail() }
        )
    }

    artistDetail?.let { (artist, artistSongs) ->
        SongListSheet(
            title = "艺术家: ${artist.name}",
            subtitle = "${artistSongs.size}首歌曲",
            songs = artistSongs,
            onSongClick = { index -> viewModel.playSongs(artistSongs, index) },
            onDismiss = { viewModel.dismissArtistDetail() }
        )
    }
}
