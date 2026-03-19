package tachiyomi.presentation.core.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FastScrollerSharedConsumerRegressionTest {

    @Test
    fun `thumb stays draggable while the list is already scrolling during an active drag`() {
        shouldEnableFastScrollerDrag(
            isThumbVisible = true,
            isThumbDragged = true,
            isScrollInProgress = true,
        ) shouldBe true

        shouldEnableFastScrollerDrag(
            isThumbVisible = true,
            isThumbDragged = false,
            isScrollInProgress = true,
        ) shouldBe false

        shouldEnableFastScrollerDrag(
            isThumbVisible = true,
            isThumbDragged = false,
            isScrollInProgress = false,
        ) shouldBe true
    }

    @Test
    fun `updates like sticky date headers do not break shared list fast scroll estimation`() {
        val progress = computeFastScrollerListProgress(
            visibleItems = listOf(
                FastScrollerItemInfo(index = 30, top = 0, size = 48, isStickyHeader = true),
                FastScrollerItemInfo(index = 31, top = 48, size = 88),
                FastScrollerItemInfo(index = 32, top = 136, size = 88),
                FastScrollerItemInfo(index = 33, top = 224, size = 88),
            ),
            totalItemsCount = 120,
            scrollHeightPx = 280f,
        )!!
        val estimate = resolveFastScrollerListEstimateState(
            previousState = null,
            progress = progress,
            anyScrollInProgress = false,
        )
        val trackBounds = resolveFastScrollerTrackBounds(
            contentHeightPx = 700f,
            thumbTopPadding = 96f,
            thumbBottomPadding = 24f,
            afterContentPaddingPx = 0,
            thumbHeightPx = 48f,
        )
        val thumbOffset = resolveFastScrollerListThumbOffset(
            progress = progress,
            maxRemainingSections = estimate.maxRemainingSections,
            trackBounds = trackBounds,
        )
        val request = resolveFastScrollerListScrollRequest(
            thumbOffsetY = thumbOffset,
            trackBounds = trackBounds,
            maxRemainingSections = estimate.maxRemainingSections,
            totalItemsCount = 120,
            visibleItems = listOf(
                FastScrollerItemInfo(index = 30, top = 0, size = 48, isStickyHeader = true),
                FastScrollerItemInfo(index = 31, top = 48, size = 88),
                FastScrollerItemInfo(index = 32, top = 136, size = 88),
                FastScrollerItemInfo(index = 33, top = 224, size = 88),
            ),
            scrollHeightPx = 280f,
        )

        progress.topItem.index shouldBe 31
        progress.bottomItem.index shouldBe 33
        request?.index shouldBe 33
    }

    @Test
    fun `library grid like shared fast scroll keeps row start alignment`() {
        val progress = computeFastScrollerGridProgress(
            visibleItems = listOf(
                FastScrollerItemInfo(index = 18, top = -16, size = 150),
                FastScrollerItemInfo(index = 19, top = -16, size = 150),
                FastScrollerItemInfo(index = 20, top = -16, size = 150),
                FastScrollerItemInfo(index = 21, top = 134, size = 150),
                FastScrollerItemInfo(index = 22, top = 134, size = 150),
                FastScrollerItemInfo(index = 23, top = 134, size = 150),
            ),
            totalItemsCount = 300,
            columnCount = 3,
        )!!
        val trackBounds = resolveFastScrollerTrackBounds(
            contentHeightPx = 800f,
            thumbTopPadding = 72f,
            thumbBottomPadding = 24f,
            afterContentPaddingPx = 0,
            thumbHeightPx = 48f,
        )

        resolveFastScrollerGridScrollRequest(
            thumbOffsetY = resolveFastScrollerGridThumbOffset(progress, trackBounds),
            progress = progress,
            trackBounds = trackBounds,
            viewportHeightPx = 704f,
        )?.index shouldBe 18
    }
}
