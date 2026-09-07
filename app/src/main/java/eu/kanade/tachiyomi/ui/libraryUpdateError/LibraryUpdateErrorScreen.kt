package eu.kanade.tachiyomi.ui.libraryUpdateError

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.ui.browse.anime.migration.config.AnimeMigrationConfigScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.config.MigrationConfigScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.config.NovelMigrationConfigScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class LibraryUpdateErrorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { LibraryUpdateErrorScreenModel() }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        LibraryUpdateErrorScreen(
            state = state,
            onTabSelected = screenModel::setSelectedTab,
            onRetryVisibleErrors = {
                // I17: a retry blocked by an already running/enqueued manual job of this media
                // used to be silently dropped - surface the reason instead.
                // I15: retryVisibleErrors queries WorkManager (blocking) - off MAIN.
                scope.launch {
                    if (!screenModel.retryVisibleErrors()) {
                        context.toast(context.stringResource(MR.strings.update_already_running))
                    }
                }
            },
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
                navigateToAnimeMigration = { navigator.push(AnimeMigrationConfigScreen(it)) },
                navigateToMangaMigration = { navigator.push(MigrationConfigScreen(it)) },
                navigateToNovelMigration = { navigator.push(NovelMigrationConfigScreen(it)) },
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
    navigateToAnimeMigration: (List<Long>) -> Unit,
    navigateToMangaMigration: (List<Long>) -> Unit,
    navigateToNovelMigration: (List<Long>) -> Unit,
): (() -> Unit)? {
    val entryIds = selected
        .map { it.record.entryId }
        .distinct()

    return when (selectedMedia) {
        LibraryUpdateErrorMedia.Anime -> entryIds.takeIf { it.isNotEmpty() }?.let { animeIds ->
            {
                clearSelection()
                navigateToAnimeMigration(animeIds)
            }
        }
        LibraryUpdateErrorMedia.Manga -> entryIds.takeIf { it.isNotEmpty() }?.let { mangaIds ->
            {
                clearSelection()
                navigateToMangaMigration(mangaIds)
            }
        }
        LibraryUpdateErrorMedia.Novel -> entryIds.takeIf { it.isNotEmpty() }?.let { novelIds ->
            {
                clearSelection()
                navigateToNovelMigration(novelIds)
            }
        }
    }
}
