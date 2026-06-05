package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LrcLine

/**
 * Parser for LRC (LyRiCs) format synchronized lyrics.
 *
 * Parses timestamps in the format [mm:ss.xx] or [mm:ss.xxx]
 * and returns ordered list of LrcLine objects.
 */
object LrcParser {

    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[\.:](\d{1,3}))?]""")

    // Matches embedded word-level timestamps in text like <00:00.000> or <00:01:234>
    private val subTimestampRegex = Regex("""<\d{1,3}:\d{2}[\.:]\d{2,3}>""")

    /**
     * Parse LRC text into ordered list of lyrics lines.
     * Automatically strips embedded word-level timestamps (<mm:ss.xxx>) from text,
     * and merges consecutive short lines within a short time window.
     */
    fun parse(lrcText: String): List<LrcLine> {
        val rawLines = parseRawLines(lrcText)
        rawLines.sortBy { it.timestamp }
        return mergeShortLines(rawLines)
    }

    private fun parseRawLines(lrcText: String): MutableList<LrcLine> {
        val rawLines = mutableListOf<LrcLine>()
        lrcText.lines().forEach { rawLine ->
            appendTimestampsForLine(rawLine, rawLines)
        }
        return rawLines
    }

    private fun appendTimestampsForLine(rawLine: String, rawLines: MutableList<LrcLine>) {
        val line = rawLine.trim()
        val matches = timestampRegex.findAll(line).toList()
        if (matches.isEmpty()) return

        val text = cleanText(line.substring(matches.last().range.last + 1))
        matches.forEach { match ->
            rawLines.add(LrcLine(parseTimestamp(match), text))
        }
    }

    private fun parseTimestamp(match: MatchResult): Long {
        val minutes = match.groupValues[1].toInt()
        val seconds = match.groupValues[2].toInt()
        val millis = parseMillis(match.groupValues.getOrNull(3))
        return (minutes * 60L + seconds) * 1000L + millis
    }

    private fun parseMillis(millisStr: String?): Long = when {
        millisStr.isNullOrEmpty() -> 0L
        millisStr.length == 1 -> millisStr.toLong() * 100L
        millisStr.length == 2 -> millisStr.toLong() * 10L
        else -> millisStr.toLong()
    }

    /**
     * Strip embedded word-level timestamps from LRC text.
     * Enhanced LRC formats sometimes include <mm:ss.xxx> before each word.
     * This removes them cleanly, preserving the actual lyric text.
     */
    private fun cleanText(text: String): String {
        return text.replace(subTimestampRegex, "").trim()
    }

    /**
     * Merge consecutive lines where each line's text is very short (1-2 chars)
     * and time gaps are small (< 500ms), which indicates word-level karaoke LRC.
     * Empty lines (representing pauses/breaks) are NOT merged.
     */
    private fun mergeShortLines(lines: List<LrcLine>): List<LrcLine> {
        if (lines.size <= 1) return lines

        val result = mutableListOf<LrcLine>()
        var index = 0

        while (index < lines.size) {
            val endIndex = findShortLineRunEnd(lines, index)
            if (endIndex > index) {
                result.add(mergeLineRange(lines, index, endIndex))
                index = endIndex + 1
            } else {
                result.add(lines[index])
                index++
            }
        }

        return result
    }

    private fun findShortLineRunEnd(lines: List<LrcLine>, start: Int): Int {
        val mergeThreshold = 500L
        val maxWordChars = 2
        var index = start
        while (index < lines.size - 1 && isMergablePair(lines[index], lines[index + 1], mergeThreshold, maxWordChars)) {
            index++
        }
        return index
    }

    private fun isMergablePair(current: LrcLine, next: LrcLine, mergeThreshold: Long, maxWordChars: Int): Boolean {
        val gap = next.timestamp - current.timestamp
        val currentLen = current.text.length
        val nextLen = next.text.length
        return gap < mergeThreshold &&
            currentLen in 1..maxWordChars &&
            nextLen in 1..maxWordChars
    }

    private fun mergeLineRange(lines: List<LrcLine>, from: Int, to: Int): LrcLine {
        val mergedText = (from..to).joinToString("") { lines[it].text }
        return LrcLine(lines[from].timestamp, mergedText.trim())
    }

    /**
     * Find the index of the active lyrics line for a given playback position.
     * Returns -1 if playback is before the first timestamped line.
     */
    fun getLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var bestIndex = -1
        for (index in lines.indices) {
            if (lines[index].timestamp <= positionMs) {
                bestIndex = index
            } else {
                break
            }
        }
        return bestIndex
    }
}
