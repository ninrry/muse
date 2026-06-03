package luzzr.muse.di

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable theme state extracted from [luzzr.muse.MuseApp] into an
 * injectable singleton so that any Hilt-managed component (ViewModels,
 * Composables via entry-point) can observe or toggle the dark theme.
 */
@Singleton
class ThemeManager @Inject constructor() {

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }
}
