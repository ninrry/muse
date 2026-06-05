package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LyricsResult

interface LyricsSearchClient {
    suspend fun fetchSync(songId: Long, title: String, artist: String?, album: String? = null): LyricsResult?

    fun restoreToCache(songId: Long, result: LyricsResult)

    fun clearCache()
}
