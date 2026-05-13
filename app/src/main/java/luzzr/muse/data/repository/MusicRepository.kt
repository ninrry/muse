package luzzr.muse.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import luzzr.muse.data.database.AlbumDao
import luzzr.muse.data.database.AlbumEntity
import luzzr.muse.data.database.ArtistDao
import luzzr.muse.data.database.ArtistEntity
import androidx.room.withTransaction
import luzzr.muse.data.database.MuseDatabase
import luzzr.muse.data.database.LyricsDao
import luzzr.muse.data.database.LyricsEntity
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.database.SongEntity
import luzzr.muse.data.model.Album
import luzzr.muse.data.model.Artist
import luzzr.muse.data.model.Song
import luzzr.muse.data.tag.DefaultCoverGenerator
import luzzr.muse.data.tag.TagEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(
    private val context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val lyricsDao: LyricsDao
) {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    data class ScanStats(
        val totalSongs: Int,
        val totalAlbums: Int,
        val totalArtists: Int,
        val duration: Long
    )

    private val _scanStats = MutableStateFlow<ScanStats?>(null)
    val scanStats: StateFlow<ScanStats?> = _scanStats.asStateFlow()

    // Shared projection — excludes GENRE (not a real column) and ALBUM_ARTIST (API 31+)
    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.MediaColumns.DATE_MODIFIED
    )

    private fun readSongsFromCursor(cursor: android.database.Cursor): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val songList = mutableListOf<Song>()

        // Use getColumnIndex (returns -1 if missing) for optional columns
        val idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
        val titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val albumIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val albumIdIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
        val durationIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        val mimeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
        val trackIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val yearIdx = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
        val sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val dateAddedIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        val dateModifiedIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

        while (cursor.moveToNext()) {
            val path = dataIdx.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: continue
            val id = idIdx.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: continue
            val albumId = albumIdIdx.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: -1

            val song = Song(
                id = id,
                title = titleIdx.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: "Unknown",
                artist = artistIdx.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: "Unknown Artist",
                album = albumIdx.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: "Unknown Album",
                albumId = albumId,
                duration = durationIdx.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
                uri = ContentUris.withAppendedId(collection, id),
                artworkUri = if (albumId > 0) ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                ) else null,
                filePath = path,
                codec = detectCodec(path, mimeIdx.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: ""),
                size = sizeIdx.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
                trackNumber = trackIdx.takeIf { it >= 0 }?.let { cursor.getInt(it) } ?: 0,
                year = yearIdx.takeIf { it >= 0 }?.let { cursor.getInt(it) }?.takeIf { it > 0 },
                dateAdded = dateAddedIdx.takeIf { it >= 0 }?.let { cursor.getLong(it) }?.let { it * 1000 } ?: 0L,
                dateModified = dateModifiedIdx.takeIf { it >= 0 }?.let { cursor.getLong(it) }?.let { it * 1000 } ?: 0L
            )
            songList.add(song)
        }

        return songList
    }

    suspend fun scanAll(): List<Song> = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext _songs.value
        _isScanning.value = true
        _scanProgress.value = 0
        val startTime = System.currentTimeMillis()
        val songList = mutableListOf<Song>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} IS NULL"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        // Try multiple URIs for broad device compatibility (HyperOS/emulators)
        val urisToTry = listOfNotNull(
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else null
            }.getOrNull(),
            runCatching { MediaStore.Audio.Media.getContentUri("external") }.getOrNull(),
            runCatching { MediaStore.Audio.Media.EXTERNAL_CONTENT_URI }.getOrNull()
        ).distinct()

        for (uri in urisToTry) {
            try {
                android.util.Log.d("MuseScan", "Trying URI: $uri")
                context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                    android.util.Log.d("MuseScan", "URI $uri returned ${cursor.count} rows")
                    if (cursor.count > 0) {
                        songList.addAll(readSongsFromCursor(cursor))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MuseScan", "Query failed for $uri: ${e.message}")
            }
            if (songList.isNotEmpty()) break
        }

        if (songList.isEmpty()) {
            _songs.value = emptyList()
            _isScanning.value = false
            return@withContext emptyList()
        }

        val db = MuseDatabase.getInstance(context)
        // Preserve existing artworkUri before wiping DB (scanAll inserts fresh entities)
        val existingArtwork = songDao.getAllSongs().associate { it.id to it.artworkUri }
        db.withTransaction {
            songDao.deleteAll()
            albumDao.deleteAll()
            artistDao.deleteAll()
            songDao.insertAll(songList.map { it.toEntity() })
            albumDao.insertAll(buildAlbumEntities(songList))
            artistDao.insertAll(buildArtistEntities(songList))
        }
        // Restore persisted artworkUri from previous session
        for ((id, uri) in existingArtwork) {
            if (uri != null && songList.any { it.id == id }) {
                songDao.updateSongArtworkUri(id, uri)
            }
        }
        // Also restore from filesDir/covers/ for songs without DB artworkUri
        val coverDir = java.io.File(context.filesDir, "covers")
        val finalSongs = songList.map { song ->
            if (song.artworkUri == null || song.artworkUri.toString().isBlank()) {
                val coverFile = java.io.File(coverDir, "muse_art_${song.id}.png")
                if (coverFile.exists()) {
                    val fileUri = android.net.Uri.fromFile(coverFile)
                    song.copy(artworkUri = fileUri)
                } else song
            } else song
        }

        _songs.value = finalSongs
        _scanStats.value = ScanStats(
            songList.size,
            songList.distinctBy { it.album }.size,
            songList.distinctBy { it.artist }.size,
            System.currentTimeMillis() - startTime
        )
        _scanProgress.value = 100
        _isScanning.value = false
        songList
    }

    suspend fun scanFolder(path: String): List<Song> = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext _songs.value
        _isScanning.value = true
        _scanProgress.value = 0
        val startTime = System.currentTimeMillis()
        val songList = mutableListOf<Song>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} IS NULL) AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$path%")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            if (cursor.count == 0) {
                _isScanning.value = false
                return@withContext emptyList()
            }
            songList.addAll(readSongsFromCursor(cursor))
        }

        val db = MuseDatabase.getInstance(context)
        db.withTransaction {
            songDao.insertAll(songList.map { it.toEntity() })
            albumDao.insertAll(buildAlbumEntities(songList))
            artistDao.insertAll(buildArtistEntities(songList))
        }

        _songs.update { current ->
            val existingIds = current.map { it.id }.toSet()
            (current + songList.filter { it.id !in existingIds }).sortedBy { it.title }
        }
        _scanStats.value = ScanStats(
            _songs.value.size,
            _songs.value.distinctBy { it.album }.size,
            _songs.value.distinctBy { it.artist }.size,
            System.currentTimeMillis() - startTime
        )
        _scanProgress.value = 100
        _isScanning.value = false
        songList
    }

    suspend fun loadFromDatabase(): List<Song> = withContext(Dispatchers.IO) {
        val list = songDao.getAllSongs().map { it.toSong() }
        // Restore artworkUri from persisted cover files if DB has null
        val restored = list.map { song ->
            if (song.artworkUri == null) {
                val coverFile = java.io.File(context.filesDir, "covers/muse_art_${song.id}.png")
                if (coverFile.exists()) {
                    song.copy(artworkUri = android.net.Uri.fromFile(coverFile))
                } else song
            } else song
        }
        _songs.value = restored
        restored
    }

    suspend fun deleteSong(song: Song): Boolean = withContext(Dispatchers.IO) {
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
                } else false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Modify an audio file via ContentResolver (MediaStore URI), bypassing
     * direct File path issues on Android 10+ FUSE filesystem.
     *
     * Flow: song.uri → ContentResolver → temp cache → TagEditor modify → ContentResolver ← song.uri
     */
    private suspend fun modifyAudioFileViaContentResolver(
        song: Song,
        modifier: suspend (File) -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            // 1. Read original file from MediaStore URI to app cache
            tempFile = File(context.cacheDir, "muse_edit_${song.id}_${System.nanoTime()}")
            context.contentResolver.openInputStream(song.uri)?.use { input ->
                tempFile!!.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false

            // 2. Apply metadata modification on the temp file
            if (!modifier(tempFile!!)) return@withContext false

            // 3. Write modified file back via ContentResolver ("wt" = truncate + write)
            context.contentResolver.openOutputStream(song.uri, "wt")?.use { output ->
                tempFile!!.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext false

            true
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "modifyAudioFileViaContentResolver failed", e)
            false
        } finally {
            tempFile?.delete()
        }
    }

    suspend fun renameSong(song: Song, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Write to file via ContentResolver (bypasses FUSE direct-path issues on A10+)
            val tagEditor = TagEditor()
            val fileOk = modifyAudioFileViaContentResolver(song) { tempFile ->
                tagEditor.writeMetadata(filePath = tempFile.absolutePath, title = newTitle)
            }

            // 2. Force MediaStore re-index
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, newTitle)
                }
                context.contentResolver.update(song.uri, values, null, null)
            } catch (_: Exception) { }
            try {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            } catch (_: Exception) { }

            // 3. ALWAYS update local Room DB — ensures rename works in-app
            songDao.updateSongMeta(song.id, newTitle, song.uri.toString(), song.filePath)
            _songs.update { list ->
                list.map { if (it.id == song.id) it.copy(title = newTitle) else it }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val likeQuery = "%$query%"
        songDao.searchSongs(likeQuery).map { it.toSong() }
    }

    /**
     * Update a song's file tags + Room DB + trigger MediaStore re-scan.
     * Writes directly to the audio file so changes survive reinstall.
     */
    suspend fun updateSongTags(
        song: Song,
        title: String,
        artist: String,
        album: String,
        year: Int?,
        genre: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Write to file via ContentResolver (bypasses FUSE direct-path issues on A10+)
            val tagEditor = TagEditor()
            val fileOk = modifyAudioFileViaContentResolver(song) { tempFile ->
                tagEditor.writeMetadata(
                    filePath = tempFile.absolutePath,
                    title = title,
                    artist = artist,
                    album = album,
                    year = year,
                    genre = genre
                )
            }

            // 2. Force MediaStore to re-index the file so the new tags are picked up
            try {
                // Update MediaStore entry with new values (may fail silently on A10+)
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    if (album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, album)
                }
                context.contentResolver.update(song.uri, values, null, null)
            } catch (_: Exception) { }

            // Also trigger file-level re-scan via MediaScannerConnection
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(song.filePath),
                    null,
                    null
                )
            } catch (_: Exception) { }

            // 3. ALWAYS update Room DB regardless of file write result
            songDao.updateSongMetadata(
                id = song.id,
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre,
                artworkUri = song.artworkUri?.toString()
            )

            // 3. Try MediaStore update as best-effort
            try {
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    if (album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, album)
                }
                context.contentResolver.update(song.uri, contentValues, null, null)
            } catch (_: Exception) { }

            // 4. Update in-memory state
            _songs.update { list ->
                list.map {
                    if (it.id == song.id) {
                        it.copy(
                            title = title,
                            artist = artist,
                            album = album,
                            year = year,
                            genre = genre
                        )
                    } else it
                }
            }

            fileOk
        } catch (_: Exception) {
            false
        }
    }
    suspend fun updateSongWithMetadata(song: Song, result: luzzr.muse.data.network.MetadataResult): Song = withContext(Dispatchers.IO) {
        var fileModified = false

        // Strategy 1: Try direct file path write (works on emulators, root, MANAGE_EXTERNAL_STORAGE)
        try {
            fileModified = TagEditor().writeMetadata(
                filePath = song.filePath,
                title = result.title.takeIf { it.isNotBlank() },
                artist = result.artist.takeIf { it.isNotBlank() },
                album = result.album.takeIf { it.isNotBlank() },
                year = result.year,
                genre = result.genre.takeIf { it.isNotBlank() }
            )
            android.util.Log.d("MusicRepository", "updateSongWithMetadata: direct write $fileModified")
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "updateSongWithMetadata: direct write failed", e)
        }

        // Strategy 2: Fall back to ContentResolver round-trip
        if (!fileModified) {
            try {
                fileModified = modifyAudioFileViaContentResolver(song) { tempFile ->
                    TagEditor().writeMetadata(
                        filePath = tempFile.absolutePath,
                        title = result.title.takeIf { it.isNotBlank() },
                        artist = result.artist.takeIf { it.isNotBlank() },
                        album = result.album.takeIf { it.isNotBlank() },
                        year = result.year,
                        genre = result.genre.takeIf { it.isNotBlank() }
                    )
                }
                android.util.Log.d("MusicRepository", "updateSongWithMetadata: CR write $fileModified")
            } catch (e: Exception) {
                android.util.Log.e("MusicRepository", "updateSongWithMetadata: CR write failed", e)
            }
        }

        // If file was modified -> scanFile to refresh MediaStore from file (single source of truth)
        if (fileModified) {
            try {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            } catch (_: Exception) { }
        } else {
            // File write failed -> try direct MediaStore database update as fallback
            try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, result.title)
                    put(MediaStore.Audio.Media.ARTIST, result.artist)
                    if (result.album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, result.album)
                }
                context.contentResolver.update(song.uri, values, null, null)
            } catch (_: Exception) { }
        }

        // 4. ALWAYS update Room DB + in-memory state (even if file write fails)
        val artworkStr = (result.coverUrl ?: song.artworkUri?.toString())
        songDao.updateSongMetadata(
            id = song.id,
            title = result.title,
            artist = result.artist,
            album = result.album,
            year = result.year ?: song.year,
            genre = result.genre.ifBlank { song.genre },
            artworkUri = artworkStr
        )
        val updated = song.copy(
            title = result.title,
            artist = result.artist,
            album = result.album,
            year = result.year ?: song.year,
            genre = result.genre.ifBlank { song.genre },
            artworkUri = artworkStr?.let { android.net.Uri.parse(it) }
        )
        _songs.update { list ->
            list.map { if (it.id == song.id) updated else it }
        }
        updated
    }

    /**
     * Write artwork bytes to an audio file, trying direct path first then ContentResolver.
     * @return true if the file was successfully modified
     */
    private suspend fun writeArtworkToFile(song: Song, artworkBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        // Strategy 1: Direct path write
        try {
            TagEditor().writeArtwork(
                filePath = song.filePath,
                artworkBytes = artworkBytes,
                mimeType = "image/png"
            ).also { ok ->
                android.util.Log.d("MusicRepository", "writeArtworkToFile: direct write $ok")
                if (ok) return@withContext true
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "writeArtworkToFile: direct write failed", e)
        }

        // Strategy 2: ContentResolver round-trip
        try {
            return@withContext modifyAudioFileViaContentResolver(song) { tempFile ->
                TagEditor().writeArtwork(
                    filePath = tempFile.absolutePath,
                    artworkBytes = artworkBytes,
                    mimeType = "image/png"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "writeArtworkToFile: CR write failed", e)
            false
        }
    }

    /**
     * Update a song's embedded album art. Writes the image bytes to the audio file
     * via direct path or ContentResolver, then updates the in-memory state.
     */
    suspend fun updateSongArtwork(song: Song, artworkBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            // Best-effort: try to embed cover into audio file's metadata
            writeArtworkToFile(song, artworkBytes)

            // ALWAYS write to internal storage — the reliable display source
            val coverDir = java.io.File(context.filesDir, "covers")
            coverDir.mkdirs()
            try {
                java.io.File(coverDir, "muse_art_${song.id}.png").outputStream().use {
                    it.write(artworkBytes)
                }
            } catch (_: Exception) { }

            // Refresh from file so MediaStore picks it up
            try {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            } catch (_: Exception) { }

            val artworkUri = android.net.Uri.fromFile(java.io.File(coverDir, "muse_art_${song.id}.png"))
            _songs.update { list ->
                list.map { if (it.id == song.id) it.copy(artworkUri = artworkUri) else it }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "updateSongArtwork failed", e)
            false
        }
    }

    // --- Lyrics Persistence ---

    /**
     * Save lyrics to Room DB for long-term persistence.
     */
    suspend fun saveLyrics(songId: Long, syncedLyrics: String?, plainText: String?) {
        withContext(Dispatchers.IO) {
            lyricsDao.insertLyrics(
                LyricsEntity(
                    songId = songId,
                    syncedLyrics = syncedLyrics,
                    plainText = plainText
                )
            )
        }
    }

    /**
     * Load lyrics from Room DB. Returns raw LRC text and plain text.
     */
    suspend fun loadLyrics(songId: Long): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        lyricsDao.getLyrics(songId)?.let {
            it.syncedLyrics to it.plainText
        }
    }

    /**
     * Delete lyrics for a song (e.g., on song removal).
     */
    suspend fun deleteLyrics(songId: Long) {
        withContext(Dispatchers.IO) { lyricsDao.deleteLyrics(songId) }
    }

    // --- Per-song Default Cover Generation ---

    /**
     * Generate and write a default cover for a song, replacing any existing artwork.
     * Always regenerates and overwrites the filesDir/covers/ file, updates in-memory
     * state, and persists to Room DB. Previous artwork (MediaStore, embedded) is
     * replaced with the generated default cover.
     * @return true if cover was successfully generated and written
     */
    suspend fun generateDefaultCoverForSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        android.util.Log.d("MusicRepository", "generateDefaultCoverForSong: song=${song.id} title=${song.title}")
        val current = _songs.value.find { it.id == song.id } ?: return@withContext false.also {
            android.util.Log.w("MusicRepository", "generateDefaultCoverForSong: song ${song.id} not found in _songs")
        }

        // 文档注释更新在函数声明处
        try {
            val coverBytes = DefaultCoverGenerator.generate(song.title)
            // Best-effort: try to embed cover into audio file's metadata
            writeArtworkToFile(song, coverBytes)

            // ALWAYS write to filesDir/covers/ — the reliable display source
            val coverDir = java.io.File(context.filesDir, "covers")
            coverDir.mkdirs()
            val coverFile = java.io.File(coverDir, "muse_art_${song.id}.png")
            try {
                coverFile.outputStream().use { it.write(coverBytes) }
            } catch (_: Exception) { }

            val artworkUri = android.net.Uri.fromFile(coverFile)
            val artworkUriStr = artworkUri.toString()
            _songs.update { list ->
                list.map { if (it.id == song.id) it.copy(artworkUri = artworkUri) else it }
            }

            // Persist artworkUri to Room DB so it survives restart
            try {
                songDao.updateSongArtworkUri(song.id, artworkUriStr)
                android.util.Log.d("MusicRepository", "persisted artworkUri to DB: id=${song.id} uri=$artworkUriStr")
            } catch (e: Exception) {
                android.util.Log.e("MusicRepository", "Failed to persist artworkUri to DB", e)
            }

            // Trigger MediaStore re-scan so the change is visible system-wide
            try {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            } catch (_: Exception) { }
            true
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("MusicRepository", "generateDefaultCoverForSong OOM", e)
            Runtime.getRuntime().gc()
            false
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "generateDefaultCoverForSong failed", e)
            false
        }
    }

    /** Signal emitted when batch cover generation completes, so ViewModels can refresh. */
    private val _coverGenerationCompleted = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val coverGenerationCompleted: kotlinx.coroutines.flow.SharedFlow<Unit> = _coverGenerationCompleted.asSharedFlow()

    // --- Batch default cover generation ---

    private val _coverGenState = MutableStateFlow(CoverGenState())
    val coverGenState: StateFlow<CoverGenState> = _coverGenState.asStateFlow()

    data class CoverGenState(
        val isRunning: Boolean = false,
        val processed: Int = 0,
        val total: Int = 0,
        val errors: Int = 0
    )

    /**
     * Generate and write default covers for ALL songs, replacing any existing artwork.
     * Uses direct file path write for reliability.
     */
    suspend fun generateDefaultCoversForAll(): Boolean = withContext(Dispatchers.IO) {
        val songs = _songs.value
        if (songs.isEmpty() || _coverGenState.value.isRunning) return@withContext false

        _coverGenState.value = CoverGenState(isRunning = true, total = songs.size)
        var errorCount = 0

        for ((index, song) in songs.withIndex()) {
            try {
                // Generate cover bytes (Bitmap created and recycled inside generate())
                val coverBytes = DefaultCoverGenerator.generate(song.title)

                // Best-effort: try to embed cover into audio file's metadata
                val ok = writeArtworkToFile(song, coverBytes)
                if (!ok) {
                    errorCount++
                }

                // ALWAYS write to internal storage for reliable display
                val coverDir = java.io.File(context.filesDir, "covers")
                coverDir.mkdirs()
                try {
                    java.io.File(coverDir, "muse_art_${song.id}.png").outputStream().use {
                        it.write(coverBytes)
                    }
                } catch (_: Exception) { }
                val artworkUri = android.net.Uri.fromFile(java.io.File(coverDir, "muse_art_${song.id}.png"))
                _songs.update { list ->
                    list.map { if (it.id == song.id) it.copy(artworkUri = artworkUri) else it }
                }

                // Persist artworkUri to Room DB so it survives app restart
                try {
                    songDao.updateSongArtworkUri(song.id, artworkUri.toString())
                } catch (e: Exception) {
                    android.util.Log.e("MusicRepository", "generateDefaultCoversForAll: DB persist failed for ${song.id}", e)
                }

                // Avoid OOM: yield and hint GC every 5 songs
                if (index % 5 == 4) {
                    kotlinx.coroutines.yield()
                    Runtime.getRuntime().gc()
                }
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("MusicRepository", "generateDefaultCovers: OOM at #$index", e)
                errorCount++
                Runtime.getRuntime().gc()
            } catch (e: NoClassDefFoundError) {
                android.util.Log.e("MusicRepository", "generateDefaultCovers: missing class at #$index", e)
                errorCount++
            } catch (e: Exception) {
                android.util.Log.e("MusicRepository", "generateDefaultCovers: error at #$index", e)
                errorCount++
            }

            _coverGenState.value = CoverGenState(
                isRunning = index < songs.size - 1,
                processed = index + 1,
                total = songs.size,
                errors = errorCount
            )
        }

        // Trigger album art refresh after batch completion
        if (errorCount < songs.size) {
            refreshAlbumAndArtistTables()
        }

        val finalErrors = errorCount
        _coverGenState.value = CoverGenState(
            isRunning = false,
            processed = songs.size,
            total = songs.size,
            errors = finalErrors
        )
        // Notify observers (LibraryViewModel) to refresh album/artist lists
        _coverGenerationCompleted.tryEmit(Unit)
        finalErrors == 0
    }

    /**
     * Generate default covers ONLY for songs that have no artworkUri and no existing
     * cover file on disk. Runs silently in the background.
     */
    suspend fun generateMissingCovers() = withContext(Dispatchers.IO) {
        val songs = _songs.value
        val coverDir = java.io.File(context.filesDir, "covers")
        coverDir.mkdirs()
        var generated = 0
        for (song in songs) {
            // Skip if song already has a valid artworkUri pointing to an existing file
            if (song.artworkUri != null && song.artworkUri.toString().isNotBlank()) {
                try {
                    val testStream = context.contentResolver.openInputStream(song.artworkUri!!)
                    if (testStream != null) {
                        testStream.close()
                        continue
                    }
                } catch (_: Exception) { }
            }
            // Skip if cover file already exists on disk
            val coverFile = java.io.File(coverDir, "muse_art_${song.id}.png")
            if (coverFile.exists()) {
                // File exists but song.artworkUri is null — restore it
                val uri = android.net.Uri.fromFile(coverFile)
                _songs.update { list ->
                    list.map { if (it.id == song.id) it.copy(artworkUri = uri) else it }
                }
                songDao.updateSongArtworkUri(song.id, uri.toString())
                continue
            }
            // Generate new cover for missing songs
            try {
                val coverBytes = DefaultCoverGenerator.generate(song.title)
                coverFile.outputStream().use { it.write(coverBytes) }
                val uri = android.net.Uri.parse("file://${coverFile.absolutePath}")
                _songs.update { list ->
                    list.map { if (it.id == song.id) it.copy(artworkUri = uri) else it }
                }
                songDao.updateSongArtworkUri(song.id, uri.toString())
                generated++
                // Yield every 5 to avoid blocking
                if (generated % 5 == 0) {
                    kotlinx.coroutines.yield()
                    Runtime.getRuntime().gc()
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicRepository", "generateMissingCovers: failed for id=${song.id}", e)
            }
        }
        if (generated > 0) {
            android.util.Log.d("MusicRepository", "generateMissingCovers: generated $generated covers")
        }
    }

    suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        albumDao.getAllAlbums().map { it.toAlbum() }
    }

    suspend fun getArtists(): List<Artist> = withContext(Dispatchers.IO) {
        artistDao.getAllArtists().map { it.toArtist() }
    }

    suspend fun getSongsByAlbum(album: String): List<Song> = withContext(Dispatchers.IO) {
        songDao.getSongsByAlbum(album).map { it.toSong() }
    }

    suspend fun getSongsByArtist(artist: String): List<Song> = withContext(Dispatchers.IO) {
        songDao.getSongsByArtist(artist).map { it.toSong() }
    }

    /**
     * Rebuild album and artist tables from current in-memory song list.
     * Call this after any metadata change that affects album/artist relationships.
     */
    suspend fun refreshAlbumAndArtistTables() = withContext(Dispatchers.IO) {
        val currentSongs = _songs.value
        albumDao.deleteAll()
        artistDao.deleteAll()
        albumDao.insertAll(buildAlbumEntities(currentSongs))
        artistDao.insertAll(buildArtistEntities(currentSongs))
    }

    /** Download bytes from a URL. Used for cover art download. */
    suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = true
            val bytes = conn.inputStream.readBytes()
            conn.disconnect()
            bytes.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "downloadBytes failed: $url", e)
            null
        }
    }

    private fun detectCodec(path: String, mime: String): String {
        val ext = path.substringAfterLast(".").lowercase()
        return when {
            mime.contains("flac") || ext == "flac" -> "FLAC"
            mime.contains("opus") || ext == "opus" -> "Opus"
            mime.contains("m4a") || mime.contains("mp4") || ext == "m4a" -> "M4A/AAC"
            mime.contains("alac") || ext == "alac" -> "ALAC"
            mime.contains("wav") || ext == "wav" -> "WAV"
            mime.contains("ogg") || ext == "ogg" -> "OGG"
            mime.contains("mp3") || ext == "mp3" -> "MP3"
            ext.isBlank() -> "UNKNOWN"
            else -> ext.uppercase()
        }
    }

    private fun buildAlbumEntities(songs: List<Song>): List<AlbumEntity> {
        return songs.groupBy { it.album to it.albumId }
            .map { (key, group) ->
                val (_, albumId) = key
                val first = group.first()
                // When albumId is -1 (MediaStore default for songs without album art),
                // derive a unique negative ID from the album title hash to avoid
                // primary key collision. Two different albums would get different IDs.
                val finalAlbumId = if (albumId <= 0) {
                    val hash = first.album.hashCode().toLong() and 0x7FFFFFFF
                    if (hash == 0L) -1L else -hash
                } else albumId
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
        return songs.groupBy { it.artist }
            .map { (artist, group) ->
                ArtistEntity(
                    artistName = artist,
                    songCount = group.size,
                    albumCount = group.distinctBy { it.album }.size
                )
            }
    }

    companion object {
        @Volatile
        private var instance: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val db = MuseDatabase.getInstance(context)
                    MusicRepository(
                        context.applicationContext,
                        db.songDao(),
                        db.albumDao(),
                        db.artistDao(),
                        db.lyricsDao()
                    ).also { instance = it }
                }
            }
        }
    }
}

fun Song.toEntity() = SongEntity(
    id, title, artist, album, albumId, duration,
    uri.toString(), artworkUri?.toString(), trackNumber, year, genre,
    dateAdded, dateModified, albumArtist, bitrate, sampleRate, channels,
    codec, size, filePath
)

fun SongEntity.toSong() = Song(
    id, title, artist, album, albumId, duration,
    Uri.parse(uri), artworkUri?.let { Uri.parse(it) }, trackNumber, year,
    genre, dateAdded, dateModified, albumArtist, bitrate, sampleRate,
    channels, codec, size, filePath
)

fun AlbumEntity.toAlbum() = Album(
    id = albumId,
    title = title,
    artist = artist,
    artworkUri = artworkUri?.let { Uri.parse(it) },
    songCount = songCount,
    year = year
)

fun ArtistEntity.toArtist() = Artist(
    name = artistName,
    songCount = songCount,
    albumCount = albumCount
)
