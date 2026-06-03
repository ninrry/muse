package luzzr.muse.domain.usecase

import luzzr.muse.data.network.LyricsFetcher
import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.repository.LyricsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FetchLyricsUseCase @Inject constructor(
    private val lyricsFetcher: LyricsFetcher,
    private val lyricsRepository: LyricsRepository
) {
    private val cache = LinkedHashMap<Long, LyricsResult>(MAX_CACHE_SIZE, 0.75f, true)

    suspend operator fun invoke(
        songId: Long,
        title: String,
        artist: String?,
        album: String? = null
    ): LyricsResult? {
        cache[songId]?.let { return it }

        val result = lyricsFetcher.fetchSync(songId = songId, title = title, artist = artist, album = album)

        if (result != null) {
            cache[songId] = result
            if (cache.size > MAX_CACHE_SIZE) {
                val eldest = cache.keys.first()
                cache.remove(eldest)
            }
            val offset = lyricsRepository.loadLyricsOffset(songId)
            if (offset != 0L) {
                return result
            }
        }
        return result
    }

    companion object {
        private const val MAX_CACHE_SIZE = 100
    }
}
