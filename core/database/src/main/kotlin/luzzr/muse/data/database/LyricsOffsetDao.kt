package luzzr.muse.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LyricsOffsetDao {

    /** Load saved lyrics offset for a song. Returns null if never adjusted. */
    @Query("SELECT * FROM lyrics_offset WHERE songId = :songId")
    suspend fun getOffset(songId: Long): LyricsOffsetEntity?

    /** Save or update lyrics offset for a song. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setOffset(offset: LyricsOffsetEntity)

    /** Remove offset entry (e.g., when song is deleted). */
    @Query("DELETE FROM lyrics_offset WHERE songId = :songId")
    suspend fun deleteOffset(songId: Long)
}
