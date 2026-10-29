package eu.kanade.tachiyomi.ui.series.anime

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.anime.interactor.AddAnimesToSeries
import tachiyomi.domain.series.anime.interactor.DeleteAnimeSeries
import tachiyomi.domain.series.anime.interactor.GetAnimeSeriesWithEntries
import tachiyomi.domain.series.anime.interactor.RemoveAnimeFromSeries
import tachiyomi.domain.series.anime.interactor.ReorderSeriesEntries
import tachiyomi.domain.series.anime.interactor.UpdateAnimeSeries
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import tachiyomi.domain.series.model.SeriesCoverMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class AnimeSeriesScreenModel(
    private val seriesId: Long,
    private val getAnimeSeriesWithEntries: GetAnimeSeriesWithEntries = Injekt.get(),
    private val updateAnimeSeries: UpdateAnimeSeries = Injekt.get(),
    private val deleteAnimeSeries: DeleteAnimeSeries = Injekt.get(),
    private val addAnimesToSeries: AddAnimesToSeries = Injekt.get(),
    private val removeAnimeFromSeries: RemoveAnimeFromSeries = Injekt.get(),
    private val reorderSeriesEntries: ReorderSeriesEntries = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getVisibleAnimeCategories: GetVisibleAnimeCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) : ScreenModel {

    private val customCoverState = MutableStateFlow(
        seriesCoverCache.getAnimeSeriesCoverFile(seriesId).takeIf { it.exists() },
    )

    private val episodesState: StateFlow<List<Pair<LibraryAnime, List<Episode>>>> = combine(
        getAnimeSeriesWithEntries.subscribe(seriesId),
        getVisibleAnimeCategories.subscribe(),
    ) { wrapper, _ -> wrapper }
        .filterNotNull()
        .flatMapLatest { wrapper ->
            flow {
                emit(
                    wrapper.series.entries.map { entry ->
                        entry to getEpisodesByAnimeId.await(entry.id)
                            .sortedWith(
                                compareBy<Episode> { it.episodeNumber }
                                    .thenBy { it.sourceOrder }
                                    .thenBy { it.id },
                            )
                    },
                )
            }
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state: StateFlow<State> = combine(
        getAnimeSeriesWithEntries.subscribe(seriesId),
        getVisibleAnimeCategories.subscribe(),
        episodesState,
        customCoverState,
    ) { wrapper, categories, episodes, customCoverFile ->
        if (wrapper == null) {
            State(isLoading = false, series = null, categories = categories, episodes = episodes)
        } else {
            State(
                isLoading = false,
                series = wrapper.series,
                entryIds = wrapper.entryIds,
                categories = categories,
                episodes = episodes,
                hasCustomCover = customCoverFile?.exists() == true,
                customCoverFile = customCoverFile,
            )
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

    fun renameSeries(newTitle: String) {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            updateAnimeSeries.await(series.copy(title = newTitle))
        }
    }

    fun deleteSeries() {
        screenModelScope.launch {
            deleteAnimeSeries.await(seriesId)
        }
    }

    fun removeAnimeFromSeries(animeId: Long) {
        screenModelScope.launch {
            removeAnimeFromSeries.await(animeId)
        }
    }

    fun reorderEntries(animeIds: List<Long>) {
        val entryIds = state.value.entryIds
        screenModelScope.launch {
            val entries = buildAnimeSeriesEntries(seriesId, animeIds, entryIds)
            reorderSeriesEntries.await(entries)
        }
    }

    fun setSeriesCategory(categoryId: Long, moveEntries: Boolean) {
        val wrapper = state.value.series ?: return
        val series = wrapper.series
        if (series.categoryId == categoryId) return
        screenModelScope.launch {
            updateAnimeSeries.await(series.copy(categoryId = categoryId))
            if (moveEntries) {
                val targetCategories = if (categoryId == 0L) emptyList() else listOf(categoryId)
                wrapper.entries.forEach { anime ->
                    setAnimeCategories.await(anime.id, targetCategories)
                }
            }
        }
    }

    fun setAutomaticCover() {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            updateAnimeSeries.await(
                series.copy(
                    coverMode = SeriesCoverMode.AUTO,
                    coverEntryId = null,
                ),
            )
        }
    }

    fun setEntryCover(animeId: Long) {
        val current = state.value.series ?: return
        if (current.entries.none { it.id == animeId }) return
        screenModelScope.launch {
            updateAnimeSeries.await(
                current.series.copy(
                    coverMode = SeriesCoverMode.ENTRY,
                    coverEntryId = animeId,
                ),
            )
        }
    }

    fun setCustomCover(context: Context, uri: Uri) {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val file = seriesCoverCache.getAnimeSeriesCoverFile(series.id)
                seriesCoverCache.setAnimeSeriesCoverToCache(series.id, input)
                updateAnimeSeries.await(
                    series.copy(
                        coverMode = SeriesCoverMode.CUSTOM,
                        coverEntryId = null,
                        coverLastModified = System.currentTimeMillis(),
                    ),
                )
                customCoverState.update { file }
            }
        }
    }

    fun deleteCustomCover() {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            seriesCoverCache.deleteAnimeSeriesCover(series.id)
            updateAnimeSeries.await(
                series.copy(
                    coverMode = SeriesCoverMode.AUTO,
                    coverEntryId = null,
                ),
            )
            customCoverState.update { null }
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val series: LibraryAnimeSeries? = null,
        val entryIds: Map<Long, Long> = emptyMap(),
        val episodes: List<Pair<LibraryAnime, List<Episode>>> = emptyList(),
        val categories: List<Category> = emptyList(),
        val hasCustomCover: Boolean = false,
        val customCoverFile: File? = null,
        val searchQuery: String? = null,
    )
}
