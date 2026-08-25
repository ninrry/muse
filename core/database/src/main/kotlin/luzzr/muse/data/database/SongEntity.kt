package luzzr.muse.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["album"]),
        Index(value = ["artist"]),
        Index(value = ["filePath"]),
        Index(value = ["albumId"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: String,
    val artworkUri: String?,
    val trackNumber: Int,
    val year: Int?,
    val genre: String,
    val dateAdded: Long,
    val dateModified: Long,
    val albumArtist: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val codec: String?,
    val size: Long,
    val filePath: String
)

@Entity(tableName = "albums", indices = [Index(value = ["artist"]), Index(value = ["year"])])
data class AlbumEntity(
    @PrimaryKey val albumId: Long,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val songCount: Int,
    val year: Int?
)

@Entity(tableName = "artists", primaryKeys = ["artistName"])
data class ArtistEntity(
    val artistName: String,
    val songCount: Int,
    val albumCount: Int
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE title LIKE :query OR artist LIKE :query OR album LIKE :query")
    suspend fun searchSongs(query: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY trackNumber ASC")
    suspend fun getSongsByAlbum(album: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    suspend fun getSongsByArtist(artist: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE filePath LIKE :prefix || '%'")
    suspend fun getSongsInFolder(prefix: String): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSong(id: Long)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>)

    @Query("UPDATE songs SET title = :newTitle, uri = :newUri, filePath = :newPath, dateModified = :dateModified, size = :size WHERE id = :id")
    suspend fun updateSongMeta(id: Long, newTitle: String, newUri: String, newPath: String, dateModified: Long, size: Long)

    @Query("UPDATE songs SET title = :newTitle, uri = :newUri, filePath = :newPath WHERE id = :id")
    suspend fun updateSongMeta(id: Long, newTitle: String, newUri: String, newPath: String)

    @Query("UPDATE songs SET filePath = :filePath, uri = :uri WHERE id = :id")
    suspend fun updateSongFilePath(id: Long, filePath: String, uri: String)

    /** Update metadata fields fetched from network or edited by user. */
    @Query(
        """
        UPDATE songs SET
            title = :title,
            artist = :artist,
            album = :album,
            year = :year,
            genre = :genre,
            artworkUri = :artworkUri,
            dateModified = :dateModified,
            size = :size
        WHERE id = :id
    """
    )
    suspend fun updateSongMetadata(
        id: Long,
        title: String,
        artist: String,
        album: String,
        year: Int?,
        genre: String,
        artworkUri: String?,
        dateModified: Long,
        size: Long
    )

    @Query(
        """
        UPDATE songs SET
            title = :title,
            artist = :artist,
            album = :album,
            year = :year,
            genre = :genre,
            artworkUri = :artworkUri
        WHERE id = :id
    """
    )
    suspend fun updateSongMetadata(id: Long, title: String, artist: String, album: String, year: Int?, genre: String, artworkUri: String?)

    @Query("UPDATE songs SET artworkUri = :uri WHERE id = :id")
    suspend fun updateSongArtworkUri(id: Long, uri: String?)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY title ASC")
    suspend fun getAllAlbums(): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY artistName ASC")
    suspend fun getAllArtists(): List<ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists")
    suspend fun deleteAll()
}
