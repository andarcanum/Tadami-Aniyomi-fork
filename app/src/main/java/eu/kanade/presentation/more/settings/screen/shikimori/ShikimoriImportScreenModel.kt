package eu.kanade.presentation.more.settings.screen.shikimori

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.anixart.AnixartSourceSearcher
import eu.kanade.tachiyomi.data.shikimori.FetchShikimoriImportEntries
import eu.kanade.tachiyomi.data.shikimori.ImportShikimoriExecutor
import eu.kanade.tachiyomi.data.shikimori.ShikimoriImportJob
import eu.kanade.tachiyomi.data.shikimori.ShikimoriMangaSourceSearcher
import eu.kanade.tachiyomi.data.shikimori.ShikimoriNovelSourceSearcher
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartMatchingCoordinator
import tachiyomi.data.anixart.AnixartSourceHints
import tachiyomi.data.anixart.AnixartTitleSearcher
import tachiyomi.data.anixart.MediaImportMatchingEngine
import tachiyomi.data.shikimori.ShikimoriImportEntry
import tachiyomi.data.shikimori.ShikimoriImportMediaType
import tachiyomi.data.shikimori.ShikimoriImportPlanner
import tachiyomi.data.shikimori.ShikimoriImportStatus
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Immutable
data class SourceChoice(
    val id: Long,
    val name: String,
    val lang: String,
    val selected: Boolean,
    val recommendation: AnixartSourceHints.Recommendation,
)

class ShikimoriImportScreenModel(
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val fetchEntries: FetchShikimoriImportEntries = Injekt.get(),
    private val importExecutor: ImportShikimoriExecutor = Injekt.get(),
) : StateScreenModel<ShikimoriImportScreenModel.State>(State.Loading(ShikimoriImportMediaType.ANIME)) {

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
        val mediaType: ShikimoriImportMediaType

        data class Loading(override val mediaType: ShikimoriImportMediaType) : State
        data class Error(
            override val mediaType: ShikimoriImportMediaType,
            val messageKey: ErrorKind,
        ) : State
        data class PickSources(
            override val mediaType: ShikimoriImportMediaType,
            val entries: List<ShikimoriImportEntry>,
            val sources: List<SourceChoice>,
            val categories: List<CategoryUi>,
            val statusCategoryIds: Map<ShikimoriImportStatus, Long?>,
            val statusFilter: Set<ShikimoriImportStatus>,
            val largeImport: Boolean,
            val searchQuery: String = "",
            val collapsedLanguages: Set<String> = emptySet(),
            val enabledLanguages: Set<String> = emptySet(),
        ) : State
        data class Matching(
            override val mediaType: ShikimoriImportMediaType,
            val current: Int,
            val total: Int,
        ) : State
        data class ManualSearchState(
            val rowIndex: Int,
            val query: String,
            val loading: Boolean = false,
        )

        data class Review(
            override val mediaType: ShikimoriImportMediaType,
            val items: List<ReviewItem>,
            val matchingReport: AnixartMatchingCoordinator.MatchingReport,
            val statusCategoryIds: Map<ShikimoriImportStatus, Long?>,
            val sourceIds: List<Long>,
            val sourceNames: Map<Long, String>,
            val manualSearch: ManualSearchState? = null,
        ) : State
        data class Importing(
            override val mediaType: ShikimoriImportMediaType,
            val current: Int,
            val total: Int,
        ) : State
        data class Done(
            override val mediaType: ShikimoriImportMediaType,
            val report: ImportShikimoriExecutor.Report,
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
        load(ShikimoriImportMediaType.ANIME)
    }

    fun switchMediaType(mediaType: ShikimoriImportMediaType) {
        val current = state.value
        if (current.mediaType == mediaType) return
        if (current !is State.PickSources && current !is State.Loading && current !is State.Error) return
        load(mediaType)
    }

    private fun load(mediaType: ShikimoriImportMediaType) {
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
                val defaultLangs = mutableSetOf("en", "ru")
                val appLang = java.util.Locale.getDefault().language
                if (appLang.isNotBlank()) {
                    defaultLangs.add(appLang)
                }
                mutableState.update {
                    State.PickSources(
                        mediaType = mediaType,
                        entries = entries,
                        sources = sources,
                        categories = categories,
                        statusCategoryIds = emptyMap(),
                        statusFilter = ShikimoriImportStatus.forMediaType(mediaType).toSet(),
                        largeImport = entries.size > MediaImportMatchingEngine.LARGE_IMPORT_THRESHOLD,
                        enabledLanguages = defaultLangs,
                    )
                }
            } catch (_: FetchShikimoriImportEntries.NotLoggedInException) {
                mutableState.update { State.Error(mediaType, ErrorKind.NOT_LOGGED_IN) }
            } catch (_: FetchShikimoriImportEntries.RateLimitedException) {
                mutableState.update { State.Error(mediaType, ErrorKind.RATE_LIMITED) }
            } catch (_: FetchShikimoriImportEntries.NetworkException) {
                mutableState.update { State.Error(mediaType, ErrorKind.NETWORK) }
            }
        }
    }

    private fun catalogueSources(mediaType: ShikimoriImportMediaType): List<SourceChoice> = when (mediaType) {
        ShikimoriImportMediaType.ANIME -> animeSourceManager.getCatalogueSources().map { source ->
            SourceChoice(
                id = source.id,
                name = source.name,
                lang = source.lang,
                selected = false,
                recommendation = AnixartSourceHints.recommendation(source.name),
            )
        }
        ShikimoriImportMediaType.MANGA -> mangaSourceManager.getCatalogueSources().map { source ->
            SourceChoice(
                id = source.id,
                name = source.name,
                lang = source.lang,
                selected = false,
                recommendation = AnixartSourceHints.Recommendation.NEUTRAL,
            )
        }
        ShikimoriImportMediaType.RANOBE -> novelSourceManager.getCatalogueSources().map { source ->
            SourceChoice(
                id = source.id,
                name = source.name,
                lang = source.lang,
                selected = false,
                recommendation = AnixartSourceHints.Recommendation.NEUTRAL,
            )
        }
    }

    private suspend fun loadCategories(mediaType: ShikimoriImportMediaType): List<CategoryUi> = when (mediaType) {
        ShikimoriImportMediaType.ANIME -> getAnimeCategories.await().map { CategoryUi(it.id, it.name) }
        ShikimoriImportMediaType.MANGA -> getMangaCategories.await().map { CategoryUi(it.id, it.name) }
        ShikimoriImportMediaType.RANOBE -> getNovelCategories.await().map { CategoryUi(it.id, it.name) }
    }

    fun toggleSource(id: Long) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            s.copy(sources = s.sources.map { if (it.id == id) it.copy(selected = !it.selected) else it })
        }
    }

    fun search(query: String) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            s.copy(searchQuery = query)
        }
    }

    fun toggleLanguage(language: String) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val updated = s.collapsedLanguages.toMutableSet()
            if (!updated.remove(language)) updated.add(language)
            s.copy(collapsedLanguages = updated)
        }
    }

    fun toggleLanguageSources(language: String, select: Boolean) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val updatedSources = s.sources.map { source ->
                if (source.lang == language) {
                    source.copy(selected = select)
                } else {
                    source
                }
            }
            s.copy(sources = updatedSources)
        }
    }

    fun toggleLanguageEnabled(language: String) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val updated = s.enabledLanguages.toMutableSet()
            if (!updated.remove(language)) updated.add(language)
            s.copy(enabledLanguages = updated)
        }
    }

    fun toggleStatusFilter(status: ShikimoriImportStatus) {
        mutableState.update { s ->
            if (s !is State.PickSources) return@update s
            val allowed = ShikimoriImportStatus.forMediaType(s.mediaType)
            if (status !in allowed) return@update s
            val updated = s.statusFilter.toMutableSet()
            if (!updated.remove(status)) updated.add(status)
            if (updated.isEmpty()) updated.addAll(allowed)
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
        mediaType: ShikimoriImportMediaType,
        sourceIds: List<Long>,
    ): AnixartTitleSearcher = when (mediaType) {
        ShikimoriImportMediaType.ANIME -> AnixartSourceSearcher(animeSourceManager, sourceIds)
        ShikimoriImportMediaType.MANGA -> ShikimoriMangaSourceSearcher(mangaSourceManager, sourceIds)
        ShikimoriImportMediaType.RANOBE -> ShikimoriNovelSourceSearcher(novelSourceManager, sourceIds)
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
            val defaultQuery = entry.russian?.takeIf { it.isNotBlank() } ?: entry.name
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
            ShikimoriImportPlanner.Selection(item.entry, chosen, item.enabled)
        }
        val statusMap = review.statusCategoryIds.filterValues { it != null }.mapValues { it.value!! }
        val plan = ShikimoriImportPlanner.plan(
            selections,
            ShikimoriImportPlanner.Config(statusCategoryIds = statusMap),
        )

        if (plan.actions.size > MediaImportMatchingEngine.LARGE_IMPORT_THRESHOLD) {
            ShikimoriImportJob.start(Injekt.get<android.app.Application>(), review.mediaType, plan)
            mutableState.update {
                State.Done(
                    mediaType = review.mediaType,
                    report = ImportShikimoriExecutor.Report(0, 0, 0, 0),
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
