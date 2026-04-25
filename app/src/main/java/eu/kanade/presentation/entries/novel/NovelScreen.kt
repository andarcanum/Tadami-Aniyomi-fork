package eu.kanade.presentation.entries.novel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.entries.manga.components.ScanlatorBranchSelector
import eu.kanade.presentation.entries.novel.components.NovelChapterActionButton
import eu.kanade.presentation.entries.resolveEntryAutoJumpTargetIndex
import eu.kanade.presentation.entries.resolveTitleListFastScrollSpec
import eu.kanade.presentation.novel.buildNovelCoverImageRequest
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.coil.staticBlur
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterActionIconState
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterActionUiState
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterDisplayRow
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.tachiyomi.ui.entries.novel.resolveNovelChapterDisplayData
import eu.kanade.tachiyomi.ui.entries.novel.resolveNovelChapterRowIndex
import eu.kanade.tachiyomi.ui.entries.novel.resolveNovelVisibleChapterRows
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.VerticalFastScroller
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
    onEditCategoryClicked: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onPosterLongClicked: (() -> Unit)? = null,
    onToggleAllChaptersRead: () -> Unit,
    onShare: (() -> Unit)?,
    onWebView: (() -> Unit)?,
    onSourceSettings: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    trackingCount: Int,
    onOpenBatchDownloadDialog: (() -> Unit)?,
    onOpenTranslatedDownloadDialog: (() -> Unit)?,
    onOpenEpubExportDialog: (() -> Unit)?,
    onChapterClick: (Long) -> Unit,
    onChapterTranslateClick: (Long) -> Unit,
    onChapterTranslatedDownloadClick: (Long) -> Unit,
    onChapterTranslatedDownloadLongClick: (Long) -> Unit,
    onChapterTranslatedDownloadOpenFolder: (Long) -> Unit,
    onChapterReadToggle: (Long) -> Unit,
    onChapterBookmarkToggle: (Long) -> Unit,
    onChapterDownloadToggle: (Long) -> Unit,
    chapterSwipeStartAction: LibraryPreferences.NovelSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.NovelSwipeAction,
    onChapterSwipe: (Long, LibraryPreferences.NovelSwipeAction) -> Unit,
    onFilterButtonClicked: () -> Unit,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    chapterPageEnabled: Boolean,
    chapterPageCurrent: Int,
    chapterPageTotal: Int,
    chapterPageLoading: Boolean,
    onChapterPageChange: (Int) -> Unit,
    onChapterLongClick: (Long) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onMultiBookmarkClicked: (Boolean) -> Unit,
    onMultiMarkAsReadClicked: (Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (NovelChapter) -> Unit,
    onMultiDownloadClicked: () -> Unit,
    onMultiDeleteClicked: () -> Unit,
    onSaveScrollPosition: (Int, Int) -> Unit = { _, _ -> },
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme by uiPreferences.appTheme().collectAsState()
    val autoJumpToNextEnabled by uiPreferences.entryAutoJumpToNextNovel().collectAsState()
    val autoJumpToNextLabel = stringResource(
        if (autoJumpToNextEnabled) {
            AYMR.strings.action_disable_auto_jump_next_chapter
        } else {
            AYMR.strings.action_enable_auto_jump_next_chapter
        },
    )
    val onToggleAutoJumpToNext = {
        uiPreferences.entryAutoJumpToNextNovel().set(!autoJumpToNextEnabled)
    }

    // Route to Aurora implementation if Aurora theme is active
    if (theme.isAuroraStyle) {
        NovelScreenAuroraImpl(
            state = state,
            isFromSource = isFromSource,
            snackbarHostState = snackbarHostState,
            nextUpdate = state.novel.expectedNextUpdate,
            onBack = { if (state.selectedChapterIds.isNotEmpty()) onAllChapterSelected(false) else onBack() },
            onStartReading = onStartReading,
            isReading = isReading,
            onToggleFavorite = onToggleFavorite,
        onEditCategoryClicked = onEditCategoryClicked,
            onRefresh = onRefresh,
            onSearch = onSearch,
            onPosterLongClicked = onPosterLongClicked,
            onShare = onShare,
            onWebView = onWebView,
            onMigrateClicked = onMigrateClicked,
            onTrackingClicked = onTrackingClicked,
            trackingCount = trackingCount,
            onOpenBatchDownloadDialog = onOpenBatchDownloadDialog,
            onOpenTranslatedDownloadDialog = onOpenTranslatedDownloadDialog,
            onOpenEpubExportDialog = onOpenEpubExportDialog,
            onChapterClick = onChapterClick,
            onChapterTranslateClick = onChapterTranslateClick,
            onChapterTranslatedDownloadClick = onChapterTranslatedDownloadClick,
            onChapterTranslatedDownloadLongClick = onChapterTranslatedDownloadLongClick,
            onChapterTranslatedDownloadOpenFolder = onChapterTranslatedDownloadOpenFolder,
            onChapterLongClick = onChapterLongClick,
            onChapterReadToggle = onChapterReadToggle,
            onChapterBookmarkToggle = onChapterBookmarkToggle,
            onChapterDownloadToggle = onChapterDownloadToggle,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            onChapterSwipe = onChapterSwipe,
            onFilterButtonClicked = onFilterButtonClicked,
            scanlatorChapterCounts = scanlatorChapterCounts,
            selectedScanlator = selectedScanlator,
            onScanlatorSelected = onScanlatorSelected,
            chapterPageEnabled = chapterPageEnabled,
            chapterPageCurrent = chapterPageCurrent,
            chapterPageTotal = chapterPageTotal,
            chapterPageLoading = chapterPageLoading,
            onChapterPageChange = onChapterPageChange,
            onToggleAllSelection = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDownloadClicked = onMultiDownloadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            isAutoJumpToNextEnabled = autoJumpToNextEnabled,
            autoJumpToNextLabel = autoJumpToNextLabel,
            onToggleAutoJumpToNext = onToggleAutoJumpToNext,
        )
        return
    }

    // Standard implementation (non-Aurora)
    val isAurora = theme.isAuroraStyle
    val auroraColors = AuroraTheme.colors

    val chapters = state.processedChapters
    val groupedByChapter = false
    val chapterGroups = remember(chapters, groupedByChapter) {
        if (groupedByChapter) {
            resolveNovelChapterDisplayData(
                chapters = chapters,
                groupedByChapter = true,
                expandedGroupKeys = emptySet(),
            ).chapterGroups
        } else {
            emptyList()
        }
    }
    val allGroupKeys = remember(chapterGroups) {
        chapterGroups.mapTo(mutableSetOf()) { it.groupKey }
    }
    val initialExpandedGroupKeys = remember(chapters, selectedScanlator, state.targetChapterIndex) {
        if (!groupedByChapter) {
            emptySet()
        } else {
            chapters.getOrNull(state.targetChapterIndex)
                ?.chapterNumber
                ?.toBits()
                ?.let(::setOf)
                .orEmpty()
        }
    }
    var expandedGroupKeys by remember(chapters, selectedScanlator) {
        mutableStateOf(initialExpandedGroupKeys)
    }
    val chapterDisplayData = remember(chapters, selectedScanlator, expandedGroupKeys, groupedByChapter) {
        resolveNovelChapterDisplayData(
            chapters = chapters,
            groupedByChapter = groupedByChapter,
            expandedGroupKeys = expandedGroupKeys,
        )
    }
    val displayRows = chapterDisplayData.displayRows
    val totalChapterCount = if (state.chapterPageEnabled) {
        maxOf(state.chapters.size, state.chapterPageEstimatedTotal)
    } else if (groupedByChapter) {
        chapterGroups.size
    } else {
        chapters.size
    }
    val selectedIds = state.selectedChapterIds
    val selectedCount = selectedIds.size
    val isAnySelected = selectedCount > 0
    val selectedChapters = chapters.filter { it.id in selectedIds }
    val downloadedChapterIds = state.downloadedChapterIds
    val visibleTopLevelCount = if (groupedByChapter) chapterGroups.size else chapters.size
    var visibleChapterCount by remember(chapters, selectedScanlator) {
        mutableIntStateOf(
            initialVisibleChapterCount(
                totalCount = visibleTopLevelCount,
                pageSize = NOVEL_CHAPTERS_PAGE_SIZE,
            ),
        )
    }
    val visibleRows = remember(displayRows, visibleChapterCount, groupedByChapter) {
        resolveNovelVisibleChapterRows(
            rows = displayRows,
            visibleTopLevelCount = visibleChapterCount,
            groupedByChapter = groupedByChapter,
        )
    }
    val chapterListState = rememberLazyListState()

    // Save scroll position when it changes
    LaunchedEffect(chapterListState.firstVisibleItemIndex, chapterListState.firstVisibleItemScrollOffset) {
        onSaveScrollPosition(
            chapterListState.firstVisibleItemIndex,
            chapterListState.firstVisibleItemScrollOffset,
        )
    }

    // Restore saved scroll position or auto-scroll to target chapter
    var hasScrolledToTarget: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(state.scrollIndex, state.targetChapterIndex, expandedGroupKeys, groupedByChapter) {
        if (!hasScrolledToTarget) {
            hasScrolledToTarget = true
            val targetChapterId = chapters.getOrNull(state.targetChapterIndex)?.id
            val targetIndex = targetChapterId?.let {
                resolveNovelChapterRowIndex(displayRows, it)
            }?.takeIf { it >= 0 }
                ?.let { rowIndex ->
                    resolveEntryAutoJumpTargetIndex(
                        enabled = autoJumpToNextEnabled,
                        targetIndex = rowIndex,
                        restoredScrollIndex = state.scrollIndex,
                    )
                }
            if (targetIndex != null) {
                chapterListState.animateScrollToItem(targetIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            val isFirstItemVisible by remember {
                derivedStateOf { chapterListState.firstVisibleItemIndex == 0 }
            }
            val isFirstItemScrolled by remember {
                derivedStateOf { chapterListState.firstVisibleItemScrollOffset > 0 }
            }
            val titleAlpha by animateFloatAsState(
                if (!isFirstItemVisible) 1f else 0f,
                label = "Top Bar Title",
            )
            val backgroundAlpha by animateFloatAsState(
                if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                label = "Top Bar Background",
            )
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
                onToggleAutoJumpToNext = onToggleAutoJumpToNext,
                autoJumpToNextLabel = autoJumpToNextLabel,
                changeAnimeSkipIntro = null,
                actionModeCounter = selectedCount,
                onCancelActionMode = { onAllChapterSelected(false) },
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = onInvertSelection,
                titleAlphaProvider = { titleAlpha },
                backgroundAlphaProvider = { backgroundAlpha },
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
                onMarkPreviousAsViewedClicked = {
                    onMarkPreviousAsReadClicked(selectedChapters.first())
                }.takeIf { selectedChapters.size == 1 },
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
        val layoutDirection = LocalLayoutDirection.current
        val density = LocalDensity.current
        val baseTopPaddingPx = with(density) { paddingValues.calculateTopPadding().roundToPx() }
        val fastScrollBlockStartIndex = resolveNovelClassicFastScrollBlockStartIndex(
            showScanlatorSelector = state.showScanlatorSelector,
            chapterPageEnabled = chapterPageEnabled,
        )
        val fastScrollSpec by remember(baseTopPaddingPx, fastScrollBlockStartIndex) {
            derivedStateOf {
                resolveTitleListFastScrollSpec(
                    baseTopPaddingPx = baseTopPaddingPx,
                    firstVisibleItemIndex = chapterListState.firstVisibleItemIndex,
                    blockStartIndex = fastScrollBlockStartIndex,
                    blockStartOffsetPx = chapterListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == fastScrollBlockStartIndex }
                        ?.offset
                        ?.plus(with(density) { NOVEL_CLASSIC_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
                )
            }
        }
        VerticalFastScroller(
            listState = chapterListState,
            onThumbDragStarted = {
                if (groupedByChapter) {
                    expandedGroupKeys = allGroupKeys
                }
                visibleChapterCount = resolveNovelFastScrollVisibleChapterCount(
                    currentVisibleCount = visibleChapterCount,
                    loadedChapterCount = visibleTopLevelCount,
                )
            },
            thumbAllowed = { fastScrollSpec.thumbAllowed },
            topContentPadding = with(density) { fastScrollSpec.topPaddingPx.toDp() },
            bottomContentPadding = paddingValues.calculateBottomPadding(),
            endContentPadding = paddingValues.calculateEndPadding(layoutDirection),
        ) {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                state = chapterListState,
            ) {
                item {
                    val context = LocalContext.current
                    val backdropGradientColors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background,
                    )
                    val blurRadiusPx = with(LocalDensity.current) { 4.dp.roundToPx() }
                    val fallbackPainter = rememberThemeAwareCoverErrorPainter(
                        variant = AuroraCoverPlaceholderVariant.Wide,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
                    ) {
                        AsyncImage(
                            model = buildNovelCoverImageRequest(context, state.novel) {
                                crossfade(true)
                                staticBlur(blurRadiusPx, intensityFactor = 0.6f)
                            },
                            error = fallbackPainter,
                            fallback = fallbackPainter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = rememberAuroraPosterColorFilter(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(colors = backdropGradientColors),
                                    )
                                }
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
                                    data = buildNovelCoverImageRequest(context, state.novel) {
                                        crossfade(true)
                                    },
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
                                            totalChapterCount,
                                            totalChapterCount,
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
                            .padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
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
                                    if (state.novel.favorite) {
                                        MR.strings.remove_from_library
                                    } else {
                                        MR.strings.add_to_library
                                    },
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
                        if (onOpenTranslatedDownloadDialog != null) {
                            TextButton(onClick = onOpenTranslatedDownloadDialog) {
                                Icon(imageVector = Icons.Outlined.Translate, contentDescription = null)
                                Text(
                                    text = stringResource(AYMR.strings.novel_translated_download_short),
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                        if (onOpenEpubExportDialog != null) {
                            TextButton(onClick = onOpenEpubExportDialog) {
                                Icon(imageVector = Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                                Text(
                                    text = stringResource(AYMR.strings.novel_epub_short),
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }

                item(
                    key = NOVEL_CLASSIC_CHAPTERS_HEADER_KEY,
                    contentType = NOVEL_CLASSIC_CHAPTERS_HEADER_KEY,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
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
                            showAllOption = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = MaterialTheme.padding.medium,
                                    vertical = MaterialTheme.padding.small,
                                ),
                        )
                    }
                }

                if (chapterPageEnabled) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = MaterialTheme.padding.medium,
                                    vertical = MaterialTheme.padding.small,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                onClick = { onChapterPageChange(chapterPageCurrent - 1) },
                                enabled = !chapterPageLoading && chapterPageCurrent > 1,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChevronLeft,
                                    contentDescription = stringResource(MR.strings.spen_previous_page),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "$chapterPageCurrent / $chapterPageTotal",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                if (chapterPageLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onChapterPageChange(chapterPageCurrent + 1) },
                                enabled = !chapterPageLoading && chapterPageCurrent < chapterPageTotal,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = stringResource(MR.strings.spen_next_page),
                                )
                            }
                        }
                    }
                }

                items(
                    items = visibleRows,
                    key = { row ->
                        when (row) {
                            is NovelChapterDisplayRow.BranchChapter -> "branch-${row.chapter.id}"
                            is NovelChapterDisplayRow.ChapterGroup -> "group-${row.groupKey}"
                            is NovelChapterDisplayRow.ChapterVariant -> "variant-${row.chapter.id}"
                        }
                    },
                ) { row ->
                    when (row) {
                        is NovelChapterDisplayRow.BranchChapter -> {
                            val chapter = row.chapter
                            NovelClassicChapterRow(
                                chapter = chapter,
                                displayNumber = row.displayNumber,
                                selected = chapter.id in selectedIds,
                                chapterActionState = state.chapterActionStates[chapter.id],
                                downloaded = chapter.id in state.downloadedChapterIds,
                                downloading = chapter.id in state.downloadingChapterIds,
                                selectionMode = isAnySelected,
                                displayMode = state.novel.displayMode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium, vertical = 4.dp),
                                onClick = { onChapterClick(chapter.id) },
                                onLongClick = { onChapterLongClick(chapter.id) },
                                onTranslateClick = { onChapterTranslateClick(chapter.id) },
                                onTranslatedDownloadClick = { onChapterTranslatedDownloadClick(chapter.id) },
                                onTranslatedDownloadLongClick = { onChapterTranslatedDownloadLongClick(chapter.id) },
                                onTranslatedDownloadOpenFolder = { onChapterTranslatedDownloadOpenFolder(chapter.id) },
                                onToggleDownload = { onChapterDownloadToggle(chapter.id) },
                                onToggleBookmark = { onChapterBookmarkToggle(chapter.id) },
                                onToggleRead = { onChapterReadToggle(chapter.id) },
                                chapterSwipeStartAction = chapterSwipeStartAction,
                                chapterSwipeEndAction = chapterSwipeEndAction,
                                onChapterSwipe = { action -> onChapterSwipe(chapter.id, action) },
                            )
                        }
                        is NovelChapterDisplayRow.ChapterGroup -> {
                            val primaryChapter = row.chapters.first()
                            val isExpanded = row.groupKey in expandedGroupKeys
                            NovelClassicChapterGroup(
                                title = stringResource(
                                    MR.strings.display_mode_chapter,
                                    formatChapterNumber(row.displayNumber.toDouble()),
                                ),
                                count = row.chapters.size,
                                expanded = isExpanded,
                                singleItemGroup = row.chapters.size == 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium, vertical = 4.dp),
                                onClick = {
                                    if (row.chapters.size == 1) {
                                        onChapterClick(primaryChapter.id)
                                    } else {
                                        expandedGroupKeys = if (isExpanded) {
                                            expandedGroupKeys - row.groupKey
                                        } else {
                                            expandedGroupKeys + row.groupKey
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (row.chapters.size == 1) {
                                        onChapterLongClick(primaryChapter.id)
                                    }
                                },
                            )
                        }
                        is NovelChapterDisplayRow.ChapterVariant -> {
                            val chapter = row.chapter
                            val chapterTitle = chapter.scanlator
                                ?.takeIf { it.isNotBlank() }
                                ?.let { scanlator ->
                                    val baseName = chapter.name.ifBlank {
                                        stringResource(
                                            MR.strings.display_mode_chapter,
                                            formatChapterNumber(row.displayNumber.toDouble()),
                                        )
                                    }
                                    "$scanlator · $baseName"
                                }
                            NovelClassicChapterRow(
                                chapter = chapter,
                                displayNumber = row.displayNumber,
                                selected = chapter.id in selectedIds,
                                chapterActionState = state.chapterActionStates[chapter.id],
                                downloaded = chapter.id in state.downloadedChapterIds,
                                downloading = chapter.id in state.downloadingChapterIds,
                                selectionMode = isAnySelected,
                                displayMode = state.novel.displayMode,
                                titleOverride = chapterTitle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = MaterialTheme.padding.large,
                                        end = MaterialTheme.padding.medium,
                                        top = 2.dp,
                                        bottom = 4.dp,
                                    ),
                                onClick = { onChapterClick(chapter.id) },
                                onLongClick = { onChapterLongClick(chapter.id) },
                                onTranslateClick = { onChapterTranslateClick(chapter.id) },
                                onTranslatedDownloadClick = { onChapterTranslatedDownloadClick(chapter.id) },
                                onTranslatedDownloadLongClick = { onChapterTranslatedDownloadLongClick(chapter.id) },
                                onTranslatedDownloadOpenFolder = { onChapterTranslatedDownloadOpenFolder(chapter.id) },
                                onToggleDownload = { onChapterDownloadToggle(chapter.id) },
                                onToggleBookmark = { onChapterBookmarkToggle(chapter.id) },
                                onToggleRead = { onChapterReadToggle(chapter.id) },
                                chapterSwipeStartAction = chapterSwipeStartAction,
                                chapterSwipeEndAction = chapterSwipeEndAction,
                                onChapterSwipe = { action -> onChapterSwipe(chapter.id, action) },
                            )
                        }
                    }
                }
                /*
                items(
                    items = visibleRows,
                    key = { row ->
                        when (row) {
                            is NovelChapterDisplayRow.BranchChapter -> "branch-${row.chapter.id}"
                            is NovelChapterDisplayRow.ChapterGroup -> "group-${row.groupKey}"
                            is NovelChapterDisplayRow.ChapterVariant -> "variant-${row.chapter.id}"
                        }
                    },
                ) { row ->
                    when (row) {
                        is NovelChapterDisplayRow.BranchChapter -> {
                            val chapter = row.chapter
                            val selected = chapter.id in selectedIds
                            val downloaded = chapter.id in state.downloadedChapterIds
                            val downloading = chapter.id in state.downloadingChapterIds
                            val chapterCard: @Composable () -> Unit = {
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
                                                        formatChapterNumber(row.displayNumber.toDouble()),
                                                    )
                                                }
                                                else -> {
                                                    chapter.name.ifBlank {
                                                        stringResource(
                                                            MR.strings.display_mode_chapter,
                                                            formatChapterNumber(row.displayNumber.toDouble()),
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
                                            novelChapterDateText(
                                                chapter = chapter,
                                                parsedDateText = if (chapter.dateUpload > 0L) {
                                                    relativeDateTimeText(chapter.dateUpload)
                                                } else {
                                                    null
                                                },
                                            )?.let { dateText ->
                                                Text(
                                                    text = dateText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (!isAnySelected) {
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

                            if (!isAnySelected) {
                                val startSwipeAction = novelSwipeAction(
                                    action = chapterSwipeStartAction,
                                    read = chapter.read,
                                    bookmark = chapter.bookmark,
                                    downloaded = downloaded,
                                    downloading = downloading,
                                    background = MaterialTheme.colorScheme.primaryContainer,
                                    onSwipe = { onChapterSwipe(chapter.id, chapterSwipeStartAction) },
                                )
                                val endSwipeAction = novelSwipeAction(
                                    action = chapterSwipeEndAction,
                                    read = chapter.read,
                                    bookmark = chapter.bookmark,
                                    downloaded = downloaded,
                                    downloading = downloading,
                                    background = MaterialTheme.colorScheme.primaryContainer,
                                    onSwipe = { onChapterSwipe(chapter.id, chapterSwipeEndAction) },
                                )
                                SwipeableActionsBox(
                                    modifier = Modifier.clipToBounds(),
                                    startActions = listOfNotNull(startSwipeAction),
                                    endActions = listOfNotNull(endSwipeAction),
                                    swipeThreshold = novelSwipeActionThreshold,
                                    backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ) {
                                    chapterCard()
                                }
                            } else {
                                chapterCard()
                            }
                        }
                        is NovelChapterDisplayRow.ChapterGroup -> {
                            val primaryChapter = row.chapters.first()
                            val isExpanded = row.groupKey in expandedGroupKeys
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium, vertical = 4.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .combinedClickable(
                                        onClick = {
                                            if (row.chapters.size == 1) {
                                                onChapterClick(primaryChapter.id)
                                            } else {
                                                expandedGroupKeys = if (isExpanded) {
                                                    expandedGroupKeys - row.groupKey
                                                } else {
                                                    expandedGroupKeys + row.groupKey
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (row.chapters.size == 1) {
                                                onChapterLongClick(primaryChapter.id)
                                            }
                                        },
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = MaterialTheme.padding.medium, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val groupTitle = stringResource(
                                            MR.strings.display_mode_chapter,
                                            formatChapterNumber(row.displayNumber.toDouble()),
                                        )
                                        Text(
                                            text = if (row.chapters.size > 1) {
                                                "$groupTitle (${row.chapters.size})"
                                            } else {
                                                groupTitle
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) {
                                            Icons.Outlined.ArrowForward
                                        } else {
                                            Icons.AutoMirrored.Outlined.ArrowForward
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        is NovelChapterDisplayRow.ChapterVariant -> {
                            val chapter = row.chapter
                            val selected = chapter.id in selectedIds
                            val downloaded = chapter.id in state.downloadedChapterIds
                            val downloading = chapter.id in state.downloadingChapterIds
                            val variantChapter = chapter.copy(
                                name = chapter.scanlator
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { scanlator ->
                                        val baseName = chapter.name.ifBlank {
                                            stringResource(
                                                MR.strings.display_mode_chapter,
                                                formatChapterNumber(chapter.chapterNumber),
                                            )
                                        }
                                        "$scanlator · $baseName"
                                    }
                                    ?: chapter.name,
                            )
                            val chapterCard: @Composable () -> Unit = {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = MaterialTheme.padding.large, end = MaterialTheme.padding.medium, top = 2.dp, bottom = 4.dp)
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
                                                text = when (state.novel.displayMode) {
                                                    DomainNovel.CHAPTER_DISPLAY_NUMBER -> {
                                                        stringResource(
                                                            MR.strings.display_mode_chapter,
                                                            formatChapterNumber(chapter.chapterNumber),
                                                        )
                                                    }
                                                    else -> {
                                                        variantChapter.name.ifBlank {
                                                            stringResource(
                                                                MR.strings.display_mode_chapter,
                                                                formatChapterNumber(chapter.chapterNumber),
                                                            )
                                                        }
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                        color = if (chapter.read) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    novelChapterDateText(
                                        chapter = chapter,
                                        parsedDateText = if (chapter.dateUpload > 0L) {
                                            relativeDateTimeText(chapter.dateUpload)
                                        } else {
                                            null
                                        },
                                    )?.let { dateText ->
                                        Text(
                                            text = dateText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (!isAnySelected) {
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

                            if (!isAnySelected) {
                                val startSwipeAction = novelSwipeAction(
                                    action = chapterSwipeStartAction,
                                    read = chapter.read,
                                    bookmark = chapter.bookmark,
                                    downloaded = downloaded,
                                    downloading = downloading,
                                    background = MaterialTheme.colorScheme.primaryContainer,
                                    onSwipe = { onChapterSwipe(chapter.id, chapterSwipeStartAction) },
                                )
                                val endSwipeAction = novelSwipeAction(
                                    action = chapterSwipeEndAction,
                                    read = chapter.read,
                                    bookmark = chapter.bookmark,
                                    downloaded = downloaded,
                                    downloading = downloading,
                                    background = MaterialTheme.colorScheme.primaryContainer,
                                    onSwipe = { onChapterSwipe(chapter.id, chapterSwipeEndAction) },
                                )
                                SwipeableActionsBox(
                                    modifier = Modifier.clipToBounds(),
                                    startActions = listOfNotNull(startSwipeAction),
                                    endActions = listOfNotNull(endSwipeAction),
                                    swipeThreshold = novelSwipeActionThreshold,
                                    backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ) {
                                    chapterCard()
                                }
                            } else {
                                chapterCard()
                            }
                        }
                    }
                }
                 */
                if (visibleChapterCount < visibleTopLevelCount) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = MaterialTheme.padding.medium,
                                    vertical = MaterialTheme.padding.small,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Button(
                                onClick = {
                                    visibleChapterCount = nextVisibleChapterCount(
                                        currentCount = visibleChapterCount,
                                        totalCount = visibleTopLevelCount,
                                        step = NOVEL_CHAPTERS_PAGE_SIZE,
                                    )
                                },
                            ) {
                                Text(
                                    text = "${stringResource(MR.strings.label_more)} " +
                                        "(${visibleTopLevelCount - visibleChapterCount})",
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(MaterialTheme.padding.small)) }
            }
        }
    }
}

internal fun initialVisibleChapterCount(totalCount: Int, pageSize: Int): Int {
    if (totalCount <= 0 || pageSize <= 0) return 0
    return minOf(totalCount, pageSize)
}

@Composable
private fun NovelClassicChapterRow(
    chapter: tachiyomi.domain.items.novelchapter.model.NovelChapter,
    displayNumber: Int,
    selected: Boolean,
    chapterActionState: NovelChapterActionUiState?,
    downloaded: Boolean,
    downloading: Boolean,
    selectionMode: Boolean,
    displayMode: Long,
    titleOverride: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onTranslatedDownloadClick: () -> Unit,
    onTranslatedDownloadLongClick: () -> Unit,
    onTranslatedDownloadOpenFolder: () -> Unit,
    onToggleDownload: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleRead: () -> Unit,
    chapterSwipeStartAction: LibraryPreferences.NovelSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.NovelSwipeAction,
    onChapterSwipe: (LibraryPreferences.NovelSwipeAction) -> Unit,
) {
    val chapterCard: @Composable () -> Unit = {
        Card(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
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
                    val chapterTitle = titleOverride ?: when (displayMode) {
                        DomainNovel.CHAPTER_DISPLAY_NUMBER -> {
                            stringResource(
                                MR.strings.display_mode_chapter,
                                formatChapterNumber(displayNumber.toDouble()),
                            )
                        }
                        else -> {
                            chapter.name.ifBlank {
                                stringResource(
                                    MR.strings.display_mode_chapter,
                                    formatChapterNumber(displayNumber.toDouble()),
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
                    novelChapterDateText(
                        chapter = chapter,
                        parsedDateText = if (chapter.dateUpload > 0L) {
                            relativeDateTimeText(chapter.dateUpload)
                        } else {
                            null
                        },
                    )?.let { dateText ->
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!selectionMode) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (chapterActionState?.showGeminiRow == true) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val translateState = chapterActionState.translateState
                                NovelChapterActionButton(
                                    icon = Icons.Outlined.Translate,
                                    iconTint = when (translateState) {
                                        NovelChapterActionIconState.Active -> MaterialTheme.colorScheme.primary
                                        NovelChapterActionIconState.InProgress -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    onClick = onTranslateClick,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                    showProgress = translateState == NovelChapterActionIconState.InProgress,
                                    progressColor = MaterialTheme.colorScheme.primary,
                                    size = 36.dp,
                                    iconSize = 20.dp,
                                    contentDescription = stringResource(
                                        AYMR.strings.novel_reader_selected_text_translation_action_translate,
                                    ),
                                )
                                val translatedDownloadState = chapterActionState.downloadTranslatedState
                                NovelChapterActionButton(
                                    icon = Icons.Outlined.Download,
                                    iconTint = when (translatedDownloadState) {
                                        NovelChapterActionIconState.Active -> MaterialTheme.colorScheme.primary
                                        NovelChapterActionIconState.InProgress -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    onClick = if (translatedDownloadState == NovelChapterActionIconState.Active) {
                                        onTranslatedDownloadOpenFolder
                                    } else {
                                        onTranslatedDownloadClick
                                    },
                                    onLongClick = onTranslatedDownloadLongClick,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                    showProgress = translatedDownloadState == NovelChapterActionIconState.InProgress,
                                    progressColor = MaterialTheme.colorScheme.primary,
                                    size = 36.dp,
                                    iconSize = 20.dp,
                                    contentDescription = stringResource(MR.strings.manga_download),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            NovelChapterActionButton(
                                icon = when {
                                    downloading -> Icons.Outlined.FileDownloadOff
                                    downloaded -> Icons.Outlined.Download
                                    else -> Icons.Outlined.Download
                                },
                                iconTint = when {
                                    downloading -> MaterialTheme.colorScheme.tertiary
                                    downloaded -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                onClick = onToggleDownload,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                showProgress = downloading,
                                progressColor = MaterialTheme.colorScheme.primary,
                                size = 32.dp,
                                iconSize = 18.dp,
                            )
                            NovelChapterActionButton(
                                icon = Icons.Outlined.Bookmark,
                                iconTint = if (chapter.bookmark) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                onClick = onToggleBookmark,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                size = 32.dp,
                                iconSize = 18.dp,
                            )
                            NovelChapterActionButton(
                                icon = Icons.Outlined.CheckCircle,
                                iconTint = if (chapter.read) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                onClick = onToggleRead,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                size = 32.dp,
                                iconSize = 18.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (!selectionMode) {
        val startSwipeAction = novelSwipeAction(
            action = chapterSwipeStartAction,
            read = chapter.read,
            bookmark = chapter.bookmark,
            downloaded = downloaded,
            downloading = downloading,
            background = MaterialTheme.colorScheme.primaryContainer,
            onSwipe = { onChapterSwipe(chapterSwipeStartAction) },
        )
        val endSwipeAction = novelSwipeAction(
            action = chapterSwipeEndAction,
            read = chapter.read,
            bookmark = chapter.bookmark,
            downloaded = downloaded,
            downloading = downloading,
            background = MaterialTheme.colorScheme.primaryContainer,
            onSwipe = { onChapterSwipe(chapterSwipeEndAction) },
        )
        SwipeableActionsBox(
            modifier = Modifier.clipToBounds(),
            startActions = listOfNotNull(startSwipeAction),
            endActions = listOfNotNull(endSwipeAction),
            swipeThreshold = novelSwipeActionThreshold,
            backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            chapterCard()
        }
    } else {
        chapterCard()
    }
}

@Composable
private fun NovelClassicChapterGroup(
    title: String,
    count: Int,
    expanded: Boolean,
    singleItemGroup: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count > 1) {
                        "$title ($count)"
                    } else {
                        title
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.ChevronLeft
                } else {
                    Icons.Outlined.ChevronRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun nextVisibleChapterCount(currentCount: Int, totalCount: Int, step: Int): Int {
    if (totalCount <= 0 || step <= 0) return 0
    if (currentCount <= 0) return minOf(totalCount, step)
    return minOf(totalCount, currentCount + step)
}

internal fun resolveNovelFastScrollVisibleChapterCount(
    currentVisibleCount: Int,
    loadedChapterCount: Int,
): Int {
    if (loadedChapterCount <= 0) return 0
    return maxOf(currentVisibleCount, loadedChapterCount)
}

private const val NOVEL_CLASSIC_CHAPTERS_HEADER_KEY = "novel-classic-chapters-header"
private val NOVEL_CLASSIC_FAST_SCROLL_ITEM_TOP_INSET = 6.dp

internal fun resolveNovelClassicFastScrollBlockStartIndex(
    showScanlatorSelector: Boolean,
    chapterPageEnabled: Boolean,
): Int {
    return 3 + listOf(showScanlatorSelector, chapterPageEnabled).count { it }
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
