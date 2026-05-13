package luzzr.muse.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted lyrics speed adjustment for a song.
 * Default speed is 1.0f (no adjustment).
 * Speed < 1.0 = lyrics slower (timestamps stretch)
 * Speed > 1.0 = lyrics faster (timestamps compress)
 */
@Entity(tableName = "lyrics_speed")
data class LyricsSpeedEntity(
    @PrimaryKey val songId: Long,
    val speed: Float = 1.0f
)
