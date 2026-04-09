package eu.kanade.presentation.reader.novel

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
}
