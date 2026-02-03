package eu.kanade.presentation.entries.manga

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.aurora.ChaptersHeader
import eu.kanade.presentation.entries.manga.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.manga.components.aurora.MangaActionCard
import eu.kanade.presentation.entries.manga.components.aurora.MangaChapterCardCompact
import eu.kanade.presentation.entries.manga.components.aurora.MangaHeroContent
import eu.kanade.presentation.entries.manga.components.aurora.MangaInfoCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import kotlinx.coroutines.launch
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
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
) {
    val manga = state.manga
    val chapters = state.chapterListItems
    val colors = AuroraTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val lazyListState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val scope = rememberCoroutineScope()
    val statsBringIntoViewRequester = remember { BringIntoViewRequester() }

    // State for chapters expansion
    var chaptersExpanded by remember { mutableStateOf(false) }
    val chaptersToShow = if (chaptersExpanded) chapters else chapters.take(5)

    // State for description and genres expansion
    var descriptionExpanded by remember { mutableStateOf(false) }
    var genresExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fixed background poster
        if (manga.initialized && !state.isRefreshingData) {
            FullscreenPosterBackground(
                manga = manga,
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

        // Scrollable content
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
                    )
                }
            }

            // Chapters header
            item {
                Spacer(modifier = Modifier.height(20.dp))
                ChaptersHeader(chapterCount = chapters.size)
            }

            // Empty state for chapters
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
                        onChapterClicked = onChapterClicked,
                        onDownloadChapter = onDownloadChapter,
                    )
                }
            }

            // Show More button if there are more than 5 chapters
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
                                    "Показать меньше"
                                } else {
                                    "Показать все ${chapters.size} глав"
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

        // Hero content (fixed at bottom of first screen) - fades out on scroll
        // Show when we haven't scrolled much (index 0 with scroll less than 70% of screen height)
        val heroThreshold = (screenHeight.value * 0.7f).toInt()
        if (firstVisibleItemIndex == 0 && scrollOffset < heroThreshold) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                // Calculate fade out alpha based on scroll (0-70% range)
                val heroAlpha = (1f - (scrollOffset / heroThreshold.toFloat())).coerceIn(0f, 1f)

                Box(modifier = Modifier.graphicsLayer { alpha = heroAlpha }) {
                    MangaHeroContent(
                        manga = manga,
                        chapterCount = chapters.size,
                        onContinueReading = onContinueReading,
                    )
                }
            }
        }

        // Floating Play button (shows after Hero Content is hidden)
        val showFab = firstVisibleItemIndex > 0 || scrollOffset > heroThreshold
        if (showFab) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp, bottom = 20.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = onContinueReading,
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

        // Top header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Back button - Aurora glassmorphism style
            AuroraActionButton(
                onClick = navigateUp,
                icon = Icons.Filled.ArrowBack,
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
                AuroraDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    AuroraDropdownMenuItem(
                        text = stringResource(MR.strings.action_webview_refresh),
                        onClick = {
                            onRefresh()
                            showMenu = false
                        },
                    )
                    if (onShareClicked != null) {
                        AuroraDropdownMenuItem(
                            text = stringResource(MR.strings.action_share),
                            onClick = {
                                onShareClicked()
                                showMenu = false
                            },
                        )
                    }
                    if (onSettingsClicked != null) {
                        AuroraDropdownMenuItem(
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

/**
 * Aurora-styled dropdown menu container
 */
@Composable
private fun AuroraDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * Aurora-styled dropdown menu item
 */
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
