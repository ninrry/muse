package luzzr.muse.data.network

/**
 * A single line of synchronized lyrics with timestamp.
 */
data class LrcLine(
    val timestamp: Long,  // milliseconds
    val text: String
)

/**
 * Result from LRCLIB API lyrics fetch.
 */
data class LyricsResult(
    val id: Long?,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double,
    val syncedLines: List<LrcLine>,
    val plainText: String?
)
