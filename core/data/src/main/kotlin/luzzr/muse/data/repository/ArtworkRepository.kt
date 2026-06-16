package luzzr.muse.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.audio.AudioFileSupport
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.tag.DefaultCoverGenerator
import luzzr.muse.data.tag.Mp4MetadataAtomWriter
import luzzr.muse.data.tag.OggOpusMetadataParser
import luzzr.muse.data.tag.TagEditor
import luzzr.muse.domain.model.CoverGenState
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.PrivilegedFileWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

@Singleton
class ArtworkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepositoryImpl,
    private val songDao: SongDao,
    private val tagEditor: TagEditor,
    private val privilegedFileWriter: PrivilegedFileWriter? = null
) : luzzr.muse.domain.repository.ArtworkRepository {

    private val _coverGenerationCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val coverGenerationCompleted: SharedFlow<Unit> = _coverGenerationCompleted.asSharedFlow()

    private val _coverGenState = MutableStateFlow(CoverGenState())
    override val coverGenState: StateFlow<CoverGenState> = _coverGenState.asStateFlow()

    override fun generateDefaultCoverPreview(title: String): OperationResult<ByteArray> {
        return try {
            OperationResult.Success(DefaultCoverGenerator.generate(title))
        } catch (e: OutOfMemoryError) {
            MuseLog.e("ArtworkRepository", "generateDefaultCoverPreview OOM", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        } catch (e: IllegalStateException) {
            MuseLog.e("ArtworkRepository", "generateDefaultCoverPreview invalid state", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    override suspend fun generateDefaultCoverForSong(song: Song): OperationResult<Unit> = withContext(Dispatchers.IO) {
        MuseLog.d("ArtworkRepository", "generateDefaultCoverForSong: song=${song.id} title=${song.title}")
        val current = allLibraryAudio().find { it.id == song.id } ?: return@withContext OperationResult.Failure(
            OperationError.NOT_FOUND,
            "Song ${song.id} was not found"
        ).also {
            MuseLog.w("ArtworkRepository", "generateDefaultCoverForSong: song ${song.id} not found in library")
        }

        try {
            tagEditor.readArtwork(current.filePath)?.takeIf { it.isNotEmpty() }?.let { embedded ->
                return@withContext persistArtworkCache(current, embedded)
            }

            val coverBytes = prepareArtworkForEmbedding(DefaultCoverGenerator.generate(current.title))
            when (val fileWrite = writeArtworkToFile(current, coverBytes)) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> return@withContext fileWrite
            }

            when (val cacheResult = persistArtworkCache(current, coverBytes)) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> return@withContext cacheResult
            }

            try {
                MediaScannerConnection.scanFile(context, arrayOf(current.filePath), null, null)
            } catch (e: android.content.ActivityNotFoundException) {
                MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: MediaScanner not found", e)
            } catch (e: SecurityException) {
                MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: MediaScanner permission denied", e)
            }
            OperationResult.Success(Unit)
        } catch (e: OutOfMemoryError) {
            MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong OOM", e)
            Runtime.getRuntime().gc()
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: android.database.sqlite.SQLiteException) {
            MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: database error", e)
            OperationResult.Failure(OperationError.DATABASE, e.message)
        }
    }

    /**
     * Generate and write default covers for ALL songs, replacing any existing artwork.
     */
    override suspend fun generateDefaultCoversForAll(): OperationResult<Unit> = withContext(Dispatchers.IO) {
        val songs = allLibraryAudio()
        if (songs.isEmpty()) {
            return@withContext OperationResult.Failure(OperationError.NOT_FOUND, "No songs available")
        }
        if (_coverGenState.value.isRunning) {
            return@withContext OperationResult.Failure(OperationError.CONFLICT, "Cover generation is running")
        }

        _coverGenState.value = CoverGenState(isRunning = true, total = songs.size)
        var errorCount = 0

        for ((index, song) in songs.withIndex()) {
            try {
                val coverBytes = prepareArtworkForEmbedding(DefaultCoverGenerator.generate(song.title))

                when (val artworkWriteResult = writeArtworkToFile(song, coverBytes)) {
                    is OperationResult.Success -> {
                        if (persistArtworkCache(song, coverBytes) is OperationResult.Failure) {
                            errorCount++
                        }
                    }
                    is OperationResult.Failure -> {
                        errorCount++
                    }
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
        if (finalErrors == 0) {
            OperationResult.Success(Unit)
        } else {
            OperationResult.Failure(OperationError.IO, "$finalErrors cover writes failed")
        }
    }

    /**
     * Generate default covers ONLY for songs that have no artworkUri and no existing
     * cover file on disk.
     */
    override suspend fun generateMissingCovers(): OperationResult<Int> = withContext(Dispatchers.IO) {
        val songs = allLibraryAudio()
        val coverDir = java.io.File(context.filesDir, "covers")
        coverDir.mkdirs()
        var generated = 0
        for (song in songs) {
            tagEditor.readArtwork(song.filePath)?.takeIf { it.isNotEmpty() }?.let { embedded ->
                if (persistArtworkCache(song, embedded) is OperationResult.Success) {
                    generated++
                }
                continue
            }

            val coverFile = java.io.File(coverDir, "muse_art_${song.id}.png")
            if (coverFile.exists()) {
                coverFile.delete()
                songDao.updateSongArtworkUri(song.id, null)
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = null) }
            }

            try {
                val coverBytes = prepareArtworkForEmbedding(DefaultCoverGenerator.generate(song.title))
                when (val fileWrite = writeArtworkToFile(song, coverBytes)) {
                    is OperationResult.Success -> {
                        if (persistArtworkCache(song, coverBytes) is OperationResult.Success) {
                            generated++
                        }
                    }
                    is OperationResult.Failure -> {
                        MuseLog.w("ArtworkRepository", "generateMissingCovers: physical write failed for ${song.id}: ${fileWrite.message}")
                    }
                }
                if (generated > 0 && generated % COVER_YIELD_INTERVAL == 0) {
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
        OperationResult.Success(generated)
    }

    private suspend fun persistArtworkCache(song: Song, artworkBytes: ByteArray): OperationResult<Unit> {
        return try {
            val coverDir = File(context.filesDir, "covers")
            coverDir.mkdirs()
            val coverFile = File(coverDir, "muse_art_${song.id}.png")
            coverFile.outputStream().use { it.write(artworkBytes) }
            val artworkUri = coverFile.toUri().toString()
            songDao.updateSongArtworkUri(song.id, artworkUri)
            songRepository.updateSongInList(song.id) { it.copy(artworkUri = artworkUri) }
            OperationResult.Success(Unit)
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "persistArtworkCache: cover file write failed for ${song.id}", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: android.database.sqlite.SQLiteException) {
            MuseLog.e("ArtworkRepository", "persistArtworkCache: DB update failed for ${song.id}", e)
            OperationResult.Failure(OperationError.DATABASE, e.message)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "persistArtworkCache: unexpected error for ${song.id}", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun allLibraryAudio(): List<Song> {
        return (songRepository.songs.value + songRepository.audiobooks.value).distinctBy { it.id }
    }

    /**
     * Update a song's embedded album art.
     */
    override suspend fun updateSongArtwork(song: Song, artworkBytes: ByteArray): OperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val preparedArtwork = prepareArtworkForEmbedding(artworkBytes)
            val fileWriteResult = writeArtworkToFile(song, preparedArtwork)
            if (fileWriteResult is OperationResult.Failure) {
                MuseLog.e(
                    "ArtworkRepository",
                    "updateSongArtwork: physical artwork write failed: ${fileWriteResult.message}"
                )
                return@withContext fileWriteResult
            }

            val coverDir = java.io.File(context.filesDir, "covers")
            coverDir.mkdirs()
            var cacheWritten = false
            try {
                java.io.File(coverDir, "muse_art_${song.id}.png").outputStream().use {
                    it.write(preparedArtwork)
                }
                cacheWritten = true
            } catch (e: IOException) {
                MuseLog.e("ArtworkRepository", "updateSongArtwork: cover file write failed", e)
            }

            if (cacheWritten) {
                val artworkUri = android.net.Uri.fromFile(java.io.File(coverDir, "muse_art_${song.id}.png")).toString()
                songDao.updateSongArtworkUri(song.id, artworkUri)
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = artworkUri) }
            }

            try {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            } catch (e: Exception) {
                MuseLog.e("ArtworkRepository", "updateSongArtwork: MediaScanner failed", e)
            }

            if (cacheWritten) {
                OperationResult.Success(Unit)
            } else {
                OperationResult.Failure(OperationError.IO, "Failed to cache artwork")
            }
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: SecurityException) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: OutOfMemoryError) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: artwork processing OOM", e)
            Runtime.getRuntime().gc()
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: unexpected error", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private suspend fun writeArtworkToFile(song: Song, artworkBytes: ByteArray): OperationResult<Unit> = withContext(Dispatchers.IO) {
        if (!AudioFileSupport.isSupportedAudioPath(song.filePath)) {
            val ext = AudioFileSupport.extension(song.filePath)
            MuseLog.e("ArtworkRepository", "writeArtworkToFile: unsupported extension '$ext' for ${song.filePath}")
            return@withContext OperationResult.Failure(
                OperationError.UNSUPPORTED_FILE,
                "Unsupported audio file type: $ext"
            )
        }
        val extension = File(song.filePath).extension.ifBlank { "mp3" }
        val suffix = "${song.id}_${System.nanoTime()}.$extension"
        val originalFile = File(context.cacheDir, "muse_art_original_$suffix")
        val editedFile = File(context.cacheDir, "muse_art_edited_$suffix")
        val startedAt = System.currentTimeMillis()
        try {
            val sourceResult = copyArtworkSourceToFile(song, originalFile)
            if (sourceResult is OperationResult.Failure) return@withContext sourceResult

            val mimeType = detectArtworkMimeType(artworkBytes)
            val writeResult = writeArtworkToEditedFile(
                sourceFile = originalFile,
                editedFile = editedFile,
                artworkBytes = artworkBytes,
                mimeType = mimeType
            )
            if (writeResult is OperationResult.Failure) {
                MuseLog.e("ArtworkRepository", "writeArtworkToFile: TagEditor failed to write artwork to temp file")
                return@withContext writeResult
            }
            if (!isEditedAudioUsable(editedFile)) {
                MuseLog.e("ArtworkRepository", "writeArtworkToFile: edited audio failed validation")
                return@withContext OperationResult.Failure(OperationError.IO, "Edited audio file failed validation")
            }

            // Step 3: Write back to original file
            val physicalFile = File(song.filePath)
            var lastResult: OperationResult<Unit>? = null

            if (physicalFile.exists() && physicalFile.canWrite()) {
                val physicalWrite = writePhysicalArtworkFileWithTimeout(physicalFile, editedFile, originalFile)
                if (physicalWrite is OperationResult.Success) {
                    MuseLog.w(
                        "ArtworkRepository",
                        "writeArtworkToFile: wrote artwork to physical file ms=${System.currentTimeMillis() - startedAt}"
                    )
                    return@withContext OperationResult.Success(Unit)
                } else {
                    lastResult = physicalWrite
                }
            }

            privilegedFileWriter?.takeIf { it.isAvailable() && song.filePath.isNotBlank() }?.let { shizuku ->
                when (val privilegedWrite = writePrivilegedArtworkFile(song, editedFile, originalFile, shizuku)) {
                    is OperationResult.Success -> {
                        MuseLog.w("ArtworkRepository", "writeArtworkToFile: privileged artwork write verified")
                        return@withContext OperationResult.Success(Unit)
                    }
                    is OperationResult.Failure -> {
                        lastResult = privilegedWrite
                        MuseLog.w(
                            "ArtworkRepository",
                            "writeArtworkToFile: privileged artwork write failed: ${privilegedWrite.message}"
                        )
                    }
                }
            }

            return@withContext lastResult ?: OperationResult.Failure(OperationError.IO, "Physical artwork write failed")
        } catch (e: SecurityException) {
            MuseLog.e("ArtworkRepository", "writeArtworkToFile: permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "writeArtworkToFile: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: OutOfMemoryError) {
            MuseLog.e("ArtworkRepository", "writeArtworkToFile: OOM while writing artwork", e)
            Runtime.getRuntime().gc()
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "writeArtworkToFile: unexpected error", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        } finally {
            originalFile.delete()
            editedFile.delete()
        }
    }

    private suspend fun writeArtworkToEditedFile(
        sourceFile: File,
        editedFile: File,
        artworkBytes: ByteArray,
        mimeType: String
    ): OperationResult<Unit> {
        val timeoutMs = artworkWriteTimeoutMs(sourceFile.length())
        return try {
            withTimeout(timeoutMs) {
                runInterruptible(Dispatchers.IO) {
                    val startedAt = System.currentTimeMillis()
                    val result = writeArtworkByFormat(sourceFile, editedFile, artworkBytes, mimeType)
                    MuseLog.w(
                        "ArtworkRepository",
                        "writeArtworkToEditedFile: ext=${sourceFile.extension.lowercase()} " +
                            "sourceBytes=${sourceFile.length()} artworkBytes=${artworkBytes.size} " +
                            "ms=${System.currentTimeMillis() - startedAt}"
                    )
                    result
                }
            }
        } catch (e: TimeoutCancellationException) {
            MuseLog.e("ArtworkRepository", "writeArtworkToEditedFile: timed out after ${timeoutMs}ms", e)
            OperationResult.Failure(OperationError.IO, "Artwork write timed out")
        } catch (e: InterruptedException) {
            MuseLog.e("ArtworkRepository", "writeArtworkToEditedFile: interrupted", e)
            OperationResult.Failure(OperationError.IO, e.message)
        }
    }

    private fun writeArtworkByFormat(
        sourceFile: File,
        editedFile: File,
        artworkBytes: ByteArray,
        mimeType: String
    ): OperationResult<Unit> {
        val ext = sourceFile.extension.lowercase()
        val success = when {
            ext in MP4_CONTAINER_EXTENSIONS -> {
                Mp4MetadataAtomWriter.writeArtwork(sourceFile, editedFile, artworkBytes, mimeType)
            }
            ext in OGG_EXTENSIONS && OggOpusMetadataParser.isOggOpusFile(sourceFile) -> {
                OggOpusMetadataParser.writeArtwork(sourceFile, editedFile, artworkBytes, mimeType)
            }
            else -> {
                sourceFile.copyTo(editedFile, overwrite = true, bufferSize = LARGE_FILE_BUFFER_SIZE)
                val writeResult = tagEditor.writeArtworkResult(
                    filePath = editedFile.absolutePath,
                    artworkBytes = artworkBytes,
                    mimeType = mimeType
                )
                writeResult is OperationResult.Success
            }
        }

        return if (success && editedFile.isFile && editedFile.length() > 0L) {
            OperationResult.Success(Unit)
        } else {
            OperationResult.Failure(OperationError.IO, "Embedded artwork write failed for .$ext")
        }
    }

    private fun copyArtworkSourceToFile(song: Song, destination: File): OperationResult<Unit> {
        val physicalResult = copyArtworkPhysicalFileToFile(song, destination)
        if (physicalResult is OperationResult.Success) return physicalResult

        val contentResult = copyArtworkContentUriToFile(song, destination)
        if (contentResult is OperationResult.Success) return contentResult

        return when {
            contentResult is OperationResult.Failure && contentResult.error == OperationError.PERMISSION_DENIED -> {
                contentResult
            }
            physicalResult is OperationResult.Failure && physicalResult.error == OperationError.PERMISSION_DENIED -> {
                physicalResult
            }
            physicalResult is OperationResult.Failure && physicalResult.error == OperationError.NOT_FOUND -> {
                physicalResult
            }
            contentResult is OperationResult.Failure -> contentResult
            else -> physicalResult
        }
    }

    private fun copyArtworkContentUriToFile(song: Song, destination: File): OperationResult<Unit> {
        return try {
            val input = context.contentResolver.openInputStream(song.uri.toUri())
                ?: return OperationResult.Failure(OperationError.IO, "Unable to read source audio URI")
            input.use { source ->
                destination.outputStream().use { output -> source.copyTo(output, LARGE_FILE_BUFFER_SIZE) }
            }
            verifyArtworkCopy(destination)
        } catch (e: SecurityException) {
            MuseLog.e("ArtworkRepository", "copyArtworkContentUriToFile: permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "copyArtworkContentUriToFile: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "copyArtworkContentUriToFile: unexpected error", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun copyArtworkPhysicalFileToFile(song: Song, destination: File): OperationResult<Unit> {
        val source = File(song.filePath)
        if (song.filePath.isBlank() || !source.exists()) {
            return OperationResult.Failure(OperationError.NOT_FOUND, "Source audio file was not found")
        }
        if (!source.canRead()) {
            return OperationResult.Failure(OperationError.PERMISSION_DENIED, "Cannot read source audio file")
        }

        return try {
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output, LARGE_FILE_BUFFER_SIZE) }
            }
            verifyArtworkCopy(destination)
        } catch (e: SecurityException) {
            MuseLog.e("ArtworkRepository", "copyArtworkPhysicalFileToFile: permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "copyArtworkPhysicalFileToFile: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "copyArtworkPhysicalFileToFile: unexpected error", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun verifyArtworkCopy(destination: File): OperationResult<Unit> {
        return if (destination.isFile && destination.length() > 0L) {
            OperationResult.Success(Unit)
        } else {
            OperationResult.Failure(OperationError.IO, "Copied source audio file was empty")
        }
    }

    private fun detectArtworkMimeType(bytes: ByteArray): String = when {
        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun prepareArtworkForEmbedding(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes
        val mimeType = detectArtworkMimeType(bytes)
        val dimensions = detectArtworkDimensions(bytes)
        val alreadySmallEnough =
            mimeType in EMBEDDABLE_ARTWORK_MIME_TYPES &&
                bytes.size <= MAX_EMBEDDED_ARTWORK_BYTES &&
                dimensions.first <= MAX_EMBEDDED_ARTWORK_DIMENSION &&
                dimensions.second <= MAX_EMBEDDED_ARTWORK_DIMENSION
        if (alreadySmallEnough) return bytes

        return compressArtworkToJpeg(bytes) ?: bytes
    }

    private fun compressArtworkToJpeg(bytes: ByteArray): ByteArray? {
        var decoded: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateBitmapSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxDimension = MAX_EMBEDDED_ARTWORK_DIMENSION
                )
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            val bitmap = decoded
            val maxSide = maxOf(bitmap.width, bitmap.height)
            val targetBitmap = if (maxSide > MAX_EMBEDDED_ARTWORK_DIMENSION) {
                val scale = MAX_EMBEDDED_ARTWORK_DIMENSION.toFloat() / maxSide.toFloat()
                val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also { scaled = it }
            } else {
                bitmap
            }

            var quality = EMBEDDED_ARTWORK_JPEG_QUALITY
            var best: ByteArray
            do {
                val output = ByteArrayOutputStream()
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                best = output.toByteArray()
                quality -= EMBEDDED_ARTWORK_QUALITY_STEP
            } while (best.size > MAX_EMBEDDED_ARTWORK_BYTES && quality >= MIN_EMBEDDED_ARTWORK_JPEG_QUALITY)

            best
        } catch (e: OutOfMemoryError) {
            MuseLog.e("ArtworkRepository", "compressArtworkToJpeg: OOM", e)
            Runtime.getRuntime().gc()
            null
        } catch (e: Throwable) {
            MuseLog.w("ArtworkRepository", "compressArtworkToJpeg: unable to normalize artwork", e)
            null
        } finally {
            scaled?.recycle()
            decoded?.recycle()
        }
    }

    private fun detectArtworkDimensions(bytes: ByteArray): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            Pair(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
        } catch (_: Throwable) {
            Pair(0, 0)
        }
    }

    private fun calculateBitmapSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (sampledWidth / 2 >= maxDimension || sampledHeight / 2 >= maxDimension) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun artworkWriteTimeoutMs(fileSizeBytes: Long): Long {
        val sizeBasedAllowance = (fileSizeBytes / ARTWORK_TIMEOUT_BYTES_PER_MS).coerceAtMost(MAX_ARTWORK_TIMEOUT_EXTRA_MS)
        return BASE_ARTWORK_WRITE_TIMEOUT_MS + sizeBasedAllowance
    }

    private fun isEditedAudioUsable(editedFile: File): Boolean {
        return tagEditor.canReadAudioFile(editedFile.absolutePath) ||
            tagEditor.hasRecognizedAudioHeader(editedFile.absolutePath)
    }

    private suspend fun writePrivilegedArtworkFile(
        song: Song,
        edited: File,
        original: File,
        shizuku: PrivilegedFileWriter
    ): OperationResult<Unit> {
        val writeResult = shizuku.copyToTarget(edited, song.filePath)
        if (writeResult is OperationResult.Failure) return writeResult

        if (targetHasSameArtworkContent(song, edited)) {
            return OperationResult.Success(Unit)
        }

        MuseLog.e("ArtworkRepository", "writePrivilegedArtworkFile: verification failed, restoring original")
        shizuku.copyToTarget(original, song.filePath)
        return OperationResult.Failure(OperationError.IO, "Privileged artwork write verification failed")
    }

    private fun targetHasSameArtworkContent(song: Song, expected: File): Boolean {
        return try {
            val physical = File(song.filePath)
            physical.isFile && physical.canRead() && filesHaveSameContent(physical, expected)
        } catch (e: Exception) {
            MuseLog.w("ArtworkRepository", "targetHasSameArtworkContent: verification read failed", e)
            false
        }
    }

    private fun writePhysicalArtworkFile(target: File, edited: File, original: File): OperationResult<Unit> {
        return try {
            edited.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, LARGE_FILE_BUFFER_SIZE) }
            }
            if (filesHaveSameContent(target, edited)) {
                OperationResult.Success(Unit)
            } else {
                restorePhysicalArtworkFile(target, original)
                OperationResult.Failure(OperationError.IO, "Physical artwork write verification failed")
            }
        } catch (e: SecurityException) {
            MuseLog.w("ArtworkRepository", "writePhysicalArtworkFile: permission denied", e)
            restorePhysicalArtworkFile(target, original)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.w("ArtworkRepository", "writePhysicalArtworkFile: IO failed", e)
            restorePhysicalArtworkFile(target, original)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.w("ArtworkRepository", "writePhysicalArtworkFile: unexpected error", e)
            restorePhysicalArtworkFile(target, original)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private suspend fun writePhysicalArtworkFileWithTimeout(target: File, edited: File, original: File): OperationResult<Unit> {
        val timeoutMs = artworkWriteTimeoutMs(edited.length())
        return try {
            withTimeout(timeoutMs) {
                runInterruptible(Dispatchers.IO) {
                    writePhysicalArtworkFile(target, edited, original)
                }
            }
        } catch (e: TimeoutCancellationException) {
            MuseLog.e("ArtworkRepository", "writePhysicalArtworkFileWithTimeout: timed out after ${timeoutMs}ms", e)
            restorePhysicalArtworkFile(target, original)
            OperationResult.Failure(OperationError.IO, "Physical artwork write timed out")
        } catch (e: InterruptedException) {
            MuseLog.e("ArtworkRepository", "writePhysicalArtworkFileWithTimeout: interrupted", e)
            restorePhysicalArtworkFile(target, original)
            OperationResult.Failure(OperationError.IO, e.message)
        }
    }

    private fun restorePhysicalArtworkFile(target: File, original: File) {
        try {
            original.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, LARGE_FILE_BUFFER_SIZE) }
            }
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "restorePhysicalArtworkFile: restore failed", e)
        }
    }

    private fun filesHaveSameContent(actual: File, expected: File): Boolean {
        if (actual.length() != expected.length()) return false
        actual.inputStream().use { actualInput ->
            expected.inputStream().use { expectedInput ->
                return streamsHaveSameContent(actualInput, expectedInput)
            }
        }
    }

    @Suppress("ReturnCount")
    private fun streamsHaveSameContent(actual: InputStream, expected: InputStream): Boolean {
        val actualBuffer = ByteArray(LARGE_FILE_BUFFER_SIZE)
        val expectedBuffer = ByteArray(LARGE_FILE_BUFFER_SIZE)
        while (true) {
            val actualCount = actual.read(actualBuffer)
            val expectedCount = expected.read(expectedBuffer)
            if (actualCount != expectedCount) return false
            if (actualCount < 0) return true
            for (index in 0 until actualCount) {
                if (actualBuffer[index] != expectedBuffer[index]) return false
            }
        }
    }

    override suspend fun downloadBytes(url: String): OperationResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = NETWORK_TIMEOUT_MS
            conn.readTimeout = NETWORK_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            val bytes = conn.inputStream.readBytes()
            conn.disconnect()
            if (bytes.isNotEmpty()) {
                OperationResult.Success(bytes)
            } else {
                OperationResult.Failure(OperationError.NETWORK, "Artwork response was empty")
            }
        } catch (e: java.net.SocketTimeoutException) {
            MuseLog.e("ArtworkRepository", "downloadBytes: timeout for $url", e)
            OperationResult.Failure(OperationError.NETWORK, e.message)
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "downloadBytes: IO error for $url", e)
            OperationResult.Failure(OperationError.NETWORK, e.message)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "downloadBytes: unexpected error for $url", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    companion object {
        private const val NETWORK_TIMEOUT_MS = 10_000
        private const val COVER_YIELD_INTERVAL = 5
        private const val LARGE_FILE_BUFFER_SIZE = 1024 * 1024
        private const val MAX_EMBEDDED_ARTWORK_BYTES = 700 * 1024
        private const val MAX_EMBEDDED_ARTWORK_DIMENSION = 1200
        private const val EMBEDDED_ARTWORK_JPEG_QUALITY = 88
        private const val MIN_EMBEDDED_ARTWORK_JPEG_QUALITY = 58
        private const val EMBEDDED_ARTWORK_QUALITY_STEP = 10
        private const val BASE_ARTWORK_WRITE_TIMEOUT_MS = 60_000L
        private const val MAX_ARTWORK_TIMEOUT_EXTRA_MS = 90_000L
        private const val ARTWORK_TIMEOUT_BYTES_PER_MS = 8_192L
        private val EMBEDDABLE_ARTWORK_MIME_TYPES = setOf("image/jpeg", "image/png")
        private val MP4_CONTAINER_EXTENSIONS = AudioFileSupport.mp4AudioContainerExtensions
        private val OGG_EXTENSIONS = setOf("ogg", "oga", "opus")
    }
}
