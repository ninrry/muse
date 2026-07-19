package luzzr.muse.domain.preferences

import kotlinx.coroutines.flow.StateFlow

/** Persistent visibility preferences for top-level navigation destinations. */
interface NavigationPreferenceController {
    val isAudiobookVisible: StateFlow<Boolean>

    fun setAudiobookVisible(visible: Boolean)
}
