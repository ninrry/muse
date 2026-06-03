package luzzr.muse.ui.state

import luzzr.muse.data.model.Song
import luzzr.muse.data.network.MetadataResult

data class LibraryMetadataState(
    val song: Song? = null,
    val results: List<MetadataResult> = emptyList(),
    val isFetching: Boolean = false,
    val error: String? = null,
    val searchTermsSong: Song? = null
)
