package luzzr.muse.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

@Composable
fun SystemBarsEffect(isDarkTheme: Boolean) {
    val context = LocalContext.current

    LaunchedEffect(isDarkTheme) {
        val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}
