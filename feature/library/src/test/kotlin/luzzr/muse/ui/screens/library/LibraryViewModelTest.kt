package luzzr.muse.ui.screens.library

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.LyricsRepository
import luzzr.muse.domain.repository.PlaylistRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.domain.text.TextNormalizer
import luzzr.muse.domain.usecase.ApplyMetadataUseCase
import luzzr.muse.domain.usecase.ClearLyricsCacheUseCase
import luzzr.muse.domain.usecase.DeleteLyricsUseCase
import luzzr.muse.domain.usecase.DeleteSongUseCase
import luzzr.muse.domain.usecase.EditSongMetadataUseCase
import luzzr.muse.domain.usecase.FetchLyricsUseCase
import luzzr.muse.domain.usecase.GetAlbumsUseCase
import luzzr.muse.domain.usecase.GetArtistsUseCase
import luzzr.muse.domain.usecase.GetSongsByAlbumUseCase
import luzzr.muse.domain.usecase.GetSongsByArtistUseCase
import luzzr.muse.domain.usecase.RefreshAlbumAndArtistTablesUseCase
import luzzr.muse.domain.usecase.SearchMetadataUseCase
import luzzr.muse.domain.usecase.SearchSongsUseCase
import luzzr.muse.domain.usecase.UpdateSongArtworkUseCase
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackState
import luzzr.muse.ui.R as CoreUiR
import luzzr.muse.ui.state.StoragePermissionController
import luzzr.muse.ui.state.UiText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)
    private val artworkRepository: ArtworkRepository = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val storagePermissionController: StoragePermissionController = mockk(relaxed = true)
    private val deleteSongUseCase: DeleteSongUseCase = mockk(relaxed = true)
    private val searchMetadataUseCase: SearchMetadataUseCase = mockk(relaxed = true)
    private val clearLyricsCacheUseCase: ClearLyricsCacheUseCase = mockk(relaxed = true)
    private val applyMetadataUseCase: ApplyMetadataUseCase = mockk(relaxed = true)
    private val updateSongArtworkUseCase: UpdateSongArtworkUseCase = mockk(relaxed = true)
    private val deleteLyricsUseCase: DeleteLyricsUseCase = mockk(relaxed = true)
    private val refreshAlbumAndArtistTablesUseCase: RefreshAlbumAndArtistTablesUseCase = mockk(relaxed = true)
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { songRepository.songs } returns MutableStateFlow(emptyList())
        every { songRepository.isScanning } returns MutableStateFlow(false)
        every { artworkRepository.coverGenerationCompleted } returns MutableSharedFlow()
        every { playbackController.state } returns MutableStateFlow(PlaybackState())

        val getAlbumsUseCase: GetAlbumsUseCase = mockk(relaxed = true)
        val getArtistsUseCase: GetArtistsUseCase = mockk(relaxed = true)
        coEvery { getAlbumsUseCase() } returns emptyList()
        coEvery { getArtistsUseCase() } returns emptyList()
        val textNormalizer: TextNormalizer = mockk()
        every { textNormalizer.toSimplified(any()) } answers { firstArg<String>() }

        viewModel = LibraryViewModel(
            songRepository = songRepository,
            playlistRepository = playlistRepository,
            artworkRepository = artworkRepository,
            playbackController = playbackController,
            storagePermissionController = storagePermissionController,
            searchMetadataUseCase = searchMetadataUseCase,
            textNormalizer = textNormalizer,
            clearLyricsCacheUseCase = clearLyricsCacheUseCase,
            fetchLyricsUseCase = mockk<FetchLyricsUseCase>(relaxed = true),
            lyricsRepository = mockk<LyricsRepository>(relaxed = true),
            playbackActionController = mockk<PlaybackActionController>(relaxed = true),
            editSongMetadataUseCase = mockk<EditSongMetadataUseCase>(relaxed = true),
            getAlbumsUseCase = getAlbumsUseCase,
            getArtistsUseCase = getArtistsUseCase,
            getSongsByAlbumUseCase = mockk<GetSongsByAlbumUseCase>(relaxed = true),
            getSongsByArtistUseCase = mockk<GetSongsByArtistUseCase>(relaxed = true),
            searchSongsUseCase = mockk<SearchSongsUseCase>(relaxed = true),
            applyMetadataUseCase = applyMetadataUseCase,
            updateSongArtworkUseCase = updateSongArtworkUseCase,
            deleteLyricsUseCase = deleteLyricsUseCase,
            refreshAlbumAndArtistTablesUseCase = refreshAlbumAndArtistTablesUseCase,
            deleteSongUseCase = deleteSongUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `failed delete keeps dialog open and exposes typed error`() = runTest {
        val song = Song(id = 1, title = "保留我", uri = "content://song/1")
        coEvery { deleteSongUseCase(song) } returns OperationResult.Failure(OperationError.IO)

        viewModel.requestDeleteSong(song)
        viewModel.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertEquals(song, viewModel.songToDelete.value)
        assertEquals(UiText.Resource(CoreUiR.string.error_io), viewModel.deleteError.value)
    }

    @Test
    fun `successful delete closes dialog and clears error`() = runTest {
        val song = Song(id = 2, title = "删除我", uri = "content://song/2")
        coEvery { deleteSongUseCase(song) } returns OperationResult.Success(Unit)

        viewModel.requestDeleteSong(song)
        viewModel.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.songToDelete.value)
        assertNull(viewModel.deleteError.value)
    }

    @Test
    fun `unexpected delete error keeps dialog open and exposes unknown error`() = runTest {
        val song = Song(id = 22, title = "异常文件", uri = "content://song/22")
        coEvery { deleteSongUseCase(song) } throws IllegalStateException("bad media row")

        viewModel.requestDeleteSong(song)
        viewModel.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertEquals(song, viewModel.songToDelete.value)
        assertEquals(UiText.Resource(CoreUiR.string.error_unknown), viewModel.deleteError.value)
    }

    @Test
    fun `failed metadata apply keeps sheet open and skips dependent writes`() = runTest {
        val song = Song(id = 3, title = "旧标题", artist = "旧歌手", uri = "content://song/3")
        val metadata = MetadataResult(
            title = "新标题",
            artist = "新歌手",
            coverUrl = "https://example.com/cover.jpg"
        )
        val errorCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.metadataError.collect()
        }
        val songCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.metadataSong.collect()
        }
        coEvery { searchMetadataUseCase(song.title, song.artist) } returns listOf(metadata)
        coEvery { applyMetadataUseCase(song, metadata) } returns OperationResult.Failure(OperationError.IO)

        viewModel.searchMetadata(song)
        testScheduler.advanceUntilIdle()
        viewModel.applyMetadataResult(metadata)
        testScheduler.advanceUntilIdle()

        assertEquals(song, viewModel.metadataSong.value)
        assertEquals(UiText.Resource(CoreUiR.string.error_io), viewModel.metadataError.value)
        coVerify(exactly = 0) { deleteLyricsUseCase(any()) }
        verify(exactly = 0) { clearLyricsCacheUseCase() }
        coVerify(exactly = 0) { artworkRepository.downloadBytes(any()) }
        coVerify(exactly = 0) { updateSongArtworkUseCase(any(), any()) }
        coVerify(exactly = 0) { refreshAlbumAndArtistTablesUseCase() }

        errorCollector.cancel()
        songCollector.cancel()
    }
}
