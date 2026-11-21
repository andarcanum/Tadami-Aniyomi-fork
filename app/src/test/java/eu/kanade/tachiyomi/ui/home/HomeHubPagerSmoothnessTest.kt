package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.i18n.aniyomi.AYMR

class HomeHubPagerSmoothnessTest {

    @Test
    fun `beyondViewportPageCount keeps all sibling tabs warm on regular devices`() {
        resolveHomeHubBeyondViewportPageCount(tabCount = 1, isLowRamDevice = false) shouldBe 0
        resolveHomeHubBeyondViewportPageCount(tabCount = 2, isLowRamDevice = false) shouldBe 1
        resolveHomeHubBeyondViewportPageCount(tabCount = 3, isLowRamDevice = false) shouldBe 2
        resolveHomeHubBeyondViewportPageCount(tabCount = 5, isLowRamDevice = false) shouldBe 2
    }

    @Test
    fun `beyondViewportPageCount caps at 1 neighbor on low-RAM devices`() {
        resolveHomeHubBeyondViewportPageCount(tabCount = 1, isLowRamDevice = true) shouldBe 0
        resolveHomeHubBeyondViewportPageCount(tabCount = 2, isLowRamDevice = true) shouldBe 1
        resolveHomeHubBeyondViewportPageCount(tabCount = 3, isLowRamDevice = true) shouldBe 1
    }

    @Test
    fun `displayedTabIndex prioritizes pending tab tap over mid-flight currentPage`() {
        resolveHomeHubDisplayedTabIndex(
            settledPage = 0,
            currentPage = 0,
            pendingTargetPage = 2,
            lastIndex = 2,
        ) shouldBe 2
    }

    @Test
    fun `displayedTabIndex follows manual swipe currentPage when no tap is pending`() {
        resolveHomeHubDisplayedTabIndex(
            settledPage = 0,
            currentPage = 1,
            pendingTargetPage = null,
            lastIndex = 2,
        ) shouldBe 1
    }

    @Test
    fun `shouldCommitSettledHomeHubSection blocks commit mid-scroll and mid-animation`() {
        shouldCommitSettledHomeHubSection(
            settledPage = 1,
            isScrollInProgress = true,
            pendingTargetPage = null,
            lastIndex = 2,
        ) shouldBe false

        shouldCommitSettledHomeHubSection(
            settledPage = 1,
            isScrollInProgress = false,
            pendingTargetPage = 2,
            lastIndex = 2,
        ) shouldBe false

        shouldCommitSettledHomeHubSection(
            settledPage = 2,
            isScrollInProgress = false,
            pendingTargetPage = 2,
            lastIndex = 2,
        ) shouldBe true

        shouldCommitSettledHomeHubSection(
            settledPage = 1,
            isScrollInProgress = false,
            pendingTargetPage = null,
            lastIndex = 2,
        ) shouldBe true
    }

    @Test
    fun `shouldResetHomeHubListPosition skips already-at-top and stale tokens`() {
        shouldResetHomeHubListPosition(
            scrollResetToken = 0,
            lastHandledToken = 0,
            isSectionActive = true,
            firstVisibleItemIndex = 2,
            firstVisibleItemScrollOffset = 10,
        ) shouldBe false

        shouldResetHomeHubListPosition(
            scrollResetToken = 1,
            lastHandledToken = 0,
            isSectionActive = true,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        ) shouldBe false

        shouldResetHomeHubListPosition(
            scrollResetToken = 1,
            lastHandledToken = 0,
            isSectionActive = false,
            firstVisibleItemIndex = 3,
            firstVisibleItemScrollOffset = 40,
        ) shouldBe false

        shouldResetHomeHubListPosition(
            scrollResetToken = 1,
            lastHandledToken = 0,
            isSectionActive = true,
            firstVisibleItemIndex = 1,
            firstVisibleItemScrollOffset = 0,
        ) shouldBe true
    }

    @Test
    fun `headerSnapshot ignores non-header HomeHubUiState changes`() {
        val base = HomeHubUiState(
            userName = "Alex",
            userAvatar = "avatar.png",
            greeting = AYMR.strings.aurora_welcome_back,
            greetingReady = true,
            isLoading = false,
            showWelcome = false,
        )
        val updatedContent = base.copy(
            isDiscoveryRefreshing = true,
            discoveryFailedSourceCount = 2,
        )
        base.toHeaderSnapshot() shouldBe updatedContent.toHeaderSnapshot()
    }
}
