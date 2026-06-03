package luzzr.muse.data.network

import luzzr.muse.core.log.MuseLog
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Lyrics source using Netease Cloud Music (网易云音�? public API.
 *
 * Provides excellent coverage for Chinese songs (Mandarin & Cantonese).
 * Used as a secondary fallback when LRCLIB has no results.
 *
 * API endpoints:
 *   Search:  POST /api/cloudsearch/pc  (body: s=<query>&type=1)
 *   Lyrics:  GET  /api/song/lyric?id=<id>&lv=1&kv=1&tv=-1
 *
 * Key: Netease uses short field names in JSON responses:
 *   ar  �?artists array   (e.g. [{"id":9272, "name":"孙燕�?}])
 *   al  �?album object    (e.g. {"id":123, "name":"专辑�?})
 *   dt  �?duration in ms
 */
class NeteaseLyricsSource {

    companion object {
        private const val SEARCH_URL = "https://music.163.com/api/cloudsearch/pc"
        private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
        private const val ALBUM_MATCH_MIN_SCORE = 42
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private data class SongMatch(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long
    )

    /**
     * Fetch lyrics from Netease for the given song.
     * Returns null if no match found or any error occurs.
     */
    suspend fun fetch(title: String, artist: String?, album: String?): LyricsResult? {
        return try {
            val cleanTitle = SearchMatch.extractBookTitle(title)
            val match = searchSong(cleanTitle, artist, album) ?: return null
            val lyricText = fetchLyricsById(match.id) ?: return null

            // Convert Traditional �?Simplified Chinese
            val simplified = toSimplifiedText(lyricText)
            val syncedLines = LrcParser.parse(simplified)
            val plainText = syncedLines.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }

            LyricsResult(
                id = match.id,
                trackName = toSimplifiedText(match.title),
                artistName = toSimplifiedText(match.artist),
                albumName = toSimplifiedText(match.album ?: album.orEmpty()).takeIf { it.isNotBlank() },
                duration = match.durationMs / 1000.0,
                syncedLines = syncedLines,
                plainText = plainText,
                rawSyncedLyrics = simplified.takeIf { it.isNotBlank() }
            )
        } catch (e: SocketTimeoutException) {
            MuseLog.e("NeteaseLyricsSource", "fetch: timeout", e)
            null
        } catch (e: UnknownHostException) {
            MuseLog.e("NeteaseLyricsSource", "fetch: host unreachable", e)
            null
        } catch (e: IOException) {
            MuseLog.e("NeteaseLyricsSource", "fetch: IO error", e)
            null
        } catch (e: JSONException) {
            MuseLog.e("NeteaseLyricsSource", "fetch: JSON parse error", e)
            null
        } catch (e: Exception) {
            MuseLog.e("NeteaseLyricsSource", "fetch: unexpected error", e)
            null
        }
    }

    /**
     * Search for a song by title + artist via POST cloudsearch API.
     * @return Netease internal song ID, or null if no match.
     */
    private fun searchSong(title: String, artist: String?, album: String?): SongMatch? {
        val conn = openSearchConnection(title, artist) ?: return null
        return try {
            val response = readSearchResponse(conn) ?: return null
            val songs = parseSearchSongs(response) ?: return null
            if (songs.length() == 0) return null
            pickBestMatch(songs, title, artist, album)
        } finally {
            conn.disconnect()
        }
    }

    private fun openSearchConnection(title: String, artist: String?): HttpURLConnection? {
        val query = buildSearchQuery(title, artist)
        val postBody = "s=${URLEncoder.encode(query, "UTF-8")}&type=1&offset=0&limit=10"
        return try {
            val url = URL(SEARCH_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", "https://music.163.com")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(postBody) }
            conn
        } catch (e: Exception) {
            MuseLog.w("NeteaseLyricsSource", "Failed to open search connection", e)
            null
        }
    }

    private fun readSearchResponse(conn: HttpURLConnection): String? {
        return try {
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            MuseLog.w("NeteaseLyricsSource", "Failed to read search response", e)
            null
        }
    }

    private fun parseSearchSongs(response: String): org.json.JSONArray? {
        val json = JSONObject(response)
        if (json.optInt("code", -1) != 200) return null
        return json.optJSONObject("result")?.optJSONArray("songs")
    }

    private fun pickBestMatch(
        songs: org.json.JSONArray,
        title: String,
        artist: String?,
        album: String?
    ): SongMatch? {
        var best: Pair<SongMatch, Int>? = null
        for (i in 0 until songs.length()) {
            val candidate = buildCandidate(songs.getJSONObject(i)) ?: continue
            if (!isAlbumMatch(album, candidate.album)) continue

            val score = SearchMatch.trackScore(title, artist, candidate.title, candidate.artist)
            if (score < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, candidate.title) < 34) continue

            if (best == null || score > best!!.second) {
                best = candidate.toSongMatch() to score
            }
        }
        return best?.first
    }

    private data class Candidate(
        val id: Long,
        val title: String,
        val artist: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long
    ) {
        fun toSongMatch(): SongMatch = SongMatch(id, title, artist, album, durationMs)
    }

    private fun buildCandidate(song: org.json.JSONObject): Candidate? {
        val id = song.optLong("id", -1).takeIf { it > 0 } ?: return null
        val title = song.optString("name", "")
        val artistsArr = song.optJSONArray("ar")
        val artistNames = mutableListOf<String>()
        if (artistsArr != null) {
            for (j in 0 until artistsArr.length()) {
                artistNames.add(artistsArr.getJSONObject(j).optString("name", ""))
            }
        }
        return Candidate(
            id = id,
            title = title,
            artist = artistNames.joinToString(" / "),
            artists = artistNames,
            album = song.optJSONObject("al")?.optNullableString("name"),
            durationMs = song.optLong("dt", 0L)
        )
    }

    /**
     * Fetch lyrics text for a given Netease song ID.
     * Parses LRC format from the response.
     */
    private fun fetchLyricsById(songId: Long): String? {
        val url = URL("$LYRIC_URL?id=$songId&lv=1&kv=1&tv=-1")

        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", "https://music.163.com")

        return try {
            if (conn.responseCode != 200) return null
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            if (json.optInt("code", -1) != 200) return null

            val lrcObj = json.optJSONObject("lrc") ?: return null
            lrcObj.optNullableString("lyric")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Build search query from title + optional artist.
     */
    private fun buildSearchQuery(title: String, artist: String?): String {
        val parts = mutableListOf(title)
        val cleanArtist = SearchMatch.cleanOptional(artist)
        if (cleanArtist != null) {
            parts.add(cleanArtist)
        }
        return parts.joinToString(" ")
    }

    private fun isStrictArtistMatch(queryArtist: String?, candidateArtists: List<String>): Boolean {
        val cleanArtist = SearchMatch.cleanOptional(queryArtist) ?: return true
        val queryArtists = splitArtists(cleanArtist)
        val candidateArtistSet = candidateArtists.map { SearchMatch.normalize(it) }.filter { it.isNotBlank() }.toSet()
        if (queryArtists.isEmpty()) return true
        if (queryArtists.size == 1) {
            return candidateArtistSet.size == 1 && candidateArtistSet.first() == queryArtists.first()
        }
        return candidateArtistSet.size == queryArtists.size && queryArtists.all { it in candidateArtistSet }
    }

    private fun isAlbumMatch(queryAlbum: String?, candidateAlbum: String?): Boolean {
        val cleanAlbum = SearchMatch.cleanOptional(queryAlbum) ?: return true
        return SearchMatch.titleScore(cleanAlbum, candidateAlbum.orEmpty()) >= ALBUM_MATCH_MIN_SCORE
    }

    private fun splitArtists(value: String): List<String> {
        val separator = Regex("\\s*(/|、|,|，|&|;|；|\\+)\\s*|(?i)\\s+feat\\.?\\s+|\\s+ft\\.?\\s+")
        return value.split(separator)
            .map { SearchMatch.normalize(it) }
            .filter { it.isNotBlank() }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}
