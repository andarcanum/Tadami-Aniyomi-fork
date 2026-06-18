package eu.kanade.tachiyomi.ui.libraryUpdateError

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.config.MigrationConfigScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen

class LibraryUpdateErrorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LibraryUpdateErrorScreenModel() }
        val state by screenModel.state.collectAsState()

        LibraryUpdateErrorScreen(
            state = state,
            onTabSelected = screenModel::setSelectedTab,
            onRetryVisibleErrors = screenModel::retryVisibleErrors,
            onClick = { item ->
                when (item.record.media) {
                    LibraryUpdateErrorMedia.Manga -> navigator.push(MangaScreen(item.record.entryId))
                    LibraryUpdateErrorMedia.Anime -> navigator.push(AnimeScreen(item.record.entryId))
                    LibraryUpdateErrorMedia.Novel -> navigator.push(NovelScreen(item.record.entryId))
                }
            },
            onSelectAll = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onMigrateSelected = state.resolveMigrationAction(
                clearSelection = { screenModel.toggleAllSelection(false) },
                navigateToAnimeMigration = { navigator.push(MigrateAnimeSearchScreen(it)) },
                navigateToMangaMigration = { navigator.push(MigrationConfigScreen(it)) },
                navigateToNovelMigration = { navigator.push(MigrateNovelSearchScreen(it)) },
            ),
            onErrorsDelete = {
                if (state.selectionMode) {
                    screenModel.deleteSelected()
                } else {
                    screenModel.clearVisible()
                }
            },
            onErrorDelete = screenModel::delete,
            onErrorSelected = screenModel::toggleSelection,
            navigateUp = navigator::pop,
        )
    }
}

private fun LibraryUpdateErrorScreenState.resolveMigrationAction(
    clearSelection: () -> Unit,
    navigateToAnimeMigration: (Long) -> Unit,
    navigateToMangaMigration: (List<Long>) -> Unit,
    navigateToNovelMigration: (Long) -> Unit,
): (() -> Unit)? {
    val entryIds = selected
        .map { it.record.entryId }
        .distinct()

    return when (selectedMedia) {
        LibraryUpdateErrorMedia.Anime -> entryIds.singleOrNull()?.let { animeId ->
            {
                clearSelection()
                navigateToAnimeMigration(animeId)
            }
        }
        LibraryUpdateErrorMedia.Manga -> entryIds.takeIf { it.isNotEmpty() }?.let { mangaIds ->
            {
                clearSelection()
                navigateToMangaMigration(mangaIds)
            }
        }
        LibraryUpdateErrorMedia.Novel -> entryIds.singleOrNull()?.let { novelId ->
            {
                clearSelection()
                navigateToNovelMigration(novelId)
            }
        }
    }
}
