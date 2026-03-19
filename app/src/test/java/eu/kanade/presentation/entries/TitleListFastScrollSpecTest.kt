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
    fun `overlay chrome hides while fast scroll thumb is dragged`() {
        shouldShowTitleFastScrollOverlayChrome(isThumbDragged = true) shouldBe false
        shouldShowTitleFastScrollOverlayChrome(isThumbDragged = false) shouldBe true
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
