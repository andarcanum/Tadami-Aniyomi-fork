package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.BrowseNovelSourceContent
import eu.kanade.presentation.browse.novel.MissingNovelSourceScreen
import eu.kanade.presentation.browse.novel.components.BrowseNovelSourceToolbar
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import mihon.presentation.core.util.collectAsLazyPagingItems
import kotlinx.coroutines.launch

data class BrowseNovelSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
) : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { BrowseNovelSourceScreenModel(sourceId, listingQuery) }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubNovelSource) {
            MissingNovelSourceScreen(
                source = screenModel.source as StubNovelSource,
                navigateUp = navigateUp,
            )
            return
        }

        Scaffold(
            topBar = { scrollBehavior ->
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                ) {
                    BrowseNovelSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onSearch = screenModel::search,
                        scrollBehavior = scrollBehavior,
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == BrowseNovelSourceScreenModel.Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(BrowseNovelSourceScreenModel.Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((screenModel.source as NovelCatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == BrowseNovelSourceScreenModel.Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(BrowseNovelSourceScreenModel.Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is BrowseNovelSourceScreenModel.Listing.Search,
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.action_filter))
                                },
                            )
                        }
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseNovelSourceContent(
                source = screenModel.source,
                novels = screenModel.novelPagerFlowFlow.collectAsLazyPagingItems(),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding()),
                onNovelClick = { novel ->
                    scope.launch {
                        val novelId = screenModel.openNovel(novel)
                        navigator.push(NovelScreen(novelId))
                    }
                },
            )

            when (state.dialog) {
                BrowseNovelSourceScreenModel.Dialog.Filter -> {
                    SourceFilterNovelDialog(
                        onDismissRequest = { screenModel.setDialog(null) },
                        filters = state.filters,
                        onReset = screenModel::resetFilters,
                        onFilter = { screenModel.search(filters = state.filters) },
                        onUpdate = screenModel::setFilters,
                    )
                }
                null -> Unit
            }
        }
    }
}
