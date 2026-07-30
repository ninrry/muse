package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.WordSegment

/**
 * Parser for LRC (LyRiCs) format synchronized lyrics.
 *
 * Parses timestamps in the format [mm:ss.xx] or [mm:ss.xxx]
 * and returns ordered list of LrcLine objects.
 */
object LrcParser {

    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[\.:](\d{1,3}))?]""")

    // KRC/QRC line timestamps are commonly stored as [startMs,durationMs].
    private val durationLineRegex = Regex("""^\[(\d{1,9}),(\d{1,9})](.*)$""")

    // Enhanced LRC: <00:00.000>word. Some writers use a colon between seconds
    // and milliseconds, so both separators are accepted.
    private val colonWordTimestampRegex = Regex("""<(\d{1,3}):(\d{2})[\.:](\d{1,3})>""")

    // YRC/KRC/QRC word tokens: (startMs,durationMs,flags) or <startMs,durationMs,flags>.
    private val durationWordTimestampRegex = Regex("""[<(](\d{1,9}),(\d{1,9})(?:,\d+)?[)>]""")

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

        durationLineRegex.matchEntire(line)?.let { match ->
            val timestamp = match.groupValues[1].toLongOrNull() ?: return@let
            val duration = match.groupValues[2].toLongOrNull()
            val content = match.groupValues[3]
            val (text, words) = parseWordsWithTiming(content, timestamp, duration)
            if (text.isNotBlank()) rawLines.add(LrcLine(timestamp, text, words))
            return
        }

        val matches = timestampRegex.findAll(line).toList()
        if (matches.isEmpty()) return

        val content = line.substring(matches.last().range.last + 1)
        val firstTimestamp = parseTimestamp(matches.first())
        val (text, words) = parseWordsWithTiming(content, firstTimestamp, null)
        matches.forEach { match ->
            val timestamp = parseTimestamp(match)
            if (timestamp >= 0L) rawLines.add(LrcLine(timestamp, text, words))
        }
    }

    private fun parseTimestamp(match: MatchResult): Long {
        val minutes = match.groupValues[1].toInt()
        val seconds = match.groupValues[2].toInt()
        if (seconds >= 60) return -1L
        val millis = parseMillis(match.groupValues.getOrNull(3))
        return (minutes * 60L + seconds) * 1000L + millis
    }

    private fun parseColonWordTimestamp(match: MatchResult): Long {
        val minutes = match.groupValues[1].toInt()
        val seconds = match.groupValues[2].toInt()
        if (seconds >= 60) return -1L
        val millis = parseMillis(match.groupValues.getOrNull(3))
        return (minutes * 60L + seconds) * 1000L + millis
    }

    /**
     * Parse enhanced-LRC word-level timing.
     * Input looks like: `<00:05.100>Hello <00:05.500>world`.
     * Returns the cleaned full text plus, when present, a list of [WordSegment]
     * carrying each word's time and its character offset within the cleaned text.
     * The cleaned text is produced by removing the `<..>` tokens so any spaces
     * between words are preserved.
     */
    private fun parseWordsWithTiming(content: String, lineTimestamp: Long, lineDurationMs: Long?): Pair<String, List<WordSegment>?> {
        val colonTokens = colonWordTimestampRegex.findAll(content).toList()
        if (colonTokens.isNotEmpty()) {
            val words = buildWordSegments(
                content = content,
                tokens = colonTokens,
                timeAt = { parseColonWordTimestamp(it) },
                durationAt = { null }
            )
            return cleanText(content) to words
        }

        val durationTokens = durationWordTimestampRegex.findAll(content).toList()
        if (durationTokens.isEmpty()) return cleanText(content) to null
        // KRC/QRC normally stores starts relative to the line, while NetEase YRC
        // stores absolute starts. Use the line duration to distinguish the two;
        // equality with the line start resolves the small early-song overlap.
        val tokenStarts = durationTokens.map {
            it.groupValues[1].toLongOrNull() ?: return cleanText(content) to null
        }
        val usesAbsoluteTime = usesAbsoluteDurationWordTime(
            tokenStarts = tokenStarts,
            lineTimestamp = lineTimestamp,
            lineDurationMs = lineDurationMs
        )
        val words = buildWordSegments(
            content = content,
            tokens = durationTokens,
            timeAt = { token ->
                val start = token.groupValues[1].toLongOrNull() ?: -1L
                when {
                    start < 0L -> -1L
                    usesAbsoluteTime -> start
                    else -> lineTimestamp + start
                }
            },
            durationAt = { token -> token.groupValues[2].toLongOrNull()?.takeIf { it > 0L } }
        )
        return cleanText(content) to words
    }

    private fun usesAbsoluteDurationWordTime(tokenStarts: List<Long>, lineTimestamp: Long, lineDurationMs: Long?): Boolean {
        val firstStart = tokenStarts.firstOrNull() ?: return false
        if (lineTimestamp == 0L) return true
        if (lineDurationMs != null && lineDurationMs > 0L) {
            val fitsRelative = tokenStarts.all { it in 0L..lineDurationMs }
            val absoluteEnd = lineTimestamp + lineDurationMs
            val fitsAbsolute = tokenStarts.all { it in lineTimestamp..absoluteEnd }
            if (fitsAbsolute && !fitsRelative) return true
            if (fitsRelative && !fitsAbsolute) return false
            if (fitsAbsolute && firstStart == lineTimestamp) return true
        }
        return firstStart == lineTimestamp
    }

    private fun buildWordSegments(
        content: String,
        tokens: List<MatchResult>,
        timeAt: (MatchResult) -> Long,
        durationAt: (MatchResult) -> Long?
    ): List<WordSegment>? {
        val cleaned = cleanText(content)
        if (cleaned.isEmpty()) return null

        val words = mutableListOf<WordSegment>()
        var searchPos = 0
        for (i in tokens.indices) {
            val start = tokens[i].range.last + 1
            val end = if (i + 1 < tokens.size) tokens[i + 1].range.first else content.length
            val rawWord = content.substring(start, end).trim()
            if (rawWord.isEmpty()) continue
            val wordTime = timeAt(tokens[i])
            if (wordTime < 0L) return null
            val charStart = cleaned.indexOf(rawWord, searchPos)
            if (charStart < 0) return null
            words.add(
                WordSegment(
                    text = rawWord,
                    timeMs = wordTime,
                    charStart = charStart,
                    charEndExclusive = charStart + rawWord.length,
                    durationMs = durationAt(tokens[i])
                )
            )
            searchPos = charStart + rawWord.length
        }

        return words.takeIf { it.isNotEmpty() }
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
        return text
            .replace(colonWordTimestampRegex, "")
            .replace(durationWordTimestampRegex, "")
            .trim()
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
        // Word-level (karaoke) lines must never be merged — they already carry timing.
        if (current.words != null || next.words != null) return false
        val gap = next.timestamp - current.timestamp
        val currentLen = current.text.length
        val nextLen = next.text.length
        return gap < mergeThreshold &&
            currentLen in 1..maxWordChars &&
            nextLen in 1..maxWordChars
    }

    private fun mergeLineRange(lines: List<LrcLine>, from: Int, to: Int): LrcLine {
        val source = lines.subList(from, to + 1)
        val mergedText = source.joinToString("") { it.text }.trim()
        var characterOffset = 0
        val timedCharacters = source.mapNotNull { line ->
            val segmentText = line.text.trim()
            if (segmentText.isEmpty()) return@mapNotNull null
            val segment = WordSegment(
                text = segmentText,
                timeMs = line.timestamp,
                charStart = characterOffset,
                charEndExclusive = characterOffset + segmentText.length
            )
            characterOffset += segmentText.length
            segment
        }
        return LrcLine(
            timestamp = lines[from].timestamp,
            text = mergedText,
            words = timedCharacters.takeIf { it.isNotEmpty() && characterOffset == mergedText.length }
        )
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
