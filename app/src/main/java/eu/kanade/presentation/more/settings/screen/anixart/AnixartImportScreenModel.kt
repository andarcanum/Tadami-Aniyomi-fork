package eu.kanade.presentation.more.settings.screen.anixart

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.anixart.AnixartImportJob
import eu.kanade.tachiyomi.data.anixart.AnixartSourceSearcher
import eu.kanade.tachiyomi.data.anixart.AnixartTrackerSync
import eu.kanade.tachiyomi.data.anixart.ImportAnixartEntries
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.data.anixart.AnixartCsvParser
import tachiyomi.data.anixart.AnixartImportPlanner
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartMatchingCoordinator
import tachiyomi.data.anixart.AnixartRow
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.data.anixart.AnixartStatus
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives the Anixart import wizard as a small state machine:
 *
 *   PickSources -> (match) -> Review -> (import) -> Done
 */
class AnixartImportScreenModel(
    private val openStream: () -> InputStream,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val importEntries: ImportAnixartEntries = Injekt.get(),
    private val trackerSync: AnixartTrackerSync = Injekt.get(),
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId = Injekt.get(),
) : StateScreenModel<AnixartImportScreenModel.State>(State.Loading) {

    @Immutable
    data class ReviewItem(
        val row: AnixartRow,
        val result: AnixartMatcher.MatchResult,
        val selectedId: Long?,
        val enabled: Boolean,
        val matchedQuery: String?,
        val matchedSourceName: String?,
    )

    @Immutable
    data class PreflightInfo(
        val totalRows: Int,
        val missingOriginalCount: Int,
        val largeImport: Boolean,
    )

    sealed interface State {
        data object Loading : State
        data class Error(val messageKey: ErrorKind) : State
        data class PickSources(
            val rows: List<AnixartRow>,
            val preflight: PreflightInfo,
            val sources: List<SourceChoice>,
            val categories: List<Category>,
            val statusCategoryIds: Map<AnixartStatus, Long?>,
            val favoriteCategoryId: Long?,
            val statusFilter: Set<AnixartStatus>,
            val syncToShikimori: Boolean,
        ) : State
        data class Matching(val current: Int, val total: Int) : State
        data class Review(
            val items: List<ReviewItem>,
            val matchingReport: AnixartMatchingCoordinator.MatchingReport,
            val statusCategoryIds: Map<AnixartStatus, Long?>,
            val favoriteCategoryId: Long?,
            val syncToShikimori: Boolean,
        ) : State
        data class Importing(val current: Int, val total: Int) : State
        data class Done(
            val report: ImportAnixartEntries.Report,
            val matchingReport: AnixartMatchingCoordinator.MatchingReport,
            val trackerReport: AnixartTrackerSync.Report?,
            val backgroundJob: Boolean,
        ) : State
    }

    enum class ErrorKind { INVALID, EMPTY }

    data class SourceChoice(
        val id: Long,
        val name: String,
        val selected: Boolean,
        val recommendation: AnixartSourceHints.Recommendation,
    )

    init {
        load()
    }

    private fun load() {
        screenModelScope.launch {
            try {
                val rows = openStream().use { AnixartCsvParser.parse(it) }
                if (rows.isEmpty()) {
                    mutableState.update { State.Error(ErrorKind.EMPTY) }
                    return@launch
                }
                val categories = getAnimeCategories.await()
                val sources = sourceManager.getCatalogueSources()
                    .map { source ->
                        SourceChoice(
                            id = source.id,
                            name = source.name,
                            selected = false,
                            recommendation = AnixartSourceHints.recommendation(source.name),
                        )
                    }
                val missingOriginal = rows.count { it.originalTitle.isBlank() }
                mutableState.update {
                    State.PickSources(
                        rows = rows,
                        preflight = PreflightInfo(
                            totalRows = rows.size,
                            missingOriginalCount = missingOriginal,
                            largeImport = rows.size > LARGE_IMPORT_THRESHOLD,
                        ),
                        sources = sources,
                        categories = categories,
                        statusCategoryIds = emptyMap(),
                        favoriteCategoryId = null,
                        statusFilter = AnixartStatus.entries.toSet(),
                        syncToShikimori = false,
                    )
                }
            } catch (e: AnixartCsvParser.InvalidAnixartCsvException) {
                mutableState.update { State.Error(ErrorKind.INVALID) }
            }
        }
    }

    fun toggleSource(id: Long) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            s.copy(sources = s.sources.map { if (it.id == id) it.copy(selected = !it.selected) else it })
        }
    }

    fun toggleStatusFilter(status: AnixartStatus) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val updated = s.statusFilter.toMutableSet()
            if (!updated.remove(status)) updated.add(status)
            if (updated.isEmpty()) updated.addAll(AnixartStatus.entries)
            s.copy(statusFilter = updated)
        }
    }

    fun setSyncToShikimori(enabled: Boolean) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            s.copy(syncToShikimori = enabled)
        }
    }

    fun setCategoryMapping(status: AnixartStatus, categoryId: Long?) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val updated = s.statusCategoryIds.toMutableMap()
            if (categoryId == null) {
                updated.remove(status)
            } else {
                updated[status] = categoryId
            }
            s.copy(statusCategoryIds = updated)
        }
    }

    fun setFavoriteCategoryMapping(categoryId: Long?) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            s.copy(favoriteCategoryId = categoryId)
        }
    }

    fun filteredRows(pick: State.PickSources): List<AnixartRow> {
        return pick.rows.filter { row ->
            val status = row.status
            status == null || status in pick.statusFilter
        }
    }

    fun startMatching() {
        val current = state.value as? State.PickSources ?: return
        val sourceIds = current.sources.filter { it.selected }.map { it.id }
        if (sourceIds.isEmpty()) return
        val rows = filteredRows(current)
        if (rows.isEmpty()) return

        val statusCategoryIds = current.statusCategoryIds
        val favoriteCategoryId = current.favoriteCategoryId
        val syncToShikimori = current.syncToShikimori
        val totalRows = rows.size
        val sourceNames = current.sources.associate { it.id to it.name }

        mutableState.update { State.Matching(0, totalRows) }
        screenModelScope.launch {
            val searcher = AnixartSourceSearcher(sourceManager, sourceIds)
            val searchCache = ConcurrentHashMap<String, List<AnixartMatcher.SearchCandidate>>()
            val semaphore = Semaphore(MATCHING_CONCURRENCY)
            val matchedCount = AtomicInteger(0)

            suspend fun cachedSearch(query: String): List<AnixartMatcher.SearchCandidate> {
                return searchCache.getOrPut(query) {
                    searcher.search(query)
                }
            }

            val items = rows.map { row ->
                async {
                    semaphore.withPermit {
                        val rowMatch = AnixartMatchingCoordinator.matchRow(row) { query ->
                            cachedSearch(query)
                        }
                        val currentMatched = matchedCount.incrementAndGet()
                        mutableState.update { State.Matching(currentMatched, totalRows) }

                        val sourceName = rowMatch.result.best?.candidate?.sourceId?.let { sourceNames[it] }
                        ReviewItem(
                            row = row,
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
            mutableState.update {
                State.Review(items, matchingReport, statusCategoryIds, favoriteCategoryId, syncToShikimori)
            }
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
        val selections = review.items.map { item ->
            val chosen = item.result.ranked.firstOrNull { it.candidate.id == item.selectedId }?.candidate
            AnixartImportPlanner.Selection(item.row, chosen, item.enabled)
        }
        val statusMap = review.statusCategoryIds.filterValues { it != null }.mapValues { it.value!! }
        val config = AnixartImportPlanner.Config(
            statusCategoryIds = statusMap,
            favoriteCategoryId = review.favoriteCategoryId,
        )
        val plan = AnixartImportPlanner.plan(selections, config)

        if (plan.actions.size > LARGE_IMPORT_THRESHOLD) {
            AnixartImportJob.start(plan, review.syncToShikimori, review.items)
            mutableState.update {
                State.Done(
                    report = ImportAnixartEntries.Report(added = 0, alreadyInLibrary = 0, failed = 0),
                    matchingReport = review.matchingReport,
                    trackerReport = null,
                    backgroundJob = true,
                )
            }
            return
        }

        mutableState.update { State.Importing(0, plan.actions.size) }
        screenModelScope.launch {
            val report = importEntries.await(plan) { current, total ->
                mutableState.update { State.Importing(current, total) }
            }
            val trackerReport = if (review.syncToShikimori) {
                syncTrackerForReview(review)
            } else {
                null
            }
            mutableState.update {
                State.Done(report, review.matchingReport, trackerReport, backgroundJob = false)
            }
        }
    }

    private suspend fun syncTrackerForReview(review: State.Review): AnixartTrackerSync.Report {
        val pairs = review.items.mapNotNull { item ->
            if (!item.enabled || item.selectedId == null) return@mapNotNull null
            val candidate = item.result.ranked.firstOrNull { it.candidate.id == item.selectedId }?.candidate
                ?: return@mapNotNull null
            val animeId = networkToLocalId(candidate) ?: return@mapNotNull null
            item.row to animeId
        }
        return trackerSync.syncToShikimori(pairs)
    }

    private suspend fun networkToLocalId(candidate: AnixartMatcher.SearchCandidate): Long? {
        return try {
            getAnimeByUrlAndSourceId.await(candidate.url, candidate.sourceId)?.id
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val LARGE_IMPORT_THRESHOLD = 100
        private const val MATCHING_CONCURRENCY = 2
    }
}
