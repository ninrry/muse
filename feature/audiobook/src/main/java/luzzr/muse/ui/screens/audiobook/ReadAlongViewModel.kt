package luzzr.muse.ui.screens.audiobook

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.AnnotationExportFormat
import luzzr.muse.domain.model.ReadAlongAnnotation
import luzzr.muse.domain.model.ReadAlongAnnotationColor
import luzzr.muse.domain.model.ReadAlongBook
import luzzr.muse.domain.model.ReadAlongBookSummary
import luzzr.muse.domain.model.ReadAlongBookmark
import luzzr.muse.domain.model.ReadAlongChapterData
import luzzr.muse.domain.model.ReadAlongFontFamily
import luzzr.muse.domain.model.ReadAlongFontWeight
import luzzr.muse.domain.model.ReadAlongImportResult
import luzzr.muse.domain.model.ReadAlongImportSource
import luzzr.muse.domain.model.ReadAlongMarker
import luzzr.muse.domain.model.ReadAlongPagerMode
import luzzr.muse.domain.model.ReadAlongProgress
import luzzr.muse.domain.model.ReadAlongReadingStats
import luzzr.muse.domain.model.ReadAlongSearchHit
import luzzr.muse.domain.model.ReadAlongShelfFilter
import luzzr.muse.domain.model.ReadAlongSortOrder
import luzzr.muse.domain.model.ReadAlongTheme
import luzzr.muse.domain.model.readAlongActiveUnitIndex
import luzzr.muse.domain.repository.MediaUsageRepository
import luzzr.muse.domain.repository.ReadAlongRepository
import luzzr.muse.media.BatchedUsageTracker
import luzzr.muse.media.ReadAlongPlaybackEngine
import luzzr.muse.media.ReadAlongPlaybackState
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val READING_USAGE_SAMPLE_INTERVAL_MS = 1_000L

/**
 * Single source of truth for the dedicated read-along surface.
 *
 * The reader consumes only audio and timing data already present in the imported
 * read-along package. It deliberately has no text-to-speech dependency or fallback.
 */
@HiltViewModel
class ReadAlongViewModel @Inject constructor(
    private val repository: ReadAlongRepository,
    private val mediaUsageRepository: MediaUsageRepository,
    private val playback: ReadAlongPlaybackEngine,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sort: ReadAlongSortOrder
        get() = _sort.value
    private val _sort = MutableStateFlow(ReadAlongSortOrder.RECENT)
    val sortFlow: StateFlow<ReadAlongSortOrder> = _sort.asStateFlow()

    val filter: ReadAlongShelfFilter
        get() = _filter.value
    private val _filter = MutableStateFlow(ReadAlongShelfFilter.ALL)
    val filterFlow: StateFlow<ReadAlongShelfFilter> = _filter.asStateFlow()

    val summaries: StateFlow<List<ReadAlongBookSummary>> = _sort
        .flatMapLatest { value -> repository.observeSummaries(value, ReadAlongShelfFilter.ALL) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun applySort(value: ReadAlongSortOrder) {
        _sort.value = value
    }

    fun applyFilter(value: ReadAlongShelfFilter) {
        _filter.value = value
    }

    val stats: StateFlow<ReadAlongReadingStats> = repository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadAlongReadingStats())

    fun observeAnnotations(bookId: String) = repository.observeAnnotations(bookId)
    fun observeBookmarks(bookId: String) = repository.observeBookmarks(bookId)

    private val _shelf = MutableStateFlow(ReadAlongShelfUiState())
    val shelf: StateFlow<ReadAlongShelfUiState> = _shelf.asStateFlow()

    private val _reader = MutableStateFlow(ReadAlongReaderUiState())
    val reader: StateFlow<ReadAlongReaderUiState> = _reader.asStateFlow()

    private var chapterLoadJob: Job? = null
    private var prefetchJob: Job? = null
    private var shelfJob: Job? = null
    private var settingsPersistJob: Job? = null
    private var searchJob: Job? = null
    private var sentencePlaybackJob: Job? = null
    private val readingTracker = BatchedUsageTracker()
    private var readingBookId: String? = null
    private var readingUsageJob: Job? = null

    init {
        viewModelScope.launch {
            playback.state.collect { state -> onPlaybackStateChanged(state) }
        }
        // Re-emit summaries whenever sort/filter changes
        viewModelScope.launch {
            _sort.collect { value ->
                _filter.value.let { current ->
                    observeSummariesAsync(value, current)
                }
            }
        }
        viewModelScope.launch {
            _filter.collect { value ->
                _sort.value.let { current ->
                    observeSummariesAsync(current, value)
                }
            }
        }
    }

    private var summariesFlow: StateFlow<List<ReadAlongBookSummary>>? = null
    private fun observeSummariesAsync(sort: ReadAlongSortOrder, filter: ReadAlongShelfFilter) {
        summariesFlow = repository.observeSummaries(sort, filter)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    val summariesLive: StateFlow<List<ReadAlongBookSummary>>
        get() = summariesFlow ?: summaries

    fun beginReading(bookId: String) {
        if (readingBookId != bookId) {
            readingUsageJob?.cancel()
            readingUsageJob = null
            flushReading()
            readingBookId = bookId
            readingTracker.reset()
        }
        if (_reader.value.book?.id == bookId && _reader.value.chapterData != null) {
            startReadingTracking()
        }
    }

    fun endReading() {
        readingUsageJob?.cancel()
        readingUsageJob = null
        flushReading()
        readingBookId = null
        readingTracker.reset()
    }

    private fun startReadingTracking() {
        if (!readingTracker.start()) return
        readingUsageJob?.cancel()
        readingUsageJob = viewModelScope.launch {
            while (isActive) {
                delay(READING_USAGE_SAMPLE_INTERVAL_MS)
                flushReading(force = false)
            }
        }
    }

    private fun flushReading(force: Boolean = true) {
        val bookId = readingBookId ?: return
        val delta = if (force) readingTracker.pause() else readingTracker.takeBatch()
        if (delta > 0L) {
            viewModelScope.launch {
                mediaUsageRepository.recordRead(bookId, delta)
            }
        }
    }

    // ================== Shelf ==================

    fun importPackage(uri: Uri, displayName: String?, mimeType: String?) {
        runImport {
            val source = ReadAlongImportSource(
                uri = uri.toString(),
                displayName = displayName,
                mimeType = mimeType
            )
            when (val result = repository.importSources(listOf(source))) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> error(result.message ?: "同步书导入失败")
            }
        }
    }

    fun importDocumentTree(treeUri: Uri) {
        runImport {
            when (val result = repository.importDocumentTree(treeUri.toString())) {
                is OperationResult.Success -> {
                    _shelf.update { it.copy(warnings = result.value.flatMap { r -> r.warnings }) }
                    result.value.lastOrNull()
                }
                is OperationResult.Failure -> error(result.message ?: "目录导入失败")
            }
        }
    }

    fun importFromWifi(payload: ByteArray, displayName: String) {
        runImport {
            when (val result = repository.importFromWifi(payload, displayName)) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> error(result.message ?: "WiFi 传书失败")
            }
        }
    }

    fun attachSources(bookId: String, sources: List<ReadAlongImportSource>) {
        runImport {
            when (val result = repository.attachSources(bookId, sources)) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> error(result.message ?: "补齐资源失败")
            }
        }
    }

    private fun runImport(block: suspend () -> ReadAlongImportResult?) {
        if (_shelf.value.isImporting) return
        _shelf.update { it.copy(isImporting = true, error = null, warnings = emptyList()) }
        shelfJob?.cancel()
        shelfJob = viewModelScope.launch {
            try {
                val result = block()
                _shelf.update {
                    it.copy(isImporting = false, importedBook = result?.book, warnings = result?.warnings.orEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _shelf.update { it.copy(isImporting = false, error = e.message ?: "导入失败") }
            }
        }
    }

    fun dismissShelfMessage() {
        _shelf.update { it.copy(importedBook = null, error = null, warnings = emptyList()) }
    }

    fun deleteBook(bookId: String) {
        shelfJob?.cancel()
        shelfJob = viewModelScope.launch {
            try {
                repository.deleteBook(bookId)
                if (_reader.value.book?.id == bookId) {
                    _reader.value = ReadAlongReaderUiState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // repository already cleaned state
            }
        }
    }

    // ================== Reader ==================

    fun openBook(bookId: String) {
        val currentState = _reader.value
        // Skip reload only if player is already loaded with the same audio
        if (currentState.book?.id == bookId && currentState.chapterData != null &&
            playback.state.value.currentChapterId == currentState.chapterData.chapter.id) return
        chapterLoadJob?.cancel()
        sentencePlaybackJob?.cancel()
        playback.stop()
        _reader.value = ReadAlongReaderUiState(isLoading = true)
        chapterLoadJob = viewModelScope.launch {
            try {
                val book = repository.getBook(bookId) ?: run {
                    _reader.update { it.copy(isLoading = false, error = "找不到这本书") }
                    return@launch
                }
                val saved = repository.getProgress(bookId)
                applySettingsToEngines(saved)
                val targetIndex = resolveOpeningChapterIndex(book, saved)
                val totalChapters = book.chapters.size.coerceAtLeast(1)
                _reader.value = ReadAlongReaderUiState(
                    book = book,
                    progress = saved,
                    settings = settingsFrom(saved),
                    isLoading = true,
                    remainingMinutes = estimateRemainingMinutes(book, saved)
                )
                loadChapter(targetIndex, autoPlay = false, persist = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _reader.update { it.copy(isLoading = false, error = e.message ?: "书籍加载失败") }
            }
        }
    }

    fun switchChapter(index: Int, autoPlay: Boolean = true) {
        val book = _reader.value.book ?: return
        val safe = index.coerceIn(0, book.chapters.lastIndex.coerceAtLeast(0))
        if (safe == _reader.value.chapterData?.chapter?.index && !_reader.value.isLoading) return
        chapterLoadJob?.cancel()
        sentencePlaybackJob?.cancel()
        playback.stop()
        _reader.update { it.copy(isLoading = true, error = null) }
        chapterLoadJob = viewModelScope.launch { loadChapter(safe, autoPlay, persist = true) }
    }

    private fun resolveOpeningChapterIndex(book: ReadAlongBook, progress: ReadAlongProgress): Int {
        if (book.chapters.isEmpty()) return 0
        val savedIndex = progress.chapterIndex.coerceIn(0, book.chapters.lastIndex)
        val preferredIndex = book.initialChapterIndex.coerceIn(0, book.chapters.lastIndex)
        val savedChapter = book.chapters[savedIndex]
        val preferredChapter = book.chapters[preferredIndex]
        val hasMeaningfulPosition = progress.audioPositionMs > 0L ||
            progress.characterIndex > 0 ||
            !progress.textLocator.isNullOrBlank()
        return if (!hasMeaningfulPosition &&
            savedIndex != preferredIndex &&
            savedChapter.audioPath == null &&
            preferredChapter.audioPath != null
        ) {
            preferredIndex
        } else {
            savedIndex
        }
    }

    private suspend fun loadChapter(index: Int, autoPlay: Boolean, persist: Boolean) {
        val book = _reader.value.book ?: return
        val chapter = book.chapters.getOrNull(index) ?: run {
            _reader.update { it.copy(isLoading = false, error = "章节不存在") }
            return
        }
        try {
            when (val result = repository.loadChapterData(book.id, index)) {
                is OperationResult.Failure -> _reader.update {
                    it.copy(isLoading = false, error = result.message ?: "章节加载失败")
                }
                is OperationResult.Success -> {
                    val saved = repository.getProgress(book.id)
                    val nextProgress = if (saved.chapterIndex == index) saved else saved.copy(
                        chapterIndex = index,
                        chapterId = result.value.chapter.id,
                        audioPositionMs = 0L,
                        characterIndex = 0,
                        scrollProgress = 0f,
                        pageProgress = 0f,
                        lastReadAt = System.currentTimeMillis()
                    )
                    val textIndex = repository.loadTextIndex(book.id, chapter.href)
                        .let { if (it is OperationResult.Success) it.value else null }
                    val marker = repository.lastMarker(book.id, chapter.id)
                    _reader.update {
                        it.copy(
                            chapterData = result.value,
                            textIndex = textIndex,
                            lastMarker = marker,
                            progress = nextProgress,
                            playbackPositionMs = nextProgress.audioPositionMs,
                            playbackDurationMs = result.value.chapter.audioDurationMs,
                            activeUnitIndex = findActiveUnit(result.value.units, nextProgress.audioPositionMs),
                            activeSentenceIndex = findActiveSentence(result.value, nextProgress.audioPositionMs),
                            isLoading = false,
                            error = null
                        )
                    }
                    if (readingBookId == book.id) {
                        startReadingTracking()
                    }
                    applyPlayback(chapter.id, book.id, result.value, nextProgress.audioPositionMs, autoPlay)
                    prefetchNext(book, index)
                    if (persist) persistProgress(nextProgress)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            _reader.update { it.copy(isLoading = false, error = e.message ?: "章节加载失败") }
        }
    }

    private fun applyPlayback(
        chapterId: String,
        bookId: String,
        data: ReadAlongChapterData,
        positionMs: Long,
        autoPlay: Boolean
    ) {
        val chapter = data.chapter
        val audioFile = chapter.audioPath?.takeIf { it.isNotBlank() }
        if (audioFile == null) {
            // An EPUB without an imported audio track remains readable, but playback
            // is intentionally unavailable. Never synthesize a replacement voice.
            playback.stop()
            _reader.update {
                it.copy(
                    isPlaying = false,
                    playbackPositionMs = 0L,
                    playbackDurationMs = 0L,
                    activeUnitIndex = -1
                )
            }
            return
        }
        playback.load(
            bookId = bookId,
            chapterId = chapterId,
            units = data.units,
            sentenceStartMs = data.sentences.firstOrNull()?.chapterStartMs ?: 0L,
            sentenceEndMs = data.sentences.lastOrNull()?.chapterEndMs ?: 0L,
            audioFile = audioFile,
            initialPositionMs = positionMs,
            autoPlay = autoPlay
        )
    }

    private fun prefetchNext(book: ReadAlongBook, currentIndex: Int) {
        val nextIndex = book.nextReadingChapterIndex(currentIndex) ?: return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.prefetchChapter(book.id, nextIndex) }
            }
        }
    }

    fun togglePlay() {
        val chapter = _reader.value.chapterData?.chapter ?: return
        if (chapter.audioPath.isNullOrBlank()) {
            // Plain EPUB chapters are readable but have no audio to play.
            return
        }
        playback.togglePlay()
    }

    fun seekTo(positionMs: Long) {
        val chapter = _reader.value.chapterData?.chapter ?: return
        val clamped = positionMs.coerceIn(0L, _reader.value.playbackDurationMs.coerceAtLeast(0L))
        if (chapter.audioPath.isNullOrBlank()) {
            return
        }
        playback.seekTo(clamped)
        val nowMs = System.currentTimeMillis()
        val updated = _reader.value.progress.copy(
            audioPositionMs = clamped,
            lastReadAt = nowMs,
            lastListenedAt = nowMs
        )
        _reader.update { it.copy(progress = updated, playbackPositionMs = clamped) }
        persistProgress(updated)
    }

    fun seekToUnit(index: Int) {
        val unit = _reader.value.chapterData?.units?.getOrNull(index) ?: return
        val ms = unit.startMs
        seekTo(ms)
    }

    /** Jump from a long-pressed character range to the nearest imported unit. */
    fun seekToTextRange(charStart: Int, charEnd: Int) {
        val start = charStart.coerceAtLeast(0)
        val end = charEnd.coerceAtLeast(start + 1)
        _reader.update { it.copy(requestedTextRange = start until end) }
        val index = _reader.value.textIndex ?: return
        val unitIndex = index.unitRanges.indexOfFirst { range ->
            start <= range.last && end > range.first
        }
        if (unitIndex >= 0) {
            seekToUnit(unitIndex)
            return
        }
        val sentenceIndex = index.sentenceRanges.indexOfFirst { range ->
            start <= range.last && end > range.first
        }
        if (sentenceIndex >= 0) {
            val sentence = _reader.value.chapterData?.sentences?.getOrNull(sentenceIndex)
            if (sentence != null) seekTo(sentence.chapterStartMs)
        }
    }

    fun clearTextRangeRequest() {
        if (_reader.value.requestedTextRange != null) {
            _reader.update { it.copy(requestedTextRange = null) }
        }
    }

    fun saveScrollProgress(value: Float) {
        val state = _reader.value
        if (state.book == null || state.chapterData == null) return
        val safe = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(state.progress.scrollProgress - safe) < 0.01f) return
        val updated = state.progress.copy(
            scrollProgress = safe,
            lastReadAt = System.currentTimeMillis()
        )
        _reader.update { it.copy(progress = updated) }
        persistProgress(updated)
    }

    fun savePageProgress(value: Float) {
        val state = _reader.value
        if (state.book == null || state.chapterData == null) return
        val safe = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(state.progress.pageProgress - safe) < 0.01f) return
        val updated = state.progress.copy(
            pageProgress = safe,
            lastReadAt = System.currentTimeMillis()
        )
        _reader.update { it.copy(progress = updated) }
        persistProgress(updated)
    }

    fun seekToSentenceByHref(href: String, elementId: String?) {
        val data = _reader.value.chapterData ?: return
        val sentence = data.sentences.firstOrNull { sentence ->
            sentence.epubHref == href && (elementId == null || sentence.elementId == elementId)
        } ?: return
        seekTo(sentence.chapterStartMs)
    }

    fun seekToChapterByHref(href: String) {
        val book = _reader.value.book ?: return
        val target = book.chapters.indexOfFirst { it.href == href }
        if (target >= 0) switchChapter(target, autoPlay = playback.state.value.isPlaying)
    }

    fun setFontScale(value: Float) {
        updateSettings { it.copy(fontScale = value.coerceIn(0.6f, 2.0f)) }
    }

    fun setFontFamily(value: ReadAlongFontFamily) {
        updateSettings { it.copy(fontFamily = value) }
    }

    fun setFontWeight(value: ReadAlongFontWeight) {
        updateSettings { it.copy(fontWeight = value) }
    }

    fun setLineHeightScale(value: Float) {
        updateSettings { it.copy(lineHeightScale = value.coerceIn(1.1f, 2.6f)) }
    }

    fun setParagraphSpacing(value: Float) {
        updateSettings { it.copy(paragraphSpacing = value.coerceIn(0.5f, 2.5f)) }
    }

    fun setPagerMode(value: ReadAlongPagerMode) {
        updateSettings { it.copy(pagerMode = value) }
    }

    fun setTheme(theme: ReadAlongTheme) {
        updateSettings { it.copy(theme = theme) }
    }

    fun setAutoFollow(value: Boolean) {
        updateSettings { it.copy(autoFollow = value) }
    }

    fun setPlaybackSpeed(value: Float) {
        val clamped = value.coerceIn(0.5f, 3f)
        playback.setRate(clamped)
        updateSettings { it.copy(playbackSpeed = clamped) }
    }

    fun saveCurrentProgress() {
        val state = _reader.value
        val updated = state.progress.copy(
            audioPositionMs = state.playbackPositionMs,
            characterIndex = state.activeUnitIndex.coerceAtLeast(0),
            lastReadAt = System.currentTimeMillis()
        )
        _reader.update { it.copy(progress = updated) }
        persistProgress(updated, immediate = true)
    }

    private fun updateSettings(transform: (ReadAlongSettingsState) -> ReadAlongSettingsState) {
        val settings = transform(_reader.value.settings)
        val progress = _reader.value.progress.copy(
            fontScale = settings.fontScale,
            lineHeightScale = settings.lineHeightScale,
            fontFamily = settings.fontFamily,
            fontWeight = settings.fontWeight,
            paragraphSpacing = settings.paragraphSpacing,
            pagerMode = settings.pagerMode,
            theme = settings.theme,
            autoFollow = settings.autoFollow,
            playbackSpeed = settings.playbackSpeed,
            lastReadAt = System.currentTimeMillis()
        )
        _reader.update { it.copy(settings = settings, progress = progress) }
        persistProgress(progress)
    }

    private fun persistProgress(progress: ReadAlongProgress, immediate: Boolean = false) {
        settingsPersistJob?.cancel()
        val job = viewModelScope.launch {
            if (!immediate) delay(200L)
            runCatching { repository.saveProgress(progress) }
            val chapterId = progress.chapterId ?: return@launch
            runCatching {
                repository.recordMarker(
                    ReadAlongMarker(
                        bookId = progress.bookId,
                        chapterId = chapterId,
                        charOffset = progress.characterIndex,
                        unitIndex = progress.characterIndex.coerceAtLeast(0),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        settingsPersistJob = job
    }

    private fun applySettingsToEngines(progress: ReadAlongProgress) {
        playback.setRate(progress.playbackSpeed)
    }

    // ================== Annotations / Bookmarks ==================

    fun addAnnotation(charStart: Int, charEnd: Int, color: ReadAlongAnnotationColor, note: String) {
        val state = _reader.value
        val book = state.book ?: return
        val chapter = state.chapterData?.chapter ?: return
        val sentence = state.chapterData.sentences.firstOrNull { sentence ->
            charStart >= sentence.chapterCharStart && charEnd <= sentence.chapterCharEnd + 1
        }
        val now = System.currentTimeMillis()
        val plainText = state.textIndex?.plainText
            ?: state.chapterData.sentences.joinToString("") { it.sourceText.ifBlank { it.spokenText } }
        val safeStart = charStart.coerceIn(0, plainText.length)
        val safeEnd = charEnd.coerceIn(safeStart, plainText.length)
        val quote = if (safeEnd > safeStart) plainText.substring(safeStart, safeEnd) else ""
        val annotation = ReadAlongAnnotation(
            id = "an-${chapter.id}-${now}",
            bookId = book.id,
            chapterId = chapter.id,
            chapterHref = chapter.href,
            elementId = sentence?.elementId,
            charStart = safeStart,
            charEnd = safeEnd,
            color = color,
            note = note,
            quote = quote,
            createdAt = now,
            updatedAt = now
        )
        viewModelScope.launch { runCatching { repository.upsertAnnotation(annotation) } }
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch { runCatching { repository.deleteAnnotation(id) } }
    }

    fun addBookmark(label: String) {
        val state = _reader.value
        val book = state.book ?: return
        val chapter = state.chapterData?.chapter ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching {
                repository.upsertBookmark(
                    ReadAlongBookmark(
                        id = "bm-${chapter.id}-${now}",
                        bookId = book.id,
                        chapterId = chapter.id,
                        chapterHref = chapter.href,
                        label = label.ifBlank { chapter.title },
                        charOffset = state.activeUnitIndex.coerceAtLeast(0),
                        audioPositionMs = state.playbackPositionMs,
                        createdAt = now
                    )
                )
            }
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch { runCatching { repository.deleteBookmark(id) } }
    }

    fun exportAnnotations(bookId: String, format: AnnotationExportFormat): String? =
        runCatching {
            kotlinx.coroutines.runBlocking { repository.exportAnnotations(bookId, format) }
        }.getOrNull()

    fun searchBook(bookId: String, query: String) {
        if (query.isBlank()) {
            _reader.update { it.copy(searchResults = emptyList(), searchActive = false) }
            return
        }
        _reader.update { it.copy(searchActive = true, searchResults = emptyList()) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val results = repository.searchBook(bookId, query)
            _reader.update { it.copy(searchResults = results) }
        }
    }

    fun clearSearch() {
        _reader.update { it.copy(searchResults = emptyList(), searchActive = false) }
    }

    fun jumpToSearchHit(hit: ReadAlongSearchHit) {
        val book = _reader.value.book ?: return
        val chapterIndex = book.chapters.indexOfFirst { chapter ->
            chapter.id == hit.chapterId || chapter.href == hit.chapterHref
        }
        if (chapterIndex < 0) return
        if (_reader.value.chapterData?.chapter?.id == hit.chapterId && !_reader.value.isLoading) {
            seekToTextRange(hit.charStart, hit.charEnd)
            return
        }
        chapterLoadJob?.cancel()
        sentencePlaybackJob?.cancel()
        playback.stop()
        _reader.update { it.copy(isLoading = true, error = null) }
        chapterLoadJob = viewModelScope.launch {
            loadChapter(chapterIndex, autoPlay = false, persist = true)
            if (_reader.value.chapterData?.chapter?.id == hit.chapterId) {
                seekToTextRange(hit.charStart, hit.charEnd)
            }
        }
    }

    private suspend fun onPlaybackStateChanged(state: ReadAlongPlaybackState) {
        val current = _reader.value
        val data = current.chapterData ?: return
        if (state.currentChapterId != null && state.currentChapterId != data.chapter.id) return
        if (state.isEnded) {
            val book = current.book
            val index = book?.chapters?.indexOfFirst { it.id == data.chapter.id } ?: -1
            if (endedHandledChapterId != data.chapter.id) {
                endedHandledChapterId = data.chapter.id
                if (book != null && index >= 0 && index < book.chapters.lastIndex) {
                    switchChapter(index + 1, autoPlay = true)
                    return
                }
            }
        } else if (state.isPlaying) {
            endedHandledChapterId = null
        }
        val active = findActiveUnit(data.units, state.positionMs)
        val now = System.currentTimeMillis()
        val sentence = sentenceForUnit(data, active)
        val locator = sentence?.let { s -> listOfNotNull(s.epubHref, s.elementId).joinToString("#") }
        val next = current.progress.copy(
            audioPositionMs = state.positionMs,
            chapterId = data.chapter.id,
            characterIndex = active.coerceAtLeast(0),
            textLocator = locator ?: current.progress.textLocator,
            lastReadAt = if (active != current.progress.characterIndex) now else current.progress.lastReadAt,
            lastListenedAt = if (state.isPlaying) now else current.progress.lastListenedAt,
            playbackSpeed = state.speed
        )
        val remainingMinutes = estimateRemainingMinutes(current.book, next, state.positionMs, state.durationMs)
        _reader.value = current.copy(
            progress = next,
            playbackPositionMs = state.positionMs,
            playbackDurationMs = state.durationMs,
            isPlaying = state.isPlaying,
            activeUnitIndex = active,
            activeSentenceIndex = findActiveSentence(data, state.positionMs),
            remainingMinutes = remainingMinutes
        )
        if (state.currentChapterId != null && now - _lastPersistAt > 1_000L) {
            _lastPersistAt = now
            settingsPersistJob?.cancel()
            settingsPersistJob = viewModelScope.launch { runCatching { repository.saveProgress(next) } }
        }
    }

    private var _lastPersistAt: Long = 0L
    private var endedHandledChapterId: String? = null

    override fun onCleared() {
        readingUsageJob?.cancel()
        flushReading()
        chapterLoadJob?.cancel()
        prefetchJob?.cancel()
        shelfJob?.cancel()
        settingsPersistJob?.cancel()
        searchJob?.cancel()
        sentencePlaybackJob?.cancel()
        playback.stop()
        super.onCleared()
    }

    private fun sentenceForUnit(data: ReadAlongChapterData, unitIndex: Int) =
        if (unitIndex < 0) null else run {
            var cursor = 0
            for (sentence in data.sentences) {
                if (unitIndex < cursor + sentence.units.size) return@run sentence
                cursor += sentence.units.size
            }
            null
        }

    private fun findActiveUnit(units: List<luzzr.muse.domain.model.ReadAlongUnit>, positionMs: Long): Int =
        readAlongActiveUnitIndex(units, positionMs)

    private fun findActiveSentence(data: ReadAlongChapterData, positionMs: Long): Int =
        data.sentences.indexOfFirst { positionMs >= it.chapterStartMs && positionMs < it.chapterEndMs }
            .takeIf { it >= 0 }
            ?: data.sentences.indexOfLast { positionMs >= it.chapterStartMs }
            .takeIf { it >= 0 }
            ?: -1

    private fun settingsFrom(progress: ReadAlongProgress): ReadAlongSettingsState = ReadAlongSettingsState(
        fontScale = progress.fontScale,
        lineHeightScale = progress.lineHeightScale,
        fontFamily = progress.fontFamily,
        fontWeight = progress.fontWeight,
        paragraphSpacing = progress.paragraphSpacing,
        pagerMode = progress.pagerMode,
        theme = progress.theme,
        autoFollow = progress.autoFollow,
        playbackSpeed = progress.playbackSpeed
    )

    private fun estimateRemainingMinutes(
        book: ReadAlongBook?,
        progress: ReadAlongProgress,
        positionMs: Long = progress.audioPositionMs,
        durationMs: Long = 0L
    ): Int? {
        val totalChars = book?.chapters?.sumOf { it.sourceChars } ?: return null
        if (totalChars <= 0) return null
        val chapter = book.chapters.getOrNull(progress.chapterIndex) ?: return null
        val remainingChars = chapter.sourceChars.coerceAtLeast(0)
        val effective = durationMs.coerceAtLeast(chapter.audioDurationMs).coerceAtLeast(0L)
        if (effective <= 0L || positionMs <= 0L) {
            // crude estimate: 400 chars / min
            return (remainingChars / 400).coerceAtLeast(0)
        }
        val ratio = positionMs.toDouble() / effective.toDouble()
        val charsPerMs = chapter.sourceChars.toDouble() / effective.toDouble()
        val remainingMs = ((remainingChars - positionMs * charsPerMs) / charsPerMs).coerceAtLeast(0.0)
        return (remainingMs / 60_000.0).toInt().coerceAtLeast(0)
    }
}

data class ReadAlongShelfUiState(
    val isImporting: Boolean = false,
    val importedBook: ReadAlongBook? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null
)

data class ReadAlongReaderUiState(
    val book: ReadAlongBook? = null,
    val chapterData: ReadAlongChapterData? = null,
    val textIndex: luzzr.muse.domain.model.ReadAlongTextIndex? = null,
    val lastMarker: ReadAlongMarker? = null,
    val progress: ReadAlongProgress = ReadAlongProgress(bookId = ""),
    val settings: ReadAlongSettingsState = ReadAlongSettingsState(),
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val activeUnitIndex: Int = -1,
    val activeSentenceIndex: Int = -1,
    val requestedTextRange: IntRange? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchActive: Boolean = false,
    val searchResults: List<ReadAlongSearchHit> = emptyList(),
    val remainingMinutes: Int? = null
)

data class ReadAlongSettingsState(
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
