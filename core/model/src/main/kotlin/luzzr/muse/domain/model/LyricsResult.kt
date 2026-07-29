package luzzr.muse.domain.model

data class LyricsResult(
    val id: Long?,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double,
    val syncedLines: List<LrcLine>,
    val plainText: String?,
    val rawSyncedLyrics: String? = null,
    /** 来源标识：lrclib / netease / qq / kugou / kuwo / ovh */
    val source: String = ""
)
