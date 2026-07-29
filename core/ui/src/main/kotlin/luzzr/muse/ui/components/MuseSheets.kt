package luzzr.muse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import luzzr.muse.ui.R
import luzzr.muse.ui.animation.MotionSheet
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens

/**
 * 统一底部弹层：唱片馆圆角 + 一致内边距。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuseBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MuseShapeTokens.Sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = AppSpacing.xlg)
        ) {
            AnimatedVisibility(
                visible = true,
                enter = if (LocalReduceMotion.current) EnterTransition.None else MotionSheet.contentEnter,
                exit = if (LocalReduceMotion.current) ExitTransition.None else MotionSheet.contentExit
            ) {
                Column {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = AppSpacing.sm)
                        )
                    }
                    content()
                }
            }
        }
    }
}

/**
 * 统一确认对话框：主次按钮层级一致。
 */
@Composable
fun MuseAlertDialog(
    onDismiss: () -> Unit,
    title: String,
    text: String? = null,
    confirmLabel: String = stringResource(R.string.ui_dialog_confirm),
    dismissLabel: String = stringResource(R.string.ui_dialog_cancel),
    onConfirm: (() -> Unit)? = null,
    confirmIsDestructive: Boolean = false,
    content: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MuseShapeTokens.Dialog,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            when {
                content != null -> content()
                text != null -> Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (onConfirm != null) {
                TextButton(onClick = onConfirm) {
                    Text(
                        text = confirmLabel,
                        color = if (confirmIsDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ui_dialog_close))
                }
            }
        },
        dismissButton = if (onConfirm != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel)
                }
            }
        } else {
            null
        }
    )
}

@Composable
fun MuseSheetSpacer() {
    Spacer(Modifier.height(AppSpacing.sm))
}
