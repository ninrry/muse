package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LrcLine

/** Serializes normalized lyrics without discarding enhanced word timestamps. */
object LrcSerializer {
    fun serialize(lines: List<LrcLine>, offsetMs: Long = 0L): String = lines
        .joinToString("\n") { line ->
            val lineTimestamp = (line.timestamp - offsetMs).coerceAtLeast(0L)
            val prefix = "[%02d:%02d.%03d]".format(
                lineTimestamp / 60_000,
                (lineTimestamp % 60_000) / 1_000,
                lineTimestamp % 1_000
            )
            prefix + serializeText(line, offsetMs)
        }

    private fun serializeText(line: LrcLine, offsetMs: Long): String {
        val words = line.words
            .orEmpty()
            .sortedBy { it.charStart }
            .takeIf { candidate ->
                var previousEnd = 0
                candidate.all { word ->
                    val valid = word.charStart >= previousEnd &&
                        word.charEndExclusive <= line.text.length &&
                        word.charEndExclusive > word.charStart &&
                        line.text.substring(word.charStart, word.charEndExclusive) == word.text
                    if (valid) previousEnd = word.charEndExclusive
                    valid
                }
            }
            .orEmpty()
        if (words.isEmpty()) return line.text

        val text = line.text
        val result = StringBuilder(text.length + words.size * 12)
        val hasExactDurations = words.all { (it.durationMs ?: 0L) > 0L }
        val serializedLineTimestamp = (line.timestamp - offsetMs).coerceAtLeast(0L)
        var cursor = 0
        words.forEach { word ->
            val start = word.charStart.coerceIn(cursor, text.length)
            val end = word.charEndExclusive.coerceIn(start, text.length)
            result.append(text.substring(cursor, start))
            val time = (word.timeMs - offsetMs).coerceAtLeast(0L)
            if (hasExactDurations) {
                val relativeStart = (time - serializedLineTimestamp).coerceAtLeast(0L)
                result.append("<$relativeStart,${word.durationMs},0>")
            } else {
                result.append("<%02d:%02d.%03d>".format(time / 60_000, (time % 60_000) / 1_000, time % 1_000))
            }
            result.append(text.substring(start, end))
            cursor = end
        }
        if (cursor < text.length) result.append(text.substring(cursor))
        return result.toString()
    }
}
