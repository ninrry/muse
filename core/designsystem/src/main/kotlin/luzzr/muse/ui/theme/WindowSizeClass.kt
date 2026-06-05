package luzzr.muse.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

enum class WindowSize { Compact, Medium, Expanded }

@Composable
fun currentWindowSize(): WindowSize {
    val windowWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    return when {
        windowWidth >= 840.dp -> WindowSize.Expanded
        windowWidth >= 600.dp -> WindowSize.Medium
        else -> WindowSize.Compact
    }
}
