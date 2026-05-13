package luzzr.muse.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches song metadata from free, no-API-key web services.
 *
 * Primary: MusicBrainz API (accurate metadata, 1 req/s rate limit)
 * Fallback: Deezer public API (cover art + metadata, no rate limit)
 */
class MetadataFetcher {

    /**
     * Sanitize a video-derived title for music search.
     *
     * Input:  "【4K修复】周杰伦 - 青花瓷 (Live 超清)_哔哩哔哩_bilibili"
     * Output: query="青花瓷", artist="周杰伦" (cleaned)
     */
    data class SanitizedQuery(
        val title: String,
        val artist: String?
    )

    fun sanitizeQuery(rawTitle: String, rawArtist: String? = null): SanitizedQuery {
        var title = rawTitle
        val extractedArtist = mutableListOf<String>()

        // 1. Remove URL suffixes and platform markers
        title = title.replace(Regex("(_哔哩哔哩|_bilibili|_YouTube|_youtube|_niconico)$", RegexOption.IGNORE_CASE), "")
        title = title.replace(Regex("[-–—·\\s]*(哔哩哔哩|bilibili|YouTube|youtube|niconico)$", RegexOption.IGNORE_CASE), "")

        // 2. Remove bracketed prefixes 【xxx】, [xxx], (xxx) — but save their content
        // Common prefix patterns: 【4K】【Hi-Res】【Official】【MV】【高清修复】
        title = title.replace(Regex("【[^】]*】|\\[[^\\]]*\\]|\\([^)]*\\)"), "")

        // 3. Remove time/quality suffixes: 4K, HD, 超清, 无损, 高音质
        title = title.replace(Regex("\\s*(4K|HD|超清|高清|无损|高音质|超高清|完美音质)\\s*", RegexOption.IGNORE_CASE), " ")

        // 4. Remove common suffixes: (Live), (Official), (MV), (Audio)
        title = title.replace(Regex("\\s*\\((Live|Official|MV|Audio|Audio Video|Lyrics|Lyric Video|Official Music Video|Official Video|Visualizer)\\)\\s*", RegexOption.IGNORE_CASE), " ")

        // 5. Try to extract "Artist - Title" pattern
        val dashSplit = title.split(Regex("\\s*[-–—·]\\s*"))
        if (dashSplit.size >= 2) {
            // Could be "Artist - Title" or "Title - Artist"
            val first = dashSplit[0].trim()
            val second = dashSplit[1].trim()
            // Heuristic: if first part is short and seems like a name, it's likely artist
            if (first.length <= 20 && first.matches(Regex("^[\\p{L}\\s]+$"))) {
                extractedArtist.add(first)
                title = second
            } else if (second.length <= 20 && second.matches(Regex("^[\\p{L}\\s]+$"))) {
                extractedArtist.add(second)
                title = first
            }
        }

        // 6. Collapse multiple spaces
        title = title.replace(Regex("\\s+"), " ").trim()

        // Use extracted artist if available, otherwise rawArtist
        val artist = extractedArtist.firstOrNull() ?: (rawArtist?.takeIf { it != "Unknown Artist" })

        return SanitizedQuery(title = title, artist = artist)
    }

    /**
     * Search for a song by title and optional artist.
     * Returns up to [maxResults] matches sorted by confidence.
     *
     * Input is automatically sanitized — video titles like
     * "【4K】周杰伦 - 青花瓷 (Live)" are cleaned to "青花瓷" with artist "周杰伦".
     */
    suspend fun search(
        rawTitle: String,
        rawArtist: String? = null,
        maxResults: Int = 10
    ): List<MetadataResult> = withContext(Dispatchers.IO) {
        // Auto-sanitize input — strip video title noise
        val sanitized = sanitizeQuery(rawTitle, rawArtist)
        val title = sanitized.title
        val artist = sanitized.artist

        val results = mutableListOf<MetadataResult>()

        // Try MusicBrainz first
        try {
            Thread.sleep(1200) // 1 req/s rate limit compliance
            val mbResults = searchMusicBrainz(title, artist)
            results.addAll(mbResults)
        } catch (_: Exception) {
            // Fall through to Deezer
        }

        // Try Deezer as supplement / fallback
        if (results.size < maxResults) {
            try {
                val dzResults = searchDeezer(title, artist, maxResults - results.size)
                results.addAll(dzResults)
            } catch (_: Exception) {
                // Give up
            }
        }

        results.distinctBy { it.title to it.artist }
            .sortedByDescending { it.score }
            .map { it.toSimplifiedChinese() }
            .take(maxResults)
    }

    /**
     * Search with exact user-provided title and artist — NO auto-sanitize.
     * Use this when the user manually enters search terms.
     */
    suspend fun searchExact(
        title: String,
        artist: String? = null,
        maxResults: Int = 10
    ): List<MetadataResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MetadataResult>()

        // Try MusicBrainz first
        try {
            Thread.sleep(1200) // 1 req/s rate limit compliance
            val mbResults = searchMusicBrainz(title, artist)
            results.addAll(mbResults)
        } catch (_: Exception) { }

        // Always try Deezer independently for more diverse results
        try {
            val dzResults = searchDeezer(title, artist, maxResults)
            results.addAll(dzResults)
        } catch (_: Exception) { }

        // Dedup by (title, artist, album, source) to keep diverse results
        results.distinctBy { it.title.lowercase() to it.artist.lowercase() to it.album.lowercase() to it.source }
            .sortedByDescending { it.score }
            .map { it.toSimplifiedChinese() }
            .take(maxResults)
    }

    private fun searchMusicBrainz(title: String, artist: String?): List<MetadataResult> {
        val query = buildString {
            append("recording:\"${escapeQuery(title)}\"")
            if (!artist.isNullOrBlank() && artist != "Unknown Artist") {
                append(" AND artist:\"${escapeQuery(artist)}\"")
            }
        }

        val url = URL("https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=10")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Muse/1.0 ( luzzr.muse )")
            connectTimeout = 5000
            readTimeout = 5000
        }

        val response = readResponse(conn)
        conn.disconnect()

        val json = JSONObject(response)
        val recordings = json.optJSONArray("recordings") ?: JSONArray()

        val results = mutableListOf<MetadataResult>()
        for (i in 0 until recordings.length()) {
            val rec = recordings.getJSONObject(i)
            val recTitle = rec.optString("title", "")
            val score = rec.optInt("score", 0)
            val artistCredit = rec.optJSONArray("artist-credit")?.optJSONObject(0)
            val recArtist = artistCredit?.optString("name", "") ?: ""
            val artistScore = if (artist?.equals(recArtist, ignoreCase = true) == true) 20 else 0
            val releases = rec.optJSONArray("releases")

            var album = ""
            var year: Int? = null
            if (releases != null && releases.length() > 0) {
                val release = releases.getJSONObject(0)
                album = release.optString("title", "")
                val dateStr = release.optString("date", "")
                if (dateStr.length >= 4) {
                    year = dateStr.substring(0, 4).toIntOrNull()
                }
            }

            results.add(MetadataResult(
                title = recTitle,
                artist = recArtist,
                album = album,
                year = year,
                source = "MusicBrainz",
                score = score + artistScore
            ))
        }

        return results
    }

    private fun searchDeezer(title: String, artist: String?, limit: Int): List<MetadataResult> {
        val query = buildString {
            append(title)
            if (!artist.isNullOrBlank() && artist != "Unknown Artist") {
                append(" ${artist.take(20)}")
            }
        }

        val url = URL("https://api.deezer.com/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=$limit&order=RANKING")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }

        val response = readResponse(conn)
        conn.disconnect()

        val json = JSONObject(response)
        val data = json.optJSONArray("data") ?: JSONArray()

        val results = mutableListOf<MetadataResult>()
        for (i in 0 until data.length()) {
            val track = data.getJSONObject(i)
            val trackTitle = track.optString("title", "")
            val trackArtist = track.optJSONObject("artist")?.optString("name", "") ?: ""
            val trackAlbum = track.optJSONObject("album")?.optString("title", "") ?: ""
            val coverUrl = track.optJSONObject("album")?.optString("cover_medium", "") ?: ""
            val explicit = track.optInt("explicit_lyrics", 0)

            results.add(MetadataResult(
                title = trackTitle,
                artist = trackArtist,
                album = trackAlbum,
                coverUrl = if (coverUrl.isNotBlank()) coverUrl else null,
                source = "Deezer",
                score = if (explicit > 0) 90 else 80
            ))
        }

        return results
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(
            if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        ))
        val response = reader.readText()
        reader.close()
        return response
    }

    companion object {
        private fun escapeQuery(s: String): String {
            return s.replace("\"", "\\\"")
                .replace("(", "\\\\(")
                .replace(")", "\\\\)")
        }

        @Volatile
        private var instance: MetadataFetcher? = null

        fun getInstance(): MetadataFetcher {
            return instance ?: synchronized(this) {
                instance ?: MetadataFetcher().also { instance = it }
            }
        }
    }
}
