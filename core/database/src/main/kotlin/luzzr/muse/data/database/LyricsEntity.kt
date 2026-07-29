package luzzr.muse.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted lyrics for a song.
 * Stores raw LRC text so we can re-parse it on load.
 */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    /** Raw synced LRC text (e.g. "[00:12.34]Hello world\n[00:15.67]Second line") */
    val syncedLyrics: String? = null,
    /** Plain (unsynced) lyrics text */
    val plainText: String? = null,
    /** Timestamp when lyrics were last fetched, in ms */
    val fetchedAt: Long = System.currentTimeMillis()
)
