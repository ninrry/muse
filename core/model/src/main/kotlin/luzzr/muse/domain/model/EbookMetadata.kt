package luzzr.muse.domain.model

data class EbookMetadata(
    val title: String = "",
    val author: String = "",
    val coverBytes: ByteArray? = null,
    val coverMediaType: String? = null
)

enum class BookCollectionImportFailureStage {
    METADATA,
    ARTWORK
}

data class BookCollectionImportFailure(
    val songId: Long,
    val originalTitle: String,
    val stage: BookCollectionImportFailureStage,
    val message: String? = null
)

data class BookCollectionImportResult(
    val totalCount: Int,
    val successCount: Int,
    val failures: List<BookCollectionImportFailure>
)
