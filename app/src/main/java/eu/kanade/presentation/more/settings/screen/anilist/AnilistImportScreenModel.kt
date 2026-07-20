package eu.kanade.presentation.more.settings.screen.anilist

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.more.settings.screen.anixart.AnixartImportScreenModel.SourceChoice
import eu.kanade.tachiyomi.data.anilist.AnilistImportJob
import eu.kanade.tachiyomi.data.anilist.FetchAnilistImportEntries
import eu.kanade.tachiyomi.data.anilist.ImportAnilistExecutor
import eu.kanade.tachiyomi.data.anixart.AnixartSourceSearcher
import eu.kanade.tachiyomi.data.shikimori.ShikimoriMangaSourceSearcher
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.data.anilist.AnilistImportEntry
import tachiyomi.data.anilist.AnilistImportMediaType
import tachiyomi.data.anilist.AnilistImportPlanner
import tachiyomi.data.anilist.AnilistImportStatus
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartMatchingCoordinator
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.data.anixart.AnixartTitleSearcher
import tachiyomi.data.anixart.MediaImportMatchingEngine
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnilistImportScreenModel(
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
    private val fetchEntries: FetchAnilistImportEntries = Injekt.get(),
    private val importExecutor: ImportAnilistExecutor = Injekt.get(),
) : StateScreenModel<AnilistImportScreenModel.State>(State.Loading(AnilistImportMediaType.ANIME)) {

    @Immutable
    data class ReviewItem(
        val entry: AnilistImportEntry,
        val result: AnixartMatcher.MatchResult,
        val selectedId: Long?,
        val enabled: Boolean,
        val matchedQuery: String?,
        val matchedSourceName: String?,
    )

    sealed interface State {
        val mediaType: AnilistImportMediaType

        data class Loading(override val mediaType: AnilistImportMediaType) : State
        data class Error(
            override val mediaType: AnilistImportMediaType,
            val messageKey: ErrorKind,
        ) : State
        data class PickSources(
            override val mediaType: AnilistImportMediaType,
            val entries: List<AnilistImportEntry>,
            val sources: List<SourceChoice>,
            val categories: List<CategoryUi>,
            val statusCategoryIds: Map<AnilistImportStatus, Long?>,
            val statusFilter: Set<AnilistImportStatus>,
            val largeImport: Boolean,
        ) : State
        data class Matching(
            override val mediaType: AnilistImportMediaType,
            val current: Int,
            val total: Int,
        ) : State
        data class ManualSearchState(
            val rowIndex: Int,
            val query: String,
            val loading: Boolean = false,
        )

        data class Review(
            override val mediaType: AnilistImportMediaType,
            val items: List<ReviewItem>,
            val matchingReport: AnixartMatchingCoordinator.MatchingReport,
            val statusCategoryIds: Map<AnilistImportStatus, Long?>,
            val sourceIds: List<Long>,
            val sourceNames: Map<Long, String>,
            val manualSearch: ManualSearchState? = null,
        ) : State
        data class Importing(
            override val mediaType: AnilistImportMediaType,
            val current: Int,
            val total: Int,
        ) : State
        data class Done(
            override val mediaType: AnilistImportMediaType,
            val report: ImportAnilistExecutor.Report,
            val matchingReport: AnixartMatchingCoordinator.MatchingReport,
            val backgroundJob: Boolean,
        ) : State
    }

    @Immutable
    data class CategoryUi(
        val id: Long,
        val name: String,
    )

    enum class ErrorKind { NOT_LOGGED_IN, EMPTY, NETWORK, RATE_LIMITED }

    init {
        load(AnilistImportMediaType.ANIME)
    }

    fun switchMediaType(mediaType: AnilistImportMediaType) {
        val current = state.value
        if (current.mediaType == mediaType) return
        if (current !is State.PickSources && current !is State.Loading && current !is State.Error) return
        load(mediaType)
    }

    private fun load(mediaType: AnilistImportMediaType) {
        mutableState.update { State.Loading(mediaType) }
        screenModelScope.launch {
            try {
                val entries = fetchEntries.await(mediaType)
                if (entries.isEmpty()) {
                    mutableState.update { State.Error(mediaType, ErrorKind.EMPTY) }
                    return@launch
                }
                val sources = catalogueSources(mediaType)
                val categories = loadCategories(mediaType)
                mutableState.update {
                    State.PickSources(
                        mediaType = mediaType,
                        entries = entries,
                        sources = sources,
                        categories = categories,
                        statusCategoryIds = emptyMap(),
                        statusFilter = AnilistImportStatus.forMediaType(mediaType).toSet(),
                        largeImport = entries.size > MediaImportMatchingEngine.LARGE_IMPORT_THRESHOLD,
                    )
                }
            } catch (_: FetchAnilistImportEntries.NotLoggedInException) {
                mutableState.update { State.Error(mediaType, ErrorKind.NOT_LOGGED_IN) }
            } catch (_: FetchAnilistImportEntries.RateLimitedException) {
                mutableState.update { State.Error(mediaType, ErrorKind.RATE_LIMITED) }
            } catch (_: FetchAnilistImportEntries.NetworkException) {
                mutableState.update { State.Error(mediaType, ErrorKind.NETWORK) }
            }
        }
    }

    private fun catalogueSources(mediaType: AnilistImportMediaType): List<SourceChoice> = when (mediaType) {
        AnilistImportMediaType.ANIME -> animeSourceManager.getCatalogueSources().map { source ->
            SourceChoice(
                id = source.id,
                name = source.name,
                selected = false,
                recommendation = AnixartSourceHints.recommendation(source.name),
            )
        }
        AnilistImportMediaType.MANGA -> mangaSourceManager.getCatalogueSources().map { source ->
            SourceChoice(
                id = source.id,
                name = source.name,
                selected = false,
                recommendation = AnixartSourceHints.Recommendation.NEUTRAL,
            )
        }
    }

    private suspend fun loadCategories(mediaType: AnilistImportMediaType): List<CategoryUi> = when (mediaType) {
        AnilistImportMediaType.ANIME -> getAnimeCategories.await().map { CategoryUi(it.id, it.name) }
        AnilistImportMediaType.MANGA -> getMangaCategories.await().map { CategoryUi(it.id, it.name) }
    }

    fun toggleSource(id: Long) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            s.copy(sources = s.sources.map { if (it.id == id) it.copy(selected = !it.selected) else it })
        }
    }

    fun toggleStatusFilter(status: AnilistImportStatus) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val allowed = AnilistImportStatus.forMediaType(s.mediaType)
            if (status !in allowed) return@update s
            val updated = s.statusFilter.toMutableSet()
            if (!updated.remove(status)) updated.add(status)
            if (updated.isEmpty()) updated.addAll(allowed)
            s.copy(statusFilter = updated)
        }
    }

    fun setCategoryMapping(status: AnilistImportStatus, categoryId: Long?) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val updated = s.statusCategoryIds.toMutableMap()
            if (categoryId == null) updated.remove(status) else updated[status] = categoryId
            s.copy(statusCategoryIds = updated)
        }
    }

    private fun filteredEntries(pick: State.PickSources): List<AnilistImportEntry> {
        return pick.entries.filter { entry ->
            val status = AnilistImportStatus.fromApi(entry.status)
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
        val total = entries.size
        val sourceNames = current.sources.associate { it.id to it.name }
        val mediaType = current.mediaType

        mutableState.update { State.Matching(mediaType, 0, total) }
        screenModelScope.launch {
            val searcher = createSearcher(mediaType, sourceIds)
            val (results, matchingReport) = MediaImportMatchingEngine.matchRows(
                rows = entries,
                toInput = { entry ->
                    MediaImportMatchingEngine.RowInput(
                        candidateTitles = entry.candidateTitles(),
                        searchQueries = entry.searchQueries(),
                    )
                },
                search = { query -> searcher.search(query) },
                sourceNames = sourceNames,
                onProgress = { currentMatched, totalRows ->
                    mutableState.update { State.Matching(mediaType, currentMatched, totalRows) }
                },
            )

            val items = results.map { row ->
                ReviewItem(
                    entry = row.row,
                    result = row.result,
                    selectedId = row.result.best?.candidate?.id,
                    enabled = row.result.confidence != AnixartMatcher.Confidence.NO_MATCH,
                    matchedQuery = row.matchedQuery,
                    matchedSourceName = row.matchedSourceName,
                )
            }
            mutableState.update {
                State.Review(
                    mediaType = mediaType,
                    items = items,
                    matchingReport = matchingReport,
                    statusCategoryIds = statusCategoryIds,
                    sourceIds = sourceIds,
                    sourceNames = sourceNames,
                )
            }
        }
    }

    private fun createSearcher(
        mediaType: AnilistImportMediaType,
        sourceIds: List<Long>,
    ): AnixartTitleSearcher = when (mediaType) {
        AnilistImportMediaType.ANIME -> AnixartSourceSearcher(animeSourceManager, sourceIds)
        AnilistImportMediaType.MANGA -> ShikimoriMangaSourceSearcher(mangaSourceManager, sourceIds)
    }

    fun setSelection(rowIndex: Int, candidateId: Long?) {
        mutableState.update { s ->
            if (s !is State.Review) return@update s
            s.copy(
                items = s.items.mapIndexed { i, item ->
                    if (i != rowIndex) return@mapIndexed item
                    item.copy(
                        selectedId = candidateId,
                        enabled = candidateId != null,
                    )
                },
            )
        }
    }

    fun openManualSearch(rowIndex: Int) {
        mutableState.update { s ->
            if (s !is State.Review) return@update s
            val entry = s.items.getOrNull(rowIndex)?.entry ?: return@update s
            val defaultQuery = entry.english?.takeIf { it.isNotBlank() } ?: entry.name
            s.copy(manualSearch = State.ManualSearchState(rowIndex, defaultQuery))
        }
    }

    fun dismissManualSearch() {
        mutableState.update { s ->
            if (s !is State.Review) return@update s
            s.copy(manualSearch = null)
        }
    }

    fun setManualSearchQuery(query: String) {
        mutableState.update { s ->
            if (s !is State.Review) return@update s
            val manual = s.manualSearch ?: return@update s
            s.copy(manualSearch = manual.copy(query = query))
        }
    }

    fun runManualSearch() {
        val review = state.value as? State.Review ?: return
        val manual = review.manualSearch ?: return
        val query = manual.query.trim()
        if (query.isEmpty()) return
        val rowIndex = manual.rowIndex
        val item = review.items.getOrNull(rowIndex) ?: return

        mutableState.update { s ->
            if (s !is State.Review) return@update s
            s.copy(manualSearch = manual.copy(loading = true))
        }

        screenModelScope.launch {
            val searcher = createSearcher(review.mediaType, review.sourceIds)
            val candidates = searcher.search(query)
            val rawMatch = AnixartMatcher.match(item.entry.candidateTitles(), candidates)
            val top = rawMatch.ranked.firstOrNull()
            val result = if (top == null || top.score <= 0) {
                rawMatch
            } else {
                rawMatch.copy(
                    confidence = AnixartMatcher.Confidence.NEEDS_REVIEW,
                    best = top,
                )
            }
            val sourceName = result.best?.candidate?.sourceId?.let { review.sourceNames[it] }
            mutableState.update { s ->
                if (s !is State.Review) return@update s
                s.copy(
                    manualSearch = null,
                    items = s.items.mapIndexed { i, row ->
                        if (i != rowIndex) return@mapIndexed row
                        row.copy(
                            result = result,
                            selectedId = result.best?.candidate?.id,
                            enabled = result.best != null,
                            matchedQuery = query,
                            matchedSourceName = sourceName,
                        )
                    },
                )
            }
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
            AnilistImportPlanner.Selection(item.entry, chosen, item.enabled)
        }
        val statusMap = review.statusCategoryIds.filterValues { it != null }.mapValues { it.value!! }
        val plan = AnilistImportPlanner.plan(
            selections,
            AnilistImportPlanner.Config(statusCategoryIds = statusMap),
        )

        if (plan.actions.size > MediaImportMatchingEngine.LARGE_IMPORT_THRESHOLD) {
            AnilistImportJob.start(Injekt.get<android.app.Application>(), review.mediaType, plan)
            mutableState.update {
                State.Done(
                    mediaType = review.mediaType,
                    report = ImportAnilistExecutor.Report(0, 0, 0, 0),
                    matchingReport = review.matchingReport,
                    backgroundJob = true,
                )
            }
            return
        }

        mutableState.update { State.Importing(review.mediaType, 0, plan.actions.size) }
        screenModelScope.launch {
            val report = importExecutor.await(review.mediaType, plan) { current, total ->
                mutableState.update { State.Importing(review.mediaType, current, total) }
            }
            mutableState.update {
                State.Done(review.mediaType, report, review.matchingReport, backgroundJob = false)
            }
        }
    }
}
