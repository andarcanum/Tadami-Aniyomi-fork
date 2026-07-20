package eu.kanade.tachiyomi.ui.browse.novel.migration.list

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.interactor.MigrateNovelUseCase
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorStore
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.ui.browse.novel.migration.NovelMigrationFlags
import eu.kanade.tachiyomi.ui.browse.novel.migration.list.search.SmartNovelSourceSearchEngine
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
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelMigrationListScreenModel(
    novelIds: Collection<Long>,
    private val sourceIds: Collection<Long>,
    private val extraSearchQuery: String?,
    val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val migrateNovel: MigrateNovelUseCase = MigrateNovelUseCase(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<NovelMigrationListScreenModel.State>(State()) {

    val items
        get() = state.value.items

    private val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags_novel", Int.MAX_VALUE)
    }

    private var migrateJob: Job? = null
    private var searchJob: Job? = null
    private var allItems: List<MigratingNovel> = emptyList()
    private val cancelledSearchIds = mutableSetOf<Long>()

    init {
        screenModelScope.launchIO {
            val items = novelIds
                .map { id ->
                    async {
                        val novel = getNovel.await(id) ?: return@async null
                        val chapterInfo = getChapterInfo(id)
                        MigratingNovel(
                            novel = novel,
                            chapterCount = chapterInfo.chapterCount,
                            latestChapter = chapterInfo.latestChapter,
                            source = sourceManager.getOrStub(novel.source).name,
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
        val chapters: List<NovelChapter> = novelChapterRepository.getChapterByNovelId(id)
        return ChapterInfo(
            latestChapter = chapters.maxOfOrNull { it.chapterNumber },
            chapterCount = chapters.size,
        )
    }

    private suspend fun getChapterInfo(
        source: NovelCatalogueSource,
        novel: Novel,
    ): ChapterInfo {
        return try {
            source.getChapterList(novel.toSNovel()).let { chapters ->
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
                migrationComplete = isNovelMigrationSearchComplete(searchItems),
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

    private suspend fun runSearches(items: List<MigratingNovel>) {
        val sources = getEnabledSources()
        val strategy = sourcePreferences.migrationStrategy()
        val useDeepSearch = sourcePreferences.migrationSearchKeywords().get()
        val useAutoMetadata = sourcePreferences.migrationExtraSearchParam().get()
        val hideNotFound = sourcePreferences.migrationHideNotFound().get()
        val onlyNewChapters = sourcePreferences.migrationOnlyNewChapters().get()

        var currentItems = items
        items.forEach { item ->
            if (item.novel.id in cancelledSearchIds) {
                currentItems = currentItems.map { current ->
                    if (current.novel.id == item.novel.id) {
                        current.copy(searchResult = SearchResult.NotFound, searchLabel = null)
                    } else {
                        current
                    }
                }
                publishSearchItems(currentItems, hideNotFound, onlyNewChapters)
                return@forEach
            }

            val result = searchSource(
                novel = item.novel,
                sources = sources,
                strategy = strategy,
                useDeepSearch = useDeepSearch,
                useAutoMetadata = useAutoMetadata,
                onProgress = { label ->
                    currentItems = currentItems.map { current ->
                        if (current.novel.id == item.novel.id) {
                            current.copy(searchLabel = label)
                        } else {
                            current
                        }
                    }
                    publishSearchItems(currentItems, hideNotFound, onlyNewChapters)
                },
            )
            if (item.novel.id in cancelledSearchIds) {
                currentItems = currentItems.map { current ->
                    if (current.novel.id == item.novel.id) {
                        current.copy(searchResult = SearchResult.NotFound, searchLabel = null)
                    } else {
                        current
                    }
                }
                publishSearchItems(currentItems, hideNotFound, onlyNewChapters)
                return@forEach
            }

            val updatedItem = when (result) {
                null -> item.copy(searchResult = SearchResult.NotFound, searchLabel = null)
                else -> item.copy(
                    searchLabel = null,
                    searchResult = SearchResult.Success(
                        novel = result.novel,
                        source = result.source.name,
                        chapterCount = result.chapterInfo.chapterCount,
                        latestChapter = result.chapterInfo.latestChapter,
                    ),
                )
            }

            currentItems = currentItems.map { current ->
                if (current.novel.id == item.novel.id) updatedItem else current
            }

            publishSearchItems(currentItems, hideNotFound, onlyNewChapters)
        }
    }

    private fun publishSearchItems(
        items: List<MigratingNovel>,
        hideNotFound: Boolean,
        onlyNewChapters: Boolean,
    ) {
        allItems = items
        val visibleItems = visibleNovelMigrationItems(
            items = items,
            hideNotFound = hideNotFound,
            onlyNewChapters = onlyNewChapters,
        ).toImmutableList()
        val finishedCount = visibleItems.count { it.searchResult != SearchResult.Searching }
        val migrationComplete = isNovelMigrationSearchComplete(visibleItems)

        mutableState.update { state ->
            state.copy(
                items = visibleItems,
                finishedCount = finishedCount,
                migrationComplete = migrationComplete,
            )
        }
    }

    private suspend fun searchSource(
        novel: Novel,
        sources: List<NovelCatalogueSource>,
        strategy: SourcePreferences.MigrationStrategy,
        useDeepSearch: Boolean,
        useAutoMetadata: Boolean,
        onProgress: (String?) -> Unit,
    ): NovelMigrationSearchCandidate? {
        val searchParams = buildNovelMigrationSearchParams(
            novel = novel,
            manualExtraSearchQuery = extraSearchQuery,
            useAutoMetadata = useAutoMetadata,
        )
        val searchEngine = SmartNovelSourceSearchEngine(searchParams)

        return when (strategy) {
            SourcePreferences.MigrationStrategy.FIRST_SOURCE -> {
                searchSourceSequentially(
                    novel = novel,
                    sources = sources,
                    searchEngine = searchEngine,
                    useDeepSearch = useDeepSearch,
                    onProgress = onProgress,
                )
            }
            SourcePreferences.MigrationStrategy.MOST_CHAPTERS -> {
                searchSourceByMostChapters(
                    novel = novel,
                    sources = sources,
                    searchEngine = searchEngine,
                    useDeepSearch = useDeepSearch,
                    onProgress = onProgress,
                )
            }
        }
    }

    private suspend fun searchSourceSequentially(
        novel: Novel,
        sources: List<NovelCatalogueSource>,
        searchEngine: SmartNovelSourceSearchEngine,
        useDeepSearch: Boolean,
        onProgress: (String?) -> Unit,
    ): NovelMigrationSearchCandidate? {
        for ((index, source) in sources.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(source.name)
            val result = searchSourceInCatalogue(
                novel = novel,
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
        novel: Novel,
        sources: List<NovelCatalogueSource>,
        searchEngine: SmartNovelSourceSearchEngine,
        useDeepSearch: Boolean,
        onProgress: (String?) -> Unit,
    ): NovelMigrationSearchCandidate? = kotlinx.coroutines.supervisorScope {
        onProgress("${sources.size} sources")
        val candidates = sources.mapIndexed { index, source ->
            async {
                currentCoroutineContext().ensureActive()
                searchSourceInCatalogue(
                    novel = novel,
                    source = source,
                    sourceIndex = index,
                    searchEngine = searchEngine,
                    useDeepSearch = useDeepSearch,
                )
            }
        }.awaitAll().filterNotNull()

        selectNovelMigrationSearchCandidate(
            candidates = candidates,
            strategy = SourcePreferences.MigrationStrategy.MOST_CHAPTERS,
        )
    }

    private suspend fun searchSourceInCatalogue(
        novel: Novel,
        source: NovelCatalogueSource,
        sourceIndex: Int,
        searchEngine: SmartNovelSourceSearchEngine,
        useDeepSearch: Boolean,
    ): NovelMigrationSearchCandidate? {
        currentCoroutineContext().ensureActive()
        val result = searchEngine.regularSearch(source, novel.title)
            ?: if (useDeepSearch) searchEngine.deepSearch(source, novel.title) else null
        currentCoroutineContext().ensureActive()
        if (result == null) return null
        if (result.url == novel.url && result.source == novel.source) return null

        val chapterInfo = getChapterInfo(source, result)
        currentCoroutineContext().ensureActive()
        return NovelMigrationSearchCandidate(
            sourceIndex = sourceIndex,
            source = source,
            novel = result,
            chapterInfo = chapterInfo,
        )
    }

    private fun getEnabledSources(): List<NovelCatalogueSource> {
        if (sourceIds.isNotEmpty()) {
            val byId = sourceManager.getCatalogueSources().associateBy { it.id }
            return sourceIds.mapNotNull { byId[it] }
        }

        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledNovelSources().get()
        val pinnedSources = sourcePreferences.pinnedNovelSources().get()

        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    fun migrateNovels() {
        migrateNovels(replace = true)
    }

    fun copyNovels() {
        migrateNovels(replace = false)
    }

    fun showMigrateDialog(copy: Boolean) {
        mutableState.update { state ->
            state.copy(
                dialog = Dialog.Migrate(
                    copy = copy,
                    totalCount = state.items.size,
                    skippedCount = novelMigrationSkippedCount(state.items),
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

    fun migrateNow(novelId: Long, replace: Boolean) {
        screenModelScope.launchIO {
            val item = items.find { it.novel.id == novelId } ?: return@launchIO
            val target = (item.searchResult as? SearchResult.Success)?.novel ?: return@launchIO
            val flags = getMigrationFlags(item.novel)
            migrateNovel.migrateNovel(item.novel, target, replace, flags)
            markUpdateErrorResolved(item.novel.id, replace)
            removeNovel(item)
        }
    }

    fun useNovelForMigration(current: Long, target: Long) {
        screenModelScope.launchIO {
            cancelledSearchIds += current
            if (allItems.none { it.novel.id == current }) return@launchIO
            val targetNovel = getNovel.await(target) ?: return@launchIO
            val source = sourceManager.get(targetNovel.source) as? NovelCatalogueSource ?: return@launchIO
            val chapterInfo = getChapterInfo(source, targetNovel)
            val updatedItems = allItems.map { item ->
                if (item.novel.id == current) {
                    item.copy(
                        searchLabel = null,
                        searchResult = SearchResult.Success(
                            novel = targetNovel,
                            source = source.name,
                            chapterCount = chapterInfo.chapterCount,
                            latestChapter = chapterInfo.latestChapter,
                        ),
                    )
                } else {
                    item
                }
            }
            publishSearchItems(
                items = updatedItems,
                hideNotFound = sourcePreferences.migrationHideNotFound().get(),
                onlyNewChapters = sourcePreferences.migrationOnlyNewChapters().get(),
            )
        }
    }

    fun cancelSearch(novelId: Long) {
        cancelledSearchIds += novelId
        val updatedItems = allItems.map { item ->
            if (item.novel.id == novelId && item.searchResult == SearchResult.Searching) {
                item.copy(searchResult = SearchResult.NotFound, searchLabel = null)
            } else {
                item
            }
        }
        publishSearchItems(
            items = updatedItems,
            hideNotFound = sourcePreferences.migrationHideNotFound().get(),
            onlyNewChapters = sourcePreferences.migrationOnlyNewChapters().get(),
        )
    }

    fun removeNovel(novelId: Long) {
        screenModelScope.launchIO {
            val item = items.find { it.novel.id == novelId } ?: return@launchIO
            removeNovel(item)
        }
    }

    private fun migrateNovels(replace: Boolean) {
        migrateJob = screenModelScope.launchIO {
            val items = state.value.items
            val migratedItems = mutableListOf<MigratingNovel>()
            mutableState.update { it.copy(isMigrating = true, migrationProgress = 0f, dialog = null) }

            try {
                items.forEachIndexed { index, item ->
                    val target = (item.searchResult as? SearchResult.Success)?.novel ?: return@forEachIndexed
                    val flags = getMigrationFlags(item.novel)
                    migrateNovel.migrateNovel(item.novel, target, replace, flags)
                    markUpdateErrorResolved(item.novel.id, replace)
                    migratedItems += item
                    mutableState.update {
                        it.copy(migrationProgress = ((index + 1).toFloat() / items.size).coerceAtMost(1f))
                    }
                }
            } finally {
                removeMigratedNovel(migratedItems)
                mutableState.update { it.copy(isMigrating = false, dialog = null) }
                migrateJob = null
            }
        }
    }

    /**
     * Computes the effective migration flags for a single entry without writing the narrowed
     * bitmap back to preferences.
     */
    private fun getMigrationFlags(novel: Novel): Int {
        val applicableFlags = NovelMigrationFlags.getFlags(novel, migrateFlags.get())
        return NovelMigrationFlags.getSelectedFlagsBitMap(
            selectedFlags = applicableFlags.map { it.isDefaultSelected },
            flags = applicableFlags,
        )
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
        mutableState.update { it.copy(isMigrating = false) }
    }

    private fun markUpdateErrorResolved(novelId: Long, replace: Boolean) {
        if (!replace) return
        LibraryUpdateErrorStore.markResolved(
            media = LibraryUpdateErrorMedia.Novel,
            entryId = novelId,
        )
    }

    private fun removeNovel(item: MigratingNovel) {
        removeMigratedNovel(listOf(item))
    }

    private fun removeMigratedNovel(items: Collection<MigratingNovel>) {
        if (items.isEmpty()) return
        val migratedIds = items.mapTo(mutableSetOf()) { it.novel.id }
        allItems = allItems.filterNot { it.novel.id in migratedIds }
        mutableState.update { state ->
            val updatedItems = state.items.filterNot { it.novel.id in migratedIds }.toPersistentList()
            val finishedCount = updatedItems.count { it.searchResult != SearchResult.Searching }
            state.copy(
                items = updatedItems,
                finishedCount = finishedCount,
                migrationComplete = isNovelMigrationSearchComplete(updatedItems),
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
    data class MigratingNovel(
        val novel: Novel,
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
            val novel: Novel,
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
        val items: ImmutableList<MigratingNovel> = persistentListOf(),
        val finishedCount: Int = 0,
        val migrationComplete: Boolean = false,
        val isMigrating: Boolean = false,
        val migrationProgress: Float = 0f,
        val dialog: Dialog? = null,
    )
}
