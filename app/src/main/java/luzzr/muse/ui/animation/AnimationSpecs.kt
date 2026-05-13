package luzzr.muse.ui.animation

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

// ============================================================
// MD3 Motion Specification — Muse
// ============================================================
// Reference: https://m3.material.io/styles/motion/easing-and-duration
//
// Tokens:
//   duration.short1 =  50ms  (micro-interactions)
//   duration.medium1 = 200ms (small element transitions)
//   duration.medium2 = 300ms (list items, cards)
//   duration.long1   = 350ms (page transitions, dialogs)
//   duration.long2   = 400ms (full-screen transitions)
// ============================================================

// --- Duration Tokens (ms) ---
object MotionDuration {
    val micro = 50
    val short = 100
    val medium1 = 200
    val medium2 = 300
    val long1 = 350
    val long2 = 400
    val long3 = 500
}

// --- Easing Curves ---
// Standard: 0.2, 0.0, 0.0, 1.0  (MD3 Standard)
// Emphasized: 0.2, 0.0, 0.0, 1.0  (same as standard in Compose)
// Accelerate: 0.4, 0.0, 1.0, 1.0
// Decelerate: 0.0, 0.0, 0.2, 1.0
object MotionEasing {
    val standard = FastOutSlowInEasing       // MD3 standard
    val accelerate = EaseInCubic
    val decelerate = EaseOutCubic
    val linear = LinearEasing
    val springy = EaseOutBack
    val bouncy = EaseInOutCubic
}

// --- Tween Presets ---
object MotionTween {
    val micro = tween<Float>(durationMillis = MotionDuration.micro, easing = MotionEasing.standard)
    val short = tween<Float>(durationMillis = MotionDuration.short, easing = MotionEasing.standard)
    val medium1 = tween<Float>(durationMillis = MotionDuration.medium1, easing = MotionEasing.standard)
    val medium2 = tween<Float>(durationMillis = MotionDuration.medium2, easing = MotionEasing.standard)
    val long1 = tween<Float>(durationMillis = MotionDuration.long1, easing = MotionEasing.standard)
    val long2 = tween<Float>(durationMillis = MotionDuration.long2, easing = MotionEasing.standard)
    val long3 = tween<Float>(durationMillis = MotionDuration.long3, easing = MotionEasing.standard)

    // Elastic spring for playful elements (shuffle, play)
    val spring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
}

// --- Page Transition Presets ---
// MD3 FadeThrough: screens without strong spatial relationship
object MotionPageTransition {
    val fadeThrough: EnterTransition = fadeIn(animationSpec = tween(MotionDuration.long2))
    val fadeThroughExit: ExitTransition = fadeOut(animationSpec = tween(MotionDuration.medium1))
}

// --- MiniPlayer Slide ---
object MotionMiniPlayer {
    val slideIn = slideInVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        initialOffsetY = { fullHeight -> fullHeight }
    )

    val slideOut = slideOutVertically(
        animationSpec = tween(durationMillis = MotionDuration.medium2),
        targetOffsetY = { fullHeight -> fullHeight }
    )

    val fadeIn = fadeIn(animationSpec = tween(durationMillis = MotionDuration.medium2, easing = EaseOutCubic))
    val fadeOut = fadeOut(animationSpec = tween(durationMillis = MotionDuration.short, easing = EaseInCubic))
}

// --- Card / Item Spring ---
object MotionCard {
    val pressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
