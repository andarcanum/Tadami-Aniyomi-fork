package eu.kanade.presentation.library.anime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.library.components.LibraryTabs
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

@Composable
fun AnimeLibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: List<LibraryAnime>,
    contentPadding: PaddingValues,
    currentPage: () -> Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onCategoryLongSelected: ((Int) -> Unit)? = null,
    onAnimeClicked: (Long) -> Unit,
    onContinueWatchingClicked: ((LibraryAnime) -> Unit)?,
    onToggleSelection: (LibraryAnime) -> Unit,
    onToggleRangeSelection: (LibraryAnime) -> Unit,
    onTogglePinned: (AnimeLibraryItem) -> Unit,
    onRefresh: (Category?) -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getNumberOfAnimeForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getAnimeLibraryForPage: (Int) -> List<AnimeLibraryItem>,
) {
    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        // NEW-19 (anime port of the manga fix): coerceIn(0, ...) - with an empty category list
        // coerceAtMost(lastIndex = -1) produced initialPage = -1 and rememberPagerState crashed.
        val coercedCurrentPage = remember { currentPage().coerceIn(0, categories.lastIndex.coerceAtLeast(0)) }
        val pagerState = rememberPagerState(coercedCurrentPage) { categories.size }

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        // B2 (port of the Aurora tab sync): keep the pager in agreement with the persisted model
        // index. Without model->pager sync a grouping-change reset (index = 0) never moved the
        // pager, so the toolbar title, the sort/filter sheet and pull-to-refresh targeted a
        // different category than the one shown; the clamp write-back retires a stale persisted
        // index after categories shrink. Both are skipped while the category list is transiently
        // empty so the index is not reset for good.
        val coercedModelPage = currentPage().coerceIn(0, categories.lastIndex.coerceAtLeast(0))
        LaunchedEffect(coercedModelPage, categories.size) {
            if (categories.isEmpty()) return@LaunchedEffect
            if (coercedModelPage != currentPage()) {
                onChangeCurrentPage(coercedModelPage)
            }
            if (coercedModelPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(coercedModelPage)
            }
        }

        if (showPageTabs && categories.size > 1) {
            LaunchedEffect(categories) {
                if (categories.size <= pagerState.currentPage) {
                    pagerState.scrollToPage(categories.size - 1)
                }
            }
            LibraryTabs(
                categories = categories,
                pagerState = pagerState,
                getNumberOfItemsForCategory = getNumberOfAnimeForCategory,
                onTabItemLongClick = onCategoryLongSelected,
            ) { scope.launch { pagerState.animateScrollToPage(it) } }
        }

        val notSelectionMode = selection.isEmpty()
        val onClickAnime = { anime: LibraryAnime ->
            if (notSelectionMode) {
                onAnimeClicked(anime.anime.id)
            } else {
                onToggleSelection(anime)
            }
        }

        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                // D-M8 (anime port): guard the stale page index - categories can shrink while the
                // pager still holds an old current page (IOOB).
                val started = categories.getOrNull(currentPage())?.let(onRefresh) ?: false
                if (!started) return@PullRefresh
                scope.launch {
                    // Fake refresh status but hide it after a second as it's a long running task
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            enabled = notSelectionMode,
        ) {
            AnimeLibraryPager(
                state = pagerState,
                categories = categories,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selectedAnime = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getLibraryForPage = getAnimeLibraryForPage,
                onClickAnime = onClickAnime,
                onLongClickAnime = onToggleRangeSelection,
                onTogglePinned = onTogglePinned,
                onClickContinueWatching = onContinueWatchingClicked,
            )
        }

        // Write the active category only when the pager settles (not mid-fling), so the
        // DataStore-backed preference is not written on every intermediate page change.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
                .collect { (page, scrolling) ->
                    // B3: skip transient empty-category settles (pageCount = 0 right after
                    // returning from a pushed screen) and redundant rewrites of the same page.
                    if (!scrolling && pagerState.pageCount > 0 && page != currentPage()) {
                        onChangeCurrentPage(page)
                    }
                }
        }
    }
}
