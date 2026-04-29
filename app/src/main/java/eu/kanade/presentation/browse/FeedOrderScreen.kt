package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.FeedOrderListItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun <T> FeedOrderScreen(
    isLoading: Boolean,
    isEmpty: Boolean,
    items: List<T>?,
    itemFeed: (T) -> FeedSavedSearch,
    itemTitle: (T) -> String,
    itemSubtitle: (T) -> String,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onChangeOrder: (FeedSavedSearch, Int) -> Unit,
) {
    when {
        isLoading -> LoadingScreen()
        isEmpty || items.isNullOrEmpty() -> EmptyScreen(stringRes = MR.strings.empty_screen)
        else -> {
            val lazyListState = rememberLazyListState()
            val feeds = items

            val feedsState = remember { feeds.toMutableStateList() }
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val item = feedsState.removeAt(from.index)
                feedsState.add(to.index, item)
                onChangeOrder(itemFeed(item), to.index)
            }

            LaunchedEffect(feeds) {
                if (!reorderableState.isAnyItemDragging) {
                    feedsState.clear()
                    feedsState.addAll(feeds)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = topSmallPaddingValues +
                    PaddingValues(horizontal = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(
                    items = feedsState,
                    key = { itemFeed(it).id },
                ) { feed ->
                    ReorderableItem(reorderableState, itemFeed(feed).id) {
                        FeedOrderListItem(
                            title = itemTitle(feed),
                            subtitle = itemSubtitle(feed),
                            dragHandleModifier = Modifier.draggableHandle(),
                            onDelete = { onClickDelete(itemFeed(feed)) },
                        )
                    }
                }
            }
        }
    }
}
