package luzzr.muse.data.network

import luzzr.muse.domain.lyrics.LrcParser
import luzzr.muse.domain.model.LyricsResult
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

class KugouLyricsSource(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val SEARCH_URL = "https://mobilecdn.kugou.com/api/v3/search/song"
        private const val LYRIC_URL = "https://krcs.kugou.com/search"
        private const val LYRIC_DOWNLOAD_URL = "https://lyrics.kugou.com/download"
    }

    suspend fun fetch(title: String, artist: String?, album: String?): LyricsResult? = safeCall("KugouLyricsSource", "fetch") {
        val cleanTitle = SearchMatch.extractBookTitle(title)
        val match = searchSong(cleanTitle, artist) ?: return@safeCall null
        val lyricText = fetchLyricsById(match.hash) ?: return@safeCall null

        val simplified = toSimplifiedText(lyricText)
        val syncedLines = LrcParser.parse(simplified)
        val plainText = syncedLines.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }

        LyricsResult(
            id = null,
            trackName = toSimplifiedText(match.title),
            artistName = toSimplifiedText(match.artist),
            albumName = toSimplifiedText(match.album ?: album.orEmpty()).takeIf { it.isNotBlank() },
            duration = match.durationMs / 1000.0,
            syncedLines = syncedLines,
            plainText = plainText,
            rawSyncedLyrics = simplified.takeIf { it.isNotBlank() }
        )
    }

    private data class SongMatch(
        val hash: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long
    )

    private suspend fun searchSong(title: String, artist: String?): SongMatch? {
        val query = buildSearchQuery(title, artist)
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$SEARCH_URL?keyword=$encodedQuery&page=1&pagesize=10"
        val response = okHttpClient.safeGet("KugouLyricsSource", url) ?: return null
        if (!response.isSuccessful) return null

        val json = JSONObject(response.body)
        val songs = json.optJSONObject("data")?.optJSONArray("info") ?: return null
        return pickBestMatch(songs, title, artist)
    }

    private fun pickBestMatch(songs: org.json.JSONArray, title: String, artist: String?): SongMatch? {
        var best: Pair<SongMatch, Int>? = null
        for (i in 0 until songs.length()) {
            val song = songs.getJSONObject(i)
            val songTitle = song.optString("songname", "")
            val songArtist = parseArtists(song)
            val hash = song.optString("hash", "").takeIf { it.isNotBlank() } ?: continue

            if (!SearchMatch.isArtistAcceptable(artist, songArtist)) continue

            val score = SearchMatch.trackScore(title, artist, songTitle, songArtist)
            if (score < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, songTitle) < 34) continue

            val durationMs = song.optInt("duration", 0) * 1000L
            val match = SongMatch(hash, songTitle, songArtist, null, durationMs)
            if (best == null || score > best.second) {
                best = match to score
            }
        }
        return best?.first
    }

    private fun parseArtists(song: org.json.JSONObject): String {
        return song.optString("singername", "").takeIf { it.isNotBlank() }
            ?: song.optString("singer", "")
    }

    private suspend fun fetchLyricsById(hash: String): String? {
        val url = "$LYRIC_URL?ver=1&man=yes&client=pc&keyword=&duration=&hash=$hash&album_audio_id="
        val response = okHttpClient.safeGet("KugouLyricsSource", url) ?: return null
        if (!response.isSuccessful) return null

        val json = JSONObject(response.body)
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val first = candidates.getJSONObject(0)
        val lyricId = first.optString("id", "").takeIf { it.isNotBlank() } ?: return null
        val accessKey = first.optString("accesskey", "").takeIf { it.isNotBlank() } ?: return null

        return downloadLyric(lyricId, accessKey)
    }

    private suspend fun downloadLyric(id: String, accessKey: String): String? {
        val url = "$LYRIC_DOWNLOAD_URL?id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
        val response = okHttpClient.safeGet("KugouLyricsSource", url) ?: return null
        if (!response.isSuccessful) return null

        val json = JSONObject(response.body)
        val content = json.optString("content", "").takeIf { it.isNotBlank() } ?: return null
        return android.util.Base64.decode(content, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
    }

    private fun buildSearchQuery(title: String, artist: String?): String {
        val parts = mutableListOf(title)
        val cleanArtist = SearchMatch.cleanOptional(artist)
        if (cleanArtist != null) parts.add(cleanArtist)
        return parts.joinToString(" ")
    }
}
