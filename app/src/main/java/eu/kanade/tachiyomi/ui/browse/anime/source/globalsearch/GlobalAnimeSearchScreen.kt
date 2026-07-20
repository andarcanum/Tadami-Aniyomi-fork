package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.anime.GlobalAnimeSearchScreen
import eu.kanade.presentation.browse.isSecretHallQuery
import eu.kanade.presentation.browse.openSecretHallIfNeeded
import eu.kanade.presentation.components.MeltdownInitiationHost
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class GlobalAnimeSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        if (isSecretHallQuery(searchQuery)) {
            LaunchedEffect(searchQuery) {
                openSecretHallIfNeeded(navigator, searchQuery)
            }
            LoadingScreen()
            return
        }

        val screenModel = rememberScreenModel {
            GlobalAnimeSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        var showSingleLoadingScreen by remember {
            mutableStateOf(
                searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1,
            )
        }

        // Шаг 1 пасхалки: триггер "third impact" / "третий удар".
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        var meltdownTriggered by remember { mutableStateOf(false) }

        MeltdownInitiationHost(
            triggered = meltdownTriggered,
            onAcknowledged = {
                meltdownTriggered = false
                uiPreferences.meltdownStage().set(1)
            },
            onDismiss = {
                meltdownTriggered = false
            },
        ) {
            if (showSingleLoadingScreen) {
                LoadingScreen()

                LaunchedEffect(state.items) {
                    when (val result = state.items.values.singleOrNull()) {
                        AnimeSearchItemResult.Loading -> return@LaunchedEffect
                        is AnimeSearchItemResult.Success -> {
                            val anime = result.result.singleOrNull()
                            if (anime != null) {
                                navigator.replace(AnimeScreen(anime.id, true))
                            } else {
                                showSingleLoadingScreen = false
                            }
                        }
                        else -> showSingleLoadingScreen = false
                    }
                }
            } else {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    GlobalAnimeSearchScreen(
                        state = state,
                        navigateUp = navigator::pop,
                        onChangeSearchQuery = screenModel::updateSearchQuery,
                        onSearch = { enteredQuery ->
                            val trimmed = enteredQuery.trim().lowercase()
                            if (trimmed == "third impact" || trimmed == "третий удар") {
                                meltdownTriggered = true
                            }
                            if (!openSecretHallIfNeeded(navigator, enteredQuery)) {
                                screenModel.search()
                            }
                        },
                        getAnime = { screenModel.getAnime(it) },
                        onChangeSearchFilter = screenModel::setSourceFilter,
                        onToggleResults = screenModel::toggleFilterResults,
                        onClickSource = {
                            navigator.push(BrowseAnimeSourceScreen(it.id, state.searchQuery ?: ""))
                        },
                        onClickItem = { navigator.push(AnimeScreen(it.id, true)) },
                        onLongClickItem = { navigator.push(AnimeScreen(it.id, true)) },
                    )
                }
            }
        }
    }
}
