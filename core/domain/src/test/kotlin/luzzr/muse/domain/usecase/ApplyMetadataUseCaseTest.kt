package luzzr.muse.domain.usecase

import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.core.result.isSuccess
import luzzr.muse.domain.model.Album
import luzzr.muse.domain.model.Artist
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class ApplyMetadataUseCaseTest {

    private val song = Song(id = 7, title = "Old", artist = "Artist", uri = "content://song/7")

    @Test
    fun `invalid metadata returns failure without touching repository`() = runBlocking {
        val repository = FakeSongRepository()
        val useCase = ApplyMetadataUseCase(repository)

        val result = useCase(song, MetadataResult(title = "", artist = "Artist"))

        assertFalse(result.isSuccess)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun `repository failure is surfaced to caller`() = runBlocking {
        val repository = FakeSongRepository(
            updateResult = OperationResult.Failure(OperationError.IO, "write failed")
        )
        val useCase = ApplyMetadataUseCase(repository)

        val result = useCase(song, MetadataResult(title = "New", artist = "Artist"))

        assertTrue(result is OperationResult.Failure)
        assertEquals(OperationError.IO, (result as OperationResult.Failure).error)
        assertEquals(1, repository.updateCalls)
    }

    @Test
    fun `successful metadata write returns updated song`() = runBlocking {
        val updatedSong = song.copy(title = "New")
        val repository = FakeSongRepository(updateResult = OperationResult.Success(updatedSong))
        val useCase = ApplyMetadataUseCase(repository)

        val result = useCase(song, MetadataResult(title = "New", artist = "Artist"))

        assertTrue(result.isSuccess)
        assertEquals(updatedSong, (result as OperationResult.Success).value)
    }

    private class FakeSongRepository(
        private val updateResult: OperationResult<Song> = OperationResult.Success(Song(id = 7, title = "New"))
    ) : SongRepository {
        var updateCalls = 0

        override val songs: StateFlow<List<Song>> = MutableStateFlow(emptyList())
        override val audiobooks: StateFlow<List<Song>> = MutableStateFlow(emptyList())
        override val isScanning: StateFlow<Boolean> = MutableStateFlow(false)
        override val scanProgress: StateFlow<Int> = MutableStateFlow(0)
        override val scanStats: StateFlow<ScanStats?> = MutableStateFlow(null)

        override suspend fun scanAll(): List<Song> = emptyList()
        override suspend fun loadFromDatabase(): List<Song> = emptyList()
        override suspend fun loadFromDatabaseFast(): List<Song> = emptyList()
        override suspend fun shouldRefreshLibrary(): Boolean = false
        override suspend fun deleteSong(song: Song): OperationResult<Unit> = OperationResult.Success(Unit)
        override suspend fun renameSong(song: Song, newTitle: String): OperationResult<Unit> = OperationResult.Success(Unit)
        override suspend fun search(query: String): List<Song> = emptyList()
        override suspend fun updateSongTags(
            song: Song,
            title: String,
            artist: String,
            album: String,
            year: Int?,
            genre: String
        ): OperationResult<Song> = OperationResult.Success(song.copy(
            title = title,
            artist = artist,
            album = album,
            year = year,
            genre = genre
        ))

        override suspend fun updateSongWithMetadata(song: Song, result: MetadataResult): OperationResult<Song> {
            updateCalls++
            return updateResult
        }

        override fun updateSongInList(songId: Long, transform: (Song) -> Song) = Unit
        override suspend fun getAlbums(): List<Album> = emptyList()
        override suspend fun getArtists(): List<Artist> = emptyList()
        override suspend fun getSongsByAlbum(album: String): List<Song> = emptyList()
        override suspend fun getSongsByArtist(artist: String): List<Song> = emptyList()
        override suspend fun refreshAlbumAndArtistTables() = Unit
    }
}
