package luzzr.muse.data.network

/**
 * Parser for LRC (LyRiCs) format synchronized lyrics.
 *
 * Parses timestamps in the format [mm:ss.xx] or [mm:ss.xxx]
 * and returns ordered list of LrcLine objects.
 */
object LrcParser {

    private val lineRegex = Regex("""\[(\d{2}):(\d{2})[\.:](\d{2,3})\](.*)""")
    // Matches embedded word-level timestamps in text like <00:00.000> or <00:01:234>
    private val subTimestampRegex = Regex("""<\d+:\d+[\.:]\d+>""")

    /**
     * Parse LRC text into ordered list of lyrics lines.
     * Automatically strips embedded word-level timestamps (<mm:ss.xxx>) from text,
     * and merges consecutive short lines within a short time window.
     */
    fun parse(lrcText: String): List<LrcLine> {
        val rawLines = mutableListOf<LrcLine>()

        lrcText.lines().forEach { rawLine ->
            val line = rawLine.trim()
            val match = lineRegex.find(line)
            if (match != null) {
                val minutes = match.groupValues[1].toInt()
                val seconds = match.groupValues[2].toInt()
                val millisStr = match.groupValues[3]
                val millis = if (millisStr.length == 2) {
                    millisStr.toInt() * 10  // [mm:ss.xx] → centiseconds
                } else {
                    millisStr.toInt()        // [mm:ss.xxx] → milliseconds
                }
                val rawText = match.groupValues[4].trim()
                // Strip embedded word-level timestamps like <00:00.000> from the text
                val text = cleanText(rawText)
                val timestamp = (minutes * 60L + seconds) * 1000L + millis
                if (text.isNotBlank()) {
                    rawLines.add(LrcLine(timestamp, text))
                }
            }
        }

        rawLines.sortBy { it.timestamp }

        // Merge consecutive short lines within time window into single lines
        return mergeShortLines(rawLines)
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
     * Merge consecutive lines where each line's text is very short (≤ 2 chars)
     * and time gaps are small (< 500ms), which indicates word-level karaoke LRC.
     * Also merge consecutive lines where ALL lines in a 3-second window are short.
     */
    private fun mergeShortLines(lines: List<LrcLine>): List<LrcLine> {
        if (lines.size <= 1) return lines

        val result = mutableListOf<LrcLine>()
        var i = 0

        while (i < lines.size) {
            // Look ahead to find a run of short lines
            var j = i
            val mergeThreshold = 500L // ms
            val maxWordChars = 2      // max chars for a single word

            while (j < lines.size - 1) {
                val gap = lines[j + 1].timestamp - lines[j].timestamp
                val nextTextLen = lines[j + 1].text.length
                val currentTextLen = lines[j].text.length
                if (gap < mergeThreshold && currentTextLen <= maxWordChars && nextTextLen <= maxWordChars) {
                    j++
                } else {
                    break
                }
            }

            if (j > i) {
                // Merge lines i..j into one
                val mergedText = (i..j).joinToString("") { lines[it].text }
                result.add(LrcLine(lines[i].timestamp, mergedText.trim()))
                i = j + 1
            } else {
                result.add(lines[i])
                i++
            }
        }

        return result
    }

    /**
     * Find the index of the active lyrics line for a given playback position.
     * Returns -1 if playback is before the first timestamped line.
     */
    fun getLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var bestIndex = -1
        for (i in lines.indices) {
            if (lines[i].timestamp <= positionMs) {
                bestIndex = i
            } else {
                break
            }
        }
        return bestIndex
    }
}
