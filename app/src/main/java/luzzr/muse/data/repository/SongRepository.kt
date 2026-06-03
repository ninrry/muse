package luzzr.muse.data.repository

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.database.AlbumDao
import luzzr.muse.data.database.AlbumEntity
import luzzr.muse.data.database.ArtistDao
import luzzr.muse.data.database.ArtistEntity
import luzzr.muse.data.database.MuseDatabase
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.database.SongEntity
import luzzr.muse.data.mapper.isUsableArtworkUri
import luzzr.muse.data.mapper.toAlbum
import luzzr.muse.data.mapper.toArtist
import luzzr.muse.data.mapper.toEntity
import luzzr.muse.data.mapper.toSong
import luzzr.muse.data.model.Album
import luzzr.muse.data.model.Artist
import luzzr.muse.data.model.Song
import luzzr.muse.data.network.MetadataResult
import luzzr.muse.data.scanner.MediaStoreScanner
import luzzr.muse.data.tag.MetadataFileWriter
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class ScanStats(
    val totalSongs: Int,
    val totalAlbums: Int,
    val totalArtists: Int,
    val duration: Long
)

@Singleton
class SongRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val database: MuseDatabase,
    private val mediaStoreScanner: MediaStoreScanner,
    private val metadataFileWriter: MetadataFileWriter
) : luzzr.muse.domain.repository.SongRepository {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    override val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    override val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()
    private val _scanStats = MutableStateFlow<ScanStats?>(null)
    val scanStats: StateFlow<ScanStats?> = _scanStats.asStateFlow()

    private fun mergePersistedSongData(scanned: Song, existing: SongEntity?): Song {
        if (existing == null || scanned.filePath != existing.filePath) return scanned
        return scanned.copy(
            title = existing.title.ifBlank { scanned.title },
            artist = existing.artist.ifBlank { scanned.artist },
            album = existing.album.ifBlank { scanned.album },
            year = existing.year ?: scanned.year,
            genre = existing.genre.ifBlank { scanned.genre },
            albumArtist = existing.albumArtist ?: scanned.albumArtist,
            artworkUri = existing.artworkUri?.let { android.net.Uri.parse(it) } ?: scanned.artworkUri
        )
    }

    private fun restorePersistentArtwork(song: Song, coverDir: File): Song {
        val persistedUri = song.artworkUri?.takeIf { isUsableArtworkUri(it, context) }
        if (persistedUri != null) return song.copy(artworkUri = persistedUri)
        val coverFile = File(coverDir, "muse_art_${song.id}.png")
        return if (coverFile.exists()) {
            song.copy(artworkUri = android.net.Uri.fromFile(coverFile))
        } else {
            song.copy(artworkUri = null)
        }
    }

    private fun buildScanStats(duration: Long): ScanStats {
        val current = _songs.value
        return ScanStats(
            current.size,
            current.distinctBy { it.album }.size,
            current.distinctBy { it.artist }.size,
            duration
        )
    }

    private fun collectMediaStoreUris() = listOfNotNull(
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                null
            }
        }.getOrNull(),
        runCatching { MediaStore.Audio.Media.getContentUri("external") }.getOrNull(),
        runCatching { MediaStore.Audio.Media.EXTERNAL_CONTENT_URI }.getOrNull()
    ).distinct()

    override suspend fun scanAll(): List<Song> = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext _songs.value
        _isScanning.value = true
        _scanProgress.value = 0
        val startTime = System.currentTimeMillis()
        val songList = mutableListOf<Song>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} IS NULL"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        for (uri in collectMediaStoreUris()) {
            try {
                MuseLog.d("MuseScan", "Trying URI: $uri")
                context.contentResolver.query(uri, mediaStoreScanner.projection, selection, null, sortOrder)?.use { cursor ->
                    MuseLog.d("MuseScan", "URI $uri returned ${cursor.count} rows")
                    if (cursor.count > 0) songList.addAll(mediaStoreScanner.readSongsFromCursor(cursor))
                }
            } catch (e: SecurityException) {
                MuseLog.e("MuseScan", "Permission denied for $uri", e)
            } catch (e: IllegalStateException) {
                MuseLog.e("MuseScan", "ContentResolver error for $uri", e)
            } catch (e: Exception) {
                MuseLog.e("MuseScan", "Unexpected error querying $uri", e)
            }
            if (songList.isNotEmpty()) break
        }

        if (songList.isEmpty()) {
            _songs.value = emptyList()
            _isScanning.value = false
            return@withContext emptyList()
        }

        val existingSongs = songDao.getAllSongs().associateBy { it.id }
        val coverDir = File(context.filesDir, "covers")
        val finalSongs = songList.map { song ->
            restorePersistentArtwork(mergePersistedSongData(song, existingSongs[song.id]), coverDir)
        }
        database.withTransaction {
            songDao.deleteAll()
            albumDao.deleteAll()
            artistDao.deleteAll()
            songDao.insertAll(finalSongs.map { it.toEntity() })
            albumDao.insertAll(buildAlbumEntities(finalSongs))
            artistDao.insertAll(buildArtistEntities(finalSongs))
        }

        _songs.value = finalSongs
        _scanStats.value = ScanStats(
            finalSongs.size,
            finalSongs.distinctBy { it.album }.size,
            finalSongs.distinctBy { it.artist }.size,
            System.currentTimeMillis() - startTime
        )
        _scanProgress.value = 100
        _isScanning.value = false
        finalSongs
    }

    override suspend fun scanFolder(path: String): List<Song> = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext _songs.value
        _isScanning.value = true
        _scanProgress.value = 0
        val startTime = System.currentTimeMillis()
        val songList = mutableListOf<Song>()
        val collection = mediaStoreScanner.mediaStoreCollection()
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
            "${MediaStore.Audio.Media.IS_MUSIC} IS NULL) AND " +
            "${MediaStore.Audio.Media.DATA} LIKE ?"

        context.contentResolver.query(
            collection, mediaStoreScanner.projection, selection, arrayOf("$path%"), "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            if (cursor.count == 0) {
                _scanStats.value = buildScanStats(System.currentTimeMillis() - startTime)
                _scanProgress.value = 100
                _isScanning.value = false
                return@withContext emptyList()
            }
            songList.addAll(mediaStoreScanner.readSongsFromCursor(cursor))
        }

        val existingSongs = songDao.getAllSongs().associateBy { it.id }
        val coverDir = File(context.filesDir, "covers")
        val finalFolderSongs = songList.map { song ->
            restorePersistentArtwork(mergePersistedSongData(song, existingSongs[song.id]), coverDir)
        }
        database.withTransaction { songDao.insertAll(finalFolderSongs.map { it.toEntity() }) }

        _songs.update { current ->
            (current.filter { cur -> finalFolderSongs.none { it.id == cur.id } } + finalFolderSongs).sortedBy { it.title }
        }
        database.withTransaction {
            albumDao.deleteAll()
            artistDao.deleteAll()
            albumDao.insertAll(buildAlbumEntities(_songs.value))
            artistDao.insertAll(buildArtistEntities(_songs.value))
        }

        _scanStats.value = buildScanStats(System.currentTimeMillis() - startTime)
        _scanProgress.value = 100
        _isScanning.value = false
        finalFolderSongs
    }

    override suspend fun loadFromDatabase(): List<Song> = withContext(Dispatchers.IO) {
        val list = songDao.getAllSongs().map { it.toSong() }
        val restored = list.map { song ->
            if (isUsableArtworkUri(song.artworkUri, context)) return@map song
            val coverFile = File(context.filesDir, "covers/muse_art_${song.id}.png")
            if (coverFile.exists()) {
                val uri = android.net.Uri.fromFile(coverFile)
                songDao.updateSongArtworkUri(song.id, uri.toString())
                song.copy(artworkUri = uri)
            } else {
                if (song.artworkUri != null) songDao.updateSongArtworkUri(song.id, null)
                song.copy(artworkUri = null)
            }
        }
        _songs.value = restored
        restored
    }

    override suspend fun deleteSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = context.contentResolver.delete(song.uri, null, null)
            if (deleted > 0) {
                songDao.deleteSong(song.id)
                _songs.update { it.filter { s -> s.id != song.id } }
                true
            } else {
                @Suppress("DEPRECATION")
                val file = File(song.filePath)
                if (file.exists() && file.delete()) {
                    songDao.deleteSong(song.id)
                    _songs.update { it.filter { s -> s.id != song.id } }
                    true
                } else {
                    false
                }
            }
        } catch (e: SecurityException) {
            MuseLog.e("SongRepository", "deleteSong: permission denied for ${song.uri}", e)
            false
        } catch (e: IOException) {
            MuseLog.e("SongRepository", "deleteSong: IO error", e)
            false
        } catch (e: android.database.sqlite.SQLiteException) {
            MuseLog.e("SongRepository", "deleteSong: database error", e)
            false
        }
    }

    override suspend fun renameSong(song: Song, newTitle: String): Boolean {
        val result = metadataFileWriter.renameSong(song, newTitle, songDao)
        if (result) _songs.update { list -> list.map { if (it.id == song.id) it.copy(title = newTitle) else it } }
        return result
    }

    override suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        songDao.searchSongs("%$query%").map { it.toSong() }
    }

    override suspend fun updateSongTags(
        song: Song,
        title: String,
        artist: String,
        album: String,
        year: Int?,
        genre: String
    ): Boolean {
        val result = metadataFileWriter.updateSongTags(song, title, artist, album, year, genre, songDao)
        if (result) {
            _songs.update { list ->
                list.map {
                    if (it.id == song.id) {
                        it.copy(title = title, artist = artist, album = album, year = year, genre = genre)
                    } else {
                        it
                    }
                }
            }
        }
        return result
    }

    override suspend fun updateSongWithMetadata(song: Song, result: MetadataResult): Song {
        val updated = metadataFileWriter.updateSongWithMetadata(song, result, songDao)
        _songs.update { list -> list.map { if (it.id == song.id) updated else it } }
        return updated
    }

    override fun updateSongInList(songId: Long, transform: (Song) -> Song) {
        _songs.update { list -> list.map { if (it.id == songId) transform(it) else it } }
    }

    override suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) { albumDao.getAllAlbums().map { it.toAlbum() } }

    override suspend fun getArtists(): List<Artist> = withContext(Dispatchers.IO) { artistDao.getAllArtists().map { it.toArtist() } }

    override suspend fun getSongsByAlbum(album: String): List<Song> = withContext(Dispatchers.IO) {
        songDao.getSongsByAlbum(album).map { it.toSong() }
    }

    override suspend fun getSongsByArtist(artist: String): List<Song> = withContext(Dispatchers.IO) {
        songDao.getSongsByArtist(artist).map { it.toSong() }
    }

    override suspend fun refreshAlbumAndArtistTables() = withContext(Dispatchers.IO) {
        val currentSongs = _songs.value
        database.withTransaction {
            albumDao.deleteAll()
            artistDao.deleteAll()
            albumDao.insertAll(buildAlbumEntities(currentSongs))
            artistDao.insertAll(buildArtistEntities(currentSongs))
        }
    }

    private fun buildAlbumEntities(songs: List<Song>): List<AlbumEntity> {
        return songs.groupBy { it.album to it.albumId }.map { (key, group) ->
            val (_, albumId) = key
            val first = group.first()
            val finalAlbumId = if (albumId <= 0) {
                val hash = first.album.hashCode().toLong() and 0x7FFFFFFF
                if (hash == 0L) -1L else -hash
            } else {
                albumId
            }
            AlbumEntity(
                albumId = finalAlbumId,
                title = first.album,
                artist = first.albumArtist ?: first.artist,
                artworkUri = first.artworkUri?.toString(),
                songCount = group.size,
                year = first.year
            )
        }
    }

    private fun buildArtistEntities(songs: List<Song>): List<ArtistEntity> {
        return songs.groupBy { it.artist }.map { (artist, group) ->
            ArtistEntity(artistName = artist, songCount = group.size, albumCount = group.distinctBy { it.album }.size)
        }
    }
}
