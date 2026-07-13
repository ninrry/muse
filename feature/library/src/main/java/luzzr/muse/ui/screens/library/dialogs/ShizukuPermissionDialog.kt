package luzzr.muse.ui.screens.library.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import luzzr.muse.feature.library.R
import luzzr.muse.ui.components.MuseAlertDialog

@Composable
fun ShizukuPermissionDialog(onGrant: () -> Unit, onDismiss: () -> Unit) {
    MuseAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.permission_shizuku),
        text = stringResource(R.string.permission_shizuku_rationale),
        confirmLabel = stringResource(R.string.permission_shizuku_grant),
        dismissLabel = stringResource(R.string.metadata_cancel),
        onConfirm = onGrant
    )
}