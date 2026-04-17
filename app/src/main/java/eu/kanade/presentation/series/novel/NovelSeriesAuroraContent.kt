package eu.kanade.presentation.series.novel

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.novel.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.novel.components.aurora.NovelChapterCardCompactUi
import eu.kanade.presentation.series.novel.components.NovelSeriesEntryCard
import eu.kanade.presentation.series.novel.components.NovelSeriesHeader
import eu.kanade.presentation.series.novel.components.NovelSeriesReadingActionRow
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.series.novel.NovelSeriesScreenModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.toMutableStateList
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus
import tachiyomi.i18n.aniyomi.AYMR

private const val NOVEL_SERIES_TITLE_LIST_START_INDEX = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelSeriesAuroraContent(
    state: NovelSeriesScreenModel.State,
    onBackClicked: () -> Unit,
    onNovelClicked: (LibraryNovel) -> Unit,
    onChapterClicked: (LibraryNovel, NovelChapter) -> Unit,
    onRenameClicked: (String) -> Unit,
    onDeleteClicked: () -> Unit,
    onRemoveEntryClicked: (Long) -> Unit,
    onReorderEntries: (List<Long>) -> Unit,
) {
    val series = state.series ?: return
    val colors = AuroraTheme.colors
    val heroNovel = series.activeNovel
    val readingTarget = remember(series, state.chapters) {
        resolveNovelSeriesReadingTarget(
            series = series,
            chapters = state.chapters,
        )
    }

    val lazyListState = rememberLazyListState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDraggingSeriesEntry by remember { mutableStateOf(false) }
    val dragDimAlpha by animateFloatAsState(
        targetValue = if (isDraggingSeriesEntry) 0.15f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "series_drag_dim",
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(AYMR.strings.series_tab_titles),
        stringResource(AYMR.strings.series_tab_chapters),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Crossfade(
            targetState = heroNovel,
            animationSpec = tween(durationMillis = 450),
            label = "series_hero_background",
        ) { novel ->
            novel?.let {
                FullscreenPosterBackground(
                    novel = it,
                    scrollOffset = lazyListState.firstVisibleItemScrollOffset,
                    firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                    minimumBlurOverlayAlpha = 0.40f,
                    posterScrimAlpha = 0.40f,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dragDimAlpha)),
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Aurora style top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AuroraSeriesActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        onClick = onBackClicked,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    AuroraSeriesActionButton(
                        icon = Icons.Default.Edit,
                        contentDescription = null,
                        onClick = { showRenameDialog = true },
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    AuroraSeriesActionButton(
                        icon = Icons.Default.Delete,
                        contentDescription = null,
                        onClick = { showDeleteDialog = true },
                        iconTint = colors.accent,
                    )
                }
            },
        ) { padding ->
            val previewEntries = remember(series.id) { series.entries.toMutableStateList() }
            val committedEntryIds by rememberUpdatedState(series.entries.map { it.id })
            val reorderEntriesLatest by rememberUpdatedState(onReorderEntries)
            val reorderableState = rememberReorderableLazyListState(lazyListState, padding) { from, to ->
                val fromIndex = from.index - NOVEL_SERIES_TITLE_LIST_START_INDEX
                if (fromIndex !in previewEntries.indices) return@rememberReorderableLazyListState

                val item = previewEntries.removeAt(fromIndex)
                val toIndex = (to.index - NOVEL_SERIES_TITLE_LIST_START_INDEX).coerceIn(0, previewEntries.size)
                previewEntries.add(toIndex, item)
            }

            LaunchedEffect(series.entries) {
                if (!reorderableState.isAnyItemDragging) {
                    previewEntries.clear()
                    previewEntries.addAll(series.entries)
                }
            }

            LaunchedEffect(reorderableState) {
                var wasDragging = false
                snapshotFlow { reorderableState.isAnyItemDragging }.collect { isDragging ->
                    isDraggingSeriesEntry = isDragging
                    if (wasDragging && !isDragging) {
                        val previewIds = previewEntries.map { it.id }
                        if (previewIds != committedEntryIds) {
                            reorderEntriesLatest(previewIds)
                        }
                    }
                    wasDragging = isDragging
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(bottom = 100.dp, start = 12.dp, end = 12.dp),
            ) {
                item {
                    NovelSeriesHeader(
                        series = series,
                        modifier = Modifier.padding(bottom = 32.dp, top = 24.dp),
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 12.dp,
                                top = 8.dp,
                                end = 12.dp,
                                bottom = 20.dp,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        NovelSeriesReadingActionRow(
                            label = stringResource(
                                if (series.hasStarted) {
                                    AYMR.strings.novel_series_cta_continue_reading
                                } else {
                                    AYMR.strings.novel_series_cta_start_reading
                                },
                            ),
                            hint = readingTarget?.let {
                                "${it.novel.novel.title} · ${it.chapter.name}"
                            },
                            enabled = readingTarget != null,
                            onClick = {
                                readingTarget?.let { target ->
                                    onChapterClicked(target.novel, target.chapter)
                                }
                            },
                        )
                    }
                }

                stickyHeader {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = colors.textPrimary,
                            )
                        },
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        color = if (selectedTab == index) colors.textPrimary else colors.textSecondary,
                                    )
                                },
                            )
                        }
                    }
                }

                if (selectedTab == 0) {
                    itemsIndexed(previewEntries, key = { _, novel -> novel.id }) { index, novel ->
                        ReorderableItem(reorderableState, novel.id) { isDragging ->
                            NovelSeriesEntryCard(
                                novel = novel,
                                ordinalLabel = resolveNovelSeriesOrdinalLabel(index, previewEntries.size),
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.draggableHandle(),
                                onRemove = { onRemoveEntryClicked(novel.id) },
                                onClick = { onNovelClicked(novel) },
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    state.chapters.forEach { (libraryNovel, chapters) ->
                        stickyHeader(key = "header_${libraryNovel.id}") {
                            ListGroupHeader(text = libraryNovel.novel.title)
                        }
                        items(chapters, key = { "ch_${it.id}" }) { chapter ->
                            NovelChapterCardCompactUi.Render(
                                novel = libraryNovel.novel,
                                chapter = chapter,
                                selected = false,
                                isNew = false,
                                selectionMode = false,
                                onClick = { onChapterClicked(libraryNovel, chapter) },
                                onLongClick = {},
                                onTranslateClick = {},
                                onTranslatedDownloadClick = {},
                                onTranslatedDownloadLongClick = {},
                                onTranslatedDownloadOpenFolder = {},
                                onToggleBookmark = {},
                                onToggleRead = {},
                                onToggleDownload = {},
                                chapterSwipeStartAction = LibraryPreferences.NovelSwipeAction.Disabled,
                                chapterSwipeEndAction = LibraryPreferences.NovelSwipeAction.Disabled,
                                onChapterSwipe = {},
                                downloaded = false,
                                downloading = false,
                            )
                    }
                }
            }
        }
    }
    }

    if (showRenameDialog) {
        RenameSeriesDialog(
            initialTitle = series.title,
            onDismissRequest = { showRenameDialog = false },
            onConfirm = onRenameClicked,
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClicked()
                        showDeleteDialog = false
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            title = { Text(text = stringResource(AYMR.strings.action_delete_series)) },
            text = { Text(text = stringResource(AYMR.strings.confirm_delete_series)) },
        )
    }
}

@Composable
private fun RenameSeriesDialog(
    initialTitle: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(title)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = { Text(text = stringResource(AYMR.strings.action_rename_series)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
    )
}

@Composable
private fun AuroraSeriesActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
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
