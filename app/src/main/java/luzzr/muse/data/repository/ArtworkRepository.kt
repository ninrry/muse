package luzzr.muse.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.mapper.isUsableArtworkUri
import luzzr.muse.data.model.Song
import luzzr.muse.data.tag.DefaultCoverGenerator
import luzzr.muse.data.tag.TagEditor
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

typealias CoverGenState = luzzr.muse.domain.model.CoverGenState

@Singleton
class ArtworkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepositoryImpl,
    private val songDao: SongDao
) : luzzr.muse.domain.repository.ArtworkRepository {

    private val _coverGenerationCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val coverGenerationCompleted: SharedFlow<Unit> = _coverGenerationCompleted.asSharedFlow()

    private val _coverGenState = MutableStateFlow(CoverGenState())
    override val coverGenState: StateFlow<CoverGenState> = _coverGenState.asStateFlow()

    override suspend fun generateDefaultCoverForSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        MuseLog.d("ArtworkRepository", "generateDefaultCoverForSong: song=${song.id} title=${song.title}")
        val current = songRepository.songs.value.find { it.id == song.id } ?: return@withContext false.also {
            MuseLog.w("ArtworkRepository", "generateDefaultCoverForSong: song ${song.id} not found in _songs")
        }

        try {
            val coverDir = java.io.File(context.filesDir, "covers")
            coverDir.mkdirs()
            val coverFile = java.io.File(coverDir, "muse_art_${current.id}.png")

            if (isUsableArtworkUri(current.artworkUri, context)) {
                return@withContext true
            }

            if (coverFile.exists()) {
                val artworkUri = android.net.Uri.fromFile(coverFile)
                songRepository.updateSongInList(current.id) { it.copy(artworkUri = artworkUri) }
                songDao.updateSongArtworkUri(current.id, artworkUri.toString())
                return@withContext true
            }

            val coverBytes = DefaultCoverGenerator.generate(current.title)
            writeArtworkToFile(current, coverBytes)

            // ALWAYS write to filesDir/covers/ �?the reliable display source
            try {
                coverFile.outputStream().use { it.write(coverBytes) }
            } catch (e: IOException) {
                MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: cover file write failed", e)
            }

            val artworkUri = android.net.Uri.fromFile(coverFile)
            val artworkUriStr = artworkUri.toString()
            songRepository.updateSongInList(current.id) { it.copy(artworkUri = artworkUri) }

            try {
                songDao.updateSongArtworkUri(current.id, artworkUriStr)
                MuseLog.d("ArtworkRepository", "persisted artworkUri to DB: id=${current.id} uri=$artworkUriStr")
            } catch (e: android.database.sqlite.SQLiteException) {
                MuseLog.e("ArtworkRepository", "Failed to persist artworkUri to DB: SQLite error", e)
            } catch (e: IllegalStateException) {
                MuseLog.e("ArtworkRepository", "Failed to persist artworkUri to DB: state error", e)
            }

            try {
                MediaScannerConnection.scanFile(context, arrayOf(current.filePath), null, null)
            } catch (e: android.content.ActivityNotFoundException) {
                MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: MediaScanner not found", e)
            } catch (e: SecurityException) {
                MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: MediaScanner permission denied", e)
            }
            true
        } catch (e: OutOfMemoryError) {
            MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong OOM", e)
            Runtime.getRuntime().gc()
            false
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: IO error", e)
            false
        } catch (e: android.database.sqlite.SQLiteException) {
            MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: database error", e)
            false
        }
    }

    /**
     * Generate and write default covers for ALL songs, replacing any existing artwork.
     */
    override suspend fun generateDefaultCoversForAll(): Boolean = withContext(Dispatchers.IO) {
        val songs = songRepository.songs.value
        if (songs.isEmpty() || _coverGenState.value.isRunning) return@withContext false

        _coverGenState.value = CoverGenState(isRunning = true, total = songs.size)
        var errorCount = 0

        for ((index, song) in songs.withIndex()) {
            try {
                val coverBytes = DefaultCoverGenerator.generate(song.title)

                val ok = writeArtworkToFile(song, coverBytes)
                if (!ok) {
                    errorCount++
                }

                val coverDir = java.io.File(context.filesDir, "covers")
                coverDir.mkdirs()
                try {
                    java.io.File(coverDir, "muse_art_${song.id}.png").outputStream().use {
                        it.write(coverBytes)
                    }
                } catch (e: IOException) {
                    MuseLog.e("ArtworkRepository", "generateDefaultCoversForAll: cover file write failed for ${song.id}", e)
                }
                val artworkUri = android.net.Uri.fromFile(java.io.File(coverDir, "muse_art_${song.id}.png"))
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = artworkUri) }

                try {
                    songDao.updateSongArtworkUri(song.id, artworkUri.toString())
                } catch (e: android.database.sqlite.SQLiteException) {
                    MuseLog.e("ArtworkRepository", "generateDefaultCoversForAll: DB SQLite error for ${song.id}", e)
                } catch (e: Exception) {
                    MuseLog.e("ArtworkRepository", "generateDefaultCoversForAll: DB persist failed for ${song.id}", e)
                }

                if (index % COVER_YIELD_INTERVAL == COVER_YIELD_INTERVAL - 1) {
                    kotlinx.coroutines.yield()
                    Runtime.getRuntime().gc()
                }
            } catch (e: OutOfMemoryError) {
                MuseLog.e("ArtworkRepository", "generateDefaultCovers: OOM at #$index", e)
                errorCount++
                Runtime.getRuntime().gc()
            } catch (e: NoClassDefFoundError) {
                MuseLog.e("ArtworkRepository", "generateDefaultCovers: missing class at #$index", e)
                errorCount++
            } catch (e: IOException) {
                MuseLog.e("ArtworkRepository", "generateDefaultCovers: IO error at #$index", e)
                errorCount++
            } catch (e: Exception) {
                MuseLog.e("ArtworkRepository", "generateDefaultCovers: unexpected error at #$index", e)
                errorCount++
            }

            _coverGenState.value = CoverGenState(
                isRunning = index < songs.size - 1,
                processed = index + 1,
                total = songs.size,
                errors = errorCount
            )
        }

        if (errorCount < songs.size) {
            songRepository.refreshAlbumAndArtistTables()
        }

        val finalErrors = errorCount
        _coverGenState.value = CoverGenState(
            isRunning = false,
            processed = songs.size,
            total = songs.size,
            errors = finalErrors
        )
        _coverGenerationCompleted.tryEmit(Unit)
        finalErrors == 0
    }

    /**
     * Generate default covers ONLY for songs that have no artworkUri and no existing
     * cover file on disk.
     */
    override suspend fun generateMissingCovers() = withContext(Dispatchers.IO) {
        val songs = songRepository.songs.value
        val coverDir = java.io.File(context.filesDir, "covers")
        coverDir.mkdirs()
        var generated = 0
        for (song in songs) {
            if (isUsableArtworkUri(song.artworkUri, context)) {
                continue
            }
            val coverFile = java.io.File(coverDir, "muse_art_${song.id}.png")
            if (coverFile.exists()) {
                val uri = android.net.Uri.fromFile(coverFile)
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = uri) }
                songDao.updateSongArtworkUri(song.id, uri.toString())
                continue
            }
            try {
                val coverBytes = DefaultCoverGenerator.generate(song.title)
                coverFile.outputStream().use { it.write(coverBytes) }
                val uri = android.net.Uri.parse("file://${coverFile.absolutePath}")
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = uri) }
                songDao.updateSongArtworkUri(song.id, uri.toString())
                generated++
                if (generated % COVER_YIELD_INTERVAL == 0) {
                    kotlinx.coroutines.yield()
                    Runtime.getRuntime().gc()
                }
            } catch (e: IOException) {
                MuseLog.e("ArtworkRepository", "generateMissingCovers: IO error for id=${song.id}", e)
            } catch (e: android.database.sqlite.SQLiteException) {
                MuseLog.e("ArtworkRepository", "generateMissingCovers: DB error for id=${song.id}", e)
            } catch (e: Exception) {
                MuseLog.e("ArtworkRepository", "generateMissingCovers: unexpected error for id=${song.id}", e)
            }
        }
        if (generated > 0) {
            MuseLog.d("ArtworkRepository", "generateMissingCovers: generated $generated covers")
        }
    }

    /**
     * Update a song's embedded album art.
     */
    override suspend fun updateSongArtwork(song: Song, artworkBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = writeArtworkToFile(song, artworkBytes)
            if (!ok) {
                MuseLog.w("ArtworkRepository", "updateSongArtwork: Failed to write artwork to audio file. Falling back to local cache.")
            }

            val coverDir = java.io.File(context.filesDir, "covers")
            coverDir.mkdirs()
            var cacheWritten = false
            try {
                java.io.File(coverDir, "muse_art_${song.id}.png").outputStream().use {
                    it.write(artworkBytes)
                }
                cacheWritten = true
            } catch (e: IOException) {
                MuseLog.e("ArtworkRepository", "updateSongArtwork: cover file write failed", e)
            }

            if (cacheWritten) {
                val artworkUri = android.net.Uri.fromFile(java.io.File(coverDir, "muse_art_${song.id}.png"))
                songDao.updateSongArtworkUri(song.id, artworkUri.toString())
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = artworkUri) }
            }

            try {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            } catch (e: Exception) {
                MuseLog.e("ArtworkRepository", "updateSongArtwork: MediaScanner failed", e)
            }

            cacheWritten
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: IO error", e)
            false
        } catch (e: SecurityException) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: permission denied", e)
            false
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: unexpected error", e)
            false
        }
    }

    private suspend fun writeArtworkToFile(song: Song, artworkBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val extension = File(song.filePath).extension.ifBlank { "mp3" }
        var tempFile: File? = null
        var fileOk = false
        try {
            tempFile = File(context.cacheDir, "muse_edit_${song.id}_${System.nanoTime()}.$extension")
            
            // Step 1: Copy original file to temp file
            context.contentResolver.openInputStream(song.uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false

            // Step 2: Apply artwork modification
            val writeOk = TagEditor().writeArtwork(
                filePath = tempFile.absolutePath,
                artworkBytes = artworkBytes,
                mimeType = "image/png"
            )
            if (!writeOk) {
                MuseLog.e("ArtworkRepository", "writeArtworkToFile: TagEditor failed to write artwork to temp file")
                return@withContext false
            }

            // Step 3: Write back to original file
            // Attempt 3.1: Direct physical file write (if has permission)
            val physicalFile = File(song.filePath)
            if (physicalFile.exists() && physicalFile.canWrite()) {
                try {
                    physicalFile.outputStream().use { output ->
                        tempFile.inputStream().use { input -> input.copyTo(output) }
                    }
                    fileOk = true
                    MuseLog.d("ArtworkRepository", "writeArtworkToFile: successfully wrote back artwork to physical file directly")
                } catch (e: Exception) {
                    MuseLog.w("ArtworkRepository", "writeArtworkToFile: physical write failed, trying ContentResolver", e)
                }
            }

            // Attempt 3.2: Fallback to ContentResolver write
            if (!fileOk) {
                try {
                    context.contentResolver.openOutputStream(song.uri, "wt")?.use { output ->
                        tempFile.inputStream().use { input -> input.copyTo(output) }
                    }
                    fileOk = true
                    MuseLog.d("ArtworkRepository", "writeArtworkToFile: successfully wrote back artwork via ContentResolver")
                } catch (e: Exception) {
                    MuseLog.e("ArtworkRepository", "writeArtworkToFile: ContentResolver write failed", e)
                }
            }
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "writeArtworkToFile: unexpected error", e)
        } finally {
            tempFile?.delete()
        }
        fileOk
    }

    override suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = NETWORK_TIMEOUT_MS
            conn.readTimeout = NETWORK_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            val bytes = conn.inputStream.readBytes()
            conn.disconnect()
            bytes.takeIf { it.isNotEmpty() }
        } catch (e: java.net.SocketTimeoutException) {
            MuseLog.e("ArtworkRepository", "downloadBytes: timeout for $url", e)
            null
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "downloadBytes: IO error for $url", e)
            null
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "downloadBytes: unexpected error for $url", e)
            null
        }
    }

    companion object {
        private const val NETWORK_TIMEOUT_MS = 10_000
        private const val COVER_YIELD_INTERVAL = 5
    }
}
