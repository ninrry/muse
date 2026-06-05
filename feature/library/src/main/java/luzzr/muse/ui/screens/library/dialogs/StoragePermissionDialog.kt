package luzzr.muse.ui.screens.library.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import luzzr.muse.feature.library.R

@Composable
fun StoragePermissionDialog(onGrant: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_storage)) },
        text = {
            Text(stringResource(R.string.permission_storage_rationale))
        },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text(stringResource(R.string.permission_go_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.metadata_cancel))
            }
        }
    )
}
