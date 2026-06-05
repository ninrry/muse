package luzzr.muse.ui.state

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackServiceStarter
import luzzr.muse.media.PlaybackState
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRestoreManagerTest {

    private lateinit var manager: SessionRestoreManager
    private val songRepository: SongRepository = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val playbackServiceStarter: PlaybackServiceStarter = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        manager = SessionRestoreManager(songRepository, playbackController, playbackServiceStarter)
    }

    @Test
    fun `restoreIfNeeded does nothing when playlist is not empty`() = testScope.runTest {
        every { playbackController.state } returns MutableStateFlow(
            PlaybackState(playlist = listOf(mockk<Song>(relaxed = true)))
        )
        manager.restoreIfNeeded()
        verify(exactly = 0) { playbackController.hasSavedSession() }
    }

    @Test
    fun `restoreIfNeeded does nothing when no saved session`() = testScope.runTest {
        every { playbackController.state } returns MutableStateFlow(PlaybackState())
        every { playbackController.hasSavedSession() } returns false
        manager.restoreIfNeeded()
        verify(exactly = 0) { playbackController.getSavedPlaylistIds() }
    }

    @Test
    fun `restoreIfNeeded does nothing when saved IDs are empty`() = testScope.runTest {
        every { playbackController.state } returns MutableStateFlow(PlaybackState())
        every { playbackController.hasSavedSession() } returns true
        every { playbackController.getSavedPlaylistIds() } returns emptyList()
        manager.restoreIfNeeded()
        verify(exactly = 0) { playbackController.playSongs(any(), any(), any()) }
    }

    @Test
    fun `restoreIfNeeded clears session when no matching songs found`() = testScope.runTest {
        every { playbackController.state } returns MutableStateFlow(PlaybackState())
        every { playbackController.hasSavedSession() } returns true
        every { playbackController.getSavedPlaylistIds() } returns listOf(999L)
        every { playbackController.getSavedPlaybackInfo() } returns Pair(0, 0L)
        every { songRepository.songs } returns MutableStateFlow(emptyList())
        coEvery { songRepository.loadFromDatabase() } returns emptyList()
        manager.restoreIfNeeded()
        testDispatcher.scheduler.advanceUntilIdle()
        verify { playbackController.clearSavedSession() }
    }

    @Test
    fun `restoreIfNeeded starts playback service when saved songs are restored`() = testScope.runTest {
        val song = Song(id = 1L, title = "Song", artist = "Artist", uri = "content://song")
        every { playbackController.state } returns MutableStateFlow(PlaybackState())
        every { playbackController.hasSavedSession() } returns true
        every { playbackController.getSavedPlaylistIds() } returns listOf(1L)
        every { playbackController.getSavedPlaybackInfo() } returns Pair(0, 0L)
        every { playbackController.getSavedShuffleMode() } returns false
        every { songRepository.songs } returns MutableStateFlow(listOf(song))

        manager.restoreIfNeeded()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { playbackServiceStarter.startForegroundService() }
        verify { playbackController.playSongs(listOf(song), 0) }
    }
}
