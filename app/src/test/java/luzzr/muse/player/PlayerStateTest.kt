package luzzr.muse.player

import android.net.Uri
import luzzr.muse.data.model.Song
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PlayerState] — covers StateFlow updates,
 * playlist management, toggle operations, and session persistence.
 *
 * ExoPlayer is not attached (player == null), so control methods
 * queue operations via pendingOperations.
 */
class PlayerStateTest {

    private lateinit var state: PlayerState
    private val mockUri: Uri = io.mockk.mockk(relaxed = true)

    private fun testSong(id: Long, title: String): Song {
        return Song(id = id, title = title, uri = mockUri)
    }

    @Before
    fun setUp() {
        state = PlayerState()
    }

    // -- Initial state -------------------------------------------

    @Test
    fun `initial state has empty playlist and null current song`() {
        assertTrue(state.currentPlaylist.value.isEmpty())
        assertNull(state.currentSong.value)
        assertFalse(state.isPlaying.value)
        assertEquals(0L, state.progress.value)
        assertEquals(0L, state.duration.value)
        assertFalse(state.shuffleMode.value)
    }

    // -- Internal update methods ---------------------------------

    @Test
    fun `updateCurrentSong sets current song`() {
        val song = testSong(id = 1, title = "Test Song")
        state.updateCurrentSong(song)
        assertEquals(song, state.currentSong.value)
    }

    @Test
    fun `updateCurrentSong with null clears current song`() {
        state.updateCurrentSong(testSong(id = 1, title = "Test"))
        state.updateCurrentSong(null)
        assertNull(state.currentSong.value)
    }

    @Test
    fun `updateIsPlaying sets playing state`() {
        state.updateIsPlaying(true)
        assertTrue(state.isPlaying.value)
        state.updateIsPlaying(false)
        assertFalse(state.isPlaying.value)
    }

    @Test
    fun `updateProgress sets progress value`() {
        state.updateProgress(30_000L)
        assertEquals(30_000L, state.progress.value)
    }

    @Test
    fun `updateDuration sets duration value`() {
        state.updateDuration(180_000L)
        assertEquals(180_000L, state.duration.value)
    }

    @Test
    fun `updateRepeatMode sets repeat mode`() {
        state.updateRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ALL, state.repeatMode.value)
    }

    @Test
    fun `updateShuffleMode sets shuffle mode`() {
        state.updateShuffleMode(true)
        assertTrue(state.shuffleMode.value)
        state.updateShuffleMode(false)
        assertFalse(state.shuffleMode.value)
    }

    // -- Floating lyrics -----------------------------------------

    @Test
    fun `toggleFloatingLyrics toggles state`() {
        assertFalse(state.floatingLyricsEnabled.value)
        state.toggleFloatingLyrics()
        assertTrue(state.floatingLyricsEnabled.value)
        state.toggleFloatingLyrics()
        assertFalse(state.floatingLyricsEnabled.value)
    }

    @Test
    fun `updateCurrentLyrics sets lyrics list`() {
        val lines = listOf(
            luzzr.muse.data.network.LrcLine(0L, "Hello"),
            luzzr.muse.data.network.LrcLine(5000L, "World")
        )
        state.updateCurrentLyrics(lines)
        assertEquals(2, state.currentLyrics.value.size)
        assertEquals("Hello", state.currentLyrics.value[0].text)
    }

    @Test
    fun `updateCurrentLyricLine sets line index`() {
        state.updateCurrentLyricLine(3)
        assertEquals(3, state.currentLyricLine.value)
    }

    // -- Song list update ----------------------------------------

    @Test
    fun `updateSongInPlaylist updates specific song at index`() {
        val song1 = testSong(id = 1, title = "Song 1")
        val song2 = testSong(id = 2, title = "Song 2")
        state.playSongs(listOf(song1, song2), startIndex = 0)

        val updatedSong1 = song1.copy(title = "Updated Song 1")
        state.updateSongInPlaylist(0, updatedSong1)

        assertEquals("Updated Song 1", state.currentPlaylist.value[0].title)
        assertEquals("Song 2", state.currentPlaylist.value[1].title)
    }

    @Test
    fun `updateSongInPlaylist updates currentSong when matching`() {
        val song = testSong(id = 1, title = "Original")
        state.playSongs(listOf(song), startIndex = 0)
        assertEquals("Original", state.currentSong.value?.title)

        val updated = song.copy(title = "Updated")
        state.updateSongInPlaylist(0, updated)
        assertEquals("Updated", state.currentSong.value?.title)
    }

    @Test
    fun `updateSongInPlaylist does not update currentSong when non-matching`() {
        val song1 = testSong(id = 1, title = "Song 1")
        val song2 = testSong(id = 2, title = "Song 2")
        state.playSongs(listOf(song1, song2), startIndex = 0)

        val updatedSong2 = song2.copy(title = "Updated Song 2")
        state.updateSongInPlaylist(1, updatedSong2)
        // currentSong is song1, should not change
        assertEquals("Song 1", state.currentSong.value?.title)
    }

    @Test
    fun `updateSongInPlaylist ignores out-of-bounds index`() {
        val song = testSong(id = 1, title = "Song")
        state.playSongs(listOf(song), startIndex = 0)

        // Should not crash
        state.updateSongInPlaylist(99, song.copy(title = "Invalid"))
        assertEquals("Song", state.currentPlaylist.value[0].title)
    }

    // -- Play/Pause toggle without player ------------------------

    @Test
    fun `togglePlayPause queues operation when player is null`() {
        // Should not crash — queues as pending operation
        state.togglePlayPause()
        // No assertion needed — verifying no exception is thrown
    }

    @Test
    fun `seekTo does nothing when player is null`() {
        state.seekTo(5000L)
        // Progress should remain unchanged since player is null
        assertEquals(0L, state.progress.value)
    }

    @Test
    fun `skipToNext does nothing when player is null`() {
        state.skipToNext()
        // Should not crash
    }

    @Test
    fun `skipToPrevious does nothing when player is null`() {
        state.skipToPrevious()
        // Should not crash
    }

    // -- playSongs with empty list -------------------------------

    @Test
    fun `playSongs with empty list clears state`() {
        state.playSongs(emptyList())
        assertTrue(state.currentPlaylist.value.isEmpty())
        assertNull(state.currentSong.value)
        assertFalse(state.isPlaying.value)
    }

    // -- Pending operations --------------------------------------

    @Test
    fun `clearPendingOperations clears queued operations`() {
        // Queue some operations
        state.togglePlayPause()
        state.togglePlayPause()
        state.clearPendingOperations()
        // After clearing, attaching a player should not execute old ops
        // We can't easily verify this without a real player, but
        // we verify no crash occurs
    }

    // -- Repeat and shuffle mode ---------------------------------

    @Test
    fun `setRepeatMode updates state flow`() {
        state.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, state.repeatMode.value)
    }

    @Test
    fun `toggleShuffle toggles shuffle state when player is null`() {
        assertFalse(state.shuffleMode.value)
        state.toggleShuffle()
        assertTrue(state.shuffleMode.value)
        state.toggleShuffle()
        assertFalse(state.shuffleMode.value)
    }

    // -- Detach player -------------------------------------------

    @Test
    fun `detachPlayer resets playing state and progress`() {
        state.updateIsPlaying(true)
        state.updateProgress(5000L)

        state.detachPlayer()

        assertFalse(state.isPlaying.value)
        assertEquals(0L, state.progress.value)
    }

    // -- Session persistence -------------------------------------

    @Test
    fun `hasSavedSession returns false without prefs`() {
        assertFalse(state.hasSavedSession())
    }

    @Test
    fun `getSavedPlaylistIds returns empty without prefs`() {
        assertTrue(state.getSavedPlaylistIds().isEmpty())
    }

    @Test
    fun `getSavedPlaybackInfo returns defaults without prefs`() {
        val (index, position) = state.getSavedPlaybackInfo()
        assertEquals(0, index)
        assertEquals(0L, position)
    }

    @Test
    fun `getSavedShuffleMode returns false without prefs`() {
        assertFalse(state.getSavedShuffleMode())
    }

    @Test
    fun `clearSavedSession does not crash without prefs`() {
        state.clearSavedSession()
        // No assertion — verifying no exception
    }

    // -- Sleep timer integration ---------------------------------

    @Test
    fun `sleepTimer is accessible`() {
        assertNotNull(state.sleepTimer)
        assertFalse(state.sleepTimer.isActive)
    }
}
