package eu.kanade.tachiyomi.ui.library.novel

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.library.novel.NovelLibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object NovelLibraryTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = MR.strings.label_library
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(title),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { NovelLibraryScreenModel() }
        val state by screenModel.state.collectAsState()
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val displayMode by libraryPreferences.displayMode().collectAsState()
        val configuration = LocalConfiguration.current
        val columnPreference = remember(configuration.orientation) {
            if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                libraryPreferences.novelLandscapeColumns()
            } else {
                libraryPreferences.novelPortraitColumns()
            }
        }
        val columns by columnPreference.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: () -> Unit = {
            val started = NovelLibraryUpdateJob.startNow(context)
            scope.launch {
                val msgRes = if (started) MR.strings.updating_category else MR.strings.update_already_running
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                LibraryToolbar(
                    hasActiveFilters = state.hasActiveFilters,
                    selectedCount = 0,
                    title = LibraryToolbarTitle(
                        text = stringResource(MR.strings.label_library),
                        numberOfEntries = state.rawItems.size.takeIf { it > 0 },
                    ),
                    onClickUnselectAll = {},
                    onClickSelectAll = {},
                    onClickInvertSelection = {},
                    onClickFilter = screenModel::showSettingsDialog,
                    onClickRefresh = onClickRefresh,
                    onClickGlobalUpdate = onClickRefresh,
                    onClickOpenRandomEntry = {
                        scope.launch {
                            val randomItem = state.items.randomOrNull()
                            if (randomItem != null) {
                                navigator.push(NovelScreen(randomItem.novel.id))
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.information_no_entries_found),
                                )
                            }
                        }
                    },
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = screenModel::search,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.isLibraryEmpty -> {
                    EmptyScreen(
                        stringRes = MR.strings.information_empty_library,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
                else -> {
                    if (displayMode == LibraryDisplayMode.List) {
                        LazyColumn(
                            modifier = Modifier.padding(contentPadding),
                            contentPadding = PaddingValues(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                NovelLibraryListItem(
                                    item = item,
                                    onClick = { navigator.push(NovelScreen(item.novel.id)) },
                                )
                            }
                        }
                    } else {
                        val gridCells = when {
                            columns > 0 -> GridCells.Fixed(columns)
                            displayMode == LibraryDisplayMode.ComfortableGrid -> GridCells.Adaptive(minSize = 180.dp)
                            else -> GridCells.Adaptive(minSize = 140.dp)
                        }

                        LazyVerticalGrid(
                            columns = gridCells,
                            modifier = Modifier.padding(contentPadding),
                            contentPadding = PaddingValues(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                NovelLibraryGridItem(
                                    item = item,
                                    showMetadata = displayMode != LibraryDisplayMode.CoverOnlyGrid,
                                    onClick = { navigator.push(NovelScreen(item.novel.id)) },
                                )
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow().collectLatest { screenModel.search(it) }
        }

        when (state.dialog) {
            NovelLibraryScreenModel.Dialog.Settings -> {
                NovelLibrarySettingsDialog(
                    onDismissRequest = screenModel::closeDialog,
                    screenModel = screenModel,
                )
            }
            null -> {}
        }
    }

    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)
}

@Composable
private fun NovelLibraryGridItem(
    item: LibraryNovel,
    showMetadata: Boolean,
    onClick: () -> Unit,
) {
    val progressText = if (item.totalChapters > 0) {
        "${item.unreadCount}/${item.totalChapters}"
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ItemCover.Book(
                data = item.novel.thumbnailUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
            )
            if (showMetadata) {
                Text(
                    text = item.novel.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                if (progressText != null) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelLibraryListItem(
    item: LibraryNovel,
    onClick: () -> Unit,
) {
    val progressText = if (item.totalChapters > 0) {
        "${item.unreadCount}/${item.totalChapters}"
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            ItemCover.Book(
                data = item.novel.thumbnailUrl,
                modifier = Modifier
                    .height(112.dp)
                    .aspectRatio(0.68f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.novel.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                )
                if (progressText != null) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
