package luzzr.muse.ui.screens.library.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import luzzr.muse.R
import luzzr.muse.data.model.Song

@Composable
fun DeleteSongDialog(
    song: Song,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_song)) },
        text = { Text(stringResource(R.string.library_delete_confirm, song.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.library_delete_yes), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_delete_no)) }
        }
    )
}
