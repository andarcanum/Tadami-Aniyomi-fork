package eu.kanade.presentation.entries

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TitleListFastScrollSpecTest {

    @Test
    fun `fast scroll stays hidden before the chapter block is reached`() {
        resolveTitleListFastScrollSpec(
            baseTopPaddingPx = 24,
            firstVisibleItemIndex = 0,
            blockStartIndex = 3,
            blockStartOffsetPx = null,
        ) shouldBe TitleListFastScrollSpec(
            thumbAllowed = false,
            topPaddingPx = 24,
        )
    }

    @Test
    fun `fast scroll uses block header offset while the header is visible below the toolbar`() {
        resolveTitleListFastScrollSpec(
            baseTopPaddingPx = 24,
            firstVisibleItemIndex = 2,
            blockStartIndex = 3,
            blockStartOffsetPx = 320,
        ) shouldBe TitleListFastScrollSpec(
            thumbAllowed = true,
            topPaddingPx = 320,
        )
    }

    @Test
    fun `fast scroll clamps to toolbar padding when the header overlaps the top area`() {
        resolveTitleListFastScrollSpec(
            baseTopPaddingPx = 24,
            firstVisibleItemIndex = 3,
            blockStartIndex = 3,
            blockStartOffsetPx = 8,
        ) shouldBe TitleListFastScrollSpec(
            thumbAllowed = true,
            topPaddingPx = 24,
        )
    }

    @Test
    fun `fast scroll stays available after the block header has scrolled off screen`() {
        resolveTitleListFastScrollSpec(
            baseTopPaddingPx = 24,
            firstVisibleItemIndex = 5,
            blockStartIndex = 3,
            blockStartOffsetPx = null,
        ) shouldBe TitleListFastScrollSpec(
            thumbAllowed = true,
            topPaddingPx = 24,
        )
    }

    @Test
    fun `overlay chrome shows normally when the list is not expanded`() {
        shouldShowTitleFastScrollOverlayChrome(
            isThumbDragged = false,
            isExpandedList = false,
            isReverseScrolling = false,
        ) shouldBe true
    }

    @Test
    fun `overlay chrome stays hidden while the expanded list is idle at the top`() {
        shouldShowTitleFastScrollOverlayChrome(
            isThumbDragged = false,
            isExpandedList = true,
            isReverseScrolling = false,
        ) shouldBe false
    }

    @Test
    fun `overlay chrome stays hidden while the expanded list scrolls forward`() {
        shouldShowTitleFastScrollOverlayChrome(
            isThumbDragged = false,
            isExpandedList = true,
            isReverseScrolling = false,
        ) shouldBe false
    }

    @Test
    fun `overlay chrome stays visible after reverse scroll while away from the top`() {
        shouldShowTitleFastScrollOverlayChrome(
            isThumbDragged = false,
            isExpandedList = true,
            isReverseScrolling = true,
        ) shouldBe true
    }

    @Test
    fun `overlay chrome hides again once reverse scrolling stops`() {
        shouldShowTitleFastScrollOverlayChrome(
            isThumbDragged = false,
            isExpandedList = true,
            isReverseScrolling = false,
        ) shouldBe false
    }

    @Test
    fun `overlay chrome hides while fast scroll thumb is dragged`() {
        shouldShowTitleFastScrollOverlayChrome(
            isThumbDragged = true,
            isExpandedList = true,
            isReverseScrolling = true,
        ) shouldBe false
    }

    @Test
    fun `overlay chrome reveal state latches on reverse scroll until the next forward scroll`() {
        var revealed = false

        revealed = resolveTitleFastScrollOverlayRevealState(
            currentRevealState = revealed,
            isExpandedList = true,
            isScrolling = true,
            movedForward = false,
        )
        revealed shouldBe true

        revealed = resolveTitleFastScrollOverlayRevealState(
            currentRevealState = revealed,
            isExpandedList = true,
            isScrolling = false,
            movedForward = false,
        )
        revealed shouldBe true

        revealed = resolveTitleFastScrollOverlayRevealState(
            currentRevealState = revealed,
            isExpandedList = true,
            isScrolling = true,
            movedForward = true,
        )
        revealed shouldBe false
    }

    @Test
    fun `overlay chrome reveal state resets when the list collapses`() {
        resolveTitleFastScrollOverlayRevealState(
            currentRevealState = true,
            isExpandedList = false,
            isScrolling = false,
            movedForward = false,
        ) shouldBe false
    }

    @Test
    fun `overlay accumulator enables reveal on reverse scroll`() {
        reduceTitleFastScrollOverlayAccumulator(
            current = TitleFastScrollOverlayAccumulator(
                prevIndex = 4,
                prevOffset = 200,
                revealed = false,
            ),
            isExpandedList = true,
            isScrolling = true,
            index = 4,
            offset = 180,
        ) shouldBe TitleFastScrollOverlayAccumulator(
            prevIndex = 4,
            prevOffset = 180,
            revealed = true,
        )
    }

    @Test
    fun `overlay accumulator keeps reveal latched while idle`() {
        reduceTitleFastScrollOverlayAccumulator(
            current = TitleFastScrollOverlayAccumulator(
                prevIndex = 4,
                prevOffset = 180,
                revealed = true,
            ),
            isExpandedList = true,
            isScrolling = false,
            index = 4,
            offset = 180,
        ) shouldBe TitleFastScrollOverlayAccumulator(
            prevIndex = 4,
            prevOffset = 180,
            revealed = true,
        )
    }

    @Test
    fun `overlay accumulator resets reveal on forward scroll`() {
        reduceTitleFastScrollOverlayAccumulator(
            current = TitleFastScrollOverlayAccumulator(
                prevIndex = 4,
                prevOffset = 180,
                revealed = true,
            ),
            isExpandedList = true,
            isScrolling = true,
            index = 4,
            offset = 220,
        ) shouldBe TitleFastScrollOverlayAccumulator(
            prevIndex = 4,
            prevOffset = 220,
            revealed = false,
        )
    }

    @Test
    fun `overlay accumulator resets reveal when list collapses`() {
        reduceTitleFastScrollOverlayAccumulator(
            current = TitleFastScrollOverlayAccumulator(
                prevIndex = 4,
                prevOffset = 180,
                revealed = true,
            ),
            isExpandedList = false,
            isScrolling = false,
            index = 0,
            offset = 0,
        ) shouldBe TitleFastScrollOverlayAccumulator(
            prevIndex = 0,
            prevOffset = 0,
            revealed = false,
        )
    }

    @Test
    fun `overlay accumulator treats larger index as forward movement`() {
        reduceTitleFastScrollOverlayAccumulator(
            current = TitleFastScrollOverlayAccumulator(
                prevIndex = 4,
                prevOffset = 900,
                revealed = true,
            ),
            isExpandedList = true,
            isScrolling = true,
            index = 5,
            offset = 0,
        ) shouldBe TitleFastScrollOverlayAccumulator(
            prevIndex = 5,
            prevOffset = 0,
            revealed = false,
        )
    }

    @Test
    fun `floating action button hides while fast scroll thumb is dragged`() {
        shouldShowTitleFastScrollFloatingActionButton(
            isEligibleToShow = true,
            isThumbDragged = true,
        ) shouldBe false

        shouldShowTitleFastScrollFloatingActionButton(
            isEligibleToShow = true,
            isThumbDragged = false,
        ) shouldBe true

        shouldShowTitleFastScrollFloatingActionButton(
            isEligibleToShow = false,
            isThumbDragged = false,
        ) shouldBe false
    }
}
