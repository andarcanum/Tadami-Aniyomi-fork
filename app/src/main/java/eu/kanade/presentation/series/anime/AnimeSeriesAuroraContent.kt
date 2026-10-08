package eu.kanade.presentation.series.anime

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.presentation.entries.anime.components.aurora.AnimeEpisodeCardCompact
import eu.kanade.presentation.entries.anime.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.series.anime.components.AnimeSeriesEntryCard
import eu.kanade.presentation.series.anime.components.AnimeSeriesHeader
import eu.kanade.presentation.series.anime.components.AnimeSeriesWatchingActionRow
import eu.kanade.presentation.series.components.SeriesCategoryDialog
import eu.kanade.presentation.series.components.SeriesCategoryOption
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import eu.kanade.tachiyomi.ui.series.anime.AnimeSeriesScreenModel
import kotlinx.coroutines.flow.collect
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.series.model.SeriesCoverMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.plus

private const val ANIME_SERIES_TITLE_LIST_START_INDEX = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeSeriesAuroraContent(
    state: AnimeSeriesScreenModel.State,
    onBackClicked: () -> Unit,
    onAnimeClicked: (LibraryAnime) -> Unit,
    onEpisodeClicked: (LibraryAnime, Episode) -> Unit,
    onRenameClicked: (String) -> Unit,
    onCoverClicked: () -> Unit,
    categoryOptions: List<SeriesCategoryOption>,
    onCategoryClicked: (Long, Boolean) -> Unit,
    onDeleteClicked: () -> Unit,
    onRemoveEntryClicked: (Long) -> Unit,
    onReorderEntries: (List<Long>) -> Unit,
) {
    val series = state.series ?: return
    val colors = AuroraTheme.colors
    val heroAnime = series.selectedCoverAnime ?: series.activeAnime
    val customCoverFile = state.customCoverFile.takeIf { series.series.coverMode == SeriesCoverMode.CUSTOM }
    val watchingTarget = remember(series, state.episodes) {
        resolveAnimeSeriesWatchingTarget(
            series = series,
            episodes = state.episodes,
        )
    }

    val lazyListState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val scrollOffsetState = remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndexState = remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDraggingSeriesEntry by remember { mutableStateOf(false) }
    val dragDimAlpha by animateFloatAsState(
        targetValue = if (isDraggingSeriesEntry) 0.15f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "series_drag_dim",
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(AYMR.strings.anime_series_tab_titles),
        stringResource(AYMR.strings.anime_series_tab_episodes),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            modifier = Modifier.hazeSource(hazeState),
            targetState = heroAnime,
            animationSpec = tween(durationMillis = 450),
            label = "series_hero_background",
        ) { anime ->
            anime?.let {
                FullscreenPosterBackground(
                    anime = it,
                    scrollOffsetState = scrollOffsetState,
                    firstVisibleItemIndexState = firstVisibleItemIndexState,
                    resolvedCoverUrl = customCoverFile?.absolutePath,
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
                        hazeState = hazeState,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    AuroraSeriesActionButton(
                        icon = Icons.Default.Edit,
                        contentDescription = null,
                        onClick = { showRenameDialog = true },
                        hazeState = hazeState,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    AuroraSeriesActionButton(
                        icon = Icons.Default.Image,
                        contentDescription = null,
                        onClick = onCoverClicked,
                        hazeState = hazeState,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    AuroraSeriesActionButton(
                        icon = Icons.Default.Folder,
                        contentDescription = null,
                        onClick = { showCategoryDialog = true },
                        hazeState = hazeState,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    AuroraSeriesActionButton(
                        icon = Icons.Default.Delete,
                        contentDescription = null,
                        onClick = { showDeleteDialog = true },
                        iconTint = colors.accent,
                        hazeState = hazeState,
                    )
                }
            },
        ) { padding ->
            val previewEntries = remember(series.id) { series.entries.toMutableStateList() }
            val committedEntryIds by rememberUpdatedState(series.entries.map { it.id })
            val reorderEntriesLatest by rememberUpdatedState(onReorderEntries)
            val reorderableState = rememberReorderableLazyListState(lazyListState, padding) { from, to ->
                val fromIndex = from.index - ANIME_SERIES_TITLE_LIST_START_INDEX
                if (fromIndex !in previewEntries.indices) return@rememberReorderableLazyListState

                val item = previewEntries.removeAt(fromIndex)
                val toIndex = (to.index - ANIME_SERIES_TITLE_LIST_START_INDEX).coerceIn(0, previewEntries.size)
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
                    AnimeSeriesHeader(
                        series = series,
                        customCoverData = customCoverFile,
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
                        AnimeSeriesWatchingActionRow(
                            label = stringResource(
                                if (series.hasStarted) {
                                    AYMR.strings.anime_series_cta_continue_watching
                                } else {
                                    AYMR.strings.anime_series_cta_start_watching
                                },
                            ),
                            hint = watchingTarget?.let {
                                "${it.anime.anime.title} · ${it.episode.name}"
                            },
                            enabled = watchingTarget != null,
                            onClick = {
                                watchingTarget?.let { target ->
                                    onEpisodeClicked(target.anime, target.episode)
                                }
                            },
                        )
                    }
                }

                stickyHeader {
                    SecondaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {
                            SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(selectedTab),
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
                    itemsIndexed(previewEntries, key = { _, anime -> anime.id }) { index, anime ->
                        ReorderableItem(reorderableState, anime.id) { isDragging ->
                            AnimeSeriesEntryCard(
                                anime = anime,
                                ordinalLabel = resolveAnimeSeriesOrdinalLabel(index, previewEntries.size),
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.draggableHandle(),
                                onRemove = { onRemoveEntryClicked(anime.id) },
                                onClick = { onAnimeClicked(anime) },
                                modifier = Modifier.padding(vertical = 4.dp),
                                hazeState = hazeState,
                            )
                        }
                    }
                } else {
                    state.episodes.forEach { (libraryAnime, episodes) ->
                        stickyHeader(key = "header_${libraryAnime.id}") {
                            ListGroupHeader(text = libraryAnime.anime.title)
                        }
                        items(episodes, key = { "ep_${it.id}" }) { episode ->
                            AnimeEpisodeCardCompact(
                                anime = libraryAnime.anime,
                                item = EpisodeList.Item(
                                    episode = episode,
                                    downloadState = AnimeDownload.State.NOT_DOWNLOADED,
                                    downloadProgress = 0,
                                    selected = false,
                                ),
                                selected = false,
                                isNew = false,
                                isAnyEpisodeSelected = false,
                                episodeSwipeStartAction = LibraryPreferences.EpisodeSwipeAction.Disabled,
                                episodeSwipeEndAction = LibraryPreferences.EpisodeSwipeAction.Disabled,
                                onClick = { onEpisodeClicked(libraryAnime, episode) },
                                onLongClick = {},
                                onEpisodeSwipe = {},
                                onDownloadEpisode = null,
                                density = eu.kanade.domain.ui.model.EpisodeListDensity.Compact,
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
            title = { Text(text = stringResource(AYMR.strings.anime_series_action_delete_series)) },
            text = { Text(text = stringResource(AYMR.strings.anime_series_confirm_delete_series)) },
        )
    }

    if (showCategoryDialog) {
        SeriesCategoryDialog(
            categories = categoryOptions,
            initialCategoryId = series.categoryId,
            onDismissRequest = { showCategoryDialog = false },
            onConfirm = onCategoryClicked,
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
        title = { Text(text = stringResource(AYMR.strings.anime_series_action_rename_series)) },
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
    hazeState: HazeState? = null,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val tint = iconTint ?: colors.accent.copy(alpha = 0.95f)

    Box(
        modifier = modifier
            .auroraHeaderIconSurface(
                colors = colors,
                hazeState = hazeState,
                isPosterMode = true,
            )
            .size(44.dp)
            .clickable(onClick = {
                appHaptics.tap()
                onClick()
            }),
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
