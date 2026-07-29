package luzzr.muse.ui.state

import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Song

data class LibraryMetadataState(
    val song: Song? = null,
    val results: List<MetadataResult> = emptyList(),
    val isFetching: Boolean = false,
    val isApplying: Boolean = false,
    val error: UiText? = null,
    val searchTermsSong: Song? = null
)
