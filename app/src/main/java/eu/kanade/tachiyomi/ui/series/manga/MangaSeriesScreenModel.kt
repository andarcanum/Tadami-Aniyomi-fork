package eu.kanade.tachiyomi.ui.series.manga

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
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
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
import tachiyomi.domain.series.model.SeriesCoverMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class MangaSeriesScreenModel(
    private val seriesId: Long,
    private val getMangaSeriesWithEntries: GetMangaSeriesWithEntries = Injekt.get(),
    private val updateMangaSeries: UpdateMangaSeries = Injekt.get(),
    private val deleteMangaSeries: DeleteMangaSeries = Injekt.get(),
    private val addMangasToSeries: AddMangasToSeries = Injekt.get(),
    private val removeMangaFromSeries: RemoveMangaFromSeries = Injekt.get(),
    private val reorderSeriesEntries: ReorderSeriesEntries = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getVisibleMangaCategories: GetVisibleMangaCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) : StateScreenModel<MangaSeriesScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                getMangaSeriesWithEntries.subscribe(seriesId),
                getVisibleMangaCategories.subscribe(),
            ) { wrapper, categories ->
                wrapper to categories
            }.collectLatest { (wrapper, categories) ->
                if (wrapper == null) {
                    mutableState.update { it.copy(isLoading = false, series = null, categories = categories) }
                    return@collectLatest
                }
                mutableState.update {
                    val customCoverFile = seriesCoverCache.getMangaSeriesCoverFile(seriesId).takeIf { it.exists() }
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

    fun setSeriesCategory(categoryId: Long, moveEntries: Boolean) {
        val wrapper = state.value.series ?: return
        val series = wrapper.series
        if (series.categoryId == categoryId) return
        screenModelScope.launch {
            updateMangaSeries.await(series.copy(categoryId = categoryId))
            if (moveEntries) {
                val targetCategories = if (categoryId == 0L) emptyList() else listOf(categoryId)
                wrapper.entries.forEach { manga ->
                    setMangaCategories.await(manga.id, targetCategories)
                }
            }
        }
    }

    fun setAutomaticCover() {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            updateMangaSeries.await(
                series.copy(
                    coverMode = SeriesCoverMode.AUTO,
                    coverEntryId = null,
                ),
            )
        }
    }

    fun setEntryCover(mangaId: Long) {
        val current = state.value.series ?: return
        if (current.entries.none { it.id == mangaId }) return
        screenModelScope.launch {
            updateMangaSeries.await(
                current.series.copy(
                    coverMode = SeriesCoverMode.ENTRY,
                    coverEntryId = mangaId,
                ),
            )
        }
    }

    fun setCustomCover(context: Context, uri: Uri) {
        val series = state.value.series?.series ?: return
        screenModelScope.launch {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val file = seriesCoverCache.getMangaSeriesCoverFile(series.id)
                seriesCoverCache.setMangaSeriesCoverToCache(series.id, input)
                updateMangaSeries.await(
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
            seriesCoverCache.deleteMangaSeriesCover(series.id)
            updateMangaSeries.await(
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
        val series: LibraryMangaSeries? = null,
        val entryIds: Map<Long, Long> = emptyMap(),
        val chapters: List<Pair<LibraryManga, List<Chapter>>> = emptyList(),
        val categories: List<Category> = emptyList(),
        val hasCustomCover: Boolean = false,
        val customCoverFile: File? = null,
        val searchQuery: String? = null,
    )
}
