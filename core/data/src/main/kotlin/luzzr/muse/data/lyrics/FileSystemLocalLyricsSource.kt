package luzzr.muse.data.lyrics

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.lyrics.LocalLyricsSource
import luzzr.muse.domain.lyrics.LrcParser
import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song

@Singleton
class FileSystemLocalLyricsSource @Inject constructor() : LocalLyricsSource {

    private data class CachedIndex(
        val createdAtMs: Long,
        val files: List<File>
    )

    private data class LrcMetadata(
        val title: String?,
        val artist: String?
    )

    private val indexMutex = Mutex()
    private val indexCache = LinkedHashMap<String, CachedIndex>(MAX_INDEX_CACHE_SIZE, 0.75f, true)

    override suspend fun find(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
        val audioFile = song.filePath
            .takeIf(String::isNotBlank)
            ?.let(::File)
            ?: return@withContext null
        if (normalizeIdentity(song.title).isBlank() || normalizeIdentity(song.artist) in unknownArtists) {
            return@withContext null
        }

        val directCandidates = directCandidates(audioFile)
        findBestMatch(song, directCandidates)?.let { return@withContext it }

        val roots = globalSearchRoots(audioFile)
        if (roots.isEmpty()) return@withContext null
        findBestMatch(song, indexedLrcFiles(roots))
    }

    internal fun findBestMatch(song: Song, files: List<File>): LyricsResult? {
        val title = normalizeIdentity(song.title)
        val artist = normalizeIdentity(song.artist)
        if (title.isBlank() || artist.isBlank() || artist in unknownArtists) return null

        val audioStem = File(song.filePath).nameWithoutExtension
        return files.asSequence()
            .filter(::isReadableLrc)
            .distinctBy { canonicalPathOrAbsolute(it) }
            .mapNotNull { file ->
                val stem = normalizeIdentity(file.nameWithoutExtension)
                val isSameStem = normalizeIdentity(audioStem) == stem
                if (!isSameStem && title !in stem) return@mapNotNull null
                val content = readLrc(file) ?: return@mapNotNull null
                val metadata = parseMetadata(content)
                val score = matchScore(
                    queryTitle = title,
                    queryArtist = artist,
                    fileStem = stem,
                    metadata = metadata,
                    sameAudioStem = isSameStem
                )
                if (score <= 0) return@mapNotNull null
                Triple(score, file, content)
            }
            .sortedWith(
                compareByDescending<Triple<Int, File, String>> { it.first }
                    .thenBy { it.second.name.length }
            )
            .mapNotNull { (_, file, content) -> toLyricsResult(song, file, content) }
            .firstOrNull()
    }

    private fun matchScore(
        queryTitle: String,
        queryArtist: String,
        fileStem: String,
        metadata: LrcMetadata,
        sameAudioStem: Boolean
    ): Int {
        val taggedTitle = normalizeIdentity(metadata.title)
        val taggedArtist = normalizeIdentity(metadata.artist)
        val titleMatches = if (taggedTitle.isNotBlank()) {
            identityMatches(queryTitle, taggedTitle)
        } else {
            queryTitle in fileStem
        }
        val artistMatches = if (taggedArtist.isNotBlank()) {
            artistMatches(queryArtist, taggedArtist)
        } else {
            queryArtist in fileStem
        }
        if (!titleMatches || !artistMatches) return 0

        return (if (taggedTitle == queryTitle) 50 else 35) +
            (if (artistMatches(queryArtist, taggedArtist)) 45 else 30) +
            (if (sameAudioStem) 8 else 0)
    }

    private fun toLyricsResult(song: Song, file: File, content: String): LyricsResult? {
        val parsed = LrcParser.parse(content)
        val plain = if (parsed.isEmpty()) {
            content.lineSequence()
                .filterNot { metadataLineRegex.matches(it.trim()) }
                .joinToString("\n")
                .trim()
                .takeIf(String::isNotBlank)
        } else {
            null
        }
        if (parsed.isEmpty() && plain == null) return null
        return LyricsResult(
            id = null,
            trackName = song.title,
            artistName = song.artist,
            albumName = song.album,
            duration = song.duration / 1000.0,
            syncedLines = parsed,
            plainText = plain,
            rawSyncedLyrics = content.takeIf { parsed.isNotEmpty() },
            source = LOCAL_SOURCE
        )
    }

    private fun directCandidates(audioFile: File): List<File> {
        val parent = audioFile.parentFile ?: return emptyList()
        val candidates = LinkedHashSet<File>()
        listLrcFiles(parent).forEach(candidates::add)
        lyricsDirectoryNames.forEach { name ->
            listLrcFiles(File(parent, name)).forEach(candidates::add)
            parent.parentFile?.let { listLrcFiles(File(it, name)).forEach(candidates::add) }
        }
        return candidates.toList()
    }

    private fun globalSearchRoots(audioFile: File): List<File> {
        val ancestors = generateSequence(audioFile.parentFile) { it.parentFile }.take(8).toList()
        val storageRoot = ancestors.firstOrNull { directory ->
            directory.name.equals("0", ignoreCase = true) &&
                directory.parentFile?.name.equals("emulated", ignoreCase = true)
        } ?: ancestors.firstOrNull { directory ->
            directory.parentFile?.name.equals("storage", ignoreCase = true)
        } ?: ancestors.firstOrNull { directory ->
            directory.name.equals("sdcard", ignoreCase = true)
        }

        return buildList {
            storageRoot?.let { root ->
                lyricsDirectoryNames.forEach { add(File(root, it)) }
                add(File(root, "Music"))
                add(File(root, "Download"))
            }
        }.filter(File::isDirectory)
            .distinctBy(::canonicalPathOrAbsolute)
    }

    private suspend fun indexedLrcFiles(roots: List<File>): List<File> {
        val key = roots.map(::canonicalPathOrAbsolute).sorted().joinToString("|")
        val now = System.currentTimeMillis()
        return indexMutex.withLock {
            indexCache[key]?.takeIf { now - it.createdAtMs < INDEX_TTL_MS }?.files?.let {
                return@withLock it
            }
            val files = scanRoots(roots)
            indexCache[key] = CachedIndex(now, files)
            while (indexCache.size > MAX_INDEX_CACHE_SIZE) {
                indexCache.remove(indexCache.keys.first())
            }
            files
        }
    }

    private fun scanRoots(roots: List<File>): List<File> {
        val results = ArrayList<File>()
        val visited = HashSet<String>()
        val queue = ArrayDeque<Pair<File, Int>>()
        roots.forEach { queue.add(it to 0) }

        while (queue.isNotEmpty() && results.size < MAX_INDEXED_FILES) {
            val (directory, depth) = queue.removeFirst()
            val path = canonicalPathOrAbsolute(directory)
            if (!visited.add(path) || !directory.isDirectory || directory.isHidden) continue
            val children = try {
                directory.listFiles().orEmpty()
            } catch (_: SecurityException) {
                emptyArray()
            }
            children.forEach { child ->
                when {
                    isReadableLrc(child) -> results.add(child)
                    child.isDirectory && depth < MAX_SCAN_DEPTH && !shouldSkipDirectory(child) ->
                        queue.add(child to depth + 1)
                }
            }
        }
        return results
    }

    private fun listLrcFiles(directory: File): List<File> {
        if (!directory.isDirectory) return emptyList()
        return try {
            directory.listFiles { file -> isReadableLrc(file) }.orEmpty().toList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun isReadableLrc(file: File): Boolean {
        return file.isFile &&
            file.extension.equals("lrc", ignoreCase = true) &&
            file.length() in 1..MAX_LRC_BYTES
    }

    private fun readLrc(file: File): String? {
        return try {
            decode(file.readBytes())
        } catch (e: Exception) {
            MuseLog.w("LocalLyrics", "Unable to read ${file.name}", e)
            null
        }
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, charset("GB18030"))
        }
    }

    private fun parseMetadata(content: String): LrcMetadata {
        var title: String? = null
        var artist: String? = null
        content.lineSequence().take(MAX_METADATA_LINES).forEach { line ->
            val match = metadataLineRegex.matchEntire(line.trim()) ?: return@forEach
            when (match.groupValues[1].lowercase(Locale.ROOT)) {
                "ti" -> if (title == null) title = match.groupValues[2].trim()
                "ar" -> if (artist == null) artist = match.groupValues[2].trim()
            }
        }
        return LrcMetadata(title, artist)
    }

    private fun identityMatches(first: String, second: String): Boolean {
        return first == second ||
            (first.length >= MIN_CONTAINED_IDENTITY_LENGTH && second.contains(first)) ||
            (second.length >= MIN_CONTAINED_IDENTITY_LENGTH && first.contains(second))
    }

    private fun artistMatches(query: String, candidate: String): Boolean {
        if (query.isBlank() || candidate.isBlank()) return false
        if (identityMatches(query, candidate)) return true
        val queryParts = splitArtists(query)
        val candidateParts = splitArtists(candidate)
        return queryParts.any { queryPart ->
            candidateParts.any { candidatePart -> identityMatches(queryPart, candidatePart) }
        }
    }

    private fun splitArtists(value: String): List<String> {
        return value.split(artistSeparatorRegex)
            .map(::normalizeIdentity)
            .filter(String::isNotBlank)
    }

    private fun normalizeIdentity(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(variantNoiseRegex, " ")
            .filter(Char::isLetterOrDigit)
    }

    private fun shouldSkipDirectory(directory: File): Boolean {
        return directory.name.lowercase(Locale.ROOT) in skippedDirectories
    }

    private fun canonicalPathOrAbsolute(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
    }

    private companion object {
        const val LOCAL_SOURCE = "local"
        const val MAX_LRC_BYTES = 2L * 1024L * 1024L
        const val MAX_INDEXED_FILES = 4_000
        const val MAX_SCAN_DEPTH = 6
        const val MAX_INDEX_CACHE_SIZE = 3
        const val MAX_METADATA_LINES = 120
        const val INDEX_TTL_MS = 2L * 60L * 1000L
        const val MIN_CONTAINED_IDENTITY_LENGTH = 3

        val lyricsDirectoryNames = listOf("Lyrics", "lyrics", "歌词")
        val skippedDirectories = setOf("android", "data", "obb", ".thumbnails", ".trash")
        val unknownArtists = setOf("", "unknown", "unknownartist", "未知", "未知艺术家", "未知歌手")
        val artistSeparatorRegex = Regex("""(?:feat\.?|ft\.?|与|和|,|，|/|&|、|;)""", RegexOption.IGNORE_CASE)
        val variantNoiseRegex = Regex(
            """(?i)(?:\bofficial\b|\bmusic\s*video\b|\blyrics?\b|\bmv\b|\baudio\b|\blive\b|""" +
                """\bremaster(?:ed)?\b|\bversion\b|高清|无损|现场|伴奏)"""
        )
        val metadataLineRegex = Regex("""^\s*\[(ti|ar)\s*:(.*)]\s*$""", RegexOption.IGNORE_CASE)
    }
}
