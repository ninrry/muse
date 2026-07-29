package luzzr.muse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import luzzr.muse.ui.theme.AppSpacing

/** Lossless audio formats ->displayed with a distinct accent to signal quality */
private val LOSSLESS_FORMATS = setOf("FLAC", "WAV", "APE", "ALAC", "AIFF", "DSD")

/**
 * Refined format badge with pill design.
 * - Lossless formats (FLAC, WAV, APE) get a **tertiaryContainer** accent to signal high quality
 * - Lossy formats (MP3, AAC, OGG) get a subtle **surfaceVariant** tone
 */
@Composable
fun FormatBadge(text: String, modifier: Modifier = Modifier) {
    val displayText = when {
        text == "M4A/AAC" -> "AAC"
        text == "OGG Vorbis" -> "OGG"
        text.startsWith("MP3") -> "MP3"
        text == "M4A" -> "AAC"
        text.contains("PCM") -> "WAV"
        else -> text
    }

    val isLossless = LOSSLESS_FORMATS.any { displayText.equals(it, ignoreCase = true) }
    val bgColor = if (isLossless) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val textColor = if (isLossless) {
        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier
            .heightIn(min = 18.dp, max = 18.dp)
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = AppSpacing.xxxs)
        )
    }
}
