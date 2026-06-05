package luzzr.muse.ui.screens.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import luzzr.muse.domain.model.Song
import luzzr.muse.media.PlaybackRepeatMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playbackControls_displays_play_icon_when_paused() {
        setPlaybackControls(isPlaying = false)
        composeTestRule.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test
    fun playbackControls_displays_pause_icon_when_playing() {
        setPlaybackControls(isPlaying = true, progress = 60_000L)
        composeTestRule.onNodeWithContentDescription("暂停").assertIsDisplayed()
    }

    @Test
    fun playbackControls_displays_skip_buttons_and_time_labels() {
        setPlaybackControls(isPlaying = false, progress = 60_000L)
        composeTestRule.onNodeWithContentDescription("上一首").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("下一首").assertIsDisplayed()
        composeTestRule.onNodeWithText("1:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("3:00").assertIsDisplayed()
    }

    @Test
    fun playbackControls_play_button_triggers_callback() {
        var toggled = false
        setPlaybackControls(isPlaying = false, onTogglePlayPause = { toggled = true })
        composeTestRule.onNodeWithContentDescription("播放").performClick()
        assertTrue(toggled)
    }

    @Test
    fun playerTopBar_displays_now_playing_title() {
        setPlayerTopBar(showLyrics = false)
        composeTestRule.onNodeWithText("正在播放").assertIsDisplayed()
    }

    @Test
    fun playerTopBar_displays_song_title_when_showing_lyrics() {
        setPlayerTopBar(showLyrics = true, currentSong = Song(title = "测试歌曲", artist = "测试艺术家"))
        composeTestRule.onNodeWithText("测试歌曲").assertIsDisplayed()
        composeTestRule.onNodeWithText("测试艺术家").assertIsDisplayed()
    }

    @Test
    fun playerTopBar_back_button_triggers_callback() {
        var backClicked = false
        setPlayerTopBar(showLyrics = false, onBack = { backClicked = true })
        composeTestRule.onNodeWithContentDescription("收起").performClick()
        assertTrue(backClicked)
    }

    private fun setPlaybackControls(isPlaying: Boolean, progress: Long = 0L, onTogglePlayPause: () -> Unit = {}) {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackControls(
                    duration = 180_000L,
                    progressProvider = { progress },
                    isPlaying = isPlaying,
                    repeatMode = PlaybackRepeatMode.OFF,
                    shuffleMode = false,
                    sleepTimerMode = null,
                    onSeek = {},
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onCyclePlayMode = {},
                    onShowSleepTimer = {}
                )
            }
        }
    }

    private fun setPlayerTopBar(showLyrics: Boolean, currentSong: Song? = null, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerTopBar(
                    showLyrics = showLyrics,
                    currentSong = currentSong,
                    onBack = onBack,
                    onToggleLyrics = {},
                    onRefreshLyrics = {},
                    onShowQueue = {}
                )
            }
        }
    }
}
