package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.MigrateAnimeSearchScreen
import eu.kanade.presentation.browse.openSecretHallIfNeeded
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.list.AnimeMigrationListScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen

class MigrateAnimeSearchScreen(private val animeId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateAnimeSearchScreenModel(animeId = animeId) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        val dialogScreenModel = rememberScreenModel {
            AnimeMigrateSearchScreenDialogScreenModel(
                animeId = animeId,
            )
        }
        val dialogState by dialogScreenModel.state.collectAsStateWithLifecycle()

        MigrateAnimeSearchScreen(
            state = state,
            fromSourceId = dialogState.anime?.source,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { enteredQuery ->
                if (!openSecretHallIfNeeded(navigator, enteredQuery)) {
                    screenModel.search()
                }
            },
            getAnime = { screenModel.getAnime(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onChangeLanguageFilter = screenModel::setLanguageFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = {
                // BMG-9: dialogState.anime loads asynchronously - `!!` crashed on a click
                // before init completed (or after the entry was deleted).
                dialogState.anime?.let { oldAnime ->
                    navigator.push(
                        AnimeSourceSearchScreen(oldAnime, it.id, state.searchQuery),
                    )
                }
            },
            onClickItem = {
                val migrationListScreen = navigator.items
                    .filterIsInstance<AnimeMigrationListScreen>()
                    .lastOrNull()
                if (migrationListScreen != null) {
                    migrationListScreen.addMatchOverride(current = animeId, target = it.id)
                    navigator.popUntil { screen -> screen is AnimeMigrationListScreen }
                } else {
                    dialogScreenModel.setDialog(
                        (AnimeMigrateSearchScreenDialogScreenModel.Dialog.Migrate(it)),
                    )
                }
            },
            onLongClickItem = { navigator.push(AnimeScreen(it.id, true)) },
        )

        when (val dialog = dialogState.dialog) {
            is AnimeMigrateSearchScreenDialogScreenModel.Dialog.Migrate -> {
                // BMG-9: the dialog can be set before the async init loaded dialogState.anime.
                val oldAnime = dialogState.anime
                if (oldAnime != null) {
                    MigrateAnimeDialog(
                        oldAnime = oldAnime,
                        newAnime = dialog.anime,
                        screenModel = rememberScreenModel { MigrateAnimeDialogScreenModel() },
                        onDismissRequest = { dialogScreenModel.setDialog(null) },
                        onClickTitle = {
                            navigator.push(AnimeScreen(dialog.anime.id, true))
                        },
                        onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(oldAnime, dialog.anime)) },
                        onPopScreen = {
                            if (navigator.lastItem is AnimeScreen) {
                                val lastItem = navigator.lastItem
                                navigator.popUntil { navigator.items.contains(lastItem) }
                                navigator.push(AnimeScreen(dialog.anime.id))
                            } else {
                                navigator.replace(AnimeScreen(dialog.anime.id))
                            }
                        },
                    )
                }
            }
            else -> {}
        }
    }
}
