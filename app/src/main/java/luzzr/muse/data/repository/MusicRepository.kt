package luzzr.muse.data.repository

import luzzr.muse.data.model.Album
import luzzr.muse.data.model.Artist
import luzzr.muse.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class MusicRepositoryFacade @Inject constructor(
    private val songDelegate: SongRepositoryDelegate,
    private val lyricsDelegate: LyricsRepositoryDelegate,
    private val artworkDelegate: ArtworkRepositoryDelegate
) {
    val songs: StateFlow<List<Song>> = songDelegate.songs
    val audiobooks: StateFlow<List<Song>> = songDelegate.audiobooks
    val isScanning: StateFlow<Boolean> = songDelegate.isScanning
    val scanProgress: StateFlow<Int> = songDelegate.scanProgress
    val scanStats: StateFlow<ScanStats?> = songDelegate.scanStats

    suspend fun scanAll(): List<Song> = songDelegate.scanAll()

    suspend fun scanFolder(path: String): List<Song> = songDelegate.scanFolder(path)

    suspend fun loadFromDatabase(): List<Song> = songDelegate.loadFromDatabase()

    suspend fun deleteSong(song: Song): Boolean = songDelegate.deleteSong(song)

    suspend fun renameSong(song: Song, newTitle: String): Boolean = songDelegate.renameSong(song, newTitle)

    suspend fun search(query: String): List<Song> = songDelegate.search(query)

    suspend fun updateSongTags(
        song: Song,
        title: String,
        artist: String,
        album: String,
        year: Int?,
        genre: String
    ): Boolean = songDelegate.updateSongTags(song, title, artist, album, year, genre)

    suspend fun updateSongWithMetadata(song: Song, result: luzzr.muse.data.network.MetadataResult): Song =
        songDelegate.updateSongWithMetadata(song, result)

    suspend fun getAlbums(): List<Album> = songDelegate.getAlbums()

    suspend fun getArtists(): List<Artist> = songDelegate.getArtists()

    suspend fun getSongsByAlbum(album: String): List<Song> = songDelegate.getSongsByAlbum(album)

    suspend fun getSongsByArtist(artist: String): List<Song> = songDelegate.getSongsByArtist(artist)

    suspend fun refreshAlbumAndArtistTables() = songDelegate.refreshAlbumAndArtistTables()

    suspend fun saveLyrics(songId: Long, syncedLyrics: String?, plainText: String?) =
        lyricsDelegate.saveLyrics(songId, syncedLyrics, plainText)

    suspend fun loadLyrics(songId: Long): Pair<String?, String?>? = lyricsDelegate.loadLyrics(songId)

    suspend fun deleteLyrics(songId: Long) = lyricsDelegate.deleteLyrics(songId)

    suspend fun loadLyricsOffset(songId: Long): Long = lyricsDelegate.loadLyricsOffset(songId)

    suspend fun saveLyricsOffset(songId: Long, offsetMs: Long) = lyricsDelegate.saveLyricsOffset(songId, offsetMs)

    val coverGenerationCompleted: SharedFlow<Unit> = artworkDelegate.coverGenerationCompleted
    val coverGenState: StateFlow<CoverGenState> = artworkDelegate.coverGenState

    suspend fun generateDefaultCoverForSong(song: Song): Boolean = artworkDelegate.generateDefaultCoverForSong(song)

    suspend fun generateDefaultCoversForAll(): Boolean = artworkDelegate.generateDefaultCoversForAll()

    suspend fun generateMissingCovers() = artworkDelegate.generateMissingCovers()

    suspend fun updateSongArtwork(song: Song, artworkBytes: ByteArray): Boolean =
        artworkDelegate.updateSongArtwork(song, artworkBytes)

    suspend fun downloadBytes(url: String): ByteArray? = artworkDelegate.downloadBytes(url)
}
