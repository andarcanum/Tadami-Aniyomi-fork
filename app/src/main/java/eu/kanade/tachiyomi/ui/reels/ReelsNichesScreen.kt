package eu.kanade.tachiyomi.ui.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.ui.reels.components.ReelsErrorState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Category browser (contract v20): a preview grid of the current source's public category
 * directory (AnimeFeedBrowseSource.getBrowseCategories). Tapping a row opens that category's
 * feed as a NICHE-mode ReelsFeedScreen. Mirrors the ReelsFollowsScreen scaffolding.
 */
data class ReelsNichesScreen(
    val sourceId: Long,
) : Screen {

    // Same Voyager key rule as ReelsFollowsScreen: the key includes the payload that
    // distinguishes two instances, so a popped screen disposes its own ScreenModel.
    override val key: String
        get() = "ReelsNichesScreen:$sourceId"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ReelsNichesScreenModel(sourceId = sourceId) }
        val state by model.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.reels_niches),
                    navigateUp = navigator::pop,
                )
            },
        ) { padding ->
            when {
                state.isLoading -> LoadingScreen()
                state.error != null && state.categories.isEmpty() ->
                    ReelsErrorState(message = state.error.orEmpty(), onRetry = model::retry)
                state.categories.isEmpty() ->
                    EmptyScreen(
                        stringRes = MR.strings.reels_niches_empty,
                        modifier = Modifier.fillMaxSize(),
                    )
                else -> {
                    val gridState = rememberLazyGridState()
                    val nearEnd by remember {
                        derivedStateOf {
                            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= state.categories.size - 4
                        }
                    }
                    LaunchedEffect(nearEnd, state.canLoadMore) {
                        if (nearEnd && state.canLoadMore) model.loadMore()
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        state = gridState,
                        contentPadding = padding,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                    ) {
                        items(state.categories, key = { it.id }) { category ->
                            NicheCell(
                                category = category,
                                onClick = {
                                    navigator.push(
                                        ReelsFeedScreen(
                                            sourceId = sourceId,
                                            nicheId = category.id,
                                            nicheName = category.name,
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
}

@Composable
private fun NicheCell(
    category: FeedCategory,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(category.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
        if (category.itemCount != null) {
            Text(
                text = category.itemCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        }
    }
}
