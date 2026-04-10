package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PageReaderTtsNavigationAdapterTest {

    @Test
    fun `syncToSegment scrolls page reader to target page`() {
        runBlocking {
            val navigator = FakePageNavigator()
            val adapter = PageReaderTtsNavigationAdapter(navigator)

            adapter.syncToSegment(
                segment = segment(pageCandidates = listOf(5)),
            )

            navigator.lastPage shouldBe 5
        }
    }

    @Test
    fun `manual navigation stores anchor for re-entry`() {
        val adapter = PageReaderTtsNavigationAdapter(FakePageNavigator())

        val anchor = adapter.captureManualAnchor(pageIndex = 7)

        anchor.pageIndex shouldBe 7
    }

    @Test
    fun `restoreVisiblePosition jumps back to saved page`() {
        runBlocking {
            val navigator = FakePageNavigator()
            val adapter = PageReaderTtsNavigationAdapter(navigator)

            adapter.restorePosition(NovelTtsNavigationAnchor(pageIndex = 9))

            navigator.lastPage shouldBe 9
        }
    }

    private fun segment(pageCandidates: List<Int>): NovelTtsSegment {
        return NovelTtsSegment(
            id = "segment-1",
            chapterId = 1L,
            text = "Example",
            sourceBlockIndex = 3,
            pageCandidates = pageCandidates,
            firstUtteranceIndex = 0,
            lastUtteranceIndex = 0,
            wordRangeCount = 1,
        )
    }

    private class FakePageNavigator : PageReaderTtsNavigator {
        var lastPage: Int? = null

        override suspend fun scrollToPage(pageIndex: Int) {
            lastPage = pageIndex
        }
    }
}
