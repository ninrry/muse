package luzzr.muse.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.core.result.isSuccess
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest

class EditSongMetadataUseCaseTest {

    private lateinit var useCase: EditSongMetadataUseCase
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val mockUri = "content://test/song"
    private val testSong = Song(id = 1, title = "旧标题", artist = "旧歌手", uri = mockUri)

    @Before
    fun setup() {
        useCase = EditSongMetadataUseCase(songRepository)
    }

    @Test
    fun `invoke delegates to songRepository updateSongTags`() = runTest {
        coEvery { songRepository.updateSongTags(any(), any(), any(), any(), any(), any()) } returns
            OperationResult.Success(Unit)
        val result = useCase(testSong, "新标题", "新歌手", "新专辑", 2024, "摇滚")
        assertTrue(result.isSuccess)
        coVerify { songRepository.updateSongTags(testSong, "新标题", "新歌手", "新专辑", 2024, "摇滚") }
    }

    @Test
    fun `invoke returns failure when songRepository fails`() = runTest {
        coEvery { songRepository.updateSongTags(any(), any(), any(), any(), any(), any()) } returns
            OperationResult.Failure(OperationError.IO)
        val result = useCase(testSong, "标题", "歌手", "专辑", null, "")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `invoke passes null year correctly`() = runTest {
        coEvery { songRepository.updateSongTags(any(), any(), any(), any(), any(), any()) } returns
            OperationResult.Success(Unit)
        useCase(testSong, "t", "a", "al", null, "g")
        coVerify { songRepository.updateSongTags(testSong, "t", "a", "al", null, "g") }
    }
}
