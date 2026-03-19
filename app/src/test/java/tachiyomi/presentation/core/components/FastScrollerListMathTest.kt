package tachiyomi.presentation.core.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FastScrollerListMathTest {

    @Test
    fun `partial top and bottom visibility contributes to list progress`() {
        computeFastScrollerListProgress(
            visibleItems = listOf(
                FastScrollerItemInfo(index = 0, top = -50, size = 100),
                FastScrollerItemInfo(index = 1, top = 50, size = 100),
                FastScrollerItemInfo(index = 2, top = 170, size = 100),
            ),
            totalItemsCount = 10,
            scrollHeightPx = 240f,
        ) shouldBe FastScrollerListProgress(
            topItem = FastScrollerItemInfo(index = 0, top = -50, size = 100),
            bottomItem = FastScrollerItemInfo(index = 2, top = 170, size = 100),
            previousSections = 0.5f,
            remainingSections = 7.3f,
            scrollableSections = 7.8f,
        )
    }

    @Test
    fun `sticky headers are ignored when resolving visible progress anchors`() {
        computeFastScrollerListProgress(
            visibleItems = listOf(
                FastScrollerItemInfo(index = 4, top = 0, size = 60, isStickyHeader = true),
                FastScrollerItemInfo(index = 5, top = 60, size = 100),
                FastScrollerItemInfo(index = 6, top = 160, size = 100),
            ),
            totalItemsCount = 20,
            scrollHeightPx = 240f,
        ) shouldBe FastScrollerListProgress(
            topItem = FastScrollerItemInfo(index = 5, top = 60, size = 100),
            bottomItem = FastScrollerItemInfo(index = 6, top = 160, size = 100),
            previousSections = 4.4f,
            remainingSections = 13.2f,
            scrollableSections = 17.6f,
        )
    }

    @Test
    fun `thumb derived from current list progress maps back to the same section on first drag`() {
        val progress = computeFastScrollerListProgress(
            visibleItems = listOf(
                FastScrollerItemInfo(index = 20, top = -20, size = 100),
                FastScrollerItemInfo(index = 21, top = 80, size = 100),
                FastScrollerItemInfo(index = 22, top = 180, size = 100),
            ),
            totalItemsCount = 100,
            scrollHeightPx = 240f,
        )!!
        val estimate = resolveFastScrollerListEstimateState(
            previousState = null,
            progress = progress,
            anyScrollInProgress = false,
        )
        val trackBounds = resolveFastScrollerTrackBounds(
            contentHeightPx = 600f,
            thumbTopPadding = 120f,
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
            totalItemsCount = 100,
            visibleItems = listOf(
                FastScrollerItemInfo(index = 20, top = -20, size = 100),
                FastScrollerItemInfo(index = 21, top = 80, size = 100),
                FastScrollerItemInfo(index = 22, top = 180, size = 100),
            ),
            scrollHeightPx = 240f,
        )

        request shouldBe FastScrollerScrollRequest(index = 22, scrollOffset = -180)
    }

    @Test
    fun `collapsed track returns top padding and no drag request`() {
        val progress = computeFastScrollerListProgress(
            visibleItems = listOf(
                FastScrollerItemInfo(index = 0, top = 0, size = 100),
                FastScrollerItemInfo(index = 1, top = 100, size = 100),
            ),
            totalItemsCount = 2,
            scrollHeightPx = 200f,
        )!!
        val trackBounds = resolveFastScrollerTrackBounds(
            contentHeightPx = 180f,
            thumbTopPadding = 120f,
            thumbBottomPadding = 24f,
            afterContentPaddingPx = 16,
            thumbHeightPx = 48f,
        )

        resolveFastScrollerListThumbOffset(
            progress = progress,
            maxRemainingSections = 0.2f,
            trackBounds = trackBounds,
        ) shouldBe 120f

        resolveFastScrollerListScrollRequest(
            thumbOffsetY = 120f,
            trackBounds = trackBounds,
            maxRemainingSections = 0.2f,
            totalItemsCount = 2,
            visibleItems = listOf(
                FastScrollerItemInfo(index = 0, top = 0, size = 100),
                FastScrollerItemInfo(index = 1, top = 100, size = 100),
            ),
            scrollHeightPx = 200f,
        ) shouldBe null
    }

    @Test
    fun `off screen drag targets use estimated section size instead of unrelated visible row size`() {
        val request = resolveFastScrollerListScrollRequest(
            thumbOffsetY = 76.25f,
            trackBounds = FastScrollerTrackBounds(
                heightPx = 300f,
                effectiveTrackHeightPx = 100f,
                minThumbOffsetY = 0f,
                maxThumbOffsetY = 100f,
            ),
            maxRemainingSections = 100f,
            totalItemsCount = 100,
            visibleItems = listOf(
                FastScrollerItemInfo(index = 10, top = 0, size = 80),
                FastScrollerItemInfo(index = 11, top = 80, size = 200),
                FastScrollerItemInfo(index = 12, top = 280, size = 90),
            ),
            scrollHeightPx = 300f,
        )

        request shouldBe FastScrollerScrollRequest(index = 76, scrollOffset = -269)
    }
}
