package eu.kanade.tachiyomi.ui.series.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.series.manga.MangaSeriesAuroraContent
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import tachiyomi.presentation.core.screens.LoadingScreen

data class MangaSeriesScreen(val seriesId: Long) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { MangaSeriesScreenModel(seriesId) }
        val state by screenModel.state.collectAsState()

        if (state.isLoading || state.series == null) {
            LoadingScreen()
            return
        }

        MangaSeriesAuroraContent(
            state = state,
            onBackClicked = navigator::pop,
            onMangaClicked = { navigator.push(MangaScreen(it.id)) },
            onChapterClicked = { manga, chapter ->
                context.startActivity(
                    ReaderActivity.newIntent(context, manga.id, chapter.id, seriesId),
                )
            },
            onRenameClicked = screenModel::renameSeries,
            onDeleteClicked = screenModel::deleteSeries,
            onRemoveEntryClicked = screenModel::removeMangaFromSeries,
            onReorderEntries = screenModel::reorderEntries,
        )
    }
}
