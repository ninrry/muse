package luzzr.muse.ui.screens.audiobook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.ReadAlongBook
import luzzr.muse.domain.model.ReadAlongChapter
import luzzr.muse.domain.model.ReadAlongChapterData
import luzzr.muse.domain.model.ReadAlongImportResult
import luzzr.muse.domain.model.ReadAlongImportSource
import luzzr.muse.domain.model.ReadAlongProgress
import luzzr.muse.domain.model.ReadAlongSentence
import luzzr.muse.domain.model.ReadAlongUnit
import luzzr.muse.domain.repository.ReadAlongRepository
import luzzr.muse.media.ReadAlongPlaybackEngine
import luzzr.muse.media.ReadAlongPlaybackState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadAlongViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ReadAlongRepository>(relaxed = true)
    private val playback = mockk<ReadAlongPlaybackEngine>(relaxed = true)
    private val playbackState = MutableStateFlow(ReadAlongPlaybackState())
    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: ReadAlongViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeBooks() } returns flowOf(emptyList())
        every { repository.observeProgress() } returns flowOf(emptyMap())
        every { repository.observeStats() } returns flowOf(luzzr.muse.domain.model.ReadAlongReadingStats())
        every { repository.observeSummaries(any(), any()) } returns flowOf(emptyList())
        every { repository.observeAnnotations(any()) } returns flowOf(emptyList())
        every { repository.observeBookmarks(any()) } returns flowOf(emptyList())
        every { playback.state } returns playbackState
        viewModel = ReadAlongViewModel(repository, playback, SavedStateHandle())
        viewModelStore.put("reader", viewModel)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `openBook restores saved chapter and position`() = runTest(dispatcher) {
        val book = TestData.book()
        val progress = ReadAlongProgress(bookId = book.id, chapterIndex = 0, chapterId = "ch001", audioPositionMs = 420L)
        coEvery { repository.getBook(book.id) } returns book
        coEvery { repository.getProgress(book.id) } returns progress
        coEvery { repository.loadChapterData(book.id, 0) } returns
            OperationResult.Success(TestData.chapterData())

        viewModel.openBook(book.id)
        advanceUntilIdle()

        assertEquals(book.id, viewModel.reader.value.book?.id)
        assertEquals(420L, viewModel.reader.value.playbackPositionMs)
    }

    @Test
    fun `playback position selects active character unit`() = runTest(dispatcher) {
        val book = TestData.book()
        coEvery { repository.getBook(book.id) } returns book
        coEvery { repository.getProgress(book.id) } returns ReadAlongProgress(bookId = book.id)
        coEvery { repository.loadChapterData(book.id, 0) } returns
            OperationResult.Success(TestData.chapterData())

        viewModel.openBook(book.id)
        advanceUntilIdle()
        playbackState.value = ReadAlongPlaybackState(
            currentChapterId = "ch001",
            positionMs = 140L,
            durationMs = 900L,
            isPlaying = true
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.reader.value.activeUnitIndex)
    }

    @Test
    fun `manual seek writes the listening position`() = runTest(dispatcher) {
        val book = TestData.book()
        coEvery { repository.getBook(book.id) } returns book
        coEvery { repository.getProgress(book.id) } returns ReadAlongProgress(bookId = book.id)
        coEvery { repository.loadChapterData(book.id, 0) } returns
            OperationResult.Success(TestData.chapterData())

        viewModel.openBook(book.id)
        advanceUntilIdle()
        viewModel.seekTo(780L)
        advanceUntilIdle()

        verify { playback.seekTo(780L) }
        coVerify { repository.saveProgress(match { it.audioPositionMs == 780L }) }
    }

    @Test
    fun `openBook skips untouched legacy cover progress for first synchronized chapter`() = runTest(dispatcher) {
        val book = TestData.bookWithFrontMatter()
        coEvery { repository.getBook(book.id) } returns book
        coEvery { repository.getProgress(book.id) } returns ReadAlongProgress(bookId = book.id, chapterIndex = 0, chapterId = "ch001")
        coEvery { repository.loadChapterData(book.id, 1) } returns OperationResult.Success(
            ReadAlongChapterData(book.chapters[1], emptyList())
        )

        viewModel.openBook(book.id)
        advanceUntilIdle()

        assertEquals("ch004", viewModel.reader.value.chapterData?.chapter?.id)
    }

    @Test
    fun `switchChapter cancels previous load and replaces current chapter`() = runTest(dispatcher) {
        val book = TestData.book(multiple = true)
        coEvery { repository.getBook(book.id) } returns book
        coEvery { repository.getProgress(book.id) } returns ReadAlongProgress(bookId = book.id)
        coEvery { repository.loadChapterData(book.id, 0) } returns
            OperationResult.Success(TestData.chapterData(index = 0))
        coEvery { repository.loadChapterData(book.id, 1) } returns
            OperationResult.Success(TestData.chapterData(index = 1))

        viewModel.openBook(book.id)
        advanceUntilIdle()
        viewModel.switchChapter(1, autoPlay = true)
        advanceUntilIdle()

        assertEquals(1, viewModel.reader.value.chapterData?.chapter?.index)
    }

    @Test
    fun `deleteBook invokes repository and clears reader state`() = runTest(dispatcher) {
        viewModel.deleteBook("book-1")
        advanceUntilIdle()
        coVerify { repository.deleteBook("book-1") }
        assertNull(viewModel.reader.value.book)
    }

    @Test
    fun `importPackage transitions through loading state and stores result`() = runTest(dispatcher) {
        val result = TestData.importResult()
        coEvery { repository.importSources(any()) } returns OperationResult.Success(result)
        val uri = mockk<android.net.Uri>()
        every { uri.toString() } returns "content://test/sample"

        viewModel.importPackage(uri, "sample.readalong.zip", "application/zip")
        advanceUntilIdle()

        assertEquals(result.book, viewModel.shelf.value.importedBook)
        assertEquals(false, viewModel.shelf.value.isImporting)
    }

    @Test
    fun `addAnnotation calls repository with computed quote`() = runTest(dispatcher) {
        val book = TestData.book()
        coEvery { repository.getBook(book.id) } returns book
        coEvery { repository.getProgress(book.id) } returns ReadAlongProgress(bookId = book.id)
        coEvery { repository.loadChapterData(book.id, 0) } returns
            OperationResult.Success(TestData.chapterData())
        viewModel.openBook(book.id)
        advanceUntilIdle()

        viewModel.addAnnotation(
            charStart = 0,
            charEnd = 2,
            color = luzzr.muse.domain.model.ReadAlongAnnotationColor.YELLOW,
            note = "重要"
        )
        advanceUntilIdle()

        coVerify {
            repository.upsertAnnotation(match {
                it.quote == "你好" && it.note == "重要" && it.charStart == 0 && it.charEnd == 2
            })
        }
    }

    @Test
    fun `searchBook updates results`() = runTest(dispatcher) {
        val hit = luzzr.muse.domain.model.ReadAlongSearchHit(
            bookId = "b", chapterId = "c", chapterHref = "OEBPS/c.xhtml", elementId = null,
            charStart = 0, charEnd = 1, excerpt = "你"
        )
        coEvery { repository.searchBook("b", "你") } returns listOf(hit)
        viewModel.searchBook("b", "你")
        advanceUntilIdle()
        assertTrue(viewModel.reader.value.searchResults.contains(hit))
    }

    @Test
    fun `applySort and applyFilter update flow values`() = runTest(dispatcher) {
        viewModel.applySort(luzzr.muse.domain.model.ReadAlongSortOrder.TITLE)
        viewModel.applyFilter(luzzr.muse.domain.model.ReadAlongShelfFilter.SYNCED)
        assertEquals(luzzr.muse.domain.model.ReadAlongSortOrder.TITLE, viewModel.sort)
        assertEquals(luzzr.muse.domain.model.ReadAlongShelfFilter.SYNCED, viewModel.filter)
    }
}

private object TestData {
    fun book(multiple: Boolean = false): ReadAlongBook = ReadAlongBook(
        id = "book-1",
        title = "测试书",
        author = "作者",
        epubPath = File("/tmp/book.epub").path,
        packageRoot = File("/tmp/readalong").path,
        coverPath = null,
        chapters = if (multiple) listOf(
            ReadAlongChapter("ch001", "第一章", 0, "OEBPS/ch001.xhtml", "/tmp/ch001.xhtml", "/tmp/ch001.m4a", 900L, 4),
            ReadAlongChapter("ch002", "第二章", 1, "OEBPS/ch002.xhtml", "/tmp/ch002.xhtml", "/tmp/ch002.m4a", 900L, 4)
        ) else listOf(
            ReadAlongChapter("ch001", "第一章", 0, "OEBPS/ch001.xhtml", "/tmp/ch001.xhtml", "/tmp/ch001.m4a", 900L, 4)
        ),
        toc = emptyList(),
        isSynchronized = true,
        sourceFingerprint = "fingerprint",
        createdAt = 1L,
        updatedAt = 1L
    )

    fun bookWithFrontMatter(): ReadAlongBook = ReadAlongBook(
        id = "book-legacy",
        title = "测试书",
        author = "作者",
        epubPath = File("/tmp/book.epub").path,
        packageRoot = File("/tmp/readalong").path,
        coverPath = null,
        chapters = listOf(
            ReadAlongChapter(
                id = "ch001", title = "封面", index = 0, href = "OEBPS/cover.xhtml",
                htmlPath = "/tmp/cover.xhtml", audioPath = null, audioDurationMs = 0L, sourceChars = 0,
                isReadingContent = false
            ),
            ReadAlongChapter(
                id = "ch004", title = "第一章", index = 1, href = "OEBPS/chapter-1.xhtml",
                htmlPath = "/tmp/chapter-1.xhtml", audioPath = "/tmp/ch004.m4a", audioDurationMs = 900L,
                sourceChars = 4, isReadingContent = true, hasAlignment = true
            )
        ),
        toc = emptyList(),
        isSynchronized = true,
        sourceFingerprint = "legacy-fingerprint",
        createdAt = 1L,
        updatedAt = 1L
    )

    fun chapterData(index: Int = 0): ReadAlongChapterData = ReadAlongChapterData(
        chapter = book(multiple = index == 1).chapters[index],
        sentences = listOf(
            ReadAlongSentence(
                id = "s1",
                chapterId = if (index == 0) "ch001" else "ch002",
                sourceText = "你好",
                spokenText = "你好",
                epubHref = if (index == 0) "OEBPS/ch001.xhtml" else "OEBPS/ch002.xhtml",
                elementId = "p1",
                elementPath = null,
                chapterCharStart = 0,
                chapterCharEnd = 2,
                quoteExact = "你好",
                chapterStartMs = 0L,
                chapterEndMs = 200L,
                units = listOf(
                    ReadAlongUnit("你", 0L, 100L),
                    ReadAlongUnit("好", 100L, 200L)
                )
            )
        )
    )

    fun importResult(): ReadAlongImportResult {
        val b = book()
        return ReadAlongImportResult(book = b, importedAudioCount = 0, hasAlignment = true)
    }
}
