package eu.kanade.presentation.reader.novel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelReaderTtsWebViewJavascriptTest {

    @Test
    fun `tts sync javascript clears previous highlight and marks the active block`() {
        val script = buildWebReaderTtsSyncJavascript(
            snippet = "Gamma delta",
            progressPercent = 50,
        )

        assertTrue(script.contains("data-an-tts-highlight"))
        assertTrue(script.contains("backgroundColor"))
        assertTrue(script.contains("closest"))
    }

    @Test
    fun `tts sync javascript injects the themed highlight colors`() {
        val script = buildWebReaderTtsSyncJavascript(
            snippet = "Gamma delta",
            progressPercent = 50,
            highlightCss = NovelTtsHighlightCss(
                paragraphBackgroundRgba = "rgba(10, 20, 30, 0.26)",
                barInsetRgba = "rgba(10, 20, 30, 1.0)",
            ),
        )

        assertTrue(script.contains("backgroundColor = 'rgba(10, 20, 30, 0.26)'"))
        assertTrue(script.contains("inset 3px 0 0 rgba(10, 20, 30, 1.0)"))
        // The hardcoded pale gold that washed out on reader backgrounds is gone.
        assertFalse(script.contains("255, 224, 130"))
    }
}
