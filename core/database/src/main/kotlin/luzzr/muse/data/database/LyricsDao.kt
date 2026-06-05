package luzzr.muse.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LyricsDao {

    /** Load persisted lyrics for a song. Returns null if not cached. */
    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun getLyrics(songId: Long): LyricsEntity?

    /** Save or update lyrics for a song. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsEntity)

    /** Remove lyrics entry (e.g., when song is deleted). */
    @Query("DELETE FROM lyrics WHERE songId = :songId")
    suspend fun deleteLyrics(songId: Long)

    /** Clear all cached lyrics. */
    @Query("DELETE FROM lyrics")
    suspend fun deleteAll()
}
