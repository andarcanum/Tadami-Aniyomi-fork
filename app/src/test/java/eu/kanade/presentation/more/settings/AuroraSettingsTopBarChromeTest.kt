package eu.kanade.presentation.more.settings

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraSettingsTopBarChromeTest {

    @Test
    fun `aurora settings top bar offset limit matches measured height`() {
        resolveAuroraSettingsTopBarHeightOffsetLimit(heightPx = 240) shouldBe -240f
    }

    @Test
    fun `aurora settings top bar offset limit stays zero for empty height`() {
        resolveAuroraSettingsTopBarHeightOffsetLimit(heightPx = 0) shouldBe 0f
    }

    @Test
    fun `aurora settings top bar offset limit excludes status bar inset`() {
        resolveAuroraSettingsTopBarHeightOffsetLimit(heightPx = 260, statusBarInsetPx = 80) shouldBe -180f
        resolveAuroraSettingsTopBarHeightOffsetLimit(heightPx = 60, statusBarInsetPx = 80) shouldBe 0f
    }

    @Test
    fun `aurora settings top bar backdrop progress fades smoothly and snaps for zero fade distance`() {
        resolveAuroraTopBarBackdropProgress(scrolledDownPx = 0f, fadeDistancePx = 48f) shouldBe 0f
        resolveAuroraTopBarBackdropProgress(scrolledDownPx = 0.4f, fadeDistancePx = 48f) shouldBe 0f
        resolveAuroraTopBarBackdropProgress(scrolledDownPx = 24f, fadeDistancePx = 48f) shouldBe 0.5f
        resolveAuroraTopBarBackdropProgress(scrolledDownPx = 120f, fadeDistancePx = 48f) shouldBe 1f
        resolveAuroraTopBarBackdropProgress(scrolledDownPx = 2f, fadeDistancePx = 0f) shouldBe 1f
        resolveAuroraTopBarBackdropProgress(scrolledDownPx = 0.2f, fadeDistancePx = 0f) shouldBe 0f
    }

    @Test
    fun `quick return stays revealed through row height and hide threshold then hides`() {
        val rowHeightPx = 180
        val hideThreshold = 60f
        val showThreshold = 40f

        var state = AuroraTopBarQuickReturnState()
        // Scroll halfway through header height: must stay revealed (no top void)
        state = reduceAuroraTopBarQuickReturn(
            current = state,
            consumedY = -120f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        state.isRevealed shouldBe true
        state.scrolledDownPx shouldBe 120f

        // Scroll past row height but still shy of rowHeight + hideThreshold (180 + 60 = 240)
        state = reduceAuroraTopBarQuickReturn(
            current = state,
            consumedY = -110f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        state.isRevealed shouldBe true
        state.scrolledDownPx shouldBe 230f

        // Cross rowHeight + hideThreshold -> hides cleanly
        state = reduceAuroraTopBarQuickReturn(
            current = state,
            consumedY = -15f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        state.isRevealed shouldBe false
        state.scrolledDownPx shouldBe 245f
    }

    @Test
    fun `quick return ignores upward micro jitter below threshold and reveals on intentional pull`() {
        val rowHeightPx = 180
        val hideThreshold = 60f
        val showThreshold = 40f

        var state = AuroraTopBarQuickReturnState(
            scrolledDownPx = 600f,
            isRevealed = false,
        )

        // Small upward bump (25px < 40px) stays hidden
        state = reduceAuroraTopBarQuickReturn(
            current = state,
            consumedY = 25f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        state.isRevealed shouldBe false
        state.showAccumulatorPx shouldBe 25f

        // Reversal downward resets showAccumulator
        state = reduceAuroraTopBarQuickReturn(
            current = state,
            consumedY = -10f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        state.isRevealed shouldBe false
        state.showAccumulatorPx shouldBe 0f

        // Intentional 45px upward scroll (>= 40px) reveals bar immediately mid-list
        state = reduceAuroraTopBarQuickReturn(
            current = state,
            consumedY = 45f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        state.isRevealed shouldBe true
        state.showAccumulatorPx shouldBe 0f

        // Subsequent small downward scroll (< 60px) keeps bar steady; >= 60px hides again
        state = reduceAuroraTopBarQuickReturn(
            current = state,
            consumedY = -30f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        state.isRevealed shouldBe true

        state = reduceAuroraTopBarQuickReturn(
            current = state,
            consumedY = -30f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        state.isRevealed shouldBe false
    }

    @Test
    fun `quick return reveals near top zone resets on overscroll and when non scrollable`() {
        val rowHeightPx = 180
        val hideThreshold = 60f
        val showThreshold = 40f

        // Re-entering top zone <= rowHeightPx reveals even if deltaUp < showThreshold
        val topZoneState = reduceAuroraTopBarQuickReturn(
            current = AuroraTopBarQuickReturnState(scrolledDownPx = 195f, isRevealed = false),
            consumedY = 20f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        topZoneState.scrolledDownPx shouldBe 175f
        topZoneState.isRevealed shouldBe true

        // Unconsumed upward pull at top edge zeros residual scroll offset
        val overscrollReset = reduceAuroraTopBarQuickReturn(
            current = AuroraTopBarQuickReturnState(scrolledDownPx = 30f, isRevealed = true),
            consumedY = 0f,
            availableY = 12f,
            rowHeightPx = rowHeightPx,
            canScroll = true,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        overscrollReset shouldBe AuroraTopBarQuickReturnState()

        // Becoming non-scrollable resets completely to top revealed state
        val nonScrollableReset = reduceAuroraTopBarQuickReturn(
            current = AuroraTopBarQuickReturnState(scrolledDownPx = 500f, isRevealed = false),
            consumedY = -20f,
            availableY = 0f,
            rowHeightPx = rowHeightPx,
            canScroll = false,
            hideThresholdPx = hideThreshold,
            showThresholdPx = showThreshold,
        )
        nonScrollableReset shouldBe AuroraTopBarQuickReturnState()
    }
}
