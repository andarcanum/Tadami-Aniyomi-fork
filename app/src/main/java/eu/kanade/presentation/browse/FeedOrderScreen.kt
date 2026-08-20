package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.FeedOrderListItem
import eu.kanade.presentation.theme.AuroraTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
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
    onAddClick: (() -> Unit)? = null,
) {
    when {
        isLoading -> LoadingScreen()
        else -> {
            val lazyListState = rememberLazyListState()
            val feeds = items.orEmpty()

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

            Column(modifier = Modifier.fillMaxSize()) {
                if (onAddClick != null) {
                    FeedManageAddRow(onClick = onAddClick)
                }
                if (isEmpty || feeds.isEmpty()) {
                    EmptyScreen(
                        stringRes = MR.strings.empty_screen,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
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
    }
}

@Composable
private fun FeedManageAddRow(onClick: () -> Unit) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(AYMR.strings.feed_add),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.accent,
        )
    }
}
