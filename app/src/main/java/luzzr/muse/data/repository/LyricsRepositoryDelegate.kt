package luzzr.muse.data.repository

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepositoryDelegate @Inject constructor(
    private val lyricsRepository: LyricsRepository
) {
    suspend fun saveLyrics(songId: Long, syncedLyrics: String?, plainText: String?) =
        lyricsRepository.saveLyrics(songId, syncedLyrics, plainText)

    suspend fun loadLyrics(songId: Long): Pair<String?, String?>? = lyricsRepository.loadLyrics(songId)

    suspend fun deleteLyrics(songId: Long) = lyricsRepository.deleteLyrics(songId)

    suspend fun loadLyricsOffset(songId: Long): Long = lyricsRepository.loadLyricsOffset(songId)

    suspend fun saveLyricsOffset(songId: Long, offsetMs: Long) = lyricsRepository.saveLyricsOffset(songId, offsetMs)
}
