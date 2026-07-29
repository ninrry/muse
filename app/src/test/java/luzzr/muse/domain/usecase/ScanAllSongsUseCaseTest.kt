package luzzr.muse.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.domain.scanner.ScanHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class ScanAllSongsUseCaseTest {

    private lateinit var useCase: ScanAllSongsUseCase
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val scanHistoryStore: ScanHistoryStore = mockk(relaxed = true)

    @Before
    fun setUp() {
        useCase = ScanAllSongsUseCase(
            songRepository = songRepository,
            scanHistoryStore = scanHistoryStore
        )
    }

    @Test
    fun `invoke skips duplicate scan while repository is scanning`() = runTest {
        val existingSongs = listOf(Song(id = 1L, title = "Existing"))
        every { songRepository.isScanning } returns MutableStateFlow(true)
        every { songRepository.songs } returns MutableStateFlow(existingSongs)

        val result = useCase()

        assertEquals(existingSongs, result)
        coVerify(exactly = 0) { songRepository.scanAll() }
        verify(exactly = 0) { scanHistoryStore.markScanCompleted(any()) }
    }

    @Test
    fun `invoke records scan completion after successful repository scan`() = runTest {
        val scannedSongs = listOf(Song(id = 2L, title = "Scanned"))
        every { songRepository.isScanning } returns MutableStateFlow(false)
        coEvery { songRepository.scanAll() } returns scannedSongs

        val result = useCase()

        assertEquals(scannedSongs, result)
        coVerify { songRepository.scanAll() }
        verify { scanHistoryStore.markScanCompleted(any()) }
    }
}
