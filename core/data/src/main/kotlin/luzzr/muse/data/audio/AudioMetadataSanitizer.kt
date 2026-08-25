package luzzr.muse.data.audio

import java.util.Locale

/**
 * Sanitizes raw metadata extracted from MediaStore or audio tags:
 * - Strips unwanted noise patterns (e.g. [1080P], (Official Video), bitrate tags)
 * - Detects and splits "Artist - Title" or "Title - Artist" combinations
 * - Protects hyphenated artist names (e.g. "T-ara", "AC-DC", "Jay-Z")
 * - Recognizes known artists and song version markers to prevent inversion
 * - Recovers GBK/GB18030 Mojibake in legacy ID3 tags
 * - Normalizes "Unknown Artist" and "Unknown Album"
 */
object AudioMetadataSanitizer {

    data class SanitizedMetadata(
        val title: String,
        val artist: String,
        val album: String
    )

    private val NOISE_PATTERNS = listOf(
        Regex("\\[(1080[pP]|720[pP]|4[kK]|HD|HQ|MV|FLAC|APE|WAV|320[kK]|Hi-Res|DSD|DFF|DSF)[^\\]]*\\]", RegexOption.IGNORE_CASE),
        Regex("【(1080[pP]|720[pP]|4[kK]|HD|HQ|MV|无损|高音质|官方|原版|首发)[^】]*】", RegexOption.IGNORE_CASE),
        Regex("_(1080[pP]|720[pP]|4[kK]|HD|HQ|MV)$", RegexOption.IGNORE_CASE),
        Regex("_(128|192|256|320)[kK]$", RegexOption.IGNORE_CASE),
        Regex("_(320kbps|flac|ape|wav|aac|dsd)$", RegexOption.IGNORE_CASE),
        Regex("_(bilibili|douyin|kugou|kuwo|netease|qqmusic|哔哩哔哩|抖音|酷狗|酷我|网易云|QQ音乐)", RegexOption.IGNORE_CASE),
        Regex("\\[(mqms2|kg_hash|kw_hash)[^\\]]*\\]", RegexOption.IGNORE_CASE)
    )

    private val TRACK_NUMBER_PREFIX_REGEX = Regex("^\\d{1,3}[.\\-\\s_]+\\s*")

    private val UNKNOWN_ARTIST_MARKERS = setOf(
        "<unknown>", "unknown", "unknown artist", "未知艺术家", "未知歌手",
        "群星", "various artists", "various", "群星合辑", "null", ""
    )

    private val UNKNOWN_ALBUM_MARKERS = setOf(
        "<unknown>", "unknown", "unknown album", "未知专辑", "未知唱片", "null", ""
    )

    private val VERSION_MARKER_REGEX = Regex(
        "[(\\[（【][^)\\]）】]*(Live|伴奏|Instrumental|Remix|Cover|Acoustic|Piano|Guitar|feat|ft\\.|with|官方|无损|高音质|原版)[^)\\]）】]*[)\\]）】]",
        RegexOption.IGNORE_CASE
    )

    /**
     * Curated dictionary of popular artists to disambiguate "Artist - Title" vs "Title - Artist".
     */
    private val KNOWN_ARTISTS = setOf(
        // Chinese Artists
        "周杰伦", "陈奕迅", "林俊杰", "张学友", "王菲", "邓紫棋", "薛之谦", "李荣浩", "华晨宇",
        "许嵩", "汪苏泷", "毛不易", "周深", "张杰", "蔡依林", "孙燕姿", "梁静茹", "五月天",
        "莫文蔚", "陶喆", "王力宏", "林宥嘉", "田馥甄", "张韶涵", "萧敬腾", "朴树", "许巍",
        "汪峰", "李健", "刀郎", "降央卓玛", "凤凰传奇", "Beyond", "信乐团", "苏打绿", "南拳妈妈",
        "F.I.R.", "S.H.E", "Twins", "羽泉", "水木年华", "筷子兄弟", "新裤子", "告五人", "草东没有派对",
        "房东的猫", "赵雷", "宋冬野", "马頔", "李志", "谢安琪", "容祖儿", "杨千嬅", "古巨基", "李克勤",
        "谭咏麟", "张国荣", "梅艳芳", "刘德华", "郭富城", "黎明", "罗大佑", "李宗盛", "齐秦", "童安格",
        // Western Artists
        "Taylor Swift", "Adele", "Ed Sheeran", "Coldplay", "Billie Eilish", "Justin Bieber",
        "Bruno Mars", "Eminem", "Rihanna", "Alan Walker", "Michael Jackson", "Queen",
        "The Beatles", "Linkin Park", "Avril Lavigne", "Imagine Dragons", "Katy Perry",
        "Lady Gaga", "Sia", "Maroon 5", "OneRepublic", "Charlie Puth", "Dua Lipa",
        "Ariana Grande", "The Weeknd", "Post Malone", "Drake", "Kendrick Lamar",
        "Beyonce", "Shawn Mendes", "Sam Smith", "Snoop Dogg", "Jay-Z", "AC/DC", "AC-DC", "T-ara",
        // Japanese & Korean Artists
        "YOASOBI", "米津玄师", "Aimer", "RADWIMPS", "LiSA", "宇多田光", "中岛美雪", "坂井泉水",
        "IU", "BLACKPINK", "BIGBANG", "EXO", "BTS", "TWICE", "NewJeans", "aespa", "IVE", "LE SSERAFIM"
    )

    private val KNOWN_ARTISTS_NORMALIZED = KNOWN_ARTISTS.associateBy { normalizeForMatching(it) }

    fun sanitize(
        rawTitle: String?,
        rawArtist: String?,
        rawAlbum: String? = null,
        fallbackFileName: String? = null
    ): SanitizedMetadata {
        var title = rawTitle?.trim().orEmpty()
        if (title.isBlank() && !fallbackFileName.isNullOrBlank()) {
            title = fallbackFileName.substringBeforeLast('.').trim()
        }

        var artist = rawArtist?.trim().orEmpty()
        val album = rawAlbum?.trim().orEmpty().let {
            if (isUnknownAlbum(it)) "Unknown Album" else it
        }

        val isArtistUnknown = isUnknownArtist(artist)

        // 0. Auto-recover GBK/GB18030 Mojibake if present
        title = fixMojibake(title)
        artist = fixMojibake(artist)

        // 1. Strip file extension if leaked into title
        title = title.replace(Regex("\\.(mp3|flac|ogg|oga|opus|m4a|m4b|alac|wav|aac|ape|dsf|dff|wma)$", RegexOption.IGNORE_CASE), "")

        // 2. Strip noise patterns
        for (pattern in NOISE_PATTERNS) {
            title = title.replace(pattern, "").trim()
        }

        // 3. Strip leading track number if followed by actual title content
        val withoutTrackNum = title.replace(TRACK_NUMBER_PREFIX_REGEX, "").trim()
        if (withoutTrackNum.isNotBlank()) {
            title = withoutTrackNum
        }

        // 4. Handle title-artist combined patterns
        val dashSplit = splitTitleArtist(title)

        if (dashSplit.size >= 2) {
            val first = dashSplit.first().trim()
            val last = dashSplit.last().trim()

            if (!isArtistUnknown) {
                // If artist is known from physical tags, check if it was prepended/appended to title
                when {
                    matchesArtist(last, artist) -> {
                        val candidate = dashSplit.dropLast(1).joinToString(" - ").trim()
                        if (candidate.isNotBlank()) title = candidate
                    }
                    matchesArtist(first, artist) -> {
                        val candidate = dashSplit.drop(1).joinToString(" - ").trim()
                        if (candidate.isNotBlank()) title = candidate
                    }
                }
            } else {
                // Artist is unknown, deduce from "Artist - Title" or "Title - Artist"
                if (dashSplit.size == 2 && first.isNotBlank() && last.isNotBlank()) {
                    val firstHasVersion = hasSongVersionMarkers(first)
                    val lastHasVersion = hasSongVersionMarkers(last)

                    val firstIsKnownArtist = isKnownArtistName(first)
                    val lastIsKnownArtist = isKnownArtistName(last)

                    when {
                        // Case A: Version markers identify Title side
                        firstHasVersion && !lastHasVersion -> {
                            title = first
                            artist = last
                        }
                        lastHasVersion && !firstHasVersion -> {
                            title = last
                            artist = first
                        }
                        // Case B: Known artist dictionary disambiguation
                        firstIsKnownArtist && !lastIsKnownArtist -> {
                            artist = resolveKnownArtistCanonicalName(first)
                            title = last
                        }
                        lastIsKnownArtist && !firstIsKnownArtist -> {
                            artist = resolveKnownArtistCanonicalName(last)
                            title = first
                        }
                        // Case C: Standard default (Artist - Title)
                        else -> {
                            artist = first
                            title = last
                        }
                    }
                }
            }
        } else if (!isArtistUnknown) {
            // Check direct prefix/suffix without spaces (e.g. "艺术家-歌名" or "歌名-艺术家")
            if (title.startsWith("$artist-", ignoreCase = true) && title.length > artist.length + 1) {
                title = title.substring(artist.length + 1).trim()
            } else if (title.endsWith("-$artist", ignoreCase = true) && title.length > artist.length + 1) {
                title = title.substring(0, title.length - artist.length - 1).trim()
            }
        }

        if (title.isBlank()) {
            title = fallbackFileName?.substringBeforeLast('.')?.trim().orEmpty().ifBlank { "Unknown Title" }
        }

        if (isUnknownArtist(artist)) {
            artist = "Unknown Artist"
        }

        return SanitizedMetadata(
            title = title,
            artist = artist,
            album = album.ifBlank { "Unknown Album" }
        )
    }

    /**
     * Splits a combined "Artist - Title" or "Title - Artist" string without breaking
     * intra-word hyphens in artist names like "T-ara", "AC-DC", "Jay-Z".
     */
    fun splitTitleArtist(input: String): List<String> {
        if (input.isBlank()) return emptyList()

        // 1. Spaced separators: " - ", " – ", " — ", " · ", " / "
        val spacedSplit = input.split(Regex("\\s+[-–—·/]\\s+|\\s*[—–·]\\s*")).map { it.trim() }.filter { it.isNotBlank() }
        if (spacedSplit.size >= 2) {
            return spacedSplit
        }

        // 2. Chinese full-width em-dash "——"
        if (input.contains("——")) {
            val emSplit = input.split("——").map { it.trim() }.filter { it.isNotBlank() }
            if (emSplit.size >= 2) return emSplit
        }

        // 3. Underscore separator: "歌名_歌手" or "歌手_歌名"
        if (input.contains("_")) {
            val underSplit = input.split(Regex("\\s*_\\s*")).map { it.trim() }.filter { it.isNotBlank() }
            if (underSplit.size >= 2) return underSplit
        }

        // 4. Hyphen between CJK characters (e.g. "晴天-周杰伦", "周杰伦-晴天")
        // Chinese/Japanese/Korean words never use hyphens internally, so hyphen is 100% a delimiter.
        val cjkHyphenRegex = Regex("([\\u4E00-\\u9FA5\\u3040-\\u30FF\\uAC00-\\uD7AF])\\s*-\\s*([\\u4E00-\\u9FA5\\u3040-\\u30FF\\uAC00-\\uD7AF])")
        if (cjkHyphenRegex.containsMatchIn(input)) {
            val parts = input.split(Regex("\\s*-\\s*")).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 2) return parts
        }

        // 5. Hyphen with space on single side (e.g. "晴天 -周杰伦" or "晴天- 周杰伦")
        val singleSpaceHyphenRegex = Regex("\\s+-[^\\s]|[^\\s]-\\s+")
        if (singleSpaceHyphenRegex.containsMatchIn(input)) {
            val parts = input.split(Regex("\\s*-\\s*")).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 2) return parts
        }

        return listOf(input.trim())
    }

    fun isUnknownArtist(artist: String?): Boolean {
        if (artist.isNullOrBlank()) return true
        val normalized = artist.trim().lowercase(Locale.ROOT)
        return normalized in UNKNOWN_ARTIST_MARKERS
    }

    fun isUnknownAlbum(album: String?): Boolean {
        if (album.isNullOrBlank()) return true
        val normalized = album.trim().lowercase(Locale.ROOT)
        return normalized in UNKNOWN_ALBUM_MARKERS
    }

    fun hasSongVersionMarkers(text: String): Boolean {
        return VERSION_MARKER_REGEX.containsMatchIn(text)
    }

    private fun isKnownArtistName(name: String): Boolean {
        return KNOWN_ARTISTS_NORMALIZED.containsKey(normalizeForMatching(name))
    }

    private fun resolveKnownArtistCanonicalName(name: String): String {
        return KNOWN_ARTISTS_NORMALIZED[normalizeForMatching(name)] ?: name.trim()
    }

    private fun normalizeForMatching(text: String): String {
        return text.trim().lowercase(Locale.ROOT).replace(Regex("[\\s._\\-–—·]"), "")
    }

    private fun matchesArtist(candidate: String, artist: String): Boolean {
        val c = normalizeForMatching(candidate)
        val a = normalizeForMatching(artist)
        return c == a && c.isNotEmpty()
    }

    /**
     * Detects and recovers GBK / GB18030 Chinese text erroneously decoded as ISO-8859-1 (Latin-1).
     */
    fun fixMojibake(input: String): String {
        if (input.isBlank()) return input
        if (input.any { it.code in 0x4E00..0x9FA5 }) return input

        val highByteCount = input.count { it.code in 0x0080..0x00FF }
        if (highByteCount >= 2 && highByteCount.toDouble() / input.length >= 0.4) {
            try {
                val bytes = input.toByteArray(Charsets.ISO_8859_1)
                val gbkCharset = java.nio.charset.Charset.forName("GB18030")
                val gbkStr = String(bytes, gbkCharset)
                if (gbkStr.any { it.code in 0x4E00..0x9FA5 } && !gbkStr.contains('\uFFFD')) {
                    return gbkStr
                }
            } catch (_: Throwable) {
            }
        }
        return input
    }
}
