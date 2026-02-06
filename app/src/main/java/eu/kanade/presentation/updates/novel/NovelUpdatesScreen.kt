package eu.kanade.presentation.updates.novel

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.ui.updates.novel.NovelUpdatesItem
import eu.kanade.tachiyomi.ui.updates.novel.NovelUpdatesScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.selectedBackground
import java.time.LocalDate

@Composable
fun NovelUpdatesScreen(
    state: NovelUpdatesScreenModel.State,
    lastUpdated: Long,
    contentPadding: PaddingValues = PaddingValues(),
    onNovelClick: (Long) -> Unit,
    onChapterClick: (Long) -> Unit,
    onToggleSelection: (NovelUpdatesItem, Boolean) -> Unit,
    onMultiBookmarkClicked: (List<NovelUpdatesItem>, Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<NovelUpdatesItem>, Boolean) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NovelUpdatesBottomBar(
                selected = state.selected,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            )
        },
    ) { _ ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.items.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.information_no_recent,
                modifier = Modifier.padding(contentPadding),
            )
            else -> FastScrollLazyColumn(contentPadding = contentPadding) {
                lastUpdatedItem(lastUpdated)
                novelUpdatesUiItems(
                    uiModels = state.getUiModel(),
                    selectionMode = state.selectionMode,
                    onNovelClick = onNovelClick,
                    onChapterClick = onChapterClick,
                    onToggleSelection = onToggleSelection,
                )
            }
        }
    }
}

private fun LazyListScope.lastUpdatedItem(lastUpdated: Long) {
    item(key = "novelUpdates-lastUpdated") {
        Box(
            modifier = Modifier
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        ) {
            Text(
                text = stringResource(MR.strings.updates_last_update_info, relativeTimeSpanString(lastUpdated)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun LazyListScope.novelUpdatesUiItems(
    uiModels: List<NovelUpdatesUiModel>,
    selectionMode: Boolean,
    onNovelClick: (Long) -> Unit,
    onChapterClick: (Long) -> Unit,
    onToggleSelection: (NovelUpdatesItem, Boolean) -> Unit,
) {
    items(
        items = uiModels,
        key = {
            when (it) {
                is NovelUpdatesUiModel.Header -> "novelUpdatesHeader-${it.hashCode()}"
                is NovelUpdatesUiModel.Item -> "novelUpdates-${it.item.update.novelId}-${it.item.update.chapterId}"
            }
        },
        contentType = {
            when (it) {
                is NovelUpdatesUiModel.Header -> "header"
                is NovelUpdatesUiModel.Item -> "item"
            }
        },
    ) { model ->
        when (model) {
            is NovelUpdatesUiModel.Header -> {
                ListGroupHeader(
                    modifier = Modifier.animateItemFastScroll(),
                    text = eu.kanade.presentation.components.relativeDateText(model.date),
                )
            }
            is NovelUpdatesUiModel.Item -> {
                NovelUpdatesItemRow(
                    modifier = Modifier.animateItemFastScroll(),
                    item = model.item,
                    selectionMode = selectionMode,
                    onNovelClick = onNovelClick,
                    onChapterClick = onChapterClick,
                    onToggleSelection = onToggleSelection,
                )
            }
        }
    }
}

@Composable
private fun NovelUpdatesItemRow(
    item: NovelUpdatesItem,
    selectionMode: Boolean,
    onNovelClick: (Long) -> Unit,
    onChapterClick: (Long) -> Unit,
    onToggleSelection: (NovelUpdatesItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val update = item.update
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .selectedBackground(item.selected)
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelection(item, !item.selected)
                    } else {
                        onChapterClick(update.chapterId)
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onToggleSelection(item, !item.selected)
                },
            )
            .padding(horizontal = MaterialTheme.padding.medium)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Square(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxHeight(),
            data = update.coverData,
            onClick = { onNovelClick(update.novelId) },
        )
        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
            Text(
                text = update.novelTitle,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!update.read) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = stringResource(MR.strings.unread),
                        modifier = Modifier
                            .size(8.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (update.bookmark) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    DotSeparatorText()
                }
                Text(
                    text = update.chapterName,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NovelUpdatesBottomBar(
    selected: List<NovelUpdatesItem>,
    onMultiBookmarkClicked: (List<NovelUpdatesItem>, Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<NovelUpdatesItem>, Boolean) -> Unit,
) {
    EntryBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        onBookmarkClicked = {
            onMultiBookmarkClicked(selected, true)
        }.takeIf { selected.fastAny { !it.update.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked(selected, false)
        }.takeIf { selected.fastAll { it.update.bookmark } },
        onMarkAsViewedClicked = {
            onMultiMarkAsReadClicked(selected, true)
        }.takeIf { selected.fastAny { !it.update.read } },
        onMarkAsUnviewedClicked = {
            onMultiMarkAsReadClicked(selected, false)
        }.takeIf { selected.fastAny { it.update.read || it.update.lastPageRead > 0L } },
        onDownloadClicked = null,
        onDeleteClicked = null,
        isManga = true,
    )
}

sealed interface NovelUpdatesUiModel {
    data class Header(val date: LocalDate) : NovelUpdatesUiModel
    data class Item(val item: NovelUpdatesItem) : NovelUpdatesUiModel
}
