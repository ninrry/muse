package luzzr.muse.data.repository

import luzzr.muse.data.database.MediaUsageDao
import luzzr.muse.data.database.MediaUsageEntity
import luzzr.muse.domain.model.MediaUsageType
import luzzr.muse.domain.model.MusicUsageStats
import luzzr.muse.domain.model.ReadAlongUsageStats
import luzzr.muse.domain.repository.MediaUsageRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class MediaUsageRepositoryImpl @Inject constructor(
    private val dao: MediaUsageDao
) : MediaUsageRepository {
    private val musicRows: Flow<List<MediaUsageEntity>> = dao.observeForType(MediaUsageType.MUSIC.name)
    private val audiobookRows: Flow<List<MediaUsageEntity>> = dao.observeForType(MediaUsageType.AUDIOBOOK.name)

    override fun observeMusicStats(): Flow<MusicUsageStats> = musicRows.map { rows ->
        val today = todayStart()
        val week = today - 6L * DAY_MS
        val listenedSongIds = HashSet<String>()
        var totalListenedMs = 0L
        var todayListenedMs = 0L
        var weekListenedMs = 0L
        var playCount = 0L
        var todayPlayCount = 0L
        var lastPlayedAt = 0L
        rows.forEach { row ->
            if (row.listenedMs > 0L) listenedSongIds += row.mediaId
            totalListenedMs += row.listenedMs
            playCount += row.playCount
            if (row.dayStart >= week) weekListenedMs += row.listenedMs
            if (row.dayStart >= today) {
                todayListenedMs += row.listenedMs
                todayPlayCount += row.playCount
            }
            lastPlayedAt = maxOf(lastPlayedAt, row.lastPlayedAt)
        }
        MusicUsageStats(
            totalListenedMs = totalListenedMs,
            todayListenedMs = todayListenedMs,
            weekListenedMs = weekListenedMs,
            playCount = playCount,
            todayPlayCount = todayPlayCount,
            listenedSongCount = listenedSongIds.size.toLong(),
            lastPlayedAt = lastPlayedAt
        )
    }

    override fun observeReadAlongStats(): Flow<ReadAlongUsageStats> = audiobookRows.map { rows ->
        val today = todayStart()
        val week = today - 6L * DAY_MS
        var totalReadMs = 0L
        var todayReadMs = 0L
        var weekReadMs = 0L
        var totalListenedMs = 0L
        var todayListenedMs = 0L
        var weekListenedMs = 0L
        rows.forEach { row ->
            totalReadMs += row.readMs
            totalListenedMs += row.listenedMs
            if (row.dayStart >= week) {
                weekReadMs += row.readMs
                weekListenedMs += row.listenedMs
            }
            if (row.dayStart >= today) {
                todayReadMs += row.readMs
                todayListenedMs += row.listenedMs
            }
        }
        ReadAlongUsageStats(
            totalReadMs = totalReadMs,
            todayReadMs = todayReadMs,
            weekReadMs = weekReadMs,
            totalListenedMs = totalListenedMs,
            todayListenedMs = todayListenedMs,
            weekListenedMs = weekListenedMs
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

    private fun key(type: MediaUsageType, mediaId: String, atMs: Long): Pair<Long, String> = dayStart(atMs) to type.name

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
