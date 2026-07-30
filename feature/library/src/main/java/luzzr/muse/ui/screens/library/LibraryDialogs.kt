package luzzr.muse.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.domain.model.Playlist
import luzzr.muse.feature.library.R
import luzzr.muse.ui.screens.library.dialogs.DeleteSongDialog
import luzzr.muse.ui.screens.library.dialogs.StoragePermissionDialog
import luzzr.muse.ui.state.asString
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseIcons

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
    val metadataApplying by viewModel.metadataApplying.collectAsStateWithLifecycle()
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
            isApplying = metadataApplying,
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

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    selectedCount: Int = 1,
    isBatchMode: Boolean = false,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Long) -> Unit
) {
    val title = if (isBatchMode && selectedCount > 1) {
        stringResource(R.string.add_to_playlist_batch_title, selectedCount)
    } else {
        stringResource(R.string.add_to_playlist)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (playlists.isEmpty()) {
                Column {
                    Text(
                        stringResource(R.string.no_playlists_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isBatchMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Long press on a song in the library to enter selection mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = {
                                Text(
                                    if (isBatchMode && selectedCount > 1) {
                                        stringResource(R.string.playlist_songs_will_add, selectedCount, playlist.songCount)
                                    } else {
                                        stringResource(R.string.playlist_songs, playlist.songCount)
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(
                                    MuseIcons.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppSpacing.xs))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onPlaylistSelected(playlist.id) }
                        )
                        if (playlist != playlists.last()) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
