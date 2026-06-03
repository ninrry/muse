package luzzr.muse.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@Composable
@Suppress("UNUSED_PARAMETER")
fun FormatsSection(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
        shape = RoundedCornerShape(MuseDimens.CardCornerRadius)
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Text(
                "MP3, AAC, M4A, Opus, FLAC, WAV, ALAC, OGG",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
