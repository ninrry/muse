package luzzr.muse.domain.model

import android.net.Uri

data class Song(
    val id: Long = 0,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = -1,
    val duration: Long = 0,
    val uri: Uri = Uri.EMPTY,
    val artworkUri: Uri? = null,
    val trackNumber: Int = 0,
    val year: Int? = null,
    val genre: String = "",
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val albumArtist: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val codec: String? = null,
    val size: Long = 0,
    val filePath: String = ""
) {
    val formattedDuration: String
        get() {
            val totalSec = duration / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }

    val formattedSize: String
        get() = when {
            size <= 0 -> ""
            size >= 1_073_741_824 -> "%.1f GB".format(size / 1_073_741_824.0)
            size >= 1_048_576 -> "%.1f MB".format(size / 1_048_576.0)
            size >= 1_024 -> "%.1f KB".format(size / 1_024.0)
            else -> "$size B"
        }
}
