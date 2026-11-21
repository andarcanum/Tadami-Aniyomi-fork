package eu.kanade.tachiyomi.ui.reels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.SourceFilterAnimeDialog
import eu.kanade.tachiyomi.ui.reels.components.ReelsErrorState
import eu.kanade.tachiyomi.ui.reels.components.ReelsLocalFilterSheet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

/**
 * One saved album's content (device feature, sign-off prototype): a 3-column preview grid of
 * the category feed with per-item progress bars (watch history fraction) and watched badges
 * (manual mark or >=90% progress); the search field filters the loaded pages client-side;
 * a tap opens the regular reels feed in niche mode at that clip; a long press offers the
 * manual watched mark toggle.
 */
data class ReelsAlbumScreen(
    val sourceId: Long,
    val albumId: String,
    val albumName: String,
) : Screen {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ReelsAlbumScreenModel(sourceId, albumId, albumName) }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var query by remember { mutableStateOf("") }
        var showSearch by remember { mutableStateOf(false) }
        var showFilterSheet by remember { mutableStateOf(false) }
        var showSourceFilterDialog by remember { mutableStateOf(false) }
        var menuItem by remember { mutableStateOf<ShortVideoItem?>(null) }

        val availableTags = remember(state.items) {
            extractAvailableTags(state.items)
        }
        val filtered = remember(state.items, state.localFilter, query, state.watched, state.progress) {
            applyReelsLocalFilter(
                items = state.items,
                filter = state.localFilter,
                query = query,
                watchedIds = state.watched,
                progressMap = state.progress,
            )
        }
        val hasLocalConstraint = query.isNotBlank() || state.localFilter.isActive

        val gridState = rememberLazyGridState()
        // F03: local search/filters must paginate in FILTERED coordinates. Auto-fill is capped per
        // constraint; beyond the cap the user pulls more pages explicitly, so a miss cannot chain
        // request after request through the whole album.
        var searchAutoPages by remember(query, state.localFilter) { mutableIntStateOf(0) }
        LaunchedEffect(
            hasLocalConstraint,
            filtered.size,
            state.items.size,
            state.canLoadMore,
            state.isLoading,
            query,
            state.localFilter,
        ) {
            if (
                hasLocalConstraint &&
                filtered.size < ALBUM_FILTER_MIN_VISIBLE_ITEMS &&
                state.canLoadMore &&
                !state.isLoading &&
                searchAutoPages < ALBUM_SEARCH_AUTO_PAGES
            ) {
                searchAutoPages++
                screenModel.loadMore()
            }
        }
        LaunchedEffect(gridState, state.items.size, filtered.size, query, state.localFilter) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { lastVisible ->
                    if (lastVisible < filtered.size - 6) return@collect
                    if (!hasLocalConstraint) {
                        screenModel.loadMore()
                    } else if (
                        state.canLoadMore &&
                        !state.isLoading &&
                        searchAutoPages < ALBUM_SEARCH_AUTO_PAGES
                    ) {
                        searchAutoPages++
                        screenModel.loadMore()
                    }
                }
        }
        val snackbarHostState = remember { SnackbarHostState() }
        val retryLabel = stringResource(MR.strings.action_retry)
        // Append failures keep the loaded grid: they surface transiently with an explicit
        // retry of the SAME page/cursor (pagination inputs were not advanced).
        LaunchedEffect(state.pageError) {
            state.pageError?.let { message ->
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = retryLabel,
                    duration = SnackbarDuration.Long,
                )
                screenModel.onPageErrorShown()
                if (result == SnackbarResult.ActionPerformed) screenModel.loadMore()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    AppBar(
                        title = albumName,
                        navigateUp = navigator::pop,
                        actions = {
                            // Report fix: save/unsave this album straight from the AppBar.
                            IconButton(onClick = { screenModel.toggleAlbumSaved() }) {
                                Icon(
                                    imageVector = if (state.isAlbumSaved) {
                                        Icons.Filled.Bookmark
                                    } else {
                                        Icons.Outlined.BookmarkBorder
                                    },
                                    contentDescription = stringResource(MR.strings.reels_albums),
                                    tint = if (state.isAlbumSaved) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            // Report fix: start the album as a reels feed from the first video.
                            IconButton(
                                onClick = {
                                    val seedItems = if (filtered.isNotEmpty()) filtered else state.items
                                    if (seedItems.isNotEmpty()) {
                                        ReelsAlbumFeedSeedStore.pending = ReelsAlbumFeedSeed(
                                            albumId = albumId,
                                            items = seedItems,
                                            targetVideoId = null,
                                            nextPage = state.nextPage,
                                            nextCursor = state.nextCursor,
                                            cursorMode = state.cursorMode,
                                            canLoadMore = state.canLoadMore,
                                            sourceId = sourceId,
                                            rawItems = state.items,
                                            localFilter = state.localFilter,
                                        )
                                    }
                                    navigator.push(
                                        ReelsFeedScreen(
                                            sourceId = sourceId,
                                            nicheId = albumId,
                                            nicheName = albumName,
                                        ),
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(MR.strings.reels_album_play_as_feed),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = stringResource(MR.strings.reels_filter_title),
                                    tint = if (state.localFilter.isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = stringResource(MR.strings.reels_album_search),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                    if (showSearch) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(MR.strings.reels_album_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            },
        ) { paddingValues ->
            when {
                state.items.isEmpty() && state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.items.isEmpty() && state.unsupported -> EmptyScreen(
                    stringRes = MR.strings.reels_album_unavailable,
                    modifier = Modifier.fillMaxSize(),
                )
                state.items.isEmpty() && state.error != null -> ReelsErrorState(
                    message = state.error.orEmpty(),
                    onRetry = screenModel::loadMore,
                    modifier = Modifier.padding(paddingValues),
                )
                state.items.isEmpty() -> EmptyScreen(
                    stringRes = MR.strings.reels_feed_empty,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasLocalConstraint) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(MR.strings.reels_album_search_loaded, state.items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { item ->
                        val progress = state.progress[item.id] ?: 0f
                        val watched = item.id in state.watched || progress >= 0.9f
                        AlbumItemCard(
                            item = item,
                            progress = progress,
                            watched = watched,
                            onClick = {
                                ReelsAlbumFeedSeedStore.pending = ReelsAlbumFeedSeed(
                                    albumId = albumId,
                                    items = filtered,
                                    targetVideoId = item.id,
                                    nextPage = state.nextPage,
                                    nextCursor = state.nextCursor,
                                    cursorMode = state.cursorMode,
                                    canLoadMore = state.canLoadMore,
                                    sourceId = sourceId,
                                    rawItems = state.items,
                                    localFilter = state.localFilter,
                                )
                                navigator.push(
                                    ReelsFeedScreen(
                                        sourceId = sourceId,
                                        nicheId = albumId,
                                        nicheName = albumName,
                                        initialVideoId = item.id,
                                        // Report fix: resume the saved watch position of the
                                        // tapped clip inside the album feed.
                                        resumeVideoId = item.id,
                                    ),
                                )
                            },
                            onLongClick = { menuItem = item },
                        )
                    }
                    if (state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                    if (hasLocalConstraint) {
                        if (filtered.isEmpty() && !state.isLoading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(MR.strings.reels_album_search_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        if (state.canLoadMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TextButton(onClick = screenModel::loadMore) {
                                        Text(stringResource(MR.strings.reels_album_search_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        menuItem?.let { item ->
            val watched = item.id in state.watched
            AlertDialog(
                onDismissRequest = { menuItem = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (watched) {
                                screenModel.unmarkWatched(item.id)
                            } else {
                                screenModel.markWatched(item.id)
                            }
                            menuItem = null
                        },
                    ) {
                        Text(
                            stringResource(
                                if (watched) {
                                    MR.strings.reels_album_unmark_watched
                                } else {
                                    MR.strings.reels_album_mark_watched
                                },
                            ),
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { menuItem = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
                title = {
                    Text(text = item.title.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
            )
        }

        if (showFilterSheet) {
            ReelsLocalFilterSheet(
                currentFilter = state.localFilter,
                availableTags = availableTags,
                showWatchFilter = true,
                showSourceFiltersAction = state.isCategoryOrderCapable,
                onOpenSourceFilters = {
                    showFilterSheet = false
                    showSourceFilterDialog = true
                },
                onApply = { next ->
                    screenModel.setLocalFilter(next)
                    showFilterSheet = false
                },
                onReset = {
                    screenModel.resetLocalFilter()
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false },
            )
        }

        if (showSourceFilterDialog) {
            SourceFilterAnimeDialog(
                onDismissRequest = { showSourceFilterDialog = false },
                filters = state.sourceFilters,
                onReset = {
                    screenModel.resetSourceFilters()
                    showSourceFilterDialog = false
                },
                onFilter = {
                    screenModel.applySourceFilters()
                    showSourceFilterDialog = false
                },
                onUpdate = screenModel::setSourceFilters,
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun AlbumItemCard(
        item: ShortVideoItem,
        progress: Float,
        watched: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (watched) {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = stringResource(MR.strings.reels_album_mark_watched),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = item.title.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// F03: pages auto-appended per local-search/filter constraint before the explicit "load more" action.
private const val ALBUM_SEARCH_AUTO_PAGES = 10
private const val ALBUM_FILTER_MIN_VISIBLE_ITEMS = 12
