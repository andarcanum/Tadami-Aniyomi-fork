package eu.kanade.tachiyomi.ui.browse.anime.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.AnimeSourceOptionsDialog
import eu.kanade.presentation.browse.anime.AnimeSourceUiModel
import eu.kanade.presentation.browse.anime.AnimeSourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourcePagerScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.animeSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { AnimeSourcesScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = AYMR.strings.label_anime_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalAnimeSearchScreen()) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navigator.push(AnimeSourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            AnimeSourcesScreen(
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
                                is AnimeSourceUiModel.Header -> {
                                    currentHeaderLang = uiModel.language
                                }
                                is AnimeSourceUiModel.Item -> {
                                    val lang = currentHeaderLang ?: ""
                                    groups.getOrPut(lang) { mutableListOf() }.add(uiModel.source.id)
                                }
                            }
                        }
                        groups.values.firstOrNull { it.contains(source.id) } ?: listOf(source.id)
                    }
                    navigator.push(BrowseAnimeSourcePagerScreen(source.id, sourceIds, listing.query))
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
                searchQuery = state.searchQuery,
                onChangeSearchQuery = screenModel::search,
                onToggleLanguage = screenModel::toggleLanguage,
            )

            state.dialog?.let { dialog ->
                val source = dialog.source
                AnimeSourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        screenModel.togglePin(source)
                        screenModel.closeDialog()
                    },
                    onClickDisable = {
                        screenModel.toggleSource(source)
                        screenModel.closeDialog()
                    },
                    onDismiss = screenModel::closeDialog,
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        AnimeSourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
