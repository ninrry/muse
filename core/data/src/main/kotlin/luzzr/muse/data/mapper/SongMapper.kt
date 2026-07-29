package luzzr.muse.data.mapper

import android.content.Context
import androidx.core.net.toUri
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.database.SongEntity
import luzzr.muse.domain.model.Song
import java.io.File
import java.io.IOException

fun Song.toEntity() = SongEntity(
    id, title, artist, album, albumId, duration,
    uri, artworkUri, trackNumber, year, genre,
    dateAdded, dateModified, albumArtist, bitrate, sampleRate, channels,
    codec, size, filePath
)

fun SongEntity.toSong() = Song(
    id, title, artist, album, albumId, duration,
    uri, artworkUri, trackNumber, year,
    genre, dateAdded, dateModified, albumArtist, bitrate, sampleRate,
    channels, codec, size, filePath
)

fun isUsableArtworkUri(uri: String?, context: Context): Boolean {
    if (uri.isNullOrBlank()) return false
    val parsedUri = uri.toUri()
    return try {
        when (parsedUri.scheme?.lowercase()) {
            "file" -> parsedUri.path?.let { File(it).exists() } == true
            "content" -> {
                context.contentResolver.getType(parsedUri) != null ||
                    context.contentResolver.openAssetFileDescriptor(parsedUri, "r")?.use { true } == true
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
