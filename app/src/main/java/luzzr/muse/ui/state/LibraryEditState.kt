package luzzr.muse.ui.state

import luzzr.muse.data.model.Song

data class LibraryEditState(
    val song: Song? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val error: String? = null,
    val needsStoragePermission: Boolean = false
)
