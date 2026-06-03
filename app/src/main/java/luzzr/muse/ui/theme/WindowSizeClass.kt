package luzzr.muse.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class WindowSize { Compact, Medium, Expanded }

@Composable
fun currentWindowSize(): WindowSize {
    val config = LocalConfiguration.current
    return when {
        config.screenWidthDp >= 840 -> WindowSize.Expanded
        config.screenWidthDp >= 600 -> WindowSize.Medium
        else -> WindowSize.Compact
    }
}
