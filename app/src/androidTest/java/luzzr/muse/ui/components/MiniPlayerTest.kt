package luzzr.muse.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import luzzr.muse.data.model.Song
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [MiniPlayer].
 *
 * Verifies that song metadata, play/pause state, and shuffle
 * indicator are rendered correctly.
 */
class MiniPlayerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testSong = Song(
        id = 1L,
        title = "测试歌曲",
        artist = "测试艺术家",
        album = "测试专辑",
        duration = 180_000L
    )

    @Test
    fun miniPlayer_displays_song_title() {
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayer(
                    song = testSong,
                    isPlaying = false,
                    onTogglePlayPause = {},
                    onClick = {}
                )
            }
        }
        composeTestRule.onNodeWithText("测试歌曲").assertIsDisplayed()
    }

    @Test
    fun miniPlayer_displays_song_artist() {
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayer(
                    song = testSong,
                    isPlaying = false,
                    onTogglePlayPause = {},
                    onClick = {}
                )
            }
        }
        composeTestRule.onNodeWithText("测试艺术家").assertIsDisplayed()
    }

    @Test
    fun miniPlayer_shows_play_icon_when_paused() {
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayer(
                    song = testSong,
                    isPlaying = false,
                    onTogglePlayPause = {},
                    onClick = {}
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test
    fun miniPlayer_shows_pause_icon_when_playing() {
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayer(
                    song = testSong,
                    isPlaying = true,
                    onTogglePlayPause = {},
                    onClick = {}
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("暂停").assertIsDisplayed()
    }

    @Test
    fun miniPlayer_shows_shuffle_indicator_when_shuffle_enabled() {
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayer(
                    song = testSong,
                    isPlaying = false,
                    shuffleMode = true,
                    onTogglePlayPause = {},
                    onClick = {}
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("随机播放").assertIsDisplayed()
    }

    @Test
    fun miniPlayer_hides_shuffle_indicator_when_shuffle_disabled() {
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayer(
                    song = testSong,
                    isPlaying = false,
                    shuffleMode = false,
                    onTogglePlayPause = {},
                    onClick = {}
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("随机播放").assertDoesNotExist()
    }

    @Test
    fun miniPlayer_shows_queue_button() {
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayer(
                    song = testSong,
                    isPlaying = false,
                    onTogglePlayPause = {},
                    onClick = {}
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("播放队列").assertIsDisplayed()
    }

    @Test
    fun miniPlayer_toggle_play_pause_triggers_callback() {
        var toggled = false
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayer(
                    song = testSong,
                    isPlaying = false,
                    onTogglePlayPause = { toggled = true },
                    onClick = {}
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("播放").performClick()
        assert(toggled)
    }

    @Test
    fun miniPlayer_progress_bar_clamps_at_zero() {
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayerProgressBar(progress = -0.5f)
            }
        }
        // Should not crash �?progress is clamped to 0f
    }

    @Test
    fun miniPlayer_progress_bar_clamps_at_one() {
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayerProgressBar(progress = 1.5f)
            }
        }
        // Should not crash �?progress is clamped to 1f
    }
}
