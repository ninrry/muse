package luzzr.muse.domain.usecase

import luzzr.muse.domain.lyrics.LocalLyricsSource
import luzzr.muse.domain.lyrics.LyricsSearchClient
import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.LyricsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FetchLyricsUseCase @Inject constructor(
    private val lyricsSearchClient: LyricsSearchClient,
    private val lyricsRepository: LyricsRepository,
    private val localLyricsSource: LocalLyricsSource
) {
    private val cache = LinkedHashMap<Long, LyricsResult>(MAX_CACHE_SIZE, 0.75f, true)

    suspend fun searchCandidates(title: String, artist: String?, album: String? = null, maxResults: Int = 12): List<LyricsResult> =
        lyricsSearchClient.searchCandidates(title, artist, album, maxResults)

    suspend fun findLocal(song: Song): LyricsResult? {
        val local = localLyricsSource.find(song) ?: return null
        putCache(song.id, local)
        return local
    }

    suspend operator fun invoke(song: Song): LyricsResult? {
        findLocal(song)?.let { return it }
        return invoke(song.id, song.title, song.artist, song.album)
    }

    suspend operator fun invoke(songId: Long, title: String, artist: String?, album: String? = null): LyricsResult? {
        cached(songId)?.let { return it }

        val result = lyricsSearchClient.fetchSync(songId = songId, title = title, artist = artist, album = album)

        if (result != null) {
            putCache(songId, result)
            val offset = lyricsRepository.loadLyricsOffset(songId)
            if (offset != 0L) {
                return result
            }
        }
        return result
    }

    fun restore(songId: Long, result: LyricsResult) {
        putCache(songId, result)
        lyricsSearchClient.restoreToCache(songId, result)
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
        lyricsSearchClient.clearCache()
    }

    fun clearCache(songId: Long) {
        synchronized(cache) { cache.remove(songId) }
        lyricsSearchClient.clearCache(songId)
    }

    private fun cached(songId: Long): LyricsResult? = synchronized(cache) {
        cache[songId]
    }

    private fun putCache(songId: Long, result: LyricsResult) {
        synchronized(cache) {
            cache[songId] = result
            if (cache.size > MAX_CACHE_SIZE) {
                val eldest = cache.keys.first()
                cache.remove(eldest)
            }
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 100
    }
}
