package eu.kanade.tachiyomi.ui.reels.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsVideoPageMediaPlanTest {

    @Test
    fun `a present url always plays directly`() {
        reelMediaPlan(
            rawUrl = "https://cdn.example.com/a.mp4",
            hasResolver = false,
            lazyResolution = false,
        ) shouldBe ReelMediaPlan.READY
    }

    @Test
    fun `a blank url resolves only with both a resolver and source permission`() {
        reelMediaPlan("", hasResolver = true, lazyResolution = true) shouldBe ReelMediaPlan.RESOLVE
        reelMediaPlan("", hasResolver = true, lazyResolution = false) shouldBe ReelMediaPlan.UNPLAYABLE
        reelMediaPlan("", hasResolver = false, lazyResolution = true) shouldBe ReelMediaPlan.UNPLAYABLE
        reelMediaPlan("", hasResolver = false, lazyResolution = false) shouldBe ReelMediaPlan.UNPLAYABLE
    }
}
