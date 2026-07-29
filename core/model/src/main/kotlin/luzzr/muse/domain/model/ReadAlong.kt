package luzzr.muse.domain.model

/** EPUB book managed by the dedicated read-along library. */
data class ReadAlongBook(
    val id: String,
    val title: String,
    val author: String,
    val language: String = "zh",
    val publisher: String = "",
    val isbn: String = "",
    val description: String = "",
    val pubDate: String = "",
    val tags: List<String> = emptyList(),
    val wordCount: Int = 0,
    val totalChapters: Int = 0,
    val epubPath: String,
    val packageRoot: String,
    val coverPath: String?,
    val chapters: List<ReadAlongChapter>,
    val toc: List<ReadAlongTocEntry>,
    val isSynchronized: Boolean,
    val sourceFingerprint: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    val readingChapterIndices: List<Int>
        get() = chapters.indices.filter { chapters[it].isReadingContent }
            .ifEmpty { chapters.indices.toList() }

    val readingChapterCount: Int
        get() = readingChapterIndices.size

    fun previousReadingChapterIndex(currentIndex: Int): Int? = readingChapterIndices.lastOrNull { it < currentIndex }

    fun nextReadingChapterIndex(currentIndex: Int): Int? = readingChapterIndices.firstOrNull { it > currentIndex }

    fun readingChapterOrdinal(currentIndex: Int): Int? = readingChapterIndices.indexOf(currentIndex).takeIf { it >= 0 }?.plus(1)

    /**
     * The first chapter that should be opened for a new reader session. EPUB
     * spine entries can begin with cover, copyright, and nav documents; those
     * are preserved but must not become the default reading destination.
     */
    val initialChapterIndex: Int
        get() = chapters.indexOfFirst { it.isReadingContent && it.audioPath != null }
            .takeIf { it >= 0 }
            ?: chapters.indexOfFirst { it.isReadingContent }
                .takeIf { it >= 0 }
            ?: chapters.indexOfFirst { it.audioPath != null }
                .takeIf { it >= 0 }
            ?: 0

    val syncStatus: ReadAlongSyncStatus
        get() {
            val playable = chapters.filter { it.audioPath != null }
            return when {
                playable.isEmpty() -> ReadAlongSyncStatus.EPUB_ONLY
                isSynchronized && playable.all { it.hasAlignment } -> ReadAlongSyncStatus.READY
                else -> ReadAlongSyncStatus.AUDIO_ONLY
            }
        }
}

data class ReadAlongChapter(
    val id: String,
    val title: String,
    val index: Int,
    val href: String,
    val htmlPath: String,
    val audioPath: String?,
    val audioDurationMs: Long,
    val sourceChars: Int,
    val wordCount: Int = sourceChars,
    /** Persisted identity for excluding external source copies from music scans. */
    val audioByteSize: Long = 0L,
    val audioSha256: String? = null,
    /** False for EPUB cover/nav/front-matter documents retained only as resources. */
    val isReadingContent: Boolean = true,
    /** True only when this chapter has at least one valid alignment record. */
    val hasAlignment: Boolean = false
)

/** Flattened EPUB navigation tree. [depth] and [parentId] preserve hierarchy. */
data class ReadAlongTocEntry(
    val id: String,
    val title: String,
    val href: String,
    val fragment: String?,
    val chapterIndex: Int,
    val depth: Int,
    val parentId: String?
)

enum class ReadAlongSyncStatus {
    EPUB_ONLY,
    AUDIO_ONLY,
    READY
}

data class ReadAlongImportSource(
    val uri: String,
    val displayName: String? = null,
    val mimeType: String? = null
)

data class ReadAlongImportResult(
    val book: ReadAlongBook,
    val importedAudioCount: Int,
    val hasAlignment: Boolean,
    val warnings: List<String> = emptyList()
)

data class ReadAlongUnit(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class ReadAlongSentence(
    val id: String,
    val chapterId: String,
    val sourceText: String,
    val spokenText: String,
    val epubHref: String,
    val elementId: String?,
    val elementPath: String?,
    val chapterCharStart: Int,
    val chapterCharEnd: Int,
    val quoteExact: String,
    val chapterStartMs: Long,
    val chapterEndMs: Long,
    val units: List<ReadAlongUnit>
)

data class ReadAlongChapterData(
    val chapter: ReadAlongChapter,
    val sentences: List<ReadAlongSentence>
) {
    val units: List<ReadAlongUnit> = sentences.flatMap { sentence ->
        sentence.units.map { unit ->
            unit.copy(
                startMs = sentence.chapterStartMs + unit.startMs,
                endMs = sentence.chapterStartMs + unit.endMs
            )
        }
    }
}

/**
 * Selects the unit that is audible at [positionMs]. Alignment files can contain
 * zero-length units or multiple units with the same start time; those must not
 * make the playback cursor jump backwards or skip a valid timed unit.
 */
fun readAlongActiveUnitIndex(units: List<ReadAlongUnit>, positionMs: Long): Int {
    if (units.isEmpty()) return -1

    var active = -1
    var activeStart = Long.MIN_VALUE
    for (index in units.indices) {
        val unit = units[index]
        if (unit.endMs <= unit.startMs) continue
        if (positionMs >= unit.startMs && positionMs < unit.endMs &&
            (unit.startMs > activeStart || (unit.startMs == activeStart && index > active))
        ) {
            active = index
            activeStart = unit.startMs
        }
    }
    if (active >= 0) return active

    // During a gap, keep the last valid unit visible; before the first unit,
    // return -1 so the reader does not highlight an arbitrary character.
    var previous = -1
    var previousStart = Long.MIN_VALUE
    for (index in units.indices) {
        val unit = units[index]
        if (unit.endMs <= unit.startMs || unit.startMs > positionMs) continue
        if (unit.startMs > previousStart || (unit.startMs == previousStart && index > previous)) {
            previous = index
            previousStart = unit.startMs
        }
    }
    return previous
}

data class ReadAlongProgress(
    val bookId: String,
    val chapterIndex: Int = 0,
    val chapterId: String? = null,
    val audioPositionMs: Long = 0L,
    val textLocator: String? = null,
    val characterIndex: Int = 0,
    val scrollProgress: Float = 0f,
    val pageProgress: Float = 0f,
    val playbackSpeed: Float = 1f,
    val fontScale: Float = 1f,
    val lineHeightScale: Float = 1.6f,
    val fontFamily: ReadAlongFontFamily = ReadAlongFontFamily.BOOK,
    val fontWeight: ReadAlongFontWeight = ReadAlongFontWeight.REGULAR,
    val paragraphSpacing: Float = 1f,
    val pagerMode: ReadAlongPagerMode = ReadAlongPagerMode.SCROLL,
    val theme: ReadAlongTheme = ReadAlongTheme.PAPER,
    val autoFollow: Boolean = true,
    val totalListenedMs: Long = 0L,
    val sessionStartMs: Long = 0L,
    val consecutiveDays: Int = 0,
    val lastReadAt: Long = 0L,
    val lastListenedAt: Long = 0L,
    val completed: Boolean = false
)

data class ReadAlongReadingStats(
    val totalReadMs: Long = 0L,
    val todayReadMs: Long = 0L,
    val weekReadMs: Long = 0L,
    val charactersRead: Long = 0L,
    val consecutiveDays: Int = 0,
    val lastReadAt: Long = 0L
)

data class ReadAlongSettings(
    val fontScale: Float = 1f,
    val lineHeightScale: Float = 1.6f,
    val fontFamily: ReadAlongFontFamily = ReadAlongFontFamily.BOOK,
    val fontWeight: ReadAlongFontWeight = ReadAlongFontWeight.REGULAR,
    val paragraphSpacing: Float = 1f,
    val pagerMode: ReadAlongPagerMode = ReadAlongPagerMode.SCROLL,
    val theme: ReadAlongTheme = ReadAlongTheme.PAPER,
    val autoFollow: Boolean = true,
    val playbackSpeed: Float = 1f
)

enum class ReadAlongFontFamily { BOOK, SERIF, SANS, MONO, SYSTEM }
enum class ReadAlongFontWeight { REGULAR, MEDIUM, SEMIBOLD }
enum class ReadAlongPagerMode { SCROLL, PAGED }
enum class ReadAlongTheme { PAPER, SEPIA, NIGHT }
enum class ReadAlongAnnotationColor { YELLOW, GREEN, PINK, BLUE, UNDERLINE }

/**
 * A user-created highlight + optional note anchored to a character range inside a
 * single chapter. Char offsets are relative to the chapter's text-only content
 * (matching the existing alignment data format).
 */
data class ReadAlongAnnotation(
    val id: String,
    val bookId: String,
    val chapterId: String,
    val chapterHref: String,
    val elementId: String?,
    val charStart: Int,
    val charEnd: Int,
    val color: ReadAlongAnnotationColor,
    val note: String,
    val quote: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class ReadAlongBookmark(
    val id: String,
    val bookId: String,
    val chapterId: String,
    val chapterHref: String,
    val label: String,
    val charOffset: Int,
    val audioPositionMs: Long,
    val createdAt: Long
)

/**
 * Lightweight per-character timeline position. Persisted alongside progress so
 * that re-opening a chapter can jump to the right character without re-loading
 * the entire alignment file.
 */
data class ReadAlongMarker(
    val bookId: String,
    val chapterId: String,
    val charOffset: Int,
    val unitIndex: Int,
    val updatedAt: Long
)

/** Aggregated, queryable view of one book. */
data class ReadAlongBookSummary(
    val book: ReadAlongBook,
    val progress: ReadAlongProgress?,
    val lastMarker: ReadAlongMarker?,
    val annotationCount: Int,
    val bookmarkCount: Int
)

/** Sort options for the shelf. */
enum class ReadAlongSortOrder(val key: String) {
    RECENT("recent"),
    TITLE("title"),
    AUTHOR("author"),
    PROGRESS("progress"),
    HAS_SYNC("sync");

    companion object {
        fun fromKey(key: String?): ReadAlongSortOrder = values().firstOrNull { it.key == key } ?: RECENT
    }
}

/** Filter applied to the shelf. */
enum class ReadAlongShelfFilter(val key: String) {
    ALL("all"),
    SYNCED("sync"),
    AUDIO_ONLY("audio"),
    EPUB_ONLY("epub"),
    RECENT("recent");

    companion object {
        fun fromKey(key: String?): ReadAlongShelfFilter = values().firstOrNull { it.key == key } ?: ALL
    }
}

/** A single search hit inside an imported book. */
data class ReadAlongSearchHit(
    val bookId: String,
    val chapterId: String,
    val chapterHref: String,
    val elementId: String?,
    val charStart: Int,
    val charEnd: Int,
    val excerpt: String
)

/**
 * A fully pre-computed per-character view of the active chapter: a flat string
 * plus per-character sentence / word / unit / sync membership. Computed once
 * after the chapter is loaded, then snapshotted into the WebView once so the
 * hot-path `updateHighlight(unitIndex)` only has to mutate `<mark>` ranges, not
 * re-walk the DOM tree.
 */
data class ReadAlongTextIndex(
    val chapterHref: String,
    val plainText: String,
    val elementIdByChar: IntArray?,
    val sentenceStartByChar: IntArray?,
    val unitStartByChar: IntArray?,
    val sentenceRanges: List<IntRange>,
    val unitRanges: List<IntRange>
) {
    fun unitFor(positionMs: Long, unitTimings: List<ReadAlongUnit>): Int = readAlongActiveUnitIndex(unitTimings, positionMs)
}
enum class AnnotationExportFormat { MARKDOWN, JSON }
