package luzzr.muse.domain.ebook

import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.EbookMetadata
import java.io.File

interface EbookMetadataParser {
    fun supports(displayName: String?, mimeType: String?): Boolean
    fun recognizes(file: File): Boolean
    suspend fun parse(file: File): OperationResult<EbookMetadata>
}
