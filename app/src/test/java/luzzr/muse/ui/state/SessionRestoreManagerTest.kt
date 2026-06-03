package luzzr.muse.ui.state

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.player.PlayerState
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
    private val context: Context = mockk(relaxed = true)
    private val musicRepo: MusicRepositoryFacade = mockk(relaxed = true)
    private val playerState: PlayerState = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        manager = SessionRestoreManager(context, musicRepo, playerState)
    }

    @Test
    fun `restoreIfNeeded does nothing when playlist is not empty`() = testScope.runTest {
        every { playerState.currentPlaylist } returns MutableStateFlow(listOf(mockk<Song>(relaxed = true)))
        manager.restoreIfNeeded()
        verify(exactly = 0) { playerState.hasSavedSession() }
    }

    @Test
    fun `restoreIfNeeded does nothing when no saved session`() = testScope.runTest {
        every { playerState.currentPlaylist } returns MutableStateFlow(emptyList())
        every { playerState.hasSavedSession() } returns false
        manager.restoreIfNeeded()
        verify(exactly = 0) { playerState.getSavedPlaylistIds() }
    }

    @Test
    fun `restoreIfNeeded does nothing when saved IDs are empty`() = testScope.runTest {
        every { playerState.currentPlaylist } returns MutableStateFlow(emptyList())
        every { playerState.hasSavedSession() } returns true
        every { playerState.getSavedPlaylistIds() } returns emptyList()
        manager.restoreIfNeeded()
        verify(exactly = 0) { playerState.playSongs(any(), any(), any()) }
    }

    @Test
    fun `restoreIfNeeded clears session when no matching songs found`() = testScope.runTest {
        every { playerState.currentPlaylist } returns MutableStateFlow(emptyList())
        every { playerState.hasSavedSession() } returns true
        every { playerState.getSavedPlaylistIds() } returns listOf(999L)
        every { playerState.getSavedPlaybackInfo() } returns Pair(0, 0L)
        every { musicRepo.songs } returns MutableStateFlow(emptyList())
        coEvery { musicRepo.loadFromDatabase() } returns emptyList()
        manager.restoreIfNeeded()
        testDispatcher.scheduler.advanceUntilIdle()
        verify { playerState.clearSavedSession() }
    }
}
