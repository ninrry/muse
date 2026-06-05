package luzzr.muse.ui.screens.audiobook

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.domain.repository.SongRepository
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

import luzzr.muse.domain.usecase.EditSongMetadataUseCase
import luzzr.muse.domain.usecase.UpdateSongArtworkUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookViewModelTest {

    private lateinit var viewModel: AudiobookViewModel
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val bookCollectionRepo: BookCollectionRepository = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val playbackActionController: PlaybackActionController = mockk(relaxed = true)
    private val editSongMetadataUseCase: EditSongMetadataUseCase = mockk(relaxed = true)
    private val updateSongArtworkUseCase: UpdateSongArtworkUseCase = mockk(relaxed = true)

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
            updateSongArtworkUseCase = updateSongArtworkUseCase
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
}
