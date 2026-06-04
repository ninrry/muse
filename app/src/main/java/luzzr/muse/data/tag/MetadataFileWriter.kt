package luzzr.muse.data.tag

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.error.MetadataException
import luzzr.muse.data.model.Song
import luzzr.muse.data.network.MetadataResult
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tagEditor: TagEditor
) {
    @Suppress("ReturnCount")
    private suspend fun modifyAudioFileViaContentResolver(
        song: Song,
        modifier: suspend (File) -> Boolean
    ): Boolean {
        var tempFile: File? = null
        var result = false
        try {
            tempFile = File(context.cacheDir, "muse_edit_${song.id}_${System.nanoTime()}")
            context.contentResolver.openInputStream(song.uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return false

            if (!modifier(tempFile)) return false

            context.contentResolver.openOutputStream(song.uri, "wt")?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            } ?: return false

            result = true
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "modifyAudioFileViaContentResolver: IO error", e)
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "modifyAudioFileViaContentResolver: permission denied", e)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "modifyAudioFileViaContentResolver: unexpected error", e)
        } finally {
            tempFile?.delete()
        }
        return result
    }

    suspend fun renameSong(song: Song, newTitle: String, songDao: SongDao): Boolean {
        try {
            // Step 1: Try direct file path write
            var fileOk = false
            try {
                fileOk = tagEditor.writeMetadata(filePath = song.filePath, title = newTitle)
                MuseLog.d("MetadataFileWriter", "renameSong: direct write $fileOk")
            } catch (e: IOException) {
                MuseLog.e("MetadataFileWriter", "renameSong: direct IO error", e)
            } catch (e: Exception) {
                MuseLog.e("MetadataFileWriter", "renameSong: direct write failed", e)
            }

            // Step 2: Fall back to ContentResolver if direct write failed
            if (!fileOk) {
                try {
                    fileOk = modifyAudioFileViaContentResolver(song) { tempFile ->
                        tagEditor.writeMetadata(filePath = tempFile.absolutePath, title = newTitle)
                    }
                    MuseLog.d("MetadataFileWriter", "renameSong: CR write $fileOk")
                } catch (e: IOException) {
                    MuseLog.e("MetadataFileWriter", "renameSong: CR IO error", e)
                } catch (e: Exception) {
                    MuseLog.e("MetadataFileWriter", "renameSong: CR write failed", e)
                }
            }

            if (!fileOk) {
                MuseLog.e("MetadataFileWriter", "renameSong: All write attempts failed. Aborting.")
                return false
            }

            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, newTitle)
                }
                context.contentResolver.update(song.uri, values, null, null)
            } catch (e: Exception) {
                MuseLog.e("MetadataFileWriter", "renameSong: MediaStore update failed", e)
            }
            try {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            } catch (e: Exception) {
                MuseLog.e("MetadataFileWriter", "renameSong: MediaScanner failed", e)
            }

            songDao.updateSongMeta(song.id, newTitle, song.uri.toString(), song.filePath)
            return true
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "renameSong: IO error", e)
            return false
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "renameSong: permission denied", e)
            return false
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "renameSong: unexpected error", e)
            return false
        }
    }

    @Suppress("ReturnCount")
    suspend fun updateSongTags(
        song: Song,
        title: String,
        artist: String,
        album: String,
        year: Int?,
        genre: String,
        songDao: SongDao
    ): Boolean {
        try {
            // Step 1: Try direct file path write (works when MANAGE_EXTERNAL_STORAGE is granted)
            var fileOk = false
            try {
                fileOk = tagEditor.writeMetadata(
                    filePath = song.filePath,
                    title = title,
                    artist = artist,
                    album = album,
                    year = year,
                    genre = genre
                )
                MuseLog.d("MetadataFileWriter", "updateSongTags: direct write $fileOk")
            } catch (e: IOException) {
                MuseLog.e("MetadataFileWriter", "updateSongTags: direct IO error", e)
            } catch (e: Exception) {
                MuseLog.e("MetadataFileWriter", "updateSongTags: direct write failed", e)
            }

            // Step 2: Fall back to ContentResolver copy-modify-write-back if direct write failed
            if (!fileOk) {
                try {
                    fileOk = modifyAudioFileViaContentResolver(song) { tempFile ->
                        tagEditor.writeMetadata(
                            filePath = tempFile.absolutePath,
                            title = title,
                            artist = artist,
                            album = album,
                            year = year,
                            genre = genre
                        )
                    }
                    MuseLog.d("MetadataFileWriter", "updateSongTags: CR write $fileOk")
                } catch (e: IOException) {
                    MuseLog.e("MetadataFileWriter", "updateSongTags: CR IO error", e)
                } catch (e: Exception) {
                    MuseLog.e("MetadataFileWriter", "updateSongTags: CR write failed", e)
                }
            }

            if (!fileOk) {
                MuseLog.e("MetadataFileWriter", "updateSongTags: All write attempts failed. Aborting.")
                return false
            }

            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    if (album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, album)
                }
                context.contentResolver.update(song.uri, values, null, null)
            } catch (e: Exception) {
                MuseLog.e("MetadataFileWriter", "updateSongTags: MediaStore update failed", e)
            }

            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(song.filePath),
                    null,
                    null
                )
            } catch (e: Exception) {
                MuseLog.e("MetadataFileWriter", "updateSongTags: MediaScanner failed", e)
            }

            songDao.updateSongMetadata(
                id = song.id,
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre,
                artworkUri = song.artworkUri?.toString()
            )

            return true
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "updateSongTags: IO error", e)
            return false
        } catch (e: SecurityException) {
            MuseLog.e("MetadataFileWriter", "updateSongTags: permission denied", e)
            return false
        } catch (e: MetadataException) {
            MuseLog.e("MetadataFileWriter", "updateSongTags: metadata error", e)
            return false
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "updateSongTags: unexpected error", e)
            return false
        }
    }

    suspend fun updateSongWithMetadata(
        song: Song,
        result: MetadataResult,
        songDao: SongDao
    ): Song {
        var fileModified = false

        try {
            fileModified = tagEditor.writeMetadata(
                filePath = song.filePath,
                title = result.title.takeIf { it.isNotBlank() },
                artist = result.artist.takeIf { it.isNotBlank() },
                album = result.album.takeIf { it.isNotBlank() },
                year = result.year,
                genre = result.genre.takeIf { it.isNotBlank() }
            )
            MuseLog.d("MetadataFileWriter", "updateSongWithMetadata: direct write $fileModified")
        } catch (e: IOException) {
            MuseLog.e("MetadataFileWriter", "updateSongWithMetadata: direct IO error", e)
        } catch (e: Exception) {
            MuseLog.e("MetadataFileWriter", "updateSongWithMetadata: direct write failed", e)
        }

        if (!fileModified) {
            try {
                fileModified = modifyAudioFileViaContentResolver(song) { tempFile ->
                    tagEditor.writeMetadata(
                        filePath = tempFile.absolutePath,
                        title = result.title.takeIf { it.isNotBlank() },
                        artist = result.artist.takeIf { it.isNotBlank() },
                        album = result.album.takeIf { it.isNotBlank() },
                        year = result.year,
                        genre = result.genre.takeIf { it.isNotBlank() }
                    )
                }
                MuseLog.d("MetadataFileWriter", "updateSongWithMetadata: CR write $fileModified")
            } catch (e: IOException) {
                MuseLog.e("MetadataFileWriter", "updateSongWithMetadata: CR IO error", e)
            } catch (e: Exception) {
                MuseLog.e("MetadataFileWriter", "updateSongWithMetadata: CR write failed", e)
            }
        }

        if (fileModified) {
            try {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            } catch (e: Exception) {
                MuseLog.e("MetadataFileWriter", "updateSongWithMetadata: MediaScanner failed", e)
            }
        } else {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, result.title)
                    put(MediaStore.Audio.Media.ARTIST, result.artist)
                    if (result.album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, result.album)
                }
                context.contentResolver.update(song.uri, values, null, null)
            } catch (e: SecurityException) {
                MuseLog.e("MetadataFileWriter", "updateSongWithMetadata: MediaStore fallback permission denied", e)
            } catch (e: Exception) {
                MuseLog.e("MetadataFileWriter", "updateSongWithMetadata: MediaStore fallback failed", e)
            }
        }

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
        return song.copy(
            title = result.title,
            artist = result.artist,
            album = result.album,
            year = result.year ?: song.year,
            genre = result.genre.ifBlank { song.genre },
            artworkUri = artworkStr?.let { android.net.Uri.parse(it) }
        )
    }
}
