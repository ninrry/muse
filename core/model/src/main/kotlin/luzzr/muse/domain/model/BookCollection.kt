package luzzr.muse.domain.model

data class BookCollection(
    val id: Long = 0L,
    val name: String,
    val author: String = "",
    val artworkUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class BookCollectionItem(
    val song: Song,
    val sortOrder: Int
)
