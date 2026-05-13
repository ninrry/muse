package luzzr.muse.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LyricsSpeedDao {

    /** Load saved lyrics speed for a song. Returns null if never adjusted. */
    @Query("SELECT * FROM lyrics_speed WHERE songId = :songId")
    suspend fun getSpeed(songId: Long): LyricsSpeedEntity?

    /** Save or update lyrics speed for a song. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSpeed(speed: LyricsSpeedEntity)

    /** Remove speed entry (e.g., when song is deleted). */
    @Query("DELETE FROM lyrics_speed WHERE songId = :songId")
    suspend fun deleteSpeed(songId: Long)
}
