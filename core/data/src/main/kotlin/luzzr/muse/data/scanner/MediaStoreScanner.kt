package luzzr.muse.data.scanner

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.data.audio.AudioFileSupport
import luzzr.muse.data.audio.AudioMetadataSanitizer
import luzzr.muse.domain.model.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(
    @Suppress("UnusedPrivateProperty") @ApplicationContext private val context: Context
) {
    val projection = arrayOf(
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

    fun readSongsFromCursor(cursor: android.database.Cursor): List<Song> {
        val collection = mediaStoreCollection()
        val columns = readColumnIndices(cursor)
        val songList = mutableListOf<Song>()

        while (cursor.moveToNext()) {
            val song = buildSongFromRow(cursor, columns, collection) ?: continue
            songList.add(song)
        }

        return songList
    }

    fun mediaStoreCollection(): android.net.Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    private data class ColumnIndices(
        val id: Int,
        val title: Int,
        val artist: Int,
        val album: Int,
        val albumId: Int,
        val duration: Int,
        val data: Int,
        val mime: Int,
        val track: Int,
        val year: Int,
        val size: Int,
        val dateAdded: Int,
        val dateModified: Int
    )

    private fun readColumnIndices(cursor: android.database.Cursor): ColumnIndices = ColumnIndices(
        id = cursor.getColumnIndex(MediaStore.Audio.Media._ID),
        title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE),
        artist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST),
        album = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM),
        albumId = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID),
        duration = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION),
        data = cursor.getColumnIndex(MediaStore.Audio.Media.DATA),
        mime = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE),
        track = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK),
        year = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR),
        size = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE),
        dateAdded = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED),
        dateModified = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
    )

    private fun buildSongFromRow(cursor: android.database.Cursor, col: ColumnIndices, collection: android.net.Uri): Song? {
        val path = col.data.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: return null
        val id = col.id.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: return null
        val albumId = col.albumId.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: -1L
        val mime = col.mime.takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()
        if (!AudioFileSupport.isSupportedAudio(path, mime)) return null

        val rawTitle = col.title.takeIf { it >= 0 }?.let { cursor.getString(it) }
        val rawArtist = col.artist.takeIf { it >= 0 }?.let { cursor.getString(it) }
        val rawAlbum = col.album.takeIf { it >= 0 }?.let { cursor.getString(it) }
        val fileName = java.io.File(path).name

        val sanitized = AudioMetadataSanitizer.sanitize(
            rawTitle = rawTitle,
            rawArtist = rawArtist,
            rawAlbum = rawAlbum,
            fallbackFileName = fileName
        )

        return Song(
            id = id,
            title = sanitized.title,
            artist = sanitized.artist,
            album = sanitized.album,
            albumId = albumId,
            duration = col.duration.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
            uri = ContentUris.withAppendedId(collection, id).toString(),
            artworkUri = albumArtUriFor(albumId),
            filePath = path,
            codec = AudioFileSupport.detectCodec(path, mime),
            size = col.size.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
            trackNumber = col.track.takeIf { it >= 0 }?.let { cursor.getInt(it) } ?: 0,
            year = col.year.takeIf { it >= 0 }?.let { cursor.getInt(it) }?.takeIf { it > 0 },
            dateAdded = secondsToMillis(col.dateAdded, cursor),
            dateModified = secondsToMillis(col.dateModified, cursor)
        )
    }

    private fun albumArtUriFor(albumId: Long): String? {
        if (albumId <= 0) return null
        val albumArtUri = "content://media/external/audio/albumart".toUri()
        return ContentUris.withAppendedId(albumArtUri, albumId).toString()
    }

    private fun secondsToMillis(columnIndex: Int, cursor: android.database.Cursor): Long {
        val seconds = columnIndex.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: return 0L
        return seconds * 1000L
    }
}
