package luzzr.muse.domain.model

data class WordSegment(
    val text: String,
    val timeMs: Long,
    /** Index of this segment's first character within [LrcLine.text]. */
    val charStart: Int,
    /** Exclusive end index. Defaults to the legacy text-length calculation. */
    val charEndExclusive: Int = charStart + text.length,
    /** Exact display duration when supplied by YRC/KRC/QRC. */
    val durationMs: Long? = null
)

data class LrcLine(
    val timestamp: Long,
    val text: String,
    /** Word-level timing for karaoke (enhanced LRC). Null when unavailable. */
    val words: List<WordSegment>? = null
)
