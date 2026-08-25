package luzzr.muse.domain.preferences

import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

interface ThemePreferenceController {
    val isDarkTheme: StateFlow<Boolean>
    val themeMode: StateFlow<ThemeMode>

    fun toggleTheme()
    fun setThemeMode(mode: ThemeMode)
}
