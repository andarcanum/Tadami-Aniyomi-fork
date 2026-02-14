package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Done
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.manga.components.ScanlatorBranchSelector
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.entries.novel.model.Novel as DomainNovel

internal const val NOVEL_CHAPTERS_PAGE_SIZE = 120

@Composable
fun NovelScreen(
    state: NovelScreenModel.State.Success,
    isFromSource: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartReading: (() -> Unit)?,
    isReading: Boolean,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onToggleAllChaptersRead: () -> Unit,
    onShare: (() -> Unit)?,
    onWebView: (() -> Unit)?,
    onSourceSettings: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    trackingCount: Int,
    onOpenBatchDownloadDialog: (() -> Unit)?,
    onOpenEpubExportDialog: (() -> Unit)?,
    onChapterClick: (Long) -> Unit,
    onChapterReadToggle: (Long) -> Unit,
    onChapterBookmarkToggle: (Long) -> Unit,
    onChapterDownloadToggle: (Long) -> Unit,
    onFilterButtonClicked: () -> Unit,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    onChapterLongClick: (Long) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onMultiBookmarkClicked: (Boolean) -> Unit,
    onMultiMarkAsReadClicked: (Boolean) -> Unit,
    onMultiDownloadClicked: () -> Unit,
    onMultiDeleteClicked: () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme by uiPreferences.appTheme().collectAsState()

    // Route to Aurora implementation if Aurora theme is active
    if (theme.isAuroraStyle) {
        NovelScreenAuroraImpl(
            state = state,
            isFromSource = isFromSource,
            nextUpdate = state.novel.expectedNextUpdate,
            onBack = { if (state.selectedChapterIds.isNotEmpty()) onAllChapterSelected(false) else onBack() },
            onStartReading = onStartReading,
            isReading = isReading,
            onToggleFavorite = onToggleFavorite,
            onRefresh = onRefresh,
            onShare = onShare,
            onWebView = onWebView,
            onMigrateClicked = onMigrateClicked,
            onTrackingClicked = onTrackingClicked,
            trackingCount = trackingCount,
            onOpenBatchDownloadDialog = onOpenBatchDownloadDialog,
            onOpenEpubExportDialog = onOpenEpubExportDialog,
            onChapterClick = onChapterClick,
            onChapterLongClick = onChapterLongClick,
            onChapterReadToggle = onChapterReadToggle,
            onChapterBookmarkToggle = onChapterBookmarkToggle,
            onChapterDownloadToggle = onChapterDownloadToggle,
            onFilterButtonClicked = onFilterButtonClicked,
            scanlatorChapterCounts = scanlatorChapterCounts,
            selectedScanlator = selectedScanlator,
            onScanlatorSelected = onScanlatorSelected,
            onToggleAllSelection = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
        )
        return
    }

    // Standard implementation (non-Aurora)
    val isAurora = theme.isAuroraStyle
    val auroraColors = AuroraTheme.colors

    val chapters = state.processedChapters
    val selectedIds = state.selectedChapterIds
    val selectedCount = selectedIds.size
    val isAnySelected = selectedCount > 0
    val selectedChapters = chapters.filter { it.id in selectedIds }
    val downloadedChapterIds = state.downloadedChapterIds
    var visibleChapterCount by remember(chapters) {
        mutableIntStateOf(
            initialVisibleChapterCount(
                totalCount = chapters.size,
                pageSize = NOVEL_CHAPTERS_PAGE_SIZE,
            ),
        )
    }
    val visibleChapters = remember(chapters, visibleChapterCount) {
        chapters.take(visibleChapterCount)
    }

    Scaffold(
        topBar = {
            EntryToolbar(
                title = state.novel.title,
                hasFilters = state.filterActive,
                navigateUp = {
                    if (isAnySelected) onAllChapterSelected(false) else onBack()
                },
                onClickFilter = onFilterButtonClicked,
                onClickShare = onShare,
                onClickDownload = null,
                onClickEditCategory = null,
                onClickRefresh = onRefresh,
                onClickMigrate = onMigrateClicked,
                onClickSettings = onSourceSettings,
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
                onDownloadClicked = onMultiDownloadClicked.takeIf {
                    selectedChapters.any { chapter -> chapter.id !in downloadedChapterIds }
                },
                onDeleteClicked = onMultiDeleteClicked.takeIf {
                    selectedChapters.any { chapter -> chapter.id in downloadedChapterIds }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
        ) {
            item {
                val context = LocalContext.current
                val backdropGradientColors = listOf(
                    Color.Transparent,
                    MaterialTheme.colorScheme.background,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(state.novel.thumbnailUrl)
                            .crossfade(true)
                            .placeholderMemoryCacheKey(state.novel.thumbnailUrl)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(colors = backdropGradientColors),
                                )
                            }
                            .blur(4.dp)
                            .alpha(0.2f),
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAurora) {
                                auroraColors.glass
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        ),
                        border = if (isAurora) {
                            BorderStroke(1.dp, auroraColors.divider.copy(alpha = 0.35f))
                        } else {
                            null
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.padding.medium),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                        ) {
                            ItemCover.Book(
                                data = ImageRequest.Builder(context)
                                    .data(state.novel.thumbnailUrl)
                                    .crossfade(true)
                                    .placeholderMemoryCacheKey(state.novel.thumbnailUrl)
                                    .build(),
                                modifier = Modifier.size(width = 112.dp, height = 158.dp),
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
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Text(
                                    text = state.source.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAurora) {
                                        auroraColors.textSecondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(
                                    text = pluralStringResource(
                                        MR.plurals.manga_num_chapters,
                                        chapters.size,
                                        chapters.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAurora) {
                                        auroraColors.textSecondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                novelStatusText(state.novel.status)?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isAurora) {
                                            auroraColors.accent
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                }
                            }
                        }

                        state.novel.description?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isAurora) {
                                    auroraColors.textSecondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = MaterialTheme.padding.medium,
                                        end = MaterialTheme.padding.medium,
                                        bottom = MaterialTheme.padding.medium,
                                    ),
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
                        Button(onClick = onStartReading) {
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
                    TextButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                    }
                    TextButton(onClick = onToggleAllChaptersRead) {
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
                    TextButton(onClick = onTrackingClicked) {
                        Icon(
                            imageVector = if (trackingCount == 0) {
                                Icons.Outlined.Sync
                            } else {
                                Icons.Outlined.Done
                            },
                            contentDescription = null,
                        )
                        Text(
                            text = if (trackingCount == 0) {
                                stringResource(MR.strings.manga_tracking_tab)
                            } else {
                                pluralStringResource(MR.plurals.num_trackers, trackingCount, trackingCount)
                            },
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (onOpenBatchDownloadDialog != null) {
                        TextButton(onClick = onOpenBatchDownloadDialog) {
                            Icon(imageVector = Icons.Outlined.Download, contentDescription = null)
                            Text(
                                text = stringResource(MR.strings.manga_download),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    if (onOpenEpubExportDialog != null) {
                        TextButton(onClick = onOpenEpubExportDialog) {
                            Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                            Text(
                                text = stringResource(AYMR.strings.novel_epub_short),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(MR.strings.chapters),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.filterActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.active,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(MR.strings.action_filter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.active,
                            )
                        }
                    }
                }
            }

            if (state.showScanlatorSelector) {
                item {
                    ScanlatorBranchSelector(
                        scanlatorChapterCounts = scanlatorChapterCounts,
                        selectedScanlator = selectedScanlator,
                        onScanlatorSelected = onScanlatorSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                    )
                }
            }

            items(
                items = visibleChapters,
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
                            val downloaded = chapter.id in state.downloadedChapterIds
                            val downloading = chapter.id in state.downloadingChapterIds
                            IconButton(
                                onClick = { onChapterDownloadToggle(chapter.id) },
                                modifier = Modifier.padding(start = 2.dp),
                            ) {
                                Icon(
                                    imageVector = when {
                                        downloading -> Icons.Outlined.FileDownloadOff
                                        downloaded -> Icons.Outlined.Delete
                                        else -> Icons.Outlined.Download
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        downloading -> MaterialTheme.colorScheme.tertiary
                                        downloaded -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
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
            if (visibleChapterCount < chapters.size) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(
                            onClick = {
                                visibleChapterCount = nextVisibleChapterCount(
                                    currentCount = visibleChapterCount,
                                    totalCount = chapters.size,
                                    step = NOVEL_CHAPTERS_PAGE_SIZE,
                                )
                            },
                        ) {
                            Text(
                                text = "${stringResource(MR.strings.label_more)} " +
                                    "(${chapters.size - visibleChapterCount})",
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(MaterialTheme.padding.small)) }
        }
    }
}

internal fun initialVisibleChapterCount(totalCount: Int, pageSize: Int): Int {
    if (totalCount <= 0 || pageSize <= 0) return 0
    return minOf(totalCount, pageSize)
}

internal fun nextVisibleChapterCount(currentCount: Int, totalCount: Int, step: Int): Int {
    if (totalCount <= 0 || step <= 0) return 0
    if (currentCount <= 0) return minOf(totalCount, step)
    return minOf(totalCount, currentCount + step)
}

@Composable
private fun novelStatusText(status: Long): String? {
    return when (status) {
        SManga.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
        SManga.COMPLETED.toLong() -> stringResource(MR.strings.completed)
        SManga.LICENSED.toLong() -> stringResource(MR.strings.licensed)
        SManga.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
        SManga.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
        SManga.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
        else -> null
    }
}
