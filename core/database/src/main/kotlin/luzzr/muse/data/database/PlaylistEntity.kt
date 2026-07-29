package luzzr.muse.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val artworkUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index(value = ["songId"])]
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val songId: Long,
    val sortOrder: Int
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deletePlaylistItems(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistItem(playlistId: Long, songId: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC, songId ASC")
    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC, songId ASC")
    suspend fun getItemsForPlaylistSync(playlistId: Long): List<PlaylistItemEntity>

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getItemCount(playlistId: Long): Int

    @Query("UPDATE playlist_items SET sortOrder = :sortOrder WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateItemSortOrder(playlistId: Long, songId: Long, sortOrder: Int)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun getPlaylistItem(playlistId: Long, songId: Long): PlaylistItemEntity?
}