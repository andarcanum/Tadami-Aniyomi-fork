package eu.kanade.presentation.entries.manga

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Fixed background poster
        FullscreenPosterBackground(
            manga = manga,
            scrollOffset = scrollOffset
        )

        // Scrollable content
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = screenHeight,
                bottom = 100.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            // Action buttons card
            item {
                Spacer(modifier = Modifier.height(16.dp))
                MangaActionCard(
                    manga = manga,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onShareClicked = onShareClicked
                )
            }

            // Info card
            item {
                Spacer(modifier = Modifier.height(12.dp))
                MangaInfoCard(
                    manga = manga,
                    chapterCount = chapters.size,
                    nextUpdate = nextUpdate,
                    onTagSearch = onTagSearch
                )
            }

            // Chapters header
            item {
                Spacer(modifier = Modifier.height(20.dp))
                ChaptersHeader(chapterCount = chapters.size)
            }

            // Chapter list
            items(
                items = chapters,
                key = { (it as? ChapterList.Item)?.chapter?.id ?: it.hashCode() },
                contentType = { "chapter" }
            ) { item ->
                if (item is ChapterList.Item) {
                    MangaChapterCardCompact(
                        manga = manga,
                        item = item,
                        onChapterClicked = onChapterClicked,
                        onDownloadChapter = onDownloadChapter
                    )
                }
            }
        }

        // Hero content (fixed at bottom of first screen) - fades out on scroll
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 0.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            // Calculate fade out alpha based on scroll (0-200dp range)
            val heroAlpha = (1f - (scrollOffset / 200f)).coerceIn(0f, 1f)

            if (heroAlpha > 0f) {
                Box(modifier = Modifier.graphicsLayer { alpha = heroAlpha }) {
                    MangaHeroContent(
                        manga = manga,
                        chapterCount = chapters.size,
                        onContinueReading = onContinueReading
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(
                onClick = navigateUp,
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accent.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.accent
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Filter button
            IconButton(
                onClick = onFilterButtonClicked,
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accent.copy(alpha = 0.2f), CircleShape)
            ) {
                val filterTint = if (state.filterActive) colors.accent else colors.accent.copy(alpha = 0.7f)
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = filterTint
                )
            }

            // Download menu
            if (onDownloadActionClicked != null) {
                var downloadExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { downloadExpanded = !downloadExpanded },
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.accent.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = colors.accent
                        )
                    }
                    DropdownMenu(
                        expanded = downloadExpanded,
                        onDismissRequest = { downloadExpanded = false }
                    ) {
                        EntryDownloadDropdownMenu(
                            expanded = true,
                            onDismissRequest = { downloadExpanded = false },
                            onDownloadClicked = { onDownloadActionClicked.invoke(it) },
                            isManga = true
                        )
                    }
                }
            }

            // More menu
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showMenu = !showMenu },
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.accent.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = colors.accent
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { androidx.compose.material3.Text(text = stringResource(MR.strings.action_webview_refresh)) },
                        onClick = {
                            onRefresh()
                            showMenu = false
                        },
                    )
                    if (onShareClicked != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { androidx.compose.material3.Text(text = stringResource(MR.strings.action_share)) },
                            onClick = {
                                onShareClicked()
                                showMenu = false
                            },
                        )
                    }
                    if (onSettingsClicked != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { androidx.compose.material3.Text(text = "Settings") },
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
