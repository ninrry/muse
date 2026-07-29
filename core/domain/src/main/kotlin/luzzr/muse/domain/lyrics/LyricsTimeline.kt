package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.WordSegment

/**
 * Immutable, normalized representation of a lyric track.
 *
 * All UI surfaces use this class for line selection and karaoke progress. Keeping
 * the rules here prevents the player, the overlay and the lyric state holder from
 * disagreeing around duplicate timestamps, seeks or the last line.
 */
class LyricsTimeline(
    sourceLines: List<LrcLine>,
    private val defaultLastLineDurationMs: Long = DEFAULT_LAST_LINE_DURATION_MS
) {
    val lines: List<LrcLine> = normalize(sourceLines)

    fun indexAt(positionMs: Long, offsetMs: Long = 0L): Int {
        if (lines.isEmpty()) return -1
        val adjusted = (positionMs + offsetMs).coerceAtLeast(0L)
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestamp <= adjusted) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun lineEndMs(index: Int, durationMs: Long = 0L): Long {
        val line = lines.getOrNull(index) ?: return 0L
        val nextTimestamp = lines.asSequence()
            .drop(index + 1)
            .map { it.timestamp }
            .firstOrNull { it > line.timestamp }
        val candidate = nextTimestamp
            ?: durationMs.takeIf { it > line.timestamp }
            ?: (line.timestamp + defaultLastLineDurationMs)
        return candidate.coerceAtLeast(line.timestamp + MIN_LINE_DURATION_MS)
    }

    fun frameAt(positionMs: Long, durationMs: Long = 0L, offsetMs: Long = 0L): LyricsFrame {
        val adjustedPositionMs = (positionMs + offsetMs).coerceAtLeast(0L)
        val currentIndex = indexAt(positionMs, offsetMs)
        if (currentIndex < 0) {
            return LyricsFrame(
                positionMs = positionMs.coerceAtLeast(0L),
                adjustedPositionMs = adjustedPositionMs,
                currentIndex = -1,
                previousIndex = -1,
                nextIndex = 0,
                lineProgress = 0f,
                revealCharacters = 0f,
                fillMode = LyricsFillMode.STATIC
            )
        }

        val line = lines[currentIndex]
        val endMs = lineEndMs(currentIndex, durationMs)
        val spanMs = (endMs - line.timestamp).coerceAtLeast(1L)
        val lineProgress = ((adjustedPositionMs - line.timestamp).toFloat() / spanMs)
            .coerceIn(0f, 1f)

        val validWords = line.words
        return LyricsFrame(
            positionMs = positionMs.coerceAtLeast(0L),
            adjustedPositionMs = adjustedPositionMs,
            currentIndex = currentIndex,
            previousIndex = (currentIndex - 1).takeIf { it >= 0 } ?: -1,
            nextIndex = (currentIndex + 1).takeIf { it <= lines.lastIndex } ?: -1,
            lineProgress = lineProgress,
            revealCharacters = revealCharacters(line, adjustedPositionMs, endMs),
            fillMode = if (validWords.isNullOrEmpty()) LyricsFillMode.LINE else LyricsFillMode.WORD,
            activeWordIndex = validWords?.indexOfLast { it.timeMs <= adjustedPositionMs } ?: -1
        )
    }

    private fun revealCharacters(line: LrcLine, positionMs: Long, endMs: Long): Float {
        val total = line.text.length
        if (total == 0) return 0f
        if (positionMs >= endMs) return total.toFloat()
        val words = line.words ?: return (
            (positionMs - line.timestamp).toFloat() /
                (endMs - line.timestamp).coerceAtLeast(1L)
            ).coerceIn(0f, 1f) * total
        if (words.isEmpty()) return 0f

        val first = words.first()
        if (positionMs < first.timeMs) {
            val headSpan = (first.timeMs - line.timestamp).coerceAtLeast(1L)
            return ((positionMs - line.timestamp).toFloat() / headSpan)
                .coerceIn(0f, 1f) * first.charStart
        }

        var reveal = 0f
        words.forEachIndexed { index, word ->
            if (positionMs < word.timeMs) return@forEachIndexed
            val nextTime = words.getOrNull(index + 1)?.timeMs ?: endMs
            val wordEnd = nextTime.coerceAtLeast(word.timeMs + 1L)
            val wordProgress = ((positionMs - word.timeMs).toFloat() / (wordEnd - word.timeMs))
                .coerceIn(0f, 1f)
            val start = word.charStart.coerceIn(0, total)
            val end = word.charEndExclusive.coerceIn(start, total)
            reveal = maxOf(reveal, start + (end - start) * wordProgress)
        }
        return reveal.coerceIn(0f, total.toFloat())
    }

    companion object {
        const val DEFAULT_LAST_LINE_DURATION_MS = 4_000L
        const val MIN_LINE_DURATION_MS = 400L

        private fun normalize(sourceLines: List<LrcLine>): List<LrcLine> {
            val normalized = sourceLines.mapNotNull { line ->
                val text = line.text.trim()
                if (text.isBlank()) return@mapNotNull null
                val timestamp = line.timestamp.coerceAtLeast(0L)
                val words = normalizeWords(text, line.words)
                LrcLine(timestamp = timestamp, text = text, words = words)
            }.sortedBy { it.timestamp }

            return normalized.distinctBy { line ->
                line.timestamp to (line.text to line.words)
            }
        }

        private fun normalizeWords(text: String, words: List<WordSegment>?): List<WordSegment>? {
            if (words.isNullOrEmpty()) return null
            var previousStart = -1
            val valid = words.sortedWith(compareBy<WordSegment> { it.timeMs }.thenBy { it.charStart })
                .mapNotNull { word ->
                    val start = word.charStart
                    val end = word.charEndExclusive
                    val matches = word.text.isNotBlank() &&
                        start >= 0 && end <= text.length &&
                        end > start &&
                        text.substring(start, end) == word.text
                    if (!matches || start < previousStart) return@mapNotNull null
                    previousStart = end
                    word.copy(timeMs = word.timeMs.coerceAtLeast(0L))
                }
            return valid.takeIf { it.size == words.size }
        }
    }
}

data class LyricsFrame(
    val positionMs: Long,
    val adjustedPositionMs: Long,
    val currentIndex: Int,
    val previousIndex: Int,
    val nextIndex: Int,
    val lineProgress: Float,
    val revealCharacters: Float,
    val fillMode: LyricsFillMode = LyricsFillMode.LINE,
    val activeWordIndex: Int = -1
)

enum class LyricsFillMode {
    STATIC,
    LINE,
    WORD
}

/**
 * Projects a stable player position between coarse progress updates while playing.
 * A changed source position always creates a new anchor, so a seek never inherits
 * the old projection and cannot cause lyric progress to jump or run backwards.
 */
class LyricsSyncEngine(private val timeline: LyricsTimeline) {
    private var anchored = false
    private var anchorPositionMs = 0L
    private var anchorWallClockMs = 0L

    fun reset(positionMs: Long, wallClockMs: Long) {
        anchored = true
        anchorPositionMs = positionMs.coerceAtLeast(0L)
        anchorWallClockMs = wallClockMs
    }

    fun frameAt(positionMs: Long, durationMs: Long = 0L, offsetMs: Long = 0L, isPlaying: Boolean, wallClockMs: Long): LyricsFrame {
        val sourcePosition = positionMs.coerceAtLeast(0L)
        if (!anchored || sourcePosition != anchorPositionMs) {
            reset(sourcePosition, wallClockMs)
        }
        val projectedPosition = if (isPlaying) {
            (anchorPositionMs + (wallClockMs - anchorWallClockMs).coerceAtLeast(0L)).coerceAtLeast(0L)
        } else {
            sourcePosition
        }
        return timeline.frameAt(projectedPosition, durationMs, offsetMs)
    }
}
