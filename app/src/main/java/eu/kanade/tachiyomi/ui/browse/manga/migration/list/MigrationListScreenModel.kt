package eu.kanade.tachiyomi.ui.browse.manga.migration.list

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.manga.interactor.MigrateMangaUseCase
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorStore
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags
import eu.kanade.tachiyomi.ui.browse.manga.migration.list.search.SmartSourceSearchEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationListScreenModel(
    mangaIds: Collection<Long>,
    private val sourceIds: Collection<Long>,
    private val extraSearchQuery: String?,
    val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val migrateManga: MigrateMangaUseCase = MigrateMangaUseCase(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrationListScreenModel.State>(State()) {

    val items
        get() = state.value.items

    private val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    private var migrateJob: Job? = null
    private var searchJob: Job? = null

    // F-M2: both fields are written from the IO search/migration coroutines AND the main thread
    // (cancelSearch / useMangaForMigration); plain non-synchronized containers lost updates.
    // allItems read-modify-writes are confined to the screen model monitor (synchronized blocks /
    // @Synchronized updateItem).
    private var allItems: List<MigratingManga> = emptyList()
    private val cancelledSearchIds: MutableSet<Long> = java.util.Collections.synchronizedSet(mutableSetOf())

    init {
        screenModelScope.launchIO {
            val items = mangaIds
                .map { id ->
                    async {
                        val manga = getManga.await(id) ?: return@async null
                        val chapterInfo = getChapterInfo(id)
                        MigratingManga(
                            manga = manga,
                            chapterCount = chapterInfo.chapterCount,
                            latestChapter = chapterInfo.latestChapter,
                            source = sourceManager.getOrStub(manga.source).name,
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

    private suspend fun getChapterInfo(id: Long): ChapterInfo {
        val chapters: List<Chapter> = getChaptersByMangaId.await(id)
        return ChapterInfo(
            latestChapter = chapters.maxOfOrNull { it.chapterNumber },
            chapterCount = chapters.size,
        )
    }

    private suspend fun getChapterInfo(
        source: CatalogueSource,
        manga: Manga,
    ): ChapterInfo {
        return try {
            source.getMangaUpdate(
                manga = manga.toSManga(),
                chapters = emptyList(),
                fetchDetails = false,
                fetchChapters = true,
            ).chapters.let { chapters ->
                ChapterInfo(
                    latestChapter = chapters.maxOfOrNull { it.chapter_number.toDouble() },
                    chapterCount = chapters.size,
                )
            }
        } catch (_: Exception) {
            ChapterInfo(
                latestChapter = null,
                chapterCount = 0,
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
                migrationComplete = isMigrationSearchComplete(searchItems),
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

    private suspend fun runSearches(items: List<MigratingManga>) {
        val sources = getEnabledSources()
        val strategy = sourcePreferences.migrationStrategy()
        val useDeepSearch = sourcePreferences.migrationSearchKeywords().get()
        val useAutoMetadata = sourcePreferences.migrationExtraSearchParam().get()
        val hideNotFound = sourcePreferences.migrationHideNotFound().get()
        val onlyNewChapters = sourcePreferences.migrationOnlyNewChapters().get()

        items.forEach { item ->
            if (item.manga.id in cancelledSearchIds) {
                updateItem(item.manga.id, hideNotFound, onlyNewChapters) { current ->
                    // F-M2: only a search still in flight may be reset. A manual match chosen
                    // before the loop reached this item (Success) used to be overwritten with
                    // NotFound here.
                    if (current.searchResult == SearchResult.Searching) {
                        current.copy(searchResult = SearchResult.NotFound, searchLabel = null)
                    } else {
                        current
                    }
                }
                return@forEach
            }

            val result = runCatching {
                searchSource(
                    manga = item.manga,
                    sources = sources,
                    strategy = strategy,
                    useDeepSearch = useDeepSearch,
                    useAutoMetadata = useAutoMetadata,
                    onProgress = { label ->
                        updateItem(item.manga.id, hideNotFound, onlyNewChapters) { current ->
                            current.copy(searchLabel = label)
                        }
                    },
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Migration search failed for manga ${item.manga.id}" }
            }.getOrNull()

            if (item.manga.id in cancelledSearchIds) {
                updateItem(item.manga.id, hideNotFound, onlyNewChapters) { current ->
                    // F-M2: same guard as above - a late-arriving auto-search result must not
                    // clobber a manual match set through useMangaForMigration while this search
                    // was in flight (cancelSearch has the identical Searching-only guard).
                    if (current.searchResult == SearchResult.Searching) {
                        current.copy(searchResult = SearchResult.NotFound, searchLabel = null)
                    } else {
                        current
                    }
                }
                return@forEach
            }

            val updatedResult = when (result) {
                null -> SearchResult.NotFound
                else -> SearchResult.Success(
                    manga = result.manga,
                    source = result.source.name,
                    chapterCount = result.chapterInfo.chapterCount,
                    latestChapter = result.chapterInfo.latestChapter,
                )
            }

            updateItem(item.manga.id, hideNotFound, onlyNewChapters) { current ->
                current.copy(searchResult = updatedResult, searchLabel = null)
            }
        }
    }

    @Synchronized
    private fun updateItem(
        mangaId: Long,
        hideNotFound: Boolean,
        onlyNewChapters: Boolean,
        transform: (MigratingManga) -> MigratingManga,
    ) {
        allItems = allItems.map { if (it.manga.id == mangaId) transform(it) else it }
        val visibleItems = visibleMigrationItems(
            items = allItems,
            hideNotFound = hideNotFound,
            onlyNewChapters = onlyNewChapters,
        ).toImmutableList()
        val finishedCount = visibleItems.count { it.searchResult != SearchResult.Searching }
        val migrationComplete = isMigrationSearchComplete(visibleItems)

        mutableState.update { state ->
            state.copy(
                items = visibleItems,
                finishedCount = finishedCount,
                migrationComplete = migrationComplete,
            )
        }
    }

    private suspend fun searchSource(
        manga: Manga,
        sources: List<CatalogueSource>,
        strategy: SourcePreferences.MigrationStrategy,
        useDeepSearch: Boolean,
        useAutoMetadata: Boolean,
        onProgress: (String?) -> Unit,
    ): MigrationSearchCandidate? {
        val searchParams = buildMigrationSearchParams(
            manga = manga,
            manualExtraSearchQuery = extraSearchQuery,
            useAutoMetadata = useAutoMetadata,
        )
        val searchEngine = SmartSourceSearchEngine(searchParams)

        return when (strategy) {
            SourcePreferences.MigrationStrategy.FIRST_SOURCE -> {
                searchSourceSequentially(
                    manga = manga,
                    sources = sources,
                    searchEngine = searchEngine,
                    useDeepSearch = useDeepSearch,
                    onProgress = onProgress,
                )
            }
            SourcePreferences.MigrationStrategy.MOST_CHAPTERS -> {
                searchSourceByMostChapters(
                    manga = manga,
                    sources = sources,
                    searchEngine = searchEngine,
                    useDeepSearch = useDeepSearch,
                    onProgress = onProgress,
                )
            }
        }
    }

    private suspend fun searchSourceSequentially(
        manga: Manga,
        sources: List<CatalogueSource>,
        searchEngine: SmartSourceSearchEngine,
        useDeepSearch: Boolean,
        onProgress: (String?) -> Unit,
    ): MigrationSearchCandidate? {
        for ((index, source) in sources.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(source.name)
            val result = searchSourceInCatalogue(
                manga = manga,
                source = source,
                sourceIndex = index,
                searchEngine = searchEngine,
                useDeepSearch = useDeepSearch,
            ) ?: continue

            return result
        }
        return null
    }

    private suspend fun searchSourceByMostChapters(
        manga: Manga,
        sources: List<CatalogueSource>,
        searchEngine: SmartSourceSearchEngine,
        useDeepSearch: Boolean,
        onProgress: (String?) -> Unit,
    ): MigrationSearchCandidate? = kotlinx.coroutines.supervisorScope {
        onProgress("${sources.size} sources")
        val candidates = sources.mapIndexed { index, source ->
            async {
                currentCoroutineContext().ensureActive()
                searchSourceInCatalogue(
                    manga = manga,
                    source = source,
                    sourceIndex = index,
                    searchEngine = searchEngine,
                    useDeepSearch = useDeepSearch,
                )
            }
        }.awaitAll().filterNotNull()

        selectMigrationSearchCandidate(
            candidates = candidates,
            strategy = SourcePreferences.MigrationStrategy.MOST_CHAPTERS,
        )
    }

    private suspend fun searchSourceInCatalogue(
        manga: Manga,
        source: CatalogueSource,
        sourceIndex: Int,
        searchEngine: SmartSourceSearchEngine,
        useDeepSearch: Boolean,
    ): MigrationSearchCandidate? {
        currentCoroutineContext().ensureActive()
        val result = searchEngine.regularSearch(source, manga.title)
            ?: if (useDeepSearch) searchEngine.deepSearch(source, manga.title) else null
        currentCoroutineContext().ensureActive()
        if (result == null) return null
        if (result.url == manga.url && result.source == manga.source) return null

        val chapterInfo = getChapterInfo(source, result)
        currentCoroutineContext().ensureActive()
        return MigrationSearchCandidate(
            sourceIndex = sourceIndex,
            source = source,
            manga = result,
            chapterInfo = chapterInfo,
        )
    }

    private fun getEnabledSources(): List<CatalogueSource> {
        if (sourceIds.isNotEmpty()) {
            val byId = sourceManager.getCatalogueSources().associateBy { it.id }
            return sourceIds.mapNotNull { byId[it] }
        }

        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledMangaSources().get()
        val pinnedSources = sourcePreferences.pinnedMangaSources().get()

        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    fun migrateMangas() {
        migrateMangas(replace = true)
    }

    fun copyMangas() {
        migrateMangas(replace = false)
    }

    fun showMigrateDialog(copy: Boolean) {
        mutableState.update { state ->
            state.copy(
                dialog = Dialog.Migrate(
                    copy = copy,
                    totalCount = state.items.size,
                    skippedCount = migrationSkippedCount(state.items),
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

    fun migrateNow(mangaId: Long, replace: Boolean) {
        screenModelScope.launchIO {
            val item = items.find { it.manga.id == mangaId } ?: return@launchIO
            val target = (item.searchResult as? SearchResult.Success)?.manga ?: return@launchIO
            val flags = getMigrationFlags(item.manga)
            runCatching {
                migrateManga.migrateManga(item.manga, target, replace, flags)
                markUpdateErrorResolved(item.manga.id, replace)
                removeManga(item)
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to migrate single manga $mangaId" }
            }
        }
    }

    fun useMangaForMigration(current: Long, target: Long) {
        screenModelScope.launchIO {
            cancelledSearchIds += current
            if (allItems.none { it.manga.id == current }) return@launchIO
            val targetManga = getManga.await(target) ?: return@launchIO
            val source = sourceManager.get(targetManga.source) as? CatalogueSource ?: return@launchIO
            val chapterInfo = getChapterInfo(source, targetManga)
            // F-M2: confine the allItems read-modify-write to the screen model monitor (shared
            // with @Synchronized updateItem and removeMigratedManga) - concurrent search-loop
            // updates used to lose the manual match.
            val updatedItems = synchronized(this@MigrationListScreenModel) {
                if (allItems.none { it.manga.id == current }) {
                    null
                } else {
                    allItems.map { item ->
                        if (item.manga.id == current) {
                            item.copy(
                                searchLabel = null,
                                searchResult = SearchResult.Success(
                                    manga = targetManga,
                                    source = source.name,
                                    chapterCount = chapterInfo.chapterCount,
                                    latestChapter = chapterInfo.latestChapter,
                                ),
                            )
                        } else {
                            item
                        }
                    }.also { allItems = it }
                }
            } ?: return@launchIO
            val hideNotFound = sourcePreferences.migrationHideNotFound().get()
            val onlyNewChapters = sourcePreferences.migrationOnlyNewChapters().get()
            val visibleItems = visibleMigrationItems(
                items = updatedItems,
                hideNotFound = hideNotFound,
                onlyNewChapters = onlyNewChapters,
            ).toImmutableList()
            val finishedCount = visibleItems.count { it.searchResult != SearchResult.Searching }
            val migrationComplete = isMigrationSearchComplete(visibleItems)

            mutableState.update { state ->
                state.copy(
                    items = visibleItems,
                    finishedCount = finishedCount,
                    migrationComplete = migrationComplete,
                )
            }
        }
    }

    fun cancelSearch(mangaId: Long) {
        cancelledSearchIds += mangaId
        val hideNotFound = sourcePreferences.migrationHideNotFound().get()
        val onlyNewChapters = sourcePreferences.migrationOnlyNewChapters().get()
        updateItem(mangaId, hideNotFound, onlyNewChapters) { current ->
            if (current.searchResult == SearchResult.Searching) {
                current.copy(searchResult = SearchResult.NotFound, searchLabel = null)
            } else {
                current
            }
        }
    }

    fun removeManga(mangaId: Long) {
        screenModelScope.launchIO {
            val item = items.find { it.manga.id == mangaId } ?: return@launchIO
            removeManga(item)
        }
    }

    private fun migrateMangas(replace: Boolean) {
        migrateJob = screenModelScope.launchIO {
            val items = state.value.items
            val migratedItems = mutableListOf<MigratingManga>()
            mutableState.update { it.copy(isMigrating = true, migrationProgress = 0f, dialog = null) }

            try {
                items.forEachIndexed { index, item ->
                    val target = (item.searchResult as? SearchResult.Success)?.manga ?: return@forEachIndexed
                    val flags = getMigrationFlags(item.manga)
                    runCatching {
                        migrateManga.migrateManga(item.manga, target, replace, flags)
                        markUpdateErrorResolved(item.manga.id, replace)
                        migratedItems += item
                    }.onFailure { error ->
                        // NEW-5: swallowing CancellationException made cancelMigrate a no-op for
                        // the loop - after a cancel, every remaining item instantly "failed" with
                        // an error log instead of stopping the batch.
                        if (error is CancellationException) throw error
                        logcat(LogPriority.ERROR, error) { "Failed to migrate manga ${item.manga.id}" }
                    }
                    mutableState.update {
                        it.copy(migrationProgress = ((index + 1).toFloat() / items.size).coerceAtMost(1f))
                    }
                }
            } finally {
                removeMigratedManga(migratedItems)
                mutableState.update { it.copy(isMigrating = false, dialog = null) }
                migrateJob = null
            }
        }
    }

    /**
     * Computes the effective migration flags for a single entry without writing the narrowed
     * bitmap back to preferences. Persisting the per-entry bitmap would permanently drop flags
     * that merely don't apply to that particular entry (e.g. custom cover, delete downloaded)
     * and the narrowing would compound across entries during bulk migration.
     */
    private fun getMigrationFlags(manga: Manga): Int {
        val applicableFlags = MangaMigrationFlags.getFlags(manga, migrateFlags.get())
        return MangaMigrationFlags.getSelectedFlagsBitMap(
            selectedFlags = applicableFlags.map { it.isDefaultSelected },
            flags = applicableFlags,
        )
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
        mutableState.update { it.copy(isMigrating = false) }
    }

    private fun markUpdateErrorResolved(mangaId: Long, replace: Boolean) {
        if (!replace) return
        LibraryUpdateErrorStore.markResolved(
            media = LibraryUpdateErrorMedia.Manga,
            entryId = mangaId,
        )
    }

    private fun removeManga(item: MigratingManga) {
        removeMigratedManga(listOf(item))
    }

    private fun removeMigratedManga(items: Collection<MigratingManga>) {
        if (items.isEmpty()) return
        val migratedIds = items.mapTo(mutableSetOf()) { it.manga.id }
        synchronized(this) {
            allItems = allItems.filterNot { it.manga.id in migratedIds }
        }
        mutableState.update { state ->
            val updatedItems = state.items.filterNot { it.manga.id in migratedIds }.toPersistentList()
            val finishedCount = updatedItems.count { it.searchResult != SearchResult.Searching }
            state.copy(
                items = updatedItems,
                finishedCount = finishedCount,
                migrationComplete = isMigrationSearchComplete(updatedItems),
            )
        }
    }

    override fun onDispose() {
        searchJob?.cancel()
        migrateJob?.cancel()
        super.onDispose()
    }

    @Immutable
    data class ChapterInfo(
        val latestChapter: Double?,
        val chapterCount: Int,
    )

    @Immutable
    data class MigratingManga(
        val manga: Manga,
        val chapterCount: Int,
        val latestChapter: Double?,
        val source: String,
        val searchResult: SearchResult = SearchResult.Searching,
        val searchLabel: String? = null,
    )

    sealed interface SearchResult {
        data object Searching : SearchResult
        data object NotFound : SearchResult
        data class Success(
            val manga: Manga,
            val source: String,
            val chapterCount: Int,
            val latestChapter: Double?,
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
        val items: ImmutableList<MigratingManga> = persistentListOf(),
        val finishedCount: Int = 0,
        val migrationComplete: Boolean = false,
        val isMigrating: Boolean = false,
        val migrationProgress: Float = 0f,
        val dialog: Dialog? = null,
    )
}
