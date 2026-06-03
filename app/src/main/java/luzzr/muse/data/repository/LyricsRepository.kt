package luzzr.muse.data.repository

import luzzr.muse.data.database.LyricsDao
import luzzr.muse.data.database.LyricsEntity
import luzzr.muse.data.database.LyricsOffsetDao
import luzzr.muse.data.database.LyricsOffsetEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LyricsRepository @Inject constructor(
    private val lyricsDao: LyricsDao,
    private val lyricsOffsetDao: LyricsOffsetDao
) : luzzr.muse.domain.repository.LyricsRepository {

    override suspend fun saveLyrics(songId: Long, syncedLyrics: String?, plainText: String?) {
        withContext(Dispatchers.IO) {
            lyricsDao.insertLyrics(
                LyricsEntity(
                    songId = songId,
                    syncedLyrics = syncedLyrics,
                    plainText = plainText
                )
            )
        }
    }

    override suspend fun loadLyrics(songId: Long): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        lyricsDao.getLyrics(songId)?.let {
            it.syncedLyrics to it.plainText
        }
    }

    override suspend fun deleteLyrics(songId: Long) {
        withContext(Dispatchers.IO) { lyricsDao.deleteLyrics(songId) }
    }

    override suspend fun loadLyricsOffset(songId: Long): Long = withContext(Dispatchers.IO) {
        lyricsOffsetDao.getOffset(songId)?.offsetMs ?: 0L
    }

    override suspend fun saveLyricsOffset(songId: Long, offsetMs: Long) {
        withContext(Dispatchers.IO) {
            lyricsOffsetDao.setOffset(LyricsOffsetEntity(songId, offsetMs))
        }
    }
}
