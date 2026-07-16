package luzzr.muse.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 全局「减少动效」：系统 Animator duration scale = 0 时为 true。
 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember {
        mutableStateOf(readAnimatorScaleZero(context.contentResolver))
    }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readAnimatorScaleZero(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        reduce = readAnimatorScaleZero(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

private fun readAnimatorScaleZero(resolver: android.content.ContentResolver): Boolean {
    return try {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
        false
    }
}

@Composable
fun ProvideReduceMotion(content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}
