package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaReaderProgressCodecTest {

    @Test
    fun `encode and decode scroll progress round-trips`() {
        val encoded = encodeWebtoonScrollProgress(index = 4, offsetPx = 1234)

        decodeWebtoonScrollProgress(encoded) shouldBe WebtoonScrollProgress(index = 4, offsetPx = 1234)
    }

    @Test
    fun `decode returns null for plain legacy index`() {
        decodeWebtoonScrollProgress(7).shouldBeNull()
    }

    @Test
    fun `decode clamps negative and out of range values`() {
        val encoded = encodeWebtoonScrollProgress(index = -5, offsetPx = 2_000_000)

        decodeWebtoonScrollProgress(encoded) shouldBe WebtoonScrollProgress(index = 0, offsetPx = 999_999)
    }

    @Test
    fun `decode chapter progress restores encoded offset when enabled`() {
        val encoded = encodeWebtoonScrollProgress(index = 3, offsetPx = 250)

        decodeStoredChapterProgress(encoded, restoreOffset = true) shouldBe
            ChapterScrollProgress(index = 3, offsetPx = 250)
    }

    @Test
    fun `decode chapter progress strips encoded offset when disabled`() {
        val encoded = encodeWebtoonScrollProgress(index = 3, offsetPx = 250)

        decodeStoredChapterProgress(encoded, restoreOffset = false) shouldBe
            ChapterScrollProgress(index = 3, offsetPx = 0)
    }

    @Test
    fun `decode chapter progress keeps legacy index`() {
        decodeStoredChapterProgress(11, restoreOffset = true) shouldBe ChapterScrollProgress(index = 11, offsetPx = 0)
    }

    @Test
    fun `long page resume keeps same relative position after page height changes`() {
        val savedProgress = encodeWebtoonScrollProgress(
            index = 0,
            offsetPx = 600,
            pageHeightPx = 3000,
        )
        val decoded = decodeStoredChapterProgress(savedProgress, restoreOffset = true)

        val restoredOffset = resolveWebtoonRestoreOffsetPx(
            progress = decoded,
            currentPageHeightPx = 4500,
        )

        restoredOffset shouldBe 900
    }

    @Test
    fun `long page resume returns same offset when page height is unchanged`() {
        val savedProgress = encodeWebtoonScrollProgress(
            index = 0,
            offsetPx = 420,
            pageHeightPx = 2800,
        )
        val decoded = decodeStoredChapterProgress(savedProgress, restoreOffset = true)

        val restoredOffset = resolveWebtoonRestoreOffsetPx(
            progress = decoded,
            currentPageHeightPx = 2800,
        )

        restoredOffset shouldBe 420
    }

    @Test
    fun `slight scroll then reopen restores same relative place after long page expands`() {
        val savedProgress = encodeWebtoonScrollProgress(
            index = 0,
            offsetPx = 280,
            pageHeightPx = 5600,
        )
        val decoded = decodeStoredChapterProgress(savedProgress, restoreOffset = true)

        // First pass can happen on an incomplete pre-layout size.
        val preLayoutRestoreOffset = resolveWebtoonRestoreOffsetPx(
            progress = decoded,
            currentPageHeightPx = 1200,
        )
        preLayoutRestoreOffset shouldBe 60

        // Final pass after full image/layout should land at the same relative position.
        val expandedPageRestoreOffset = resolveWebtoonRestoreOffsetPx(
            progress = decoded,
            currentPageHeightPx = 6800,
        )
        expandedPageRestoreOffset shouldBe 340
    }

    @Test
    fun `pending restore does not clear before strong ready signal`() {
        val settle = evaluateWebtoonRestoreSettle(
            currentOffsetPx = 340,
            targetOffsetPx = 340,
            currentPageHeightPx = 6800,
            previousPageHeightPx = 6800,
            previousStableHeightFrames = 2,
            isPageReady = true,
            imageDecoded = false,
            previousReadyFrames = 5,
            minStableHeightFrames = 2,
            offsetTolerancePx = 2,
            minReadyFramesFallback = 30,
        )

        settle.settled shouldBe true
        settle.canClearPending shouldBe false
    }

    @Test
    fun `pending restore clears after decode signal when settled`() {
        val settle = evaluateWebtoonRestoreSettle(
            currentOffsetPx = 340,
            targetOffsetPx = 340,
            currentPageHeightPx = 6800,
            previousPageHeightPx = 6800,
            previousStableHeightFrames = 2,
            isPageReady = true,
            imageDecoded = true,
            previousReadyFrames = 1,
            minStableHeightFrames = 2,
            offsetTolerancePx = 2,
            minReadyFramesFallback = 30,
        )

        settle.settled shouldBe true
        settle.canClearPending shouldBe true
    }

    @Test
    fun `pending restore clears on ready fallback after enough frames`() {
        val settle = evaluateWebtoonRestoreSettle(
            currentOffsetPx = 340,
            targetOffsetPx = 340,
            currentPageHeightPx = 6800,
            previousPageHeightPx = 6800,
            previousStableHeightFrames = 2,
            isPageReady = true,
            imageDecoded = false,
            previousReadyFrames = 29,
            minStableHeightFrames = 2,
            offsetTolerancePx = 2,
            minReadyFramesFallback = 30,
        )

        settle.settled shouldBe true
        settle.canClearPending shouldBe true
    }
}
