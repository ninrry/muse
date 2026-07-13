package luzzr.muse.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

// ============================================================
// MD3 Motion Specification ->Muse
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
// Reference: https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
//
// MD3 Standard Decelerate: cubic-bezier(0.0, 0.0, 0.2, 1.0) ->entering
// MD3 Standard Accelerate: cubic-bezier(0.4, 0.0, 1.0, 1.0) ->exiting
// MD3 Emphasized Decelerate: cubic-bezier(0.05, 0.7, 0.1, 1.0) ->hero entering
// MD3 Emphasized Accelerate: cubic-bezier(0.3, 0.0, 0.8, 0.15) ->hero exiting
object MotionEasing {
    /** MD3 Standard Decelerate: cubic-bezier(0.0, 0.0, 0.2, 1.0) ->elements entering */
    val standard = FastOutSlowInEasing

    /** MD3 Standard Accelerate: cubic-bezier(0.4, 0.0, 1.0, 1.0) ->elements leaving */
    val accelerate = EaseInCubic

    /** MD3 Emphasized Decelerate: cubic-bezier(0.05, 0.7, 0.1, 1.0) ->hero entering, FAB */
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** MD3 Emphasized Accelerate: cubic-bezier(0.3, 0.0, 0.8, 0.15) ->hero leaving */
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    val decelerate = EaseOutCubic
    val linear = LinearEasing
    val springy = EaseOutBack

    /** Genuine elastic bounce for playful micro-interactions (overshoot + settle) */
    val bouncy = EaseOutBack
}

// --- Tween Presets (MD3-aligned easing selection) ---
// micro/short: standard decelerate (fast, subtle)
// medium1/medium2: standard decelerate (everyday transitions)
// long1: emphasized decelerate (hero entering ->FAB, page transitions)
// long2/long3: emphasized decelerate (full-screen transitions)
object MotionTween {
    val micro = tween<Float>(durationMillis = MotionDuration.micro, easing = MotionEasing.standard)
    val short = tween<Float>(durationMillis = MotionDuration.short, easing = MotionEasing.standard)
    val medium1 = tween<Float>(durationMillis = MotionDuration.medium1, easing = MotionEasing.standard)
    val medium2 = tween<Float>(durationMillis = MotionDuration.medium2, easing = MotionEasing.standard)
    val long1 = tween<Float>(durationMillis = MotionDuration.long1, easing = MotionEasing.emphasizedDecelerate)
    val long2 = tween<Float>(durationMillis = MotionDuration.long2, easing = MotionEasing.emphasizedDecelerate)
    val long3 = tween<Float>(durationMillis = MotionDuration.long3, easing = MotionEasing.emphasizedDecelerate)

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

// --- Lyrics: Apple Music 式流畅排版 ---
object MotionLyrics {
    /** 行聚焦缩放：柔和 spring，无弹跳 */
    val focusScale = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 景深透明度 */
    val depthAlpha = spring<Float>(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 行状态沉降 */
    val lineSettle = tween<Float>(
        durationMillis = MotionDuration.long2,
        easing = MotionEasing.emphasizedDecelerate
    )

    /** 渐进填色：短线性，贴合播放进度 */
    val inkFill = tween<Float>(
        durationMillis = 50,
        easing = LinearEasing
    )

    val karaokeFill = inkFill
    val currentLineSpring = focusScale
    val autoScroll = spring<Float>(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow
    )
    val stateTransition = depthAlpha
}
