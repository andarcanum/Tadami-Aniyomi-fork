package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderSelectionGestureArbiterTest {

    @Test
    fun `tap stays tap with no active selection`() {
        NovelReaderSelectionGestureArbiter.shouldInterceptTap(activeSelection = null) shouldBe false
    }

    @Test
    fun `moving beyond slop before timeout never becomes selection`() {
        NovelReaderSelectionGestureArbiter.shouldPromoteSelectionCandidate(
            elapsedMillis = 180L,
            movedDistancePx = 32f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe false
    }

    @Test
    fun `long press within slop becomes selection candidate`() {
        NovelReaderSelectionGestureArbiter.shouldPromoteSelectionCandidate(
            elapsedMillis = 550L,
            movedDistancePx = 6f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe true
    }

    @Test
    fun `short tap within slop stays a plain tap`() {
        NovelReaderSelectionGestureArbiter.shouldHandlePlainTap(
            elapsedMillis = 120L,
            movedDistancePx = 3f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe true
    }

    @Test
    fun `long press no longer counts as a plain tap`() {
        NovelReaderSelectionGestureArbiter.shouldHandlePlainTap(
            elapsedMillis = 550L,
            movedDistancePx = 3f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe false
    }

    @Test
    fun `drag beyond slop no longer counts as a plain tap`() {
        NovelReaderSelectionGestureArbiter.shouldHandlePlainTap(
            elapsedMillis = 120L,
            movedDistancePx = 24f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe false
    }

    @Test
    fun `active selection suppresses only the active renderer surface`() {
        val selection = NovelSelectedTextSelection(
            sessionId = 1L,
            renderer = NovelSelectedTextRenderer.WEBVIEW,
            text = "Hello",
            anchor = NovelSelectedTextAnchor(
                leftPx = 0,
                topPx = 0,
                rightPx = 10,
                bottomPx = 10,
            ),
        )

        NovelReaderSelectionGestureArbiter.shouldSuppressRendererSurface(
            activeSelection = selection,
            renderer = NovelSelectedTextRenderer.WEBVIEW,
        ) shouldBe true
        NovelReaderSelectionGestureArbiter.shouldSuppressRendererSurface(
            activeSelection = selection,
            renderer = NovelSelectedTextRenderer.PAGE_READER,
        ) shouldBe false
    }

    @Test
    fun `exiting selection restores gesture ownership`() {
        NovelReaderSelectionGestureArbiter.shouldRestoreGestureOwnership(activeSelection = null) shouldBe true
    }

    @Test
    fun `entering selection cancels active page turn and auto scroll intent`() {
        NovelReaderSelectionGestureArbiter.shouldCancelReaderMotionOnSelectionStart(
            pageTurnIntentActive = true,
            autoScrollIntentActive = false,
        ) shouldBe true
        NovelReaderSelectionGestureArbiter.shouldCancelReaderMotionOnSelectionStart(
            pageTurnIntentActive = false,
            autoScrollIntentActive = true,
        ) shouldBe true
        NovelReaderSelectionGestureArbiter.shouldCancelReaderMotionOnSelectionStart(
            pageTurnIntentActive = false,
            autoScrollIntentActive = false,
        ) shouldBe false
    }
}
