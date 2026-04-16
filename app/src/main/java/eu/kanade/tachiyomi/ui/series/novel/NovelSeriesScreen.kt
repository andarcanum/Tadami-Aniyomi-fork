package eu.kanade.tachiyomi.ui.series.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.series.novel.NovelSeriesAuroraContent
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.series.novel.NovelSeriesScreenModel

data class NovelSeriesScreen(val seriesId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { NovelSeriesScreenModel(seriesId) }
        val state by screenModel.state.collectAsState()

        NovelSeriesAuroraContent(
            state = state,
            onBackClicked = navigator::pop,
            onNovelClicked = { navigator.push(NovelScreen(it.id)) },
            onChapterClicked = { novel, chapter ->
                navigator.push(
                    NovelReaderScreen(
                        chapter.id,
                        sourceId = novel.novel.source,
                        seriesId = seriesId,
                    ),
                )
            },
            onRenameClicked = screenModel::renameSeries,
            onDeleteClicked = screenModel::deleteSeries,
            onRemoveEntryClicked = screenModel::removeNovelFromSeries,
            onReorderEntries = screenModel::reorderEntries,
        )
    }
}
