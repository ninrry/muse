package luzzr.muse.ui.screens.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [PlaybackControls] and [PlayerTopBar].
 *
 * Verifies that playback controls, repeat/shuffle indicators,
 * and top bar navigation are rendered and interactive.
 */
class PlayerControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -- PlaybackControls ----------------------------------------

    @Test
    fun playbackControls_displays_play_icon_when_paused() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackControls(
                    duration = 180_000L,
                    progress = 0L,
                    isPlaying = false,
                    repeatMode = Player.REPEAT_MODE_OFF,
                    shuffleMode = false,
                    sleepTimerMode = null,
                    sleepTimerRemaining = null,
                    onSeek = {},
                    onTogglePlayPause = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onShowSleepTimer = {},
                    onShowQueue = {},
                    sleepTimerFormat = { "" }
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test
    fun playbackControls_displays_pause_icon_when_playing() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackControls(
                    duration = 180_000L,
                    progress = 60_000L,
                    isPlaying = true,
                    repeatMode = Player.REPEAT_MODE_OFF,
                    shuffleMode = false,
                    sleepTimerMode = null,
                    sleepTimerRemaining = null,
                    onSeek = {},
                    onTogglePlayPause = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onShowSleepTimer = {},
                    onShowQueue = {},
                    sleepTimerFormat = { "" }
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("暂停").assertIsDisplayed()
    }

    @Test
    fun playbackControls_displays_skip_buttons() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackControls(
                    duration = 180_000L,
                    progress = 0L,
                    isPlaying = false,
                    repeatMode = Player.REPEAT_MODE_OFF,
                    shuffleMode = false,
                    sleepTimerMode = null,
                    sleepTimerRemaining = null,
                    onSeek = {},
                    onTogglePlayPause = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onShowSleepTimer = {},
                    onShowQueue = {},
                    sleepTimerFormat = { "" }
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("上一首").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("下一首").assertIsDisplayed()
    }

    @Test
    fun playbackControls_displays_time_labels() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackControls(
                    duration = 180_000L,
                    progress = 60_000L,
                    isPlaying = false,
                    repeatMode = Player.REPEAT_MODE_OFF,
                    shuffleMode = false,
                    sleepTimerMode = null,
                    sleepTimerRemaining = null,
                    onSeek = {},
                    onTogglePlayPause = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onShowSleepTimer = {},
                    onShowQueue = {},
                    sleepTimerFormat = { "" }
                )
            }
        }
        // 1:00 / 3:00
        composeTestRule.onNodeWithText("1:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("3:00").assertIsDisplayed()
    }

    @Test
    fun playbackControls_play_button_triggers_callback() {
        var toggled = false
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackControls(
                    duration = 180_000L,
                    progress = 0L,
                    isPlaying = false,
                    repeatMode = Player.REPEAT_MODE_OFF,
                    shuffleMode = false,
                    sleepTimerMode = null,
                    sleepTimerRemaining = null,
                    onSeek = {},
                    onTogglePlayPause = { toggled = true },
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onShowSleepTimer = {},
                    onShowQueue = {},
                    sleepTimerFormat = { "" }
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("播放").performClick()
        assert(toggled)
    }

    // -- PlayerTopBar --------------------------------------------

    @Test
    fun playerTopBar_displays_now_playing_title() {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerTopBar(
                    showLyrics = false,
                    onBack = {},
                    onToggleLyrics = {},
                    onRefreshLyrics = {},
                    onShowQueue = {}
                )
            }
        }
        composeTestRule.onNodeWithText("正在播放").assertIsDisplayed()
    }

    @Test
    fun playerTopBar_displays_lyrics_title_when_showing_lyrics() {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerTopBar(
                    showLyrics = true,
                    onBack = {},
                    onToggleLyrics = {},
                    onRefreshLyrics = {},
                    onShowQueue = {}
                )
            }
        }
        composeTestRule.onNodeWithText("歌词").assertIsDisplayed()
    }

    @Test
    fun playerTopBar_back_button_triggers_callback() {
        var backClicked = false
        composeTestRule.setContent {
            MaterialTheme {
                PlayerTopBar(
                    showLyrics = false,
                    onBack = { backClicked = true },
                    onToggleLyrics = {},
                    onRefreshLyrics = {},
                    onShowQueue = {}
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("收起").performClick()
        assert(backClicked)
    }
}
