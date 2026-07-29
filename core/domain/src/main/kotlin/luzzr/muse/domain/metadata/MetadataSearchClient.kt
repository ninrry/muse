package luzzr.muse.domain.metadata

import luzzr.muse.domain.model.MetadataResult

interface MetadataSearchClient {
    suspend fun search(rawTitle: String, rawArtist: String? = null, maxResults: Int = 10): List<MetadataResult>

    suspend fun searchExact(title: String, artist: String? = null, maxResults: Int = 10): List<MetadataResult>
}
