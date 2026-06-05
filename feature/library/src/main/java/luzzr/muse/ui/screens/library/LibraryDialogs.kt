package luzzr.muse.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.feature.library.R
import luzzr.muse.ui.screens.library.dialogs.DeleteSongDialog
import luzzr.muse.ui.screens.library.dialogs.StoragePermissionDialog
import luzzr.muse.ui.state.asString

@Composable
fun LibraryDialogs(viewModel: LibraryViewModel) {
    val songToDelete by viewModel.songToDelete.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    val songToEdit by viewModel.songToEdit.collectAsStateWithLifecycle()
    val editError by viewModel.editError.collectAsStateWithLifecycle()
    val isSavingMetadata by viewModel.isSavingMetadata.collectAsStateWithLifecycle()
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
            error = deleteError?.asString(),
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }

    songToEdit?.let { song ->
        MetadataEditDialog(
            song = song,
            error = editError?.asString(),
            isSaving = isSavingMetadata,
            onGenerateDefaultCover = viewModel::generateDefaultCoverPreview,
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
            error = metadataError?.asString(),
            onApply = { viewModel.applyMetadataResult(it) },
            onDismiss = { viewModel.closeMetadataSheet() }
        )
    }

    albumDetail?.let { (album, albumSongs) ->
        SongListSheet(
            title = stringResource(R.string.library_album_title, album.title),
            subtitle = stringResource(R.string.library_song_count, albumSongs.size),
            songs = albumSongs,
            onSongClick = { index -> viewModel.playSongs(albumSongs, index) },
            onDismiss = { viewModel.dismissAlbumDetail() }
        )
    }

    artistDetail?.let { (artist, artistSongs) ->
        SongListSheet(
            title = stringResource(R.string.library_artist_title, artist.name),
            subtitle = stringResource(R.string.library_song_count, artistSongs.size),
            songs = artistSongs,
            onSongClick = { index -> viewModel.playSongs(artistSongs, index) },
            onDismiss = { viewModel.dismissArtistDetail() }
        )
    }
}
