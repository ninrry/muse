package luzzr.muse.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted lyrics timing offset for a song.
 * Offset is applied as: adjustedPosition = progressMs + offsetMs
 *
 * Positive offset = lyrics shifted later (lyrics appear earlier relative to audio)
 * Negative offset = lyrics shifted earlier (lyrics appear later relative to audio)
 *
 * Default: 0ms (no adjustment).
 */
@Entity(tableName = "lyrics_offset")
data class LyricsOffsetEntity(
    @PrimaryKey val songId: Long,
    val offsetMs: Long = 0L
)
