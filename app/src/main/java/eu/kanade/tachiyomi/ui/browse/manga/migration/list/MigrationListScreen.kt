package eu.kanade.tachiyomi.ui.browse.manga.migration.list

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class MigrationListScreen(
    private val mangaIds: Collection<Long>,
    private val sourceIds: Collection<Long>,
    private val extraSearchQuery: String?,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrationListScreenModel(mangaIds, sourceIds, extraSearchQuery) }
        val state by screenModel.state.collectAsState()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        MigrationListScreenContent(
            items = state.items,
            finishedCount = state.finishedCount,
            migrationComplete = state.migrationComplete,
            isMigrating = state.isMigrating,
            migrationProgress = state.migrationProgress,
            onItemClick = { navigator.push(MangaScreen(it.id)) },
            onSearchManually = { navigator.push(MigrateMangaSearchScreen(it.manga.id)) },
            onSkip = screenModel::removeManga,
            onMigrateNow = { screenModel.migrateNow(it, replace = true) },
            onCopyNow = { screenModel.migrateNow(it, replace = false) },
            onBulkMigrate = screenModel::migrateMangas,
            onBulkCopy = screenModel::copyMangas,
        )

        BackHandler(enabled = true) {
            if (state.isMigrating) {
                screenModel.cancelMigrate()
            }
            navigator.pop()
        }
    }
}
