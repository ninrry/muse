package luzzr.muse.data.tag

import android.content.Context
import android.database.sqlite.SQLiteException
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.audio.AudioFileSupport
import luzzr.muse.data.database.SongDao
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Song
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class MetadataFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tagEditor: TagEditor
) {
    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    private suspend fun safeModifyAudioFile(
        song: Song,
        modifier: suspend (File) -> OperationResult<Unit>,
        prepareEditedFile: (suspend (File, File) -> OperationResult<Unit>)? = null,
        afterFileWrite: suspend () -> OperationResult<Unit> = { OperationResult.Success(Unit) }
    ): OperationResult<Unit> {
        MuseLog.d(
            "MetadataFileWriter",
            "safeModifyAudioFile: id=${song.id} file=${if (song.filePath.isNotBlank()) java.io.File(song.filePath).name else "?"} " +
                "size=${song.size}"
        )

        if (hasUnsupportedExtension(song)) {
            val ext = fileExtension(song.filePath)
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: unsupported extension '$ext' for ${song.filePath}")
            return OperationResult.Failure(
                OperationError.UNSUPPORTED_FILE,
                "Unsupported audio file type: $ext"
            )
        }

        if (song.filePath.isBlank()) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: empty file path")
            return OperationResult.Failure(OperationError.NOT_FOUND, "Empty file path")
        }

        val extension = fileExtension(song.filePath).ifBlank { "mp3" }
        val suffix = "${song.id}_${System.nanoTime()}.$extension"
        val originalFile = File(context.cacheDir, "muse_original_$suffix")
        val editedFile = File(context.cacheDir, "muse_edited_$suffix")
        val startedAt = System.currentTimeMillis()
        try {
            val copyStartedAt = System.currentTimeMillis()
            val copyResult = copySongToFile(song, originalFile)
            if (copyResult is OperationResult.Failure) {
                MuseLog.e(
                    "MetadataFileWriter",
                    "safeModifyAudioFile: failed to copy source to temp: ${copyResult.error} ${copyResult.message}"
                )
                return copyResult
            }
            MuseLog.d(
                "MetadataFileWriter",
                "safeModifyAudioFile: copied source bytes=${originalFile.length()} ms=${System.currentTimeMillis() - copyStartedAt}"
            )
            val prepareStartedAt = System.currentTimeMillis()
            val prepareResult = prepareEditedFile?.invoke(originalFile, editedFile)
                ?: copyOriginalToEdited(originalFile, editedFile)
            if (prepareResult is OperationResult.Failure) {
                MuseLog.e(
                    "MetadataFileWriter",
                    "safeModifyAudioFile: failed to prepare edited temp file: ${prepareResult.error} ${prepareResult.message}"
                )
                return prepareResult
            }
            MuseLog.d(
                "MetadataFileWriter",
                "safeModifyAudioFile: prepared edited bytes=${editedFile.length()} ms=${System.currentTimeMillis() - prepareStartedAt}"
            )
            val modifierStartedAt = System.currentTimeMillis()
            val modifierResult = modifier(editedFile)
            if (modifierResult is OperationResult.Failure) {
                MuseLog.e(
                    "MetadataFileWriter",
                    "safeModifyAudioFile: modifier failed to write tags to temp file: ${modifierResult.error}"
                )
                return modifierResult
            }
            MuseLog.d(
                "MetadataFileWriter",
                "safeModifyAudioFile: modifier completed ms=${System.currentTimeMillis() - modifierStartedAt}"
            )
            val validationStartedAt = System.currentTimeMillis()
            when (val validationResult = verifyEditedAudioFile(originalFile, editedFile)) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> return validationResult
            }
            MuseLog.d(
                "MetadataFileWriter",
                "safeModifyAudioFile: validation completed ms=${System.currentTimeMillis() - validationStartedAt}"
            )

            val physicalFile = File(song.filePath)
            MuseLog.d(
                "MetadataFileWriter",
                "safeModifyAudioFile: physical exists=${physicalFile.exists()} canWrite=${physicalFile.canWrite()}"
            )

            var lastResult: OperationResult<Unit>? = null

            if (physicalFile.exists() && physicalFile.canWrite()) {
                val physicalStartedAt = System.currentTimeMillis()
                when (val physicalWrite = writePhysicalFileWithTimeout(physicalFile, editedFile, originalFile)) {
                    is OperationResult.Success -> {
                        MuseLog.d(
                            "MetadataFileWriter",
                            "safeModifyAudioFile: physical write succeeded ms=${System.currentTimeMillis() - physicalStartedAt}"
                        )
                        return commitOrRollback(
                            song = song,
                            original = originalFile,
                            physicalTarget = physicalFile,
                            afterFileWrite = afterFileWrite
                        )
                    }
                    is OperationResult.Failure -> {
                        lastResult = physicalWrite
                        MuseLog.d(
                            "MetadataFileWriter",
                            "safeModifyAudioFile: physical write failed, falling back: ${physicalWrite.message}"
                        )
                    }
                }
            }

            return lastResult ?: when {
                !physicalFile.exists() -> OperationResult.Failure(
                    OperationError.NOT_FOUND,
                    "Physical audio file does not exist"
                )
                !physicalFile.canWrite() -> OperationResult.Failure(
                    OperationError.PERMISSION_DENIED,
                    "Audio file is not writable; grant full file access"
                )
                else -> OperationResult.Failure(OperationError.IO, "Physical write failed")
            }
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: permission denied", e)
            return OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: IO error", e)
            return OperationResult.Failure(OperationError.IO, e.message)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: unexpected error/LinkageError", e)
            return OperationResult.Failure(OperationError.UNKNOWN, e.message)
        } finally {
            MuseLog.d(
                "MetadataFileWriter",
                "safeModifyAudioFile: finished totalMs=${System.currentTimeMillis() - startedAt}"
            )
            originalFile.delete()
            editedFile.delete()
        }
    }

    private fun hasUnsupportedExtension(song: Song): Boolean {
        val extension = fileExtension(song.filePath)
        return extension.isBlank() || extension !in SUPPORTED_AUDIO_EXTENSIONS
    }

    private fun fileExtension(path: String): String {
        return path.substringAfterLast('.', "").lowercase()
    }

    private fun copyOriginalToEdited(originalFile: File, editedFile: File): OperationResult<Unit> {
        return try {
            originalFile.copyTo(editedFile, overwrite = true, bufferSize = LARGE_FILE_BUFFER_SIZE)
            OperationResult.Success(Unit)
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "copyOriginalToEdited: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "copyOriginalToEdited: unexpected error", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun copySongToFile(song: Song, destination: File): OperationResult<Unit> {
        val physicalResult = copyPhysicalSongToFile(song, destination)
        if (physicalResult is OperationResult.Success) return physicalResult

        val contentResult = copyContentUriToFile(song, destination)
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

    private fun copyContentUriToFile(song: Song, destination: File): OperationResult<Unit> {
        return try {
            val input = context.contentResolver.openInputStream(song.uri.toUri())
                ?: return OperationResult.Failure(OperationError.IO, "Unable to open source audio URI")
            input.use { source ->
                destination.outputStream().use { output -> source.copyTo(output, LARGE_FILE_BUFFER_SIZE) }
            }
            verifyCopiedSource(destination)
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "copyContentUriToFile: permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "copyContentUriToFile: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "copyContentUriToFile: unexpected error", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun copyPhysicalSongToFile(song: Song, destination: File): OperationResult<Unit> {
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
            verifyCopiedSource(destination)
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "copyPhysicalSongToFile: permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "copyPhysicalSongToFile: IO error", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "copyPhysicalSongToFile: unexpected error", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun verifyCopiedSource(destination: File): OperationResult<Unit> {
        return if (destination.isFile && destination.length() > 0L) {
            OperationResult.Success(Unit)
        } else {
            OperationResult.Failure(OperationError.IO, "Copied source audio file was empty")
        }
    }

    private fun verifyEditedAudioFile(originalFile: File, editedFile: File): OperationResult<Unit> {
        if (!editedFile.isFile || editedFile.length() <= 0L) {
            return OperationResult.Failure(OperationError.IO, "Edited audio file was empty")
        }

        // 快速检查：文件大小是否合理
        if (originalFile.length() > MIN_SIZE_FOR_TRUNCATION_CHECK &&
            editedFile.length() < originalFile.length() / MAX_SAFE_SHRINK_RATIO
        ) {
            return OperationResult.Failure(OperationError.IO, "Edited audio file looks truncated")
        }

        // Metadata updates are expected to change bytes. Validate structural
        // readability instead of requiring the edited file to match the source.
        if (tagEditor.canReadAudioFile(editedFile.absolutePath)) {
            return OperationResult.Success(Unit)
        }

        if (tagEditor.hasRecognizedAudioHeader(editedFile.absolutePath)) {
            MuseLog.w(
                "MetadataFileWriter",
                "verifyEditedAudioFile: tag library could not read edited file, but audio header is valid"
            )
            return OperationResult.Success(Unit)
        }

        return OperationResult.Failure(OperationError.IO, "Edited audio file failed validation")
    }

    private fun writePhysicalFile(target: File, edited: File, original: File): OperationResult<Unit> {
        return try {
            edited.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, LARGE_FILE_BUFFER_SIZE) }
            }
            if (filesHaveSameContent(target, edited)) {
                MuseLog.d("MetadataFileWriter", "safeModifyAudioFile: verified physical write")
                OperationResult.Success(Unit)
            } else {
                restorePhysicalFile(target, original)
                OperationResult.Failure(OperationError.IO, "Physical write verification failed")
            }
        } catch (e: SecurityException) {
            MuseLog.w("MetadataFileWriter", "safeModifyAudioFile: physical write permission denied", e)
            restorePhysicalFile(target, original)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: Exception) {
            MuseLog.w("MetadataFileWriter", "safeModifyAudioFile: physical write failed", e)
            restorePhysicalFile(target, original)
            OperationResult.Failure(OperationError.IO, e.message)
        }
    }

    private suspend fun writePhysicalFileWithTimeout(target: File, edited: File, original: File): OperationResult<Unit> {
        val timeoutMs = fileWriteTimeoutMs(edited.length())
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(timeoutMs) {
                    runInterruptible {
                        writePhysicalFile(target, edited, original)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                MuseLog.e("MetadataFileWriter", "writePhysicalFileWithTimeout: timed out after ${timeoutMs}ms", e)
                restorePhysicalFile(target, original)
                OperationResult.Failure(OperationError.IO, "Physical write timed out")
            } catch (e: InterruptedException) {
                MuseLog.e("MetadataFileWriter", "writePhysicalFileWithTimeout: interrupted", e)
                restorePhysicalFile(target, original)
                OperationResult.Failure(OperationError.IO, e.message)
            }
        }
    }

    private fun restorePhysicalFile(target: File, original: File) {
        try {
            original.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, LARGE_FILE_BUFFER_SIZE) }
            }
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: physical restore failed", e)
        }
    }



    private suspend fun commitOrRollback(
        song: Song,
        original: File,
        physicalTarget: File?,
        afterFileWrite: suspend () -> OperationResult<Unit>
    ): OperationResult<Unit> {
        val commitStartedAt = System.currentTimeMillis()
        val commitResult = try {
            afterFileWrite()
        } catch (e: SQLiteException) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: database commit failed", e)
            OperationResult.Failure(OperationError.DATABASE, e.message)
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: commit permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: commit IO failed", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: commit failed", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }

        if (commitResult is OperationResult.Success) {
            MuseLog.w(
                "MetadataFileWriter",
                "safeModifyAudioFile: commit succeeded ms=${System.currentTimeMillis() - commitStartedAt}"
            )
            return commitResult
        }

        if (physicalTarget != null && physicalTarget.exists()) {
            restorePhysicalFile(physicalTarget, original)
        }
        return commitResult
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

    private fun isMp4Container(song: Song): Boolean {
        return fileExtension(song.filePath) in MP4_CONTAINER_EXTENSIONS
    }

    private fun textMetadataModifier(
        song: Song,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        genre: String? = null
    ): suspend (File) -> OperationResult<Unit> {
        if (isMp4Container(song)) {
            return { OperationResult.Success(Unit) }
        }
        return { tempFile ->
            tagEditor.writeMetadataResult(
                filePath = tempFile.absolutePath,
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre
            )
        }
    }

    private fun mp4TextMetadataPreparer(
        song: Song,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        genre: String? = null
    ): (suspend (File, File) -> OperationResult<Unit>)? {
        if (!isMp4Container(song)) return null
        return { originalFile, editedFile ->
            val wrote = Mp4MetadataAtomWriter.writeTextMetadata(
                sourceFile = originalFile,
                targetFile = editedFile,
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre
            )
            if (wrote) {
                OperationResult.Success(Unit)
            } else {
                OperationResult.Failure(OperationError.IO, "MP4 metadata atom write failed")
            }
        }
    }

    suspend fun renameSong(song: Song, newTitle: String, songDao: SongDao): OperationResult<Song> {
        val writeResult = safeModifyAudioFile(
            song = song,
            modifier = textMetadataModifier(song, title = newTitle),
            prepareEditedFile = mp4TextMetadataPreparer(song, title = newTitle),
            afterFileWrite = {
                songDao.updateSongMeta(song.id, newTitle, song.uri, song.filePath)
                scanFile(song)
                OperationResult.Success(Unit)
            }
        )

        if (writeResult is OperationResult.Failure) return writeResult
        return renameWrittenSong(song.copy(title = newTitle), newTitle, songDao, requireRename = true)
    }

    suspend fun updateSongTags(
        song: Song,
        title: String,
        artist: String,
        album: String,
        year: Int?,
        genre: String,
        songDao: SongDao
    ): OperationResult<Song> {
        val updated = song.copy(title = title, artist = artist, album = album, year = year, genre = genre)
        val writeResult = safeModifyAudioFile(
            song = song,
            modifier = textMetadataModifier(
                song = song,
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre
            ),
            prepareEditedFile = mp4TextMetadataPreparer(
                song = song,
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre
            ),
            afterFileWrite = {
                songDao.updateSongMetadata(
                    id = song.id,
                    title = title,
                    artist = artist,
                    album = album,
                    year = year,
                    genre = genre,
                    artworkUri = song.artworkUri
                )
                scanFile(song)
                OperationResult.Success(Unit)
            }
        )

        if (writeResult is OperationResult.Failure) return writeResult
        return renameWrittenSong(updated, title, songDao, requireRename = false)
    }

    suspend fun updateSongWithMetadata(song: Song, result: MetadataResult, songDao: SongDao): OperationResult<Song> {
        val updated = song.copy(
            title = result.title,
            artist = result.artist.ifBlank { song.artist },
            album = result.album.ifBlank { song.album },
            year = result.year ?: song.year,
            genre = result.genre.ifBlank { song.genre },
            artworkUri = song.artworkUri
        )
        val writeResult = safeModifyAudioFile(
            song = song,
            modifier = textMetadataModifier(
                song = song,
                title = result.title.takeIf { it.isNotBlank() },
                artist = result.artist.takeIf { it.isNotBlank() },
                album = result.album.takeIf { it.isNotBlank() },
                year = result.year,
                genre = result.genre.takeIf { it.isNotBlank() }
            ),
            prepareEditedFile = mp4TextMetadataPreparer(
                song = song,
                title = result.title.takeIf { it.isNotBlank() },
                artist = result.artist.takeIf { it.isNotBlank() },
                album = result.album.takeIf { it.isNotBlank() },
                year = result.year,
                genre = result.genre.takeIf { it.isNotBlank() }
            ),
            afterFileWrite = {
                songDao.updateSongMetadata(
                    id = song.id,
                    title = updated.title,
                    artist = updated.artist,
                    album = updated.album,
                    year = updated.year,
                    genre = updated.genre,
                    artworkUri = updated.artworkUri
                )
                scanFile(song)
                OperationResult.Success(Unit)
            }
        )

        return when (writeResult) {
            is OperationResult.Success -> renameWrittenSong(updated, updated.title, songDao, requireRename = false)
            is OperationResult.Failure -> writeResult
        }
    }

    private suspend fun renameWrittenSong(
        song: Song,
        title: String,
        songDao: SongDao,
        requireRename: Boolean
    ): OperationResult<Song> {
        return when (val renameResult = renameFileToTitle(song, title)) {
            is OperationResult.Success -> {
                val renamed = renameResult.value
                try {
                    if (renamed.filePath != song.filePath || renamed.uri != song.uri) {
                        songDao.updateSongMeta(renamed.id, renamed.title, renamed.uri, renamed.filePath)
                    }
                } catch (e: SQLiteException) {
                    MuseLog.e("MetadataFileWriter", "renameWrittenSong: database update failed", e)
                    return OperationResult.Failure(OperationError.DATABASE, e.message)
                } catch (e: Exception) {
                    MuseLog.e("MetadataFileWriter", "renameWrittenSong: database update failed", e)
                    return OperationResult.Failure(OperationError.UNKNOWN, e.message)
                }
                OperationResult.Success(renamed)
            }
            is OperationResult.Failure -> {
                if (requireRename) {
                    renameResult
                } else {
                    MuseLog.w(
                        "MetadataFileWriter",
                        "renameWrittenSong: metadata was written but filename rename failed: ${renameResult.message}"
                    )
                    OperationResult.Success(song)
                }
            }
        }
    }

    private suspend fun renameFileToTitle(song: Song, title: String): OperationResult<Song> {
        val oldFile = File(song.filePath)
        val safeTitle = sanitizeFileName(title)
        if (safeTitle.isBlank()) return OperationResult.Success(song)

        val extension = oldFile.extension.ifBlank { fileExtension(song.filePath) }
        val newName = if (extension.isBlank()) safeTitle else "$safeTitle.$extension"
        if (oldFile.name.equals(newName, ignoreCase = true)) return OperationResult.Success(song)

        if (!oldFile.exists()) {
            return OperationResult.Failure(OperationError.NOT_FOUND, "Physical file does not exist")
        }

        val parent = oldFile.parentFile ?: return OperationResult.Success(song)
        val target = resolveRenameConflict(parent, newName, oldFile)

        return try {
            if (oldFile.renameTo(target)) {
                val renamed = song.copy(
                    filePath = target.absolutePath,
                    uri = Uri.fromFile(target).toString()
                )
                scanPaths(oldFile.absolutePath, target.absolutePath)
                OperationResult.Success(renamed)
            } else {
                OperationResult.Failure(OperationError.IO, "Physical renameTo failed")
            }
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "renameFileToTitle: permission denied", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "renameFileToTitle: IO failed", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "renameFileToTitle: failed", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun sanitizeFileName(value: String): String {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(MAX_FILENAME_BASE_LENGTH)
    }

    private fun resolveRenameConflict(parent: File, requestedName: String, currentFile: File): File {
        val base = requestedName.substringBeforeLast('.', requestedName)
        val ext = requestedName.substringAfterLast('.', "").takeIf { requestedName.contains('.') }?.let { ".$it" }.orEmpty()
        var candidate = File(parent, "$base$ext")
        var index = 1
        while (candidate.exists() && candidate.absolutePath != currentFile.absolutePath) {
            candidate = File(parent, "$base ($index)$ext")
            index++
        }
        return candidate
    }



    private fun scanFile(song: Song) {
        scanPaths(song.filePath)
    }

    /**
     * Bake corrected (offset-applied) synchronized lyrics into the audio file's
     * LYRICS tag so the correction survives reinstalls and is visible to other players.
     */
    suspend fun writeLyrics(song: Song, lrc: String): OperationResult<Unit> =
        safeModifyAudioFile(
            song = song,
            modifier = lyricsModifier(song, lrc),
            afterFileWrite = { OperationResult.Success(Unit) }
        )

    /**
     * Remove the baked lyrics from the audio file (used when a correction is reset).
     */
    suspend fun clearLyrics(song: Song): OperationResult<Unit> =
        safeModifyAudioFile(
            song = song,
            modifier = { tempFile -> tagEditor.deleteLyricsResult(tempFile.absolutePath) },
            afterFileWrite = { OperationResult.Success(Unit) }
        )

    private fun lyricsModifier(song: Song, lrc: String): suspend (File) -> OperationResult<Unit> {
        return { tempFile -> tagEditor.writeLyricsResult(tempFile.absolutePath, lrc) }
    }

    private fun scanPaths(vararg paths: String) {
        try {
            MediaScannerConnection.scanFile(context, paths.filter { it.isNotBlank() }.distinct().toTypedArray(), null, null)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "scanFile: MediaScanner failed", e)
        }
    }

    private companion object {
        val SUPPORTED_AUDIO_EXTENSIONS = AudioFileSupport.supportedAudioExtensions
        val MP4_CONTAINER_EXTENSIONS = AudioFileSupport.mp4AudioContainerExtensions
        const val LARGE_FILE_BUFFER_SIZE = 4 * 1024 * 1024  // 4MB - optimized buffer
        const val MAX_FILENAME_BASE_LENGTH = 120
        const val MIN_SIZE_FOR_TRUNCATION_CHECK = 4096L
        const val MAX_SAFE_SHRINK_RATIO = 4
        const val BASE_FILE_WRITE_TIMEOUT_MS = 60_000L
        const val MAX_FILE_WRITE_TIMEOUT_EXTRA_MS = 90_000L
        const val FILE_WRITE_TIMEOUT_BYTES_PER_MS = 8_192L
    }

    private fun fileWriteTimeoutMs(fileSizeBytes: Long): Long {
        val sizeBasedAllowance = (fileSizeBytes / FILE_WRITE_TIMEOUT_BYTES_PER_MS).coerceAtMost(MAX_FILE_WRITE_TIMEOUT_EXTRA_MS)
        return BASE_FILE_WRITE_TIMEOUT_MS + sizeBasedAllowance
    }
}
