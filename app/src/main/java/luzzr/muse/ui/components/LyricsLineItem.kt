package luzzr.muse.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import luzzr.muse.ui.modifier.highlightGradient
import luzzr.muse.ui.theme.AppSpacing
import kotlinx.coroutines.delay

private val linePaddingV = AppSpacing.md
private val linePaddingH = 28.dp
private const val ACCENT_BAR_WIDTH = 4f // dp

/**
 * Renders a single synchronized lyrics line with animation polish:
 * - **Karaoke fill** �?text color blends as [lineProgress] advances
 * - **Breathing pulse** �?accent bar glows with an infinite pulse (current line only)
 * - **Spring-animated** size, weight, scale, alpha, and letter-spacing transitions
 * - **Tap bounce** �?brief elastic scale-down on tap
 *
 * All parameters are stable types, making this composable skippable.
 * The infinite-transition for the breathing pulse lives inside [AccentBarPulse],
 * which is only composed when [isCurrent] is `true` �?avoiding unnecessary
 * animation ticks on non-current lines.
 *
 * @param text         Lyrics text for this line.
 * @param isCurrent    Whether this is the currently playing line.
 * @param isPast       Whether this line has already been played.
 * @param lineProgress Playback progress through this line (0�?). Only meaningful
 *                     when [isCurrent] is `true`; pass `0f` otherwise.
 * @param onClick      Callback invoked when the line is tapped.
 * @param modifier     Optional [Modifier] applied to the root [Surface].
 */
@Composable
fun LyricsLineItem(
    text: String,
    isCurrent: Boolean,
    isPast: Boolean,
    lineProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCalibrationMode: Boolean = false
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    // === Animated visual properties ===
    val targetAlpha = when {
        isCurrent -> 1f
        isPast -> 0.60f
        else -> 0.80f
    }
    val targetScale = if (isCurrent) 1.15f else 0.90f
    val targetHighlightAlpha = if (isCurrent) 0.12f else 0f

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(350),
        label = "line_alpha"
    )
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "line_scale"
    )
    val accentBarWidth by animateDpAsState(
        targetValue = if (isCurrent) ACCENT_BAR_WIDTH.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "accent_bar"
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = targetHighlightAlpha,
        animationSpec = tween(280),
        label = "highlight_alpha"
    )

    // Tap bounce animation
    var isTapped by remember { mutableStateOf(false) }
    val tapBounce by animateFloatAsState(
        targetValue = if (isTapped) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tap_bounce"
    )

    val animatedColor by animateColorAsState(
        targetValue = if (isCurrent) primary else onSurface,
        animationSpec = tween(350),
        label = "text_color"
    )
    val interactionSource = remember { MutableInteractionSource() }

    // Current line keeps the vibrant primary color throughout its playback to avoid dimming and flickering.
    val karaokeColor = if (isCurrent) {
        primary
    } else {
        animatedColor.copy(alpha = animatedAlpha)
    }

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(AppSpacing.md),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xs)
            .zIndex(if (isCurrent) 1f else 0f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(tapBounce)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        bottomStart = 20.dp,
                        topEnd = 0.dp,
                        bottomEnd = 0.dp
                    )
                )
                .highlightGradient(primary, highlightAlpha)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    isTapped = true
                    onClick()
                }
        ) {
            // Accent bar with breathing pulse �?only composed for the current
            // line so that the infiniteTransition does not tick on other items.
            if (isCurrent) {
                AccentBarPulse(
                    accentBarWidth = accentBarWidth,
                    primaryColor = primary,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Karaoke fill overlay: a semi-transparent bar sweeps left-to-right at the bottom
            if (isCurrent && lineProgress in 0.02f..0.98f) {
                KaraokeFillOverlay(
                    lineProgress = lineProgress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
                    .padding(vertical = linePaddingV, horizontal = linePaddingH),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    color = karaokeColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCalibrationMode && !isCurrent) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "对齐此行",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    LaunchedEffect(isTapped) {
        if (isTapped) {
            delay(200)
            isTapped = false
        }
    }
}

/**
 * Accent bar with breathing pulse animation.
 *
 * Extracted as a separate composable so that [rememberInfiniteTransition]
 * only lives in the composition while the line is current, avoiding
 * unnecessary animation ticks on non-current lyrics lines.
 */
@Composable
private fun AccentBarPulse(accentBarWidth: Dp, primaryColor: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "accent_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "accent_pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "accent_pulse_scale"
    )

    Canvas(
        modifier = modifier
            .width(accentBarWidth * pulseScale)
            .height(40.dp)
    ) {
        drawRoundRect(
            color = primaryColor.copy(alpha = pulseAlpha),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
    }
}

/**
 * Karaoke fill overlay �?a semi-transparent bar that sweeps left-to-right
 * based on [lineProgress].
 */
@Composable
private fun KaraokeFillOverlay(lineProgress: Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth(0.6f)
            .height(3.dp)
    ) {
        val barWidth = size.width * lineProgress
        val barHeight = size.height
        // Background track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            size = size,
            cornerRadius = CornerRadius(1.5f.dp.toPx(), 1.5f.dp.toPx())
        )
        // Progress track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.35f),
            size = Size(barWidth.coerceAtLeast(4f), barHeight),
            cornerRadius = CornerRadius(1.5f.dp.toPx(), 1.5f.dp.toPx())
        )
    }
}
