package luzzr.muse.data.network

import luzzr.muse.core.log.MuseLog
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Fetches synchronized lyrics from LRCLIB (https://lrclib.net).
 * No API key required. Rate limit: generous, but we cache results in memory and DB.
 */
class LyricsFetcher {

    companion object {
        private const val BASE_URL = "https://lrclib.net/api"
        private const val MAX_CACHE_SIZE = 50
        private const val MAX_CONCURRENT_REQUESTS = 5
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000

        @Volatile
        private var instance: LyricsFetcher? = null

        fun getInstance(): LyricsFetcher {
            return instance ?: synchronized(this) {
                instance ?: LyricsFetcher().also { instance = it }
            }
        }
    }

    // LRU in-memory cache: songId �?LyricsResult (evicts least-recently-used when full)
    private val cache = object : java.util.LinkedHashMap<Long, LyricsResult>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LyricsResult>?) = size > MAX_CACHE_SIZE
    }
    private val networkSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

    /**
     * Fetch synchronized lyrics by artist + track name.
     * Cascading fallback: LRCLIB (exact) �?LRCLIB (search) �?Netease Cloud Music.
     * All Chinese text is auto-converted to Simplified Chinese.
     */
    suspend fun fetchSync(songId: Long, title: String, artist: String?, album: String? = null): LyricsResult? =
        withContext(Dispatchers.IO) {
            // Check cache first
            cache.get(songId)?.let { return@withContext it }
            val cleanTitle = SearchMatch.extractBookTitle(title)
            val cleanArtist = SearchMatch.cleanOptional(artist)
            val cleanAlbum = SearchMatch.cleanOptional(album)

            // Tier 1: LRCLIB exact match
            var plainFallback: LyricsResult? = null
            val exactResult = tryLrclibExact(cleanTitle, cleanArtist, cleanAlbum)
            if (exactResult != null) {
                if (exactResult.syncedLines.isNotEmpty()) {
                    cache.put(songId, exactResult)
                    return@withContext exactResult
                }
                if (!exactResult.plainText.isNullOrBlank()) plainFallback = exactResult
            }

            // Tier 2: LRCLIB search fallback
            val searchResult = search(cleanTitle, cleanArtist)
            if (searchResult != null) {
                if (searchResult.syncedLines.isNotEmpty()) {
                    cache.put(songId, searchResult)
                    return@withContext searchResult
                }
                if (!searchResult.plainText.isNullOrBlank() && plainFallback == null) {
                    plainFallback = searchResult
                }
            }

            // Tier 3: Netease Cloud Music (best for Chinese songs)
            val neteaseResult = tryNetease(title, cleanArtist, cleanAlbum)
            if (neteaseResult != null) {
                cache.put(songId, neteaseResult)
                return@withContext neteaseResult
            }

            // Tier 4: QQ Music (excellent for Chinese copyright-restricted songs, like Jay Chou)
            val qqResult = tryQQMusic(title, cleanArtist)
            if (qqResult != null) {
                cache.put(songId, qqResult)
                return@withContext qqResult
            }

            plainFallback?.also { cache.put(songId, it) }
        }

    /**
     * Try LRCLIB exact match by track_name + artist_name + album_name.
     */
    private suspend fun tryLrclibExact(title: String, artist: String?, album: String?): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val params = mutableListOf(
                "track_name=${URLEncoder.encode(title, "UTF-8")}"
            )
            if (!artist.isNullOrBlank()) {
                params.add("artist_name=${URLEncoder.encode(artist, "UTF-8")}")
            }
            if (!album.isNullOrBlank()) {
                params.add("album_name=${URLEncoder.encode(album, "UTF-8")}")
            }

            networkSemaphore.withPermit {
                val url = URL("$BASE_URL/get?${params.joinToString("&")}")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.instanceFollowRedirects = true

                try {
                    if (conn.responseCode != 200) return@withPermit null
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    parseResult(json)
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: SocketTimeoutException) {
            MuseLog.e("LyricsFetcher", "tryLrclibExact: timeout", e)
            null
        } catch (e: UnknownHostException) {
            MuseLog.e("LyricsFetcher", "tryLrclibExact: host unreachable", e)
            null
        } catch (e: IOException) {
            MuseLog.e("LyricsFetcher", "tryLrclibExact: IO error", e)
            null
        } catch (e: JSONException) {
            MuseLog.e("LyricsFetcher", "tryLrclibExact: JSON parse error", e)
            null
        } catch (e: Exception) {
            MuseLog.e("LyricsFetcher", "tryLrclibExact: unexpected error", e)
            null
        }
    }

    /**
     * Try Netease Cloud Music as secondary lyrics source.
     */
    private suspend fun tryNetease(title: String, artist: String?, album: String?): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            networkSemaphore.withPermit {
                val source = NeteaseLyricsSource()
                source.fetch(title, artist, album)
            }
        } catch (e: Exception) {
            MuseLog.e("LyricsFetcher", "tryNetease: unexpected error", e)
            null
        }
    }

    private suspend fun tryQQMusic(title: String, artist: String?): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val query = buildString {
                append(title)
                val cleanArtist = SearchMatch.cleanOptional(artist)
                if (cleanArtist != null) {
                    append(" $cleanArtist")
                }
            }
            networkSemaphore.withPermit {
                // 1. 搜索歌曲 mid
                val searchUrl = URL("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=5&w=${URLEncoder.encode(query, "UTF-8")}&format=json")
                val searchConn = searchUrl.openConnection() as HttpURLConnection
                searchConn.apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Referer", "https://y.qq.com/")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                }
                var songmid: String? = null
                var trackTitle = ""
                var trackArtist = ""
                var albumName = ""
                if (searchConn.responseCode == 200) {
                    val response = searchConn.inputStream.bufferedReader().readText()
                    val cleaned = cleanJsonp(response)
                    val json = JSONObject(cleaned)
                    if (json.optInt("code", -1) == 0) {
                        val list = json.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
                        if (list != null && list.length() > 0) {
                            // 寻找相似度最高的一项
                            var bestIndex = 0
                            var maxScore = -1
                            for (i in 0 until list.length()) {
                                val item = list.getJSONObject(i)
                                val itemTitle = item.optString("songname", "")
                                val singerArr = item.optJSONArray("singer")
                                val singers = mutableListOf<String>()
                                if (singerArr != null) {
                                    for (j in 0 until singerArr.length()) {
                                        singers.add(singerArr.getJSONObject(j).optString("name", ""))
                                    }
                                }
                                val itemArtist = singers.joinToString(" / ")
                                val score = SearchMatch.trackScore(title, artist, itemTitle, itemArtist)
                                if (score > maxScore) {
                                    maxScore = score
                                    bestIndex = i
                                }
                            }
                            // 必须满足最低匹配度分数
                            if (maxScore >= SearchMatch.minimumAcceptableScore(artist) || SearchMatch.titleScore(title, list.getJSONObject(bestIndex).optString("songname", "")) >= 34) {
                                val bestItem = list.getJSONObject(bestIndex)
                                songmid = bestItem.optString("songmid", "")
                                trackTitle = bestItem.optString("songname", "")
                                val singerArr = bestItem.optJSONArray("singer")
                                val singers = mutableListOf<String>()
                                if (singerArr != null) {
                                    for (j in 0 until singerArr.length()) {
                                        singers.add(singerArr.getJSONObject(j).optString("name", ""))
                                    }
                                }
                                trackArtist = singers.joinToString(" / ")
                                albumName = bestItem.optString("albumname", "")
                            }
                        }
                    }
                }
                searchConn.disconnect()

                if (songmid.isNullOrBlank()) return@withPermit null

                // 2. 拉取歌词
                val lyricUrl = URL("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songmid&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0")
                val lyricConn = lyricUrl.openConnection() as HttpURLConnection
                lyricConn.apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Referer", "https://y.qq.com/")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                }
                var rawLrc: String? = null
                if (lyricConn.responseCode == 200) {
                    val response = lyricConn.inputStream.bufferedReader().readText()
                    val cleaned = cleanJsonp(response)
                    val json = JSONObject(cleaned)
                    if (json.optInt("code", -1) == 0) {
                        val base64Lyric = json.optString("lyric", "")
                        if (base64Lyric.isNotBlank()) {
                            val decodedBytes = try {
                                java.util.Base64.getDecoder().decode(base64Lyric)
                            } catch (e: Throwable) {
                                android.util.Base64.decode(base64Lyric, android.util.Base64.DEFAULT)
                            }
                            rawLrc = String(decodedBytes, kotlin.text.Charsets.UTF_8)
                        }
                    }
                }
                lyricConn.disconnect()

                if (rawLrc.isNullOrBlank()) return@withPermit null

                val simplifiedLrc = toSimplifiedText(rawLrc)
                val parsedLines = LrcParser.parse(simplifiedLrc)
                
                if (parsedLines.isEmpty()) return@withPermit null

                LyricsResult(
                    id = null,
                    trackName = trackTitle,
                    artistName = trackArtist,
                    albumName = albumName,
                    duration = 0.0,
                    syncedLines = parsedLines,
                    plainText = null,
                    rawSyncedLyrics = simplifiedLrc
                )
            }
        } catch (e: Exception) {
            MuseLog.e("LyricsFetcher", "tryQQMusic error", e)
            null
        }
    }

    private fun cleanJsonp(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }

    /**
     * Search LRCLIB when exact match fails.
     */
    private suspend fun search(query: String, artist: String?): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val searchQuery = if (!artist.isNullOrBlank()) {
                "$query $artist"
            } else {
                query
            }

            val response = networkSemaphore.withPermit {
                val url = URL("$BASE_URL/search?q=${URLEncoder.encode(searchQuery, "UTF-8")}")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS

                try {
                    if (conn.responseCode != 200) return@withPermit null
                    conn.inputStream.bufferedReader().readText()
                } finally {
                    conn.disconnect()
                }
            } ?: return@withContext null

            val arr = org.json.JSONArray(response)
            if (arr.length() == 0) return@withContext null

            var bestSynced: Pair<LyricsResult, Int>? = null
            var bestPlain: Pair<LyricsResult, Int>? = null
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val result = parseResult(obj)
                val score = SearchMatch.trackScore(query, artist, result.trackName, result.artistName)
                if (score < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(query, result.trackName) < 34) continue

                if (result.syncedLines.isNotEmpty()) {
                    if (bestSynced == null || score > bestSynced!!.second) {
                        bestSynced = result to score
                    }
                } else if (!result.plainText.isNullOrBlank()) {
                    if (bestPlain == null || score > bestPlain!!.second) {
                        bestPlain = result to score
                    }
                }
            }

            bestSynced?.first ?: bestPlain?.first
        } catch (e: SocketTimeoutException) {
            MuseLog.e("LyricsFetcher", "search: timeout", e)
            null
        } catch (e: UnknownHostException) {
            MuseLog.e("LyricsFetcher", "search: host unreachable", e)
            null
        } catch (e: IOException) {
            MuseLog.e("LyricsFetcher", "search: IO error", e)
            null
        } catch (e: JSONException) {
            MuseLog.e("LyricsFetcher", "search: JSON parse error", e)
            null
        } catch (e: Exception) {
            MuseLog.e("LyricsFetcher", "search: unexpected error", e)
            null
        }
    }

    private fun parseResult(json: JSONObject): LyricsResult {
        var syncedLyrics = json.optNullableString("syncedLyrics")
        var plainLyrics = json.optNullableString("plainLyrics")

        // Convert Traditional Chinese �?Simplified Chinese for all text
        syncedLyrics = syncedLyrics?.let { toSimplified(it) }
        plainLyrics = plainLyrics?.let { toSimplified(it) }

        val syncedLines = if (!syncedLyrics.isNullOrBlank()) {
            LrcParser.parse(syncedLyrics)
        } else {
            emptyList()
        }

        return LyricsResult(
            id = json.optLong("id", -1).takeIf { it >= 0 },
            trackName = json.optString("name", "").let { toSimplified(it) },
            artistName = json.optString("artistName", "").let { toSimplified(it) },
            albumName = json.optNullableString("albumName")?.let { toSimplified(it) },
            duration = json.optDouble("duration", 0.0),
            syncedLines = syncedLines,
            plainText = plainLyrics?.takeIf { it.isNotBlank() },
            rawSyncedLyrics = syncedLyrics?.takeIf { it.isNotBlank() }
        )
    }

    /** Convert Traditional Chinese characters to Simplified Chinese. */
    private fun toSimplified(text: String): String = toSimplifiedText(text)

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    /** Get the raw LRC text stored for a song (for DB persistence). */
    fun getRawSyncedLyrics(songId: Long): String? {
        val result = cache.get(songId) ?: return null
        return result.rawSyncedLyrics
    }

    /** Get cached result for a song (for DB persistence fallbacks). */
    fun getCachedResult(songId: Long): LyricsResult? = cache.get(songId)

    /** Restore a result into cache (e.g., loaded from DB on cold start). */
    fun restoreToCache(songId: Long, result: LyricsResult) {
        // Only restore if not already cached (DB data is the fallback)
        if (cache.get(songId) == null) {
            cache.put(songId, result)
        }
    }

    /** Clear cache (e.g., on song metadata update). */
    fun clearCache() {
        cache.clear()
    }
}
