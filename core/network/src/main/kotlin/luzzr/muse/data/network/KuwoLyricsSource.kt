package luzzr.muse.data.network

import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.lyrics.LrcParser
import luzzr.muse.domain.model.LyricsResult
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

class KuwoLyricsSource(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val SEARCH_URL = "https://m.kuwo.cn/newh5/singles/search"
        private const val LYRIC_URL = "https://m.kuwo.cn/newh5/singles/songinfoandlrc"
    }

    suspend fun fetch(title: String, artist: String?, album: String?): LyricsResult? = safeCall("KuwoLyricsSource", "fetch") {
        val cleanTitle = SearchMatch.extractBookTitle(title)
        val match = searchSong(cleanTitle, artist) ?: return@safeCall null
        val lyricText = fetchLyricsById(match.rid) ?: return@safeCall null

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
        val rid: Long,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long
    )

    private suspend fun searchSong(title: String, artist: String?): SongMatch? {
        val query = buildSearchQuery(title, artist)
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$SEARCH_URL?key=$encodedQuery&pn=1&rn=10"
        val response = okHttpClient.safeGet("KuwoLyricsSource", url) ?: return null
        if (!response.isSuccessful) return null

        val json = JSONObject(response.body)
        val songs = json.optJSONArray("data") ?: return null
        return pickBestMatch(songs, title, artist)
    }

    private fun pickBestMatch(songs: org.json.JSONArray, title: String, artist: String?): SongMatch? {
        var best: Pair<SongMatch, Int>? = null
        for (i in 0 until songs.length()) {
            val song = songs.getJSONObject(i)
            val rid = song.optLong("musicrid", -1).takeIf { it > 0 }
                ?: song.optLong("rid", -1).takeIf { it > 0 }
                ?: continue
            val songTitle = song.optString("name", "")
            val songArtist = song.optString("artist", "")
                .takeIf { it.isNotBlank() }
                ?: song.optString("singer", "")
                .takeIf { it.isNotBlank() }
                ?: ""
            val album = song.optString("album", "").takeIf { it.isNotBlank() }
            val durationMs = (song.optDouble("duration", 0.0) * 1000).toLong()

            if (!SearchMatch.isArtistAcceptable(artist, songArtist)) continue

            val score = SearchMatch.trackScore(title, artist, songTitle, songArtist)
            if (score < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, songTitle) < 34) continue

            val match = SongMatch(rid, songTitle, songArtist, album, durationMs)
            if (best == null || score > best.second) {
                best = match to score
            }
        }
        return best?.first
    }

    private suspend fun fetchLyricsById(rid: Long): String? {
        val url = "$LYRIC_URL?musicId=$rid"
        val response = okHttpClient.safeGet("KuwoLyricsSource", url) ?: return null
        if (!response.isSuccessful) return null

        val json = JSONObject(response.body)
        val lrcList = json.optJSONObject("data")?.optJSONArray("lrclist") ?: return null
        if (lrcList.length() == 0) return null

        val lrcBuilder = StringBuilder()
        for (i in 0 until lrcList.length()) {
            val line = lrcList.getJSONObject(i)
            val timeStr = line.optString("time", "0")
            val text = line.optString("lineLyric", "")
            val timeMs = (timeStr.toDoubleOrNull() ?: 0.0) * 1000
            val mins = timeMs.toLong() / 60000
            val secs = (timeMs.toLong() % 60000) / 1000
            val millis = timeMs.toLong() % 1000
            lrcBuilder.append("[%02d:%02d.%03d]%s\n".format(mins, secs, millis, text))
        }
        return lrcBuilder.toString().takeIf { it.isNotBlank() }
    }

    private fun buildSearchQuery(title: String, artist: String?): String {
        val parts = mutableListOf(title)
        val cleanArtist = SearchMatch.cleanOptional(artist)
        if (cleanArtist != null) parts.add(cleanArtist)
        return parts.joinToString(" ")
    }
}
