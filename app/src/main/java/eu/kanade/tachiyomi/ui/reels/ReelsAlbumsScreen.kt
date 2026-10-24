package eu.kanade.tachiyomi.ui.reels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

/**
 * Albums collection library of one source (device feature, sign-off prototype): saved albums
 * with covers, entry into an album's preview grid, removal, and the add-flow over the source's
 * category catalog (the same directory the niches screen browses).
 */
data class ReelsAlbumsScreen(
    val sourceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ReelsAlbumsScreenModel(sourceId) }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var showAdd by mutableStateOf(false)

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.reels_albums),
                    navigateUp = navigator::pop,
                    actions = {
                        IconButton(onClick = { showAdd = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(MR.strings.reels_album_add),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (state.albums.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.reels_albums_empty,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.albums, key = { it.albumId }) { album ->
                        AlbumRow(
                            album = album,
                            onOpen = {
                                navigator.push(
                                    ReelsAlbumScreen(
                                        sourceId = sourceId,
                                        albumId = album.albumId,
                                        albumName = album.name,
                                    ),
                                )
                            },
                            onRemove = { screenModel.removeAlbum(album) },
                            onPlay = {
                                navigator.push(
                                    ReelsFeedScreen(
                                        sourceId = sourceId,
                                        nicheId = album.albumId,
                                        nicheName = album.name,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }

        if (showAdd) {
            AdaptiveSheet(onDismissRequest = { showAdd = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(MR.strings.reels_album_add),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    when {
                        state.isCatalogLoading -> Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        state.catalogError != null -> Column {
                            Text(text = state.catalogError.orEmpty(), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = screenModel::loadCatalog) {
                                Text(stringResource(MR.strings.action_retry))
                            }
                        }
                        else -> {
                            val savedIds = state.albums.map { it.albumId }.toSet()
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(state.catalog, key = { it.id }) { category ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = category.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            category.itemCount?.let { count ->
                                                Text(
                                                    text = count.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        val saved = category.id in savedIds
                                        IconButton(
                                            onClick = {
                                                if (!saved) screenModel.addAlbum(category)
                                            },
                                        ) {
                                            Icon(
                                                imageVector = if (saved) {
                                                    Icons.Filled.CheckCircle
                                                } else {
                                                    Icons.Outlined.AddCircle
                                                },
                                                contentDescription = stringResource(MR.strings.reels_album_add),
                                                tint = if (saved) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                        }
                                    }
                                }
                                // F04: the catalog is paginated, so the sheet loads the next
                                // page when its footer scrolls into view instead of pretending
                                // the first page is the whole directory.
                                if (state.catalogCanLoadMore || state.catalogPageError != null) {
                                    item(key = "catalog-footer") {
                                        CatalogFooter(
                                            state = state,
                                            onLoadMore = screenModel::loadCatalogMore,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CatalogFooter(state: ReelsAlbumsScreenModel.State, onLoadMore: () -> Unit) {
        LaunchedEffect(state.catalog.size, state.catalogCanLoadMore, state.catalogPageError) {
            // Auto-load only while pages succeed; a failure parks the footer on the retry
            // button so a broken source cannot spin requests.
            if (state.catalogCanLoadMore && state.catalogPageError == null) onLoadMore()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.catalogPageError != null -> {
                    Text(
                        text = state.catalogPageError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onLoadMore) {
                        Text(stringResource(MR.strings.action_retry))
                    }
                }
                state.isCatalogPageLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }

    @Composable
    private fun AlbumRow(album: ReelsAlbum, onOpen: () -> Unit, onPlay: () -> Unit, onRemove: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 72.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Report fix: start the album as a reels feed straight from the saved list.
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(MR.strings.reels_album_play_as_feed),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(MR.strings.reels_album_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
