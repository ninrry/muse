package luzzr.muse.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "media_usage_daily",
    primaryKeys = ["mediaType", "mediaId", "dayStart"]
)
data class MediaUsageEntity(
    val mediaType: String,
    val mediaId: String,
    val dayStart: Long,
    val listenedMs: Long = 0L,
    val readMs: Long = 0L,
    val playCount: Long = 0L,
    val completionCount: Long = 0L,
    val lastPlayedAt: Long = 0L
)

@Dao
interface MediaUsageDao {
    @Query("SELECT * FROM media_usage_daily WHERE mediaType = :mediaType")
    fun observeForType(mediaType: String): Flow<List<MediaUsageEntity>>

    @Query("SELECT * FROM media_usage_daily WHERE mediaType = :mediaType")
    suspend fun getForType(mediaType: String): List<MediaUsageEntity>

    @Query("""
        INSERT OR IGNORE INTO media_usage_daily(
            mediaType, mediaId, dayStart, listenedMs, readMs, playCount, completionCount, lastPlayedAt
        ) VALUES (:mediaType, :mediaId, :dayStart, 0, 0, 0, 0, 0)
    """)
    suspend fun ensureRow(mediaType: String, mediaId: String, dayStart: Long): Long

    @Query("""
        UPDATE media_usage_daily
        SET listenedMs = listenedMs + :durationMs,
            lastPlayedAt = CASE WHEN lastPlayedAt > :atMs THEN lastPlayedAt ELSE :atMs END
        WHERE mediaType = :mediaType AND mediaId = :mediaId AND dayStart = :dayStart
    """)
    suspend fun addListened(
        mediaType: String,
        mediaId: String,
        dayStart: Long,
        durationMs: Long,
        atMs: Long
    ): Int

    @Query("""
        UPDATE media_usage_daily
        SET readMs = readMs + :durationMs,
            lastPlayedAt = CASE WHEN lastPlayedAt > :atMs THEN lastPlayedAt ELSE :atMs END
        WHERE mediaType = :mediaType AND mediaId = :mediaId AND dayStart = :dayStart
    """)
    suspend fun addRead(
        mediaType: String,
        mediaId: String,
        dayStart: Long,
        durationMs: Long,
        atMs: Long
    ): Int

    @Query("""
        UPDATE media_usage_daily
        SET playCount = playCount + 1,
            lastPlayedAt = CASE WHEN lastPlayedAt > :atMs THEN lastPlayedAt ELSE :atMs END
        WHERE mediaType = :mediaType AND mediaId = :mediaId AND dayStart = :dayStart
    """)
    suspend fun addPlay(mediaType: String, mediaId: String, dayStart: Long, atMs: Long): Int

    @Query("""
        UPDATE media_usage_daily
        SET completionCount = completionCount + 1,
            lastPlayedAt = CASE WHEN lastPlayedAt > :atMs THEN lastPlayedAt ELSE :atMs END
        WHERE mediaType = :mediaType AND mediaId = :mediaId AND dayStart = :dayStart
    """)
    suspend fun addCompletion(mediaType: String, mediaId: String, dayStart: Long, atMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: MediaUsageEntity)
}
