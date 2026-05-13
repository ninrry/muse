package luzzr.muse.data.model

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

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri? = null,
    val songCount: Int = 0,
    val year: Int? = null
)

data class Artist(
    val name: String,
    val songCount: Int = 0,
    val albumCount: Int = 0
)

enum class SortType(val label: String) {
    TITLE_ASC("标题 A→Z"),
    TITLE_DESC("标题 Z→A"),
    ARTIST_ASC("艺术家 A→Z"),
    ARTIST_DESC("艺术家 Z→A"),
    ALBUM_ASC("专辑 A→Z"),
    ALBUM_DESC("专辑 Z→A"),
    DURATION_ASC("时长 ↑"),
    DURATION_DESC("时长 ↓"),
    DATE_ADDED_DESC("最近添加"),
    DATE_ADDED_ASC("最早添加");
}
