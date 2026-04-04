package eu.kanade.tachiyomi.ui.browse.manga.migration.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.manga.components.MangaSourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.manga.migration.list.MigrationListScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import kotlinx.collections.immutable.persistentListOf
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.shouldExpandFAB
import tachiyomi.presentation.core.util.plus
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

class MigrationConfigScreen(private val mangaIds: Collection<Long>) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrationConfigScreenModel() }
        val state by screenModel.state.collectAsState()
        var migrationSheetOpen by rememberSaveable { mutableStateOf(false) }

        fun continueMigration(openSheet: Boolean, extraSearchQuery: String?) {
            val mangaId = mangaIds.singleOrNull()
            if (mangaId != null) {
                navigator.replace(MigrateMangaSearchScreen(mangaId))
                return
            }
            if (openSheet) {
                migrationSheetOpen = true
                return
            }
            navigator.replace(
                MigrationListScreen(
                    mangaIds = mangaIds,
                    sourceIds = state.selectedSourceIds,
                    extraSearchQuery = extraSearchQuery,
                ),
            )
        }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val lazyListState = rememberLazyListState()
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = null,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_all),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = { screenModel.toggleSelection(MigrationConfigScreenModel.SelectionConfig.All) },
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_inverse),
                                    icon = Icons.Outlined.Deselect,
                                    onClick = { screenModel.toggleSelection(MigrationConfigScreenModel.SelectionConfig.None) },
                                ),
                            ),
                        )
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(AYMR.strings.action_continue)) },
                    icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                    onClick = {
                        screenModel.saveSources()
                        continueMigration(openSheet = true, extraSearchQuery = null)
                    },
                    expanded = lazyListState.shouldExpandFAB(),
                )
            },
        ) { contentPadding ->
            FastScrollLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                if (state.selectedSources.isNotEmpty()) {
                    item("selected-header") {
                        Text(
                            text = stringResource(MR.strings.migration_selected_sources),
                            style = MaterialTheme.typography.header,
                            modifier = Modifier.padding(MaterialTheme.padding.medium),
                        )
                    }
                    itemsIndexed(
                        items = state.selectedSources,
                        key = { _, item -> "selected-${item.id}" },
                    ) { index, item ->
                        SourceItemContainer(
                            firstItem = index == 0,
                            lastItem = index == state.selectedSources.lastIndex,
                            source = item,
                            onClick = { screenModel.toggleSelection(item.id) },
                        )
                    }
                }

                if (state.availableSources.isNotEmpty()) {
                    item("available-header") {
                        Text(
                            text = stringResource(MR.strings.migration_available_sources),
                            style = MaterialTheme.typography.header,
                            modifier = Modifier.padding(MaterialTheme.padding.medium),
                        )
                    }
                    itemsIndexed(
                        items = state.availableSources,
                        key = { _, item -> "available-${item.id}" },
                    ) { index, item ->
                        SourceItemContainer(
                            firstItem = index == 0,
                            lastItem = index == state.availableSources.lastIndex,
                            source = item,
                            onClick = { screenModel.toggleSelection(item.id) },
                        )
                    }
                }
            }
        }

        if (migrationSheetOpen) {
            MigrationConfigScreenSheet(
                preferences = screenModel.sourcePreferences,
                onDismissRequest = { migrationSheetOpen = false },
                onStartMigration = { extraSearchQuery ->
                    migrationSheetOpen = false
                    continueMigration(openSheet = false, extraSearchQuery = extraSearchQuery)
                },
            )
        }
    }

    @Composable
    private fun LazyItemScope.SourceItemContainer(
        firstItem: Boolean,
        lastItem: Boolean,
        source: MigrationConfigScreenModel.MigrationSource,
        onClick: () -> Unit,
    ) {
        val shape = remember(firstItem, lastItem) {
            val top = if (firstItem) 12.dp else 0.dp
            val bottom = if (lastItem) 12.dp else 0.dp
            RoundedCornerShape(top, top, bottom, bottom)
        }

        ElevatedCard(
            shape = shape,
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MangaSourceIcon(source = source.source)
                        Text(
                            text = source.source.name.ifBlank { source.id.toString() },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                trailingContent = {
                    Checkbox(checked = source.isSelected, onCheckedChange = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onClick),
            )
        }

        if (!lastItem) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium))
        }
    }
}
