package luzzr.muse.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches synchronized lyrics from LRCLIB (https://lrclib.net).
 * No API key required. Rate limit: generous, but we cache results in memory and DB.
 */
class LyricsFetcher {

    companion object {
        private const val BASE_URL = "https://lrclib.net/api"
        private const val TIMEOUT = 8000

        @Volatile
        private var instance: LyricsFetcher? = null

        fun getInstance(): LyricsFetcher {
            return instance ?: synchronized(this) {
                instance ?: LyricsFetcher().also { instance = it }
            }
        }
    }

    // Simple in-memory cache: songId → LyricsResult
    private val cache = mutableMapOf<Long, LyricsResult?>()

    /**
     * Fetch synchronized lyrics by artist + track name.
     * Cascading fallback: LRCLIB (exact) → LRCLIB (search) → Netease Cloud Music.
     * All Chinese text is auto-converted to Simplified Chinese.
     */
    suspend fun fetchSync(
        songId: Long,
        title: String,
        artist: String?,
        album: String? = null
    ): LyricsResult? = withContext(Dispatchers.IO) {
        // Check cache first
        cache[songId]?.let { return@withContext it }

        // Tier 1: LRCLIB exact match
        val exactResult = tryLrclibExact(songId, title, artist, album)
        if (exactResult != null) {
            cache[songId] = exactResult
            return@withContext exactResult
        }

        // Tier 2: LRCLIB search fallback
        val searchResult = searchAndCache(songId, title, artist)
        if (searchResult != null) {
            cache[songId] = searchResult
            return@withContext searchResult
        }

        // Tier 3: Netease Cloud Music (best for Chinese songs)
        val neteaseResult = tryNetease(songId, title, artist, album)
        if (neteaseResult != null) {
            cache[songId] = neteaseResult
            return@withContext neteaseResult
        }

        null
    }

    /**
     * Try LRCLIB exact match by track_name + artist_name + album_name.
     */
    private suspend fun tryLrclibExact(
        songId: Long,
        title: String,
        artist: String?,
        album: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val params = mutableListOf(
                "track_name=${URLEncoder.encode(title, "UTF-8")}"
            )
            if (!artist.isNullOrBlank() && artist != "Unknown Artist") {
                params.add("artist_name=${URLEncoder.encode(artist, "UTF-8")}")
            }
            if (!album.isNullOrBlank() && album != "Unknown Album") {
                params.add("album_name=${URLEncoder.encode(album, "UTF-8")}")
            }

            val url = URL("$BASE_URL/get?${params.joinToString("&")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.instanceFollowRedirects = true

            try {
                if (conn.responseCode != 200) return@withContext null
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                return@withContext parseResult(json)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Try Netease Cloud Music as secondary lyrics source.
     */
    private suspend fun tryNetease(
        songId: Long,
        title: String,
        artist: String?,
        album: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val source = NeteaseLyricsSource()
            source.fetch(title, artist, album)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Search LRCLIB when exact match fails.
     */
    private suspend fun searchAndCache(
        songId: Long,
        query: String,
        artist: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val searchQuery = if (!artist.isNullOrBlank() && artist != "Unknown Artist") {
                "$query $artist"
            } else {
                query
            }
            val url = URL("$BASE_URL/search?q=${URLEncoder.encode(searchQuery, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = org.json.JSONArray(response)
            if (arr.length() == 0) return@withContext null

            // Take the best match (has synced lyrics)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val synced = obj.optString("syncedLyrics", null)
                if (!synced.isNullOrBlank()) {
                    val result = parseResult(obj)
                    cache[songId] = result
                    return@withContext result
                }
            }

            // Fallback to first result even without synced lyrics
            val result = parseResult(arr.getJSONObject(0))
            cache[songId] = result
            result
        } catch (_: Exception) {
            null
        }
    }

    private fun parseResult(json: JSONObject): LyricsResult {
        var syncedLyrics = json.optString("syncedLyrics", null)
        var plainLyrics = json.optString("plainLyrics", null)

        // Convert Traditional Chinese → Simplified Chinese for all text
        syncedLyrics = syncedLyrics?.let { toSimplified(it) }
        plainLyrics = plainLyrics?.let { toSimplified(it) }

        val syncedLines = if (!syncedLyrics.isNullOrBlank()) {
            LrcParser.parse(syncedLyrics)
        } else emptyList()

        return LyricsResult(
            id = json.optLong("id", -1).takeIf { it >= 0 },
        trackName = json.optString("name", "").let { toSimplified(it) },
        artistName = json.optString("artistName", "").let { toSimplified(it) },
        albumName = json.optString("albumName", null)?.let { toSimplified(it) },
            duration = json.optDouble("duration", 0.0),
            syncedLines = syncedLines,
            plainText = plainLyrics?.takeIf { it.isNotBlank() }
        )
    }

    /** Convert Traditional Chinese characters to Simplified Chinese. */
    private fun toSimplified(text: String): String =
        MetadataResult.toSimplifiedText(text)

    /** Get the raw LRC text stored for a song (for DB persistence). */
    fun getRawSyncedLyrics(songId: Long): String? {
        val result = cache[songId] ?: return null
        // We don't store raw text in cache; we store parsed lines.
        // This is for the DB persistence which stores raw LRC text.
        return null
    }

    /** Get cached result for a song (for DB persistence fallbacks). */
    fun getCachedResult(songId: Long): LyricsResult? = cache[songId]

    /** Restore a result into cache (e.g., loaded from DB on cold start). */
    fun restoreToCache(songId: Long, result: LyricsResult) {
        // Only restore if not already cached (DB data is the fallback)
        if (songId !in cache) {
            cache[songId] = result
        }
    }

    /** Clear cache (e.g., on song metadata update). */
    fun clearCache() { cache.clear() }
}
