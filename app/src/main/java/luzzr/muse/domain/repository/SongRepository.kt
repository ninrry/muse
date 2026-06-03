package luzzr.muse.domain.repository

import luzzr.muse.domain.model.Album
import luzzr.muse.domain.model.Artist
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

interface SongRepository {
    val songs: StateFlow<List<Song>>
    val isScanning: StateFlow<Boolean>
    val scanProgress: StateFlow<Int>

    suspend fun scanAll(): List<Song>
    suspend fun scanFolder(path: String): List<Song>
    suspend fun loadFromDatabase(): List<Song>
    suspend fun deleteSong(song: Song): Boolean
    suspend fun renameSong(song: Song, newTitle: String): Boolean
    suspend fun search(query: String): List<Song>
    suspend fun updateSongTags(song: Song, title: String, artist: String, album: String, year: Int?, genre: String): Boolean
    suspend fun updateSongWithMetadata(song: Song, result: MetadataResult): Song
    fun updateSongInList(songId: Long, transform: (Song) -> Song)
    suspend fun getAlbums(): List<Album>
    suspend fun getArtists(): List<Artist>
    suspend fun getSongsByAlbum(album: String): List<Song>
    suspend fun getSongsByArtist(artist: String): List<Song>
    suspend fun refreshAlbumAndArtistTables()
}
