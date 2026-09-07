package eu.kanade.presentation.library.manga

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
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

@Composable
fun MangaLibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: List<MangaLibraryItem>,
    contentPadding: PaddingValues,
    currentPage: () -> Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onCategoryLongSelected: ((Int) -> Unit)? = null,
    onMangaClicked: (Long) -> Unit,
    onSeriesClicked: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (MangaLibraryItem) -> Unit,
    onToggleRangeSelection: (MangaLibraryItem) -> Unit,
    onTogglePinned: (MangaLibraryItem) -> Unit,
    onRefresh: suspend (Category?) -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getNumberOfMangaForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getLibraryForPage: (Int) -> List<MangaLibraryItem>,
) {
    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        // NEW-19: coerceIn(0, ...) - with an empty category list coerceAtMost(lastIndex = -1)
        // produced initialPage = -1 and rememberPagerState crashed.
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
                getNumberOfItemsForCategory = getNumberOfMangaForCategory,
                onTabItemLongClick = onCategoryLongSelected,
            ) { scope.launch { pagerState.animateScrollToPage(it) } }
        }

        val notSelectionMode = selection.isEmpty()
        val onClickItem = { item: MangaLibraryItem ->
            if (notSelectionMode) {
                when (item) {
                    is MangaLibraryItem.Single -> onMangaClicked(item.libraryManga.manga.id)
                    is MangaLibraryItem.Series -> onSeriesClicked(item.librarySeries.id)
                }
            } else {
                onToggleSelection(item)
            }
        }

        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                // I15: onRefresh is suspend (the update guard queries WorkManager, blocking) -
                // run it off MAIN inside the scope.
                scope.launch {
                    // D-M8: guard the stale page index - categories can shrink while the pager still
                    // holds an old current page (IOOB).
                    val started = categories.getOrNull(currentPage())?.let { onRefresh(it) } ?: false
                    if (!started) return@launch
                    // Fake refresh status but hide it after a second as it's a long running task
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            enabled = notSelectionMode,
        ) {
            MangaLibraryPager(
                state = pagerState,
                categories = categories,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selectedItems = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getLibraryForPage = getLibraryForPage,
                onClickItem = onClickItem,
                onClickSeries = onSeriesClicked,
                onLongClickItem = onToggleRangeSelection,
                onTogglePinned = onTogglePinned,
                onClickContinueReading = onContinueReadingClicked,
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
