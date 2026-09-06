package eu.kanade.tachiyomi.ui.browse.manga.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.manga.MangaSourceOptionsDialog
import eu.kanade.presentation.browse.manga.MangaSourceUiModel
import eu.kanade.presentation.browse.manga.MangaSourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourcePagerScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.util.system.LAST_USED_KEY
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.mangaSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { MangaSourcesScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = AYMR.strings.label_manga_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Filled.TravelExplore,
                onClick = { navigator.push(GlobalMangaSearchScreen()) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Filled.FilterList,
                onClick = { navigator.push(MangaSourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            MangaSourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    val sourceIds = if (state.pinnedItems.any { it.id == source.id } && !state.verticalPinnedLayout) {
                        state.pinnedItems.map { it.id }
                    } else {
                        var currentHeaderLang: String? = null
                        val groups = mutableMapOf<String, MutableList<Long>>()
                        state.items.forEach { uiModel ->
                            when (uiModel) {
                                is MangaSourceUiModel.Header -> {
                                    currentHeaderLang = uiModel.language
                                }
                                is MangaSourceUiModel.Item -> {
                                    val lang = currentHeaderLang ?: ""
                                    groups.getOrPut(lang) { mutableListOf() }.add(uiModel.source.id)
                                }
                            }
                        }
                        // BRM-15: a last-used source lives in the cross-language LAST_USED
                        // group (sorted first) - the pager used to get that mixed group as its
                        // swipe neighbors. Prefer the source's own LANGUAGE group.
                        val foundEntry = groups.entries.firstOrNull { it.value.contains(source.id) }
                        when {
                            foundEntry == null -> listOf(source.id)
                            foundEntry.key == LAST_USED_KEY -> groups[source.lang] ?: listOf(source.id)
                            else -> foundEntry.value
                        }
                    }
                    navigator.push(BrowseMangaSourcePagerScreen(source.id, sourceIds, listing.query))
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
                searchQuery = state.searchQuery,
                onChangeSearchQuery = screenModel::search,
                onToggleLanguage = screenModel::toggleLanguage,
            )

            state.dialog?.let { dialog ->
                val source = dialog.source
                MangaSourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        screenModel.togglePin(source)
                        screenModel.closeDialog()
                    },
                    onClickDisable = {
                        screenModel.toggleSource(source)
                        screenModel.closeDialog()
                    },
                    onClickToggleDataSaver = {
                        screenModel.toggleExcludeFromMangaDataSaver(source)
                        screenModel.closeDialog()
                    }.takeIf { state.dataSaverEnabled },
                    onDismiss = screenModel::closeDialog,
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        MangaSourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
