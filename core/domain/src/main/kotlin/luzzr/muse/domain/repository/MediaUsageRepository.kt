package luzzr.muse.domain.repository

import luzzr.muse.domain.model.MediaUsageType
import luzzr.muse.domain.model.MusicUsageStats
import luzzr.muse.domain.model.ReadAlongUsageStats
import kotlinx.coroutines.flow.Flow

interface MediaUsageRepository {
    fun observeMusicStats(): Flow<MusicUsageStats>
    fun observeReadAlongStats(): Flow<ReadAlongUsageStats>

    suspend fun recordPlayStart(type: MediaUsageType, mediaId: String, atMs: Long = System.currentTimeMillis())
    suspend fun recordListened(type: MediaUsageType, mediaId: String, durationMs: Long, atMs: Long = System.currentTimeMillis())
    suspend fun recordRead(bookId: String, durationMs: Long, atMs: Long = System.currentTimeMillis())
    suspend fun recordCompletion(type: MediaUsageType, mediaId: String, atMs: Long = System.currentTimeMillis())
}
