package luzzr.muse.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import luzzr.muse.R
import luzzr.muse.data.model.Song
import luzzr.muse.ui.theme.AppSpacing

/**
 * Dialog that lets the user review and edit search terms before
 * submitting a metadata search. Pre-filled with the song's current
 * title and artist.
 *
 * Used in LibraryScreen before calling [MetadataFetcher.searchExact].
 */
@Composable
fun SearchTermsDialog(song: Song, onConfirm: (title: String, artist: String) -> Unit, onDismiss: () -> Unit) {
    var searchTitle by remember(song.id) { mutableStateOf(song.title) }
    var searchArtist by remember(song.id) { mutableStateOf(song.artist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metadata_search_results)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedTextField(
                    value = searchTitle,
                    onValueChange = { searchTitle = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_song_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.metadata_input_song)) }
                )
                OutlinedTextField(
                    value = searchArtist,
                    onValueChange = { searchArtist = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_artist)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.metadata_input_artist)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(searchTitle, searchArtist) },
                enabled = searchTitle.isNotBlank()
            ) {
                Text(stringResource(R.string.metadata_confirm_search))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.metadata_cancel))
            }
        }
    )
}
