package luzzr.muse.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Animated 3-bar equalizer indicator for the currently playing song.
 * Each bar pulses at a slightly different rate for a natural "EQ" look.
 *
 * @param color The color of the bars
 * @param modifier Modifier for sizing
 * @param enabled Whether to animate (false for static placeholder, saves GPU)
 */
@Composable
fun NowPlayingBars(color: Color, modifier: Modifier = Modifier, enabled: Boolean = true) {
    // Only run animation when enabled (visible). Static mode shows minimum height.
    val speeds = listOf(700, 500, 620)

    // Use nullable animation values to handle both enabled/disabled cases
    val phaseValues: List<Float> = if (enabled) {
        val infinite = rememberInfiniteTransition(label = "eq_bars")
        speeds.map { ms ->
            infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = ms, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$ms"
            ).value
        }
    } else {
        // Static fallback: all bars at minimum height (0.35)
        speeds.map { 0.35f }
    }

    Canvas(modifier = modifier) {
        val barCount = 3
        val barWidthPx = size.width / (barCount * 2f - 1f)
        phaseValues.forEachIndexed { index, phase ->
            val fraction = 0.35f + phase * 0.65f
            val barHeight = size.height * fraction
            val x = index * barWidthPx * 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidthPx, barHeight),
                cornerRadius = CornerRadius(barWidthPx / 2f)
            )
        }
    }
}
