package eu.kanade.tachiyomi.ui.home

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.model.HomeHeroCtaMode
import eu.kanade.domain.ui.model.HomeHeroMode
import eu.kanade.domain.ui.model.HomeHubRecentCardMode

internal const val HOME_HUB_TAB_SCROLL_STIFFNESS = 440f
internal const val HOME_HUB_TAB_SCROLL_DAMPING = Spring.DampingRatioNoBouncy
internal const val HOME_HUB_MAX_BEYOND_VIEWPORT_PAGES = 2

internal fun homeHubTabRibbonAnimationSpec(): AnimationSpec<Float> = spring(
    dampingRatio = HOME_HUB_TAB_SCROLL_DAMPING,
    stiffness = HOME_HUB_TAB_SCROLL_STIFFNESS,
)

/**
 * Keeps sibling HomeHub pages (Anime / Manga / Novel) composed off-screen so horizontal
 * ribbon swipes and tab clicks never build an entire screen or re-trigger 20+ Coil loads
 * mid-gesture. On low-RAM devices we cap to 1 neighbor to limit peak memory pressure.
 */
internal fun resolveHomeHubBeyondViewportPageCount(
    tabCount: Int,
    isLowRamDevice: Boolean,
): Int {
    if (tabCount <= 1) return 0
    val maxOffscreen = if (isLowRamDevice) 1 else HOME_HUB_MAX_BEYOND_VIEWPORT_PAGES
    return (tabCount - 1).coerceIn(1, maxOffscreen)
}

/**
 * Tab indicator index shown in the header. When a user taps a tab button, we highlight
 * the tapped tab immediately while the HorizontalPager ribbon animates toward it; during
 * manual finger drags (pendingTargetPage == null), we follow pagerState.currentPage.
 */
internal fun resolveHomeHubDisplayedTabIndex(
    settledPage: Int,
    currentPage: Int,
    pendingTargetPage: Int?,
    lastIndex: Int,
): Int {
    if (lastIndex < 0) return 0
    if (pendingTargetPage != null && pendingTargetPage in 0..lastIndex) {
        return pendingTargetPage
    }
    return currentPage.coerceIn(0, lastIndex)
}

/**
 * Commits selectedSection, scroll reset, and header expansion ONLY after a swipe or
 * programmatic ribbon animation has settled. Firing these mid-swipe (at the 50% midpoint
 * of pagerState.currentPage) caused recomposition/scroll-reset hitches during gestures.
 */
internal fun shouldCommitSettledHomeHubSection(
    settledPage: Int,
    isScrollInProgress: Boolean,
    pendingTargetPage: Int?,
    lastIndex: Int,
): Boolean {
    if (lastIndex < 0 || isScrollInProgress) return false
    if (settledPage !in 0..lastIndex) return false
    return pendingTargetPage == null || pendingTargetPage == settledPage
}

/**
 * Avoids redundant scrollToItem(0) dispatches during initial composition or when the
 * section's LazyColumn is already sitting at the top.
 */
internal fun shouldResetHomeHubListPosition(
    scrollResetToken: Int,
    lastHandledToken: Int,
    isSectionActive: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean {
    if (!isSectionActive) return false
    if (scrollResetToken <= lastHandledToken) return false
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
}

/**
 * Narrow header-only projection of [HomeHubUiState] so [HomeHubTab] does not recompose
 * the entire pager and header whenever history/recommendations/discovery lists emit.
 */
@Immutable
internal data class HomeHubHeaderSnapshot(
    val userName: String,
    val userAvatar: String,
    val greeting: StringResource,
    val greetingReady: Boolean,
    val isLoading: Boolean,
)

internal fun HomeHubUiState.toHeaderSnapshot(): HomeHubHeaderSnapshot = HomeHubHeaderSnapshot(
    userName = userName,
    userAvatar = userAvatar,
    greeting = greeting,
    greetingReady = greetingReady,
    isLoading = isLoading,
)

/**
 * Stable bridge read inside HomeHub pager pages so the `tabs` list passed to
 * [TabbedScreenAurora] is constructed once per enabled section set instead of rebuilding
 * TabContent lambdas on every section switch, scroll reset, or search query edit.
 */
@Stable
internal class HomeHubPagerBridgeState(
    initialSection: HomeHubSection,
    initialHeroCtaMode: HomeHeroCtaMode,
    initialRecentCardMode: HomeHubRecentCardMode,
    initialHeroMode: HomeHeroMode,
) {
    var activeSection by mutableStateOf(initialSection)
    var scrollResetToken by mutableIntStateOf(0)
    var animeSearchQuery by mutableStateOf<String?>(null)
    var mangaSearchQuery by mutableStateOf<String?>(null)
    var novelSearchQuery by mutableStateOf<String?>(null)
    var heroCtaMode by mutableStateOf(initialHeroCtaMode)
    var recentCardMode by mutableStateOf(initialRecentCardMode)
    var heroMode by mutableStateOf(initialHeroMode)
    var hiddenSnackbar by mutableStateOf<String?>(null)

    var onUndoHidden: () -> Unit = {}
    var onDiscoveryHide: (HomeHubDiscoveryItem) -> Unit = {}
    var onDiscoveryBlacklistTag: (HomeHubDiscoveryItem, String, Int) -> Unit = { _, _, _ -> }
    var onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit = { _, _, _ -> }
}
