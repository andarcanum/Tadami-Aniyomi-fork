package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.entries.novel.model.chaptersFiltered
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.util.formatChapterNumber
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.theme.active
import tachiyomi.domain.entries.novel.model.Novel as DomainNovel

@Composable
fun NovelScreen(
    state: NovelScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartReading: (() -> Unit)?,
    isReading: Boolean,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onToggleAllChaptersRead: () -> Unit,
    onShare: (() -> Unit)?,
    onWebView: (() -> Unit)?,
    onChapterClick: (Long) -> Unit,
    onChapterReadToggle: (Long) -> Unit,
    onChapterBookmarkToggle: (Long) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onChapterLongClick: (Long) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onMultiBookmarkClicked: (Boolean) -> Unit,
    onMultiMarkAsReadClicked: (Boolean) -> Unit,
) {
    val chapters = state.processedChapters
    val selectedIds = state.selectedChapterIds
    val selectedCount = selectedIds.size
    val isAnySelected = selectedCount > 0
    val selectedChapters = chapters.filter { it.id in selectedIds }
    Scaffold(
        topBar = {
            EntryToolbar(
                title = state.novel.title,
                hasFilters = state.novel.chaptersFiltered(),
                navigateUp = {
                    if (isAnySelected) onAllChapterSelected(false) else onBack()
                },
                onClickFilter = onFilterButtonClicked,
                onClickShare = onShare,
                onClickDownload = null,
                onClickEditCategory = null,
                onClickRefresh = onRefresh,
                onClickMigrate = null,
                onClickSettings = null,
                changeAnimeSkipIntro = null,
                actionModeCounter = selectedCount,
                onCancelActionMode = { onAllChapterSelected(false) },
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = onInvertSelection,
                titleAlphaProvider = { 1f },
                backgroundAlphaProvider = { 1f },
                isManga = true,
            )
        },
        bottomBar = {
            EntryBottomActionMenu(
                visible = selectedChapters.isNotEmpty(),
                isManga = true,
                onBookmarkClicked = {
                    onMultiBookmarkClicked(true)
                }.takeIf { selectedChapters.any { !it.bookmark } },
                onRemoveBookmarkClicked = {
                    onMultiBookmarkClicked(false)
                }.takeIf { selectedChapters.isNotEmpty() && selectedChapters.all { it.bookmark } },
                onMarkAsViewedClicked = {
                    onMultiMarkAsReadClicked(true)
                }.takeIf { selectedChapters.any { !it.read } },
                onMarkAsUnviewedClicked = {
                    onMultiMarkAsReadClicked(false)
                }.takeIf { selectedChapters.any { it.read || it.lastPageRead > 0L } },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                ) {
                    ItemCover.Book(
                        data = state.novel.thumbnailUrl,
                        modifier = Modifier.size(width = 104.dp, height = 148.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = state.novel.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.novel.author?.takeIf { it.isNotBlank() }?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = state.source.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.novel.description?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onStartReading != null) {
                        Button(
                            onClick = onStartReading,
                        ) {
                            Text(
                                text = stringResource(
                                    if (isReading) MR.strings.action_resume else MR.strings.action_start,
                                ),
                            )
                        }
                    }
                    Button(
                        onClick = onToggleFavorite,
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text(
                            text = stringResource(
                                if (state.novel.favorite) MR.strings.remove_from_library else MR.strings.add_to_library,
                            ),
                        )
                    }
                    Button(
                        onClick = onRefresh,
                    ) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                    }
                    Button(
                        onClick = onToggleAllChaptersRead,
                    ) {
                        Text(
                            text = stringResource(
                                if (state.chapters.any { !it.read }) {
                                    MR.strings.action_mark_as_read
                                } else {
                                    MR.strings.action_mark_as_unread
                                },
                            ),
                        )
                    }
                    if (onShare != null) {
                        IconButton(onClick = onShare) {
                            Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                        }
                    }
                    if (onWebView != null) {
                        IconButton(onClick = onWebView) {
                            Icon(imageVector = Icons.Outlined.Visibility, contentDescription = null)
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(MR.strings.chapters),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                )
            }

            items(
                items = chapters,
                key = { it.id },
            ) { chapter ->
                val selected = chapter.id in selectedIds
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .combinedClickable(
                            onClick = { onChapterClick(chapter.id) },
                            onLongClick = { onChapterLongClick(chapter.id) },
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val chapterTitle = when (state.novel.displayMode) {
                                DomainNovel.CHAPTER_DISPLAY_NUMBER -> {
                                    stringResource(
                                        MR.strings.display_mode_chapter,
                                        formatChapterNumber(chapter.chapterNumber),
                                    )
                                }
                                else -> {
                                    chapter.name.ifBlank {
                                        stringResource(
                                            MR.strings.display_mode_chapter,
                                            formatChapterNumber(chapter.chapterNumber),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (chapter.read) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (chapter.dateUpload > 0) {
                                Text(
                                    text = relativeDateTimeText(chapter.dateUpload),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!isAnySelected) {
                            IconButton(
                                onClick = { onChapterBookmarkToggle(chapter.id) },
                                modifier = Modifier.padding(start = 2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bookmark,
                                    contentDescription = null,
                                    tint = if (chapter.bookmark) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            IconButton(
                                onClick = { onChapterReadToggle(chapter.id) },
                                modifier = Modifier.padding(start = 2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = if (chapter.read) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(MaterialTheme.padding.small)) }
        }
    }
}
