package luzzr.muse.domain.repository

import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.Album
import luzzr.muse.domain.model.Artist
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

interface SongRepository {
    val songs: StateFlow<List<Song>>
    val audiobooks: StateFlow<List<Song>>
    val isScanning: StateFlow<Boolean>
    val scanProgress: StateFlow<Int>
    val scanStats: StateFlow<ScanStats?>

    suspend fun scanAll(): List<Song>
    suspend fun scanFolder(path: String): List<Song>
    suspend fun loadFromDatabase(): List<Song>
    suspend fun loadFromDatabaseFast(): List<Song>
    suspend fun shouldRefreshLibrary(): Boolean
    suspend fun deleteSong(song: Song): OperationResult<Unit>
    suspend fun renameSong(song: Song, newTitle: String): OperationResult<Unit>
    suspend fun search(query: String): List<Song>
    suspend fun updateSongTags(song: Song, title: String, artist: String, album: String, year: Int?, genre: String): OperationResult<Unit>
    suspend fun updateSongWithMetadata(song: Song, result: MetadataResult): OperationResult<Song>
    fun updateSongInList(songId: Long, transform: (Song) -> Song)
    suspend fun getAlbums(): List<Album>
    suspend fun getArtists(): List<Artist>
    suspend fun getSongsByAlbum(album: String): List<Song>
    suspend fun getSongsByArtist(artist: String): List<Song>
    suspend fun refreshAlbumAndArtistTables()
}
