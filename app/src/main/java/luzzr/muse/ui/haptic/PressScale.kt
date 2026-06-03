package luzzr.muse.ui.haptic

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import luzzr.muse.ui.animation.MotionDuration

/**
 * Adds subtle press-scale feedback to any clickable element.
 *
 * MUST be paired with a clickable/combinedClickable that accepts the
 * same [MutableInteractionSource]. Example:
 *
 * ```kotlin
 * val interactionSource = remember { MutableInteractionSource() }
 * Modifier
 *     .pressScale(interactionSource)
 *     .clickable(interactionSource, indication = null) { onClick() }
 * ```
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, scaleTarget: Float = 0.96f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleTarget else 1f,
        animationSpec = tween(durationMillis = MotionDuration.short),
        label = "press_scale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
