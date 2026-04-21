package eu.kanade.tachiyomi.ui.series.manga

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.interactor.AddMangasToSeries
import tachiyomi.domain.series.manga.interactor.DeleteMangaSeries
import tachiyomi.domain.series.manga.interactor.GetMangaSeriesWithEntries
import tachiyomi.domain.series.manga.interactor.RemoveMangaFromSeries
import tachiyomi.domain.series.manga.interactor.ReorderSeriesEntries
import tachiyomi.domain.series.manga.interactor.UpdateMangaSeries
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaSeriesScreenModel(
    private val seriesId: Long,
    private val getMangaSeriesWithEntries: GetMangaSeriesWithEntries = Injekt.get(),
    private val updateMangaSeries: UpdateMangaSeries = Injekt.get(),
    private val deleteMangaSeries: DeleteMangaSeries = Injekt.get(),
    private val addMangasToSeries: AddMangasToSeries = Injekt.get(),
    private val removeMangaFromSeries: RemoveMangaFromSeries = Injekt.get(),
    private val reorderSeriesEntries: ReorderSeriesEntries = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
) : StateScreenModel<MangaSeriesScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            getMangaSeriesWithEntries.subscribe(seriesId).collectLatest { wrapper ->
                if (wrapper != null) {
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            series = wrapper.series,
                            entryIds = wrapper.entryIds,
                        )
                    }
                    fetchChapters(wrapper.series.entries)
                } else {
                    mutableState.update { it.copy(isLoading = false, series = null) }
                }
            }
        }
    }

    private fun fetchChapters(entries: List<LibraryManga>) {
        screenModelScope.launch {
            val chapters = entries.map { entry ->
                entry to getChaptersByMangaId.await(entry.id)
                    .sortedWith(
                        compareBy<Chapter> { it.chapterNumber }
                            .thenBy { it.sourceOrder }
                            .thenBy { it.id },
                    )
            }
            mutableState.update { it.copy(chapters = chapters) }
        }
    }

    fun renameSeries(newTitle: String) {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            updateMangaSeries.await(series.copy(title = newTitle))
        }
    }

    fun deleteSeries() {
        screenModelScope.launch {
            deleteMangaSeries.await(seriesId)
        }
    }

    fun removeMangaFromSeries(mangaId: Long) {
        screenModelScope.launch {
            removeMangaFromSeries.await(mangaId)
        }
    }

    fun reorderEntries(mangaIds: List<Long>) {
        val entryIds = state.value.entryIds
        screenModelScope.launch {
            val entries = buildMangaSeriesEntries(seriesId, mangaIds, entryIds)
            reorderSeriesEntries.await(entries)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val series: LibraryMangaSeries? = null,
        val entryIds: Map<Long, Long> = emptyMap(),
        val chapters: List<Pair<LibraryManga, List<Chapter>>> = emptyList(),
        val searchQuery: String? = null,
    )
}
