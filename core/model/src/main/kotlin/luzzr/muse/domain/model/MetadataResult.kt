package luzzr.muse.domain.model

data class MetadataResult(
    val title: String,
    val artist: String,
    val album: String = "",
    val year: Int? = null,
    val genre: String = "",
    val coverUrl: String? = null,
    val source: String = "MusicBrainz",
    val score: Int = 0
)
