package luzzr.muse.ui

import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

@Composable
fun SystemBarsEffect(isDarkTheme: Boolean) {
    val context = LocalContext.current
    val navBarColor = if (isDarkTheme) Color(0xFF141210) else Color(0xFFFFFFFF)
    val statusBarColor = if (isDarkTheme) Color(0xFF141210) else Color(0xFFFDF8F3)

    LaunchedEffect(isDarkTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = (context as? ComponentActivity)?.window
            window?.let { w ->
                w.navigationBarColor = navBarColor.toArgb()
                w.statusBarColor = statusBarColor.toArgb()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    w.decorView.systemUiVisibility = if (!isDarkTheme) {
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    } else {
                        w.decorView.systemUiVisibility and
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    }
                }
            }
        }
    }
}
