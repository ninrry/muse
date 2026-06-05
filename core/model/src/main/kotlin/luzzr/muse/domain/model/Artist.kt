package luzzr.muse.domain.model

data class Artist(
    val name: String,
    val songCount: Int = 0,
    val albumCount: Int = 0
)
