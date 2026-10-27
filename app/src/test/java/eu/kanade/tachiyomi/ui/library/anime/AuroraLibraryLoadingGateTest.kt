package eu.kanade.tachiyomi.ui.library.anime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Regression net for the library flicker: the Aurora section pager must never be replaced by a
 * full-screen spinner. [androidx.compose.foundation.pager.PagerState.currentPage] flips before a
 * swipe gesture ends, so a full-screen gate on it destroyed the pager mid-drag on the first visit
 * to a section (background -> spinner -> content flash) while warm sections stayed smooth.
 */
class AuroraLibraryLoadingGateTest {

    @Test
    fun `aurora never takes the full screen loading path`() {
        shouldShowAuroraLibraryFullScreenLoading(isAurora = true, isCurrentSectionLoading = true) shouldBe false
        shouldShowAuroraLibraryFullScreenLoading(isAurora = true, isCurrentSectionLoading = false) shouldBe false
    }

    @Test
    fun `classic keeps its full screen loading path`() {
        shouldShowAuroraLibraryFullScreenLoading(isAurora = false, isCurrentSectionLoading = true) shouldBe true
        shouldShowAuroraLibraryFullScreenLoading(isAurora = false, isCurrentSectionLoading = false) shouldBe false
    }
}
