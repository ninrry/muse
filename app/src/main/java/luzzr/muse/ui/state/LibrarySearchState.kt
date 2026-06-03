package luzzr.muse.ui.state

import luzzr.muse.data.model.Song

data class LibrarySearchState(
    val query: String = "",
    val isActive: Boolean = false,
    val results: List<Song> = emptyList()
)
