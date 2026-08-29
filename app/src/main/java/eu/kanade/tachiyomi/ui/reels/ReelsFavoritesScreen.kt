package eu.kanade.tachiyomi.ui.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.components.AppBar
import kotlinx.coroutines.launch
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private enum class FavoritesSort { DateDesc, DateAsc, Source }

class ReelsFavoritesScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ReelsFavoritesScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val removedMessage = stringResource(MR.strings.reels_favorites_removed_snackbar)
        val undoLabel = stringResource(MR.strings.action_undo)
        var sort by remember { mutableStateOf(FavoritesSort.DateDesc) }

        val sorted = remember(state.favorites, sort) {
            when (sort) {
                FavoritesSort.DateDesc -> state.favorites.sortedByDescending { it.addedAt }
                FavoritesSort.DateAsc -> state.favorites.sortedBy { it.addedAt }
                FavoritesSort.Source -> state.favorites.sortedBy { it.sourceId }
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.reels_favorites_title),
                    navigateUp = navigator::pop,
                    actions = {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.reels_sort_newest)) },
                                onClick = {
                                    sort = FavoritesSort.DateDesc
                                    menuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.reels_sort_oldest)) },
                                onClick = {
                                    sort = FavoritesSort.DateAsc
                                    menuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.reels_sort_source)) },
                                onClick = {
                                    sort = FavoritesSort.Source
                                    menuOpen = false
                                },
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            if (sorted.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.reels_favorites_empty),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.reels_favorites_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(sorted, key = { it.videoId + ":" + it.sourceId }) { fav ->
                        SwipeToRemoveFavorite(
                            favorite = fav,
                            sourceName = state.sourceNames[fav.sourceId],
                            onRemove = {
                                screenModel.removeFavorite(fav)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = removedMessage,
                                        actionLabel = undoLabel,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        screenModel.restoreFavorite(fav)
                                    }
                                }
                            },
                            onClick = {
                                navigator.push(
                                    ReelsFeedScreen(
                                        sourceId = fav.sourceId,
                                        initialFavorites = sorted,
                                        initialPage = sorted.indexOf(fav),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToRemoveFavorite(
    favorite: ReelsFavorite,
    sourceName: String?,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onRemove()
            value != SwipeToDismissBoxValue.Settled
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        // One-directional removal (end-to-start only): the accidental edge-swipe from the
        // left edge is the navigation gesture and must not delete favorites.
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        FavoriteCell(favorite = favorite, sourceName = sourceName, onClick = onClick)
    }
}

@Composable
private fun FavoriteCell(
    favorite: ReelsFavorite,
    sourceName: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        val poster = favorite.posterUrlVertical ?: favorite.posterUrl
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(poster)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        Text(
            text = favorite.author?.let { "@$it" }
                ?: favorite.title
                ?: stringResource(MR.strings.reels_default_video_title),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
        if (sourceName != null) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        } else {
            // The source plugin was uninstalled: show an explicit fallback instead of
            // silently dropping the line (approved design).
            Text(
                text = stringResource(MR.strings.reels_unknown_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        }
    }
}
