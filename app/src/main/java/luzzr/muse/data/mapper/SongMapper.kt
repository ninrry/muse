package luzzr.muse.data.mapper

import android.content.Context
import android.net.Uri
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.database.SongEntity
import luzzr.muse.data.model.Song
import java.io.File
import java.io.IOException

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

fun isUsableArtworkUri(uri: Uri?, context: Context): Boolean {
    if (uri == null || uri.toString().isBlank()) return false
    return try {
        when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it).exists() } == true
            "content" -> {
                context.contentResolver.getType(uri) != null ||
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
            }
            "http", "https" -> true
            else -> false
        }
    } catch (e: IOException) {
        MuseLog.e("SongMapper", "isUsableArtworkUri: IO error for $uri", e)
        false
    } catch (e: SecurityException) {
        MuseLog.e("SongMapper", "isUsableArtworkUri: permission denied for $uri", e)
        false
    } catch (e: Exception) {
        MuseLog.e("SongMapper", "isUsableArtworkUri: unexpected error for $uri", e)
        false
    }
}
