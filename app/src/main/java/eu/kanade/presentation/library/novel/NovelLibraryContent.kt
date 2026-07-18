package eu.kanade.presentation.library.novel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.library.components.LibraryTabs
import eu.kanade.presentation.library.components.idsToHashSet
import eu.kanade.presentation.novel.sourceAwareNovelCoverModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.util.plus
import kotlin.time.Duration.Companion.seconds

@Composable
fun NovelLibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: List<NovelLibraryItem>,
    contentPadding: PaddingValues,
    currentPage: () -> Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onClearFilters: () -> Unit,
    onChangeCurrentPage: (Int) -> Unit,
    onCategoryLongSelected: ((Int) -> Unit)? = null,
    onNovelClicked: (NovelLibraryItem) -> Unit,
    onToggleSelection: (NovelLibraryItem) -> Unit,
    onToggleRangeSelection: (NovelLibraryItem) -> Unit,
    onRefresh: (Category?) -> Boolean,
    getNumberOfNovelForCategory: (Category) -> Int?,
    displayMode: LibraryDisplayMode,
    columns: Int,
    showDownloadBadge: Boolean,
    downloadedNovelIds: Set<Long>,
    showUnreadBadge: Boolean,
    showLanguageBadge: Boolean,
    sourceLanguageByNovelId: Map<Long, String>,
    getLibraryForPage: (Int) -> List<NovelLibraryItem>,
) {
    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val coercedCurrentPage = remember { currentPage().coerceAtMost(categories.lastIndex) }
        val pagerState = rememberPagerState(coercedCurrentPage) { categories.size }

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (showPageTabs && categories.size > 1) {
            LaunchedEffect(categories) {
                if (categories.size <= pagerState.currentPage) {
                    pagerState.scrollToPage(categories.size - 1)
                }
            }
            LibraryTabs(
                categories = categories,
                pagerState = pagerState,
                getNumberOfItemsForCategory = getNumberOfNovelForCategory,
                onTabItemLongClick = onCategoryLongSelected,
            ) { scope.launch { pagerState.animateScrollToPage(it) } }
        }

        val notSelectionMode = selection.isEmpty()
        val onClickItem = { item: NovelLibraryItem ->
            if (notSelectionMode) {
                onNovelClicked(item)
            } else {
                onToggleSelection(item)
            }
        }

        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                val started = onRefresh(categories[currentPage()])
                if (!started) return@PullRefresh
                scope.launch {
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            enabled = notSelectionMode,
        ) {
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
                verticalAlignment = Alignment.Top,
            ) { page ->
                if (page !in ((pagerState.currentPage - 1)..(pagerState.currentPage + 1))) {
                    return@HorizontalPager
                }
                val library = getLibraryForPage(page)

                if (library.isEmpty()) {
                    val msg = when {
                        !searchQuery.isNullOrEmpty() -> MR.strings.no_results_found
                        hasActiveFilters -> MR.strings.error_no_match
                        else -> MR.strings.information_no_manga_category
                    }
                    EmptyScreen(
                        stringRes = msg,
                        modifier = Modifier
                            .padding(contentPadding + PaddingValues(bottom = contentPadding.calculateBottomPadding()))
                            .fillMaxSize(),
                        actions = if (hasActiveFilters && searchQuery.isNullOrEmpty()) {
                            persistentListOf(
                                EmptyScreenAction(
                                    stringRes = MR.strings.action_reset,
                                    icon = Icons.Default.Refresh,
                                    onClick = onClearFilters,
                                ),
                            )
                        } else {
                            null
                        },
                    )
                    return@HorizontalPager
                }

                val selectedIds = remember(selection) { selection.idsToHashSet { it.id } }

                if (displayMode == LibraryDisplayMode.List) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ) + PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        items(library, key = { it.id }) { item ->
                            val isSelected = item.id in selectedIds
                            NovelLibraryListItem(
                                item = item,
                                badgeState = resolveNovelLibraryBadgeState(
                                    item = item,
                                    showDownloadBadge = showDownloadBadge,
                                    downloadedNovelIds = downloadedNovelIds,
                                    showUnreadBadge = showUnreadBadge,
                                    showLanguageBadge = showLanguageBadge,
                                    sourceLanguage = sourceLanguageByNovelId[item.id].orEmpty(),
                                ),
                                isSelected = isSelected,
                                onClick = { onClickItem(item) },
                                onLongClick = { onToggleRangeSelection(item) },
                            )
                        }
                    }
                } else {
                    val gridCells = when {
                        columns > 0 -> GridCells.Fixed(columns)
                        displayMode == LibraryDisplayMode.ComfortableGrid -> GridCells.Adaptive(minSize = 180.dp)
                        else -> GridCells.Adaptive(minSize = 140.dp)
                    }

                    LazyVerticalGrid(
                        columns = gridCells,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ) + PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        items(library, key = { it.id }) { item ->
                            val isSelected = item.id in selectedIds
                            NovelLibraryGridItem(
                                item = item,
                                badgeState = resolveNovelLibraryBadgeState(
                                    item = item,
                                    showDownloadBadge = showDownloadBadge,
                                    downloadedNovelIds = downloadedNovelIds,
                                    showUnreadBadge = showUnreadBadge,
                                    showLanguageBadge = showLanguageBadge,
                                    sourceLanguage = sourceLanguageByNovelId[item.id].orEmpty(),
                                ),
                                isSelected = isSelected,
                                showMetadata = displayMode != LibraryDisplayMode.CoverOnlyGrid,
                                onClick = { onClickItem(item) },
                                onLongClick = { onToggleRangeSelection(item) },
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelLibraryGridItem(
    item: NovelLibraryItem,
    badgeState: eu.kanade.presentation.library.novel.NovelLibraryBadgeState,
    isSelected: Boolean,
    showMetadata: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val progressText = if (item.totalChapters > 0) {
        "${item.unreadCount}/${item.totalChapters}"
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = if (isSelected) {
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
            ) {
                ItemCover.Book(
                    data = (item as? NovelLibraryItem.Single)?.libraryNovel?.novel?.let {
                        sourceAwareNovelCoverModel(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                )
                if (badgeState.showDownloaded || badgeState.unreadCount != null || badgeState.language != null) {
                    BadgeGroup(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopStart)
                            .padding(6.dp),
                    ) {
                        if (badgeState.showDownloaded) {
                            Badge(text = "DL")
                        }
                        badgeState.unreadCount?.let {
                            Badge(text = it.toString())
                        }
                        badgeState.language?.let {
                            Badge(text = it.uppercase())
                        }
                    }
                }
            }
            if (showMetadata) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                if (progressText != null) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelLibraryListItem(
    item: NovelLibraryItem,
    badgeState: eu.kanade.presentation.library.novel.NovelLibraryBadgeState,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val progressText = if (item.totalChapters > 0) {
        "${item.unreadCount}/${item.totalChapters}"
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = if (isSelected) {
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Box(
                modifier = Modifier
                    .height(112.dp)
                    .aspectRatio(0.68f),
            ) {
                ItemCover.Book(
                    data = (item as? NovelLibraryItem.Single)?.libraryNovel?.novel?.let {
                        sourceAwareNovelCoverModel(it)
                    },
                    modifier = Modifier
                        .height(112.dp)
                        .aspectRatio(0.68f),
                )
                if (badgeState.showDownloaded || badgeState.unreadCount != null || badgeState.language != null) {
                    BadgeGroup(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopStart)
                            .padding(6.dp),
                    ) {
                        if (badgeState.showDownloaded) {
                            Badge(text = "DL")
                        }
                        badgeState.unreadCount?.let {
                            Badge(text = it.toString())
                        }
                        badgeState.language?.let {
                            Badge(text = it.uppercase())
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                )
                if (progressText != null) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
