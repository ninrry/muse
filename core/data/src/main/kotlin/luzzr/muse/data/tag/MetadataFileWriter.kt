package luzzr.muse.data.tag

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteException
import android.media.MediaScannerConnection
import android.provider.MediaStore
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.database.SongDao
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Song
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tagEditor: TagEditor
) {
    @Suppress("ReturnCount")
    private suspend fun safeModifyAudioFile(
        song: Song,
        modifier: suspend (File) -> OperationResult<Unit>,
        afterFileWrite: suspend () -> OperationResult<Unit> = { OperationResult.Success(Unit) }
    ): OperationResult<Unit> {
        if (hasUnsupportedExtension(song)) {
            return OperationResult.Failure(
                OperationError.UNSUPPORTED_FILE,
                "Unsupported audio file type: ${File(song.filePath).extension}"
            )
        }

        val extension = File(song.filePath).extension.ifBlank { "mp3" }
        val suffix = "${song.id}_${System.nanoTime()}.$extension"
        val originalFile = File(context.cacheDir, "muse_original_$suffix")
        val editedFile = File(context.cacheDir, "muse_edited_$suffix")
        try {
            val copyResult = copySongToFile(song, originalFile)
            if (copyResult is OperationResult.Failure) {
                return copyResult
            }
            originalFile.copyTo(editedFile, overwrite = true)
            val modifierResult = modifier(editedFile)
            if (modifierResult is OperationResult.Failure) {
                MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: modifier failed to write tags to temp file, fallback to database-only update")
                val dbResult = afterFileWrite()
                return when (dbResult) {
                    is OperationResult.Success -> OperationResult.Success(Unit)
                    is OperationResult.Failure -> dbResult
                }
            }

            val physicalFile = File(song.filePath)
            if (physicalFile.exists() && physicalFile.canWrite()) {
                val physicalWrite = writePhysicalFile(physicalFile, editedFile, originalFile)
                if (physicalWrite is OperationResult.Success) {
                    return commitOrRollback(
                        song = song,
                        original = originalFile,
                        physicalTarget = physicalFile,
                        afterFileWrite = afterFileWrite
                    )
                }
            }

            val contentWrite = writeContentUri(song, editedFile, originalFile)
            if (contentWrite is OperationResult.Failure) {
                MuseLog.w("MetadataFileWriter", "safeModifyAudioFile: ContentResolver write failed, fallback to database-only update: ${contentWrite.message}")
                val dbResult = afterFileWrite()
                return when (dbResult) {
                    is OperationResult.Success -> OperationResult.Success(Unit)
                    is OperationResult.Failure -> dbResult
                }
            }

            return commitOrRollback(
                song = song,
                original = originalFile,
                physicalTarget = null,
                afterFileWrite = afterFileWrite
            )
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: permission denied", e)
            return OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: IO error", e)
            return OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Throwable) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: unexpected error/LinkageError, fallback to database-only", e)
            return try {
                val dbResult = afterFileWrite()
                when (dbResult) {
                    is OperationResult.Success -> OperationResult.Success(Unit)
                    is OperationResult.Failure -> dbResult
                }
            } catch (ex: Exception) {
                OperationResult.Failure(OperationError.UNKNOWN, e.message ?: ex.message)
            }
        } finally {
            originalFile.delete()
            editedFile.delete()
        }
    }

    private fun hasUnsupportedExtension(song: Song): Boolean {
        val extension = File(song.filePath).extension.lowercase()
        return extension.isNotBlank() && extension !in SUPPORTED_AUDIO_EXTENSIONS
    }

    private fun copySongToFile(song: Song, destination: File): OperationResult<Unit> {
        val contentResult = copyContentUriToFile(song, destination)
        if (contentResult is OperationResult.Success) return contentResult

        val physicalResult = copyPhysicalSongToFile(song, destination)
        if (physicalResult is OperationResult.Success) return physicalResult

        return when (contentResult) {
            is OperationResult.Failure -> if (contentResult.error == OperationError.PERMISSION_DENIED) {
                contentResult
            } else {
                physicalResult
            }
            is OperationResult.Success -> contentResult
        }
    }

    private fun copyContentUriToFile(song: Song, destination: File): OperationResult<Unit> {
        return try {
            val input = context.contentResolver.openInputStream(song.uri.toUri())
                ?: return OperationResult.Failure(OperationError.IO, "Unable to open source audio URI")
            input.use { source ->
                destination.outputStream().use { output -> source.copyTo(output) }
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
                destination.outputStream().use { output -> input.copyTo(output) }
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

    private fun writePhysicalFile(target: File, edited: File, original: File): OperationResult<Unit> {
        return try {
            edited.copyTo(target, overwrite = true)
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

    private fun restorePhysicalFile(target: File, original: File) {
        try {
            original.copyTo(target, overwrite = true)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: physical restore failed", e)
        }
    }

    private fun writeContentUri(song: Song, edited: File, original: File): OperationResult<Unit> {
        return try {
            val output = context.contentResolver.openOutputStream(song.uri.toUri(), "wt")
                ?: return OperationResult.Failure(OperationError.PERMISSION_DENIED, "Unable to open output stream")
            output.use { destination ->
                edited.inputStream().use { source -> source.copyTo(destination) }
            }
            if (contentUriHasSameContent(song, edited)) {
                MuseLog.d("MetadataFileWriter", "safeModifyAudioFile: verified ContentResolver write")
                OperationResult.Success(Unit)
            } else {
                restoreContentUri(song, original)
                OperationResult.Failure(OperationError.IO, "ContentResolver write verification failed")
            }
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: ContentResolver permission denied", e)
            restoreContentUri(song, original)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: ContentResolver IO failed", e)
            restoreContentUri(song, original)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: ContentResolver write failed", e)
            restoreContentUri(song, original)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private suspend fun commitOrRollback(
        song: Song,
        original: File,
        physicalTarget: File?,
        afterFileWrite: suspend () -> OperationResult<Unit>
    ): OperationResult<Unit> {
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

        if (commitResult is OperationResult.Success) return commitResult

        if (physicalTarget != null) {
            restorePhysicalFile(physicalTarget, original)
        } else {
            restoreContentUri(song, original)
        }
        return commitResult
    }

    private fun restoreContentUri(song: Song, original: File) {
        try {
            context.contentResolver.openOutputStream(song.uri.toUri(), "wt")?.use { destination ->
                original.inputStream().use { source -> source.copyTo(destination) }
            }
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "safeModifyAudioFile: ContentResolver restore failed", e)
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
        val actualBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val expectedBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
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

    suspend fun renameSong(song: Song, newTitle: String, songDao: SongDao): OperationResult<Unit> {
        return safeModifyAudioFile(
            song = song,
            modifier = { tempFile ->
                tagEditor.writeMetadataResult(filePath = tempFile.absolutePath, title = newTitle)
            },
            afterFileWrite = {
                songDao.updateSongMeta(song.id, newTitle, song.uri, song.filePath)
                updateMediaStore(song, title = newTitle, artist = null, album = null)
                scanFile(song)
                OperationResult.Success(Unit)
            }
        )
    }

    suspend fun updateSongTags(
        song: Song,
        title: String,
        artist: String,
        album: String,
        year: Int?,
        genre: String,
        songDao: SongDao
    ): OperationResult<Unit> {
        return safeModifyAudioFile(
            song = song,
            modifier = { tempFile ->
                tagEditor.writeMetadataResult(
                    filePath = tempFile.absolutePath,
                    title = title,
                    artist = artist,
                    album = album,
                    year = year,
                    genre = genre
                )
            },
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
                updateMediaStore(song, title = title, artist = artist, album = album)
                scanFile(song)
                OperationResult.Success(Unit)
            }
        )
    }

    suspend fun updateSongWithMetadata(song: Song, result: MetadataResult, songDao: SongDao): OperationResult<Song> {
        val updated = song.copy(
            title = result.title,
            artist = result.artist,
            album = result.album,
            year = result.year ?: song.year,
            genre = result.genre.ifBlank { song.genre },
            artworkUri = result.coverUrl ?: song.artworkUri
        )
        val writeResult = safeModifyAudioFile(
            song = song,
            modifier = { tempFile ->
                tagEditor.writeMetadataResult(
                    filePath = tempFile.absolutePath,
                    title = result.title.takeIf { it.isNotBlank() },
                    artist = result.artist.takeIf { it.isNotBlank() },
                    album = result.album.takeIf { it.isNotBlank() },
                    year = result.year,
                    genre = result.genre.takeIf { it.isNotBlank() }
                )
            },
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
                updateMediaStore(song, title = updated.title, artist = updated.artist, album = updated.album)
                scanFile(song)
                OperationResult.Success(Unit)
            }
        )

        return when (writeResult) {
            is OperationResult.Success -> OperationResult.Success(updated)
            is OperationResult.Failure -> writeResult
        }
    }

    private fun updateMediaStore(song: Song, title: String?, artist: String?, album: String?) {
        try {
            val values = ContentValues().apply {
                title?.let { put(MediaStore.Audio.Media.TITLE, it) }
                artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                album?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            }
            if (values.size() > 0) {
                context.contentResolver.update(song.uri.toUri(), values, null, null)
            }
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "updateMediaStore: permission denied", e)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "updateMediaStore: update failed", e)
        }
    }

    private fun scanFile(song: Song) {
        try {
            MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "scanFile: MediaScanner failed", e)
        }
    }

    private companion object {
        val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "mp4", "wav")
    }
}
