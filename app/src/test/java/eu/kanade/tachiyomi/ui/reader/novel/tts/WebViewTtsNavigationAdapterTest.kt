package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.Test

class WebViewTtsNavigationAdapterTest {

    @Test
    fun `syncToSegment uses approximate WebView location`() {
        runBlocking {
            val navigator = FakeWebViewNavigator()
            val adapter = WebViewTtsNavigationAdapter(
                navigator = navigator,
                totalBlocks = 10,
            )

            adapter.syncToSegment(segment(sourceBlockIndex = 4, text = "Approximate text"))

            navigator.lastJavascript.shouldContain("50")
            navigator.lastJavascript.shouldContain("Approximate text")
        }
    }

    @Test
    fun `syncToSegment does not crash when DOM and model diverge`() {
        runBlocking {
            val navigator = FakeWebViewNavigator(result = "\"fallback\"")
            val adapter = WebViewTtsNavigationAdapter(
                navigator = navigator,
                totalBlocks = 8,
            )

            try {
                adapter.syncToSegment(segment(sourceBlockIndex = 6, text = "Missing snippet"))
            } catch (error: Throwable) {
                throw AssertionError("Expected WebView sync fallback to avoid crashes", error)
            }
        }
    }

    @Test
    fun `restorePosition falls back to stored WebView progress`() {
        runBlocking {
            val navigator = FakeWebViewNavigator()
            val adapter = WebViewTtsNavigationAdapter(
                navigator = navigator,
                totalBlocks = 12,
            )

            adapter.restorePosition(NovelTtsNavigationAnchor(webProgressPercent = 73))

            navigator.lastJavascript.shouldContain("73")
        }
    }

    private fun segment(sourceBlockIndex: Int, text: String): NovelTtsSegment {
        return NovelTtsSegment(
            id = "segment-1",
            chapterId = 1L,
            text = text,
            sourceBlockIndex = sourceBlockIndex,
            firstUtteranceIndex = 0,
            lastUtteranceIndex = 0,
            wordRangeCount = 1,
        )
    }

    private class FakeWebViewNavigator(
        private val result: String = "\"aligned\"",
    ) : WebViewTtsNavigator {
        var lastJavascript: String = ""

        override suspend fun evaluateJavascript(script: String): String? {
            lastJavascript = script
            return result
        }
    }
}
