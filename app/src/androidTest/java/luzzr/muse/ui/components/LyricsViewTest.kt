package luzzr.muse.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import luzzr.muse.data.network.LrcLine
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [LyricsView], [LyricsEmptyState], and [LyricsLoadingState].
 *
 * Tests verify that lyrics lines, empty states, and loading indicators
 * are rendered correctly.
 */
class LyricsViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleLyrics = listOf(
        LrcLine(0L, "第一行歌词"),
        LrcLine(5000L, "第二行歌词"),
        LrcLine(10000L, "第三行歌词"),
        LrcLine(15000L, "第四行歌词")
    )

    @Test
    fun lyricsView_displays_lyrics_lines() {
        composeTestRule.setContent {
            MaterialTheme {
                LyricsView(
                    lyrics = sampleLyrics,
                    currentLineIndex = 0,
                    lineProgress = 0f,
                    onSeek = {},
                    modifier = Modifier.height(400.dp)
                )
            }
        }
        composeTestRule.onNodeWithText("第一行歌词").assertIsDisplayed()
    }

    @Test
    fun lyricsView_displays_all_visible_lines() {
        composeTestRule.setContent {
            MaterialTheme {
                LyricsView(
                    lyrics = sampleLyrics,
                    currentLineIndex = 0,
                    lineProgress = 0f,
                    onSeek = {},
                    modifier = Modifier.height(800.dp)
                )
            }
        }
        composeTestRule.onNodeWithText("第一行歌词").assertIsDisplayed()
        composeTestRule.onNodeWithText("第二行歌词").assertIsDisplayed()
    }

    @Test
    fun lyricsView_handles_empty_lyrics() {
        composeTestRule.setContent {
            MaterialTheme {
                LyricsView(
                    lyrics = emptyList(),
                    currentLineIndex = -1,
                    lineProgress = 0f,
                    onSeek = {},
                    modifier = Modifier.height(400.dp)
                )
            }
        }
        // Should not crash with empty list
    }

    @Test
    fun lyricsView_handles_current_line_highlight() {
        composeTestRule.setContent {
            MaterialTheme {
                LyricsView(
                    lyrics = sampleLyrics,
                    currentLineIndex = 2,
                    lineProgress = 0.5f,
                    onSeek = {},
                    modifier = Modifier.height(400.dp)
                )
            }
        }
        composeTestRule.onNodeWithText("第三行歌词").assertIsDisplayed()
    }

    // -- LyricsEmptyState ----------------------------------------

    @Test
    fun lyricsEmptyState_displays_message() {
        composeTestRule.setContent {
            MaterialTheme {
                LyricsEmptyState(
                    message = "未找到同步歌词",
                    modifier = Modifier.height(200.dp)
                )
            }
        }
        composeTestRule.onNodeWithText("未找到同步歌词").assertIsDisplayed()
    }

    @Test
    fun lyricsEmptyState_displays_custom_message() {
        composeTestRule.setContent {
            MaterialTheme {
                LyricsEmptyState(
                    message = "暂无歌词",
                    modifier = Modifier.height(200.dp)
                )
            }
        }
        composeTestRule.onNodeWithText("暂无歌词").assertIsDisplayed()
    }

    // -- LyricsLoadingState --------------------------------------

    @Test
    fun lyricsLoadingState_renders_without_crash() {
        composeTestRule.setContent {
            MaterialTheme {
                LyricsLoadingState(modifier = Modifier.height(200.dp))
            }
        }
        // CircularProgressIndicator is rendered �?verify no crash
    }
}
