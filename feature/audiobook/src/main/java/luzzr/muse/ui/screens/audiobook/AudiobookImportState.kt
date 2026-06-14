package luzzr.muse.ui.screens.audiobook

import luzzr.muse.domain.model.BookCollectionImportResult
import luzzr.muse.domain.model.EbookMetadata
import luzzr.muse.ui.state.UiText

data class EbookImportPreview(
    val sourceUri: String,
    val displayName: String?,
    val mimeType: String?,
    val metadata: EbookMetadata,
    val finalTitle: String,
    val chapterCount: Int,
    val exampleTitle: String?
)

data class AudiobookImportState(
    val isParsing: Boolean = false,
    val preview: EbookImportPreview? = null,
    val isImporting: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val result: BookCollectionImportResult? = null,
    val error: UiText? = null
)
