package luzzr.muse.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionImportFailureStage
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.model.EbookMetadata
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.domain.repository.SongRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.test.runTest

class ImportBookCollectionMetadataUseCaseTest {
    private val collectionRepository = mockk<BookCollectionRepository>()
    private val songRepository = mockk<SongRepository>()
    private val artworkRepository = mockk<ArtworkRepository>()
    private val useCase = ImportBookCollectionMetadataUseCase(collectionRepository, songRepository, artworkRepository)

    @Test
    fun `uses sort order and continues after chapter failure`() = runTest {
        val first = Song(id = 1, title = "old one", artist = "old", album = "old")
        val second = Song(id = 2, title = "old two", artist = "old", album = "old")
        val cover = byteArrayOf(1)
        coEvery { collectionRepository.getCollection(7) } returns BookCollection(id = 7, name = "Existing")
        coEvery { collectionRepository.updateCollectionMetadata(7, "Book", "Author", cover) } returns OperationResult.Success(Unit)
        coEvery { collectionRepository.getItemsForCollectionSync(7) } returns listOf(
            BookCollectionItem(first, 2),
            BookCollectionItem(second, 9)
        )
        coEvery { songRepository.updateSongTags(first, "Book 02", "Author", "Book", null, "") } returns
            OperationResult.Failure(OperationError.IO, "read only")
        coEvery { songRepository.updateSongTags(second, "Book 09", "Author", "Book", null, "") } returns
            OperationResult.Success(Unit)
        coEvery { artworkRepository.updateSongArtwork(any(), cover) } returns OperationResult.Success(Unit)
        coEvery { songRepository.refreshAlbumAndArtistTables() } returns Unit

        val result = useCase(7, EbookMetadata("Book", "Author", cover)) as OperationResult.Success

        assertEquals(2, result.value.totalCount)
        assertEquals(1, result.value.successCount)
        assertEquals(BookCollectionImportFailureStage.METADATA, result.value.failures.single().stage)
        coVerify(exactly = 0) { artworkRepository.updateSongArtwork(match { it.id == 1L }, any()) }
        coVerify { artworkRepository.updateSongArtwork(match { it.id == 2L && it.title == "Book 09" }, cover) }
        coVerify { songRepository.refreshAlbumAndArtistTables() }
    }

    @Test
    fun `keeps collection title author and chapter artists when epub fields are missing`() = runTest {
        val song = Song(id = 1, title = "Chapter", artist = "Narrator", year = 2020, genre = "Book")
        coEvery { collectionRepository.getCollection(3) } returns BookCollection(id = 3, name = "Existing", author = "Writer")
        coEvery { collectionRepository.updateCollectionMetadata(3, "Existing", "Writer", null) } returns OperationResult.Success(Unit)
        coEvery { collectionRepository.getItemsForCollectionSync(3) } returns listOf(BookCollectionItem(song, 1))
        coEvery {
            songRepository.updateSongTags(song, "Existing 01", "Narrator", "Existing", 2020, "Book")
        } returns OperationResult.Success(Unit)
        coEvery { songRepository.refreshAlbumAndArtistTables() } returns Unit

        val result = useCase(3, EbookMetadata()) as OperationResult.Success

        assertEquals(1, result.value.successCount)
        coVerify(exactly = 0) { artworkRepository.updateSongArtwork(any(), any()) }
    }
}
