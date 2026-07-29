package luzzr.muse.data.repository

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import luzzr.muse.data.database.MediaUsageDao
import luzzr.muse.data.database.MediaUsageEntity
import luzzr.muse.domain.model.MediaUsageType
import luzzr.muse.domain.model.MusicUsageStats
import luzzr.muse.domain.model.ReadAlongUsageStats
import luzzr.muse.domain.repository.MediaUsageRepository

@Singleton
class MediaUsageRepositoryImpl @Inject constructor(
    private val dao: MediaUsageDao
) : MediaUsageRepository {
    private val musicRows: Flow<List<MediaUsageEntity>> = dao.observeForType(MediaUsageType.MUSIC.name)
    private val audiobookRows: Flow<List<MediaUsageEntity>> = dao.observeForType(MediaUsageType.AUDIOBOOK.name)

    override fun observeMusicStats(): Flow<MusicUsageStats> = musicRows.map { rows ->
        val today = todayStart()
        val week = today - 6L * DAY_MS
        MusicUsageStats(
            totalListenedMs = rows.sumOf { it.listenedMs },
            todayListenedMs = rows.filter { it.dayStart >= today }.sumOf { it.listenedMs },
            weekListenedMs = rows.filter { it.dayStart >= week }.sumOf { it.listenedMs },
            playCount = rows.sumOf { it.playCount },
            todayPlayCount = rows.filter { it.dayStart >= today }.sumOf { it.playCount },
            listenedSongCount = rows.filter { it.listenedMs > 0L }.map { it.mediaId }.distinct().size.toLong(),
            lastPlayedAt = rows.maxOfOrNull { it.lastPlayedAt } ?: 0L
        )
    }

    override fun observeReadAlongStats(): Flow<ReadAlongUsageStats> = audiobookRows.map { rows ->
        val today = todayStart()
        val week = today - 6L * DAY_MS
        ReadAlongUsageStats(
            totalReadMs = rows.sumOf { it.readMs },
            todayReadMs = rows.filter { it.dayStart >= today }.sumOf { it.readMs },
            weekReadMs = rows.filter { it.dayStart >= week }.sumOf { it.readMs },
            totalListenedMs = rows.sumOf { it.listenedMs },
            todayListenedMs = rows.filter { it.dayStart >= today }.sumOf { it.listenedMs },
            weekListenedMs = rows.filter { it.dayStart >= week }.sumOf { it.listenedMs }
        )
    }

    override suspend fun recordPlayStart(type: MediaUsageType, mediaId: String, atMs: Long) {
        val (day, name) = key(type, mediaId, atMs)
        withContext(Dispatchers.IO) {
            dao.ensureRow(name, mediaId, day)
            dao.addPlay(name, mediaId, day, atMs)
        }
    }

    override suspend fun recordListened(type: MediaUsageType, mediaId: String, durationMs: Long, atMs: Long) {
        if (durationMs <= 0L) return
        val (day, name) = key(type, mediaId, atMs)
        withContext(Dispatchers.IO) {
            dao.ensureRow(name, mediaId, day)
            dao.addListened(name, mediaId, day, durationMs, atMs)
        }
    }

    override suspend fun recordRead(bookId: String, durationMs: Long, atMs: Long) {
        if (durationMs <= 0L) return
        val (day, name) = key(MediaUsageType.AUDIOBOOK, bookId, atMs)
        withContext(Dispatchers.IO) {
            dao.ensureRow(name, bookId, day)
            dao.addRead(name, bookId, day, durationMs, atMs)
        }
    }

    override suspend fun recordCompletion(type: MediaUsageType, mediaId: String, atMs: Long) {
        val (day, name) = key(type, mediaId, atMs)
        withContext(Dispatchers.IO) {
            dao.ensureRow(name, mediaId, day)
            dao.addCompletion(name, mediaId, day, atMs)
        }
    }

    private fun key(type: MediaUsageType, mediaId: String, atMs: Long): Pair<Long, String> =
        dayStart(atMs) to type.name

    private fun todayStart(): Long = dayStart(System.currentTimeMillis())

    private fun dayStart(atMs: Long): Long = Instant.ofEpochMilli(atMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
