package luzzr.muse.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Detects whether the user has enabled "Remove animations"
 * (Settings > Accessibility > Remove animations / Developer options > Animator duration scale = 0).
 * When true, lyrics and other components should skip spring/tween animations
 * and jump to their target state instantly.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    }
}
