package luzzr.muse.data.network

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared matching helpers for metadata and lyrics lookups.
 *
 * The network sources all return ranked results, but their ranking is often based on
 * global popularity rather than this local file's title/artist. Keep a local score
 * gate so common titles do not silently attach the wrong metadata or lyrics.
 */
internal object SearchMatch {
    private val noiseRegex = Regex(
        "【[^】]*】|\\[[^\\]]*]|\\([^)]*\\)|" +
            "(?i)official|music\\s*video|lyrics?|lyric\\s*video|mv|live|audio|" +
            "visualizer|remaster(?:ed)?|version|完整版|纯享|伴奏|高清|超清|无损|高音质|4k|hd"
    )

    private val unknownMarkers = setOf(
        "",
        "unknown",
        "unknownartist",
        "unknownalbum",
        "unknownsong",
        "unknowntrack",
        "unknownsinger",
        "<unknown>",
        "未知",
        "未知艺术家",
        "未知歌手",
        "未知专辑",
        "未知歌曲"
    )

    fun extractBookTitle(title: String): String {
        val regex = Regex("《([^》]+)》")
        val matchResult = regex.find(title)
        return matchResult?.groupValues?.get(1)?.trim() ?: title
    }

    fun cleanOptional(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return trimmed.takeIf { normalize(it) !in unknownMarkers }
    }

    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return toSimplifiedText(value)
            .lowercase(Locale.ROOT)
            .replace(noiseRegex, " ")
            .filter { it.isLetterOrDigit() }
    }

    fun titleScore(query: String, candidate: String): Int {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isBlank() || c.isBlank()) return 0
        if (q == c) return 60
        if (c.contains(q)) return 54
        return titleContainedOrOverlapScore(q, c)
    }

    private fun titleContainedOrOverlapScore(q: String, c: String): Int {
        if (q.contains(c) && c.length >= (q.length / 2).coerceAtLeast(2)) return 42
        return overlapScore(q, c, 36)
    }

    fun artistScore(query: String?, candidate: String?): Int {
        val cleanQuery = cleanOptional(query) ?: return 24
        val q = normalize(cleanQuery)
        val c = normalize(candidate)
        if (q.isBlank() || c.isBlank()) return 0
        if (q == c) return 32
        if (c.contains(q) || q.contains(c)) return 26
        return overlapScore(q, c, 20)
    }

    fun trackScore(queryTitle: String, queryArtist: String?, candidateTitle: String, candidateArtist: String?): Int {
        val title = titleScore(queryTitle, candidateTitle)
        if (title < 18) return title
        return (title + artistScore(queryArtist, candidateArtist)).coerceIn(0, 100)
    }

    fun minimumAcceptableScore(queryArtist: String?): Int {
        return if (cleanOptional(queryArtist) == null) 34 else 46
    }

    private fun overlapScore(query: String, candidate: String, maxScore: Int): Int {
        val queryChars = query.toSet()
        if (queryChars.isEmpty()) return 0
        val overlap = queryChars.count { it in candidate }.toDouble() / queryChars.size
        return (overlap * maxScore).roundToInt()
    }
}
