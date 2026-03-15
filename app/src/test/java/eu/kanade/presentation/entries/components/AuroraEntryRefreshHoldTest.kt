package eu.kanade.presentation.entries.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraEntryRefreshHoldTest {

    @Test
    fun `hold refresh can trigger only after threshold when not refreshing and not yet triggered`() {
        shouldStartAuroraEntryHoldRefresh(
            distanceFraction = 1.0f,
            refreshing = false,
            hasTriggeredForCurrentPull = false,
        ) shouldBe true
    }

    @Test
    fun `hold refresh does not trigger before threshold or while refreshing or after current-pull trigger`() {
        shouldStartAuroraEntryHoldRefresh(
            distanceFraction = 0.92f,
            refreshing = false,
            hasTriggeredForCurrentPull = false,
        ) shouldBe false

        shouldStartAuroraEntryHoldRefresh(
            distanceFraction = 1.1f,
            refreshing = true,
            hasTriggeredForCurrentPull = false,
        ) shouldBe false

        shouldStartAuroraEntryHoldRefresh(
            distanceFraction = 1.1f,
            refreshing = false,
            hasTriggeredForCurrentPull = true,
        ) shouldBe false
    }

    @Test
    fun `hold refresh latch resets only after pull snaps back near rest`() {
        shouldResetAuroraEntryHoldRefreshLatch(distanceFraction = 0.06f) shouldBe true
        shouldResetAuroraEntryHoldRefreshLatch(distanceFraction = 0.25f) shouldBe false
    }
}
