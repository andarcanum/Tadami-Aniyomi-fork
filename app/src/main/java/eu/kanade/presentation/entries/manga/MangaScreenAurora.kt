package eu.kanade.presentation.entries.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.resolveEntryAutoJumpTargetIndex
import eu.kanade.presentation.entries.resolveTitleListFastScrollSpec
import eu.kanade.presentation.entries.shouldShowTitleFastScrollFloatingActionButton
import eu.kanade.presentation.entries.shouldShowTitleFastScrollOverlayChrome
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.entries.components.AuroraEntryHoldToRefresh
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionFab
import eu.kanade.presentation.entries.components.normalizeAuroraGlobalSearchQuery
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.ScanlatorBranchSelector
import eu.kanade.presentation.entries.manga.components.aurora.ChaptersHeader
import eu.kanade.presentation.entries.manga.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.manga.components.aurora.MangaActionCard
import eu.kanade.presentation.entries.manga.components.aurora.MangaChapterCardCompact
import eu.kanade.presentation.entries.manga.components.aurora.MangaHeroContent
import eu.kanade.presentation.entries.manga.components.aurora.MangaInfoCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.resolveAuroraAdaptiveSpec
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import kotlinx.coroutines.launch
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaScreenAuroraImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    showScanlatorSelector: Boolean,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onCoverClicked: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onSettingsClicked: (() -> Unit)?,
    isAutoJumpToNextEnabled: Boolean,
    autoJumpToNextLabel: String,
    onToggleAutoJumpToNext: () -> Unit,
) {
    val manga = state.manga
    val globalSearchQuery = remember(manga.title) { normalizeAuroraGlobalSearchQuery(manga.title) }
    val chapters = state.chapterListItems
    val selectedChapters = remember(chapters) {
        chapters.filterIsInstance<ChapterList.Item>().filter { it.selected }
    }
    val isAnyChapterSelected = selectedChapters.isNotEmpty()
    val colors = AuroraTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val auroraAdaptiveSpec = remember(isTabletUi, configuration.screenWidthDp) {
        resolveAuroraAdaptiveSpec(
            isTabletUi = isTabletUi,
            containerWidthDp = configuration.screenWidthDp,
        )
    }
    val contentMaxWidthDp = auroraAdaptiveSpec.entryMaxWidthDp
    val useTwoPaneLayout = shouldUseMangaAuroraTwoPane(auroraAdaptiveSpec.deviceClass)

    val lazyListState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val scope = rememberCoroutineScope()
    val statsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val haptic = LocalHapticFeedback.current

    // State for chapters expansion
    var chaptersExpanded by remember { mutableStateOf(false) }
    val chaptersToShow = if (chaptersExpanded) chapters else chapters.take(5)
    val hasReadingProgress = remember(chapters) {
        chapters.any { chapterItem ->
            val chapter = (chapterItem as? ChapterList.Item)?.chapter ?: return@any false
            chapter.read || chapter.lastPageRead > 0L
        }
    }

    // Auto-scroll to target chapter on initial load
    var hasScrolledToTarget: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(state.targetChapterIndex, chaptersExpanded) {
        if (!hasScrolledToTarget && chaptersExpanded) {
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

    BackHandler(onBack = {
        if (isAnyChapterSelected) {
            onAllChapterSelected(false)
        } else {
            navigateUp()
        }
    })

    LaunchedEffect(isAnyChapterSelected, chaptersExpanded, chapters.size) {
        if (isAnyChapterSelected &&
            shouldAutoExpandAuroraChaptersList(
                chaptersExpanded = chaptersExpanded,
                totalChapters = chapters.size,
            )
        ) {
            chaptersExpanded = true
        }
    }

    // State for description and genres expansion
    var descriptionExpanded by remember { mutableStateOf(false) }
    var genresExpanded by remember { mutableStateOf(false) }
    var isThumbFastScrolling by remember { mutableStateOf(false) }

    AuroraEntryHoldToRefresh(
        refreshing = state.isRefreshingData,
        onRefresh = onRefresh,
        enabled = !isAnyChapterSelected,
        modifier = Modifier.fillMaxSize(),
        indicatorPadding = WindowInsets.statusBars.asPaddingValues(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Fixed background poster
            FullscreenPosterBackground(
                manga = manga,
                scrollOffset = scrollOffset,
                firstVisibleItemIndex = firstVisibleItemIndex,
            )

            if (useTwoPaneLayout) {
                val topContentPadding = 96.dp
                val paneDensity = LocalDensity.current
                val paneTopPaddingPx = with(paneDensity) { topContentPadding.roundToPx() }
                val paneFastScrollBlockStartIndex = resolveMangaAuroraFastScrollBlockStartIndex(
                    useTwoPaneLayout = true,
                    showScanlatorSelector = showScanlatorSelector,
                )
                val paneFastScrollSpec by remember(paneTopPaddingPx, paneFastScrollBlockStartIndex) {
                    derivedStateOf {
                        resolveTitleListFastScrollSpec(
                            baseTopPaddingPx = paneTopPaddingPx,
                            firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                            blockStartIndex = paneFastScrollBlockStartIndex,
                            blockStartOffsetPx = lazyListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == paneFastScrollBlockStartIndex }
                                ?.offset
                                ?.plus(with(paneDensity) { MANGA_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
                        )
                    }
                }
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
                                    .auroraCenteredMaxWidth(420)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                        alignment = Alignment.TopStart,
                                    ),
                            ) {
                                MangaHeroContent(
                                    manga = manga,
                                    chapterCount = chapters.size,
                                    hasReadingProgress = hasReadingProgress,
                                    onContinueReading = onContinueReading,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                MangaInfoCard(
                                    manga = manga,
                                    chapterCount = chapters.size,
                                    nextUpdate = nextUpdate,
                                    onTagSearch = onTagSearch,
                                    descriptionExpanded = descriptionExpanded,
                                    genresExpanded = genresExpanded,
                                    onToggleDescription = {
                                        descriptionExpanded = !descriptionExpanded
                                        if (descriptionExpanded) {
                                            scope.launch {
                                                statsBringIntoViewRequester.bringIntoView()
                                            }
                                        }
                                    },
                                    onToggleGenres = { genresExpanded = !genresExpanded },
                                    statsRequester = statsBringIntoViewRequester,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                MangaActionCard(
                                    manga = manga,
                                    trackingCount = state.trackingCount,
                                    onAddToLibraryClicked = onAddToLibraryClicked,
                                    onWebViewClicked = onWebViewClicked,
                                    onTrackingClicked = onTrackingClicked,
                                    onShareClicked = onShareClicked,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    },
                    endContent = {
                        VerticalFastScroller(
                            listState = lazyListState,
                            onThumbDragStarted = {
                                if (shouldAutoExpandAuroraChaptersListForFastScroll(chaptersExpanded, chapters.size)) {
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
                                state = lazyListState,
                                contentPadding = PaddingValues(top = topContentPadding, bottom = 100.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 6.dp, end = 12.dp),
                            ) {
                            item {
                                ChaptersHeader(chapterCount = chapters.size)
                            }

                            if (showScanlatorSelector) {
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
                            }

                            items(
                                items = chaptersToShow,
                                key = { (it as? ChapterList.Item)?.chapter?.id ?: it.hashCode() },
                                contentType = { "chapter" },
                            ) { item ->
                                if (item is ChapterList.Item) {
                                    MangaChapterCardCompact(
                                        manga = manga,
                                        item = item,
                                        selected = item.selected,
                                        isAnyChapterSelected = isAnyChapterSelected,
                                        chapterSwipeStartAction = chapterSwipeStartAction,
                                        chapterSwipeEndAction = chapterSwipeEndAction,
                                        onChapterClicked = {
                                            when (
                                                resolveAuroraChapterClickAction(
                                                    isChapterSelected = item.selected,
                                                    isAnyChapterSelected = isAnyChapterSelected,
                                                )
                                            ) {
                                                AuroraChapterClickAction.OpenChapter -> {
                                                    onChapterClicked(item.chapter)
                                                }
                                                AuroraChapterClickAction.SelectChapter -> {
                                                    onChapterSelected(item, true, true, false)
                                                    if (shouldAutoExpandAuroraChaptersList(
                                                            chaptersExpanded,
                                                            chapters.size,
                                                        )
                                                    ) {
                                                        chaptersExpanded = true
                                                    }
                                                }
                                                AuroraChapterClickAction.UnselectChapter -> {
                                                    onChapterSelected(item, false, true, false)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            onChapterSelected(item, !item.selected, true, true)
                                            if (!item.selected &&
                                                shouldAutoExpandAuroraChaptersList(chaptersExpanded, chapters.size)
                                            ) {
                                                chaptersExpanded = true
                                            }
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onChapterSwipe = { action -> onChapterSwipe(item, action) },
                                        onDownloadChapter = onDownloadChapter,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            if (chapters.size > 5) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
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
                                                .clickable { chaptersExpanded = !chaptersExpanded }
                                                .padding(horizontal = 24.dp, vertical = 12.dp),
                                        ) {
                                            Text(
                                                text = if (chaptersExpanded) {
                                                    stringResource(AYMR.strings.action_show_less)
                                                } else {
                                                    stringResource(AYMR.strings.action_show_all_chapters, chapters.size)
                                                },
                                                color = colors.accent,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    },
                )
            } else {
                // Scrollable content
                val density = LocalDensity.current
                val fastScrollBaseTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp
                val fastScrollBaseTopPaddingPx = with(density) { fastScrollBaseTopPadding.roundToPx() }
                val fastScrollBlockStartIndex = resolveMangaAuroraFastScrollBlockStartIndex(
                    useTwoPaneLayout = false,
                    showScanlatorSelector = showScanlatorSelector,
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
                                ?.plus(with(density) { MANGA_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
                        )
                    }
                }
                VerticalFastScroller(
                    listState = lazyListState,
                    onThumbDragStarted = {
                        if (shouldAutoExpandAuroraChaptersListForFastScroll(chaptersExpanded, chapters.size)) {
                            chaptersExpanded = true
                        }
                    },
                    onThumbDragStateChanged = { isThumbFastScrolling = it },
                    thumbAllowed = { fastScrollSpec.thumbAllowed },
                    topContentPadding = with(density) { fastScrollSpec.topPaddingPx.toDp() },
                    bottomContentPadding = 100.dp,
                    modifier = Modifier.zIndex(1f),
                ) {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(bottom = 100.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    // Spacer for poster/hero area
                    item {
                        Spacer(modifier = Modifier.height(screenHeight))
                    }

                    // Info and Action cards merged into one item for layout stability
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .auroraCenteredMaxWidth(contentMaxWidthDp)
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                    alignment = Alignment.TopStart,
                                ),
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            MangaInfoCard(
                                manga = manga,
                                chapterCount = chapters.size,
                                nextUpdate = nextUpdate,
                                onTagSearch = onTagSearch,
                                descriptionExpanded = descriptionExpanded,
                                genresExpanded = genresExpanded,
                                onToggleDescription = {
                                    descriptionExpanded = !descriptionExpanded
                                    if (descriptionExpanded) {
                                        scope.launch {
                                            statsBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                                onToggleGenres = { genresExpanded = !genresExpanded },
                                statsRequester = statsBringIntoViewRequester,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            MangaActionCard(
                                manga = manga,
                                trackingCount = state.trackingCount,
                                onAddToLibraryClicked = onAddToLibraryClicked,
                                onWebViewClicked = onWebViewClicked,
                                onTrackingClicked = onTrackingClicked,
                                onShareClicked = onShareClicked,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Chapters header
                    item(
                        key = MANGA_AURORA_CHAPTERS_HEADER_KEY,
                        contentType = MANGA_AURORA_CHAPTERS_HEADER_KEY,
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))
                        ChaptersHeader(
                            chapterCount = chapters.size,
                            modifier = Modifier.auroraCenteredMaxWidth(contentMaxWidthDp),
                        )
                    }

                    if (showScanlatorSelector) {
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

                    // Empty state for chapters
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
                    }

                    // Chapter list
                    items(
                        items = chaptersToShow,
                        key = { (it as? ChapterList.Item)?.chapter?.id ?: it.hashCode() },
                        contentType = { "chapter" },
                    ) { item ->
                        if (item is ChapterList.Item) {
                            MangaChapterCardCompact(
                                manga = manga,
                                item = item,
                                selected = item.selected,
                                isAnyChapterSelected = isAnyChapterSelected,
                                chapterSwipeStartAction = chapterSwipeStartAction,
                                chapterSwipeEndAction = chapterSwipeEndAction,
                                onChapterClicked = {
                                    when (
                                        resolveAuroraChapterClickAction(
                                            isChapterSelected = item.selected,
                                            isAnyChapterSelected = isAnyChapterSelected,
                                        )
                                    ) {
                                        AuroraChapterClickAction.OpenChapter -> {
                                            onChapterClicked(item.chapter)
                                        }
                                        AuroraChapterClickAction.SelectChapter -> {
                                            onChapterSelected(item, true, true, false)
                                            if (shouldAutoExpandAuroraChaptersList(chaptersExpanded, chapters.size)) {
                                                chaptersExpanded = true
                                            }
                                        }
                                        AuroraChapterClickAction.UnselectChapter -> {
                                            onChapterSelected(item, false, true, false)
                                        }
                                    }
                                },
                                onLongClick = {
                                    onChapterSelected(item, !item.selected, true, true)
                                    if (!item.selected &&
                                        shouldAutoExpandAuroraChaptersList(chaptersExpanded, chapters.size)
                                    ) {
                                        chaptersExpanded = true
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onChapterSwipe = { action -> onChapterSwipe(item, action) },
                                onDownloadChapter = onDownloadChapter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // Show More button if there are more than 5 chapters
                    if (chapters.size > 5) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
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
                                        .clickable { chaptersExpanded = !chaptersExpanded }
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        text = if (chaptersExpanded) {
                                            stringResource(AYMR.strings.action_show_less)
                                        } else {
                                            stringResource(AYMR.strings.action_show_all_chapters, chapters.size)
                                        },
                                        color = colors.accent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }

            // Hero content (fixed at bottom of first screen) - fades out on scroll
            // Show when we haven't scrolled much (index 0 with scroll less than 70% of screen height)
            val heroThreshold = (screenHeight.value * 0.7f).toInt()
            if (!useTwoPaneLayout && firstVisibleItemIndex == 0 && scrollOffset < heroThreshold) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(2f)
                        .padding(bottom = 0.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    // Calculate fade out alpha based on scroll (0-70% range)
                    val heroAlpha = (1f - (scrollOffset / heroThreshold.toFloat())).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .zIndex(2f)
                            .graphicsLayer { alpha = heroAlpha },
                    ) {
                        MangaHeroContent(
                            manga = manga,
                            chapterCount = chapters.size,
                            hasReadingProgress = hasReadingProgress,
                            onContinueReading = onContinueReading,
                        )
                    }
                }
            }

            // Floating Play button (shows after Hero Content is hidden)
            val showFab = firstVisibleItemIndex > 0 || scrollOffset > heroThreshold
            val shouldShowFab = !useTwoPaneLayout && showFab && !isAnyChapterSelected
            if (shouldShowTitleFastScrollFloatingActionButton(shouldShowFab, isThumbFastScrolling)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .padding(end = 20.dp, bottom = 20.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    AuroraTitleHeroActionFab(
                        onClick = onContinueReading,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            if (!isAnyChapterSelected && shouldShowTitleFastScrollOverlayChrome(isThumbFastScrolling)) {
                // Top header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(2f)
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Back button - Aurora glassmorphism style
                    AuroraActionButton(
                        onClick = navigateUp,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Filter button - Aurora glassmorphism style
                    AuroraActionButton(
                        onClick = onFilterButtonClicked,
                        icon = Icons.Default.FilterList,
                        contentDescription = null,
                        iconTint = if (state.filterActive) colors.accent else colors.accent.copy(alpha = 0.7f),
                    )

                    // Download menu - Aurora glassmorphism style
                    if (onDownloadActionClicked != null) {
                        var downloadExpanded by remember { mutableStateOf(false) }
                        Box(contentAlignment = Alignment.TopEnd) {
                            AuroraActionButton(
                                onClick = { downloadExpanded = !downloadExpanded },
                                icon = Icons.Filled.Download,
                                contentDescription = null,
                            )
                            EntryDownloadDropdownMenu(
                                expanded = downloadExpanded,
                                onDismissRequest = { downloadExpanded = false },
                                onDownloadClicked = { onDownloadActionClicked.invoke(it) },
                                isManga = true,
                                useAuroraStyle = true,
                            )
                        }
                    }

                    // More menu - Aurora glassmorphism style
                    var showMenu by remember { mutableStateOf(false) }
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
                                text = stringResource(MR.strings.action_webview_refresh),
                                onClick = {
                                    onRefresh()
                                    showMenu = false
                                },
                            )
                            AuroraEntryDropdownMenuItem(
                                text = autoJumpToNextLabel,
                                onClick = {
                                    onToggleAutoJumpToNext()
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
                            if (onShareClicked != null) {
                                AuroraEntryDropdownMenuItem(
                                    text = stringResource(MR.strings.action_share),
                                    onClick = {
                                        onShareClicked()
                                        showMenu = false
                                    },
                                )
                            }
                            if (onSettingsClicked != null) {
                                AuroraEntryDropdownMenuItem(
                                    text = stringResource(MR.strings.action_settings),
                                    onClick = {
                                        onSettingsClicked()
                                        showMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            AuroraChapterSelectionBottomStack(
                selected = selectedChapters,
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = onInvertSelection,
                onCancel = { onAllChapterSelected(false) },
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                onDownloadChapter = onDownloadChapter,
                onMultiDeleteClicked = onMultiDeleteClicked,
                fillFraction = if (useTwoPaneLayout) 0.5f else 1f,
                modifier = Modifier
                    .align(if (useTwoPaneLayout) Alignment.BottomEnd else Alignment.BottomCenter)
                    .padding(WindowInsets.systemBars.asPaddingValues()),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(WindowInsets.systemBars.asPaddingValues()),
            )
        }
    }
}

internal fun shouldUseMangaAuroraTwoPane(deviceClass: AuroraDeviceClass): Boolean {
    return deviceClass == AuroraDeviceClass.TabletExpanded
}

internal fun shouldUseMangaAuroraPaneScopedFastScroller(useTwoPaneLayout: Boolean): Boolean {
    return useTwoPaneLayout
}

internal fun resolveMangaAuroraFastScrollBlockStartIndex(
    useTwoPaneLayout: Boolean,
    showScanlatorSelector: Boolean,
): Int {
    val baseIndex = if (useTwoPaneLayout) 1 else 3
    return baseIndex + if (showScanlatorSelector) 1 else 0
}

private const val MANGA_AURORA_CHAPTERS_HEADER_KEY = "manga-aurora-chapters-header"
private val MANGA_AURORA_FAST_SCROLL_ITEM_TOP_INSET = 6.dp

internal enum class AuroraChapterClickAction {
    OpenChapter,
    SelectChapter,
    UnselectChapter,
}

internal fun resolveAuroraChapterClickAction(
    isChapterSelected: Boolean,
    isAnyChapterSelected: Boolean,
): AuroraChapterClickAction {
    return when {
        isChapterSelected -> AuroraChapterClickAction.UnselectChapter
        isAnyChapterSelected -> AuroraChapterClickAction.SelectChapter
        else -> AuroraChapterClickAction.OpenChapter
    }
}

internal fun shouldAutoExpandAuroraChaptersList(
    chaptersExpanded: Boolean,
    totalChapters: Int,
): Boolean {
    return !chaptersExpanded && totalChapters > 5
}

internal fun shouldAutoExpandAuroraChaptersListForFastScroll(
    chaptersExpanded: Boolean,
    totalChapters: Int,
): Boolean {
    return shouldAutoExpandAuroraChaptersList(
        chaptersExpanded = chaptersExpanded,
        totalChapters = totalChapters,
    )
}

@Composable
private fun AuroraSelectionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        colors.accent.copy(alpha = 0.2f),
                        colors.accent.copy(alpha = 0.1f),
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AuroraChapterSelectionBottomStack(
    selected: List<ChapterList.Item>,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancel: () -> Unit,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    fillFraction: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(fillFraction)) {
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AuroraTheme.colors.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AuroraTheme.colors.accent.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = selected.size.toString(),
                        color = AuroraTheme.colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AuroraSelectionIconButton(
                    icon = Icons.Outlined.SelectAll,
                    contentDescription = stringResource(MR.strings.action_select_all),
                    onClick = onSelectAll,
                )
                AuroraSelectionIconButton(
                    icon = Icons.Filled.SwapHoriz,
                    contentDescription = stringResource(MR.strings.action_select_inverse),
                    onClick = onInvertSelection,
                )
                AuroraSelectionIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(MR.strings.action_cancel),
                    onClick = onCancel,
                )
            }
        }

        EntryBottomActionMenu(
            visible = selected.isNotEmpty(),
            isManga = true,
            modifier = Modifier.fillMaxWidth(),
            onBookmarkClicked = {
                onMultiBookmarkClicked(selected.map { it.chapter }, true)
            }.takeIf { selected.any { !it.chapter.bookmark } },
            onRemoveBookmarkClicked = {
                onMultiBookmarkClicked(selected.map { it.chapter }, false)
            }.takeIf { selected.all { it.chapter.bookmark } },
            onMarkAsViewedClicked = {
                onMultiMarkAsReadClicked(selected.map { it.chapter }, true)
            }.takeIf { selected.any { !it.chapter.read } },
            onMarkAsUnviewedClicked = {
                onMultiMarkAsReadClicked(selected.map { it.chapter }, false)
            }.takeIf { selected.any { it.chapter.read || it.chapter.lastPageRead > 0L } },
            onMarkPreviousAsViewedClicked = {
                onMarkPreviousAsReadClicked(selected.first().chapter)
            }.takeIf { selected.size == 1 },
            onDownloadClicked = {
                onDownloadChapter!!(selected, ChapterDownloadAction.START)
            }.takeIf {
                onDownloadChapter != null && selected.any { it.downloadState != MangaDownload.State.DOWNLOADED }
            },
            onDeleteClicked = {
                onMultiDeleteClicked(selected.map { it.chapter })
            }.takeIf {
                selected.any { it.downloadState == MangaDownload.State.DOWNLOADED }
            },
        )
    }
}

/**
 * Aurora-styled action button with glassmorphism effect
 */
@Composable
private fun AuroraActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                // Subtle inner glow
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
