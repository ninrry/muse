package luzzr.muse.data.network

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lyrics source using Netease Cloud Music (网易云音乐) public API.
 *
 * Provides excellent coverage for Chinese songs (Mandarin & Cantonese).
 * Used as a secondary fallback when LRCLIB has no results.
 *
 * API endpoints:
 *   Search:  POST /api/cloudsearch/pc  (body: s=<query>&type=1)
 *   Lyrics:  GET  /api/song/lyric?id=<id>&lv=1&kv=1&tv=-1
 *
 * Key: Netease uses short field names in JSON responses:
 *   ar  → artists array   (e.g. [{"id":9272, "name":"孙燕姿"}])
 *   al  → album object    (e.g. {"id":123, "name":"专辑名"})
 *   dt  → duration in ms
 */
class NeteaseLyricsSource {

    companion object {
        private const val SEARCH_URL = "https://music.163.com/api/cloudsearch/pc"
        private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
        private const val TIMEOUT = 8000
        private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    /**
     * Fetch lyrics from Netease for the given song.
     * Returns null if no match found or any error occurs.
     */
    suspend fun fetch(
        title: String,
        artist: String?,
        album: String?
    ): LyricsResult? {
        return try {
            val songId = searchSong(title, artist) ?: return null
            val lyricText = fetchLyricsById(songId) ?: return null

            // Convert Traditional → Simplified Chinese
            val simplified = MetadataResult.toSimplifiedText(lyricText)
            val syncedLines = LrcParser.parse(simplified)

            LyricsResult(
                id = null,
                trackName = title,
                artistName = artist ?: "",
                albumName = album,
                duration = 0.0,
                syncedLines = syncedLines,
                plainText = null
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Search for a song by title + artist via POST cloudsearch API.
     * @return Netease internal song ID, or null if no match.
     */
    private fun searchSong(title: String, artist: String?): Long? {
        val query = buildSearchQuery(title, artist)
        val postBody = "s=${URLEncoder.encode(query, "UTF-8")}&type=1&offset=0&limit=10"

        val url = URL(SEARCH_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", "https://music.163.com")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

        return try {
            // Write POST body
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(postBody) }

            if (conn.responseCode != 200) return null
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            if (json.optInt("code", -1) != 200) return null

            val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return null
            if (songs.length() == 0) return null

            // Return the best match (first result, most relevant)
            songs.getJSONObject(0).optLong("id", -1).takeIf { it > 0 }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Fetch lyrics text for a given Netease song ID.
     * Parses LRC format from the response.
     */
    private fun fetchLyricsById(songId: Long): String? {
        val url = URL("$LYRIC_URL?id=$songId&lv=1&kv=1&tv=-1")

        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", "https://music.163.com")

        return try {
            if (conn.responseCode != 200) return null
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            if (json.optInt("code", -1) != 200) return null

            val lrcObj = json.optJSONObject("lrc") ?: return null
            lrcObj.optString("lyric", null)?.takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Build search query from title + optional artist.
     */
    private fun buildSearchQuery(title: String, artist: String?): String {
        val parts = mutableListOf(title)
        if (!artist.isNullOrBlank() && artist != "Unknown Artist") {
            parts.add(artist)
        }
        return parts.joinToString(" ")
    }
}
