package luzzr.muse.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.audio.AudioFileSupport
import luzzr.muse.data.audio.AudioMetadataSanitizer
import luzzr.muse.data.database.AlbumDao
import luzzr.muse.data.database.AlbumEntity
import luzzr.muse.data.database.ArtistDao
import luzzr.muse.data.database.ArtistEntity
import luzzr.muse.data.database.MuseDatabase
import luzzr.muse.data.database.ReadAlongDao
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.database.SongEntity
import luzzr.muse.data.library.LibraryMediaInvalidation
import luzzr.muse.data.mapper.isUsableArtworkUri
import luzzr.muse.data.mapper.toAlbum
import luzzr.muse.data.mapper.toArtist
import luzzr.muse.data.mapper.toEntity
import luzzr.muse.data.mapper.toSong
import luzzr.muse.data.readalong.ReadAlongMediaOwnershipIndex
import luzzr.muse.data.scanner.MediaStoreScanner
import luzzr.muse.data.tag.MetadataFileWriter
import luzzr.muse.data.tag.Mp4MetadataAtomWriter
import luzzr.muse.data.tag.TagEditor
import luzzr.muse.domain.model.Album
import luzzr.muse.domain.model.Artist
import luzzr.muse.domain.model.MediaClassifier
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class SongRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val database: MuseDatabase,
    private val mediaStoreScanner: MediaStoreScanner,
    private val metadataFileWriter: MetadataFileWriter,
    private val tagEditor: TagEditor,
    private val readAlongDao: ReadAlongDao,
    private val libraryMediaInvalidation: LibraryMediaInvalidation
) : luzzr.muse.domain.repository.SongRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("muse_song_repo", Context.MODE_PRIVATE)
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    override val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _audiobooks = MutableStateFlow<List<Song>>(emptyList())
    override val audiobooks: StateFlow<List<Song>> = _audiobooks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    override val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()
    private val _scanStats = MutableStateFlow<ScanStats?>(null)
    override val scanStats: StateFlow<ScanStats?> = _scanStats.asStateFlow()
    private val invalidationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        invalidationScope.launch {
            libraryMediaInvalidation.requests.collect {
                while (_isScanning.value) delay(50L)
                loadFromDatabase()
            }
        }
    }

    private suspend fun readAlongOwnership(): ReadAlongMediaOwnershipIndex =
        ReadAlongMediaOwnershipIndex.fromBooks(readAlongDao.observeBooks().first())

    private fun mergePersistedSongData(scanned: Song, existing: SongEntity?): Song {
        if (existing == null || scanned.filePath != existing.filePath) return scanned
        return scanned.copy(
            artworkUri = existing.artworkUri ?: scanned.artworkUri
        )
    }

    private fun restorePersistentArtwork(song: Song, coverDir: File): Song {
        val persistedUri = song.artworkUri?.takeIf { isUsableArtworkUri(it, context) }
        if (persistedUri != null) return song.copy(artworkUri = persistedUri)
        val coverFile = File(coverDir, "muse_art_${song.id}.png")
        return if (coverFile.exists()) {
            song.copy(artworkUri = android.net.Uri.fromFile(coverFile).toString())
        } else {
            song.copy(artworkUri = null)
        }
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
        if (_isScanning.value) return@withContext _allSongs.value
        _isScanning.value = true
        val startTime = System.currentTimeMillis()
        val songList = mutableListOf<Song>()
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} IS NULL) AND " +
            "(${MediaStore.Audio.Media.MIME_TYPE} IS NULL OR ${MediaStore.Audio.Media.MIME_TYPE} NOT LIKE 'video/%')"
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

        val ownership = readAlongOwnership()
        // Filter stale MediaStore rows and external source copies belonging to a
        // synchronized-reading package before anything reaches the Song pipeline.
        val validSongs = songList.filter { song ->
            if (song.filePath.isNotBlank() && song.filePath.startsWith("/")) {
                try {
                    File(song.filePath).exists()
                } catch (e: Exception) {
                    MuseLog.w("MuseScan", "Unable to validate path ${song.filePath}; retaining item", e)
                    true
                }
            } else {
                true
            }
        }.filterNot(ownership::owns)

        // --- 物理扫描兜底 ---
        val existingPaths = validSongs.map { File(it.filePath).safeCanonicalPath() }.toSet()
        val extraSongs = mutableListOf<Song>()
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetExtensions = AudioFileSupport.supportedAudioExtensions

        scanPhysicalDirectory(musicDir, targetExtensions, existingPaths, extraSongs, ownership)
        scanPhysicalDirectory(downloadDir, targetExtensions, existingPaths, extraSongs, ownership)

        if (extraSongs.isNotEmpty()) {
            MuseLog.w("MuseScan", "Found ${extraSongs.size} extra songs from physical scan!")
        }
        // ------------------

        val combinedSongs = (validSongs + extraSongs).distinctBy { File(it.filePath).safeCanonicalPath() }
        // Continue even when no files were found: this lets the same transaction
        // purge stale/previously leaked rows from the music database.
        val dbSongs = songDao.getAllSongs()
        val dbMap = dbSongs.associateBy { it.id }
        val coverDir = File(context.filesDir, "covers")
        if (!coverDir.exists()) {
            coverDir.mkdirs()
        }
        val finalSongs = combinedSongs.map { song ->
            val dbSong = dbMap[song.id]
            val isStaleDbItem = dbSong != null && (
                dbSong.artworkUri == null ||
                (AudioMetadataSanitizer.isUnknownArtist(dbSong.artist) && (dbSong.title.contains(" - ") || dbSong.title.contains("-") || dbSong.title.contains("_")))
            )
            // 未改动且元数据完整的条目跳过物理重读（允许2秒内的秒/毫秒舍入误差）
            val unchanged = dbSong != null && !isStaleDbItem &&
                (dbSong.dateModified == song.dateModified || kotlin.math.abs(dbSong.dateModified - song.dateModified) < 2000L) &&
                File(dbSong.filePath).safeCanonicalPath() == File(song.filePath).safeCanonicalPath()
            val currentSong = if (unchanged) {
                song.copy(
                    title = dbSong.title,
                    artist = dbSong.artist,
                    album = dbSong.album,
                    year = dbSong.year,
                    genre = dbSong.genre,
                    trackNumber = dbSong.trackNumber,
                    albumArtist = dbSong.albumArtist,
                    artworkUri = dbSong.artworkUri,
                    dateModified = dbSong.dateModified,
                    size = dbSong.size
                )
            } else {
                refreshFromPhysicalFile(song, coverDir, dbSong)
            }
            restorePersistentArtwork(mergePersistedSongData(currentSong, dbSong), coverDir)
        }

        val newKeySet = finalSongs.map { it.id }.toSet()
        val toInsertOrUpdate = mutableListOf<SongEntity>()
        val toDelete = mutableListOf<Long>()

        for (dbSong in dbSongs) {
            if (dbSong.id !in newKeySet) {
                toDelete.add(dbSong.id)
            }
        }

        for (song in finalSongs) {
            val dbSong = dbMap[song.id]
            if (dbSong == null) {
                toInsertOrUpdate.add(song.toEntity())
            } else {
                val dbPath = File(dbSong.filePath).safeCanonicalPath()
                val newPath = File(song.filePath).safeCanonicalPath()
                if (dbSong.dateModified != song.dateModified || dbPath != newPath) {
                    toInsertOrUpdate.add(song.toEntity())
                }
            }
        }

        database.withTransaction {
            val batchSize = 500
            for (i in toDelete.indices step batchSize) {
                val batch = toDelete.subList(i, minOf(i + batchSize, toDelete.size))
                songDao.deleteSongsByIds(batch)
            }
            for (i in toInsertOrUpdate.indices step batchSize) {
                val batch = toInsertOrUpdate.subList(
                    i,
                    minOf(i + batchSize, toInsertOrUpdate.size)
                )
                songDao.insertAll(batch)
            }

            albumDao.deleteAll()
            artistDao.deleteAll()
            albumDao.insertAll(buildAlbumEntities(finalSongs))
            artistDao.insertAll(buildArtistEntities(finalSongs))
        }

        updateAllSongs(finalSongs)
        _scanStats.value = ScanStats(
            finalSongs.size,
            finalSongs.distinctBy { it.album }.size,
            finalSongs.distinctBy { it.artist }.size,
            System.currentTimeMillis() - startTime
        )
        _scanProgress.value = 100
        _isScanning.value = false
        updateLastRefreshTime()
        finalSongs
    }

    private fun scanPhysicalDirectory(
        dir: File,
        extensions: Set<String>,
        existingPaths: Set<String>,
        resultList: MutableList<Song>,
        ownership: ReadAlongMediaOwnershipIndex
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    scanPhysicalDirectory(file, extensions, existingPaths, resultList, ownership)
                }
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in extensions &&
                    AudioFileSupport.isSupportedAudioPath(file.absolutePath) &&
                    !ownership.owns(file)
                ) {
                    val canonicalPath = file.safeCanonicalPath()
                    if (canonicalPath !in existingPaths && resultList.none { File(it.filePath).safeCanonicalPath() == canonicalPath }) {
                        val song = createSongFromFile(file)
                        if (song != null) {
                            resultList.add(song)
                        }
                    }
                }
            }
        }
    }

    private fun createSongFromFile(file: File): Song? {
        return try {
            val path = file.absolutePath
            if (!AudioFileSupport.isSupportedAudioPath(path)) return null
            val meta = tagEditor.readMetadata(path)

            val sanitized = AudioMetadataSanitizer.sanitize(
                rawTitle = meta?.title,
                rawArtist = meta?.artist,
                rawAlbum = meta?.album,
                fallbackFileName = file.name
            )
            val title = sanitized.title
            val artist = sanitized.artist
            val album = sanitized.album
            val year = meta?.year
            val genre = meta?.genre ?: ""
            val trackNum = meta?.trackNumber ?: 0

            val id = (path.hashCode().toLong() and 0x7FFFFFFF_FFFFFFFFL).let { if (it == 0L) 1L else it }
            val duration = getAudioDurationMs(file)

            Song(
                id = id,
                title = title,
                artist = artist,
                album = album,
                albumId = (album.hashCode().toLong() and 0x7FFFFFFF_FFFFFFFFL).let { if (it == 0L) 1L else it },
                duration = duration,
                uri = android.net.Uri.fromFile(file).toString(),
                artworkUri = null,
                filePath = path,
                codec = AudioFileSupport.detectCodec(path),
                size = file.length(),
                trackNumber = trackNum,
                year = year,
                dateAdded = file.lastModified(),
                dateModified = file.lastModified()
            )
        } catch (e: Exception) {
            MuseLog.e("MuseScan", "createSongFromFile failed for ${file.name}", e)
            null
        }
    }

    private fun getAudioDurationMs(file: File): Long {
        val ext = file.extension.lowercase()
        if (ext in MP4_DURATION_EXTENSIONS) {
            val duration = Mp4MetadataAtomWriter.getDurationMs(file)
            if (duration > 0) return duration
        }
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 180000L
        } catch (_: Exception) {
            180000L
        }
    }

    override suspend fun loadFromDatabase(): List<Song> = withContext(Dispatchers.IO) {
        val stored = songDao.getAllSongs().map { it.toSong() }
        val ownership = readAlongOwnership()
        val list = stored.filterNot(ownership::owns)
        val coverDir = File(context.filesDir, "covers")
        if (!coverDir.exists()) {
            coverDir.mkdirs()
        }
        val audioRows = list.filter { AudioFileSupport.isSupportedAudioPath(it.filePath) }
        val dbMap = stored.associateBy { it.id }
        val restored = audioRows.map { song ->
            val dbSong = dbMap[song.id]?.toEntity()
            restorePersistentArtwork(refreshFromPhysicalFile(song, coverDir, dbSong), coverDir)
        }
        if (restored.size != stored.size) {
            MuseLog.w("SongRepository", "loadFromDatabase: removed ${stored.size - restored.size} non-music or read-along rows")
            database.withTransaction {
                songDao.deleteAll()
                albumDao.deleteAll()
                artistDao.deleteAll()
                if (restored.isNotEmpty()) {
                    songDao.insertAll(restored.map { it.toEntity() })
                    albumDao.insertAll(buildAlbumEntities(restored))
                    artistDao.insertAll(buildArtistEntities(restored))
                }
            }
        } else if (restored.isNotEmpty()) {
            database.withTransaction {
                songDao.insertAll(restored.map { it.toEntity() })
                albumDao.deleteAll()
                artistDao.deleteAll()
                albumDao.insertAll(buildAlbumEntities(restored))
                artistDao.insertAll(buildArtistEntities(restored))
            }
        }
        updateAllSongs(restored)
        restored
    }

    override suspend fun loadFromDatabaseFast(): List<Song> = withContext(Dispatchers.IO) {
        // Fast-path still honours the read-along ownership boundary. If it finds
        // historical leakage, use the normal path once to purge persisted rows.
        val stored = songDao.getAllSongs().map { it.toSong() }
        val list = stored.filterNot(readAlongOwnership()::owns)
        if (list.size != stored.size) return@withContext loadFromDatabase()
        updateAllSongs(list)
        list
    }

    override suspend fun shouldRefreshLibrary(): Boolean {
        // 如果距离上次刷新超过24小时，则需要刷新
        val lastRefresh = prefs.getLong("last_library_refresh", 0L)
        return System.currentTimeMillis() - lastRefresh > 24 * 60 * 60 * 1000L
    }

    private fun updateLastRefreshTime() {
        prefs.edit {
            putLong("last_library_refresh", System.currentTimeMillis())
        }
    }

    override suspend fun deleteSong(song: Song): OperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val deleted = deleteByContentResolver(song)
            if (deleted > 0) {
                removeSongFromLibrary(song.id)
                OperationResult.Success(Unit)
            } else {
                @Suppress("DEPRECATION")
                val file = File(song.filePath)
                if (file.exists() && file.delete()) {
                    removeSongFromLibrary(song.id)
                    OperationResult.Success(Unit)
                } else {
                    OperationResult.Failure(OperationError.NOT_FOUND, "Song file was not found")
                }
            }
        } catch (e: SecurityException) {
            MuseLog.e("SongRepository", "deleteSong: permission denied for ${song.uri}", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("SongRepository", "deleteSong: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: android.database.sqlite.SQLiteException) {
            MuseLog.e("SongRepository", "deleteSong: database error", e)
            OperationResult.Failure(OperationError.DATABASE, e.message)
        } catch (e: Exception) {
            MuseLog.e("SongRepository", "deleteSong: unexpected error for ${song.uri}", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun deleteByContentResolver(song: Song): Int {
        val uri = song.uri.toUri()
        if (uri.scheme != "content") return 0
        return try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: IllegalArgumentException) {
            MuseLog.w("SongRepository", "deleteByContentResolver: unsupported uri ${song.uri}", e)
            0
        } catch (e: UnsupportedOperationException) {
            MuseLog.w("SongRepository", "deleteByContentResolver: delete not supported for ${song.uri}", e)
            0
        }
    }

    private suspend fun removeSongFromLibrary(songId: Long) {
        songDao.deleteSong(songId)
        updateAllSongs(_allSongs.value.filter { s -> s.id != songId })
    }

    override suspend fun renameSong(song: Song, newTitle: String): OperationResult<Unit> {
        val result = metadataFileWriter.renameSong(song, newTitle, songDao)
        if (result is OperationResult.Success) {
            updateAllSongs(_allSongs.value.map { if (it.id == song.id) result.value else it })
        }
        return when (result) {
            is OperationResult.Success -> OperationResult.Success(Unit)
            is OperationResult.Failure -> result
        }
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
    ): OperationResult<Song> {
        val result = metadataFileWriter.updateSongTags(song, title, artist, album, year, genre, songDao)
        if (result is OperationResult.Success) {
            val updated = result.value
            updateAllSongs(_allSongs.value.map { if (it.id == song.id) updated else it })
            refreshAlbumAndArtistTables()
        }
        return result
    }

    override suspend fun updateSongWithMetadata(song: Song, result: MetadataResult): OperationResult<Song> {
        val updated = metadataFileWriter.updateSongWithMetadata(song, result, songDao)
        if (updated is OperationResult.Success) {
            val updatedSong = updated.value
            updateAllSongs(_allSongs.value.map { if (it.id == song.id) updatedSong else it })
            refreshAlbumAndArtistTables()
        }
        return updated
    }

    override fun updateSongInList(songId: Long, transform: (Song) -> Song) {
        updateAllSongs(_allSongs.value.map { if (it.id == songId) transform(it) else it })
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
        val currentSongs = _allSongs.value
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
                artworkUri = first.artworkUri,
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

    private fun updateAllSongs(songs: List<Song>) {
        _allSongs.value = songs
        _songs.value = songs.filterNot(MediaClassifier::isAudiobook)
        _audiobooks.value = songs.filter(MediaClassifier::isAudiobook)
    }

    private fun refreshFromPhysicalFile(song: Song, coverDir: File, dbSong: SongEntity? = null): Song {
        var currentSong = readPhysicalTagMetadata(song, dbSong)
        currentSong = rebuildArtworkCacheFromEmbedded(currentSong, coverDir)
        return currentSong
    }

    private fun readPhysicalTagMetadata(song: Song, dbSong: SongEntity? = null): Song {
        return try {
            val meta = tagEditor.readMetadata(song.filePath, song.uri)
            val file = File(song.filePath)

            val rawTitle = meta?.title?.takeIf { it.isNotBlank() }
                ?: (if (dbSong != null && !dbSong.title.contains(" - ") && !dbSong.title.contains("-") && !dbSong.title.contains("_")) dbSong.title else null)
                ?: song.title
            val rawArtist = meta?.artist?.takeIf { it.isNotBlank() }
                ?: (if (dbSong != null && !AudioMetadataSanitizer.isUnknownArtist(dbSong.artist)) dbSong.artist else null)
                ?: song.artist
            val rawAlbum = meta?.album?.takeIf { it.isNotBlank() }
                ?: (if (dbSong != null && !AudioMetadataSanitizer.isUnknownAlbum(dbSong.album)) dbSong.album else null)
                ?: song.album

            val sanitized = AudioMetadataSanitizer.sanitize(
                rawTitle = rawTitle,
                rawArtist = rawArtist,
                rawAlbum = rawAlbum,
                fallbackFileName = file.name
            )

            song.copy(
                title = sanitized.title,
                artist = sanitized.artist,
                album = sanitized.album,
                year = meta?.year ?: dbSong?.year ?: song.year,
                genre = meta?.genre?.takeIf { it.isNotBlank() } ?: dbSong?.genre?.takeIf { it.isNotBlank() } ?: song.genre,
                trackNumber = meta?.trackNumber ?: dbSong?.trackNumber ?: song.trackNumber,
                albumArtist = meta?.albumArtist ?: dbSong?.albumArtist ?: song.albumArtist,
                dateModified = if (file.exists()) file.lastModified().takeIf { it > 0L } ?: song.dateModified else song.dateModified,
                size = if (file.exists()) file.length().takeIf { it > 0L } ?: song.size else song.size
            )
        } catch (e: Exception) {
            MuseLog.e("MuseScan", "Failed to read physical metadata for ${song.filePath}", e)
            song
        }
    }

    private fun rebuildArtworkCacheFromEmbedded(song: Song, coverDir: File): Song {
        val coverFile = File(coverDir, "muse_art_${song.id}.png")
        return try {
            val artworkBytes = tagEditor.readArtwork(song.filePath, song.uri)
            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                song.copy(artworkUri = ArtworkCacheStorage.write(coverFile, artworkBytes))
            } else {
                if (coverFile.exists() && coverFile.length() > 0) {
                    song.copy(artworkUri = coverFile.toURI().toString())
                } else {
                    song.copy(artworkUri = null)
                }
            }
        } catch (e: Exception) {
            MuseLog.e("MuseScan", "Failed to extract artwork from ${song.filePath}", e)
            song
        }
    }

    private fun File.safeCanonicalPath(): String {
        return runCatching { canonicalPath }.getOrElse { absolutePath }
    }

    private companion object {
        val MP4_DURATION_EXTENSIONS = AudioFileSupport.mp4AudioContainerExtensions
    }
}
