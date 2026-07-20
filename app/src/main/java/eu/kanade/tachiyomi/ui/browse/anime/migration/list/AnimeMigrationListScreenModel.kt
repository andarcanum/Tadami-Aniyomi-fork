package eu.kanade.tachiyomi.ui.browse.anime.migration.list

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.MigrateAnimeUseCase
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorStore
import eu.kanade.tachiyomi.ui.browse.anime.migration.AnimeMigrationFlags
import eu.kanade.tachiyomi.ui.browse.anime.migration.list.search.SmartAnimeSourceSearchEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeMigrationListScreenModel(
    animeIds: Collection<Long>,
    private val sourceIds: Collection<Long>,
    private val extraSearchQuery: String?,
    val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val migrateAnime: MigrateAnimeUseCase = MigrateAnimeUseCase(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<AnimeMigrationListScreenModel.State>(State()) {

    val items
        get() = state.value.items

    private val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags_anime", Int.MAX_VALUE)
    }

    private var migrateJob: Job? = null
    private var searchJob: Job? = null
    private var allItems: List<MigratingAnime> = emptyList()
    private val cancelledSearchIds = mutableSetOf<Long>()

    init {
        screenModelScope.launchIO {
            val items = animeIds
                .map { id ->
                    async {
                        val anime = getAnime.await(id) ?: return@async null
                        // Season-type entries can't be bulk-migrated; use the single-entry
                        // migration flow for those.
                        if (anime.fetchType != FetchType.Episodes) return@async null
                        val episodeInfo = getEpisodeInfo(id)
                        MigratingAnime(
                            anime = anime,
                            episodeCount = episodeInfo.episodeCount,
                            latestEpisode = episodeInfo.latestEpisode,
                            source = sourceManager.getOrStub(anime.source).name,
                        )
                    }
                }
                .awaitAll()
                .filterNotNull()

            allItems = items
            mutableState.update { it.copy(items = items.toImmutableList()) }

            mutableState.update { it.copy(isLoading = false) }

            startSearches(resetResults = false)
        }
    }

    private suspend fun getEpisodeInfo(id: Long): EpisodeInfo {
        val episodes: List<Episode> = getEpisodesByAnimeId.await(id)
        return EpisodeInfo(
            latestEpisode = episodes.maxOfOrNull { it.episodeNumber },
            episodeCount = episodes.size,
        )
    }

    private suspend fun getEpisodeInfo(
        source: AnimeCatalogueSource,
        anime: Anime,
    ): EpisodeInfo {
        return try {
            source.getEpisodeList(anime.toSAnime()).let { episodes ->
                EpisodeInfo(
                    latestEpisode = episodes.maxOfOrNull { it.episode_number.toDouble() },
                    episodeCount = episodes.size,
                )
            }
        } catch (_: Exception) {
            EpisodeInfo(
                latestEpisode = null,
                episodeCount = 0,
            )
        }
    }

    private fun startSearches(resetResults: Boolean) {
        searchJob?.cancel()
        cancelledSearchIds.clear()
        val searchItems = if (resetResults) {
            allItems.map { it.copy(searchResult = SearchResult.Searching) }
        } else {
            allItems
        }
        allItems = searchItems
        mutableState.update {
            it.copy(
                items = searchItems.toImmutableList(),
                finishedCount = searchItems.count { item -> item.searchResult != SearchResult.Searching },
                migrationComplete = isAnimeMigrationSearchComplete(searchItems),
            )
        }
        searchJob = screenModelScope.launchIO {
            try {
                runSearches(searchItems)
            } finally {
                searchJob = null
            }
        }
    }

    private suspend fun runSearches(items: List<MigratingAnime>) {
        val sources = getEnabledSources()
        val strategy = sourcePreferences.migrationStrategy()
        val useDeepSearch = sourcePreferences.migrationSearchKeywords().get()
        val useAutoMetadata = sourcePreferences.migrationExtraSearchParam().get()
        val hideNotFound = sourcePreferences.migrationHideNotFound().get()
        val onlyNewEpisodes = sourcePreferences.migrationOnlyNewChapters().get()

        var currentItems = items
        items.forEach { item ->
            if (item.anime.id in cancelledSearchIds) {
                currentItems = currentItems.map { current ->
                    if (current.anime.id == item.anime.id) {
                        current.copy(searchResult = SearchResult.NotFound, searchLabel = null)
                    } else {
                        current
                    }
                }
                publishSearchItems(currentItems, hideNotFound, onlyNewEpisodes)
                return@forEach
            }

            val result = searchSource(
                anime = item.anime,
                sources = sources,
                strategy = strategy,
                useDeepSearch = useDeepSearch,
                useAutoMetadata = useAutoMetadata,
                onProgress = { label ->
                    currentItems = currentItems.map { current ->
                        if (current.anime.id == item.anime.id) {
                            current.copy(searchLabel = label)
                        } else {
                            current
                        }
                    }
                    publishSearchItems(currentItems, hideNotFound, onlyNewEpisodes)
                },
            )
            if (item.anime.id in cancelledSearchIds) {
                currentItems = currentItems.map { current ->
                    if (current.anime.id == item.anime.id) {
                        current.copy(searchResult = SearchResult.NotFound, searchLabel = null)
                    } else {
                        current
                    }
                }
                publishSearchItems(currentItems, hideNotFound, onlyNewEpisodes)
                return@forEach
            }

            val updatedItem = when (result) {
                null -> item.copy(searchResult = SearchResult.NotFound, searchLabel = null)
                else -> item.copy(
                    searchLabel = null,
                    searchResult = SearchResult.Success(
                        anime = result.anime,
                        source = result.source.name,
                        episodeCount = result.episodeInfo.episodeCount,
                        latestEpisode = result.episodeInfo.latestEpisode,
                    ),
                )
            }

            currentItems = currentItems.map { current ->
                if (current.anime.id == item.anime.id) updatedItem else current
            }

            publishSearchItems(currentItems, hideNotFound, onlyNewEpisodes)
        }
    }

    private fun publishSearchItems(
        items: List<MigratingAnime>,
        hideNotFound: Boolean,
        onlyNewEpisodes: Boolean,
    ) {
        allItems = items
        val visibleItems = visibleAnimeMigrationItems(
            items = items,
            hideNotFound = hideNotFound,
            onlyNewEpisodes = onlyNewEpisodes,
        ).toImmutableList()
        val finishedCount = visibleItems.count { it.searchResult != SearchResult.Searching }
        val migrationComplete = isAnimeMigrationSearchComplete(visibleItems)

        mutableState.update { state ->
            state.copy(
                items = visibleItems,
                finishedCount = finishedCount,
                migrationComplete = migrationComplete,
            )
        }
    }

    private suspend fun searchSource(
        anime: Anime,
        sources: List<AnimeCatalogueSource>,
        strategy: SourcePreferences.MigrationStrategy,
        useDeepSearch: Boolean,
        useAutoMetadata: Boolean,
        onProgress: (String?) -> Unit,
    ): AnimeMigrationSearchCandidate? {
        val searchParams = buildAnimeMigrationSearchParams(
            anime = anime,
            manualExtraSearchQuery = extraSearchQuery,
            useAutoMetadata = useAutoMetadata,
        )
        val searchEngine = SmartAnimeSourceSearchEngine(searchParams)

        return when (strategy) {
            SourcePreferences.MigrationStrategy.FIRST_SOURCE -> {
                searchSourceSequentially(
                    anime = anime,
                    sources = sources,
                    searchEngine = searchEngine,
                    useDeepSearch = useDeepSearch,
                    onProgress = onProgress,
                )
            }
            SourcePreferences.MigrationStrategy.MOST_CHAPTERS -> {
                searchSourceByMostEpisodes(
                    anime = anime,
                    sources = sources,
                    searchEngine = searchEngine,
                    useDeepSearch = useDeepSearch,
                    onProgress = onProgress,
                )
            }
        }
    }

    private suspend fun searchSourceSequentially(
        anime: Anime,
        sources: List<AnimeCatalogueSource>,
        searchEngine: SmartAnimeSourceSearchEngine,
        useDeepSearch: Boolean,
        onProgress: (String?) -> Unit,
    ): AnimeMigrationSearchCandidate? {
        for ((index, source) in sources.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(source.name)
            val result = searchSourceInCatalogue(
                anime = anime,
                source = source,
                sourceIndex = index,
                searchEngine = searchEngine,
                useDeepSearch = useDeepSearch,
            ) ?: continue

            return result
        }
        return null
    }

    private suspend fun searchSourceByMostEpisodes(
        anime: Anime,
        sources: List<AnimeCatalogueSource>,
        searchEngine: SmartAnimeSourceSearchEngine,
        useDeepSearch: Boolean,
        onProgress: (String?) -> Unit,
    ): AnimeMigrationSearchCandidate? = kotlinx.coroutines.supervisorScope {
        onProgress("${sources.size} sources")
        val candidates = sources.mapIndexed { index, source ->
            async {
                currentCoroutineContext().ensureActive()
                searchSourceInCatalogue(
                    anime = anime,
                    source = source,
                    sourceIndex = index,
                    searchEngine = searchEngine,
                    useDeepSearch = useDeepSearch,
                )
            }
        }.awaitAll().filterNotNull()

        selectAnimeMigrationSearchCandidate(
            candidates = candidates,
            strategy = SourcePreferences.MigrationStrategy.MOST_CHAPTERS,
        )
    }

    private suspend fun searchSourceInCatalogue(
        anime: Anime,
        source: AnimeCatalogueSource,
        sourceIndex: Int,
        searchEngine: SmartAnimeSourceSearchEngine,
        useDeepSearch: Boolean,
    ): AnimeMigrationSearchCandidate? {
        currentCoroutineContext().ensureActive()
        val result = searchEngine.regularSearch(source, anime.title)
            ?: if (useDeepSearch) searchEngine.deepSearch(source, anime.title) else null
        currentCoroutineContext().ensureActive()
        if (result == null) return null
        if (result.url == anime.url && result.source == anime.source) return null
        if (result.fetchType != FetchType.Episodes) return null

        val episodeInfo = getEpisodeInfo(source, result)
        currentCoroutineContext().ensureActive()
        return AnimeMigrationSearchCandidate(
            sourceIndex = sourceIndex,
            source = source,
            anime = result,
            episodeInfo = episodeInfo,
        )
    }

    private fun getEnabledSources(): List<AnimeCatalogueSource> {
        if (sourceIds.isNotEmpty()) {
            val byId = sourceManager.getCatalogueSources().associateBy { it.id }
            return sourceIds.mapNotNull { byId[it] }
        }

        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledAnimeSources().get()
        val pinnedSources = sourcePreferences.pinnedAnimeSources().get()

        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    fun migrateAnimes() {
        migrateAnimes(replace = true)
    }

    fun copyAnimes() {
        migrateAnimes(replace = false)
    }

    fun showMigrateDialog(copy: Boolean) {
        mutableState.update { state ->
            state.copy(
                dialog = Dialog.Migrate(
                    copy = copy,
                    totalCount = state.items.size,
                    skippedCount = animeMigrationSkippedCount(state.items),
                ),
            )
        }
    }

    fun showExitDialog() {
        mutableState.update { it.copy(dialog = Dialog.Exit) }
    }

    fun openOptionsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Options) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun onMigrationOptionsUpdated() {
        dismissDialog()
        startSearches(resetResults = true)
    }

    fun migrateNow(animeId: Long, replace: Boolean) {
        screenModelScope.launchIO {
            val item = items.find { it.anime.id == animeId } ?: return@launchIO
            val target = (item.searchResult as? SearchResult.Success)?.anime ?: return@launchIO
            val flags = getMigrationFlags(item.anime)
            migrateAnime.migrateAnime(item.anime, target, replace, flags)
            markUpdateErrorResolved(item.anime.id, replace)
            removeAnime(item)
        }
    }

    fun useAnimeForMigration(current: Long, target: Long) {
        screenModelScope.launchIO {
            cancelledSearchIds += current
            if (allItems.none { it.anime.id == current }) return@launchIO
            val targetAnime = getAnime.await(target) ?: return@launchIO
            val source = sourceManager.get(targetAnime.source) as? AnimeCatalogueSource ?: return@launchIO
            val episodeInfo = getEpisodeInfo(source, targetAnime)
            val updatedItems = allItems.map { item ->
                if (item.anime.id == current) {
                    item.copy(
                        searchLabel = null,
                        searchResult = SearchResult.Success(
                            anime = targetAnime,
                            source = source.name,
                            episodeCount = episodeInfo.episodeCount,
                            latestEpisode = episodeInfo.latestEpisode,
                        ),
                    )
                } else {
                    item
                }
            }
            publishSearchItems(
                items = updatedItems,
                hideNotFound = sourcePreferences.migrationHideNotFound().get(),
                onlyNewEpisodes = sourcePreferences.migrationOnlyNewChapters().get(),
            )
        }
    }

    fun cancelSearch(animeId: Long) {
        cancelledSearchIds += animeId
        val updatedItems = allItems.map { item ->
            if (item.anime.id == animeId && item.searchResult == SearchResult.Searching) {
                item.copy(searchResult = SearchResult.NotFound, searchLabel = null)
            } else {
                item
            }
        }
        publishSearchItems(
            items = updatedItems,
            hideNotFound = sourcePreferences.migrationHideNotFound().get(),
            onlyNewEpisodes = sourcePreferences.migrationOnlyNewChapters().get(),
        )
    }

    fun removeAnime(animeId: Long) {
        screenModelScope.launchIO {
            val item = items.find { it.anime.id == animeId } ?: return@launchIO
            removeAnime(item)
        }
    }

    private fun migrateAnimes(replace: Boolean) {
        migrateJob = screenModelScope.launchIO {
            val items = state.value.items
            val migratedItems = mutableListOf<MigratingAnime>()
            mutableState.update { it.copy(isMigrating = true, migrationProgress = 0f, dialog = null) }

            try {
                items.forEachIndexed { index, item ->
                    val target = (item.searchResult as? SearchResult.Success)?.anime ?: return@forEachIndexed
                    val flags = getMigrationFlags(item.anime)
                    migrateAnime.migrateAnime(item.anime, target, replace, flags)
                    markUpdateErrorResolved(item.anime.id, replace)
                    migratedItems += item
                    mutableState.update {
                        it.copy(migrationProgress = ((index + 1).toFloat() / items.size).coerceAtMost(1f))
                    }
                }
            } finally {
                removeMigratedAnime(migratedItems)
                mutableState.update { it.copy(isMigrating = false, dialog = null) }
                migrateJob = null
            }
        }
    }

    /**
     * Computes the effective migration flags for a single entry without writing the narrowed
     * bitmap back to preferences.
     */
    private fun getMigrationFlags(anime: Anime): Int {
        val applicableFlags = AnimeMigrationFlags.getFlags(anime, migrateFlags.get())
        return AnimeMigrationFlags.getSelectedFlagsBitMap(
            selectedFlags = applicableFlags.map { it.isDefaultSelected },
            flags = applicableFlags,
        )
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
        mutableState.update { it.copy(isMigrating = false) }
    }

    private fun markUpdateErrorResolved(animeId: Long, replace: Boolean) {
        if (!replace) return
        LibraryUpdateErrorStore.markResolved(
            media = LibraryUpdateErrorMedia.Anime,
            entryId = animeId,
        )
    }

    private fun removeAnime(item: MigratingAnime) {
        removeMigratedAnime(listOf(item))
    }

    private fun removeMigratedAnime(items: Collection<MigratingAnime>) {
        if (items.isEmpty()) return
        val migratedIds = items.mapTo(mutableSetOf()) { it.anime.id }
        allItems = allItems.filterNot { it.anime.id in migratedIds }
        mutableState.update { state ->
            val updatedItems = state.items.filterNot { it.anime.id in migratedIds }.toPersistentList()
            val finishedCount = updatedItems.count { it.searchResult != SearchResult.Searching }
            state.copy(
                items = updatedItems,
                finishedCount = finishedCount,
                migrationComplete = isAnimeMigrationSearchComplete(updatedItems),
            )
        }
    }

    override fun onDispose() {
        searchJob?.cancel()
        migrateJob?.cancel()
        super.onDispose()
    }

    @Immutable
    data class EpisodeInfo(
        val latestEpisode: Double?,
        val episodeCount: Int,
    )

    @Immutable
    data class MigratingAnime(
        val anime: Anime,
        val episodeCount: Int,
        val latestEpisode: Double?,
        val source: String,
        val searchResult: SearchResult = SearchResult.Searching,
        val searchLabel: String? = null,
    )

    sealed interface SearchResult {
        data object Searching : SearchResult
        data object NotFound : SearchResult
        data class Success(
            val anime: Anime,
            val source: String,
            val episodeCount: Int,
            val latestEpisode: Double?,
        ) : SearchResult
    }

    sealed interface Dialog {
        data class Migrate(
            val copy: Boolean,
            val totalCount: Int,
            val skippedCount: Int,
        ) : Dialog

        data object Exit : Dialog
        data object Options : Dialog
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: ImmutableList<MigratingAnime> = persistentListOf(),
        val finishedCount: Int = 0,
        val migrationComplete: Boolean = false,
        val isMigrating: Boolean = false,
        val migrationProgress: Float = 0f,
        val dialog: Dialog? = null,
    )
}
