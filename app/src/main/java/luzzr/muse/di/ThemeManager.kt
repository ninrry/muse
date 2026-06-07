package luzzr.muse.di

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.domain.preferences.ThemeMode
import luzzr.muse.domain.preferences.ThemePreferenceController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable theme state extracted from [luzzr.muse.MuseApp] into an
 * injectable singleton so that any Hilt-managed component (ViewModels,
 * Composables via entry-point) can observe or toggle the dark theme.
 * Implements persistent configuration and system-theme auto-detection.
 */
@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ThemePreferenceController, ComponentCallbacks {

    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(calculateIsDarkTheme(_themeMode.value))
    override val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    init {
        context.registerComponentCallbacks(this)
    }

    private fun loadThemeMode(): ThemeMode {
        val raw = prefs.getInt("theme_mode", 0) // 0: System, 1: Light, 2: Dark
        return when (raw) {
            1 -> ThemeMode.LIGHT
            2 -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    private fun saveThemeMode(mode: ThemeMode) {
        val value = when (mode) {
            ThemeMode.SYSTEM -> 0
            ThemeMode.LIGHT -> 1
            ThemeMode.DARK -> 2
        }
        prefs.edit { putInt("theme_mode", value) }
    }

    private fun calculateIsDarkTheme(mode: ThemeMode): Boolean {
        return when (mode) {
            ThemeMode.SYSTEM -> isSystemDark()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }

    private fun isSystemDark(): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateThemeState() {
        val currentMode = _themeMode.value
        _isDarkTheme.value = calculateIsDarkTheme(currentMode)
    }

    override fun toggleTheme() {
        val nextMode = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        _themeMode.value = nextMode
        saveThemeMode(nextMode)
        updateThemeState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        updateThemeState()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        // No-op: required by ComponentCallbacks interface
    }
}
