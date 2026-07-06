package eu.kanade.presentation.more.settings.screen.shikimori

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.more.settings.screen.anixart.AnixartImportScreenModel.SourceChoice
import eu.kanade.tachiyomi.data.anixart.AnixartSourceSearcher
import eu.kanade.tachiyomi.data.shikimori.FetchShikimoriImportEntries
import eu.kanade.tachiyomi.data.shikimori.ImportShikimoriEntries
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartMatchingCoordinator
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.data.shikimori.ShikimoriImportEntry
import tachiyomi.data.shikimori.ShikimoriImportStatus
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ShikimoriImportScreenModel(
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val fetchEntries: FetchShikimoriImportEntries = Injekt.get(),
    private val importEntries: ImportShikimoriEntries = Injekt.get(),
) : StateScreenModel<ShikimoriImportScreenModel.State>(State.Loading) {

    @Immutable
    data class ReviewItem(
        val entry: ShikimoriImportEntry,
        val result: AnixartMatcher.MatchResult,
        val selectedId: Long?,
        val enabled: Boolean,
        val matchedQuery: String?,
        val matchedSourceName: String?,
    )

    sealed interface State {
        data object Loading : State
        data class Error(val messageKey: ErrorKind) : State
        data class PickSources(
            val entries: List<ShikimoriImportEntry>,
            val sources: List<SourceChoice>,
            val categories: List<Category>,
            val statusCategoryIds: Map<ShikimoriImportStatus, Long?>,
            val favoriteCategoryId: Long?,
            val statusFilter: Set<ShikimoriImportStatus>,
            val largeImport: Boolean,
        ) : State
        data class Matching(val current: Int, val total: Int) : State
        data class Review(
            val items: List<ReviewItem>,
            val matchingReport: AnixartMatchingCoordinator.MatchingReport,
            val statusCategoryIds: Map<ShikimoriImportStatus, Long?>,
            val favoriteCategoryId: Long?,
        ) : State
        data class Importing(val current: Int, val total: Int) : State
        data class Done(
            val report: ImportShikimoriEntries.Report,
            val matchingReport: AnixartMatchingCoordinator.MatchingReport,
        ) : State
    }

    enum class ErrorKind { NOT_LOGGED_IN, EMPTY }

    init {
        load()
    }

    private fun load() {
        screenModelScope.launch {
            try {
                val entries = fetchEntries.await()
                if (entries.isEmpty()) {
                    mutableState.update { State.Error(ErrorKind.EMPTY) }
                    return@launch
                }
                val categories = getAnimeCategories.await()
                val sources = sourceManager.getCatalogueSources().map { source ->
                    SourceChoice(
                        id = source.id,
                        name = source.name,
                        selected = false,
                        recommendation = AnixartSourceHints.recommendation(source.name),
                    )
                }
                mutableState.update {
                    State.PickSources(
                        entries = entries,
                        sources = sources,
                        categories = categories,
                        statusCategoryIds = emptyMap(),
                        favoriteCategoryId = null,
                        statusFilter = ShikimoriImportStatus.entries.toSet(),
                        largeImport = entries.size > 100,
                    )
                }
            } catch (_: FetchShikimoriImportEntries.NotLoggedInException) {
                mutableState.update { State.Error(ErrorKind.NOT_LOGGED_IN) }
            } catch (_: Exception) {
                mutableState.update { State.Error(ErrorKind.EMPTY) }
            }
        }
    }

    fun toggleSource(id: Long) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            s.copy(sources = s.sources.map { if (it.id == id) it.copy(selected = !it.selected) else it })
        }
    }

    fun toggleStatusFilter(status: ShikimoriImportStatus) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val updated = s.statusFilter.toMutableSet()
            if (!updated.remove(status)) updated.add(status)
            if (updated.isEmpty()) updated.addAll(ShikimoriImportStatus.entries)
            s.copy(statusFilter = updated)
        }
    }

    fun setCategoryMapping(status: ShikimoriImportStatus, categoryId: Long?) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val updated = s.statusCategoryIds.toMutableMap()
            if (categoryId == null) updated.remove(status) else updated[status] = categoryId
            s.copy(statusCategoryIds = updated)
        }
    }

    fun setFavoriteCategoryMapping(categoryId: Long?) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            s.copy(favoriteCategoryId = categoryId)
        }
    }

    private fun filteredEntries(pick: State.PickSources): List<ShikimoriImportEntry> {
        return pick.entries.filter { entry ->
            val status = ShikimoriImportStatus.fromApi(entry.status)
            status == null || status in pick.statusFilter
        }
    }

    fun startMatching() {
        val current = state.value as? State.PickSources ?: return
        val sourceIds = current.sources.filter { it.selected }.map { it.id }
        if (sourceIds.isEmpty()) return
        val entries = filteredEntries(current)
        if (entries.isEmpty()) return

        val statusCategoryIds = current.statusCategoryIds
        val favoriteCategoryId = current.favoriteCategoryId
        val total = entries.size
        val sourceNames = current.sources.associate { it.id to it.name }

        mutableState.update { State.Matching(0, total) }
        screenModelScope.launch {
            val searcher = AnixartSourceSearcher(sourceManager, sourceIds)
            val searchCache = ConcurrentHashMap<String, List<AnixartMatcher.SearchCandidate>>()
            val semaphore = Semaphore(2)
            val matchedCount = AtomicInteger(0)

            suspend fun cachedSearch(query: String) = searchCache.getOrPut(query) { searcher.search(query) }

            val items = entries.map { entry ->
                async {
                    semaphore.withPermit {
                        val rowMatch = AnixartMatchingCoordinator.matchTitles(
                            candidateTitles = entry.candidateTitles(),
                            searchQueries = entry.searchQueries(),
                            search = { cachedSearch(it) },
                        )
                        val currentMatched = matchedCount.incrementAndGet()
                        mutableState.update { State.Matching(currentMatched, total) }
                        val sourceName = rowMatch.result.best?.candidate?.sourceId?.let { sourceNames[it] }
                        ReviewItem(
                            entry = entry,
                            result = rowMatch.result,
                            selectedId = rowMatch.result.best?.candidate?.id,
                            enabled = rowMatch.result.confidence != AnixartMatcher.Confidence.NO_MATCH,
                            matchedQuery = rowMatch.matchedQuery,
                            matchedSourceName = sourceName,
                        )
                    }
                }
            }.awaitAll()

            val matchingReport = AnixartMatchingCoordinator.summarize(
                items.map { AnixartMatchingCoordinator.RowMatch(it.result, it.matchedQuery) },
            )
            mutableState.update { State.Review(items, matchingReport, statusCategoryIds, favoriteCategoryId) }
        }
    }

    fun setSelection(rowIndex: Int, candidateId: Long?) {
        mutableState.update { s ->
            if (s !is State.Review) return@update s
            s.copy(items = s.items.mapIndexed { i, it -> if (i == rowIndex) it.copy(selectedId = candidateId) else it })
        }
    }

    fun setEnabled(rowIndex: Int, enabled: Boolean) {
        mutableState.update { s ->
            if (s !is State.Review) return@update s
            s.copy(items = s.items.mapIndexed { i, it -> if (i == rowIndex) it.copy(enabled = enabled) else it })
        }
    }

    fun selectedCount(): Int =
        (state.value as? State.Review)?.items?.count { it.enabled && it.selectedId != null } ?: 0

    fun startImport() {
        val review = state.value as? State.Review ?: return
        val actions = review.items.mapNotNull { item ->
            if (!item.enabled || item.selectedId == null) return@mapNotNull null
            val candidate = item.result.ranked.firstOrNull { it.candidate.id == item.selectedId }?.candidate
                ?: return@mapNotNull null
            val status = ShikimoriImportStatus.fromApi(item.entry.status)
            val cats = buildSet {
                status?.let { s -> review.statusCategoryIds[s]?.let(::add) }
            }
            ImportShikimoriEntries.Action(item.entry, candidate, cats)
        }

        mutableState.update { State.Importing(0, actions.size) }
        screenModelScope.launch {
            val report = importEntries.await(actions) { current, total ->
                mutableState.update { State.Importing(current, total) }
            }
            mutableState.update { State.Done(report, review.matchingReport) }
        }
    }
}
