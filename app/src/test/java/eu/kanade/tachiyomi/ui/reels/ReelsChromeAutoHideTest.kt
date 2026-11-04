package eu.kanade.tachiyomi.ui.reels

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Chrome auto-hide policy (device report): the top bar must stay reachable whenever the feed
 * is not actually playing healthy content — otherwise an errored or stalled feed leaves the
 * user without retry or source switch and forces a screen re-entry.
 */
class ReelsChromeAutoHideTest {

    @Test
    fun `chrome hides only during real playback of a healthy feed`() {
        reelsChromeShouldAutoHide(
            visible = true,
            actuallyPlaying = true,
            hasItems = true,
            hasError = false,
            isLoading = false,
        ) shouldBe true
    }

    @Test
    fun `chrome stays reachable whenever a blocking surface is open`() {
        reelsChromeShouldAutoHide(
            visible = true,
            actuallyPlaying = true,
            hasItems = true,
            hasError = false,
            isLoading = false,
            blockingSurfaceOpen = true,
        ) shouldBe false
    }

    @Test
    fun `chrome stays reachable while the feed is errored empty stalled or paused`() {
        // Full-feed error: retry and source switch must be reachable.
        reelsChromeShouldAutoHide(
            true,
            actuallyPlaying = false,
            hasItems = false,
            hasError = true,
            isLoading = false,
        ) shouldBe
            false
        // Initial or stalled load: nothing plays, the bar keeps the escape hatches.
        reelsChromeShouldAutoHide(
            true,
            actuallyPlaying = false,
            hasItems = false,
            hasError = false,
            isLoading = true,
        ) shouldBe
            false
        // Paused playback: the user is looking at the controls on purpose.
        reelsChromeShouldAutoHide(
            true,
            actuallyPlaying = false,
            hasItems = true,
            hasError = false,
            isLoading = false,
        ) shouldBe
            false
        // Already hidden: the effect must not re-trigger a hide loop.
        reelsChromeShouldAutoHide(
            false,
            actuallyPlaying = true,
            hasItems = true,
            hasError = false,
            isLoading = false,
        ) shouldBe
            false
        // Mid-feed page error with items: the bar stays for the same reason.
        reelsChromeShouldAutoHide(
            true,
            actuallyPlaying = false,
            hasItems = true,
            hasError = true,
            isLoading = false,
        ) shouldBe
            false
    }

    @Test
    fun `status bar follows the feed chrome`() {
        // Clean fullscreen playback: no chrome, no status icons.
        reelsSystemChromeVisible(
            chromeVisible = false,
            moreMenuOpen = false,
            isSearchBarOpen = false,
            isFilterDialogOpen = false,
            isSourcePickerOpen = false,
            isLoginDialogOpen = false,
            isCustomFeedsOpen = false,
            isWebLoginDialogOpen = false,
        ) shouldBe false

        // Top bar up (revealed by tap or before the auto-hide): icons are back.
        reelsSystemChromeVisible(
            chromeVisible = true,
            moreMenuOpen = false,
            isSearchBarOpen = false,
            isFilterDialogOpen = false,
            isSourcePickerOpen = false,
            isLoginDialogOpen = false,
            isCustomFeedsOpen = false,
            isWebLoginDialogOpen = false,
        ) shouldBe true
    }

    @Test
    fun `status bar stays while a blocking surface covers the feed`() {
        // ⋮ settings block holds the chrome open.
        reelsSystemChromeVisible(
            chromeVisible = false,
            moreMenuOpen = true,
            isSearchBarOpen = false,
            isFilterDialogOpen = false,
            isSourcePickerOpen = false,
            isLoginDialogOpen = false,
            isCustomFeedsOpen = false,
            isWebLoginDialogOpen = false,
        ) shouldBe true

        // Search bar with the input field.
        reelsSystemChromeVisible(
            chromeVisible = false,
            moreMenuOpen = false,
            isSearchBarOpen = true,
            isFilterDialogOpen = false,
            isSourcePickerOpen = false,
            isLoginDialogOpen = false,
            isCustomFeedsOpen = false,
            isWebLoginDialogOpen = false,
        ) shouldBe true

        // Any open sheet (source picker as the representative case).
        reelsSystemChromeVisible(
            chromeVisible = false,
            moreMenuOpen = false,
            isSearchBarOpen = false,
            isFilterDialogOpen = false,
            isSourcePickerOpen = true,
            isLoginDialogOpen = false,
            isCustomFeedsOpen = false,
            isWebLoginDialogOpen = false,
        ) shouldBe true
    }
}
