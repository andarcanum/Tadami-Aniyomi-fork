package eu.kanade.tachiyomi.ui.series.novel

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
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.model.SeriesCoverMode
import tachiyomi.domain.series.novel.interactor.AddNovelsToSeries
import tachiyomi.domain.series.novel.interactor.DeleteNovelSeries
import tachiyomi.domain.series.novel.interactor.GetNovelSeriesWithEntries
import tachiyomi.domain.series.novel.interactor.RemoveNovelFromSeries
import tachiyomi.domain.series.novel.interactor.ReorderSeriesEntries
import tachiyomi.domain.series.novel.interactor.UpdateNovelSeries
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
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
    private val getNovelBookState: tachiyomi.domain.book.novel.interactor.GetNovelBookState = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) : ScreenModel {

    private val customCoverState = MutableStateFlow(
        seriesCoverCache.getNovelSeriesCoverFile(seriesId).takeIf { it.exists() },
    )

    private val chaptersState: StateFlow<List<Pair<LibraryNovel, List<NovelChapter>>>> = combine(
        getNovelSeriesWithEntries.subscribe(seriesId),
        getNovelCategories.subscribe(),
    ) { wrapper, _ -> wrapper }
        .filterNotNull()
        .flatMapLatest { wrapper ->
            flow {
                emit(
                    wrapper.series.entries.map { entry ->
                        entry to getNovelChapters.await(entry.id)
                    },
                )
            }
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Book-mode positions of the series entries, so the reading target resolves a compiled book
    // to its stored chapter instead of the per-chapter heuristic.
    private val bookStatesState: StateFlow<Map<Long, tachiyomi.domain.book.novel.model.NovelBookState>> =
        getNovelSeriesWithEntries.subscribe(seriesId)
            .filterNotNull()
            .flatMapLatest { wrapper ->
                flow {
                    emit(
                        getNovelBookState.awaitAll()
                            .filter { bookState -> wrapper.series.entries.any { it.id == bookState.novelId } }
                            .associateBy { it.novelId },
                    )
                }
            }
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val state: StateFlow<State> = combine(
        getNovelSeriesWithEntries.subscribe(seriesId),
        getNovelCategories.subscribe(),
        chaptersState,
        customCoverState,
        bookStatesState,
    ) { wrapper, categories, chapters, customCoverFile, bookStates ->
        if (wrapper == null) {
            State(isLoading = false, series = null, categories = categories, chapters = chapters)
        } else {
            State(
                isLoading = false,
                series = wrapper.series,
                entryIds = wrapper.entryIds,
                categories = categories,
                chapters = chapters,
                hasCustomCover = customCoverFile?.exists() == true,
                customCoverFile = customCoverFile,
                bookStates = bookStates,
            )
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

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
                customCoverState.update { file }
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
            customCoverState.update { null }
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
        val bookStates: Map<Long, tachiyomi.domain.book.novel.model.NovelBookState> = emptyMap(),
    )
}
