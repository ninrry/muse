package luzzr.muse.data.network

import luzzr.muse.domain.lyrics.LrcParser
import luzzr.muse.domain.model.LyricsResult
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

class NeteaseLyricsSource(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val SEARCH_URL = "https://music.163.com/api/cloudsearch/pc"
        private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
        private const val ALBUM_MATCH_MIN_SCORE = 12
    }

    private data class SongMatch(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long
    )

    suspend fun fetch(title: String, artist: String?, album: String?): LyricsResult? = safeCall("NeteaseLyricsSource", "fetch") {
        val cleanTitle = SearchMatch.extractBookTitle(title)
        val match = searchSong(cleanTitle, artist, album) ?: return@safeCall null
        val lyricText = fetchLyricsById(match.id) ?: return@safeCall null

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
    }

    private suspend fun searchSong(title: String, artist: String?, album: String?): SongMatch? {
        val query = buildSearchQuery(title, artist)
        val postBody = "s=${URLEncoder.encode(query, "UTF-8")}&type=1&offset=0&limit=10"
        val response = okHttpClient.safePost(
            "NeteaseLyricsSource", SEARCH_URL, postBody,
            headers = mapOf(
                "Referer" to "https://music.163.com",
                "User-Agent" to MOBILE_USER_AGENT
            )
        ) ?: return null
        if (!response.isSuccessful) return null

        val songs = parseSearchSongs(response.body) ?: return null
        if (songs.length() == 0) return null
        return pickBestMatch(songs, title, artist, album)
    }

    private fun parseSearchSongs(response: String): org.json.JSONArray? {
        val json = JSONObject(response)
        if (json.optInt("code", -1) != 200) return null
        return json.optJSONObject("result")?.optJSONArray("songs")
    }

    private fun pickBestMatch(songs: org.json.JSONArray, title: String, artist: String?, album: String?): SongMatch? {
        var best: Pair<SongMatch, Int>? = null
        for (i in 0 until songs.length()) {
            val candidate = buildCandidate(songs.getJSONObject(i)) ?: continue
            if (!isAlbumMatch(album, candidate.album)) continue
            if (!SearchMatch.isArtistAcceptable(artist, candidate.artist)) continue

            val score = SearchMatch.trackScore(title, artist, candidate.title, candidate.artist)
            if (score < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, candidate.title) < 34) continue

            if (best == null || score > best.second) {
                best = candidate.toSongMatch() to score
            }
        }
        return best?.first
    }

    private data class Candidate(
        val id: Long,
        val title: String,
        val artist: String,
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
            album = song.optJSONObject("al")?.optNullableString("name"),
            durationMs = song.optLong("dt", 0L)
        )
    }

    private suspend fun fetchLyricsById(songId: Long): String? {
        val url = "$LYRIC_URL?id=$songId&lv=1&kv=1&tv=-1&yv=1&rv=1"
        val response = okHttpClient.safeGet(
            "NeteaseLyricsSource", url,
            headers = mapOf(
                "Referer" to "https://music.163.com",
                "User-Agent" to MOBILE_USER_AGENT
            )
        ) ?: return null
        if (!response.isSuccessful) return null

        return parseSyncedLyricsResponse(response.body)
    }

    internal fun parseSyncedLyricsResponse(responseBody: String): String? {
        val json = JSONObject(responseBody)
        if (json.optInt("code", -1) != 200) return null
        val wordTimed = json.optJSONObject("yrc")?.optNullableString("lyric")
        val lineTimed = json.optJSONObject("lrc")?.optNullableString("lyric")
        return selectPreferredSyncedLyrics(wordTimed, lineTimed)
    }

    internal fun selectPreferredSyncedLyrics(wordTimed: String?, lineTimed: String?): String? {
        return wordTimed
            ?.takeIf { candidate -> LrcParser.parse(candidate).any { !it.words.isNullOrEmpty() } }
            ?: lineTimed
    }

    private fun buildSearchQuery(title: String, artist: String?): String {
        val parts = mutableListOf(title)
        val cleanArtist = SearchMatch.cleanOptional(artist)
        if (cleanArtist != null) parts.add(cleanArtist)
        return parts.joinToString(" ")
    }

    private fun isAlbumMatch(queryAlbum: String?, candidateAlbum: String?): Boolean {
        val cleanAlbum = SearchMatch.cleanOptional(queryAlbum) ?: return true
        return SearchMatch.titleScore(cleanAlbum, candidateAlbum.orEmpty()) >= ALBUM_MATCH_MIN_SCORE
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}
