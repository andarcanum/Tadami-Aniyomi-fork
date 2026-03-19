package tachiyomi.presentation.core.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FastScrollerGridMathTest {

    @Test
    fun `row based grid progress tracks multi column viewport`() {
        computeFastScrollerGridProgress(
            visibleItems = listOf(
                FastScrollerItemInfo(index = 6, top = -20, size = 120),
                FastScrollerItemInfo(index = 7, top = -20, size = 120),
                FastScrollerItemInfo(index = 8, top = -20, size = 120),
                FastScrollerItemInfo(index = 9, top = 100, size = 120),
                FastScrollerItemInfo(index = 10, top = 100, size = 120),
                FastScrollerItemInfo(index = 11, top = 100, size = 120),
                FastScrollerItemInfo(index = 12, top = 220, size = 120),
                FastScrollerItemInfo(index = 13, top = 220, size = 120),
                FastScrollerItemInfo(index = 14, top = 220, size = 120),
            ),
            totalItemsCount = 30,
            columnCount = 3,
        ) shouldBe FastScrollerGridProgress(
            avgSizePerRow = 120f,
            scrollOffset = 260,
            scrollRange = 1200,
            totalItemsCount = 30,
            columnCount = 3,
        )
    }

    @Test
    fun `grid thumb offset stays within track bounds for uneven row heights`() {
        val progress = computeFastScrollerGridProgress(
            visibleItems = listOf(
                FastScrollerItemInfo(index = 3, top = -10, size = 100),
                FastScrollerItemInfo(index = 4, top = -10, size = 100),
                FastScrollerItemInfo(index = 5, top = -10, size = 100),
                FastScrollerItemInfo(index = 6, top = 90, size = 150),
                FastScrollerItemInfo(index = 7, top = 90, size = 150),
                FastScrollerItemInfo(index = 8, top = 90, size = 150),
            ),
            totalItemsCount = 60,
            columnCount = 3,
        )!!
        val trackBounds = resolveFastScrollerTrackBounds(
            contentHeightPx = 560f,
            thumbTopPadding = 96f,
            thumbBottomPadding = 24f,
            afterContentPaddingPx = 0,
            thumbHeightPx = 48f,
        )

        val thumbOffset = resolveFastScrollerGridThumbOffset(
            progress = progress,
            trackBounds = trackBounds,
        )

        (thumbOffset >= trackBounds.minThumbOffsetY) shouldBe true
        (thumbOffset <= trackBounds.maxThumbOffsetY) shouldBe true
    }

    @Test
    fun `library grid like drag request stays within valid item bounds`() {
        val progress = computeFastScrollerGridProgress(
            visibleItems = listOf(
                FastScrollerItemInfo(index = 12, top = -30, size = 140),
                FastScrollerItemInfo(index = 13, top = -30, size = 140),
                FastScrollerItemInfo(index = 14, top = -30, size = 140),
                FastScrollerItemInfo(index = 15, top = 110, size = 140),
                FastScrollerItemInfo(index = 16, top = 110, size = 140),
                FastScrollerItemInfo(index = 17, top = 110, size = 140),
            ),
            totalItemsCount = 90,
            columnCount = 3,
        )!!
        val trackBounds = resolveFastScrollerTrackBounds(
            contentHeightPx = 720f,
            thumbTopPadding = 64f,
            thumbBottomPadding = 24f,
            afterContentPaddingPx = 0,
            thumbHeightPx = 48f,
        )

        val request = resolveFastScrollerGridScrollRequest(
            thumbOffsetY = resolveFastScrollerGridThumbOffset(progress, trackBounds),
            progress = progress,
            trackBounds = trackBounds,
            viewportHeightPx = 632f,
        )

        request shouldBe FastScrollerScrollRequest(index = 12, scrollOffset = 30)
    }
}
