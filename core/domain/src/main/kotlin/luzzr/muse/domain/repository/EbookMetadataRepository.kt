package luzzr.muse.domain.repository

import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.EbookMetadata

interface EbookMetadataRepository {
    suspend fun extract(uri: String, displayName: String?, mimeType: String?): OperationResult<EbookMetadata>
}
