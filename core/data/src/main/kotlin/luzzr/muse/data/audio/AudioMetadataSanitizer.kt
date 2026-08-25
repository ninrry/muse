package luzzr.muse.data.audio

import java.util.Locale

/**
 * Normalizes and cleans raw audio metadata extracted from MediaStore or audio tags.
 * Resolves compatibility issues where titles are formatted as "Title - Artist" or "Artist - Title",
 * strips redundant track numbers and platform noise suffixes.
 */
object AudioMetadataSanitizer {

    data class SanitizedMetadata(
        val title: String,
        val artist: String,
        val album: String
    )

    private val UNKNOWN_ARTIST_MARKERS = setOf(
        "",
        "unknown",
        "unknown artist",
        "unknownartist",
        "unknownsinger",
        "<unknown>",
        "未知",
        "未知艺术家",
        "未知歌手",
        "未知作者"
    )

    private val UNKNOWN_ALBUM_MARKERS = setOf(
        "",
        "unknown",
        "unknown album",
        "unknownalbum",
        "<unknown>",
        "未知",
        "未知专辑"
    )

    private val NOISE_PATTERNS = listOf(
        Regex("(_哔哩哔哩|_bilibili|_YouTube|_youtube|_niconico)", RegexOption.IGNORE_CASE),
        Regex("[-–—·\\s]*(哔哩哔哩|bilibili|YouTube|youtube|niconico)$", RegexOption.IGNORE_CASE),
        Regex("\\[[^\\]]*(4K|HD|Official|MV|Audio|Lyrics?|高清|无损|192kHz|FLAC|Hi-Res)[^\\]]*]", RegexOption.IGNORE_CASE),
        Regex("【[^】]*(4K|HD|超清|高清|无损|高音质|官方|MV|字幕)[^】]*】", RegexOption.IGNORE_CASE)
    )

    private val TRACK_NUMBER_PREFIX_REGEX = Regex(
        "^\\s*(?:cd\\s*\\d{1,2}[._\\-\\s]+)?(?:track\\s*)?\\d{1,3}[._\\-\\s]+",
        RegexOption.IGNORE_CASE
    )

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
        title = title.replace(Regex("\\.(mp3|flac|ogg|oga|opus|m4a|m4b|alac|wav|aac)$", RegexOption.IGNORE_CASE), "")

        // 2. Strip noise patterns
        for (pattern in NOISE_PATTERNS) {
            title = title.replace(pattern, "").trim()
        }

        // 3. Strip leading track number if followed by actual title content
        val withoutTrackNum = title.replace(TRACK_NUMBER_PREFIX_REGEX, "").trim()
        if (withoutTrackNum.isNotBlank()) {
            title = withoutTrackNum
        }

        // 4. Handle title-artist combined patterns (e.g. "歌名 - 艺术家" or "艺术家 - 歌名")
        val dashSplit = splitTitleArtist(title)

        if (dashSplit.size >= 2) {
            val first = dashSplit.first().trim()
            val last = dashSplit.last().trim()

            if (!isArtistUnknown) {
                when {
                    matchesArtist(last, artist) -> {
                        // Title is "Song - Artist"
                        val candidate = dashSplit.dropLast(1).joinToString(" - ").trim()
                        if (candidate.isNotBlank()) title = candidate
                    }
                    matchesArtist(first, artist) -> {
                        // Title is "Artist - Song"
                        val candidate = dashSplit.drop(1).joinToString(" - ").trim()
                        if (candidate.isNotBlank()) title = candidate
                    }
                }
            } else {
                // Artist is unknown, try to extract from "Artist - Title" or "Title - Artist"
                if (first.length in 1..40 && dashSplit.size == 2) {
                    val firstHasVersion = hasSongVersionMarkers(first)
                    val lastHasVersion = hasSongVersionMarkers(last)

                    if (firstHasVersion && !lastHasVersion) {
                        // "歌名 (Live) - 歌手" -> Title is first, Artist is last
                        title = first
                        artist = last
                    } else {
                        // Standard "Artist - Title"
                        artist = first
                        title = last
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

        // 5. Final validation and fallbacks
        if (title.isBlank()) {
            title = rawTitle?.takeIf { it.isNotBlank() }
                ?: fallbackFileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: "Unknown"
        }

        if (isUnknownArtist(artist)) {
            artist = "Unknown Artist"
        }

        return SanitizedMetadata(
            title = title,
            artist = artist,
            album = album
        )
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

    private fun matchesArtist(candidate: String, artist: String): Boolean {
        val c = candidate.trim().lowercase(Locale.ROOT)
        val a = artist.trim().lowercase(Locale.ROOT)
        return c == a || c.replace(" ", "") == a.replace(" ", "")
    }

    private val VERSION_MARKER_REGEX = Regex(
        "[(\\[（【][^)\\]）】]*(Live|伴奏|Instrumental|Remix|Cover|Acoustic|Piano|Guitar|feat|ft\\.|with|官方|无损|高音质|原版)[^)\\]）】]*[)\\]）】]",
        RegexOption.IGNORE_CASE
    )

    fun hasSongVersionMarkers(text: String): Boolean {
        return VERSION_MARKER_REGEX.containsMatchIn(text)
    }

    /**
     * Splits a combined "Artist - Title" or "Title - Artist" string without breaking
     * intra-word hyphens in artist names like "T-ara", "AC-DC", "Jay-Z".
     */
    fun splitTitleArtist(input: String): List<String> {
        if (input.isBlank()) return emptyList()

        // 1. Try splitting on whitespace-padded dash/hyphen or em-dash/bullet: " - ", " – ", " — ", " · "
        val spacedSplit = input.split(Regex("\\s+[-–—·]\\s+|\\s*[—–·]\\s*")).map { it.trim() }.filter { it.isNotBlank() }
        if (spacedSplit.size >= 2) {
            return spacedSplit
        }

        // 2. Try Chinese full-width em-dash "——"
        if (input.contains("——")) {
            val emSplit = input.split("——").map { it.trim() }.filter { it.isNotBlank() }
            if (emSplit.size >= 2) return emSplit
        }

        return listOf(input.trim())
    }

    /**
     * Detects and recovers GBK / GB18030 Chinese text erroneously decoded as ISO-8859-1 (Latin-1).
     */
    fun fixMojibake(input: String): String {
        if (input.isBlank()) return input
        // If it already contains CJK characters, it's not mojibake
        if (input.any { it.code in 0x4E00..0x9FA5 }) return input

        // Check if it has suspicious Latin-1 high bytes (e.g. Ö, Ü, ½, Â, etc.)
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
