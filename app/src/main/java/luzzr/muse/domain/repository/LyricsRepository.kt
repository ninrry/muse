package luzzr.muse.domain.repository

interface LyricsRepository {
    suspend fun saveLyrics(songId: Long, syncedLyrics: String?, plainText: String?)
    suspend fun loadLyrics(songId: Long): Pair<String?, String?>?
    suspend fun deleteLyrics(songId: Long)
    suspend fun loadLyricsOffset(songId: Long): Long
    suspend fun saveLyricsOffset(songId: Long, offsetMs: Long)
}
