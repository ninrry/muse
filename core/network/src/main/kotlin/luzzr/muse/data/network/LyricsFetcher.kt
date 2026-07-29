package luzzr.muse.data.network

import java.net.URLEncoder
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.lyrics.LrcParser
import luzzr.muse.domain.lyrics.LyricsSearchClient
import luzzr.muse.domain.model.LyricsResult
import okhttp3.OkHttpClient
import org.json.JSONObject

class LyricsFetcher(
    private val okHttpClient: OkHttpClient
) : LyricsSearchClient {

    companion object {
        private const val BASE_URL = "https://lrclib.net/api"
        private const val MAX_CACHE_SIZE = 50
        private const val MAX_CONCURRENT_REQUESTS = 5
    }

    private val kugouSource by lazy { KugouLyricsSource(okHttpClient) }
    private val kuwoSource by lazy { KuwoLyricsSource(okHttpClient) }

    private val cache: MutableMap<Long, LyricsResult> = Collections.synchronizedMap(
        object : LinkedHashMap<Long, LyricsResult>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LyricsResult>?) = size > MAX_CACHE_SIZE
        }
    )

    private val networkSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

    override suspend fun fetchSync(songId: Long, title: String, artist: String?, album: String?): LyricsResult? =
        safeCall("LyricsFetcher", "fetchSync") {
            cache.get(songId)?.let { return@safeCall it }

            val cleanTitle = SearchMatch.extractBookTitle(title)
            val cleanArtist = SearchMatch.cleanOptional(artist)
            val cleanAlbum = SearchMatch.cleanOptional(album)

            var plainFallback: LyricsResult? = null

            // 优先级：QQ → 网易 → lrclib → 其它
            fun accept(result: LyricsResult?, source: String = ""): LyricsResult? {
                if (result == null) return null
                val tagged = if (source.isNotEmpty() && result.source.isEmpty()) {
                    result.copy(source = source)
                } else {
                    result
                }
                if (tagged.syncedLines.isNotEmpty()) {
                    cache.put(songId, tagged)
                    return tagged
                }
                if (!tagged.plainText.isNullOrBlank() && plainFallback == null) {
                    plainFallback = tagged
                }
                return null
            }

            accept(tryQQMusic(title, cleanArtist), "qq")?.let { return@safeCall it }
            accept(tryNetease(title, cleanArtist, cleanAlbum), "netease")?.let { return@safeCall it }
            accept(tryLrclibExact(cleanTitle, cleanArtist, cleanAlbum), "lrclib")?.let { return@safeCall it }
            accept(search(cleanTitle, cleanArtist), "lrclib")?.let { return@safeCall it }
            accept(tryRelaxed(title))?.let { return@safeCall it }
            accept(tryKugou(title, cleanArtist, cleanAlbum), "kugou")?.let { return@safeCall it }
            accept(tryKuwo(title, cleanArtist, cleanAlbum), "kuwo")?.let { return@safeCall it }
            accept(tryLyricsOvh(cleanTitle, cleanArtist), "ovh")?.let { return@safeCall it }

            plainFallback?.also { cache.put(songId, it) }
        }

    private suspend fun tryLrclibExact(title: String, artist: String?, album: String?): LyricsResult? = safeCall("LyricsFetcher", "tryLrclibExact") {
        networkSemaphore.withPermit {
            val params = mutableListOf("track_name=${URLEncoder.encode(title, "UTF-8")}")
            if (!artist.isNullOrBlank()) params.add("artist_name=${URLEncoder.encode(artist, "UTF-8")}")
            if (!album.isNullOrBlank()) params.add("album_name=${URLEncoder.encode(album, "UTF-8")}")

            val url = "$BASE_URL/get?${params.joinToString("&")}"
            val response = okHttpClient.safeGet("LyricsFetcher", url) ?: return@withPermit null
            if (!response.isSuccessful) return@withPermit null

            val json = JSONObject(response.body)
            val result = parseResult(json)
            if (!SearchMatch.isArtistAcceptable(artist, result.artistName)) return@withPermit null
            result
        }
    }

    private suspend fun tryNetease(title: String, artist: String?, album: String?): LyricsResult? = safeCall("LyricsFetcher", "tryNetease") {
        networkSemaphore.withPermit {
            val source = NeteaseLyricsSource(okHttpClient)
            source.fetch(title, artist, album)
        }
    }

    private suspend fun tryQQMusic(title: String, artist: String?): LyricsResult? = safeCall("LyricsFetcher", "tryQQMusic") {
        networkSemaphore.withPermit {
            // TODO: 非官方 API，待获取官方授权后替换
            val match = searchQQSong(title, artist) ?: return@withPermit null
            val rawLyrics = fetchQQLyrics(match.mid) ?: return@withPermit null
            val simplifiedLyrics = toSimplifiedText(rawLyrics)
            val parsedLines = LrcParser.parse(simplifiedLyrics)
            if (parsedLines.isEmpty()) return@withPermit null

            LyricsResult(
                id = null,
                trackName = match.title,
                artistName = match.artist,
                albumName = match.album,
                duration = 0.0,
                syncedLines = parsedLines,
                plainText = null,
                rawSyncedLyrics = simplifiedLyrics,
                source = "qq"
            )
        }
    }

    private data class QQSongMatch(
        val mid: String,
        val title: String,
        val artist: String,
        val album: String
    )

    private suspend fun tryRelaxed(title: String): LyricsResult? = safeCall("LyricsFetcher", "tryRelaxed") {
        networkSemaphore.withPermit {
            // 放宽匹配：QQ → 网易 → 酷狗 → 酷我
            val match = searchQQSong(title, null)
            if (match != null) {
                val raw = fetchQQLyrics(match.mid)
                if (raw != null) {
                    val simplified = toSimplifiedText(raw)
                    val lines = LrcParser.parse(simplified)
                    if (lines.isNotEmpty()) {
                        return@withPermit LyricsResult(
                            id = null,
                            trackName = match.title,
                            artistName = match.artist,
                            albumName = match.album,
                            duration = 0.0,
                            syncedLines = lines,
                            plainText = null,
                            rawSyncedLyrics = simplified,
                            source = "qq"
                        )
                    }
                }
            }

            val netease = NeteaseLyricsSource(okHttpClient).fetch(title, null, null)
            if (netease != null && netease.syncedLines.isNotEmpty()) {
                return@withPermit netease.copy(source = "netease")
            }

            val kugouResult = kugouSource.fetch(title, null, null)
            if (kugouResult != null && kugouResult.syncedLines.isNotEmpty()) {
                return@withPermit kugouResult.copy(source = "kugou")
            }

            val kuwoResult = kuwoSource.fetch(title, null, null)
            if (kuwoResult != null && kuwoResult.syncedLines.isNotEmpty()) {
                return@withPermit kuwoResult.copy(source = "kuwo")
            }

            null
        }
    }

    private suspend fun searchQQSong(title: String, artist: String?): QQSongMatch? {
        val query = buildSearchQuery(title, artist)
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp" +
            "?p=1&n=5&w=$encodedQuery&format=json"
        val response = okHttpClient.safeGetWithReferer("LyricsFetcher", url, "https://y.qq.com/") ?: return null
        if (!response.isSuccessful) return null
        val songs = JSONObject(cleanJsonp(response.body))
            .takeIf { it.optInt("code", -1) == 0 }
            ?.optJSONObject("data")
            ?.optJSONObject("song")
            ?.optJSONArray("list")
            ?: return null
        return pickBestQQSong(songs, title, artist)
    }

    private fun pickBestQQSong(songs: org.json.JSONArray, title: String, artist: String?): QQSongMatch? {
        var best: Pair<QQSongMatch, Int>? = null
        for (index in 0 until songs.length()) {
            val candidate = parseQQSong(songs.getJSONObject(index)) ?: continue
            val score = SearchMatch.trackScore(title, artist, candidate.title, candidate.artist)
            if (best == null || score > best.second) {
                best = candidate to score
            }
        }
        val match = best ?: return null
        return match.first.takeIf {
            SearchMatch.isArtistAcceptable(artist, it.artist) &&
                (match.second >= SearchMatch.minimumAcceptableScore(artist) || SearchMatch.titleScore(title, it.title) >= 34)
        }
    }

    private fun parseQQSong(song: JSONObject): QQSongMatch? {
        val mid = song.optString("songmid", "").takeIf { it.isNotBlank() } ?: return null
        return QQSongMatch(
            mid = mid,
            title = song.optString("songname", ""),
            artist = parseQQArtists(song),
            album = song.optString("albumname", "")
        )
    }

    private fun parseQQArtists(song: JSONObject): String {
        val artists = song.optJSONArray("singer") ?: return ""
        return buildList {
            for (index in 0 until artists.length()) {
                add(artists.getJSONObject(index).optString("name", ""))
            }
        }.joinToString(" / ")
    }

    private suspend fun fetchQQLyrics(songMid: String): String? {
        // TODO: 非官方 API，待获取官方授权后替换
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
            "?songmid=$songMid&g_tk=5381&loginUin=0&hostUin=0&format=json" +
            "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
        val response = okHttpClient.safeGetWithReferer("LyricsFetcher", url, "https://y.qq.com/") ?: return null
        if (!response.isSuccessful) return null
        val base64Lyrics = JSONObject(cleanJsonp(response.body))
            .takeIf { it.optInt("code", -1) == 0 }
            ?.optString("lyric", "")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val decoded = android.util.Base64.decode(base64Lyrics, android.util.Base64.DEFAULT)
        return String(decoded, Charsets.UTF_8)
    }

    private fun buildSearchQuery(title: String, artist: String?): String {
        val cleanArtist = SearchMatch.cleanOptional(artist)
        return if (cleanArtist == null) title else "$title $cleanArtist"
    }

    private fun cleanJsonp(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }

    private suspend fun search(query: String, artist: String?): LyricsResult? = safeCall("LyricsFetcher", "search") {
        val searchQuery = if (!artist.isNullOrBlank()) "$query $artist" else query

        val response = networkSemaphore.withPermit {
            val url = "$BASE_URL/search?q=${URLEncoder.encode(searchQuery, "UTF-8")}"
            val resp = okHttpClient.safeGet("LyricsFetcher", url) ?: return@withPermit null
            if (!resp.isSuccessful) return@withPermit null
            resp.body
        } ?: return@safeCall null

        val arr = org.json.JSONArray(response)
        if (arr.length() == 0) return@safeCall null

        var bestSynced: Pair<LyricsResult, Int>? = null
        var bestPlain: Pair<LyricsResult, Int>? = null
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val result = parseResult(obj)
            val score = SearchMatch.trackScore(query, artist, result.trackName, result.artistName)
            if (score < SearchMatch.minimumAcceptableScore(artist)) continue
            if (!SearchMatch.isArtistAcceptable(artist, result.artistName)) continue

            if (result.syncedLines.isNotEmpty()) {
                if (bestSynced == null || score > bestSynced.second) {
                    bestSynced = result to score
                }
            } else if (!result.plainText.isNullOrBlank()) {
                if (bestPlain == null || score > bestPlain.second) {
                    bestPlain = result to score
                }
            }
        }

        bestSynced?.first ?: bestPlain?.first
    }

    private fun parseResult(json: JSONObject): LyricsResult {
        var syncedLyrics = json.optNullableString("syncedLyrics")
        var plainLyrics = json.optNullableString("plainLyrics")

        syncedLyrics = syncedLyrics?.let { toSimplified(it) }
        plainLyrics = plainLyrics?.let { toSimplified(it) }

        val syncedLines = if (!syncedLyrics.isNullOrBlank()) {
            LrcParser.parse(syncedLyrics)
        } else {
            emptyList()
        }

        return LyricsResult(
            id = json.optLong("id", -1).takeIf { it >= 0 },
            trackName = json.optString("name", json.optString("trackName", "")).let { toSimplified(it) },
            artistName = json.optString("artistName", "").let { toSimplified(it) },
            albumName = json.optNullableString("albumName")?.let { toSimplified(it) },
            duration = json.optDouble("duration", 0.0),
            syncedLines = syncedLines,
            plainText = plainLyrics?.takeIf { it.isNotBlank() },
            rawSyncedLyrics = syncedLyrics?.takeIf { it.isNotBlank() },
            source = "lrclib"
        )
    }

    private fun toSimplified(text: String): String = toSimplifiedText(text)

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    fun getRawSyncedLyrics(songId: Long): String? {
        val result = cache.get(songId) ?: return null
        return result.rawSyncedLyrics
    }

    fun getCachedResult(songId: Long): LyricsResult? = cache.get(songId)

    override fun restoreToCache(songId: Long, result: LyricsResult) {
        if (cache.get(songId) == null) {
            cache.put(songId, result)
        }
    }

    override fun clearCache() {
        cache.clear()
    }

    override fun clearCache(songId: Long) {
        cache.remove(songId)
    }

    override suspend fun searchCandidates(
        title: String,
        artist: String?,
        album: String?,
        maxResults: Int
    ): List<LyricsResult> = safeCall("LyricsFetcher", "searchCandidates") {
        val cleanTitle = SearchMatch.extractBookTitle(title)
        val cleanArtist = SearchMatch.cleanOptional(artist)
        val cleanAlbum = SearchMatch.cleanOptional(album)
        val out = linkedMapOf<String, LyricsResult>()

        fun keyOf(r: LyricsResult) =
            "${r.source}|${r.trackName.trim().lowercase()}|${r.artistName.trim().lowercase()}|${r.rawSyncedLyrics?.take(64) ?: r.plainText?.take(64) ?: ""}"

        fun add(r: LyricsResult?) {
            if (r == null) return
            if (r.syncedLines.isEmpty() && r.plainText.isNullOrBlank()) return
            out.putIfAbsent(keyOf(r), r)
        }

        // 优先采集 QQ / 网易，再 lrclib 多结果与其它源
        add(tryQQMusic(title, cleanArtist)?.copy(source = "qq"))
        add(tryNetease(title, cleanArtist, cleanAlbum)?.copy(source = "netease"))
        searchAll(cleanTitle, cleanArtist).forEach { add(it.copy(source = "lrclib")) }
        add(tryLrclibExact(cleanTitle, cleanArtist, cleanAlbum)?.copy(source = "lrclib"))
        add(tryKugou(title, cleanArtist, cleanAlbum)?.copy(source = "kugou"))
        add(tryKuwo(title, cleanArtist, cleanAlbum)?.copy(source = "kuwo"))

        fun sourceRank(source: String): Int = when (source) {
            "qq" -> 3
            "netease" -> 2
            "lrclib" -> 1
            else -> 0
        }

        // 源优先级 → 同步歌词 → 曲名匹配分
        out.values
            .sortedWith(
                compareByDescending<LyricsResult> { sourceRank(it.source) }
                    .thenByDescending { it.syncedLines.isNotEmpty() }
                    .thenByDescending {
                        SearchMatch.trackScore(cleanTitle, cleanArtist, it.trackName, it.artistName)
                    }
            )
            .take(maxResults.coerceAtLeast(1))
    } ?: emptyList()

    private suspend fun searchAll(query: String, artist: String?): List<LyricsResult> =
        safeCall("LyricsFetcher", "searchAll") {
            val searchQuery = if (!artist.isNullOrBlank()) "$query $artist" else query
            val response = networkSemaphore.withPermit {
                val url = "$BASE_URL/search?q=${URLEncoder.encode(searchQuery, "UTF-8")}"
                val resp = okHttpClient.safeGet("LyricsFetcher", url) ?: return@withPermit null
                if (!resp.isSuccessful) return@withPermit null
                resp.body
            } ?: return@safeCall emptyList()

            val arr = org.json.JSONArray(response)
            val list = ArrayList<LyricsResult>(arr.length().coerceAtMost(20))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val result = parseResult(obj)
                val score = SearchMatch.trackScore(query, artist, result.trackName, result.artistName)
                if (score < SearchMatch.minimumAcceptableScore(artist)) continue
                if (!SearchMatch.isArtistAcceptable(artist, result.artistName)) continue
                if (result.syncedLines.isEmpty() && result.plainText.isNullOrBlank()) continue
                list.add(result)
            }
            list
        } ?: emptyList()

    private suspend fun tryKugou(title: String, artist: String?, album: String?): LyricsResult? = safeCall("LyricsFetcher", "tryKugou") {
        networkSemaphore.withPermit {
            kugouSource.fetch(title, artist, album)
        }
    }

    private suspend fun tryKuwo(title: String, artist: String?, album: String?): LyricsResult? = safeCall("LyricsFetcher", "tryKuwo") {
        networkSemaphore.withPermit {
            kuwoSource.fetch(title, artist, album)
        }
    }

    private suspend fun tryLyricsOvh(title: String, artist: String?): LyricsResult? = safeCall("LyricsFetcher", "tryLyricsOvh") {
        if (artist.isNullOrBlank()) return@safeCall null
        networkSemaphore.withPermit {
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val url = "https://api.lyrics.ovh/v1/$encArtist/$encTitle"
            val response = okHttpClient.safeGet("LyricsFetcher", url) ?: return@withPermit null
            if (!response.isSuccessful) return@withPermit null
            val json = JSONObject(response.body)
            val plain = json.optString("lyrics", "").takeIf { it.isNotBlank() } ?: return@withPermit null
            val syncedLines = LrcParser.parse(plain)
            LyricsResult(
                id = null,
                trackName = title,
                artistName = artist,
                albumName = null,
                duration = 0.0,
                syncedLines = syncedLines,
                plainText = if (syncedLines.isEmpty()) plain else null,
                rawSyncedLyrics = if (syncedLines.isNotEmpty()) plain else null
            )
        }
    }
}
