package luzzr.muse.ui.screens.library.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.library.R
import luzzr.muse.ui.components.MuseAlertDialog

@Composable
fun DeleteSongDialog(song: Song, error: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MuseAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.library_delete_song),
        confirmLabel = stringResource(R.string.library_delete_yes),
        dismissLabel = stringResource(R.string.library_delete_no),
        onConfirm = onConfirm,
        confirmIsDestructive = true,
        content = {
            Column {
                Text(
                    text = stringResource(R.string.library_delete_confirm, song.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}
