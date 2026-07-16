package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LyricsResult

interface LyricsSearchClient {
    suspend fun fetchSync(songId: Long, title: String, artist: String?, album: String? = null): LyricsResult?

    /**
     * 多源搜索候选，供用户挑选。按相关度排序，最多 [maxResults] 条。
     */
    suspend fun searchCandidates(
        title: String,
        artist: String?,
        album: String? = null,
        maxResults: Int = 12
    ): List<LyricsResult>

    fun restoreToCache(songId: Long, result: LyricsResult)

    fun clearCache()

    fun clearCache(songId: Long) {}
}
