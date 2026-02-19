package eu.kanade.presentation.entries.novel

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.entries.manga.components.ScanlatorBranchSelector
import eu.kanade.presentation.entries.novel.components.aurora.ChaptersHeader
import eu.kanade.presentation.entries.novel.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.novel.components.aurora.NovelActionCard
import eu.kanade.presentation.entries.novel.components.aurora.NovelChapterCardCompact
import eu.kanade.presentation.entries.novel.components.aurora.NovelHeroContent
import eu.kanade.presentation.entries.novel.components.aurora.NovelInfoCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
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
    onShare: (() -> Unit)?,
    onWebView: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    trackingCount: Int,
    onOpenBatchDownloadDialog: (() -> Unit)?,
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
    onToggleAllSelection: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onMultiBookmarkClicked: (Boolean) -> Unit,
    onMultiMarkAsReadClicked: (Boolean) -> Unit,
) {
    val novel = state.novel
    val chapters = state.processedChapters
    val colors = AuroraTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val lazyListState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }

    val selectedIds = state.selectedChapterIds
    val isSelectionMode = selectedIds.isNotEmpty()
    val selectedChapters = chapters.filter { it.id in selectedIds }
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

    var descriptionExpanded by remember { mutableStateOf(false) }
    var genresExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!novel.thumbnailUrl.isNullOrBlank()) {
            FullscreenPosterBackground(
                novel = novel,
                scrollOffset = scrollOffset,
                firstVisibleItemIndex = firstVisibleItemIndex,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 112.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Spacer(modifier = Modifier.height(screenHeight)) }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NovelInfoCard(
                        novel = novel,
                        chapterCount = chapters.size,
                        nextUpdate = nextUpdate,
                        onTagSearch = {},
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
                        onExportEpubClicked = onOpenEpubExportDialog,
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
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                ChaptersHeader(chapterCount = chapters.size)
            }

            if (state.showScanlatorSelector) {
                item {
                    ScanlatorBranchSelector(
                        scanlatorChapterCounts = scanlatorChapterCounts,
                        selectedScanlator = selectedScanlator,
                        onScanlatorSelected = onScanlatorSelected,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                    items = visibleChapters,
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

                if (visibleChapterCount < chapters.size) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SelectionChip(
                                text = "${stringResource(MR.strings.label_more)} " +
                                    "(${chapters.size - visibleChapterCount})",
                                onClick = {
                                    visibleChapterCount = nextVisibleChapterCount(
                                        currentCount = visibleChapterCount,
                                        totalCount = chapters.size,
                                        step = NOVEL_CHAPTERS_PAGE_SIZE,
                                    )
                                },
                            )
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
                    .graphicsLayer { alpha = heroAlpha },
                contentAlignment = Alignment.BottomStart,
            ) {
                NovelHeroContent(
                    novel = novel,
                    chapterCount = chapters.size,
                    onContinueReading = onStartReading,
                    isReading = isReading,
                )
            }
        }

        val showFab = onStartReading != null && (firstVisibleItemIndex > 0 || scrollOffset > heroThreshold)
        if (showFab) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp, bottom = 20.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                FloatingActionButton(
                    onClick = onStartReading!!,
                    containerColor = colors.accent,
                    contentColor = colors.textOnAccent,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AuroraActionButton(onClick = onBack, icon = Icons.Filled.ArrowBack, contentDescription = null)

            Spacer(modifier = Modifier.weight(1f))

            AuroraActionButton(
                onClick = onFilterButtonClicked,
                icon = Icons.Default.FilterList,
                contentDescription = null,
                iconTint = if (state.filterActive) colors.accent else colors.accent.copy(alpha = 0.7f),
            )

            if (!isFromSource) {
                AuroraActionButton(
                    onClick = onRefresh,
                    icon = Icons.Default.Refresh,
                    contentDescription = null,
                )
            }

            if (onWebView != null) {
                AuroraActionButton(
                    onClick = onWebView,
                    icon = Icons.Filled.Public,
                    contentDescription = null,
                )
            }

            var showMenu by remember { mutableStateOf(false) }
            val hasMenuActions = if (isFromSource) {
                true
            } else {
                onShare != null || onMigrateClicked != null
            }
            if (hasMenuActions) {
                Box(contentAlignment = Alignment.TopEnd) {
                    AuroraActionButton(
                        onClick = { showMenu = !showMenu },
                        icon = Icons.Default.MoreVert,
                        contentDescription = null,
                    )

                    AuroraDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        if (isFromSource) {
                            AuroraDropdownMenuItem(
                                text = stringResource(MR.strings.action_webview_refresh),
                                onClick = {
                                    onRefresh()
                                    showMenu = false
                                },
                            )
                            if (onShare != null) {
                                AuroraDropdownMenuItem(
                                    text = stringResource(MR.strings.action_share),
                                    onClick = {
                                        onShare()
                                        showMenu = false
                                    },
                                )
                            }
                        } else {
                            if (onShare != null) {
                                AuroraDropdownMenuItem(
                                    text = stringResource(MR.strings.action_share),
                                    onClick = {
                                        onShare()
                                        showMenu = false
                                    },
                                )
                            }
                            if (onMigrateClicked != null) {
                                AuroraDropdownMenuItem(
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
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = Modifier
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
        if (canBookmark) SelectionChip(text = stringResource(MR.strings.action_bookmark), onClick = onBookmark)
        if (canUnbookmark) {
            SelectionChip(
                text = stringResource(MR.strings.action_remove_bookmark),
                onClick = onUnbookmark,
            )
        }
        if (canMarkRead) SelectionChip(text = stringResource(MR.strings.action_mark_as_read), onClick = onMarkRead)
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
                Brush.horizontalGradient(listOf(colors.accent.copy(alpha = 0.20f), colors.accent.copy(alpha = 0.10f))),
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

@Composable
private fun AuroraDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
private fun AuroraDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        },
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.MenuDefaults.itemColors(
            textColor = colors.textPrimary,
        ),
    )
}
