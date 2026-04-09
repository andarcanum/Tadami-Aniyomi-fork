package eu.kanade.presentation.reader.novel

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngineDescriptor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngineCapabilities
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelReaderTtsUiState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsVoiceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NovelReaderTtsUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<NovelReaderTtsTestActivity>()

    @Test
    fun controls_expose_transport_and_settings_dialog() {
        val toggleCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        val previousCount = AtomicInteger(0)
        val nextCount = AtomicInteger(0)

        composeTestRule.setContent {
            MaterialTheme {
                NovelReaderTtsControls(
                    uiState = previewTtsUiState(),
                    onTogglePlayback = { toggleCount.incrementAndGet() },
                    onStop = { stopCount.incrementAndGet() },
                    onSkipPrevious = { previousCount.incrementAndGet() },
                    onSkipNext = { nextCount.incrementAndGet() },
                    onSetEnginePackage = {},
                    onSetVoiceId = {},
                    onSetLocaleTag = {},
                    onSetSpeechRate = {},
                    onSetPitch = {},
                )
            }
        }

        composeTestRule.onNodeWithText("TTS").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Previous").performClick()
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        composeTestRule.onNodeWithContentDescription("Stop").performClick()
        composeTestRule.onNodeWithContentDescription("Next").performClick()
        composeTestRule.onNodeWithContentDescription("Open TTS options").performClick()

        composeTestRule.onNodeWithText("Engine").assertIsDisplayed()
        composeTestRule.onNodeWithText("Voice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Locale").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").performClick()

        assertEquals(1, previousCount.get())
        assertEquals(1, toggleCount.get())
        assertEquals(1, stopCount.get())
        assertEquals(1, nextCount.get())
    }

    @Composable
    private fun previewTtsUiState(): NovelReaderTtsUiState {
        return NovelReaderTtsUiState(
            enabled = true,
            playbackState = NovelTtsPlaybackState.PLAYING,
            activeHighlightMode = NovelTtsHighlightMode.ESTIMATED,
            availableEngines = listOf(
                NovelTtsEngineDescriptor(
                    packageName = "org.example.tts",
                    label = "Example TTS",
                    isSystemDefault = true,
                ),
            ),
            availableVoices = listOf(
                NovelTtsVoiceDescriptor(
                    id = "voice-1",
                    name = "Narrator",
                    localeTag = "en-US",
                    requiresNetwork = false,
                    isInstalled = true,
                ),
            ),
            availableLocales = listOf("en-US"),
            selectedEnginePackage = "org.example.tts",
            selectedVoiceId = "voice-1",
            selectedLocaleTag = "en-US",
            speechRate = 1f,
            pitch = 1f,
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = true,
                supportsLocaleEnumeration = true,
            ),
        )
    }
}
