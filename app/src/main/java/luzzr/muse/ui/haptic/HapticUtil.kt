package luzzr.muse.ui.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Lightweight haptic feedback for key interactions.
 * Falls back gracefully on devices without vibrator hardware.
 */
object HapticUtil {

    fun performClick(v: View) {
        try {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) { }
    }

    fun performHeavyClick(v: View) {
        try {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) { }
    }

    fun performTick(v: View) {
        try {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Exception) { }
    }

    /** Programmatic vibration for custom intensity */
    fun vibrate(context: Context, durationMs: Long = 20, amplitude: Int = 100) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, amplitude)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) { }
    }
}
