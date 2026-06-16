package luzzr.muse.data.tag

import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.core.result.isSuccess
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes audio metadata tags directly to audio files using Jaudiotagger.
 * Supports MP3 (ID3v2), FLAC (Vorbis), OGG/Opus (Vorbis), M4A/MP4 (iTunes), WAV (RIFF).
 *
 * All changes are written to the file itself ->they survive app reinstall,
 * MediaStore re-scan, and are visible to other music players.
 */
@Singleton
class TagEditor @Inject constructor() {

    data class FileMetadata(
        var title: String? = null,
        var artist: String? = null,
        var album: String? = null,
        var year: Int? = null,
        var genre: String? = null,
        var albumArtist: String? = null,
        var trackNumber: Int? = null
    )

    /**
     * Read metadata from an audio file.
     */
    fun readMetadata(filePath: String): FileMetadata? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return FileMetadata()

            FileMetadata(
                title = tag.getFirst(FieldKey.TITLE).ifBlank { null },
                artist = tag.getFirst(FieldKey.ARTIST).ifBlank { null },
                album = tag.getFirst(FieldKey.ALBUM).ifBlank { null },
                year = tag.getFirst(FieldKey.YEAR).ifBlank { null }?.take(4)?.toIntOrNull(),
                genre = tag.getFirst(FieldKey.GENRE).ifBlank { null },
                albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).ifBlank { null },
                trackNumber = tag.getFirst(FieldKey.TRACK).ifBlank { null }?.toIntOrNull()
            )
        } catch (e: Exception) {
            MuseLog.w("TagEditor", "Failed to read metadata from file", e)
            null
        }
    }

    fun canReadAudioFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.exists() && file.length() > 0L && AudioFileIO.read(file) != null
        } catch (e: Exception) {
            MuseLog.w("TagEditor", "Audio file validation failed for $filePath", e)
            false
        }
    }

    fun hasRecognizedAudioHeader(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists() || file.length() <= 0L) return false
            file.inputStream().use { input ->
                val header = ByteArray(64)
                val count = input.read(header)
                if (count <= 0) return false
                when (file.extension.lowercase()) {
                    "mp3" -> hasMp3Header(header, count)
                    "flac" -> startsWithAscii(header, count, "fLaC")
                    "ogg", "oga", "opus" -> startsWithAscii(header, count, "OggS")
                    "wav" -> hasWavHeader(header, count)
                    "m4a", "mp4" -> hasMp4Header(header, count)
                    else -> false
                }
            }
        } catch (e: Exception) {
            MuseLog.w("TagEditor", "Audio header validation failed for $filePath", e)
            false
        }
    }

    private fun hasMp3Header(header: ByteArray, count: Int): Boolean {
        if (startsWithAscii(header, count, "ID3")) return true
        return count >= 2 &&
            header[0].toInt() and 0xFF == 0xFF &&
            header[1].toInt() and 0xE0 == 0xE0
    }

    private fun hasWavHeader(header: ByteArray, count: Int): Boolean {
        return startsWithAscii(header, count, "RIFF") &&
            count >= 12 &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'A'.code.toByte() &&
            header[10] == 'V'.code.toByte() &&
            header[11] == 'E'.code.toByte()
    }

    private fun hasMp4Header(header: ByteArray, count: Int): Boolean {
        if (count < 12) return false
        for (index in 4..minOf(count - 4, 32)) {
            if (
                header[index] == 'f'.code.toByte() &&
                header[index + 1] == 't'.code.toByte() &&
                header[index + 2] == 'y'.code.toByte() &&
                header[index + 3] == 'p'.code.toByte()
            ) {
                return true
            }
        }
        return false
    }

    private fun startsWithAscii(header: ByteArray, count: Int, value: String): Boolean {
        if (count < value.length) return false
        return value.indices.all { index -> header[index] == value[index].code.toByte() }
    }

    /**
     * Write metadata to an audio file.
     * Only non-null fields are written ->null fields are left unchanged.
     *
     */
    fun writeMetadata(
        filePath: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        genre: String? = null
    ): Boolean = writeMetadataResult(
        filePath = filePath,
        title = title,
        artist = artist,
        album = album,
        year = year,
        genre = genre
    ).isSuccess

    fun writeMetadataResult(
        filePath: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        genre: String? = null
    ): OperationResult<Unit> {
        val ext = File(filePath).extension.lowercase()
        if (ext in MP4_EXTENSIONS) {
            return if (writeMp4MetadataFallback(filePath, title, artist, album, year, genre, Exception("Direct MP4 path"))) {
                OperationResult.Success(Unit)
            } else {
                OperationResult.Failure(OperationError.IO, "MP4 metadata atom write failed")
            }
        }

        return try {
            val file = File(filePath)
            MuseLog.d("TagEditor", "writeMetadata: file=$filePath exists=${file.exists()} canWrite=${file.canWrite()}")
            if (!file.exists()) {
                MuseLog.e("TagEditor", "File not found")
                return OperationResult.Failure(OperationError.NOT_FOUND, "File not found: $filePath")
            }
            if (!file.canWrite()) {
                MuseLog.e("TagEditor", "Cannot write file")
                return OperationResult.Failure(OperationError.PERMISSION_DENIED, "Cannot write file: $filePath")
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            title?.let {
                tag.setField(FieldKey.TITLE, it)
                MuseLog.d("TagEditor", "Set TITLE=$it")
            }
            artist?.let {
                tag.setField(FieldKey.ARTIST, it)
                MuseLog.d("TagEditor", "Set ARTIST=$it")
            }
            album?.let { tag.setField(FieldKey.ALBUM, it) }
            year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
            genre?.let { tag.setField(FieldKey.GENRE, it) }

            audioFile.commit()
            MuseLog.d("TagEditor", "commit successful")
            OperationResult.Success(Unit)
        } catch (e: NullPointerException) {
            val ext = File(filePath).extension.lowercase()
            if (ext in MP4_EXTENSIONS && writeMp4MetadataFallback(filePath, title, artist, album, year, genre, e)) {
                return OperationResult.Success(Unit)
            }
            MuseLog.e("TagEditor", "JAudioTagger NPE on $ext; refusing unsafe container patch", e)
            OperationResult.Failure(OperationError.IO, "$ext 文件标签结构异常或文件已损坏")
        } catch (e: SecurityException) {
            MuseLog.e("TagEditor", "writeMetadata permission denied: ${e.message}", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("TagEditor", "writeMetadata IO failed: ${e.message}", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            val ext = File(filePath).extension.lowercase()
            if (ext in MP4_EXTENSIONS && writeMp4MetadataFallback(filePath, title, artist, album, year, genre, e)) {
                return OperationResult.Success(Unit)
            }
            MuseLog.e("TagEditor", "writeMetadata failed: ${e.message}", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private fun writeMp4MetadataFallback(
        filePath: String,
        title: String?,
        artist: String?,
        album: String?,
        year: Int?,
        genre: String?,
        cause: Throwable
    ): Boolean {
        MuseLog.w("TagEditor", "writeMetadata: using MP4 atom fallback for $filePath", cause)
        return Mp4MetadataAtomWriter.writeTextMetadata(
            file = File(filePath),
            title = title,
            artist = artist,
            album = album,
            year = year,
            genre = genre
        )
    }

    /**
     * Write all metadata fields to an audio file at once.
     */
    fun writeAllMetadata(filePath: String, metadata: FileMetadata): Boolean = writeAllMetadataResult(filePath, metadata).isSuccess

    fun writeAllMetadataResult(filePath: String, metadata: FileMetadata): OperationResult<Unit> {
        return writeMetadataResult(
            filePath = filePath,
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            year = metadata.year,
            genre = metadata.genre
        )
    }

    /**
     * Read embedded artwork from an audio file.
     * @return ByteArray of the artwork image, or null if no artwork or error
     */
    fun readArtwork(filePath: String): ByteArray? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return null
            val artwork = tag.getFirstArtwork() ?: return null
            artwork.binaryData
        } catch (e: Exception) {
            MuseLog.e("TagEditor", "readArtwork failed: ${e.message}", e)
            null
        }
    }

    /**
     * Read embedded artwork MIME type from an audio file.
     */
    fun readArtworkMime(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return null
            tag.getFirstArtwork()?.mimeType
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Write artwork (cover image) to an audio file.
     * @param filePath path to the audio file
     * @param artworkBytes raw image bytes (PNG/JPEG)
     * @param mimeType MIME type ("image/jpeg" or "image/png")
     * @return true if successful
     */
    fun writeArtwork(filePath: String, artworkBytes: ByteArray, mimeType: String = "image/jpeg"): Boolean =
        writeArtworkResult(filePath, artworkBytes, mimeType).isSuccess

    fun writeArtworkResult(filePath: String, artworkBytes: ByteArray, mimeType: String = "image/jpeg"): OperationResult<Unit> {
        return try {
            val file = File(filePath)
            if (!file.exists()) return OperationResult.Failure(OperationError.NOT_FOUND, "File not found: $filePath")
            if (!file.canWrite()) return OperationResult.Failure(OperationError.PERMISSION_DENIED, "Cannot write file: $filePath")
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            val artwork = ArtworkFactory.getNew()
            artwork.binaryData = artworkBytes
            artwork.mimeType = mimeType
            tag.setField(artwork)
            audioFile.commit()
            MuseLog.d("TagEditor", "writeArtwork successful, size=${artworkBytes.size}")
            OperationResult.Success(Unit)
        } catch (e: NullPointerException) {
            val ext = File(filePath).extension.lowercase()
            MuseLog.e("TagEditor", "writeArtwork NPE on $ext; refusing unsafe container patch", e)
            OperationResult.Failure(OperationError.IO, "$ext 文件标签结构异常或文件已损坏")
        } catch (e: NoClassDefFoundError) {
            // javax.imageio.ImageIO is not available on Android
            // This affects Vorbis Comment tags (OGG/Opus/FLAC) which need ImageIO to decode artwork
            MuseLog.e("TagEditor", "writeArtwork failed: Android lacks javax.imageio (${e.message})")
            OperationResult.Failure(OperationError.UNSUPPORTED_FILE, e.message)
        } catch (e: SecurityException) {
            MuseLog.e("TagEditor", "writeArtwork permission denied: ${e.message}", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("TagEditor", "writeArtwork IO failed: ${e.message}", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            MuseLog.e("TagEditor", "writeArtwork failed: ${e.message}", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    /**
     * Remove embedded artwork from an audio file.
     */
    fun deleteArtwork(filePath: String): Boolean = deleteArtworkResult(filePath).isSuccess

    fun deleteArtworkResult(filePath: String): OperationResult<Unit> {
        return try {
            val file = File(filePath)
            if (!file.exists()) return OperationResult.Failure(OperationError.NOT_FOUND, "File not found: $filePath")
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.deleteArtworkField()
            audioFile.commit()
            OperationResult.Success(Unit)
        } catch (e: SecurityException) {
            MuseLog.e("TagEditor", "deleteArtwork permission denied: ${e.message}", e)
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: IOException) {
            MuseLog.e("TagEditor", "deleteArtwork IO failed: ${e.message}", e)
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            MuseLog.e("TagEditor", "deleteArtwork failed: ${e.message}", e)
            OperationResult.Failure(OperationError.UNKNOWN, e.message)
        }
    }

    private companion object {
        val MP4_EXTENSIONS = setOf("m4a", "mp4")
    }
}
