package luzzr.muse.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.mapper.isUsableArtworkUri
import luzzr.muse.data.tag.DefaultCoverGenerator
import luzzr.muse.data.tag.TagEditor
import luzzr.muse.domain.model.CoverGenState
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.PrivilegedFileWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
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
        val current = songRepository.songs.value.find { it.id == song.id } ?: return@withContext OperationResult.Failure(
            OperationError.NOT_FOUND,
            "Song ${song.id} was not found"
        ).also {
            MuseLog.w("ArtworkRepository", "generateDefaultCoverForSong: song ${song.id} not found in _songs")
        }

        try {
            val coverDir = java.io.File(context.filesDir, "covers")
            coverDir.mkdirs()
            val coverFile = java.io.File(coverDir, "muse_art_${current.id}.png")

            if (isUsableArtworkUri(current.artworkUri, context)) {
                return@withContext OperationResult.Success(Unit)
            }

            if (coverFile.exists()) {
                val artworkUri = android.net.Uri.fromFile(coverFile).toString()
                songRepository.updateSongInList(current.id) { it.copy(artworkUri = artworkUri) }
                songDao.updateSongArtworkUri(current.id, artworkUri)
                return@withContext OperationResult.Success(Unit)
            }

            val coverBytes = DefaultCoverGenerator.generate(current.title)
            writeArtworkToFile(current, coverBytes)

            // ALWAYS write to filesDir/covers/ ->the reliable display source
            try {
                coverFile.outputStream().use { it.write(coverBytes) }
            } catch (e: IOException) {
                MuseLog.e("ArtworkRepository", "generateDefaultCoverForSong: cover file write failed", e)
            }

            val artworkUriStr = android.net.Uri.fromFile(coverFile).toString()
            songRepository.updateSongInList(current.id) { it.copy(artworkUri = artworkUriStr) }

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
        val songs = songRepository.songs.value
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
                val coverBytes = DefaultCoverGenerator.generate(song.title)

                val artworkWriteResult = writeArtworkToFile(song, coverBytes)
                if (artworkWriteResult is OperationResult.Failure) {
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
                val artworkUri = android.net.Uri.fromFile(java.io.File(coverDir, "muse_art_${song.id}.png")).toString()
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = artworkUri) }

                try {
                    songDao.updateSongArtworkUri(song.id, artworkUri)
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
                val uri = android.net.Uri.fromFile(coverFile).toString()
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = uri) }
                songDao.updateSongArtworkUri(song.id, uri)
                continue
            }
            try {
                val coverBytes = DefaultCoverGenerator.generate(song.title)
                coverFile.outputStream().use { it.write(coverBytes) }
                val uri = coverFile.toUri().toString()
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = uri) }
                songDao.updateSongArtworkUri(song.id, uri)
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
        OperationResult.Success(generated)
    }

    /**
     * Update a song's embedded album art.
     */
    override suspend fun updateSongArtwork(song: Song, artworkBytes: ByteArray): OperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val fileWriteResult = writeArtworkToFile(song, artworkBytes)
            if (fileWriteResult is OperationResult.Failure) {
                MuseLog.w(
                    "ArtworkRepository",
                    "updateSongArtwork: embedded artwork write failed, continuing with cached artwork (${fileWriteResult.error})"
                )
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
                val artworkUri = android.net.Uri.fromFile(java.io.File(coverDir, "muse_art_${song.id}.png")).toString()
                songDao.updateSongArtworkUri(song.id, artworkUri)
                songRepository.updateSongInList(song.id) { it.copy(artworkUri = artworkUri) }
            }

            try {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            } catch (e: Exception) {
                MuseLog.e("ArtworkRepository", "updateSongArtwork: MediaScanner failed", e)
            }

            when {
                cacheWritten -> OperationResult.Success(Unit)
                fileWriteResult is OperationResult.Failure -> fileWriteResult
                else -> OperationResult.Failure(OperationError.IO, "Failed to cache artwork")
            }
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: SecurityException) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "updateSongArtwork: unexpected error", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private suspend fun writeArtworkToFile(song: Song, artworkBytes: ByteArray): OperationResult<Unit> = withContext(Dispatchers.IO) {
        if (isMp4Container(song)) {
            MuseLog.w(
                "ArtworkRepository",
                "writeArtworkToFile: skipping embedded MP4/M4A artwork write; cached artwork will be used"
            )
            return@withContext OperationResult.Success(Unit)
        }

        val extension = File(song.filePath).extension.ifBlank { "mp3" }
        val suffix = "${song.id}_${System.nanoTime()}.$extension"
        val originalFile = File(context.cacheDir, "muse_art_original_$suffix")
        val editedFile = File(context.cacheDir, "muse_art_edited_$suffix")
        var fileOk = false
        try {
            val sourceResult = copyArtworkSourceToFile(song, originalFile)
            if (sourceResult is OperationResult.Failure) return@withContext sourceResult
            originalFile.copyTo(editedFile, overwrite = true, bufferSize = LARGE_FILE_BUFFER_SIZE)

            // Step 2: Apply artwork modification
            val writeResult = tagEditor.writeArtworkResult(
                filePath = editedFile.absolutePath,
                artworkBytes = artworkBytes,
                mimeType = detectArtworkMimeType(artworkBytes)
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
            if (physicalFile.exists() && physicalFile.canWrite()) {
                val physicalWrite = writePhysicalArtworkFile(physicalFile, editedFile, originalFile)
                if (physicalWrite is OperationResult.Success) {
                    fileOk = true
                    MuseLog.d("ArtworkRepository", "writeArtworkToFile: successfully wrote back artwork to physical file directly")
                }
            }

            // Attempt 3.2: Fallback to ContentResolver write
            if (!fileOk) {
                val contentWrite = writeContentArtworkFile(song, editedFile, originalFile)
                if (contentWrite is OperationResult.Failure) {
                    privilegedFileWriter?.takeIf { it.isAvailable() && song.filePath.isNotBlank() }?.let { shizuku ->
                        when (val privilegedWrite = writePrivilegedArtworkFile(song, editedFile, originalFile, shizuku)) {
                            is OperationResult.Success -> {
                                MuseLog.w("ArtworkRepository", "writeArtworkToFile: privileged artwork write verified")
                                return@withContext OperationResult.Success(Unit)
                            }
                            is OperationResult.Failure -> {
                                MuseLog.w(
                                    "ArtworkRepository",
                                    "writeArtworkToFile: privileged artwork write failed: ${privilegedWrite.message}"
                                )
                            }
                        }
                    }
                    return@withContext contentWrite
                }
                MuseLog.d("ArtworkRepository", "writeArtworkToFile: successfully wrote back artwork via ContentResolver")
            }
            OperationResult.Success(Unit)
        } catch (e: SecurityException) {
            MuseLog.e("ArtworkRepository", "writeArtworkToFile: permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "writeArtworkToFile: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "writeArtworkToFile: unexpected error", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        } finally {
            originalFile.delete()
            editedFile.delete()
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
            when {
                physical.isFile && physical.canRead() -> filesHaveSameContent(physical, expected)
                song.uri.isNotBlank() -> contentUriHasSameContent(song, expected)
                else -> false
            }
        } catch (e: Exception) {
            MuseLog.w("ArtworkRepository", "targetHasSameArtworkContent: verification read failed", e)
            false
        }
    }

    private fun writePhysicalArtworkFile(target: File, edited: File, original: File): OperationResult<Unit> {
        return try {
            edited.copyTo(target, overwrite = true, bufferSize = LARGE_FILE_BUFFER_SIZE)
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

    private fun restorePhysicalArtworkFile(target: File, original: File) {
        try {
            original.copyTo(target, overwrite = true, bufferSize = LARGE_FILE_BUFFER_SIZE)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "restorePhysicalArtworkFile: restore failed", e)
        }
    }

    private fun writeContentArtworkFile(song: Song, edited: File, original: File): OperationResult<Unit> {
        return try {
            val output = context.contentResolver.openOutputStream(song.uri.toUri(), "wt")
                ?: return OperationResult.Failure(OperationError.PERMISSION_DENIED, "Unable to open output stream")
            output.use { destination ->
                edited.inputStream().use { input -> input.copyTo(destination, LARGE_FILE_BUFFER_SIZE) }
            }
            if (contentUriHasSameContent(song, edited)) {
                OperationResult.Success(Unit)
            } else {
                restoreContentArtworkFile(song, original)
                OperationResult.Failure(OperationError.IO, "ContentResolver artwork write verification failed")
            }
        } catch (e: SecurityException) {
            MuseLog.e("ArtworkRepository", "writeContentArtworkFile: permission denied", e)
            restoreContentArtworkFile(song, original)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("ArtworkRepository", "writeContentArtworkFile: IO failed", e)
            restoreContentArtworkFile(song, original)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "writeContentArtworkFile: unexpected error", e)
            restoreContentArtworkFile(song, original)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun restoreContentArtworkFile(song: Song, original: File) {
        try {
            context.contentResolver.openOutputStream(song.uri.toUri(), "wt")?.use { destination ->
                original.inputStream().use { input -> input.copyTo(destination, LARGE_FILE_BUFFER_SIZE) }
            }
        } catch (e: Exception) {
            MuseLog.e("ArtworkRepository", "restoreContentArtworkFile: restore failed", e)
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

    private fun contentUriHasSameContent(song: Song, expected: File): Boolean {
        val actualInput = context.contentResolver.openInputStream(song.uri.toUri()) ?: return false
        actualInput.use { actual ->
            expected.inputStream().use { expectedInput ->
                return streamsHaveSameContent(actual, expectedInput)
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

    private fun isMp4Container(song: Song): Boolean {
        return song.filePath.substringAfterLast('.', "").lowercase() in MP4_CONTAINER_EXTENSIONS
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
        private const val LARGE_FILE_BUFFER_SIZE = 256 * 1024
        private val MP4_CONTAINER_EXTENSIONS = setOf("m4a", "mp4")
    }
}
