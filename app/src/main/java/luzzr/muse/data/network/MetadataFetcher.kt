package luzzr.muse.data.network

import luzzr.muse.core.log.MuseLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
     * Input:  "�?K修复】周杰伦 - 青花�?(Live 超清)_哔哩哔哩_bilibili"
     * Output: query="青花�?, artist="周杰�? (cleaned)
     */
    data class SanitizedQuery(
        val title: String,
        val artist: String?
    )

    fun sanitizeQuery(rawTitle: String, rawArtist: String? = null): SanitizedQuery {
        var title = SearchMatch.extractBookTitle(rawTitle)
        val extractedArtist = mutableListOf<String>()

        // 1. Remove URL suffixes and platform markers
        title = title.replace(Regex("(_哔哩哔哩|_bilibili|_YouTube|_youtube|_niconico)$", RegexOption.IGNORE_CASE), "")
        title = title.replace(Regex("[-–—·\\s]*(哔哩哔哩|bilibili|YouTube|youtube|niconico)$", RegexOption.IGNORE_CASE), "")

        // 2. Remove bracketed prefixes 【xxx】 [xxx], (xxx) but save their content
        // Common prefix patterns: 【4K】【Hi-Res】【Official】【MV】【高清修复】
        title = title.replace(Regex("【[^】]*】|\\[[^\\]]*\\]|\\([^)]*\\)"), "")

        // 3. Remove time/quality suffixes: 4K, HD, 超清, 无损, 高音质
        val qualityPattern = "\\s*(4K|HD|超清|高清|无损|高音质|超高清|完美音质)\\s*"
        title = title.replace(Regex(qualityPattern, RegexOption.IGNORE_CASE), " ")

        // 4. Remove common suffixes: (Live), (Official), (MV), (Audio)
        val suffixPattern = "\\s*\\((Live|Official|MV|Audio|Audio Video|Lyrics|" +
            "Lyric Video|Official Music Video|Official Video|Visualizer)\\)\\s*"
        title = title.replace(Regex(suffixPattern, RegexOption.IGNORE_CASE), " ")

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
        val artist = extractedArtist.firstOrNull() ?: SearchMatch.cleanOptional(rawArtist)

        return SanitizedQuery(title = title, artist = artist)
    }

    /**
     * Search for a song by title and optional artist.
     * Returns up to [maxResults] matches sorted by confidence.
     *
     * Input is automatically sanitized �?video titles like
     * "�?K】周杰伦 - 青花�?(Live)" are cleaned to "青花�? with artist "周杰�?.
     */
    suspend fun search(rawTitle: String, rawArtist: String? = null, maxResults: Int = 10): List<MetadataResult> =
        withContext(Dispatchers.IO) {
            // Auto-sanitize input �?strip video title noise
            val sanitized = sanitizeQuery(rawTitle, rawArtist)
            val title = sanitized.title
            val artist = sanitized.artist

            val results = mutableListOf<MetadataResult>()

            // Try MusicBrainz first
            try {
                delay(THROTTLE_DELAY_MS) // MusicBrainz 1 req/s rate limit compliance
                val mbResults = searchMusicBrainz(title, artist)
                results.addAll(mbResults)
            } catch (e: SocketTimeoutException) {
                MuseLog.e("MetadataFetcher", "search MusicBrainz: timeout", e)
                // Fall through to Deezer
            } catch (e: UnknownHostException) {
                MuseLog.e("MetadataFetcher", "search MusicBrainz: host unreachable", e)
                // Fall through to Deezer
            } catch (e: IOException) {
                MuseLog.e("MetadataFetcher", "search MusicBrainz: IO error", e)
                // Fall through to Deezer
            } catch (e: JSONException) {
                MuseLog.e("MetadataFetcher", "search MusicBrainz: JSON parse error", e)
                // Fall through to Deezer
            } catch (e: Exception) {
                MuseLog.e("MetadataFetcher", "search MusicBrainz: unexpected error", e)
                // Fall through to Deezer
            }

            // Try Netease (supplement / fallback)
            if (results.size < maxResults) {
                try {
                    val neResults = searchNetease(title, artist, maxResults - results.size)
                    results.addAll(neResults)
                } catch (e: Exception) {
                    MuseLog.e("MetadataFetcher", "search Netease error", e)
                }
            }

            // Try iTunes (supplement / fallback)
            if (results.size < maxResults) {
                try {
                    val itResults = searchITunes(title, artist, maxResults - results.size)
                    results.addAll(itResults)
                } catch (e: Exception) {
                    MuseLog.e("MetadataFetcher", "search iTunes error", e)
                }
            }

            // Try Deezer as supplement / fallback
            if (results.size < maxResults) {
                try {
                    val dzResults = searchDeezer(title, artist, maxResults - results.size)
                    results.addAll(dzResults)
                } catch (e: SocketTimeoutException) {
                    MuseLog.e("MetadataFetcher", "search Deezer: timeout", e)
                } catch (e: UnknownHostException) {
                    MuseLog.e("MetadataFetcher", "search Deezer: host unreachable", e)
                } catch (e: IOException) {
                    MuseLog.e("MetadataFetcher", "search Deezer: IO error", e)
                } catch (e: JSONException) {
                    MuseLog.e("MetadataFetcher", "search Deezer: JSON parse error", e)
                } catch (e: Exception) {
                    MuseLog.e("MetadataFetcher", "search Deezer: unexpected error", e)
                }
            }

            // Try QQMusic for Chinese songs / fallback
            if (results.size < maxResults) {
                try {
                    val qqResults = searchQQMusic(title, artist, maxResults - results.size)
                    results.addAll(qqResults)
                } catch (e: Exception) {
                    MuseLog.e("MetadataFetcher", "search QQMusic error", e)
                }
            }

            val grouped = results.groupBy {
                SearchMatch.normalize(it.title) to SearchMatch.canonicalizeArtist(it.artist) to it.album.lowercase()
            }
            val merged = grouped.map { (_, list) ->
                val best = list.maxByOrNull { it.score } ?: list.first()
                val cover = list.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
                if (best.coverUrl.isNullOrBlank() && !cover.isNullOrBlank()) {
                    best.copy(coverUrl = cover)
                } else {
                    best
                }
            }
            merged.sortedByDescending { it.score }
                .map { it.toSimplifiedChinese() }
                .take(maxResults)
        }

    /**
     * Search with exact user-provided title and artist �?NO auto-sanitize.
     * Use this when the user manually enters search terms.
     */
    suspend fun searchExact(title: String, artist: String? = null, maxResults: Int = 10): List<MetadataResult> =
        withContext(Dispatchers.IO) {
            val cleanTitle = SearchMatch.extractBookTitle(title)
            val results = mutableListOf<MetadataResult>()

            // Try MusicBrainz first
            try {
                delay(THROTTLE_DELAY_MS) // MusicBrainz 1 req/s rate limit compliance
                val mbResults = searchMusicBrainz(cleanTitle, artist)
                results.addAll(mbResults)
            } catch (e: Exception) {
                MuseLog.e("MetadataFetcher", "searchExact: MusicBrainz error", e)
            }

            // Try Netease
            try {
                val neResults = searchNetease(cleanTitle, artist, maxResults)
                results.addAll(neResults)
            } catch (e: Exception) {
                MuseLog.e("MetadataFetcher", "searchExact: Netease error", e)
            }

            // Try iTunes
            try {
                val itResults = searchITunes(cleanTitle, artist, maxResults)
                results.addAll(itResults)
            } catch (e: Exception) {
                MuseLog.e("MetadataFetcher", "searchExact: iTunes error", e)
            }

            // Always try Deezer independently for more diverse results
            try {
                val dzResults = searchDeezer(cleanTitle, artist, maxResults)
                results.addAll(dzResults)
            } catch (e: Exception) {
                MuseLog.e("MetadataFetcher", "searchExact: Deezer error", e)
            }

            // Try QQMusic for Chinese songs
            try {
                val qqResults = searchQQMusic(cleanTitle, artist, maxResults)
                results.addAll(qqResults)
            } catch (e: Exception) {
                MuseLog.e("MetadataFetcher", "searchExact: QQMusic error", e)
            }

            val grouped = results.groupBy {
                SearchMatch.normalize(it.title) to SearchMatch.canonicalizeArtist(it.artist) to it.album.lowercase()
            }
            val merged = grouped.map { (_, list) ->
                val best = list.maxByOrNull { it.score } ?: list.first()
                val cover = list.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
                if (best.coverUrl.isNullOrBlank() && !cover.isNullOrBlank()) {
                    best.copy(coverUrl = cover)
                } else {
                    best
                }
            }
            merged.sortedByDescending { it.score }
                .map { it.toSimplifiedChinese() }
                .take(maxResults)
        }

    private fun searchMusicBrainz(title: String, artist: String?): List<MetadataResult> {
        val query = buildString {
            append("recording:\"${escapeQuery(title)}\"")
            val cleanArtist = SearchMatch.cleanOptional(artist)
            if (cleanArtist != null) {
                append(" AND artist:\"${escapeQuery(cleanArtist)}\"")
            }
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://musicbrainz.org/ws/2/recording/?query=$encodedQuery&fmt=json&limit=10")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Muse/1.0 ( luzzr.muse )")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
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
            val matchScore = SearchMatch.trackScore(title, artist, recTitle, recArtist)
            if (matchScore < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, recTitle) < 34) continue
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

            results.add(
                MetadataResult(
                    title = recTitle,
                    artist = recArtist,
                    album = album,
                    year = year,
                    source = "MusicBrainz",
                    score = ((score.coerceIn(0, 100) * 0.55f) + (matchScore * 0.45f)).toInt().coerceIn(0, 100)
                )
            )
        }

        return results
    }

    private fun searchDeezer(title: String, artist: String?, limit: Int): List<MetadataResult> {
        val query = buildString {
            append(title)
            val cleanArtist = SearchMatch.cleanOptional(artist)
            if (cleanArtist != null) {
                append(" ${cleanArtist.take(20)}")
            }
        }

        val url = URL("https://api.deezer.com/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=$limit&order=RANKING")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
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
            val rawCoverUrl = track.optJSONObject("album")?.optString("cover_medium", "") ?: ""
            val coverUrl = if (rawCoverUrl.startsWith("http://")) "https://" + rawCoverUrl.substring(7) else rawCoverUrl
            val explicit = track.optInt("explicit_lyrics", 0)
            val matchScore = SearchMatch.trackScore(title, artist, trackTitle, trackArtist)
            if (matchScore < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, trackTitle) < 34) continue
            val sourceBonus = (if (explicit > 0) 3 else 0) + (if (coverUrl.isNotBlank()) 3 else 0)

            results.add(
                MetadataResult(
                    title = trackTitle,
                    artist = trackArtist,
                    album = trackAlbum,
                    coverUrl = if (coverUrl.isNotBlank()) coverUrl else null,
                    source = "Deezer",
                    score = (matchScore + sourceBonus).coerceIn(0, 100)
                )
            )
        }

        return results
    }

    private fun searchNetease(title: String, artist: String?, limit: Int): List<MetadataResult> {
        val query = buildString {
            append(title)
            val cleanArtist = SearchMatch.cleanOptional(artist)
            if (cleanArtist != null) {
                append(" $cleanArtist")
            }
        }
        val postBody = "s=${URLEncoder.encode(query, "UTF-8")}&type=1&offset=0&limit=$limit"
        val results = mutableListOf<MetadataResult>()

        try {
            val url = URL("https://music.163.com/api/cloudsearch/pc")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Referer", "https://music.163.com")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.writer(Charsets.UTF_8).use { it.write(postBody) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                if (json.optInt("code", -1) == 200) {
                    val songs = json.optJSONObject("result")?.optJSONArray("songs")
                    if (songs != null) {
                        for (i in 0 until songs.length()) {
                            val songObj = songs.getJSONObject(i)
                            val trackTitle = songObj.optString("name", "")

                            val artistsArr = songObj.optJSONArray("ar")
                            val artistNames = mutableListOf<String>()
                            if (artistsArr != null) {
                                for (j in 0 until artistsArr.length()) {
                                    artistNames.add(artistsArr.getJSONObject(j).optString("name", ""))
                                }
                            }
                            val trackArtist = artistNames.joinToString(" / ")

                            val albumObj = songObj.optJSONObject("al")
                            val trackAlbum = albumObj?.optString("name", "") ?: ""
                            val rawCoverUrl = albumObj?.optString("picUrl", "") ?: ""
                            val coverUrl = if (rawCoverUrl.startsWith("http://")) "https://" + rawCoverUrl.substring(7) else rawCoverUrl

                            val matchScore = SearchMatch.trackScore(title, artist, trackTitle, trackArtist)
                            if (matchScore < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, trackTitle) < 34) continue

                            results.add(
                                MetadataResult(
                                    title = trackTitle,
                                    artist = trackArtist,
                                    album = trackAlbum,
                                    coverUrl = if (coverUrl.isNotBlank()) coverUrl else null,
                                    source = "Netease",
                                    score = matchScore
                                )
                            )
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            MuseLog.e("MetadataFetcher", "searchNetease error", e)
        }
        return results
    }

    private fun searchITunes(title: String, artist: String?, limit: Int): List<MetadataResult> {
        val query = buildString {
            append(title)
            val cleanArtist = SearchMatch.cleanOptional(artist)
            if (cleanArtist != null) {
                append(" $cleanArtist")
            }
        }
        val results = mutableListOf<MetadataResult>()
        try {
            val url = URL("https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&media=music&limit=$limit")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val data = json.optJSONArray("results") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val track = data.getJSONObject(i)
                    val trackTitle = track.optString("trackName", "")
                    val trackArtist = track.optString("artistName", "")
                    val trackAlbum = track.optString("collectionName", "")
                    var coverUrl = track.optString("artworkUrl100", "")
                    if (coverUrl.contains("/100x100")) {
                        coverUrl = coverUrl.replace("/100x100", "/500x500")
                    }
                    if (coverUrl.startsWith("http://")) {
                        coverUrl = "https://" + coverUrl.substring(7)
                    }
                    val matchScore = SearchMatch.trackScore(title, artist, trackTitle, trackArtist)
                    if (matchScore < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, trackTitle) < 34) continue
                    
                    results.add(
                        MetadataResult(
                            title = trackTitle,
                            artist = trackArtist,
                            album = trackAlbum,
                            coverUrl = if (coverUrl.isNotBlank()) coverUrl else null,
                            source = "iTunes",
                            score = matchScore
                        )
                    )
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            MuseLog.e("MetadataFetcher", "searchITunes error", e)
        }
        return results;
    }

    private fun searchQQMusic(title: String, artist: String?, limit: Int): List<MetadataResult> {
        val query = buildString {
            append(title)
            val cleanArtist = SearchMatch.cleanOptional(artist)
            if (cleanArtist != null) {
                append(" $cleanArtist")
            }
        }
        val results = mutableListOf<MetadataResult>()
        try {
            val url = URL("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=$limit&w=${URLEncoder.encode(query, "UTF-8")}&format=json")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Referer", "https://y.qq.com/")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                if (json.optInt("code", -1) == 0) {
                    val songObj = json.optJSONObject("data")?.optJSONObject("song")
                    val list = songObj?.optJSONArray("list") ?: JSONArray()
                    for (i in 0 until list.length()) {
                        val track = list.getJSONObject(i)
                        val trackTitle = track.optString("songname", "")
                        val trackAlbum = track.optString("albumname", "")
                        val albummid = track.optString("albummid", "")
                        
                        val singerArr = track.optJSONArray("singer")
                        val singerNames = mutableListOf<String>()
                        if (singerArr != null) {
                            for (j in 0 until singerArr.length()) {
                                singerNames.add(singerArr.getJSONObject(j).optString("name", ""))
                            }
                        }
                        val trackArtist = singerNames.joinToString(" / ")
                        
                        val coverUrl = if (albummid.isNotBlank()) {
                            "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albummid}.jpg"
                        } else {
                            ""
                        }
                        
                        val matchScore = SearchMatch.trackScore(title, artist, trackTitle, trackArtist)
                        if (matchScore < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, trackTitle) < 34) continue
                        
                        val pubtime = track.optLong("pubtime", 0L)
                        val year = if (pubtime > 0L) {
                            val calendar = java.util.Calendar.getInstance()
                            calendar.timeInMillis = pubtime * 1000L
                            calendar.get(java.util.Calendar.YEAR)
                        } else {
                            null
                        }
                        
                        results.add(
                            MetadataResult(
                                title = trackTitle,
                                artist = trackArtist,
                                album = trackAlbum,
                                year = year,
                                coverUrl = if (coverUrl.isNotBlank()) coverUrl else null,
                                source = "QQMusic",
                                score = matchScore
                            )
                        )
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            MuseLog.e("MetadataFetcher", "searchQQMusic error", e)
        }
        return results
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: return "")
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val THROTTLE_DELAY_MS = 1_200L

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
