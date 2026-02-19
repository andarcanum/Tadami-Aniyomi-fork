package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.GlobalSearchResultItem
import eu.kanade.presentation.browse.novel.components.GlobalNovelSearchCardRow
import eu.kanade.presentation.browse.novel.components.GlobalNovelSearchToolbar
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSearchItemResult
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun GlobalNovelSearchScreen(
    state: NovelSearchScreenModel.State,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (NovelSourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getNovel: @Composable (Novel) -> State<Novel>,
    onClickSource: (NovelCatalogueSource) -> Unit,
    onClickItem: (Novel) -> Unit,
    onLongClickItem: (Novel) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            GlobalNovelSearchToolbar(
                searchQuery = state.searchQuery,
                progress = state.progress,
                total = state.total,
                navigateUp = navigateUp,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                sourceFilter = state.sourceFilter,
                onChangeSearchFilter = onChangeSearchFilter,
                onlyShowHasResults = state.onlyShowHasResults,
                onToggleResults = onToggleResults,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        GlobalSearchContent(
            items = state.filteredItems,
            contentPadding = paddingValues,
            getNovel = getNovel,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
        )
    }
}

@Composable
internal fun GlobalSearchContent(
    items: Map<NovelCatalogueSource, NovelSearchItemResult>,
    contentPadding: PaddingValues,
    getNovel: @Composable (Novel) -> State<Novel>,
    onClickSource: (NovelCatalogueSource) -> Unit,
    onClickItem: (Novel) -> Unit,
    onLongClickItem: (Novel) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items.forEach { (source, result) ->
            item(key = source.id) {
                GlobalSearchResultItem(
                    title = source.name,
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                    modifier = Modifier.animateItem(),
                ) {
                    when (result) {
                        NovelSearchItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }
                        is NovelSearchItemResult.Success -> {
                            GlobalNovelSearchCardRow(
                                titles = result.result,
                                getNovel = getNovel,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                            )
                        }
                        is NovelSearchItemResult.Error -> {
                            GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                    }
                }
            }
        }
    }
}
