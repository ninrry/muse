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

/**
 * Refined format badge with subtle pill design.
 * Uses surfaceVariant background with onSurfaceVariant text for low visual noise.
 * Fixed height 18dp, minimum width 40dp, fully rounded (9dp radius).
 */
@Composable
fun FormatBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    val displayText = when {
        text == "M4A/AAC" -> "AAC"
        text == "OGG Vorbis" -> "OGG"
        text.startsWith("MP3") -> "MP3"
        text == "M4A" -> "AAC"
        text.contains("PCM") -> "WAV"
        else -> text
    }

    Box(
        modifier = modifier
            .heightIn(min = 18.dp, max = 18.dp)
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
