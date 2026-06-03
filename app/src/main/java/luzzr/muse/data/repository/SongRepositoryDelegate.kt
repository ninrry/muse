package luzzr.muse.data.repository

import luzzr.muse.data.model.Album
import luzzr.muse.data.model.Artist
import luzzr.muse.data.model.Song
import luzzr.muse.data.network.MetadataResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class SongRepositoryDelegate @Inject constructor(
    private val songRepository: SongRepositoryImpl
) {
    val songs: StateFlow<List<Song>> = songRepository.songs
    val isScanning: StateFlow<Boolean> = songRepository.isScanning
    val scanProgress: StateFlow<Int> = songRepository.scanProgress
    val scanStats: StateFlow<ScanStats?> = songRepository.scanStats

    suspend fun scanAll(): List<Song> = songRepository.scanAll()

    suspend fun scanFolder(path: String): List<Song> = songRepository.scanFolder(path)

    suspend fun loadFromDatabase(): List<Song> = songRepository.loadFromDatabase()

    suspend fun deleteSong(song: Song): Boolean = songRepository.deleteSong(song)

    suspend fun renameSong(song: Song, newTitle: String): Boolean = songRepository.renameSong(song, newTitle)

    suspend fun search(query: String): List<Song> = songRepository.search(query)

    suspend fun updateSongTags(
        song: Song,
        title: String,
        artist: String,
        album: String,
        year: Int?,
        genre: String
    ): Boolean = songRepository.updateSongTags(song, title, artist, album, year, genre)

    suspend fun updateSongWithMetadata(song: Song, result: MetadataResult): Song =
        songRepository.updateSongWithMetadata(song, result)

    suspend fun getAlbums(): List<Album> = songRepository.getAlbums()

    suspend fun getArtists(): List<Artist> = songRepository.getArtists()

    suspend fun getSongsByAlbum(album: String): List<Song> = songRepository.getSongsByAlbum(album)

    suspend fun getSongsByArtist(artist: String): List<Song> = songRepository.getSongsByArtist(artist)

    suspend fun refreshAlbumAndArtistTables() = songRepository.refreshAlbumAndArtistTables()
}
