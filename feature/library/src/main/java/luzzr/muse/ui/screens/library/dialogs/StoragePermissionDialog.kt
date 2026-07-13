package luzzr.muse.ui.screens.library.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import luzzr.muse.feature.library.R
import luzzr.muse.ui.components.MuseAlertDialog

@Composable
fun StoragePermissionDialog(onGrant: () -> Unit, onDismiss: () -> Unit) {
    MuseAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.permission_storage),
        text = stringResource(R.string.permission_storage_rationale),
        confirmLabel = stringResource(R.string.permission_go_settings),
        dismissLabel = stringResource(R.string.metadata_cancel),
        onConfirm = onGrant
    )
}
