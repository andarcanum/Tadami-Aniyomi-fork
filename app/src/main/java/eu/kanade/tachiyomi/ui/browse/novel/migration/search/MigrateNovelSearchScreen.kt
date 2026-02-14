package eu.kanade.tachiyomi.ui.browse.novel.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.GlobalNovelSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen

class MigrateNovelSearchScreen(private val novelId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateNovelSearchScreenModel(novelId = novelId) }
        val state by screenModel.state.collectAsState()

        val dialogScreenModel = rememberScreenModel {
            NovelMigrateSearchScreenDialogScreenModel(
                novelId = novelId,
            )
        }
        val dialogState by dialogScreenModel.state.collectAsState()

        GlobalNovelSearchScreen(
            state = state,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getNovel = { screenModel.getNovel(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = {
                navigator.push(BrowseNovelSourceScreen(it.id, state.searchQuery))
            },
            onClickItem = {
                dialogScreenModel.setDialog(
                    NovelMigrateSearchScreenDialogScreenModel.Dialog.Migrate(it),
                )
            },
            onLongClickItem = { navigator.push(NovelScreen(it.id, true)) },
        )

        when (val dialog = dialogState.dialog) {
            is NovelMigrateSearchScreenDialogScreenModel.Dialog.Migrate -> {
                MigrateNovelDialog(
                    oldNovel = dialogState.novel!!,
                    newNovel = dialog.novel,
                    screenModel = rememberScreenModel { MigrateNovelDialogScreenModel() },
                    onDismissRequest = { dialogScreenModel.setDialog(null) },
                    onClickTitle = {
                        navigator.push(NovelScreen(dialog.novel.id, true))
                    },
                    onPopScreen = {
                        if (navigator.lastItem is NovelScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(NovelScreen(dialog.novel.id, true))
                        } else {
                            navigator.replace(NovelScreen(dialog.novel.id, true))
                        }
                    },
                )
            }

            else -> {}
        }
    }
}
