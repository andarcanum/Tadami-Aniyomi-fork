package eu.kanade.tachiyomi.ui.series.manga

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
import eu.kanade.presentation.series.components.SeriesCoverDialog
import eu.kanade.presentation.series.components.SeriesCoverEntryOption
import eu.kanade.presentation.series.manga.MangaSeriesAuroraContent
import eu.kanade.presentation.series.components.SeriesCategoryOption
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
            onCoverClicked = { showCoverDialog = true },
            categoryOptions = state.categories.map { SeriesCategoryOption(it.id, it.name) },
            onCategoryClicked = screenModel::setSeriesCategory,
            onDeleteClicked = screenModel::deleteSeries,
            onRemoveEntryClicked = screenModel::removeMangaFromSeries,
            onReorderEntries = screenModel::reorderEntries,
        )

        if (showCoverDialog) {
            SeriesCoverDialog(
                titleEntries = state.series?.entries
                    ?.map { SeriesCoverEntryOption(it.id, it.manga.title) }
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
