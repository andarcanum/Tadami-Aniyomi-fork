package eu.kanade.presentation.entries.novel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.entries.components.AuroraEntryHoldToRefresh
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionFab
import eu.kanade.presentation.entries.components.normalizeAuroraGlobalSearchQuery
import eu.kanade.presentation.entries.manga.components.ScanlatorBranchSelector
import eu.kanade.presentation.entries.novel.components.aurora.ChaptersHeader
import eu.kanade.presentation.entries.novel.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.novel.components.aurora.NovelActionCard
import eu.kanade.presentation.entries.novel.components.aurora.NovelChapterCardCompact
import eu.kanade.presentation.entries.novel.components.aurora.NovelHeroContent
import eu.kanade.presentation.entries.novel.components.aurora.NovelInfoCard
import eu.kanade.presentation.entries.resolveEntryAutoJumpTargetIndex
import eu.kanade.presentation.entries.resolveTitleFastScrollOverlayRevealState
import eu.kanade.presentation.entries.resolveTitleListFastScrollSpec
import eu.kanade.presentation.entries.shouldShowTitleFastScrollFloatingActionButton
import eu.kanade.presentation.entries.shouldShowTitleFastScrollOverlayChrome
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant

@Composable
fun NovelScreenAuroraImpl(
    state: NovelScreenModel.State.Success,
    isFromSource: Boolean,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    onBack: () -> Unit,
    onStartReading: (() -> Unit)?,
    isReading: Boolean,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onShare: (() -> Unit)?,
    onWebView: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    trackingCount: Int,
    onOpenBatchDownloadDialog: (() -> Unit)?,
    onOpenTranslatedDownloadDialog: (() -> Unit)?,
    onOpenEpubExportDialog: (() -> Unit)?,
    onChapterClick: (Long) -> Unit,
    onChapterLongClick: (Long) -> Unit,
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
    onToggleAllSelection: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onMultiBookmarkClicked: (Boolean) -> Unit,
    onMultiMarkAsReadClicked: (Boolean) -> Unit,
    isAutoJumpToNextEnabled: Boolean,
    autoJumpToNextLabel: String,
    onToggleAutoJumpToNext: () -> Unit,
) {
    val novel = state.novel
    val globalSearchQuery = remember(novel.title) { normalizeAuroraGlobalSearchQuery(novel.title) }
    val chapters = state.processedChapters
    val totalChapterCount = if (state.chapterPageEnabled) {
        maxOf(state.chapters.size, state.chapterPageEstimatedTotal)
    } else {
        chapters.size
    }
    val colors = AuroraTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.entryMaxWidthDp
    val useTwoPaneLayout = shouldUseNovelAuroraTwoPane(auroraAdaptiveSpec.deviceClass)

    val lazyListState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }

    var chaptersExpanded by remember { mutableStateOf(false) }
    var isReverseScrollingOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(chaptersExpanded) {
        if (chaptersExpanded) {
            isReverseScrollingOverlay = false
        }
    }
    LaunchedEffect(Unit) {
        var prevIndex = lazyListState.firstVisibleItemIndex
        var prevOffset = lazyListState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                lazyListState.isScrollInProgress,
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset,
            )
        }.collect { (isScrolling, index, offset) ->
            val movedForward = index > prevIndex || (index == prevIndex && offset > prevOffset)
            isReverseScrollingOverlay = resolveTitleFastScrollOverlayRevealState(
                currentRevealState = isReverseScrollingOverlay,
                isExpandedList = chaptersExpanded,
                isScrolling = isScrolling,
                movedForward = movedForward,
            )
            prevIndex = index
            prevOffset = offset
        }
    }
    val chaptersToShow = remember(chapters, chaptersExpanded) {
        chapters.take(resolveNovelAuroraVisibleChapterCount(chaptersExpanded, chapters.size))
    }

    // Auto-scroll to target chapter on initial load
    var hasScrolledToTarget: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(state.targetChapterIndex, chaptersExpanded, chapters.size) {
        if (shouldAutoJumpToNovelAuroraTarget(hasScrolledToTarget, chaptersExpanded, chapters.size)) {
            hasScrolledToTarget = true
            val targetIndex = resolveEntryAutoJumpTargetIndex(
                enabled = isAutoJumpToNextEnabled,
                targetIndex = state.targetChapterIndex,
                restoredScrollIndex = state.scrollIndex,
            )
            if (targetIndex != null) {
                lazyListState.animateScrollToItem(targetIndex)
            }
        }
    }

    val selectedIds = state.selectedChapterIds
    val isSelectionMode = selectedIds.isNotEmpty()
    val selectedChapters = chapters.filter { it.id in selectedIds }

    var descriptionExpanded by remember { mutableStateOf(false) }
    var genresExpanded by remember { mutableStateOf(false) }
    var isThumbFastScrolling by remember { mutableStateOf(false) }
    val showNovelOverlayChrome by remember {
        derivedStateOf {
            shouldShowTitleFastScrollOverlayChrome(
                isThumbDragged = isThumbFastScrolling,
                isExpandedList = chaptersExpanded,
                isReverseScrolling = isReverseScrollingOverlay,
            )
        }
    }

    LaunchedEffect(isSelectionMode, chaptersExpanded, chapters.size) {
        if (isSelectionMode &&
            shouldAutoExpandAuroraNovelChaptersList(
                chaptersExpanded = chaptersExpanded,
                totalChapters = chapters.size,
            )
        ) {
            chaptersExpanded = true
        }
    }

    if (useTwoPaneLayout) {
        val topContentPadding = 96.dp

        AuroraEntryHoldToRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = !isSelectionMode,
            modifier = Modifier.fillMaxSize(),
            indicatorPadding = WindowInsets.statusBars.asPaddingValues(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FullscreenPosterBackground(
                    novel = novel,
                    scrollOffset = 0,
                    firstVisibleItemIndex = 0,
                )

                TwoPanelBox(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f),
                    startContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp, end = 6.dp, top = topContentPadding, bottom = 20.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .auroraCenteredMaxWidth(420),
                            ) {
                                NovelHeroContent(
                                    novel = novel,
                                    chapterCount = totalChapterCount,
                                    onContinueReading = onStartReading,
                                    isReading = isReading,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                NovelInfoCard(
                                    novel = novel,
                                    chapterCount = totalChapterCount,
                                    nextUpdate = nextUpdate,
                                    onTagSearch = { tag -> onSearch(tag, true) },
                                    descriptionExpanded = descriptionExpanded,
                                    genresExpanded = genresExpanded,
                                    onToggleDescription = { descriptionExpanded = !descriptionExpanded },
                                    onToggleGenres = { genresExpanded = !genresExpanded },
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                NovelActionCard(
                                    novel = novel,
                                    trackingCount = trackingCount,
                                    onAddToLibraryClicked = onToggleFavorite,
                                    onTrackingClicked = onTrackingClicked,
                                    onBatchDownloadClicked = onOpenBatchDownloadDialog,
                                    onTranslatedDownloadClicked = onOpenTranslatedDownloadDialog,
                                    onExportEpubClicked = onOpenEpubExportDialog,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    },
                    endContent = {
                        val chapterListState = rememberLazyListState()

                        // Auto-scroll to target chapter in two-pane layout
                        var hasScrolledToTargetInPane: Boolean by remember { mutableStateOf(false) }
                        LaunchedEffect(state.targetChapterIndex, chaptersExpanded, chapters.size) {
                            if (shouldAutoJumpToNovelAuroraTarget(
                                    hasScrolledToTarget = hasScrolledToTargetInPane,
                                    chaptersExpanded = chaptersExpanded,
                                    totalChapters = chapters.size,
                                )
                            ) {
                                hasScrolledToTargetInPane = true
                                val targetIndex = resolveEntryAutoJumpTargetIndex(
                                    enabled = isAutoJumpToNextEnabled,
                                    targetIndex = state.targetChapterIndex,
                                    restoredScrollIndex = state.scrollIndex,
                                )
                                if (targetIndex != null) {
                                    chapterListState.animateScrollToItem(targetIndex)
                                }
                            }
                        }

                        val paneDensity = LocalDensity.current
                        val paneTopPaddingPx = with(paneDensity) { topContentPadding.roundToPx() }
                        val paneFastScrollBlockStartIndex = resolveNovelAuroraFastScrollBlockStartIndex(
                            useTwoPaneLayout = true,
                            isSelectionMode = isSelectionMode,
                            chapterPageEnabled = chapterPageEnabled,
                            showScanlatorSelector = state.showScanlatorSelector,
                        )
                        val paneFastScrollSpec by remember(paneTopPaddingPx, paneFastScrollBlockStartIndex) {
                            derivedStateOf {
                                resolveTitleListFastScrollSpec(
                                    baseTopPaddingPx = paneTopPaddingPx,
                                    firstVisibleItemIndex = chapterListState.firstVisibleItemIndex,
                                    blockStartIndex = paneFastScrollBlockStartIndex,
                                    blockStartOffsetPx = chapterListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == paneFastScrollBlockStartIndex }
                                        ?.offset
                                        ?.plus(
                                            with(paneDensity) {
                                                NOVEL_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx()
                                            },
                                        ),
                                )
                            }
                        }
                        VerticalFastScroller(
                            listState = chapterListState,
                            onThumbDragStarted = {
                                if (shouldAutoExpandAuroraNovelChaptersListForFastScroll(
                                        chaptersExpanded,
                                        chapters.size,
                                    )
                                ) {
                                    chaptersExpanded = true
                                }
                            },
                            onThumbDragStateChanged = { isThumbFastScrolling = it },
                            thumbAllowed = { paneFastScrollSpec.thumbAllowed },
                            topContentPadding = with(paneDensity) { paneFastScrollSpec.topPaddingPx.toDp() },
                            endContentPadding = 12.dp,
                            modifier = Modifier.zIndex(1f),
                        ) {
                            LazyColumn(
                                state = chapterListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 6.dp, end = 12.dp),
                                contentPadding = PaddingValues(
                                    top = topContentPadding,
                                    bottom = 112.dp,
                                ),
                            ) {
                                if (isSelectionMode) {
                                    item {
                                        SelectionBar(
                                            selectedCount = selectedIds.size,
                                            canMarkRead = selectedChapters.any { !it.read },
                                            canMarkUnread = selectedChapters.any {
                                                it.read || it.lastPageRead > 0L
                                            },
                                            canBookmark = selectedChapters.any { !it.bookmark },
                                            canUnbookmark = selectedChapters.any { it.bookmark },
                                            onClear = { onToggleAllSelection(false) },
                                            onSelectAll = { onToggleAllSelection(true) },
                                            onInvert = onInvertSelection,
                                            onMarkRead = { onMultiMarkAsReadClicked(true) },
                                            onMarkUnread = { onMultiMarkAsReadClicked(false) },
                                            onBookmark = { onMultiBookmarkClicked(true) },
                                            onUnbookmark = { onMultiBookmarkClicked(false) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                item {
                                    ChaptersHeader(chapterCount = totalChapterCount)
                                }

                                if (chapterPageEnabled) {
                                    item {
                                        AuroraChapterPageControls(
                                            chapterPageCurrent = chapterPageCurrent,
                                            chapterPageTotal = chapterPageTotal,
                                            chapterPageLoading = chapterPageLoading,
                                            onChapterPageChange = onChapterPageChange,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                        )
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
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                }

                                if (chapters.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(MR.strings.no_chapters_error),
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 14.sp,
                                            )
                                        }
                                    }
                                } else {
                                    items(
                                        items = chaptersToShow,
                                        key = { it.id },
                                        contentType = { "chapter" },
                                    ) { chapter ->
                                        NovelChapterCardCompact(
                                            novel = novel,
                                            chapter = chapter,
                                            selected = chapter.id in selectedIds,
                                            selectionMode = isSelectionMode,
                                            onClick = { onChapterClick(chapter.id) },
                                            onLongClick = { onChapterLongClick(chapter.id) },
                                            onToggleBookmark = { onChapterBookmarkToggle(chapter.id) },
                                            onToggleRead = { onChapterReadToggle(chapter.id) },
                                            onToggleDownload = { onChapterDownloadToggle(chapter.id) },
                                            chapterSwipeStartAction = chapterSwipeStartAction,
                                            chapterSwipeEndAction = chapterSwipeEndAction,
                                            onChapterSwipe = { action -> onChapterSwipe(chapter.id, action) },
                                            downloaded = chapter.id in state.downloadedChapterIds,
                                            downloading = chapter.id in state.downloadingChapterIds,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }

                                    if (chapters.size > NOVEL_AURORA_COLLAPSED_PREVIEW_COUNT) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AuroraChapterListToggleButton(
                                                    text = if (chaptersExpanded) {
                                                        stringResource(AYMR.strings.action_show_less)
                                                    } else {
                                                        stringResource(
                                                            AYMR.strings.action_show_all_chapters,
                                                            chapters.size,
                                                        )
                                                    },
                                                    onClick = { chaptersExpanded = !chaptersExpanded },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                )

                val overlayChromeAlphaTwoPane by animateFloatAsState(
                    targetValue = if (showNovelOverlayChrome) 1f else 0f,
                    label = "overlayChromeAlpha",
                )
                val overlayChromeOffsetYTwoPane by animateFloatAsState(
                    targetValue = if (showNovelOverlayChrome) 0f else -1f,
                    label = "overlayChromeOffsetY",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = overlayChromeAlphaTwoPane
                            translationY = overlayChromeOffsetYTwoPane * size.height
                        }
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AuroraActionButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AuroraActionButton(
                        onClick = onFilterButtonClicked,
                        icon = Icons.Default.FilterList,
                        contentDescription = null,
                        iconTint = if (state.filterActive) colors.accent else colors.accent.copy(alpha = 0.7f),
                    )
                    if (onWebView != null) {
                        AuroraActionButton(
                            onClick = onWebView,
                            icon = Icons.Filled.Public,
                            contentDescription = null,
                        )
                    }

                    var showMenu by remember { mutableStateOf(false) }
                    val hasMenuActions = true
                    if (hasMenuActions) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            AuroraActionButton(
                                onClick = { showMenu = !showMenu },
                                icon = Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                            AuroraEntryDropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                AuroraEntryDropdownMenuItem(
                                    text = autoJumpToNextLabel,
                                    onClick = {
                                        onToggleAutoJumpToNext()
                                        showMenu = false
                                    },
                                )
                                if (isFromSource) {
                                    AuroraEntryDropdownMenuItem(
                                        text = stringResource(MR.strings.action_webview_refresh),
                                        onClick = {
                                            onRefresh()
                                            showMenu = false
                                        },
                                    )
                                    if (globalSearchQuery != null) {
                                        AuroraEntryDropdownMenuItem(
                                            text = stringResource(MR.strings.action_global_search),
                                            onClick = {
                                                onSearch(globalSearchQuery, true)
                                                showMenu = false
                                            },
                                        )
                                    }
                                    if (onShare != null) {
                                        AuroraEntryDropdownMenuItem(
                                            text = stringResource(MR.strings.action_share),
                                            onClick = {
                                                onShare()
                                                showMenu = false
                                            },
                                        )
                                    }
                                } else {
                                    AuroraEntryDropdownMenuItem(
                                        text = stringResource(MR.strings.action_webview_refresh),
                                        onClick = {
                                            onRefresh()
                                            showMenu = false
                                        },
                                    )
                                    if (globalSearchQuery != null) {
                                        AuroraEntryDropdownMenuItem(
                                            text = stringResource(MR.strings.action_global_search),
                                            onClick = {
                                                onSearch(globalSearchQuery, true)
                                                showMenu = false
                                            },
                                        )
                                    }
                                    if (onShare != null) {
                                        AuroraEntryDropdownMenuItem(
                                            text = stringResource(MR.strings.action_share),
                                            onClick = {
                                                onShare()
                                                showMenu = false
                                            },
                                        )
                                    }
                                    if (onMigrateClicked != null) {
                                        AuroraEntryDropdownMenuItem(
                                            text = stringResource(MR.strings.action_migrate),
                                            onClick = {
                                                onMigrateClicked()
                                                showMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    snackbar = { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = colors.surface.copy(alpha = 0.96f),
                            contentColor = colors.textPrimary,
                            actionColor = colors.accent,
                            dismissActionContentColor = colors.textSecondary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
        }
        return
    }

    AuroraEntryHoldToRefresh(
        refreshing = state.isRefreshingData,
        onRefresh = onRefresh,
        enabled = !isSelectionMode,
        modifier = Modifier.fillMaxSize(),
        indicatorPadding = WindowInsets.statusBars.asPaddingValues(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FullscreenPosterBackground(
                novel = novel,
                scrollOffset = scrollOffset,
                firstVisibleItemIndex = firstVisibleItemIndex,
            )

            val density = LocalDensity.current
            val fastScrollBaseTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp
            val fastScrollBaseTopPaddingPx = with(density) { fastScrollBaseTopPadding.roundToPx() }
            val fastScrollBlockStartIndex = resolveNovelAuroraFastScrollBlockStartIndex(
                useTwoPaneLayout = false,
                isSelectionMode = isSelectionMode,
                chapterPageEnabled = chapterPageEnabled,
                showScanlatorSelector = state.showScanlatorSelector,
            )
            val fastScrollSpec by remember(fastScrollBaseTopPaddingPx, fastScrollBlockStartIndex) {
                derivedStateOf {
                    resolveTitleListFastScrollSpec(
                        baseTopPaddingPx = fastScrollBaseTopPaddingPx,
                        firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                        blockStartIndex = fastScrollBlockStartIndex,
                        blockStartOffsetPx = lazyListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == fastScrollBlockStartIndex }
                            ?.offset
                            ?.plus(with(density) { NOVEL_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
                    )
                }
            }

            VerticalFastScroller(
                listState = lazyListState,
                onThumbDragStarted = {
                    if (shouldAutoExpandAuroraNovelChaptersListForFastScroll(chaptersExpanded, chapters.size)) {
                        chaptersExpanded = true
                    }
                },
                onThumbDragStateChanged = { isThumbFastScrolling = it },
                thumbAllowed = { fastScrollSpec.thumbAllowed },
                topContentPadding = with(density) { fastScrollSpec.topPaddingPx.toDp() },
                bottomContentPadding = 112.dp,
                modifier = Modifier.zIndex(1f),
            ) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = 112.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Spacer(modifier = Modifier.height(screenHeight)) }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .auroraCenteredMaxWidth(contentMaxWidthDp),
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            NovelInfoCard(
                                novel = novel,
                                chapterCount = totalChapterCount,
                                nextUpdate = nextUpdate,
                                onTagSearch = { tag -> onSearch(tag, true) },
                                descriptionExpanded = descriptionExpanded,
                                genresExpanded = genresExpanded,
                                onToggleDescription = { descriptionExpanded = !descriptionExpanded },
                                onToggleGenres = { genresExpanded = !genresExpanded },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            NovelActionCard(
                                novel = novel,
                                trackingCount = trackingCount,
                                onAddToLibraryClicked = onToggleFavorite,
                                onTrackingClicked = onTrackingClicked,
                                onBatchDownloadClicked = onOpenBatchDownloadDialog,
                                onTranslatedDownloadClicked = onOpenTranslatedDownloadDialog,
                                onExportEpubClicked = onOpenEpubExportDialog,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (isSelectionMode) {
                        item {
                            SelectionBar(
                                selectedCount = selectedIds.size,
                                canMarkRead = selectedChapters.any { !it.read },
                                canMarkUnread = selectedChapters.any { it.read || it.lastPageRead > 0L },
                                canBookmark = selectedChapters.any { !it.bookmark },
                                canUnbookmark = selectedChapters.any { it.bookmark },
                                onClear = { onToggleAllSelection(false) },
                                onSelectAll = { onToggleAllSelection(true) },
                                onInvert = onInvertSelection,
                                onMarkRead = { onMultiMarkAsReadClicked(true) },
                                onMarkUnread = { onMultiMarkAsReadClicked(false) },
                                onBookmark = { onMultiBookmarkClicked(true) },
                                onUnbookmark = { onMultiBookmarkClicked(false) },
                                modifier = Modifier.auroraCenteredMaxWidth(contentMaxWidthDp),
                            )
                        }
                    }

                    item(
                        key = NOVEL_AURORA_CHAPTERS_HEADER_KEY,
                        contentType = NOVEL_AURORA_CHAPTERS_HEADER_KEY,
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ChaptersHeader(
                            chapterCount = totalChapterCount,
                            modifier = Modifier.auroraCenteredMaxWidth(contentMaxWidthDp),
                        )
                    }

                    if (chapterPageEnabled) {
                        item {
                            AuroraChapterPageControls(
                                chapterPageCurrent = chapterPageCurrent,
                                chapterPageTotal = chapterPageTotal,
                                chapterPageLoading = chapterPageLoading,
                                onChapterPageChange = onChapterPageChange,
                                modifier = Modifier.auroraCenteredMaxWidth(contentMaxWidthDp),
                            )
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
                                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    if (chapters.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(MR.strings.no_chapters_error),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    } else {
                        items(
                            items = chaptersToShow,
                            key = { it.id },
                            contentType = { "chapter" },
                        ) { chapter ->
                            NovelChapterCardCompact(
                                novel = novel,
                                chapter = chapter,
                                selected = chapter.id in selectedIds,
                                selectionMode = isSelectionMode,
                                onClick = { onChapterClick(chapter.id) },
                                onLongClick = { onChapterLongClick(chapter.id) },
                                onToggleBookmark = { onChapterBookmarkToggle(chapter.id) },
                                onToggleRead = { onChapterReadToggle(chapter.id) },
                                onToggleDownload = { onChapterDownloadToggle(chapter.id) },
                                chapterSwipeStartAction = chapterSwipeStartAction,
                                chapterSwipeEndAction = chapterSwipeEndAction,
                                onChapterSwipe = { action -> onChapterSwipe(chapter.id, action) },
                                downloaded = chapter.id in state.downloadedChapterIds,
                                downloading = chapter.id in state.downloadingChapterIds,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }

                        if (chapters.size > NOVEL_AURORA_COLLAPSED_PREVIEW_COUNT) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .auroraCenteredMaxWidth(contentMaxWidthDp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AuroraChapterListToggleButton(
                                        text = if (chaptersExpanded) {
                                            stringResource(AYMR.strings.action_show_less)
                                        } else {
                                            stringResource(
                                                AYMR.strings.action_show_all_chapters,
                                                chapters.size,
                                            )
                                        },
                                        onClick = { chaptersExpanded = !chaptersExpanded },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val heroThreshold = (screenHeight.value * 0.7f).toInt()
            if (firstVisibleItemIndex == 0 && scrollOffset < heroThreshold) {
                val heroAlpha = (1f - (scrollOffset / heroThreshold.toFloat())).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .graphicsLayer { alpha = heroAlpha },
                    contentAlignment = Alignment.BottomStart,
                ) {
                    NovelHeroContent(
                        novel = novel,
                        chapterCount = totalChapterCount,
                        onContinueReading = onStartReading,
                        isReading = isReading,
                    )
                }
            }

            val showFab = onStartReading != null && (firstVisibleItemIndex > 0 || scrollOffset > heroThreshold)
            val shouldShowFab = !useTwoPaneLayout && showFab && !isSelectionMode
            if (shouldShowTitleFastScrollFloatingActionButton(shouldShowFab, isThumbFastScrolling)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .padding(end = 20.dp, bottom = 20.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    AuroraTitleHeroActionFab(
                        onClick = onStartReading!!,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            val overlayChromeAlpha by animateFloatAsState(
                targetValue = if (showNovelOverlayChrome) 1f else 0f,
                label = "overlayChromeAlpha",
            )
            val overlayChromeOffsetY by animateFloatAsState(
                targetValue = if (showNovelOverlayChrome) 0f else -1f,
                label = "overlayChromeOffsetY",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
                    .graphicsLayer {
                        alpha = overlayChromeAlpha
                        translationY = overlayChromeOffsetY * size.height
                    }
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AuroraActionButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )

                Spacer(modifier = Modifier.weight(1f))

                AuroraActionButton(
                    onClick = onFilterButtonClicked,
                    icon = Icons.Default.FilterList,
                    contentDescription = null,
                    iconTint = if (state.filterActive) colors.accent else colors.accent.copy(alpha = 0.7f),
                )

                if (onWebView != null) {
                    AuroraActionButton(
                        onClick = onWebView,
                        icon = Icons.Filled.Public,
                        contentDescription = null,
                    )
                }

                var showMenu by remember { mutableStateOf(false) }
                val hasMenuActions = true
                if (hasMenuActions) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        AuroraActionButton(
                            onClick = { showMenu = !showMenu },
                            icon = Icons.Default.MoreVert,
                            contentDescription = null,
                        )

                        AuroraEntryDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            AuroraEntryDropdownMenuItem(
                                text = autoJumpToNextLabel,
                                onClick = {
                                    onToggleAutoJumpToNext()
                                    showMenu = false
                                },
                            )
                            if (isFromSource) {
                                AuroraEntryDropdownMenuItem(
                                    text = stringResource(MR.strings.action_webview_refresh),
                                    onClick = {
                                        onRefresh()
                                        showMenu = false
                                    },
                                )
                                if (globalSearchQuery != null) {
                                    AuroraEntryDropdownMenuItem(
                                        text = stringResource(MR.strings.action_global_search),
                                        onClick = {
                                            onSearch(globalSearchQuery, true)
                                            showMenu = false
                                        },
                                    )
                                }
                                if (onShare != null) {
                                    AuroraEntryDropdownMenuItem(
                                        text = stringResource(MR.strings.action_share),
                                        onClick = {
                                            onShare()
                                            showMenu = false
                                        },
                                    )
                                }
                            } else {
                                AuroraEntryDropdownMenuItem(
                                    text = stringResource(MR.strings.action_webview_refresh),
                                    onClick = {
                                        onRefresh()
                                        showMenu = false
                                    },
                                )
                                if (globalSearchQuery != null) {
                                    AuroraEntryDropdownMenuItem(
                                        text = stringResource(MR.strings.action_global_search),
                                        onClick = {
                                            onSearch(globalSearchQuery, true)
                                            showMenu = false
                                        },
                                    )
                                }
                                if (onShare != null) {
                                    AuroraEntryDropdownMenuItem(
                                        text = stringResource(MR.strings.action_share),
                                        onClick = {
                                            onShare()
                                            showMenu = false
                                        },
                                    )
                                }
                                if (onMigrateClicked != null) {
                                    AuroraEntryDropdownMenuItem(
                                        text = stringResource(MR.strings.action_migrate),
                                        onClick = {
                                            onMigrateClicked()
                                            showMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = colors.surface.copy(alpha = 0.96f),
                        contentColor = colors.textPrimary,
                        actionColor = colors.accent,
                        dismissActionContentColor = colors.textSecondary,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
    }
}

internal fun shouldUseNovelAuroraTwoPane(deviceClass: AuroraDeviceClass): Boolean {
    return deviceClass == AuroraDeviceClass.TabletExpanded
}

internal fun shouldUseNovelAuroraPaneScopedFastScroller(useTwoPaneLayout: Boolean): Boolean {
    return useTwoPaneLayout
}

internal fun resolveNovelAuroraVisibleChapterCount(
    chaptersExpanded: Boolean,
    totalChapters: Int,
): Int {
    if (totalChapters <= 0) return 0
    return if (chaptersExpanded) totalChapters else minOf(totalChapters, NOVEL_AURORA_COLLAPSED_PREVIEW_COUNT)
}

internal fun shouldAutoJumpToNovelAuroraTarget(
    hasScrolledToTarget: Boolean,
    chaptersExpanded: Boolean,
    totalChapters: Int,
): Boolean {
    if (hasScrolledToTarget) return false
    return chaptersExpanded || totalChapters <= NOVEL_AURORA_COLLAPSED_PREVIEW_COUNT
}

internal fun shouldAutoExpandAuroraNovelChaptersList(
    chaptersExpanded: Boolean,
    totalChapters: Int,
): Boolean {
    return !chaptersExpanded && totalChapters > NOVEL_AURORA_COLLAPSED_PREVIEW_COUNT
}

internal fun shouldAutoExpandAuroraNovelChaptersListForFastScroll(
    chaptersExpanded: Boolean,
    totalChapters: Int,
): Boolean {
    return shouldAutoExpandAuroraNovelChaptersList(
        chaptersExpanded = chaptersExpanded,
        totalChapters = totalChapters,
    )
}

internal fun resolveNovelAuroraFastScrollBlockStartIndex(
    useTwoPaneLayout: Boolean,
    isSelectionMode: Boolean,
    chapterPageEnabled: Boolean,
    showScanlatorSelector: Boolean,
): Int {
    val baseIndex = when {
        useTwoPaneLayout && isSelectionMode -> 2
        useTwoPaneLayout -> 1
        isSelectionMode -> 4
        else -> 3
    }
    return baseIndex + listOf(chapterPageEnabled, showScanlatorSelector).count { it }
}

private const val NOVEL_AURORA_COLLAPSED_PREVIEW_COUNT = 5
private const val NOVEL_AURORA_CHAPTERS_HEADER_KEY = "novel-aurora-chapters-header"
private val NOVEL_AURORA_FAST_SCROLL_ITEM_TOP_INSET = 4.dp

@Composable
private fun AuroraChapterPageControls(
    chapterPageCurrent: Int,
    chapterPageTotal: Int,
    chapterPageLoading: Boolean,
    onChapterPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.86f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SelectionChip(
            text = "←",
            onClick = {
                if (!chapterPageLoading && chapterPageCurrent > 1) {
                    onChapterPageChange(chapterPageCurrent - 1)
                }
            },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$chapterPageCurrent / $chapterPageTotal",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (chapterPageLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent,
                )
            }
        }
        SelectionChip(
            text = "→",
            onClick = {
                if (!chapterPageLoading && chapterPageCurrent < chapterPageTotal) {
                    onChapterPageChange(chapterPageCurrent + 1)
                }
            },
        )
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    canMarkRead: Boolean,
    canMarkUnread: Boolean,
    canBookmark: Boolean,
    canUnbookmark: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onBookmark: () -> Unit,
    onUnbookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.88f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = selectedCount.toString(),
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Text(
            text = stringResource(MR.strings.selected),
            color = colors.textPrimary,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        SelectionChip(text = stringResource(MR.strings.action_select_all), onClick = onSelectAll)
        SelectionChip(text = stringResource(MR.strings.action_select_inverse), onClick = onInvert)
        if (canBookmark) {
            SelectionChip(
                text = stringResource(MR.strings.action_bookmark),
                onClick = onBookmark,
            )
        }
        if (canUnbookmark) {
            SelectionChip(
                text = stringResource(MR.strings.action_remove_bookmark),
                onClick = onUnbookmark,
            )
        }
        if (canMarkRead) {
            SelectionChip(
                text = stringResource(MR.strings.action_mark_as_read),
                onClick = onMarkRead,
            )
        }
        if (canMarkUnread) {
            SelectionChip(
                text = stringResource(MR.strings.action_mark_as_unread),
                onClick = onMarkUnread,
            )
        }
        SelectionChip(text = stringResource(MR.strings.action_cancel), onClick = onClear)
    }
}

@Composable
private fun SelectionChip(
    text: String,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        colors.accent.copy(alpha = 0.20f),
                        colors.accent.copy(alpha = 0.10f),
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AuroraChapterListToggleButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.08f),
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            color = colors.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AuroraActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
) {
    val colors = AuroraTheme.colors
    val tint = iconTint ?: colors.accent.copy(alpha = 0.95f)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.surface.copy(alpha = 0.9f),
                        colors.surface.copy(alpha = 0.6f),
                    ),
                    center = Offset(0.3f, 0.3f),
                    radius = 0.8f,
                ),
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.3f, size.height * 0.3f),
                        radius = size.width * 0.6f,
                    ),
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
