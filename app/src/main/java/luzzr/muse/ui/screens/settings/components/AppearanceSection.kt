package luzzr.muse.ui.screens.settings.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.ui.screens.settings.SettingsViewModel

@Composable
@Suppress("UNUSED_PARAMETER")
fun AppearanceSection(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    if (viewModel.isDarkThemeSupported) {
        SettingItem(
            icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
            title = "深色主题",
            subtitle = if (isDarkTheme) "当前为深色模式" else "当前为浅色模式",
            onClick = { viewModel.toggleTheme() }
        )
    }
}
