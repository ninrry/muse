package luzzr.muse.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [FormatBadge].
 *
 * These run as instrumented tests (androidTest) because they require
 * a real Compose host to render the composable tree.
 */
class FormatBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun badge_displays_provided_text() {
        composeTestRule.setContent {
            FormatBadge(text = "FLAC")
        }
        composeTestRule.onNodeWithText("FLAC").assertIsDisplayed()
    }

    @Test
    fun badge_displays_mp3_text() {
        composeTestRule.setContent {
            FormatBadge(text = "MP3")
        }
        composeTestRule.onNodeWithText("MP3").assertIsDisplayed()
    }

    @Test
    fun badge_displays_wav_text() {
        composeTestRule.setContent {
            FormatBadge(text = "WAV")
        }
        composeTestRule.onNodeWithText("WAV").assertIsDisplayed()
    }
}
