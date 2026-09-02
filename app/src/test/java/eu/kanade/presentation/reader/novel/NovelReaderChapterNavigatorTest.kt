package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class NovelReaderChapterNavigatorTest {

    @AfterEach
    fun tearDown() {
        NovelReaderChapterHandoffPolicy.clear()
    }

    private fun createNavigator(
        bookMode: Boolean,
        onOpenPreviousChapter: (Long) -> Unit = {},
        onOpenNextChapter: (Long) -> Unit = {},
    ): NovelReaderChapterNavigator {
        val state = mockk<NovelReaderScreenModel.State.Success> {
            every { previousChapterId } returns 7L
            every { nextChapterId } returns 9L
        }
        return NovelReaderChapterNavigator(
            state = { state },
            isBookMode = { bookMode },
            webViewInstance = { null },
            onOpenPreviousChapter = onOpenPreviousChapter,
            onOpenNextChapter = onOpenNextChapter,
            onPrepareAutoScrollHandoff = { _, _ -> },
            onCancelAutoScrollHandoff = { },
            bookContentHandle = { error("not used") },
            showReaderUi = { true },
            autoScrollEnabled = { false },
            setAutoScrollEnabled = { },
            autoScrollSpeed = { 10 },
            setAutoScrollEndStableFrames = { },
            autoScrollEndDwellActive = { false },
            setAutoScrollEndDwellActive = { },
            setAutoScrollEndDwellRemainingSeconds = { },
            pageTurnChapterNavigationRequest = { null },
            setPageTurnChapterNavigationRequest = { },
            pageTurnChapterNavigationRequestToken = { 0L },
            setPageTurnChapterNavigationRequestToken = { },
        )
    }

    @Test
    fun `book mode previous chapter navigation does not leak a page reader handoff mark`() {
        val opened = mutableListOf<Long>()
        val navigator = createNavigator(bookMode = true, onOpenPreviousChapter = { opened += it })

        navigator.openPreviousChapterFromReader()

        // The book seeks inside the same document: state.chapter.id never changes, so a mark set
        // here would never be consumed and would leak into the next page-reader session of ANY
        // novel (opening it at END and falsely tripping its read threshold).
        opened shouldBe listOf(7L)
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff() shouldBe
            NovelReaderPageReaderHandoffTarget.SAVED
    }

    @Test
    fun `book mode next chapter navigation does not leak a page reader handoff mark`() {
        val opened = mutableListOf<Long>()
        val navigator = createNavigator(bookMode = true, onOpenNextChapter = { opened += it })

        navigator.openNextChapterFromReader()

        opened shouldBe listOf(9L)
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff() shouldBe
            NovelReaderPageReaderHandoffTarget.SAVED
    }

    @Test
    fun `chapter mode navigation still marks the page reader handoff target`() {
        val navigator = createNavigator(bookMode = false)

        navigator.openPreviousChapterFromReader()
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff() shouldBe
            NovelReaderPageReaderHandoffTarget.END

        navigator.openNextChapterFromReader()
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff() shouldBe
            NovelReaderPageReaderHandoffTarget.START
    }

    @Test
    fun `missing adjacent chapter is a no-op`() {
        val emptyState = mockk<NovelReaderScreenModel.State.Success> {
            every { previousChapterId } returns null
            every { nextChapterId } returns null
        }
        var previousCalls = 0
        var nextCalls = 0
        val navigator = NovelReaderChapterNavigator(
            state = { emptyState },
            isBookMode = { false },
            webViewInstance = { null },
            onOpenPreviousChapter = { previousCalls++ },
            onOpenNextChapter = { nextCalls++ },
            onPrepareAutoScrollHandoff = { _, _ -> },
            onCancelAutoScrollHandoff = { },
            bookContentHandle = { error("not used") },
            showReaderUi = { true },
            autoScrollEnabled = { false },
            setAutoScrollEnabled = { },
            autoScrollSpeed = { 10 },
            setAutoScrollEndStableFrames = { },
            autoScrollEndDwellActive = { false },
            setAutoScrollEndDwellActive = { },
            setAutoScrollEndDwellRemainingSeconds = { },
            pageTurnChapterNavigationRequest = { null },
            setPageTurnChapterNavigationRequest = { },
            pageTurnChapterNavigationRequestToken = { 0L },
            setPageTurnChapterNavigationRequestToken = { },
        )

        navigator.openPreviousChapterFromReader()
        navigator.openNextChapterFromReader()

        previousCalls shouldBe 0
        nextCalls shouldBe 0
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff() shouldBe
            NovelReaderPageReaderHandoffTarget.SAVED
    }
}
