package eu.kanade.tachiyomi.ui.series.novel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.series.components.SeriesCategoryOption
import eu.kanade.presentation.series.components.SeriesCoverDialog
import eu.kanade.presentation.series.components.SeriesCoverEntryOption
import eu.kanade.presentation.series.novel.NovelSeriesAuroraContent
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.series.novel.NovelSeriesScreenModel
import tachiyomi.presentation.core.screens.LoadingScreen

data class NovelSeriesScreen(val seriesId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { NovelSeriesScreenModel(seriesId) }
        val state by screenModel.state.collectAsState()
        var showCoverDialog by remember { mutableStateOf(false) }
        val pickImageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                screenModel.setCustomCover(context, uri)
            }
        }

        if (state.isLoading || state.series == null) {
            LoadingScreen()
            return
        }

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
            onCoverClicked = { showCoverDialog = true },
            categoryOptions = state.categories.map { SeriesCategoryOption(it.id, it.name) },
            onCategoryClicked = screenModel::setSeriesCategory,
            onDeleteClicked = screenModel::deleteSeries,
            onRemoveEntryClicked = screenModel::removeNovelFromSeries,
            onReorderEntries = screenModel::reorderEntries,
        )

        if (showCoverDialog) {
            SeriesCoverDialog(
                titleEntries = state.series?.entries
                    ?.map { SeriesCoverEntryOption(it.id, it.novel.title) }
                    .orEmpty(),
                currentMode = state.series?.series?.coverMode ?: tachiyomi.domain.series.model.SeriesCoverMode.AUTO,
                selectedEntryId = state.series?.series?.coverEntryId,
                hasCustomCover = state.hasCustomCover,
                onDismissRequest = { showCoverDialog = false },
                onSetAutomatic = screenModel::setAutomaticCover,
                onPickCustom = { pickImageLauncher.launch("image/*") },
                onDeleteCustom = screenModel::deleteCustomCover.takeIf { state.hasCustomCover },
                onSelectEntry = screenModel::setEntryCover,
            )
        }
    }
}
