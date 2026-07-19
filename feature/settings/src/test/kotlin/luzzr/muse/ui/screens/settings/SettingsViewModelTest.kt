package luzzr.muse.ui.screens.settings

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.preferences.ThemePreferenceController
import luzzr.muse.domain.preferences.NavigationPreferenceController
import luzzr.muse.domain.scanner.LibraryScanController
import luzzr.muse.domain.healthcheck.AudioFileHealthCheckUseCase
import luzzr.muse.ui.state.StoragePermissionController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private val libraryScanController: LibraryScanController = mockk(relaxed = true)
    private val themePreferenceController: ThemePreferenceController = mockk(relaxed = true)
    private val navigationPreferenceController: NavigationPreferenceController = mockk(relaxed = true)
    private val storagePermissionController: StoragePermissionController = mockk(relaxed = true)
    private val audioFileHealthCheckUseCase: AudioFileHealthCheckUseCase = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val songs = MutableStateFlow(listOf(Song(id = 1L, title = "Song")))
    private val isScanning = MutableStateFlow(false)
    private val scanProgress = MutableStateFlow(25)
    private val scanStats = MutableStateFlow<ScanStats?>(ScanStats(totalSongs = 1, totalAlbums = 1, totalArtists = 1, duration = 1000L))
    private val isDarkTheme = MutableStateFlow(false)
    private val themeMode = MutableStateFlow(luzzr.muse.domain.preferences.ThemeMode.SYSTEM)
    private val isAudiobookVisible = MutableStateFlow(true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { libraryScanController.songs } returns songs
        every { libraryScanController.isScanning } returns isScanning
        every { libraryScanController.scanProgress } returns scanProgress
        every { libraryScanController.scanStats } returns scanStats
        every { themePreferenceController.isDarkTheme } returns isDarkTheme
        every { themePreferenceController.themeMode } returns themeMode
        every { navigationPreferenceController.isAudiobookVisible } returns isAudiobookVisible
        every { storagePermissionController.hasFullFileAccess() } returns false

        viewModel = SettingsViewModel(
            libraryScanController = libraryScanController,
            themePreferenceController = themePreferenceController,
            navigationPreferenceController = navigationPreferenceController,
            storagePermissionController = storagePermissionController,
            audioFileHealthCheckUseCase = audioFileHealthCheckUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state is forwarded from controllers`() {
        assertEquals(songs.value, viewModel.songs.value)
        assertFalse(viewModel.isScanning.value)
        assertEquals(25, viewModel.scanProgress.value)
        assertEquals(scanStats.value, viewModel.scanStats.value)
        assertFalse(viewModel.isDarkTheme.value)
    }

    @Test
    fun `toggleTheme delegates to theme controller`() {
        viewModel.toggleTheme()

        verify { themePreferenceController.toggleTheme() }
    }

    @Test
    fun `toggleAudiobookVisibility persists the opposite state`() {
        viewModel.toggleAudiobookVisibility()

        verify { navigationPreferenceController.setAudiobookVisible(false) }
    }

    @Test
    fun `scan actions delegate to scan controller`() = runTest(testDispatcher) {
        viewModel.scanAll()
        testScheduler.advanceUntilIdle()

        // Verify scan controller methods were called
        coVerify { libraryScanController.scanAll() }
    }

    @Test
    fun `permission actions refresh state and open full access settings`() {
        every { storagePermissionController.hasFullFileAccess() } returns true

        viewModel.refreshPermissionState()
        viewModel.requestFullFileAccess()

        assertEquals(true, viewModel.hasFullFileAccess.value)
        verify { storagePermissionController.requestFullFileAccess() }
    }
}
