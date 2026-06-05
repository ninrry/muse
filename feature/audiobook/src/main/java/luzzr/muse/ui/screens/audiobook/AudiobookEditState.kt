package luzzr.muse.ui.screens.audiobook

import luzzr.muse.domain.model.Song
import luzzr.muse.ui.state.UiText

data class AudiobookEditState(
    val song: Song? = null,
    val error: UiText? = null,
    val needsStoragePermission: Boolean = false,
    val isSaving: Boolean = false
)
