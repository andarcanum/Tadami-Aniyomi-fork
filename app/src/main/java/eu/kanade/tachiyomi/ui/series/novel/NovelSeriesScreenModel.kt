package eu.kanade.tachiyomi.ui.series.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.interactor.AddNovelsToSeries
import tachiyomi.domain.series.novel.interactor.DeleteNovelSeries
import tachiyomi.domain.series.novel.interactor.GetNovelSeriesWithEntries
import tachiyomi.domain.series.novel.interactor.RemoveNovelFromSeries
import tachiyomi.domain.series.novel.interactor.ReorderSeriesEntries
import tachiyomi.domain.series.novel.interactor.UpdateNovelSeries
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelSeriesScreenModel(
    private val seriesId: Long,
    private val getNovelSeriesWithEntries: GetNovelSeriesWithEntries = Injekt.get(),
    private val updateNovelSeries: UpdateNovelSeries = Injekt.get(),
    private val deleteNovelSeries: DeleteNovelSeries = Injekt.get(),
    private val addNovelsToSeries: AddNovelsToSeries = Injekt.get(),
    private val removeNovelFromSeries: RemoveNovelFromSeries = Injekt.get(),
    private val reorderSeriesEntries: ReorderSeriesEntries = Injekt.get(),
    private val getNovelChapters: GetNovelChapters = Injekt.get(),
) : StateScreenModel<NovelSeriesScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            getNovelSeriesWithEntries.subscribe(seriesId).collectLatest { wrapper ->
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

    private fun fetchChapters(entries: List<LibraryNovel>) {
        screenModelScope.launch {
            val chapters = entries.map { entry ->
                entry to getNovelChapters.await(entry.id)
            }
            mutableState.update { it.copy(chapters = chapters) }
        }
    }

    fun renameSeries(newTitle: String) {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            updateNovelSeries.await(series.copy(title = newTitle))
        }
    }

    fun deleteSeries() {
        screenModelScope.launch {
            deleteNovelSeries.await(seriesId)
        }
    }

    fun removeNovelFromSeries(novelId: Long) {
        screenModelScope.launch {
            removeNovelFromSeries.await(novelId)
        }
    }

    fun reorderEntries(novelIds: List<Long>) {
        val entryIds = state.value.entryIds
        screenModelScope.launch {
            val entries = buildNovelSeriesEntries(seriesId, novelIds, entryIds)
            reorderSeriesEntries.await(entries)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val series: LibraryNovelSeries? = null,
        val entryIds: Map<Long, Long> = emptyMap(),
        val chapters: List<Pair<LibraryNovel, List<NovelChapter>>> = emptyList(),
        val searchQuery: String? = null,
    )
}
