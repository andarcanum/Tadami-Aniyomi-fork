package eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifNovelSourcesLoaded
import eu.kanade.presentation.browse.novel.GlobalNovelSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class GlobalNovelSearchScreen(
    val searchQuery: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifNovelSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            GlobalNovelSearchScreenModel(
                initialQuery = searchQuery,
            )
        }
        val state by screenModel.state.collectAsState()

        GlobalNovelSearchScreen(
            state = state,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            getNovel = { screenModel.getNovel(it) },
            onClickSource = {
                navigator.push(BrowseNovelSourceScreen(it.id, state.searchQuery))
            },
            onClickItem = { navigator.push(NovelScreen(it.id, true)) },
            onLongClickItem = { navigator.push(NovelScreen(it.id, true)) },
        )
    }
}
