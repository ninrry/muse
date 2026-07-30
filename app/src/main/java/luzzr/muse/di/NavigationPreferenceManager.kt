package luzzr.muse.di

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.domain.preferences.NavigationPreferenceController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NavigationPreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) : NavigationPreferenceController {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _isAudiobookVisible = MutableStateFlow(
        prefs.getBoolean(KEY_AUDIOBOOK_VISIBLE, true)
    )

    override val isAudiobookVisible: StateFlow<Boolean> = _isAudiobookVisible.asStateFlow()

    override fun setAudiobookVisible(visible: Boolean) {
        _isAudiobookVisible.value = visible
        prefs.edit { putBoolean(KEY_AUDIOBOOK_VISIBLE, visible) }
    }

    private companion object {
        const val PREFS_NAME = "navigation_prefs"
        const val KEY_AUDIOBOOK_VISIBLE = "audiobook_visible"
    }
}
