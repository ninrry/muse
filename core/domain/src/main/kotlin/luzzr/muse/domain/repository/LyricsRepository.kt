package luzzr.muse.domain.repository

interface LyricsRepository {
    suspend fun saveLyrics(songId: Long, syncedLyrics: String?, plainText: String?)
    suspend fun loadLyrics(songId: Long): Pair<String?, String?>?
    suspend fun deleteLyrics(songId: Long)
    suspend fun loadLyricsOffset(songId: Long): Long
    suspend fun saveLyricsOffset(songId: Long, offsetMs: Long)
    /** 已有歌词（同步或纯文本）的 songId 集合 */
    suspend fun getSongIdsWithLyrics(): Set<Long>
    suspend fun hasLyrics(songId: Long): Boolean
}
