package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.toggle
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class AnimeSearchScreenModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
    private val achievementHandler: AchievementHandler = Injekt.get(),
) : StateScreenModel<AnimeSearchScreenModel.State>(initialState) {

    private var searchJob: Job? = null

    companion object {
        // BGS-3/BRA-7: each SM instance used to allocate its own
        // Executors.newFixedThreadPool(5).asCoroutineDispatcher() and NEVER closed it (no
        // close(), no onDispose - scope cancellation does not close executors): 5 non-daemon
        // threads leaked per global-search/migrate-search screen visit, inherited by all
        // Global*/Migrate* subclasses. A shared limited view of the IO pool keeps the
        // "at most 5 parallel searches" semantics without owning threads (codebase precedent:
        // App.kt, NovelChapterImagePrefetcher, DiscordPresenceManager).
        private val searchDispatcher = Dispatchers.IO.limitedParallelism(5)
    }

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledAnimeSources().get()
    protected val pinnedSources = sourcePreferences.pinnedAnimeSources().get()

    private var lastQuery: String? = null
    private var lastSourceFilter: AnimeSourceFilter? = null

    protected var extensionFilter: String? = null

    private val sortComparator = { map: Map<AnimeCatalogueSource, AnimeSearchItemResult> ->
        compareBy<AnimeCatalogueSource>(
            { (map[it] as? AnimeSearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        screenModelScope.launch {
            preferences.globalSearchFilterState().changes().collectLatest { state ->
                mutableState.update { it.copy(onlyShowHasResults = state) }
            }
        }
        screenModelScope.launch {
            preferences.searchFilterAnimeLanguages().changes().collectLatest { languages ->
                mutableState.update { it.copy(languageFilter = languages.toImmutableSet()) }
            }
        }
        screenModelScope.launch {
            preferences.enabledLanguages().changes().collectLatest { languages ->
                mutableState.update { it.copy(availableLanguages = languages.toImmutableSet()) }
            }
        }
    }

    @Composable
    fun getAnime(initialAnime: Anime): androidx.compose.runtime.State<Anime> {
        return produceState(initialValue = initialAnime) {
            getAnime.subscribe(initialAnime.url, initialAnime.source)
                .filterNotNull()
                .collectLatest { anime ->
                    value = anime
                }
        }
    }

    open fun getEnabledSources(): List<AnimeCatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    private fun getSelectedSources(): List<AnimeCatalogueSource> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        return extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filterIsInstance<AnimeCatalogueSource>()
            .filter { it in enabledSources }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: AnimeSourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun setLanguageFilter(languages: Set<String>) {
        preferences.searchFilterAnimeLanguages().set(languages)
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState().toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val manager = Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>()
        if (!manager.state.value.unlocked) {
            screenModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                manager.offer(query)
            }
        }

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter
        achievementHandler.trackFeatureUsed(AchievementEvent.Feature.SEARCH)

        val sources = getSelectedSources()

        // Reuse previous results if possible
        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: AnimeSearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { AnimeSearchItemResult.Loading }
                    .toPersistentMap(),
            )
        }

        // BGS-1/BRA-6: cancel the previous cycle (manga/novel etalon) - without it a still-
        // running old job passed the Loading guard after the reset and wrote STALE-query
        // results into the new search's map.
        searchJob?.cancel()
        searchJob = ioCoroutineScope.launch {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is AnimeSearchItemResult.Loading) {
                        return@async
                    }
                    try {
                        val page = withContext(searchDispatcher) {
                            source.getSearchAnime(1, query, source.getFilterList())
                        }

                        val titles = page.animes.map {
                            networkToLocalAnime.await(it.toDomainAnime(source.id))
                        }

                        if (isActive) {
                            updateItem(source, AnimeSearchItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, AnimeSearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    private fun updateItems(items: PersistentMap<AnimeCatalogueSource, AnimeSearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: AnimeCatalogueSource, result: AnimeSearchItemResult) {
        // BGS-2/BRA-8: the mutate() used to be computed OUTSIDE mutableState.update - a CAS
        // retry re-ran the lambda with the already-computed STALE newItems, so concurrent async
        // completions lost updates and rows stayed Loading forever. Compute from the fresh
        // state inside the update block.
        mutableState.update { current ->
            val newItems = current.items.mutate { it[source] = result }
            current.copy(
                items = newItems
                    .toSortedMap(sortComparator(newItems))
                    .toPersistentMap(),
            )
        }
    }

    @Immutable
    data class State(
        val fromSourceId: Long? = null,
        val searchQuery: String? = null,
        val sourceFilter: AnimeSourceFilter = AnimeSourceFilter.All,
        val onlyShowHasResults: Boolean = false,
        val languageFilter: ImmutableSet<String> = persistentSetOf(),
        val availableLanguages: ImmutableSet<String> = persistentSetOf(),
        val items: PersistentMap<AnimeCatalogueSource, AnimeSearchItemResult> = persistentMapOf(),
    ) {
        val progress: Int = items.count { it.value !is AnimeSearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (source, result) ->
            result.isVisible(onlyShowHasResults) &&
                (languageFilter.isEmpty() || source.lang in languageFilter)
        }
    }
}

enum class AnimeSourceFilter {
    All,
    PinnedOnly,
}

sealed interface AnimeSearchItemResult {
    data object Loading : AnimeSearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : AnimeSearchItemResult

    data class Success(
        val result: List<Anime>,
    ) : AnimeSearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
