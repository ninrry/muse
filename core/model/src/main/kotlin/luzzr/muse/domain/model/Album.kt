package luzzr.muse.domain.model

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: String? = null,
    val songCount: Int = 0,
    val year: Int? = null
)
