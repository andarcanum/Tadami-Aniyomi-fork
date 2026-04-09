package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

class NativeScrollTtsNavigationAdapterTest {

    @Test
    fun `syncToSegment scrolls native reader to target block`() {
        runBlocking {
            val navigator = FakeNativeNavigator()
            val adapter = NativeScrollTtsNavigationAdapter(navigator)

            adapter.syncToSegment(segment(sourceBlockIndex = 12))

            navigator.lastIndex shouldBe 12
        }
    }

    @Test
    fun `pause on manual navigation captures block anchor`() {
        val adapter = NativeScrollTtsNavigationAdapter(FakeNativeNavigator())

        val anchor = adapter.captureManualAnchor(
            blockIndex = 18,
            scrollOffsetPx = 32,
        )

        anchor.blockIndex shouldBe 18
        anchor.scrollOffsetPx shouldBe 32
    }

    @Test
    fun `restoreVisiblePosition scrolls back to previous block and offset`() {
        runBlocking {
            val navigator = FakeNativeNavigator()
            val adapter = NativeScrollTtsNavigationAdapter(navigator)

            adapter.restorePosition(
                NovelTtsNavigationAnchor(
                    blockIndex = 4,
                    scrollOffsetPx = 24,
                ),
            )

            navigator.lastIndex shouldBe 4
            navigator.lastOffsetPx shouldBe 24
        }
    }

    private fun segment(sourceBlockIndex: Int): NovelTtsSegment {
        return NovelTtsSegment(
            id = "segment-1",
            chapterId = 1L,
            text = "Example",
            sourceBlockIndex = sourceBlockIndex,
            firstUtteranceIndex = 0,
            lastUtteranceIndex = 0,
            wordRangeCount = 1,
        )
    }

    private class FakeNativeNavigator : NativeScrollTtsNavigator {
        var lastIndex: Int? = null
        var lastOffsetPx: Int? = null

        override suspend fun scrollToBlock(blockIndex: Int, scrollOffsetPx: Int) {
            lastIndex = blockIndex
            lastOffsetPx = scrollOffsetPx
        }
    }
}
