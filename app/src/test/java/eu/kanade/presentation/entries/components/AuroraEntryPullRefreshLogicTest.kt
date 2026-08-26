package eu.kanade.presentation.entries.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraEntryPullRefreshLogicTest {

    @Test
    fun `shouldStartAuroraEntryHoldRefresh returns true only when fraction is at or above trigger threshold`() {
        shouldStartAuroraEntryHoldRefresh(
            distanceFraction = 0.99f,
            refreshing = false,
            hasTriggeredForCurrentPull = false,
        ) shouldBe false

        shouldStartAuroraEntryHoldRefresh(
            distanceFraction = 1.0f,
            refreshing = false,
            hasTriggeredForCurrentPull = false,
        ) shouldBe true

        shouldStartAuroraEntryHoldRefresh(
            distanceFraction = 1.25f,
            refreshing = false,
            hasTriggeredForCurrentPull = false,
        ) shouldBe true
    }

    @Test
    fun `shouldStartAuroraEntryHoldRefresh returns false if already refreshing or triggered`() {
        shouldStartAuroraEntryHoldRefresh(
            distanceFraction = 1.5f,
            refreshing = true,
            hasTriggeredForCurrentPull = false,
        ) shouldBe false

        shouldStartAuroraEntryHoldRefresh(
            distanceFraction = 1.5f,
            refreshing = false,
            hasTriggeredForCurrentPull = true,
        ) shouldBe false
    }

    @Test
    fun `shouldResetAuroraEntryHoldRefreshLatch resets when user releases pull close to origin`() {
        shouldResetAuroraEntryHoldRefreshLatch(0.05f) shouldBe true
        shouldResetAuroraEntryHoldRefreshLatch(0.10f) shouldBe true
        shouldResetAuroraEntryHoldRefreshLatch(0.11f) shouldBe false
    }

    @Test
    fun `isHoldThresholdReached evaluates boolean boundary cleanly for LaunchedEffect keying`() {
        isAuroraHoldThresholdReached(0.99f) shouldBe false
        isAuroraHoldThresholdReached(1.0f) shouldBe true
        isAuroraHoldThresholdReached(1.4f) shouldBe true
    }
}
