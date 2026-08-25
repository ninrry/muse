package luzzr.muse.data.network

import luzzr.muse.domain.metadata.MetadataSearchClient
import luzzr.muse.domain.model.MetadataResult
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class MetadataFetcher(
    private val okHttpClient: OkHttpClient
) : MetadataSearchClient {

    data class SanitizedQuery(
        val title: String,
        val artist: String?
    )

    fun sanitizeQuery(rawTitle: String, rawArtist: String? = null): SanitizedQuery {
        var title = SearchMatch.extractBookTitle(rawTitle)
        val suppliedArtist = SearchMatch.cleanOptional(rawArtist)
        val extractedArtist = mutableListOf<String>()

        title = title.replace(Regex("\\.(mp3|flac|ogg|oga|opus|m4a|m4b|alac|wav)$", RegexOption.IGNORE_CASE), "")
        title = title.replace(Regex("^\\s*(?:cd\\s*)?\\d{1,3}[._\\-\\s]+", RegexOption.IGNORE_CASE), "")
        title = title.replace(Regex("(_哔哩哔哩|_bilibili|_YouTube|_youtube|_niconico)$", RegexOption.IGNORE_CASE), "")
        title = title.replace(Regex("[-–—·\\s]*(哔哩哔哩|bilibili|YouTube|youtube|niconico)$", RegexOption.IGNORE_CASE), "")

        val bracketNoise = Regex(
            "【[^】]*(?:4K|HD|超清|高清|无损|高音质|官方|MV|字幕)[^】]*】|" +
                "\\[[^\\]]*(?:4K|HD|Official|MV|Audio|Lyrics?|高清|无损)[^\\]]*]",
            RegexOption.IGNORE_CASE
        )
        title = title.replace(bracketNoise, "")

        val qualityPattern = "\\s*(4K|HD|超清|高清|无损|高音质|超高清|完美音质)\\s*"
        title = title.replace(Regex(qualityPattern, RegexOption.IGNORE_CASE), " ")

        val suffixPattern = "\\s*\\((Official Audio|Official|MV|Audio|Audio Video|Lyrics|" +
            "Lyric Video|Official Music Video|Official Video|Visualizer)\\)\\s*"
        title = title.replace(Regex(suffixPattern, RegexOption.IGNORE_CASE), " ")

        val dashSplit = title.split(Regex("\\s*[-–—·]\\s*"))
        if (dashSplit.size >= 2) {
            val first = dashSplit[0].trim()
            val last = dashSplit.last().trim()
            if (suppliedArtist != null) {
                when {
                    SearchMatch.artistScore(suppliedArtist, first) >= 32 ->
                        title = dashSplit.drop(1).joinToString(" - ").trim()
                    SearchMatch.artistScore(suppliedArtist, last) >= 32 ->
                        title = dashSplit.dropLast(1).joinToString(" - ").trim()
                }
            } else if (first.length <= 40 && first.matches(Regex("^[\\p{L}\\p{N}\\s.&'’]+$"))) {
                extractedArtist.add(first)
                title = dashSplit.drop(1).joinToString(" - ").trim()
            } else if (last.length <= 40 && last.matches(Regex("^[\\p{L}\\p{N}\\s.&'’]+$"))) {
                extractedArtist.add(last)
                title = dashSplit.dropLast(1).joinToString(" - ").trim()
            }
        }

        title = title.replace(Regex("\\s+"), " ").trim()

        val artist = suppliedArtist ?: extractedArtist.firstOrNull()
        return SanitizedQuery(title = title, artist = artist)
    }

    override suspend fun search(rawTitle: String, rawArtist: String?, rawAlbum: String?, maxResults: Int): List<MetadataResult> {
        val sanitized = sanitizeQuery(rawTitle, rawArtist)
        val title = sanitized.title
        val artist = sanitized.artist
        val album = SearchMatch.cleanOptional(rawAlbum)

        return coroutineScope {
            val mbDeferred = async {
                withTimeoutOrNull(OVERSEAS_SEARCH_TIMEOUT_MS) {
                    safeCall("MetadataFetcher", "search MusicBrainz") {
                        searchMusicBrainz(title, artist, album)
                    }
                } ?: emptyList()
            }

            val neDeferred = async {
                safeCall("MetadataFetcher", "search Netease") {
                    searchNetease(title, artist, maxResults)
                } ?: emptyList()
            }

            val itDeferred = async {
                withTimeoutOrNull(ITUNES_SEARCH_TIMEOUT_MS) {
                    safeCall("MetadataFetcher", "search iTunes") {
                        searchITunes(title, artist, maxResults)
                    }
                } ?: emptyList()
            }

            val dzDeferred = async {
                withTimeoutOrNull(OVERSEAS_SEARCH_TIMEOUT_MS) {
                    safeCall("MetadataFetcher", "search Deezer") {
                        searchDeezer(title, artist, maxResults)
                    }
                } ?: emptyList()
            }

            val qqDeferred = async {
                safeCall("MetadataFetcher", "search QQMusic") {
                    searchQQMusic(title, artist, maxResults)
                } ?: emptyList()
            }

            val results = mbDeferred.await() + neDeferred.await() + itDeferred.await() + dzDeferred.await() + qqDeferred.await()
            mergeAndRankResults(results, title, artist, album, maxResults)
        }
    }

    override suspend fun searchExact(title: String, artist: String?, maxResults: Int): List<MetadataResult> {
        val cleanTitle = SearchMatch.extractBookTitle(title)

        return coroutineScope {
            val mbDeferred = async {
                withTimeoutOrNull(OVERSEAS_SEARCH_TIMEOUT_MS) {
                    safeCall("MetadataFetcher", "searchExact MusicBrainz") {
                        searchMusicBrainz(cleanTitle, artist, queryAlbum = null)
                    }
                } ?: emptyList()
            }

            val neDeferred = async {
                safeCall("MetadataFetcher", "searchExact Netease") {
                    searchNetease(cleanTitle, artist, maxResults)
                } ?: emptyList()
            }

            val itDeferred = async {
                withTimeoutOrNull(ITUNES_SEARCH_TIMEOUT_MS) {
                    safeCall("MetadataFetcher", "searchExact iTunes") {
                        searchITunes(cleanTitle, artist, maxResults)
                    }
                } ?: emptyList()
            }

            val dzDeferred = async {
                withTimeoutOrNull(OVERSEAS_SEARCH_TIMEOUT_MS) {
                    safeCall("MetadataFetcher", "searchExact Deezer") {
                        searchDeezer(cleanTitle, artist, maxResults)
                    }
                } ?: emptyList()
            }

            val qqDeferred = async {
                safeCall("MetadataFetcher", "searchExact QQMusic") {
                    searchQQMusic(cleanTitle, artist, maxResults)
                } ?: emptyList()
            }

            val results = mbDeferred.await() + neDeferred.await() + itDeferred.await() + dzDeferred.await() + qqDeferred.await()
            mergeAndRankResults(
                results = results,
                queryTitle = cleanTitle,
                queryArtist = artist,
                queryAlbum = null,
                maxResults = maxResults
            )
        }
    }

    internal fun mergeAndRankResults(
        results: List<MetadataResult>,
        queryTitle: String,
        queryArtist: String?,
        queryAlbum: String?,
        maxResults: Int
    ): List<MetadataResult> {
        val safeCandidates = results.mapNotNull { result ->
            val titleScore = SearchMatch.titleScore(queryTitle, result.title)
            val artistAcceptable = SearchMatch.isArtistAcceptable(queryArtist, result.artist)
            if (titleScore < MIN_METADATA_TITLE_SCORE || !artistAcceptable) return@mapNotNull null
            result.copy(
                coverUrl = result.coverUrl.takeIf {
                    result.album.isNotBlank() &&
                        titleScore >= SAFE_COVER_TITLE_SCORE
                }
            )
        }
        val grouped = safeCandidates.groupBy {
            Triple(
                SearchMatch.normalize(it.title),
                SearchMatch.canonicalizeArtist(it.artist),
                SearchMatch.normalize(it.album)
            )
        }
        return grouped.map { (_, list) ->
            mergeResultGroup(list)
        }.map { result ->
            result.copy(
                score = SearchMatch.metadataQualityScore(
                    queryTitle = queryTitle,
                    queryArtist = queryArtist,
                    queryAlbum = queryAlbum,
                    candidateTitle = result.title,
                    candidateArtist = result.artist,
                    candidateAlbum = result.album,
                    sourceScore = result.score,
                    hasCover = !result.coverUrl.isNullOrBlank(),
                    hasYear = result.year != null
                )
            )
        }.filter { result ->
            SearchMatch.titleScore(queryTitle, result.title) >= MIN_METADATA_TITLE_SCORE &&
                SearchMatch.isArtistAcceptable(queryArtist, result.artist)
        }.sortedWith(
            compareByDescending<MetadataResult> { it.score }
                .thenBy { sourceRank(it.source) }
                .thenBy { it.title.length }
        ).map { it.toSimplifiedChinese() }
            .take(maxResults)
    }

    private fun mergeResultGroup(list: List<MetadataResult>): MetadataResult {
        val best = list.maxWithOrNull(
            compareBy<MetadataResult> { it.score }
                .thenBy { metadataCompletenessScore(it) }
                .thenBy { sourcePreferenceScore(it.source) }
        ) ?: list.first()
        val enrichedCandidates = list.sortedWith(
            compareByDescending<MetadataResult> { metadataCompletenessScore(it) }
                .thenBy { sourceRank(it.source) }
                .thenByDescending { it.score }
        )
        return best.copy(
            album = best.album.ifBlank { enrichedCandidates.firstOrNull { it.album.isNotBlank() }?.album.orEmpty() },
            year = best.year ?: enrichedCandidates.firstOrNull { it.year != null }?.year,
            genre = best.genre.ifBlank { enrichedCandidates.firstOrNull { it.genre.isNotBlank() }?.genre.orEmpty() },
            coverUrl = best.coverUrl ?: enrichedCandidates.firstOrNull { candidate ->
                !candidate.coverUrl.isNullOrBlank() && (
                    best.album.isBlank() ||
                        candidate.album.isBlank() ||
                        SearchMatch.normalize(candidate.album) == SearchMatch.normalize(best.album)
                )
            }?.coverUrl
        )
    }

    private fun metadataCompletenessScore(result: MetadataResult): Int {
        return (if (!result.coverUrl.isNullOrBlank()) 5 else 0) +
            (if (result.album.isNotBlank()) 2 else 0) +
            (if (result.year != null) 1 else 0) +
            (if (result.genre.isNotBlank()) 1 else 0)
    }

    private fun sourcePreferenceScore(source: String): Int = 10 - sourceRank(source)

    private suspend fun searchMusicBrainz(title: String, artist: String?, queryAlbum: String?): List<MetadataResult> {
        val query = buildString {
            append("recording:\"${escapeQuery(title)}\"")
            val cleanArtist = SearchMatch.cleanOptional(artist)
            if (cleanArtist != null) append(" AND artist:\"${escapeQuery(cleanArtist)}\"")
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://musicbrainz.org/ws/2/recording/?query=$encodedQuery&fmt=json&limit=10"
        val response = okHttpClient.safeGet(
            "MetadataFetcher", url,
            headers = mapOf("User-Agent" to "Muse/1.0 ( luzzr.muse )")
        ) ?: return emptyList()
        if (!response.isSuccessful) return emptyList()

        val json = JSONObject(response.body)
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

            val release = bestMusicBrainzRelease(rec.optJSONArray("releases"), queryAlbum)
            val album = release?.title.orEmpty()
            val year = release?.year

            results.add(
                MetadataResult(
                    title = recTitle,
                    artist = recArtist,
                    album = album,
                    year = year,
                    coverUrl = release?.coverUrl,
                    source = "MusicBrainz",
                    score = ((score.coerceIn(0, 100) * 0.50f) + (matchScore * 0.42f) + providerRankBonus(i)).toInt().coerceIn(0, 100)
                )
            )
        }
        return results
    }

    private data class MusicBrainzRelease(
        val title: String,
        val year: Int?,
        val coverUrl: String?
    )

    private fun bestMusicBrainzRelease(releases: JSONArray?, queryAlbum: String?): MusicBrainzRelease? {
        if (releases == null || releases.length() == 0) return null
        val candidates = buildList {
            for (index in 0 until releases.length()) {
                val release = releases.getJSONObject(index)
                val releaseId = release.optString("id", "")
                val title = release.optString("title", "")
                val dateStr = release.optString("date", "")
                val year = dateStr.take(4).toIntOrNull()
                val coverUrl = coverArtArchiveUrl(releaseId, release.optJSONObject("cover-art-archive"))
                val primaryType = release.optJSONObject("release-group")?.optString("primary-type", "").orEmpty()
                val isCompilation = primaryType.equals("Compilation", ignoreCase = true)
                val status = release.optString("status", "")
                val relScore = (if (coverUrl != null) 4 else 0) +
                    (if (primaryType.equals("Album", ignoreCase = true)) 6 else if (primaryType.equals("Single", ignoreCase = true)) 4 else 0) +
                    (if (isCompilation) -6 else 0) +
                    (if (status.equals("Official", ignoreCase = true)) 3 else 0) +
                    SearchMatch.albumPreferenceScore(queryAlbum, title) +
                    providerRankBonus(index)
                add(Triple(relScore, index, MusicBrainzRelease(title, year, coverUrl)))
            }
        }
        return candidates.sortedWith(
            compareByDescending<Triple<Int, Int, MusicBrainzRelease>> { it.first }
                .thenBy { it.second }
        ).firstOrNull()?.third
    }

    private fun coverArtArchiveUrl(releaseId: String, coverArchive: JSONObject?): String? {
        if (releaseId.isBlank()) return null
        if (coverArchive?.optBoolean("front", false) != true) return null
        return "https://coverartarchive.org/release/$releaseId/front-500"
    }

    private suspend fun searchDeezer(title: String, artist: String?, limit: Int): List<MetadataResult> {
        val query = buildString {
            append(title)
            val cleanArtist = SearchMatch.cleanOptional(artist)
            if (cleanArtist != null) append(" ${cleanArtist.take(20)}")
        }
        val url = "https://api.deezer.com/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=$limit&order=RANKING"
        val response = okHttpClient.safeGet("MetadataFetcher", url) ?: return emptyList()
        if (!response.isSuccessful) return emptyList()

        val json = JSONObject(response.body)
        val data = json.optJSONArray("data") ?: JSONArray()

        val results = mutableListOf<MetadataResult>()
        for (i in 0 until data.length()) {
            val track = data.getJSONObject(i)
            val trackTitle = track.optString("title", "")
            val trackArtist = track.optJSONObject("artist")?.optString("name", "") ?: ""
            val trackAlbum = track.optJSONObject("album")?.optString("title", "") ?: ""
            val rawCoverUrl = track.optJSONObject("album")?.optString("cover_xl", "").orEmpty()
                .ifBlank { track.optJSONObject("album")?.optString("cover_big", "").orEmpty() }
                .ifBlank { track.optJSONObject("album")?.optString("cover_medium", "").orEmpty() }
            val coverUrl = if (rawCoverUrl.startsWith("http://")) "https://" + rawCoverUrl.substring(7) else rawCoverUrl
            val explicit = track.optInt("explicit_lyrics", 0)
            val matchScore = SearchMatch.trackScore(title, artist, trackTitle, trackArtist)
            if (matchScore < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, trackTitle) < 34) continue
            val sourceBonus = providerRankBonus(i) + (if (explicit > 0) 2 else 0) + (if (coverUrl.isNotBlank()) 3 else 0)

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

    private suspend fun searchNetease(title: String, artist: String?, limit: Int): List<MetadataResult> {
        val query = buildSearchQuery(title, artist)
        val postBody = "s=${URLEncoder.encode(query, "UTF-8")}&type=1&offset=0&limit=$limit"
        val response = okHttpClient.safePost(
            "MetadataFetcher", "https://music.163.com/api/cloudsearch/pc", postBody,
            headers = mapOf(
                "Referer" to "https://music.163.com",
                "User-Agent" to MOBILE_USER_AGENT
            )
        ) ?: return emptyList()
        if (!response.isSuccessful) return emptyList()
        return parseNeteaseResults(response.body, title, artist)
    }

    private fun parseNeteaseResults(response: String, title: String, artist: String?): List<MetadataResult> {
        val json = JSONObject(response)
        if (json.optInt("code", -1) != 200) return emptyList()
        val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        return buildList {
            for (index in 0 until songs.length()) {
                parseNeteaseTrack(songs.getJSONObject(index), title, artist, index)?.let(::add)
            }
        }
    }

    private fun parseNeteaseTrack(song: JSONObject, title: String, artist: String?, index: Int): MetadataResult? {
        val trackTitle = song.optString("name", "")
        val trackArtist = parseArtistNames(song.optJSONArray("ar"))
        val matchScore = SearchMatch.trackScore(title, artist, trackTitle, trackArtist)
        if (!isAcceptableMatch(title, artist, trackTitle, matchScore)) return null
        val album = song.optJSONObject("al")
        val coverUrl = neteaseCoverUrl(album?.optString("picUrl", "").orEmpty())
        return MetadataResult(
            title = trackTitle,
            artist = trackArtist,
            album = album?.optString("name", "").orEmpty(),
            coverUrl = coverUrl.takeIf(String::isNotBlank),
            source = "Netease",
            score = (matchScore + providerRankBonus(index) + if (coverUrl.isNotBlank()) 3 else 0).coerceIn(0, 100)
        )
    }

    private suspend fun searchITunes(title: String, artist: String?, limit: Int): List<MetadataResult> {
        val query = buildString {
            append(title)
            val cleanArtist = SearchMatch.cleanOptional(artist)
            if (cleanArtist != null) append(" $cleanArtist")
        }
        val results = mutableListOf<MetadataResult>()
        val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&media=music&limit=$limit"
        val response = okHttpClient.safeGet("MetadataFetcher", url) ?: return results
        if (!response.isSuccessful) return results

        val json = JSONObject(response.body)
        val data = json.optJSONArray("results") ?: JSONArray()
        for (i in 0 until data.length()) {
            val track = data.getJSONObject(i)
            val trackTitle = track.optString("trackName", "")
            val trackArtist = track.optString("artistName", "")
            val trackAlbum = track.optString("collectionName", "")
            var coverUrl = upgradeITunesArtwork(track.optString("artworkUrl100", ""))
            if (coverUrl.startsWith("http://")) coverUrl = "https://" + coverUrl.substring(7)
            val matchScore = SearchMatch.trackScore(title, artist, trackTitle, trackArtist)
            if (matchScore < SearchMatch.minimumAcceptableScore(artist) && SearchMatch.titleScore(title, trackTitle) < 34) continue

            results.add(
                MetadataResult(
                    title = trackTitle,
                    artist = trackArtist,
                    album = trackAlbum,
                    year = track.optString("releaseDate", "").take(4).toIntOrNull(),
                    coverUrl = if (coverUrl.isNotBlank()) coverUrl else null,
                    source = "iTunes",
                    score = (matchScore + providerRankBonus(i) + if (coverUrl.isNotBlank()) 3 else 0).coerceIn(0, 100)
                )
            )
        }
        return results
    }

    private suspend fun searchQQMusic(title: String, artist: String?, limit: Int): List<MetadataResult> {
        // 非官方 API；获得官方授权后应替换。
        val query = buildSearchQuery(title, artist)
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp" +
            "?p=1&n=$limit&w=$encodedQuery&format=json"
        val response = okHttpClient.safeGetWithReferer("MetadataFetcher", url, "https://y.qq.com/") ?: return emptyList()
        if (!response.isSuccessful) return emptyList()
        return parseQQMusicResults(response.body, title, artist)
    }

    private fun parseQQMusicResults(response: String, title: String, artist: String?): List<MetadataResult> {
        val json = JSONObject(cleanJsonp(response))
        if (json.optInt("code", -1) != 0) return emptyList()
        val songs = json.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return emptyList()
        return buildList {
            for (index in 0 until songs.length()) {
                parseQQMusicTrack(songs.getJSONObject(index), title, artist, index)?.let(::add)
            }
        }
    }

    private fun parseQQMusicTrack(track: JSONObject, title: String, artist: String?, index: Int): MetadataResult? {
        val trackTitle = track.optString("songname", "")
        val trackArtist = parseArtistNames(track.optJSONArray("singer"))
        val matchScore = SearchMatch.trackScore(title, artist, trackTitle, trackArtist)
        if (!isAcceptableMatch(title, artist, trackTitle, matchScore)) return null
        val albumMid = track.optString("albummid", "")
        val coverUrl = albumMid.takeIf(String::isNotBlank)
            ?.let { "https://y.gtimg.cn/music/photo_new/T002R800x800M000$it.jpg" }
        return MetadataResult(
            title = trackTitle,
            artist = trackArtist,
            album = track.optString("albumname", ""),
            year = qqPublicationYear(track.optLong("pubtime", 0L)),
            coverUrl = coverUrl,
            source = "QQMusic",
            score = (matchScore + providerRankBonus(index) + if (coverUrl != null) 3 else 0).coerceIn(0, 100)
        )
    }

    private fun parseArtistNames(artists: JSONArray?): String {
        if (artists == null) return ""
        return buildList {
            for (index in 0 until artists.length()) {
                add(artists.getJSONObject(index).optString("name", ""))
            }
        }.joinToString(" / ")
    }

    private fun isAcceptableMatch(queryTitle: String, queryArtist: String?, trackTitle: String, matchScore: Int): Boolean {
        return matchScore >= SearchMatch.minimumAcceptableScore(queryArtist) ||
            SearchMatch.titleScore(queryTitle, trackTitle) >= 34
    }

    private fun qqPublicationYear(publicationTimeSeconds: Long): Int? {
        if (publicationTimeSeconds <= 0L) return null
        return Calendar.getInstance().run {
            timeInMillis = publicationTimeSeconds * 1000L
            get(Calendar.YEAR)
        }
    }

    private fun buildSearchQuery(title: String, artist: String?): String {
        val cleanArtist = SearchMatch.cleanOptional(artist)
        return if (cleanArtist == null) title else "$title $cleanArtist"
    }

    private fun secureUrl(url: String): String {
        return if (url.startsWith("http://")) "https://${url.substring(7)}" else url
    }

    internal fun neteaseCoverUrl(rawUrl: String): String {
        val secure = secureUrl(rawUrl)
        if (secure.isBlank()) return ""
        if ("param=" in secure) return secure
        val separator = if ('?' in secure) '&' else '?'
        return "$secure${separator}param=800y800"
    }

    private fun upgradeITunesArtwork(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return rawUrl
            .replace(Regex("/\\d+x\\d+bb\\."), "/600x600bb.")
            .replace(Regex("/\\d+x\\d+\\."), "/600x600.")
    }

    private fun providerRankBonus(index: Int): Int {
        return (10 - index).coerceIn(0, 10)
    }

    private fun sourceRank(source: String): Int {
        return when (source) {
            "Netease" -> 0
            "QQMusic" -> 1
            "iTunes" -> 2
            "Deezer" -> 3
            "MusicBrainz" -> 4
            else -> 9
        }
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

    companion object {
        private const val MIN_METADATA_TITLE_SCORE = 42
        private const val SAFE_COVER_TITLE_SCORE = 54
        private const val OVERSEAS_SEARCH_TIMEOUT_MS = 2_500L
        private const val ITUNES_SEARCH_TIMEOUT_MS = 3_500L
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private fun escapeQuery(s: String): String {
            return s.replace("\"", "\\\"")
                .replace("(", "\\\\(")
                .replace(")", "\\\\)")
        }
    }
}
