package luzzr.muse.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import luzzr.muse.R
import luzzr.muse.player.SleepTimerMode
import luzzr.muse.ui.theme.AppSpacing

/**
 * Dialog for selecting sleep timer duration.
 */
@Composable
fun SleepTimerDialog(currentMode: SleepTimerMode?, onSelect: (SleepTimerMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.sleep_timer_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                val options = listOf(
                    SleepTimerMode.OFF,
                    SleepTimerMode.MIN_15,
                    SleepTimerMode.MIN_30,
                    SleepTimerMode.MIN_60,
                    SleepTimerMode.END_OF_TRACK
                )

                options.forEach { mode ->
                    val isActive = currentMode == mode
                    val modeLabel = when (mode) {
                        SleepTimerMode.OFF -> stringResource(R.string.sleep_timer_off)
                        SleepTimerMode.MIN_15 -> stringResource(R.string.sleep_timer_15)
                        SleepTimerMode.MIN_30 -> stringResource(R.string.sleep_timer_30)
                        SleepTimerMode.MIN_60 -> stringResource(R.string.sleep_timer_60)
                        SleepTimerMode.END_OF_TRACK -> stringResource(R.string.sleep_timer_end)
                    }
                    Button(
                        onClick = { onSelect(mode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppSpacing.xxlg),
                        shape = RoundedCornerShape(AppSpacing.lg),
                        colors = if (isActive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            ButtonDefaults.textButtonColors()
                        }
                    ) {
                        Text(
                            modeLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                if (currentMode != null && currentMode != SleepTimerMode.OFF) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = stringResource(R.string.sleep_timer_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.metadata_cancel))
            }
        }
    )
}
