package eu.kanade.tachiyomi.ui.series.anime

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.series.anime.AnimeSeriesAuroraContent
import eu.kanade.presentation.series.components.SeriesCategoryOption
import eu.kanade.presentation.series.components.SeriesCoverDialog
import eu.kanade.presentation.series.components.SeriesCoverEntryOption
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.coroutines.launch
import tachiyomi.domain.series.model.SeriesCoverMode
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AnimeSeriesScreen(val seriesId: Long) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { AnimeSeriesScreenModel(seriesId) }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var showCoverDialog by remember { mutableStateOf(false) }
        val pickImageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                screenModel.setCustomCover(context, uri)
            }
        }

        if (state.isLoading) {
            LoadingScreen()
            return
        }
        if (state.series == null) {
            LaunchedEffect(Unit) {
                navigator.pop()
            }
            LoadingScreen()
            return
        }

        AnimeSeriesAuroraContent(
            state = state,
            onBackClicked = navigator::pop,
            onAnimeClicked = { navigator.push(AnimeScreen(it.id)) },
            onEpisodeClicked = { anime, episode ->
                scope.launch {
                    val playerPreferences: PlayerPreferences = Injekt.get()
                    val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
                    MainActivity.startPlayerActivity(context, anime.id, episode.id, extPlayer)
                }
            },
            onRenameClicked = screenModel::renameSeries,
            onCoverClicked = { showCoverDialog = true },
            categoryOptions = state.categories.map { SeriesCategoryOption(it.id, it.name) },
            onCategoryClicked = screenModel::setSeriesCategory,
            onDeleteClicked = screenModel::deleteSeries,
            onRemoveEntryClicked = screenModel::removeAnimeFromSeries,
            onReorderEntries = screenModel::reorderEntries,
        )

        if (showCoverDialog) {
            SeriesCoverDialog(
                titleEntries = state.series?.entries
                    ?.map { SeriesCoverEntryOption(it.id, it.anime.title) }
                    .orEmpty(),
                currentMode = state.series?.series?.coverMode ?: SeriesCoverMode.AUTO,
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
