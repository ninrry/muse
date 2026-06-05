package luzzr.muse.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.core.result.isSuccess
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class GenerateAllDefaultCoversUseCaseTest {

    private lateinit var useCase: GenerateAllDefaultCoversUseCase
    private val artworkRepository: ArtworkRepository = mockk(relaxed = true)
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)

    @Before
    fun setUp() {
        useCase = GenerateAllDefaultCoversUseCase(
            artworkRepository = artworkRepository,
            songRepository = songRepository,
            playbackController = playbackController
        )
    }

    @Test
    fun `invoke refreshes current song artwork after successful generation`() = runTest {
        val current = Song(id = 1L, title = "Song", artworkUri = null)
        val refreshed = current.copy(artworkUri = "content://artwork/1")
        coEvery { artworkRepository.generateDefaultCoversForAll() } returns OperationResult.Success(Unit)
        every { playbackController.state } returns MutableStateFlow(PlaybackState(currentSong = current))
        every { songRepository.songs } returns MutableStateFlow(listOf(refreshed))

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify { artworkRepository.generateDefaultCoversForAll() }
        verify { playbackController.refreshCurrentSong(refreshed) }
    }

    @Test
    fun `invoke does not refresh current song artwork after failed generation`() = runTest {
        val current = Song(id = 1L, title = "Song")
        coEvery { artworkRepository.generateDefaultCoversForAll() } returns OperationResult.Failure(OperationError.IO)
        every { playbackController.state } returns MutableStateFlow(PlaybackState(currentSong = current))
        every { songRepository.songs } returns MutableStateFlow(listOf(current.copy(artworkUri = "content://artwork/1")))

        val result = useCase()

        assertFalse(result.isSuccess)
        verify(exactly = 0) { playbackController.refreshCurrentSong(any()) }
    }
}
