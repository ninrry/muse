package luzzr.muse.domain.model

data class WordSegment(
    val text: String,
    val timeMs: Long,
    /** Index of this word's first character within [LrcLine.text]. */
    val charStart: Int
)

data class LrcLine(
    val timestamp: Long,
    val text: String,
    /** Word-level timing for karaoke (enhanced LRC). Null when unavailable. */
    val words: List<WordSegment>? = null
)
