package luzzr.muse.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.library.LibraryMediaInvalidation
import luzzr.muse.data.readalong.buildReadAlongTextIndex
import luzzr.muse.data.database.ReadAlongAnnotationEntity
import luzzr.muse.data.database.ReadAlongBookEntity
import luzzr.muse.data.database.ReadAlongBookmarkEntity
import luzzr.muse.data.database.ReadAlongDao
import luzzr.muse.data.database.ReadAlongMarkerEntity
import luzzr.muse.data.database.ReadAlongProgressEntity
import luzzr.muse.domain.model.AnnotationExportFormat
import luzzr.muse.domain.model.ReadAlongAnnotation
import luzzr.muse.domain.model.ReadAlongAnnotationColor
import luzzr.muse.domain.model.ReadAlongBook
import luzzr.muse.domain.model.ReadAlongBookSummary
import luzzr.muse.domain.model.ReadAlongBookmark
import luzzr.muse.domain.model.ReadAlongChapter
import luzzr.muse.domain.model.ReadAlongChapterData
import luzzr.muse.domain.model.ReadAlongImportResult
import luzzr.muse.domain.model.ReadAlongImportSource
import luzzr.muse.domain.model.ReadAlongMarker
import luzzr.muse.domain.model.ReadAlongProgress
import luzzr.muse.domain.model.ReadAlongReadingStats
import luzzr.muse.domain.model.ReadAlongSearchHit
import luzzr.muse.domain.model.ReadAlongSentence
import luzzr.muse.domain.model.ReadAlongShelfFilter
import luzzr.muse.domain.model.ReadAlongSortOrder
import luzzr.muse.domain.model.ReadAlongTextIndex
import luzzr.muse.domain.model.ReadAlongTocEntry
import luzzr.muse.domain.model.ReadAlongTocRuleEngine
import luzzr.muse.domain.model.ReadAlongUnit
import luzzr.muse.domain.repository.ReadAlongRepository
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element

@Singleton
class ReadAlongRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ReadAlongDao,
    private val libraryMediaInvalidation: LibraryMediaInvalidation
) : ReadAlongRepository {

    override fun observeBooks(): Flow<List<ReadAlongBook>> =
        dao.observeBooks().map { rows -> rows.map { repairCoverPath(it) }.map(::toDomain) }

    override fun observeSummaries(
        sort: ReadAlongSortOrder,
        filter: ReadAlongShelfFilter
    ): Flow<List<ReadAlongBookSummary>> = dao.observeBooks().combine(dao.observeProgress()) { books, progress ->
        val progressById = progress.associateBy { it.bookId }
        books.map { entity ->
            val repaired = repairCoverPath(entity)
            val p = progressById[repaired.id]
            val domain = toDomain(repaired)
            ReadAlongBookSummary(
                book = domain,
                progress = p?.let(::toDomain),
                lastMarker = null,
                annotationCount = 0,
                bookmarkCount = 0
            )
        }.let { summaries ->
            val filtered = when (filter) {
                ReadAlongShelfFilter.ALL -> summaries
                ReadAlongShelfFilter.SYNCED -> summaries.filter { it.book.syncStatus.name == "READY" }
                ReadAlongShelfFilter.AUDIO_ONLY -> summaries.filter { it.book.syncStatus.name == "AUDIO_ONLY" }
                ReadAlongShelfFilter.EPUB_ONLY -> summaries.filter { it.book.syncStatus.name == "EPUB_ONLY" }
                ReadAlongShelfFilter.RECENT -> summaries.filter { it.progress?.lastReadAt ?: 0L > 0L }
            }
            when (sort) {
                ReadAlongSortOrder.RECENT -> filtered.sortedByDescending { it.progress?.lastReadAt ?: it.book.updatedAt }
                ReadAlongSortOrder.TITLE -> filtered.sortedBy { it.book.title.lowercase(Locale.ROOT) }
                ReadAlongSortOrder.AUTHOR -> filtered.sortedBy { it.book.author.lowercase(Locale.ROOT) }
                ReadAlongSortOrder.PROGRESS -> filtered.sortedByDescending { it.progress?.let(::progressRatio) ?: 0f }
                ReadAlongSortOrder.HAS_SYNC -> filtered.sortedByDescending { it.book.isSynchronized }
            }
        }
    }

    override fun observeProgress(): Flow<Map<String, ReadAlongProgress>> =
        dao.observeProgress().map { rows -> rows.associate { it.bookId to toDomain(it) } }

    override fun observeStats(): Flow<ReadAlongReadingStats> = combine(
        dao.observeTotalListenedMs(),
        dao.observeMaxStreak(),
        dao.observeTotalCharacters()
    ) { totalMs, streak, chars ->
        val todayStart = startOfToday()
        val weekStart = todayStart - 6L * 24L * 60L * 60L * 1000L
        ReadAlongReadingStats(
            totalReadMs = totalMs,
            todayReadMs = withContext(Dispatchers.IO) { dao.totalListenedSince(todayStart) },
            weekReadMs = withContext(Dispatchers.IO) { dao.totalListenedSince(weekStart) },
            charactersRead = chars,
            consecutiveDays = streak,
            lastReadAt = withContext(Dispatchers.IO) {
                dao.observeProgress().firstSnapshot().maxOfOrNull { it.lastReadAt } ?: 0L
            }
        )
    }

    override fun observeAnnotations(bookId: String): Flow<List<ReadAlongAnnotation>> =
        dao.observeAnnotations(bookId).map { rows -> rows.map(::toDomain) }

    override fun observeBookmarks(bookId: String): Flow<List<ReadAlongBookmark>> =
        dao.observeBookmarks(bookId).map { rows -> rows.map(::toDomain) }

    override suspend fun getBook(bookId: String): ReadAlongBook? = withContext(Dispatchers.IO) {
        dao.getBook(bookId)?.let(::toDomain)
    }

    override suspend fun getSummary(bookId: String): ReadAlongBookSummary? = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookId)?.let(::toDomain) ?: return@withContext null
        ReadAlongBookSummary(
            book = book,
            progress = dao.getProgress(bookId)?.let(::toDomain),
            lastMarker = dao.getMarker(bookId, book.chapters.firstOrNull()?.id ?: "")?.let(::toDomain),
            annotationCount = dao.annotationCount(bookId),
            bookmarkCount = dao.bookmarkCount(bookId)
        )
    }

    override suspend fun getProgress(bookId: String): ReadAlongProgress = withContext(Dispatchers.IO) {
        dao.getProgress(bookId)?.let(::toDomain) ?: ReadAlongProgress(bookId = bookId)
    }

    override suspend fun saveProgress(progress: ReadAlongProgress) {
        withContext(Dispatchers.IO) {
            dao.saveProgress(progress.toEntity())
            dao.getBook(progress.bookId)?.let { book ->
                val touchedAt = maxOf(progress.lastReadAt, progress.lastListenedAt)
                if (touchedAt > book.updatedAt) dao.insertBook(book.copy(updatedAt = touchedAt))
            }
            Unit
        }
    }

    override suspend fun deleteBook(bookId: String) {
        withContext(Dispatchers.IO) {
            dao.getBook(bookId)?.packageRoot?.let { File(it).deleteRecursively() }
            dao.deleteProgress(bookId)
            dao.deleteBook(bookId)
            Unit
        }
    }

    override suspend fun importSources(
        sources: List<ReadAlongImportSource>
    ): OperationResult<ReadAlongImportResult> = withContext(Dispatchers.IO) {
        resultOf { importSourcesInternal(sources.map(::resolveSource)) }
    }

    override suspend fun importDocumentTree(
        treeUri: String
    ): OperationResult<List<ReadAlongImportResult>> = withContext(Dispatchers.IO) {
        resultOf {
            val sources = collectDocumentTree(treeUri)
            require(sources.isNotEmpty()) { "所选目录中没有可导入文件" }
            val packages = sources.filter(::isPackageSource)
            val epubs = sources.filter(::isEpubSource)
            val audioFiles = sources.filter(::isAudioSource)
            val results = mutableListOf<ReadAlongImportResult>()
            packages.forEach { results += importSourcesInternal(listOf(it)) }
            // A selected folder is one package when it contains one EPUB. Keep
            // manifest.json and alignment.jsonl together with audio files; dropping
            // them was the reason folder imports previously became EPUB-only books.
            when {
                epubs.size == 1 -> {
                    val loose = sources.filterNot { it in packages }
                    results += importSourcesInternal(loose)
                }
                epubs.size > 1 -> epubs.forEach { results += importSourcesInternal(listOf(it)) }
                packages.isEmpty() && audioFiles.isNotEmpty() ->
                    throw IllegalArgumentException("仅有音频文件，缺少 EPUB 源")
                packages.isEmpty() -> throw IllegalArgumentException("目录中没有 EPUB 或 .readalong.zip")
            }
            results
        }
    }

    override suspend fun importFromWifi(
        payload: ByteArray,
        displayName: String
    ): OperationResult<ReadAlongImportResult> = withContext(Dispatchers.IO) {
        resultOf {
            val staging = newStagingDirectory()
            try {
                val zip = File(staging, displayName)
                zip.writeBytes(payload)
                val source = ReadAlongImportSource(
                    uri = Uri.fromFile(zip).toString(),
                    displayName = displayName,
                    mimeType = "application/zip"
                )
                importSourcesInternal(listOf(resolveSource(source)))
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    override suspend fun attachSources(
        bookId: String,
        sources: List<ReadAlongImportSource>
    ): OperationResult<ReadAlongImportResult> = withContext(Dispatchers.IO) {
        resultOf {
            val existing = dao.getBook(bookId)?.let(::toDomain)
                ?: throw IllegalArgumentException("书籍不存在")
            require(sources.isNotEmpty()) { "请选择 alignment、manifest 或章节音频" }
            val resolved = sources.map(::resolveSource)
            require(resolved.none(::isEpubSource) && resolved.none(::isPackageSource)) {
                "补充资源时不能替换 EPUB；请作为新书导入"
            }
            importComposite(resolved, existing)
        }
    }

    override suspend fun loadChapterData(
        bookId: String,
        chapterIndex: Int
    ): OperationResult<ReadAlongChapterData> = withContext(Dispatchers.IO) {
        resultOf {
            val book = dao.getBook(bookId)?.let(::toDomain)
                ?: throw MissingResourceException("书籍不存在")
            val chapter = book.chapters.getOrNull(chapterIndex)
                ?: throw MissingResourceException("章节不存在")
            val alignment = File(book.packageRoot, ALIGNMENT_FILE)
            val sentences = if (alignment.isFile) {
                alignment.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.filter(String::isNotBlank).mapNotNull { line ->
                        parseSentence(JSONObject(line), chapter.id, chapter.href)
                    }.toList()
                }
            } else emptyList()
            ReadAlongChapterData(chapter, sentences)
        }
    }

    override suspend fun loadTextIndex(
        bookId: String,
        chapterHref: String
    ): OperationResult<ReadAlongTextIndex> = withContext(Dispatchers.IO) {
        resultOf {
            val book = dao.getBook(bookId)?.let(::toDomain)
                ?: throw MissingResourceException("书籍不存在")
            val chapter = book.chapters.firstOrNull { it.href == chapterHref }
                ?: throw MissingResourceException("章节不存在")
            val html = File(chapter.htmlPath)
            require(html.isFile) { "EPUB 资源已损坏或被清理" }
            val plain = extractPlainText(html.readText(StandardCharsets.UTF_8))
            val alignment = File(book.packageRoot, ALIGNMENT_FILE)
            val sentences = if (alignment.isFile) {
                alignment.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.filter(String::isNotBlank).mapNotNull { line ->
                        parseSentence(JSONObject(line), chapter.id, chapter.href)
                    }.toList()
                }
            } else emptyList()
            buildReadAlongTextIndex(
                chapterHref = chapterHref,
                plainText = plain,
                sentences = sentences
            )
        }
    }

    override suspend fun prefetchChapter(
        bookId: String,
        chapterIndex: Int
    ): OperationResult<ReadAlongChapterData> = loadChapterData(bookId, chapterIndex)

    override suspend fun upsertAnnotation(annotation: ReadAlongAnnotation) = withContext(Dispatchers.IO) {
        dao.upsertAnnotation(annotation.toEntity())
        Unit
    }

    override suspend fun deleteAnnotation(annotationId: String) = withContext(Dispatchers.IO) {
        dao.deleteAnnotation(annotationId)
        Unit
    }

    override suspend fun annotationsForChapter(
        bookId: String,
        chapterHref: String
    ): List<ReadAlongAnnotation> = withContext(Dispatchers.IO) {
        dao.annotationsForChapter(bookId, chapterHref).map(::toDomain)
    }

    override suspend fun upsertBookmark(bookmark: ReadAlongBookmark) = withContext(Dispatchers.IO) {
        dao.upsertBookmark(bookmark.toEntity())
        Unit
    }

    override suspend fun deleteBookmark(bookmarkId: String) = withContext(Dispatchers.IO) {
        dao.deleteBookmark(bookmarkId)
        Unit
    }

    override suspend fun recordMarker(marker: ReadAlongMarker) = withContext(Dispatchers.IO) {
        dao.upsertMarker(marker.toEntity())
        Unit
    }

    override suspend fun lastMarker(bookId: String, chapterId: String): ReadAlongMarker? = withContext(Dispatchers.IO) {
        dao.getMarker(bookId, chapterId)?.let(::toDomain)
    }

    override suspend fun searchBook(bookId: String, query: String): List<ReadAlongSearchHit> = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookId)?.let(::toDomain) ?: return@withContext emptyList()
        searchInBook(book, query)
    }

    override suspend fun searchAllBooks(query: String): List<ReadAlongSearchHit> = withContext(Dispatchers.IO) {
        val books = dao.observeBooks().firstSnapshot().map(::toDomain)
        books.flatMap { searchInBook(it, query) }
    }

    override suspend fun exportAnnotations(
        bookId: String,
        format: AnnotationExportFormat
    ): String = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookId)?.let(::toDomain)
            ?: throw MissingResourceException("书籍不存在")
        val annotations = mutableListOf<ReadAlongAnnotation>()
        for (chapter in book.chapters) {
            annotations += annotationsForChapter(bookId, chapter.href)
        }
        when (format) {
            AnnotationExportFormat.MARKDOWN -> renderMarkdown(book, annotations)
            AnnotationExportFormat.JSON -> renderJson(book, annotations)
        }
    }

    // ===================== Private =====================

    private suspend fun importSourcesInternal(sources: List<ReadAlongImportSource>): ReadAlongImportResult {
        require(sources.isNotEmpty()) { "请选择 EPUB、同步书包或同步资源" }
        if (sources.size == 1 && isPackageSource(sources.single())) {
            return importArchive(sources.single())
        }
        return importComposite(sources, existing = null)
    }

    private fun importArchive(source: ReadAlongImportSource): ReadAlongImportResult {
        val temporary = File.createTempFile("muse-readalong-", ".zip", context.cacheDir)
        val staging = newStagingDirectory()
        try {
            copyUri(source.uri, temporary, MAX_IMPORT_BYTES)
            ZipFile(temporary).use { zip -> extractArchive(zip, staging) }
            val nestedManifest = staging.walkTopDown()
                .firstOrNull { it.isFile && it.name.equals(MANIFEST_FILE, true) }
            require(nestedManifest != null) { "同步书包缺少 manifest.json" }
            if (nestedManifest.parentFile?.canonicalPath != staging.canonicalPath) {
                val packageRoot = nestedManifest.parentFile!!
                packageRoot.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val target = safeFile(staging, file.relativeTo(packageRoot).invariantSeparatorsPath)
                        target.parentFile?.mkdirs()
                        file.copyTo(target, overwrite = true)
                    }
                }
            }
            val manifestFile = File(staging, MANIFEST_FILE)
            val manifest = JSONObject(manifestFile.readText(StandardCharsets.UTF_8))
            val epubRelative = manifest.optString("epub").ifBlank {
                staging.walkTopDown().firstOrNull { it.isFile && it.extension.equals("epub", true) }
                    ?.relativeTo(staging)?.invariantSeparatorsPath
                    ?: throw IllegalArgumentException("同步书包缺少 EPUB")
            }
            val epub = safeFile(staging, epubRelative)
            require(epub.isFile) { "同步书包中的 EPUB 不存在" }
            normalizeAlignment(staging, manifest)
            val audioMap = buildChapterAudioMap(manifest, staging)
            val metadata = ManifestMetadata.from(manifest)
            val parsed = parseEpub(epub, staging, audioMap, tocPatterns(manifest))
            val bound = bindLooseAudio(parsed, staging, emptyList())
            return commitImportedBook(staging, bound, metadata, manifest)
        } finally {
            temporary.delete()
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun importComposite(
        sources: List<ReadAlongImportSource>,
        existing: ReadAlongBook?
    ): ReadAlongImportResult {
        val staging = newStagingDirectory()
        try {
            if (existing != null) {
                copyDirectoryBounded(File(existing.packageRoot), staging)
            }
            val epubSource = sources.firstOrNull(::isEpubSource)
            val epub = when {
                epubSource != null -> File(staging, EPUB_FILE).also {
                    copyUri(epubSource.uri, it, MAX_IMPORT_BYTES)
                }
                existing != null -> File(staging, EPUB_FILE).takeIf(File::isFile)
                    ?: File(existing.epubPath).also { source -> source.copyTo(File(staging, EPUB_FILE), overwrite = true) }
                else -> null
            } ?: throw IllegalArgumentException("组合导入至少需要一个 EPUB")

            val copiedAudio = mutableListOf<File>()
            sources.filter(::isAlignmentSource).lastOrNull()?.let { source ->
                copyUri(source.uri, File(staging, ALIGNMENT_FILE), MAX_ALIGNMENT_BYTES)
            }
            val manifestSource = sources.firstOrNull {
                it.displayName?.equals(MANIFEST_FILE, ignoreCase = true) == true
            } ?: sources.filter(::isManifestSource).lastOrNull()
            manifestSource?.let { source ->
                copyUri(source.uri, File(staging, MANIFEST_FILE), MAX_MANIFEST_BYTES)
            }
            val audioDir = File(staging, AUDIO_DIR).apply { mkdirs() }
            sources.filter(::isAudioSource).forEachIndexed { index, source ->
                val safeName = safeDisplayName(source.displayName, "chapter-${index + 1}.m4a")
                val target = uniqueFile(audioDir, safeName)
                copyUri(source.uri, target, MAX_AUDIO_BYTES)
                copiedAudio += target
            }

            val manifestFile = File(staging, MANIFEST_FILE)
            val manifest = manifestFile.takeIf(File::isFile)?.let {
                require(it.length() <= MAX_MANIFEST_BYTES) { "manifest.json 过大" }
                JSONObject(it.readText(StandardCharsets.UTF_8))
            }
            normalizeAlignment(staging, manifest)
            val metadata = manifest?.let(ManifestMetadata::from) ?: ManifestMetadata()
            val audioMap = if (manifest != null) buildChapterAudioMap(manifest, staging) else emptyMap()
            val parsed = parseEpub(epub, staging, audioMap, tocPatterns(manifest))
            val bound = bindLooseAudio(parsed, staging, copiedAudio)
            return commitImportedBook(staging, bound, metadata, manifest)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun commitImportedBook(
        staging: File,
        parsed: ParsedEpub,
        metadata: ManifestMetadata,
        manifest: JSONObject?
    ): ReadAlongImportResult = runBlocking(Dispatchers.IO) {
        val fingerprint = sha256(File(parsed.epubPath))
        manifest?.optString("epub_sha256")?.takeIf(String::isNotBlank)?.let { expected ->
            require(expected.equals(fingerprint, ignoreCase = true)) { "EPUB 校验失败，文件可能已损坏" }
        }
        val bookId = fingerprint.take(20)
        val finalRoot = File(context.filesDir, "$LIBRARY_DIR/$bookId")
        val existing = dao.getBook(bookId)
        commitDirectory(staging, finalRoot)
        var finalParsed = parsed.rebaseTo(finalRoot)
        val alignment = File(finalRoot, ALIGNMENT_FILE)
        val alignedChapterIds = alignment.takeIf(File::isFile)
            ?.let { alignedChapterIds(it, finalParsed.chapters) }
            .orEmpty()
        finalParsed = finalParsed.copy(
            chapters = finalParsed.chapters.map { chapter ->
                chapter.copy(hasAlignment = chapter.id in alignedChapterIds)
            }
        )
        val playableChapters = finalParsed.chapters.filter { it.audioPath != null }
        val isFullySynchronized = playableChapters.isNotEmpty() && playableChapters.all { it.hasAlignment }
        val warnings = buildList {
            if (finalParsed.usedFallbackToc) add("EPUB 未提供标准目录，已按 spine 生成临时目录")
            if (!alignment.isFile) add("未导入逐字时间轴，可先作为普通 EPUB 阅读")
            val missingAudio = finalParsed.chapters.count { it.isReadingContent && it.audioPath == null }
            if (missingAudio > 0) add("$missingAudio 个章节尚未绑定音频")
            val missingAlignment = playableChapters.count { !it.hasAlignment }
            if (missingAlignment > 0) add("$missingAlignment 个有声章节没有有效时间轴")
            if (manifest == null || manifest.optString("epub_sha256").isBlank()) add("书包未提供 EPUB 哈希")
        }
        val now = System.currentTimeMillis()
        val entity = ReadAlongBookEntity(
            id = bookId,
            title = metadata.title.ifBlank { finalParsed.title.ifBlank { "未命名书籍" } },
            author = metadata.author.ifBlank { finalParsed.author },
            language = finalParsed.language.ifBlank { "zh" },
            publisher = finalParsed.publisher,
            isbn = finalParsed.isbn,
            description = finalParsed.description,
            pubDate = finalParsed.pubDate,
            tags = finalParsed.tags.joinToString("|"),
            wordCount = finalParsed.chapters.sumOf { it.wordCount },
            totalChapters = finalParsed.chapters.size,
            epubPath = finalParsed.epubPath,
            packageRoot = finalRoot.absolutePath,
            coverPath = finalParsed.coverPath,
            chaptersJson = encodeChapters(finalParsed.chapters),
            tocJson = encodeToc(finalParsed.toc),
            sourceFingerprint = fingerprint,
            hasAlignment = isFullySynchronized,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.insertBook(entity)
        val previousProgress = dao.getProgress(bookId)
        val initialChapter = selectInitialChapter(finalParsed.chapters)
        if (previousProgress == null) {
            dao.saveProgress(
                ReadAlongProgress(
                    bookId = bookId,
                    chapterIndex = initialChapter?.index ?: 0,
                    chapterId = initialChapter?.id,
                    lastReadAt = now
                ).toEntity()
            )
        } else if (previousProgress.chapterIndex !in finalParsed.chapters.indices) {
            dao.saveProgress(
                previousProgress.copy(
                    chapterIndex = finalParsed.chapters.lastIndex.coerceAtLeast(0),
                    chapterId = finalParsed.chapters.lastOrNull()?.id,
                    audioPositionMs = 0L,
                    characterIndex = 0,
                    scrollProgress = 0f
                )
            )
        }
        libraryMediaInvalidation.requestReload()
        val book = toDomain(entity)
        ReadAlongImportResult(
            book = book,
            importedAudioCount = finalParsed.chapters.count { it.audioPath != null },
            hasAlignment = book.isSynchronized,
            warnings = warnings
        )
    }

    private fun parseEpub(
        epub: File,
        root: File,
        audioMap: Map<String, AudioManifest>,
        tocPatterns: List<String> = emptyList()
    ): ParsedEpub {
        ZipFile(epub).use { zip ->
            validateArchive(zip)
            val container = parseXml(readEntry(zip, "META-INF/container.xml", MAX_XML_BYTES))
            val opfPath = container.elements("rootfile").firstOrNull()?.getAttribute("full-path")?.trim()
                ?: throw IllegalArgumentException("EPUB container.xml 缺少 rootfile")
            val opf = parseXml(readEntry(zip, opfPath, MAX_XML_BYTES))
            val opfDir = opfPath.substringBeforeLast('/', "")
            val manifestItems = opf.elements("item").mapNotNull { item ->
                val id = item.getAttribute("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val href = item.getAttribute("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
                id to EpubManifestItem(
                    id = id,
                    path = resolvePath(opfDir, href),
                    mediaType = item.getAttribute("media-type"),
                    properties = item.getAttribute("properties").split(Regex("\\s+")).filter(String::isNotBlank).toSet()
                )
            }.toMap()
            val spineElement = opf.elements("spine").firstOrNull()
            val spine = opf.elements("itemref").mapNotNull { manifestItems[it.getAttribute("idref")] }
                .filter { it.mediaType == "application/xhtml+xml" || it.mediaType == "text/html" }
            require(spine.isNotEmpty()) { "EPUB spine 为空" }

            val contentDir = File(root, CONTENT_DIR).apply { mkdirs() }
            extractArchive(zip, contentDir)
            val rawToc = parseToc(zip, manifestItems, spineElement, opfDir)
            val fallbackToc = mutableListOf<RawTocEntry>()
            val tocTitles = rawToc.groupBy { it.path }.mapValues { (_, rows) -> rows.first().title }
            val tocPaths = rawToc.map { it.path }.toSet()
            val chapters = spine.mapIndexed { index, item ->
                val bytes = readEntry(zip, item.path, MAX_CHAPTER_BYTES)
                val htmlPath = safeFile(contentDir, item.path)
                val document = parseXml(bytes)
                if (rawToc.isEmpty()) {
                    document.elements("h1").plus(document.elements("h2")).plus(document.elements("h3")).forEach { heading ->
                        val headingText = heading.textContent.replace(Regex("\\s+"), " ").trim()
                        if (ReadAlongTocRuleEngine.isLikelyHeading(headingText, heading.tagName, tocPatterns)) {
                            fallbackToc += RawTocEntry(
                                id = "toc-fallback-${fallbackToc.size}",
                                title = headingText,
                                path = item.path,
                                fragment = heading.getAttribute("id").takeIf(String::isNotBlank),
                                depth = heading.tagName.lowercase(Locale.ROOT).removePrefix("h").toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0,
                                parentId = null
                            )
                        }
                    }
                }
                val title = tocTitles[item.path].orEmpty()
                    .ifBlank { document.elements("h1").firstOrNull()?.textContent?.trim().orEmpty() }
                    .ifBlank { document.elements("h2").firstOrNull()?.textContent?.trim().orEmpty() }
                    .ifBlank { document.elements("title").firstOrNull()?.textContent?.trim().orEmpty() }
                    .ifBlank { "第 ${index + 1} 章" }
                val generatedId = "ch${(index + 1).toString().padStart(3, '0')}"
                val audio = audioMap[generatedId]
                    ?: audioMap[item.id]
                    ?: audioMap[item.path]
                    ?: audioMap[item.path.substringAfterLast('/')]
                val audioFile = audio?.path?.let { safeFile(root, it) }?.takeIf(File::isFile)
                val htmlText = document.documentElement?.textContent.orEmpty()
                val chars = htmlText.replace(Regex("\\s+"), "").length
                ReadAlongChapter(
                    id = generatedId,
                    title = title,
                    index = index,
                    href = item.path,
                    htmlPath = htmlPath.absolutePath,
                    audioPath = audioFile?.absolutePath,
                    audioDurationMs = audio?.durationMs ?: 0L,
                    sourceChars = chars,
                    wordCount = estimateWordCount(htmlText),
                    audioByteSize = audioFile?.length() ?: 0L,
                    audioSha256 = audioFile?.let(::sha256),
                    isReadingContent = isReadingContent(item, document, audioFile != null, item.path in tocPaths)
                )
            }
            val effectiveToc = if (rawToc.isNotEmpty()) rawToc else fallbackToc
            val chapterIndexByPath = chapters.associate { it.href to it.index }
            val toc = effectiveToc.mapNotNull { raw ->
                val chapterIndex = chapterIndexByPath[raw.path] ?: return@mapNotNull null
                ReadAlongTocEntry(raw.id, raw.title, raw.path, raw.fragment, chapterIndex, raw.depth, raw.parentId)
            }.ifEmpty {
                chapters.filter { it.isReadingContent }
                    .ifEmpty { chapters }
                    .map { chapter ->
                        ReadAlongTocEntry("toc-${chapter.index}", chapter.title, chapter.href, null, chapter.index, 0, null)
                    }
            }
            val cover = manifestItems.values.firstOrNull { "cover-image" in it.properties }
                ?: manifestItems.values.firstOrNull { it.id.contains("cover", true) && it.mediaType.startsWith("image/") }
            val epub2CoverId = opf.elements("meta").firstOrNull {
                it.getAttribute("name").equals("cover", true)
            }?.getAttribute("content")?.takeIf(String::isNotBlank)
            val coverItem = epub2CoverId?.let(manifestItems::get) ?: cover
            val extractedCoverPath = coverItem?.path?.let { path ->
                runCatching {
                    val extension = path.substringAfterLast('.', "jpg").take(8).lowercase(Locale.ROOT)
                    require(extension in COVER_EXTENSIONS) { "不支持的封面格式" }
                    val target = File(root, "cover.$extension")
                    target.writeBytes(readEntry(zip, path, MAX_COVER_BYTES))
                    target.absolutePath
                }.getOrNull()
            }
            val coverPath = extractedCoverPath ?: root.walkTopDown()
                .firstOrNull { file ->
                    file.isFile && file.nameWithoutExtension.equals("cover", true) &&
                        file.extension.lowercase(Locale.ROOT) in COVER_EXTENSIONS
                }?.absolutePath
            val title = opf.elements("title").firstOrNull()?.textContent?.trim().orEmpty()
            val author = opf.elements("creator").map { it.textContent.trim() }
                .filter(String::isNotBlank).distinct().joinToString(" / ")
            val language = opf.elements("language").firstOrNull()?.textContent?.trim().orEmpty().ifBlank { "zh" }
            val opfElement = opf.documentElement
            val publisher = opfElement.descendants("publisher").joinToString(" / ") { it.textContent.trim() }
                .ifBlank { "" }
            val description = opfElement.descendants("description").joinToString("\n") { it.textContent.trim() }
                .ifBlank { "" }
            val pubDate = opfElement.descendants("date").firstOrNull()?.textContent?.trim().orEmpty()
            val identifiers = opfElement.descendants("identifier").map { it.textContent.trim() }
            val isbn = identifiers.firstOrNull { it.matches(Regex("(ISBN\\s*1[03]?:?\\s*)?\\d{9}[\\dXx]")) }.orEmpty()
            val tags = opfElement.descendants("subject").map { it.textContent.trim() }.filter { it.isNotBlank() }
            val rights = opfElement.descendants("rights").joinToString(" ") { it.textContent.trim() }
                .ifBlank { "" }
            val enrichedPublisher = listOf(publisher, rights).filter { it.isNotBlank() }.joinToString(" · ")
            val totalChars = chapters.sumOf { it.sourceChars }
            return ParsedEpub(
                title = title,
                author = author,
                language = language,
                publisher = enrichedPublisher,
                isbn = isbn,
                description = description,
                pubDate = pubDate,
                tags = tags,
                wordCount = totalChars,
                totalChapters = chapters.size,
                epubPath = epub.absolutePath,
                coverPath = coverPath,
                chapters = chapters,
                toc = toc,
                usedFallbackToc = rawToc.isEmpty()
            )
        }
    }

    private fun isReadingContent(
        item: EpubManifestItem,
        document: Document,
        hasAudio: Boolean,
        appearsInToc: Boolean
    ): Boolean {
        if ("nav" in item.properties) return false
        if (hasAudio || appearsInToc) return true
        val identity = "${item.id}/${item.path}".lowercase(Locale.ROOT)
        if (identity.contains("cover") || identity.contains("nav") || identity.contains("toc")) return false
        val prose = document.documentElement?.textContent.orEmpty().replace(Regex("\\s+"), "")
        val hasStructuralText = document.elements("p").isNotEmpty() ||
            document.elements("h1").isNotEmpty() ||
            document.elements("h2").isNotEmpty()
        return hasStructuralText && prose.length >= MIN_READING_CONTENT_CHARS
    }

    private fun parseToc(
        zip: ZipFile,
        manifest: Map<String, EpubManifestItem>,
        spine: Element?,
        opfDir: String
    ): List<RawTocEntry> {
        val nav = manifest.values.firstOrNull { "nav" in it.properties }
        if (nav != null) {
            val result = parseNavDocument(parseXml(readEntry(zip, nav.path, MAX_TOC_BYTES)), nav.path)
            if (result.isNotEmpty()) return result
        }
        val ncxId = spine?.getAttribute("toc")?.takeIf(String::isNotBlank)
        val ncx = ncxId?.let(manifest::get)
            ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        if (ncx != null) {
            val result = parseNcxDocument(parseXml(readEntry(zip, ncx.path, MAX_TOC_BYTES)), ncx.path)
            if (result.isNotEmpty()) return result
        }
        val guide = spine?.ownerDocument?.elements("reference").orEmpty()
        return guide.mapIndexedNotNull { index, element ->
            val href = element.getAttribute("href").takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
            val resolved = resolveHref(opfDir, href)
            RawTocEntry("toc-guide-$index", element.getAttribute("title").ifBlank { "章节 ${index + 1}" }, resolved.first, resolved.second, 0, null)
        }
    }

    private fun parseNavDocument(document: Document, navPath: String): List<RawTocEntry> {
        val nav = document.elements("nav").firstOrNull { element ->
            val type = listOf(element.getAttribute("epub:type"), element.getAttributeNS(EPUB_NS, "type"))
                .joinToString(" ")
            type.split(Regex("\\s+")).contains("toc")
        } ?: document.elements("nav").firstOrNull() ?: return emptyList()
        val root = nav.directChildren("ol").firstOrNull() ?: return emptyList()
        val result = mutableListOf<RawTocEntry>()
        fun visit(ol: Element, depth: Int, parentId: String?) {
            ol.directChildren("li").forEach { li ->
                val labelElement = li.directChildren("a").firstOrNull() ?: li.directChildren("span").firstOrNull()
                val title = labelElement?.textContent?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                val href = labelElement?.getAttribute("href").orEmpty()
                val id = "toc-${result.size}"
                val nextParent = if (title.isNotBlank() && href.isNotBlank()) {
                    val resolved = resolveHref(navPath.substringBeforeLast('/', ""), href)
                    result += RawTocEntry(id, title, resolved.first, resolved.second, depth, parentId)
                    id
                } else parentId
                li.directChildren("ol").forEach { visit(it, depth + 1, nextParent) }
            }
        }
        visit(root, 0, null)
        return result
    }

    private fun parseNcxDocument(document: Document, ncxPath: String): List<RawTocEntry> {
        val navMap = document.elements("navMap").firstOrNull() ?: return emptyList()
        val result = mutableListOf<RawTocEntry>()
        fun visit(parent: Element, depth: Int, parentId: String?) {
            parent.directChildren("navPoint").forEach { point ->
                val title = point.descendants("text").firstOrNull()?.textContent?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                val href = point.descendants("content").firstOrNull()?.getAttribute("src").orEmpty()
                val id = "toc-${result.size}"
                val nextParent = if (title.isNotBlank() && href.isNotBlank()) {
                    val resolved = resolveHref(ncxPath.substringBeforeLast('/', ""), href)
                    result += RawTocEntry(id, title, resolved.first, resolved.second, depth, parentId)
                    id
                } else parentId
                visit(point, depth + 1, nextParent)
            }
        }
        visit(navMap, 0, null)
        return result
    }

    private fun bindLooseAudio(parsed: ParsedEpub, root: File, newlyCopied: List<File>): ParsedEpub {
        val allAudio = root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS }
            .toList()
        if (allAudio.isEmpty()) return parsed
        val unused = allAudio.toMutableList()
        val bound = parsed.chapters.map { chapter ->
            if (chapter.audioPath != null) {
                unused.removeAll { it.absolutePath == chapter.audioPath }
                chapter
            } else {
                val normalizedTitle = normalizeMatchKey(chapter.title)
                val indexTokens = listOf(
                    chapter.id.lowercase(Locale.ROOT),
                    (chapter.index + 1).toString(),
                    (chapter.index + 1).toString().padStart(2, '0'),
                    (chapter.index + 1).toString().padStart(3, '0')
                )
                val match = unused.firstOrNull { file ->
                    val key = normalizeMatchKey(file.nameWithoutExtension)
                    key == normalizedTitle || indexTokens.any { token -> key == token || key.endsWith(token) }
                }
                if (match != null) {
                    unused.remove(match)
                    chapter.copy(
                        audioPath = match.absolutePath,
                        audioDurationMs = if (mediaDuration(match) > 0L) mediaDuration(match) else chapter.audioDurationMs,
                        audioByteSize = match.length(),
                        audioSha256 = sha256(match)
                    )
                } else chapter
            }
        }.toMutableList()
        if (unused.isNotEmpty()) {
            val openIndices = bound.indices.filter { bound[it].audioPath == null }
            if (unused.size == openIndices.size) {
                unused.sortedBy { it.name.lowercase(Locale.ROOT) }.zip(openIndices).forEach { (file, index) ->
                    bound[index] = bound[index].copy(
                        audioPath = file.absolutePath,
                        audioDurationMs = mediaDuration(file),
                        audioByteSize = file.length(),
                        audioSha256 = sha256(file)
                    )
                }
            }
        }
        return parsed.copy(chapters = bound)
    }

    private fun parseSentence(json: JSONObject, chapterId: String, chapterHref: String): ReadAlongSentence? {
        if (json.optString("chapter_id") != chapterId) return null
        val locator = json.optJSONObject("source_locator") ?: return null
        if (locator.optString("epub_href") != chapterHref) return null
        val audioLocator = json.optJSONObject("audio_locator") ?: return null
        val sentenceStart = audioLocator.optDouble("chapter_start_seconds", -1.0)
        val sentenceEnd = audioLocator.optDouble("chapter_end_seconds", -1.0)
        if (sentenceStart < 0 || sentenceEnd < sentenceStart) return null
        val unitsJson = json.optJSONArray("unit_timings")
        val units = buildList {
            if (unitsJson != null) for (index in 0 until unitsJson.length()) {
                val item = unitsJson.optJSONObject(index) ?: continue
                val text = item.optString("text")
                val start = item.optDouble("start", -1.0)
                val end = item.optDouble("end", -1.0)
                if (text.isNotEmpty() && start >= 0 && end >= start) {
                    add(ReadAlongUnit(text, (start * 1000).toLong(), (end * 1000).toLong()))
                }
            }
        }.ifEmpty {
            val fallbackText = json.optString("spoken_text").ifBlank { json.optString("source_text") }
            if (fallbackText.isBlank()) emptyList()
            else listOf(ReadAlongUnit(fallbackText, (sentenceStart * 1000).toLong(), (sentenceEnd * 1000).toLong()))
        }
        return ReadAlongSentence(
            id = json.optString("sentence_id"),
            chapterId = chapterId,
            sourceText = json.optString("source_text"),
            spokenText = json.optString("spoken_text"),
            epubHref = chapterHref,
            elementId = locator.optString("element_id").takeIf(String::isNotBlank),
            elementPath = locator.optString("element_path").takeIf(String::isNotBlank),
            chapterCharStart = locator.optInt("chapter_char_start", 0),
            chapterCharEnd = locator.optInt("chapter_char_end", 0),
            quoteExact = locator.optJSONObject("text_quote")?.optString("exact").orEmpty(),
            chapterStartMs = (sentenceStart * 1000).toLong(),
            chapterEndMs = (sentenceEnd * 1000).toLong(),
            units = units
        )
    }

    private fun searchInBook(book: ReadAlongBook, query: String): List<ReadAlongSearchHit> {
        if (query.isBlank()) return emptyList()
        return book.chapters.flatMap { chapter ->
            val file = File(chapter.htmlPath)
            if (!file.isFile) return@flatMap emptyList()
            val text = extractPlainText(file.readText(StandardCharsets.UTF_8))
            val hits = mutableListOf<ReadAlongSearchHit>()
            var start = 0
            while (true) {
                val found = text.indexOf(query, start, ignoreCase = true)
                if (found < 0) break
                val end = (found + query.length).coerceAtMost(text.length)
                val from = (found - 20).coerceAtLeast(0)
                val to = (end + 20).coerceAtMost(text.length)
                val excerpt = (if (from > 0) "…" else "") + text.substring(from, to) + (if (to < text.length) "…" else "")
                hits += ReadAlongSearchHit(
                    bookId = book.id,
                    chapterId = chapter.id,
                    chapterHref = chapter.href,
                    elementId = null,
                    charStart = found,
                    charEnd = end,
                    excerpt = excerpt
                )
                start = end
                if (hits.size >= MAX_HITS_PER_BOOK) break
            }
            hits
        }
    }

    private fun renderMarkdown(book: ReadAlongBook, annotations: List<ReadAlongAnnotation>): String {
        val byChapter = annotations.groupBy { it.chapterHref }
        val sb = StringBuilder()
        sb.appendLine("# ${book.title} 批注")
        if (book.author.isNotBlank()) sb.appendLine("作者：${book.author}")
        sb.appendLine()
        book.chapters.forEach { chapter ->
            val list = byChapter[chapter.href].orEmpty()
            if (list.isEmpty()) return@forEach
            sb.appendLine("## ${chapter.title}")
            list.forEach { a ->
                sb.appendLine("- [${a.color}] ${a.quote}")
                if (a.note.isNotBlank()) sb.appendLine("  > ${a.note}")
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun renderJson(book: ReadAlongBook, annotations: List<ReadAlongAnnotation>): String {
        val json = JSONObject()
        json.put("bookId", book.id)
        json.put("title", book.title)
        json.put("author", book.author)
        val arr = JSONArray()
        annotations.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("chapter", a.chapterId)
                    put("href", a.chapterHref)
                    put("charStart", a.charStart)
                    put("charEnd", a.charEnd)
                    put("color", a.color.name)
                    put("quote", a.quote)
                    put("note", a.note)
                    put("createdAt", a.createdAt)
                }
            )
        }
        json.put("annotations", arr)
        return json.toString(2)
    }

    private fun extractPlainText(html: String): String {
        val builder = StringBuilder()
        var skippedDepth = 0
        var i = 0
        while (i < html.length) {
            val c = html[i]
            if (c == '<') {
                val close = html.indexOf('>', i)
                if (close < 0) break
                val rawTag = html.substring(i + 1, close).trim()
                val closing = rawTag.startsWith("/")
                val tag = rawTag.removePrefix("/").takeWhile { !it.isWhitespace() && it != '/' }
                    .lowercase(Locale.ROOT)
                if (tag in setOf("head", "style", "script", "noscript", "svg")) {
                    if (closing) skippedDepth = (skippedDepth - 1).coerceAtLeast(0)
                    else if (!rawTag.endsWith("/")) skippedDepth++
                    i = close + 1
                    continue
                }
                // Keep element boundaries out of the index. The WebView TreeWalker
                // joins text nodes in the same way; <br>/<p> are not text nodes.
                i = close + 1
                continue
            }
            if (skippedDepth == 0) builder.append(c)
            i++
        }
        // &nbsp; → \\u00a0 (non-breaking space) to match browser's DOM nodeValue,
        // then \\u00a0 → regular space because JS /\s+/ matches \\u00a0 but the
        // Kotlin version of \\s+ does not.  Without this conversion the
        // character-position mapping between extractPlainText and the JS
        // TreeWalker diverges wherever the HTML uses consecutive &nbsp; entities
        // (common between paragraphs), causing every later offset to be off by
        // the number of collapsed NBSP characters.
        return builder.toString()
            .replace("&nbsp;", "\u00a0")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", 34.toChar().toString())
            .replace("&apos;", 39.toChar().toString())
            .replace("\u00a0", " ")
            .replace(Regex("\\s+"), " ")
    }

    private fun alignStartInPlain(plain: String, wanted: String): Int {
        if (wanted.isEmpty()) return -1
        val direct = plain.indexOf(wanted)
        if (direct >= 0) return direct
        val compactWanted = wanted.replace(Regex("\\s+"), "")
        val compact = plain.replace(Regex("\\s+"), "")
        val compactStart = compact.indexOf(compactWanted)
        if (compactStart < 0) return -1
        var raw = 0
        var compactIndex = 0
        while (raw < plain.length && compactIndex < compactStart) {
            if (!plain[raw].isWhitespace()) compactIndex++
            raw++
        }
        return raw
    }

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun estimateWordCount(text: String): Int {
        if (text.isEmpty()) return 0
        val cjk = text.count { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
        val latin = text.split(Regex("[^A-Za-z0-9']+")).count { it.isNotBlank() }
        return cjk + latin
    }

    private fun progressRatio(progress: ReadAlongProgress): Float {
        val totalMs = maxOf(progress.audioPositionMs, progress.totalListenedMs)
        if (totalMs <= 0L) return 0f
        val target = (totalMs.toDouble() / 1_000_000.0).toFloat()
        return target.coerceIn(0f, 1f)
    }

    private fun collectDocumentTree(treeUri: String): List<ReadAlongImportSource> {
        val resolver = context.contentResolver
        val tree = treeUri.toUri()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val result = mutableListOf<ReadAlongImportSource>()
        fun visit(parentId: String, depth: Int) {
            require(depth <= MAX_TREE_DEPTH) { "目录层级过深" }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val typeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    require(result.size <= MAX_TREE_FILES) { "目录文件过多" }
                    val id = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex)
                    val type = cursor.getString(typeIndex)
                    if (type == DocumentsContract.Document.MIME_TYPE_DIR) {
                        visit(id, depth + 1)
                    } else if (isSupportedName(name)) {
                        result += ReadAlongImportSource(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                            displayName = name,
                            mimeType = type
                        )
                    }
                }
            }
        }
        visit(rootId, 0)
        return result
    }

    private fun resolveSource(source: ReadAlongImportSource): ReadAlongImportSource {
        if (!source.displayName.isNullOrBlank() && !source.mimeType.isNullOrBlank()) return source
        var name = source.displayName
        var mime = source.mimeType ?: context.contentResolver.getType(source.uri.toUri())
        context.contentResolver.query(
            source.uri.toUri(),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
        if (mime.isNullOrBlank()) mime = guessMime(name)
        return source.copy(displayName = name, mimeType = mime)
    }

    private fun copyUri(uri: String, target: File, maxBytes: Long) {
        target.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri.toUri()) ?: throw IOException("无法打开文件")
        input.use { source -> target.outputStream().use { output -> copyLimited(source, output, maxBytes) } }
    }

    private fun extractArchive(zip: ZipFile, root: File) {
        require(zip.size() <= MAX_ENTRY_COUNT) { "压缩包文件过多" }
        var total = 0L
        zip.entries().asSequence().forEach { entry ->
            if (entry.isDirectory) return@forEach
            val target = safeFile(root, entry.name)
            require(entry.size < 0 || entry.size <= MAX_ENTRY_BYTES) { "压缩包条目过大: ${entry.name}" }
            target.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { output ->
                    total += copyLimited(input, output, MAX_ENTRY_BYTES)
                    require(total <= MAX_EXTRACTED_BYTES) { "解压后内容超过大小限制" }
                }
            }
        }
    }

    private fun validateArchive(zip: ZipFile) {
        require(zip.size() <= MAX_ENTRY_COUNT) { "EPUB 文件过多" }
        var declaredTotal = 0L
        zip.entries().asSequence().forEach { entry ->
            safeArchivePath(entry.name)
            require(entry.size < 0 || entry.size <= MAX_ENTRY_BYTES) { "EPUB 条目过大" }
            if (entry.size > 0) {
                declaredTotal += entry.size
                require(declaredTotal <= MAX_EXTRACTED_BYTES) { "EPUB 解压后过大" }
            }
        }
    }

    private fun readEntry(zip: ZipFile, name: String, maxBytes: Long): ByteArray {
        val entry = zip.getEntry(name) ?: throw IllegalArgumentException("缺少 EPUB 条目: $name")
        require(!entry.isDirectory && (entry.size < 0 || entry.size <= maxBytes)) { "EPUB 条目超过大小限制: $name" }
        return zip.getInputStream(entry).use { input ->
            ByteArrayOutputStream().use { output ->
                copyLimited(input, output, maxBytes)
                output.toByteArray()
            }
        }
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "文件超过大小限制" }
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun copyDirectoryBounded(source: File, target: File) {
        require(source.isDirectory) { "书籍资源目录不存在" }
        var total = 0L
        var count = 0
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source).invariantSeparatorsPath
            if (relative.isBlank()) return@forEach
            count++
            require(count <= MAX_ENTRY_COUNT) { "书籍资源文件过多" }
            val destination = safeFile(target, relative)
            if (file.isDirectory) {
                destination.mkdirs()
            } else {
                total += file.length()
                require(file.length() <= MAX_ENTRY_BYTES && total <= MAX_EXTRACTED_BYTES) { "书籍资源过大" }
                destination.parentFile?.mkdirs()
                file.copyTo(destination, overwrite = true)
            }
        }
    }

    private fun commitDirectory(staging: File, finalRoot: File) {
        finalRoot.parentFile?.mkdirs()
        val backup = File(finalRoot.parentFile, ".backup-${finalRoot.name}-${UUID.randomUUID()}")
        if (finalRoot.exists()) require(finalRoot.renameTo(backup)) { "无法备份旧书籍资源" }
        try {
            require(staging.renameTo(finalRoot)) { "无法保存书籍资源" }
            backup.deleteRecursively()
        } catch (error: Throwable) {
            if (!finalRoot.exists() && backup.exists()) backup.renameTo(finalRoot)
            throw error
        }
    }

    private fun normalizeAlignment(root: File, manifest: JSONObject?) {
        val relative = manifest?.optString("alignment")?.takeIf(String::isNotBlank) ?: ALIGNMENT_FILE
        val source = runCatching { safeFile(root, relative) }.getOrNull() ?: return
        val target = File(root, ALIGNMENT_FILE)
        if (source.isFile && source.canonicalPath != target.canonicalPath) source.copyTo(target, overwrite = true)
        if (target.isFile) require(target.length() <= MAX_ALIGNMENT_BYTES) { "alignment.jsonl 过大" }
    }

    private fun buildChapterAudioMap(manifest: JSONObject, root: File): Map<String, AudioManifest> {
        val result = mutableMapOf<String, AudioManifest>()
        val chapters = manifest.optJSONArray("chapters") ?: JSONArray()
        val audioRoot = manifest.optString("audio_root").takeIf(String::isNotBlank) ?: AUDIO_DIR
        for (index in 0 until chapters.length()) {
            val item = chapters.optJSONObject(index) ?: continue
            // 兼容两种 schema：
            //  1) {"audio": "audio/ch001.m4a", "id": "ch001", "href": "OEBPS/ch001.xhtml"}
            //  2) {"id": "ch001", "href": "...", "filename": "ch001.m4a"}  → 拼成 audioRoot + filename
            val path = item.optString("audio").takeIf(String::isNotBlank)
                ?: item.optString("filename").takeIf(String::isNotBlank)
                    ?.let { fname -> if (fname.contains('/')) fname else "$audioRoot/$fname" }
            val value = AudioManifest(
                path = path,
                durationMs = item.optDouble("duration", 0.0).takeIf { it > 0 }?.times(1000)?.toLong() ?: 0L
            )
            listOf(item.optString("id"), item.optString("href"), item.optString("href").substringAfterLast('/'))
                .filter(String::isNotBlank).forEach { result[it] = value }
        }
        // 当 manifest 没有 chapters 数组（e.g. zaibiekangqiao）时，回退到扫 audio/ 目录
        // 用文件名作为 key（ch001、ch001.m4a、chapter-1.m4a 等），由 parseEpub 解析时回退匹配
        if (result.isEmpty()) {
            val audioDir = File(root, audioRoot)
            if (audioDir.isDirectory) {
                audioDir.listFiles().orEmpty()
                    .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS }
                    .forEach { f ->
                        val base = f.nameWithoutExtension
                        val am = AudioManifest(
                            path = f.relativeTo(root).invariantSeparatorsPath,
                            durationMs = mediaDuration(f)
                        )
                        listOf(base, base.removeSuffix("-m4a")).forEach { result[it] = am }
                    }
            }
        }
        return result
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { setExpandEntityReferences(false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(sanitizeEpubXml(bytes).inputStream())
    }

    /**
     * EPUB XHTML commonly declares the XHTML DTD. We keep DTD processing disabled
     * (to prevent XXE and entity-expansion attacks), but remove declarations before
     * parsing so otherwise valid EPUB content remains importable.
     */
    private fun sanitizeEpubXml(bytes: ByteArray): ByteArray {
        val withoutDoctype = stripDoctypeDeclarations(bytes.toString(StandardCharsets.UTF_8))
        val safeEntities = XML_NAMED_ENTITY.replace(withoutDoctype) { match ->
            HTML_ENTITY_REPLACEMENTS[match.groupValues[1]] ?: "&#xfffd;"
        }
        return safeEntities.toByteArray(StandardCharsets.UTF_8)
    }

    private fun stripDoctypeDeclarations(xml: String): String {
        var cursor = 0
        val cleaned = StringBuilder(xml.length)
        while (cursor < xml.length) {
            val start = xml.indexOf("<!DOCTYPE", cursor, ignoreCase = true)
            if (start < 0) {
                cleaned.append(xml, cursor, xml.length)
                break
            }
            cleaned.append(xml, cursor, start)
            val end = findDoctypeEnd(xml, start)
                ?: throw IllegalArgumentException("不完整的 XML DOCTYPE 声明")
            cursor = end + 1
        }
        return cleaned.toString()
    }

    private fun findDoctypeEnd(xml: String, start: Int): Int? {
        var quote: Char? = null
        var internalSubsetDepth = 0
        for (index in (start + "<!DOCTYPE".length) until xml.length) {
            val char = xml[index]
            if (quote != null) {
                if (char == quote) quote = null
                continue
            }
            when (char) {
                '\'', '\"' -> quote = char
                '[' -> internalSubsetDepth += 1
                ']' -> if (internalSubsetDepth > 0) internalSubsetDepth -= 1
                '>' -> if (internalSubsetDepth == 0) return index
            }
        }
        return null
    }

    private fun resolveHref(base: String, href: String): Pair<String, String?> {
        val rawPath = href.substringBefore('#')
        val decoded = runCatching { URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name()) }
            .getOrDefault(rawPath)
        val path = resolvePath(base, decoded)
        return path to href.substringAfter('#', "").takeIf(String::isNotBlank)
    }

    private fun resolvePath(base: String, href: String): String = safeArchivePath(
        listOf(base, href).filter(String::isNotBlank).joinToString("/")
    )

    private fun safeArchivePath(raw: String): String {
        val normalized = raw.replace('\\', '/').trim()
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !normalized.contains("://")) { "不安全的压缩包路径" }
        val parts = mutableListOf<String>()
        normalized.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> require(parts.isNotEmpty()) { "不安全的压缩包路径" }.also { parts.removeAt(parts.lastIndex) }
                else -> parts += part
            }
        }
        require(parts.isNotEmpty()) { "不安全的压缩包路径" }
        return parts.joinToString("/")
    }

    private fun safeFile(root: File, relative: String): File {
        val clean = safeArchivePath(relative)
        val rootCanonical = root.canonicalFile
        val target = File(rootCanonical, clean).canonicalFile
        require(target.path.startsWith(rootCanonical.path + File.separator)) { "不安全的文件路径" }
        return target
    }

    private fun newStagingDirectory(): File =
        File(context.filesDir, "$LIBRARY_DIR/.staging-${UUID.randomUUID()}").apply {
            require(mkdirs()) { "无法创建导入暂存目录" }
        }

    private fun encodeChapters(chapters: List<ReadAlongChapter>): String = JSONArray().apply {
        chapters.forEach { chapter ->
            put(JSONObject().apply {
                put("id", chapter.id)
                put("title", chapter.title)
                put("index", chapter.index)
                put("href", chapter.href)
                put("htmlPath", chapter.htmlPath)
                put("audioPath", chapter.audioPath)
                put("audioDurationMs", chapter.audioDurationMs)
                put("sourceChars", chapter.sourceChars)
                put("wordCount", chapter.wordCount)
                put("audioByteSize", chapter.audioByteSize)
                put("audioSha256", chapter.audioSha256)
                put("isReadingContent", chapter.isReadingContent)
                put("hasAlignment", chapter.hasAlignment)
            })
        }
    }.toString()

    private fun decodeChapters(value: String, fallbackHasAlignment: Boolean = false): List<ReadAlongChapter> {
        val array = JSONArray(value)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val audioPath = item.optString("audioPath").takeIf(String::isNotBlank)
                add(
                    ReadAlongChapter(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        index = item.getInt("index"),
                        href = item.getString("href"),
                        htmlPath = item.getString("htmlPath"),
                        audioPath = audioPath,
                        audioDurationMs = item.optLong("audioDurationMs", 0L),
                        sourceChars = item.optInt("sourceChars", 0),
                        wordCount = item.optInt("wordCount", 0).takeIf { it > 0 }
                            ?: item.optInt("sourceChars", 0),
                        audioByteSize = item.optLong("audioByteSize", 0L),
                        audioSha256 = item.optString("audioSha256").takeIf(String::isNotBlank),
                        isReadingContent = item.optBoolean("isReadingContent", true),
                        hasAlignment = if (item.has("hasAlignment")) item.optBoolean("hasAlignment") else fallbackHasAlignment && audioPath != null
                    )
                )
            }
        }
    }

    private fun encodeToc(toc: List<ReadAlongTocEntry>): String = JSONArray().apply {
        toc.forEach { entry ->
            put(JSONObject().apply {
                put("id", entry.id)
                put("title", entry.title)
                put("href", entry.href)
                put("fragment", entry.fragment)
                put("chapterIndex", entry.chapterIndex)
                put("depth", entry.depth)
                put("parentId", entry.parentId)
            })
        }
    }.toString()

    private fun decodeToc(value: String, chapters: List<ReadAlongChapter>): List<ReadAlongTocEntry> = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ReadAlongTocEntry(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        href = item.getString("href"),
                        fragment = item.optString("fragment").takeIf(String::isNotBlank),
                        chapterIndex = item.getInt("chapterIndex"),
                        depth = item.optInt("depth", 0),
                        parentId = item.optString("parentId").takeIf(String::isNotBlank)
                    )
                )
            }
        }
    }.getOrDefault(emptyList()).ifEmpty {
        chapters.map { chapter -> ReadAlongTocEntry("toc-${chapter.index}", chapter.title, chapter.href, null, chapter.index, 0, null) }
    }

    private suspend fun repairCoverPath(entity: ReadAlongBookEntity): ReadAlongBookEntity {
        val current = entity.coverPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
        if (current != null) return entity
        val root = File(entity.packageRoot)
        val fallback = root.walkTopDown()
            .firstOrNull { file ->
                file.isFile && file.nameWithoutExtension.equals("cover", true) &&
                    file.extension.lowercase(Locale.ROOT) in COVER_EXTENSIONS
            }
            ?: return entity
        if (entity.coverPath != fallback.absolutePath) {
            runCatching { dao.updateCoverPath(entity.id, fallback.absolutePath) }
        }
        return entity.copy(coverPath = fallback.absolutePath)
    }

    private fun toDomain(entity: ReadAlongBookEntity): ReadAlongBook {
        val chapters = decodeChapters(entity.chaptersJson, entity.hasAlignment)
        return ReadAlongBook(
            id = entity.id,
            title = entity.title,
            author = entity.author,
            language = entity.language.ifBlank { "zh" },
            publisher = entity.publisher,
            isbn = entity.isbn,
            description = entity.description,
            pubDate = entity.pubDate,
            tags = entity.tags.split('|').filter(String::isNotBlank),
            wordCount = entity.wordCount.takeIf { it > 0 } ?: chapters.sumOf { it.sourceChars },
            totalChapters = entity.totalChapters.takeIf { it > 0 } ?: chapters.size,
            epubPath = entity.epubPath,
            packageRoot = entity.packageRoot,
            coverPath = entity.coverPath,
            chapters = chapters,
            toc = decodeToc(entity.tocJson, chapters),
            isSynchronized = entity.hasAlignment,
            sourceFingerprint = entity.sourceFingerprint,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun toDomain(entity: ReadAlongProgressEntity): ReadAlongProgress = ReadAlongProgress(
        bookId = entity.bookId,
        chapterIndex = entity.chapterIndex,
        chapterId = entity.chapterId,
        audioPositionMs = entity.audioPositionMs,
        textLocator = entity.textLocator,
        characterIndex = entity.characterIndex,
        scrollProgress = entity.scrollProgress,
        pageProgress = entity.pageProgress,
        playbackSpeed = entity.playbackSpeed,
        fontScale = entity.fontScale,
        lineHeightScale = entity.lineHeightScale,
        fontFamily = runCatching { luzzr.muse.domain.model.ReadAlongFontFamily.valueOf(entity.fontFamily) }
            .getOrDefault(luzzr.muse.domain.model.ReadAlongFontFamily.BOOK),
        fontWeight = runCatching { luzzr.muse.domain.model.ReadAlongFontWeight.valueOf(entity.fontWeight) }
            .getOrDefault(luzzr.muse.domain.model.ReadAlongFontWeight.REGULAR),
        paragraphSpacing = entity.paragraphSpacing,
        pagerMode = runCatching { luzzr.muse.domain.model.ReadAlongPagerMode.valueOf(entity.pagerMode) }
            .getOrDefault(luzzr.muse.domain.model.ReadAlongPagerMode.SCROLL),
        theme = runCatching { luzzr.muse.domain.model.ReadAlongTheme.valueOf(entity.theme) }
            .getOrDefault(luzzr.muse.domain.model.ReadAlongTheme.PAPER),
        autoFollow = entity.autoFollow,
        totalListenedMs = entity.totalListenedMs,
        sessionStartMs = entity.sessionStartMs,
        consecutiveDays = entity.consecutiveDays,
        lastReadAt = entity.lastReadAt,
        lastListenedAt = entity.lastListenedAt,
        completed = entity.completed
    )

    private fun toDomain(entity: ReadAlongAnnotationEntity): ReadAlongAnnotation = ReadAlongAnnotation(
        id = entity.id,
        bookId = entity.bookId,
        chapterId = entity.chapterId,
        chapterHref = entity.chapterHref,
        elementId = entity.elementId,
        charStart = entity.charStart,
        charEnd = entity.charEnd,
        color = runCatching { luzzr.muse.domain.model.ReadAlongAnnotationColor.valueOf(entity.color) }
            .getOrDefault(luzzr.muse.domain.model.ReadAlongAnnotationColor.YELLOW),
        note = entity.note,
        quote = entity.quote,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    private fun ReadAlongAnnotation.toEntity() = ReadAlongAnnotationEntity(
        id = id,
        bookId = bookId,
        chapterId = chapterId,
        chapterHref = chapterHref,
        elementId = elementId,
        charStart = charStart,
        charEnd = charEnd,
        color = color.name,
        note = note,
        quote = quote,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun toDomain(entity: ReadAlongBookmarkEntity): ReadAlongBookmark = ReadAlongBookmark(
        id = entity.id,
        bookId = entity.bookId,
        chapterId = entity.chapterId,
        chapterHref = entity.chapterHref,
        label = entity.label,
        charOffset = entity.charOffset,
        audioPositionMs = entity.audioPositionMs,
        createdAt = entity.createdAt
    )

    private fun ReadAlongBookmark.toEntity() = ReadAlongBookmarkEntity(
        id = id,
        bookId = bookId,
        chapterId = chapterId,
        chapterHref = chapterHref,
        label = label,
        charOffset = charOffset,
        audioPositionMs = audioPositionMs,
        createdAt = createdAt
    )

    private fun toDomain(entity: ReadAlongMarkerEntity): ReadAlongMarker = ReadAlongMarker(
        bookId = entity.bookId,
        chapterId = entity.chapterId,
        charOffset = entity.charOffset,
        unitIndex = entity.unitIndex,
        updatedAt = entity.updatedAt
    )

    private fun ReadAlongMarker.toEntity() = ReadAlongMarkerEntity(
        bookId = bookId,
        chapterId = chapterId,
        charOffset = charOffset,
        unitIndex = unitIndex,
        updatedAt = updatedAt
    )

    private fun ReadAlongProgress.toEntity(): ReadAlongProgressEntity = ReadAlongProgressEntity(
        bookId = bookId,
        chapterIndex = chapterIndex,
        chapterId = chapterId,
        audioPositionMs = audioPositionMs.coerceAtLeast(0L),
        textLocator = textLocator,
        characterIndex = characterIndex.coerceAtLeast(0),
        scrollProgress = scrollProgress.coerceIn(0f, 1f),
        pageProgress = pageProgress.coerceIn(0f, 1f),
        playbackSpeed = playbackSpeed.coerceIn(0.5f, 3f),
        fontScale = fontScale.coerceIn(0.6f, 2.0f),
        lineHeightScale = lineHeightScale.coerceIn(1.1f, 2.6f),
        fontFamily = fontFamily.name,
        fontWeight = fontWeight.name,
        paragraphSpacing = paragraphSpacing.coerceIn(0.5f, 2.5f),
        pagerMode = pagerMode.name,
        theme = theme.name,
        autoFollow = autoFollow,
        totalListenedMs = totalListenedMs.coerceAtLeast(0L),
        sessionStartMs = sessionStartMs,
        consecutiveDays = consecutiveDays,
        lastReadAt = lastReadAt,
        lastListenedAt = lastListenedAt,
        completed = completed
    )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun mediaDuration(file: File): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    private fun isEpubSource(source: ReadAlongImportSource): Boolean =
        source.displayName?.endsWith(".epub", true) == true || source.mimeType.equals("application/epub+zip", true)

    private fun isPackageSource(source: ReadAlongImportSource): Boolean {
        val isReadAlong = source.displayName?.endsWith(".readalong.zip", true) == true
        val isCalibre = source.displayName?.endsWith(".calibre", true) == true
        val isAnyZip = source.displayName?.endsWith(".zip", true) == true
        return isReadAlong || isCalibre || (isAnyZip && !isEpubSource(source))
    }

    private fun isAlignmentSource(source: ReadAlongImportSource): Boolean =
        source.displayName?.endsWith(".jsonl", true) == true || source.displayName.equals(ALIGNMENT_FILE, true)

    private fun tocPatterns(manifest: JSONObject?): List<String> {
        if (manifest == null) return emptyList()
        val direct = manifest.optJSONArray("toc_regex")?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
        }.orEmpty()
        if (direct.isNotEmpty()) return direct
        return manifest.optJSONArray("toc_rules")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index)
                item?.optString("regex")?.takeIf(String::isNotBlank)
            }
        }.orEmpty()
    }

    private fun isManifestSource(source: ReadAlongImportSource): Boolean =
        source.displayName?.endsWith(".json", true) == true &&
            !source.displayName.equals("provenance.json", true) &&
            !isAlignmentSource(source)

    private fun isAudioSource(source: ReadAlongImportSource): Boolean =
        source.displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS ||
            source.mimeType?.startsWith("audio/") == true

    private fun isSupportedName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".epub") || lower.endsWith(".zip") || lower.endsWith(".json") ||
            lower.endsWith(".jsonl") || lower.substringAfterLast('.', "") in AUDIO_EXTENSIONS
    }

    private fun guessMime(name: String?): String? = when (name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
        "epub" -> "application/epub+zip"
        "zip" -> "application/zip"
        "json" -> "application/json"
        "jsonl" -> "application/x-ndjson"
        in AUDIO_EXTENSIONS -> "audio/*"
        else -> null
    }

    private fun safeDisplayName(name: String?, fallback: String): String {
        val candidate = name?.substringAfterLast('/')?.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")?.trim().orEmpty()
        return candidate.take(180).ifBlank { fallback }
    }

    private fun uniqueFile(directory: File, name: String): File {
        val first = File(directory, name)
        if (!first.exists()) return first
        val stem = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
        var index = 2
        while (true) {
            val candidate = File(directory, "$stem-$index$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun normalizeMatchKey(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun alignedChapterIds(
        alignment: File,
        chapters: List<ReadAlongChapter>
    ): Set<String> {
        val byId = chapters.associateBy { it.id }
        return alignment.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.filter(String::isNotBlank).mapNotNull { line ->
                val json = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
                val chapterId = json.optString("chapter_id")
                val chapter = byId[chapterId] ?: return@mapNotNull null
                parseSentence(json, chapterId, chapter.href)
                    ?.takeIf { sentence -> sentence.units.any { it.endMs > it.startMs } }
                    ?.chapterId
            }.toSet()
        }
    }

    private fun selectInitialChapter(chapters: List<ReadAlongChapter>): ReadAlongChapter? =
        chapters.firstOrNull { it.isReadingContent && it.audioPath != null }
            ?: chapters.firstOrNull { it.isReadingContent }
            ?: chapters.firstOrNull { it.audioPath != null }
            ?: chapters.firstOrNull()

    private inline fun <T> resultOf(block: () -> T): OperationResult<T> = try {
        OperationResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: SecurityException) {
        OperationResult.Failure(OperationError.PERMISSION_DENIED, error.message)
    } catch (error: MissingResourceException) {
        OperationResult.Failure(OperationError.NOT_FOUND, error.message)
    } catch (error: IllegalArgumentException) {
        OperationResult.Failure(OperationError.UNSUPPORTED_FILE, error.message)
    } catch (error: IOException) {
        OperationResult.Failure(OperationError.IO, error.message)
    } catch (error: Throwable) {
        if (error is CancellationException || error is Error) throw error
        OperationResult.Failure(OperationError.UNKNOWN, error.message)
    }

    private fun Document.elements(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun Element.directChildren(localName: String): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
            .filter { it.localName == localName || it.tagName.substringAfter(':') == localName }

    private fun Element.descendants(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private data class EpubManifestItem(
        val id: String,
        val path: String,
        val mediaType: String,
        val properties: Set<String>
    )

    private data class AudioManifest(val path: String?, val durationMs: Long)

    private data class RawTocEntry(
        val id: String,
        val title: String,
        val path: String,
        val fragment: String?,
        val depth: Int,
        val parentId: String?
    )

    private data class ManifestMetadata(
        val title: String = "",
        val author: String = ""
    ) {
        companion object {
            fun from(manifest: JSONObject) = ManifestMetadata(
                title = manifest.optString("title").trim(),
                author = manifest.optString("author").trim()
            )
        }
    }

    private data class ParsedEpub(
        val title: String,
        val author: String,
        val language: String,
        val publisher: String,
        val isbn: String,
        val description: String,
        val pubDate: String,
        val tags: List<String>,
        val wordCount: Int,
        val totalChapters: Int,
        val epubPath: String,
        val coverPath: String?,
        val chapters: List<ReadAlongChapter>,
        val toc: List<ReadAlongTocEntry>,
        val usedFallbackToc: Boolean
    ) {
        fun rebaseTo(finalRoot: File): ParsedEpub {
            val stagingRoot = requireNotNull(File(epubPath).parentFile) {
                "staging root missing for $epubPath"
            }
            fun rebase(path: String?): String? = path?.let { original ->
                val relative = File(original).relativeTo(stagingRoot).invariantSeparatorsPath
                File(finalRoot, relative).absolutePath
            }
            return copy(
                epubPath = rebase(epubPath)!!,
                coverPath = rebase(coverPath),
                chapters = chapters.map { chapter ->
                    chapter.copy(htmlPath = rebase(chapter.htmlPath)!!, audioPath = rebase(chapter.audioPath))
                }
            )
        }
    }

    private class MissingResourceException(message: String) : IllegalStateException(message)

    private companion object {
        const val LIBRARY_DIR = "readalong"
        const val EPUB_FILE = "book.epub"
        const val MANIFEST_FILE = "manifest.json"
        const val ALIGNMENT_FILE = "alignment.jsonl"
        const val AUDIO_DIR = "audio"
        const val CONTENT_DIR = "epub-content"
        const val EPUB_NS = "http://www.idpf.org/2007/ops"
        const val MAX_IMPORT_BYTES = 500L * 1024 * 1024
        const val MAX_EXTRACTED_BYTES = 1_000L * 1024 * 1024
        const val MAX_ENTRY_BYTES = 100L * 1024 * 1024
        const val MAX_AUDIO_BYTES = 500L * 1024 * 1024
        const val MAX_ALIGNMENT_BYTES = 64L * 1024 * 1024
        const val MAX_ENTRY_COUNT = 20_000
        const val MAX_TREE_FILES = 5_000
        const val MAX_TREE_DEPTH = 32
        const val MAX_MANIFEST_BYTES = 2L * 1024 * 1024
        const val MAX_XML_BYTES = 8L * 1024 * 1024
        const val MAX_TOC_BYTES = 8L * 1024 * 1024
        const val MAX_CHAPTER_BYTES = 20L * 1024 * 1024
        const val MAX_COVER_BYTES = 15L * 1024 * 1024
        val COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        const val MAX_HITS_PER_BOOK = 200
        const val MIN_READING_CONTENT_CHARS = 80
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "ogg", "opus", "wav", "flac", "aac")
        val XML_NAMED_ENTITY = Regex("&([A-Za-z][A-Za-z0-9]+);")
        val HTML_ENTITY_REPLACEMENTS = mapOf(
            "amp" to "&amp;", "lt" to "&lt;", "gt" to "&gt;", "quot" to "&quot;", "apos" to "&apos;",
            "nbsp" to "&#160;", "mdash" to "&#8212;", "ndash" to "&#8211;", "hellip" to "&#8230;",
            "ldquo" to "&#8220;", "rdquo" to "&#8221;", "lsquo" to "&#8216;", "rsquo" to "&#8217;",
            "copy" to "&#169;", "reg" to "&#174;", "trade" to "&#8482;"
        )
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstSnapshot(): T {
    var found: T? = null
    this@firstSnapshot.collect { value -> found = value; throw kotlinx.coroutines.CancellationException("first-snapshot") }
    @Suppress("UNCHECKED_CAST")
    return found as T
}
