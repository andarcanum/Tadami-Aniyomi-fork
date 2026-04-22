package eu.kanade.tachiyomi.ui.series.novel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.category.novel.model.NovelCategory
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
import tachiyomi.domain.series.model.SeriesCoverMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class NovelSeriesScreenModel(
    private val seriesId: Long,
    private val getNovelSeriesWithEntries: GetNovelSeriesWithEntries = Injekt.get(),
    private val updateNovelSeries: UpdateNovelSeries = Injekt.get(),
    private val deleteNovelSeries: DeleteNovelSeries = Injekt.get(),
    private val addNovelsToSeries: AddNovelsToSeries = Injekt.get(),
    private val removeNovelFromSeries: RemoveNovelFromSeries = Injekt.get(),
    private val reorderSeriesEntries: ReorderSeriesEntries = Injekt.get(),
    private val getNovelChapters: GetNovelChapters = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) : StateScreenModel<NovelSeriesScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                getNovelSeriesWithEntries.subscribe(seriesId),
                getNovelCategories.subscribe(),
            ) { wrapper, categories ->
                wrapper to categories
            }.collectLatest { (wrapper, categories) ->
                if (wrapper == null) {
                    mutableState.update { it.copy(isLoading = false, series = null, categories = categories) }
                    return@collectLatest
                }
                mutableState.update {
                    val customCoverFile = seriesCoverCache.getNovelSeriesCoverFile(seriesId).takeIf { it.exists() }
                    it.copy(
                        isLoading = false,
                        series = wrapper.series,
                        entryIds = wrapper.entryIds,
                        categories = categories,
                        hasCustomCover = customCoverFile != null,
                        customCoverFile = customCoverFile,
                    )
                }
                fetchChapters(wrapper.series.entries)
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

    fun setSeriesCategory(categoryId: Long, moveEntries: Boolean) {
        val wrapper = state.value.series ?: return
        val series = wrapper.series
        if (series.categoryId == categoryId) return
        screenModelScope.launch {
            updateNovelSeries.await(series.copy(categoryId = categoryId))
            if (moveEntries) {
                val targetCategories = if (categoryId == 0L) emptyList() else listOf(categoryId)
                wrapper.entries.forEach { novel ->
                    setNovelCategories.await(novel.id, targetCategories)
                }
            }
        }
    }

    fun setAutomaticCover() {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            updateNovelSeries.await(
                series.copy(
                    coverMode = SeriesCoverMode.AUTO,
                    coverEntryId = null,
                ),
            )
        }
    }

    fun setEntryCover(novelId: Long) {
        val current = state.value.series ?: return
        if (current.entries.none { it.id == novelId }) return
        screenModelScope.launch {
            updateNovelSeries.await(
                current.series.copy(
                    coverMode = SeriesCoverMode.ENTRY,
                    coverEntryId = novelId,
                ),
            )
        }
    }

    fun setCustomCover(context: Context, uri: Uri) {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val file = seriesCoverCache.getNovelSeriesCoverFile(series.id)
                seriesCoverCache.setNovelSeriesCoverToCache(series.id, input)
                updateNovelSeries.await(
                    series.copy(
                        coverMode = SeriesCoverMode.CUSTOM,
                        coverEntryId = null,
                        coverLastModified = System.currentTimeMillis(),
                    ),
                )
                mutableState.update { it.copy(hasCustomCover = true, customCoverFile = file) }
            }
        }
    }

    fun deleteCustomCover() {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            seriesCoverCache.deleteNovelSeriesCover(series.id)
            updateNovelSeries.await(
                series.copy(
                    coverMode = SeriesCoverMode.AUTO,
                    coverEntryId = null,
                ),
            )
            mutableState.update { it.copy(hasCustomCover = false, customCoverFile = null) }
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val series: LibraryNovelSeries? = null,
        val entryIds: Map<Long, Long> = emptyMap(),
        val chapters: List<Pair<LibraryNovel, List<NovelChapter>>> = emptyList(),
        val categories: List<NovelCategory> = emptyList(),
        val hasCustomCover: Boolean = false,
        val customCoverFile: File? = null,
        val searchQuery: String? = null,
    )
}
