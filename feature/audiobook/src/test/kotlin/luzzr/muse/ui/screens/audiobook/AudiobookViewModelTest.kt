package luzzr.muse.ui.screens.audiobook

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionImportResult
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.model.EbookMetadata
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.domain.repository.EbookMetadataRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.domain.usecase.EditSongMetadataUseCase
import luzzr.muse.domain.usecase.ImportBookCollectionMetadataUseCase
import luzzr.muse.domain.usecase.UpdateSongArtworkUseCase
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookViewModelTest {

    private lateinit var viewModel: AudiobookViewModel
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val bookCollectionRepo: BookCollectionRepository = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val playbackActionController: PlaybackActionController = mockk(relaxed = true)
    private val editSongMetadataUseCase: EditSongMetadataUseCase = mockk(relaxed = true)
    private val updateSongArtworkUseCase: UpdateSongArtworkUseCase = mockk(relaxed = true)
    private val ebookMetadataRepository: EbookMetadataRepository = mockk(relaxed = true)
    private val importBookCollectionMetadataUseCase: ImportBookCollectionMetadataUseCase = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val audiobooks = MutableStateFlow<List<Song>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { songRepository.audiobooks } returns audiobooks
        every { bookCollectionRepo.getAllCollections() } returns flowOf(emptyList())
        every { bookCollectionRepo.getItemsForCollection(any()) } returns flowOf(emptyList())

        viewModel = AudiobookViewModel(
            songRepository = songRepository,
            bookCollectionRepo = bookCollectionRepo,
            playbackController = playbackController,
            playbackActionController = playbackActionController,
            editSongMetadataUseCase = editSongMetadataUseCase,
            updateSongArtworkUseCase = updateSongArtworkUseCase,
            ebookMetadataRepository = ebookMetadataRepository,
            importBookCollectionMetadataUseCase = importBookCollectionMetadataUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `playAudiobook plays single song through playback action controller`() {
        val song = Song(id = 1L, title = "Chapter 1")

        viewModel.playAudiobook(song)

        verify { playbackActionController.playSongAtIndex(listOf(song), 0) }
    }

    @Test
    fun `playCollection plays collection songs from requested index`() {
        val first = Song(id = 1L, title = "Chapter 1")
        val second = Song(id = 2L, title = "Chapter 2")
        val items = listOf(
            BookCollectionItem(song = first, sortOrder = 1),
            BookCollectionItem(song = second, sortOrder = 2)
        )

        viewModel.playCollection(items, startIndex = 1)

        verify { playbackActionController.playSongAtIndex(listOf(first, second), 1) }
    }

    @Test
    fun `playCollection ignores empty collection`() {
        viewModel.playCollection(emptyList())

        verify(exactly = 0) { playbackActionController.playSongAtIndex(any(), any()) }
    }

    @Test
    fun `updateSongSortOrder clamps out of range values`() = runTest(testDispatcher) {
        coEvery { bookCollectionRepo.updateItemSortOrder(any(), any(), any()) } returns Unit

        viewModel.updateSongSortOrder(collectionId = 7L, songId = 9L, sortOrder = 120)
        testScheduler.advanceUntilIdle()

        coVerify { bookCollectionRepo.updateItemSortOrder(7L, 9L, 99) }
    }

    @Test
    fun `deleteCollection clears selected collection before deleting`() = runTest(testDispatcher) {
        viewModel.selectCollection(7L)

        viewModel.deleteCollection(7L)
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.selectedCollectionId.value)
        coVerify { bookCollectionRepo.deleteCollection(7L) }
    }

    @Test
    fun `getSavedProgressPercent returns clamped integer percent`() {
        every { playbackController.getSavedSongProgress(1L) } returns 2_500L

        val percent = viewModel.getSavedProgressPercent(Song(id = 1L, duration = 10_000L))

        assertEquals(25, percent)
    }

    @Test
    fun `ebook preview uses collection title when epub title is missing`() = runTest(testDispatcher) {
        coEvery { ebookMetadataRepository.extract("content://book", "book.epub", "application/epub+zip") } returns
            OperationResult.Success(EbookMetadata(author = "Writer"))
        coEvery { bookCollectionRepo.getCollection(7L) } returns BookCollection(id = 7L, name = "Existing")
        coEvery { bookCollectionRepo.getItemsForCollectionSync(7L) } returns listOf(
            BookCollectionItem(Song(id = 1L), sortOrder = 3)
        )
        viewModel.selectCollection(7L)

        viewModel.requestEbookPreview("content://book", "book.epub", "application/epub+zip")
        testScheduler.advanceUntilIdle()

        assertEquals("Existing", viewModel.importState.value.preview?.finalTitle)
        assertEquals("Existing 03", viewModel.importState.value.preview?.exampleTitle)
    }

    @Test
    fun `confirm ebook import ignores duplicate request while running`() = runTest(testDispatcher) {
        val metadata = EbookMetadata(title = "Book")
        coEvery { ebookMetadataRepository.extract(any(), any(), any()) } returns OperationResult.Success(metadata)
        coEvery { bookCollectionRepo.getCollection(7L) } returns BookCollection(id = 7L, name = "Existing")
        coEvery { bookCollectionRepo.getItemsForCollectionSync(7L) } returns emptyList()
        coEvery { importBookCollectionMetadataUseCase(7L, metadata, any()) } returns OperationResult.Success(
            BookCollectionImportResult(totalCount = 0, successCount = 0, failures = emptyList())
        )
        viewModel.selectCollection(7L)
        viewModel.requestEbookPreview("content://book", "book.epub", "application/epub+zip")
        testScheduler.advanceUntilIdle()

        viewModel.confirmEbookImport()
        viewModel.confirmEbookImport()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { importBookCollectionMetadataUseCase(7L, metadata, any()) }
        assertEquals(0, viewModel.importState.value.result?.totalCount)
    }
}
