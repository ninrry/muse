package luzzr.muse.ui.modifier

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Applies a horizontal gradient that fades from [accentColor] at the given [alpha]
 * through a reduced-alpha midpoint to fully transparent.
 *
 * Commonly used for item highlight backgrounds (e.g., current lyrics line).
 *
 * @param accentColor The base color for the gradient start.
 * @param alpha       Overall opacity applied to the start of the gradient;
 *                    the midpoint uses 30 % of this value.
 */
fun Modifier.highlightGradient(accentColor: Color, alpha: Float): Modifier = this.background(
    brush = Brush.horizontalGradient(
        colors = listOf(
            accentColor.copy(alpha = alpha),
            accentColor.copy(alpha = alpha * 0.3f),
            Color.Transparent
        )
    )
)
