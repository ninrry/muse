package luzzr.muse.domain.model

/**
 * Domain model for a music playlist.
 */
data class Playlist(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val artworkUri: String? = null,
    val songCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
