package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeAlbumSearchSource
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.AnimeFeedErrorClassifierSource
import eu.kanade.tachiyomi.animesource.AnimeFeedLoginSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeFeedWebLoginSource
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.FeedError
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class ReelsAlbumsTab {
    Saved,
    Discover,
}

enum class ReelsAlbumsSort {
    DateDesc,
    DateAsc,
    Name,
    Source,
}

enum class RemoteSourceKind {
    Browse,
    Search,
}

sealed interface RemoteSourceStatus {
    data object Idle : RemoteSourceStatus
    data object Loading : RemoteSourceStatus
    data object Ready : RemoteSourceStatus
    data object NeedsAuth : RemoteSourceStatus
    data class RateLimited(val retryAtEpochMillis: Long? = null) : RemoteSourceStatus
    data class Error(val message: String, val canRetry: Boolean = true) : RemoteSourceStatus
}

@Immutable
data class RemoteAlbumSourceGroup(
    val sourceId: Long,
    val sourceName: String,
    val supportsBrowse: Boolean,
    val supportsSearch: Boolean,
    val supportsLogin: Boolean = false,
    val supportsWebLogin: Boolean = false,
    val categories: ImmutableList<FeedCategory> = persistentListOf(),
    val pagination: ReelsPagination = ReelsPagination(),
    val status: RemoteSourceStatus = RemoteSourceStatus.Idle,
    val isPageLoading: Boolean = false,
    val pageError: String? = null,
    val pageRateLimitedUntil: Long? = null,
    val activeMode: RemoteSourceKind = RemoteSourceKind.Browse,
)

@Immutable
data class AlbumFilterSourceChip(
    val sourceId: Long,
    val name: String,
)

@Immutable
data class ReelsAlbumsLoginState(
    val sourceId: Long,
    val sourceName: String,
    val isLoggingIn: Boolean = false,
    val error: String? = null,
    val rejected: Boolean = false,
)

/**
 * Short-lived in-memory cache for cross-plugin remote album browse/search results so back
 * navigation and quick re-entry avoid re-fanning out across all installed Reels plugins.
 */
class ReelsRemoteAlbumsCache(
    private val maxEntries: Int = 32,
) {
    data class Key(
        val sourceId: Long,
        val mode: RemoteSourceKind,
        val query: String,
    )

    data class Entry(
        val categories: ImmutableList<FeedCategory>,
        val pagination: ReelsPagination,
        val cachedAtMs: Long,
    )

    private val entries = LinkedHashMap<Key, Entry>(16, 0.75f, true)

    @Synchronized
    fun getIfFresh(key: Key, nowMs: Long, ttlMs: Long): Entry? {
        val entry = entries[key] ?: return null
        if (nowMs - entry.cachedAtMs > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry
    }

    @Synchronized
    fun put(key: Key, categories: ImmutableList<FeedCategory>, pagination: ReelsPagination, nowMs: Long) {
        entries[key] = Entry(categories = categories, pagination = pagination, cachedAtMs = nowMs)
        while (entries.size > maxEntries) {
            val eldest = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldest)
        }
    }

    @Synchronized
    fun invalidateSource(sourceId: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.sourceId == sourceId) {
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

/**
 * Unified cross-plugin Reels Albums manager:
 * 1) Subscribes to all saved albums across every installed/uninstalled source via
 *    [ReelsAlbumRepository.subscribeAll], with source chips, badges, search, sort, and cleanup.
 * 2) Fans out live browse (`AnimeFeedBrowseSource`) and search (`AnimeAlbumSearchSource` plus
 *    local directory fallback for browse-only plugins) across all enabled Reels plugins using
 *    bounded concurrency, `AnimeFeedLoginSource` pre-check, `FeedError` isolation per source,
 *    and a short-TTL in-memory session cache.
 */
class ReelsAlbumsScreenModel(
    val sourceId: Long? = null,
    private val albumRepository: ReelsAlbumRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val disabledSourcesFlow: Flow<Set<String>>? = null,
    private val sourceIconProvider: ((Long) -> ImageBitmap?)? = null,
    private val remoteCache: ReelsRemoteAlbumsCache = sharedRemoteCache,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val cacheTtlMs: Long = DEFAULT_REMOTE_CACHE_TTL_MS,
) : StateScreenModel<ReelsAlbumsScreenModel.State>(State()) {

    @Immutable
    data class State(
        val savedAlbums: ImmutableList<ReelsAlbum> = persistentListOf(),
        val selectedSourceFilterId: Long? = null,
        val savedSearchQuery: String = "",
        val savedSort: ReelsAlbumsSort = ReelsAlbumsSort.DateDesc,
        val missingSourceIds: ImmutableSet<Long> = persistentSetOf(),
        val remoteSearchQuery: String = "",
        val remoteGroups: ImmutableList<RemoteAlbumSourceGroup> = persistentListOf(),
        val isRemoteRefreshing: Boolean = false,
        val sourceNames: ImmutableMap<Long, String> = persistentMapOf(),
        val sourceIcons: ImmutableMap<Long, ImageBitmap> = persistentMapOf(),
        val filterSources: ImmutableList<AlbumFilterSourceChip> = persistentListOf(),
        val loginPrompt: ReelsAlbumsLoginState? = null,
        val cleanupDeletedCount: Int? = null,
    ) {
        val savedKeys: Set<Pair<Long, String>>
            get() = savedAlbums.mapTo(HashSet()) { it.sourceId to it.albumId }

        val visibleSavedAlbums: ImmutableList<ReelsAlbum>
            get() = filterAndSortReelsAlbums(
                albums = savedAlbums,
                selectedSourceId = selectedSourceFilterId,
                searchQuery = savedSearchQuery,
                sort = savedSort,
                sourceNames = sourceNames,
            ).toImmutableList()

        val visibleRemoteGroups: ImmutableList<RemoteAlbumSourceGroup>
            get() = if (selectedSourceFilterId == null) {
                remoteGroups
            } else {
                remoteGroups.filter { it.sourceId == selectedSourceFilterId }.toImmutableList()
            }
    }

    private val boundedIoDispatcher = ioDispatcher.limitedParallelism(MAX_REMOTE_CONCURRENCY)
    private val remoteConcurrencySemaphore = Semaphore(MAX_REMOTE_CONCURRENCY)
    private val remoteGeneration = AtomicInteger(0)
    private val sourceAppendGenerations = ConcurrentHashMap<Long, AtomicInteger>()
    private val appendInFlightGenerations = ConcurrentHashMap<Long, Long>()
    private val appendTokenSeq = AtomicLong(0L)
    private val savedToggleMutex = Mutex()
    private val cacheResetMutex = Mutex()

    private var remoteFanoutJob: Job? = null
    private var searchDebounceJob: Job? = null

    @Volatile
    private var activeRemoteSources: List<AnimeFeedSource> = emptyList()

    private val resolvedDisabledSourcesFlow: Flow<Set<String>> by lazy {
        disabledSourcesFlow ?: runCatching {
            Injekt.get<SourcePreferences>().disabledAnimeSources().changes()
        }.getOrElse { flowOf(emptySet()) }
    }

    private val lazyExtensionManager: AnimeExtensionManager? by lazy {
        runCatching { Injekt.get<AnimeExtensionManager>() }.getOrNull()
    }

    private val resolvedIconProvider: (Long) -> ImageBitmap? = { id ->
        if (sourceIconProvider != null) {
            sourceIconProvider.invoke(id)
        } else {
            runCatching {
                lazyExtensionManager?.getAppIconForSource(id)?.toBitmap()?.asImageBitmap()
            }.getOrNull()
        }
    }

    init {
        observeSavedAndSources()
    }

    private fun observeSavedAndSources() {
        screenModelScope.launch(ioDispatcher) {
            combine(
                albumRepository.subscribeAll(),
                sourceManager.sources,
                resolvedDisabledSourcesFlow,
                sourceManager.isInitialized,
            ) { albums, sources, disabledIds, initialized ->
                ObsSnapshot(albums, sources, disabledIds, initialized)
            }.collectLatest { snapshot ->
                val allSourcesById = snapshot.sources.associateBy { it.id }
                val activeFeedSources = snapshot.sources
                    .filterIsInstance<AnimeFeedSource>()
                    .filterNot { it.id.toString() in snapshot.disabledIds }
                    .sortedWith(
                        compareByDescending<AnimeFeedSource> {
                            it.id == sourceId
                        }.thenBy { it.name.lowercase() },
                    )

                val relevantSourceIds = buildSet {
                    activeFeedSources.forEach { add(it.id) }
                    snapshot.albums.forEach { add(it.sourceId) }
                }

                val names = relevantSourceIds.mapNotNull { sid ->
                    allSourcesById[sid]?.name?.let { sid to it }
                }.toMap().toImmutableMap()

                val icons = relevantSourceIds.mapNotNull { sid ->
                    resolvedIconProvider(sid)?.let { sid to it }
                }.toMap().toImmutableMap()

                val filterChips = activeFeedSources
                    .map { AlbumFilterSourceChip(it.id, it.name) }
                    .plus(
                        snapshot.albums
                            .asSequence()
                            .map { it.sourceId }
                            .distinct()
                            .filter { sid -> activeFeedSources.none { it.id == sid } && sid in names }
                            .map { sid -> AlbumFilterSourceChip(sid, names[sid].orEmpty()) },
                    )
                    .distinctBy { it.sourceId }
                    .toImmutableList()

                val missingIds = if (snapshot.initialized) {
                    snapshot.albums
                        .asSequence()
                        .map { it.sourceId }
                        .filter { sid -> allSourcesById[sid] !is AnimeFeedSource }
                        .toImmutableSet()
                } else {
                    persistentSetOf()
                }

                ensureActive()
                val previousIds = activeRemoteSources.map { it.id }
                val newIds = activeFeedSources.map { it.id }
                val sourcesChanged = previousIds != newIds
                activeRemoteSources = activeFeedSources

                mutableState.update { current ->
                    val validFilterId = current.selectedSourceFilterId?.takeIf { fid ->
                        filterChips.any { it.sourceId == fid } || snapshot.albums.any { it.sourceId == fid }
                    }
                    current.copy(
                        savedAlbums = snapshot.albums.toImmutableList(),
                        sourceNames = names,
                        sourceIcons = icons,
                        filterSources = filterChips,
                        missingSourceIds = missingIds,
                        selectedSourceFilterId = validFilterId,
                    )
                }

                if (sourcesChanged || state.value.remoteGroups.isEmpty()) {
                    loadRemote(forceRefresh = false)
                }
            }
        }
    }

    fun selectSourceFilter(sourceFilterId: Long?) {
        val currentFilter = state.value.selectedSourceFilterId
        if (currentFilter == sourceFilterId) return
        mutableState.update { it.copy(selectedSourceFilterId = sourceFilterId) }
        // If switching to a specific plugin whose query was already entered, ensure its group exists.
        if (state.value.remoteGroups.isEmpty() && activeRemoteSources.isNotEmpty()) {
            loadRemote(forceRefresh = false)
        }
    }

    fun setSavedSearchQuery(query: String) {
        mutableState.update { it.copy(savedSearchQuery = query) }
    }

    fun setSavedSort(sort: ReelsAlbumsSort) {
        mutableState.update { it.copy(savedSort = sort) }
    }

    fun setRemoteSearchQuery(query: String) {
        val previous = state.value.remoteSearchQuery
        mutableState.update { it.copy(remoteSearchQuery = query) }
        if (previous.trim() == query.trim()) return
        searchDebounceJob?.cancel()
        searchDebounceJob = screenModelScope.launch(ioDispatcher) {
            delay(SEARCH_DEBOUNCE_MS)
            loadRemote(forceRefresh = false)
        }
    }

    fun submitRemoteSearch() {
        searchDebounceJob?.cancel()
        loadRemote(forceRefresh = false)
    }

    /** Explicitly reloads remote categories/search across enabled plugins, bypassing the TTL cache. */
    fun loadCatalog() {
        searchDebounceJob?.cancel()
        loadRemote(forceRefresh = true)
    }

    fun refreshRemote() {
        loadCatalog()
    }

    /** Retry a single plugin group without disturbing other plugins' results. */
    fun retryRemoteSource(targetSourceId: Long) {
        val source = activeRemoteSources.firstOrNull { it.id == targetSourceId } ?: return
        val query = state.value.remoteSearchQuery.trim()
        val generation = remoteGeneration.get()
        remoteCache.invalidateSource(targetSourceId)
        sourceAppendGenerations.getOrPut(targetSourceId) { AtomicInteger(0) }.incrementAndGet()
        appendInFlightGenerations.remove(targetSourceId)

        val placeholder = buildPlaceholderGroup(source, query, RemoteSourceStatus.Loading)
        upsertRemoteGroup(placeholder)

        screenModelScope.launch(boundedIoDispatcher) {
            val loaded = remoteConcurrencySemaphore.withPermit {
                fetchSourceInitialGroup(source, query, forceRefresh = true)
            }
            if (remoteGeneration.get() != generation) return@launch
            upsertRemoteGroup(loaded)
        }
    }

    /** Appends the next page for a specific remote plugin group (preserves other sources on error). */
    fun loadCatalogMore(targetSourceId: Long) {
        val group = state.value.remoteGroups.firstOrNull { it.sourceId == targetSourceId } ?: return
        if (group.isPageLoading || !group.pagination.canLoadMore) return
        val source = activeRemoteSources.firstOrNull { it.id == targetSourceId } ?: return
        val appendToken = appendTokenSeq.incrementAndGet()
        if (appendInFlightGenerations.putIfAbsent(targetSourceId, appendToken) != null) return

        val globalGen = remoteGeneration.get()
        val appendGen = sourceAppendGenerations
            .getOrPut(targetSourceId) { AtomicInteger(0) }
            .incrementAndGet()
        if (remoteGeneration.get() != globalGen || appendInFlightGenerations[targetSourceId] != appendToken) {
            appendInFlightGenerations.remove(targetSourceId, appendToken)
            return
        }
        val query = state.value.remoteSearchQuery.trim()
        val page = group.pagination.nextPage
        val cursor = if (group.pagination.cursorMode) group.pagination.nextCursor else null

        upsertRemoteGroup(group.copy(isPageLoading = true, pageError = null, pageRateLimitedUntil = null))

        screenModelScope.launch(boundedIoDispatcher) {
            try {
                val pageData = remoteConcurrencySemaphore.withPermit {
                    when (group.activeMode) {
                        RemoteSourceKind.Search -> {
                            val searchSource = source as? AnimeAlbumSearchSource
                                ?: throw IllegalStateException("Source does not support album search")
                            searchSource.searchAlbums(query, page, cursor)
                        }
                        RemoteSourceKind.Browse -> {
                            val browseSource = source as? AnimeFeedBrowseSource
                                ?: throw IllegalStateException("Source does not support album browse")
                            browseSource.getBrowseCategories(page, cursor)
                        }
                    }
                }
                currentCoroutineContextEnsureActive()
                if (!isCurrentAppend(targetSourceId, globalGen, appendGen, appendToken)) return@launch

                updateRemoteGroup(targetSourceId) { current ->
                    val step = current.pagination.advance(pageData.hasNextPage, pageData.nextCursor)
                    if (step.cursorLost) {
                        logcat(LogPriority.WARN) {
                            "Albums catalog of ${source.id} dropped cursor mid-stream; stopping pagination."
                        }
                    }
                    val merged = (current.categories + pageData.categories)
                        .distinctBy { it.id }
                        .toImmutableList()
                    if ((group.activeMode == RemoteSourceKind.Browse && query.isEmpty()) ||
                        group.activeMode == RemoteSourceKind.Search
                    ) {
                        remoteCache.put(
                            key = ReelsRemoteAlbumsCache.Key(source.id, current.activeMode, query),
                            categories = merged,
                            pagination = step.pagination,
                            nowMs = nowEpochMs(),
                        )
                    }
                    current.copy(
                        categories = merged,
                        pagination = step.pagination,
                        status = RemoteSourceStatus.Ready,
                        isPageLoading = false,
                        pageError = null,
                        pageRateLimitedUntil = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (!isCurrentAppend(targetSourceId, globalGen, appendGen, appendToken)) return@launch
                logcat(LogPriority.ERROR, throwable = t) {
                    "Failed to append albums for source ${source.id}"
                }
                val verdict = classifyError(source, t)
                val message = t.localizedMessage.orEmpty().ifBlank { "Failed to load" }
                updateRemoteGroup(targetSourceId) { current ->
                    when (verdict) {
                        FeedError.AuthRequired -> current.copy(
                            status = RemoteSourceStatus.NeedsAuth,
                            isPageLoading = false,
                            pageError = null,
                        )
                        is FeedError.RateLimited -> current.copy(
                            isPageLoading = false,
                            pageError = message,
                            pageRateLimitedUntil = verdict.retryAtEpochMillis,
                        )
                        FeedError.ExpiredCursor -> current.copy(
                            isPageLoading = false,
                            pageError = null,
                            pagination = current.pagination.copy(canLoadMore = false),
                        )
                        FeedError.Transient,
                        FeedError.Terminal,
                        null,
                        -> current.copy(
                            isPageLoading = false,
                            pageError = message,
                        )
                    }
                }
            } finally {
                appendInFlightGenerations.remove(targetSourceId, appendToken)
            }
        }
    }

    private fun isCurrentAppend(
        targetSourceId: Long,
        globalGen: Int,
        appendGen: Int,
        appendToken: Long,
    ): Boolean =
        remoteGeneration.get() == globalGen &&
            sourceAppendGenerations[targetSourceId]?.get() == appendGen &&
            appendInFlightGenerations[targetSourceId] == appendToken

    internal fun isAppendInFlight(targetSourceId: Long): Boolean =
        appendInFlightGenerations.containsKey(targetSourceId)

    /** Convenience overload for legacy callers targeting [sourceId]. */
    fun loadCatalogMore() {
        val target = sourceId ?: state.value.remoteGroups.firstOrNull()?.sourceId ?: return
        loadCatalogMore(target)
    }

    private fun loadRemote(forceRefresh: Boolean) {
        val generation = remoteGeneration.incrementAndGet()
        remoteFanoutJob?.cancel()
        appendInFlightGenerations.clear()

        val query = state.value.remoteSearchQuery.trim()
        val eligibleSources = selectEligibleRemoteSources(activeRemoteSources, query)

        if (eligibleSources.isEmpty()) {
            mutableState.update {
                it.copy(remoteGroups = persistentListOf(), isRemoteRefreshing = false)
            }
            return
        }

        if (forceRefresh) {
            eligibleSources.forEach { remoteCache.invalidateSource(it.id) }
        }

        val initialGroups = eligibleSources.map { source ->
            val mode = resolveRemoteMode(source, query)
            val cacheKey = if (mode == RemoteSourceKind.Browse && query.isNotEmpty()) {
                ReelsRemoteAlbumsCache.Key(source.id, RemoteSourceKind.Browse, "")
            } else {
                ReelsRemoteAlbumsCache.Key(source.id, mode, query)
            }
            val loginRequired = needsLoginPrecheck(source)
            val cached = if (!forceRefresh && !loginRequired) {
                remoteCache.getIfFresh(cacheKey, nowEpochMs(), cacheTtlMs)
            } else {
                null
            }
            when {
                loginRequired -> buildPlaceholderGroup(source, query, RemoteSourceStatus.NeedsAuth)
                cached != null -> {
                    val categories = if (mode == RemoteSourceKind.Browse && query.isNotEmpty()) {
                        filterFeedCategoriesLocally(cached.categories, query).toImmutableList()
                    } else {
                        cached.categories
                    }
                    val pagination = if (mode == RemoteSourceKind.Browse && query.isNotEmpty()) {
                        ReelsPagination(canLoadMore = false)
                    } else {
                        cached.pagination
                    }
                    buildPlaceholderGroup(source, query, RemoteSourceStatus.Ready).copy(
                        categories = categories,
                        pagination = pagination,
                    )
                }
                else -> buildPlaceholderGroup(source, query, RemoteSourceStatus.Loading)
            }
        }

        val needsFetch = eligibleSources.filterIndexed { index, _ ->
            initialGroups[index].status is RemoteSourceStatus.Loading
        }

        mutableState.update {
            it.copy(
                remoteGroups = initialGroups.toImmutableList(),
                isRemoteRefreshing = needsFetch.isNotEmpty(),
            )
        }

        if (needsFetch.isEmpty()) return

        remoteFanoutJob = screenModelScope.launch(boundedIoDispatcher) {
            try {
                coroutineScope {
                    needsFetch.map { source ->
                        async {
                            val group = remoteConcurrencySemaphore.withPermit {
                                fetchSourceInitialGroup(source, query, forceRefresh)
                            }
                            if (remoteGeneration.get() == generation) {
                                upsertRemoteGroup(group)
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                if (remoteGeneration.get() == generation) {
                    mutableState.update { it.copy(isRemoteRefreshing = false) }
                }
            }
        }
    }

    private suspend fun fetchSourceInitialGroup(
        source: AnimeFeedSource,
        query: String,
        forceRefresh: Boolean,
    ): RemoteAlbumSourceGroup {
        val mode = resolveRemoteMode(source, query)
        val base = buildPlaceholderGroup(source, query, RemoteSourceStatus.Ready)

        if (needsLoginPrecheck(source)) {
            return base.copy(status = RemoteSourceStatus.NeedsAuth, pagination = ReelsPagination(canLoadMore = false))
        }

        if (!forceRefresh) {
            val cacheKey = if (mode == RemoteSourceKind.Browse && query.isNotEmpty()) {
                ReelsRemoteAlbumsCache.Key(source.id, RemoteSourceKind.Browse, "")
            } else {
                ReelsRemoteAlbumsCache.Key(source.id, mode, query)
            }
            val cached = remoteCache.getIfFresh(cacheKey, nowEpochMs(), cacheTtlMs)
            if (cached != null) {
                val cats = if (mode == RemoteSourceKind.Browse && query.isNotEmpty()) {
                    filterFeedCategoriesLocally(cached.categories, query).toImmutableList()
                } else {
                    cached.categories
                }
                val pag = if (mode == RemoteSourceKind.Browse && query.isNotEmpty()) {
                    ReelsPagination(canLoadMore = false)
                } else {
                    cached.pagination
                }
                return base.copy(categories = cats, pagination = pag, status = RemoteSourceStatus.Ready)
            }
        }

        return try {
            when {
                query.isNotEmpty() && source is AnimeAlbumSearchSource -> {
                    val pageData = source.searchAlbums(query, 1, null)
                    val step = ReelsPagination().advance(pageData.hasNextPage, pageData.nextCursor)
                    val items = pageData.categories.distinctBy { it.id }.toImmutableList()
                    remoteCache.put(
                        key = ReelsRemoteAlbumsCache.Key(source.id, RemoteSourceKind.Search, query),
                        categories = items,
                        pagination = step.pagination,
                        nowMs = nowEpochMs(),
                    )
                    base.copy(
                        categories = items,
                        pagination = step.pagination,
                        status = RemoteSourceStatus.Ready,
                    )
                }
                source is AnimeFeedBrowseSource -> {
                    val pageData = source.getBrowseCategories(1, null)
                    val step = ReelsPagination().advance(pageData.hasNextPage, pageData.nextCursor)
                    val items = pageData.categories.distinctBy { it.id }.toImmutableList()
                    remoteCache.put(
                        key = ReelsRemoteAlbumsCache.Key(source.id, RemoteSourceKind.Browse, ""),
                        categories = items,
                        pagination = step.pagination,
                        nowMs = nowEpochMs(),
                    )
                    val visibleItems = if (query.isNotEmpty()) {
                        filterFeedCategoriesLocally(items, query).toImmutableList()
                    } else {
                        items
                    }
                    val visiblePagination = if (query.isNotEmpty()) {
                        ReelsPagination(canLoadMore = false)
                    } else {
                        step.pagination
                    }
                    base.copy(
                        categories = visibleItems,
                        pagination = visiblePagination,
                        status = RemoteSourceStatus.Ready,
                    )
                }
                else -> base.copy(
                    status = RemoteSourceStatus.Error("Source unavailable", canRetry = false),
                    pagination = ReelsPagination(canLoadMore = false),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logcat(LogPriority.ERROR, throwable = t) {
                "Failed to load albums catalog of ${source.id}"
            }
            val verdict = classifyError(source, t)
            val status = when (verdict) {
                FeedError.AuthRequired -> RemoteSourceStatus.NeedsAuth
                is FeedError.RateLimited -> RemoteSourceStatus.RateLimited(verdict.retryAtEpochMillis)
                FeedError.Terminal -> RemoteSourceStatus.Error(
                    message = t.localizedMessage.orEmpty().ifBlank { "Failed to load" },
                    canRetry = false,
                )
                FeedError.ExpiredCursor,
                FeedError.Transient,
                null,
                -> RemoteSourceStatus.Error(
                    message = t.localizedMessage.orEmpty().ifBlank { "Failed to load" },
                    canRetry = true,
                )
            }
            base.copy(
                status = status,
                pagination = ReelsPagination(canLoadMore = false),
            )
        }
    }

    private fun needsLoginPrecheck(source: AnimeFeedSource): Boolean {
        val loginSource = source as? AnimeFeedLoginSource ?: return false
        return !runCatching { loginSource.isLoggedIn() }.getOrDefault(false)
    }

    private fun classifyError(source: AnimeFeedSource, error: Throwable): FeedError? {
        val classifier = source as? AnimeFeedErrorClassifierSource ?: return null
        return runCatching { classifier.classifyFeedError(error) }.getOrNull()
    }

    private fun buildPlaceholderGroup(
        source: AnimeFeedSource,
        query: String,
        status: RemoteSourceStatus,
    ): RemoteAlbumSourceGroup {
        val mode = resolveRemoteMode(source, query)
        return RemoteAlbumSourceGroup(
            sourceId = source.id,
            sourceName = source.name,
            supportsBrowse = source is AnimeFeedBrowseSource,
            supportsSearch = source is AnimeAlbumSearchSource,
            supportsLogin = source is AnimeFeedLoginSource,
            supportsWebLogin = source is AnimeFeedWebLoginSource,
            status = status,
            activeMode = mode,
        )
    }

    private fun upsertRemoteGroup(group: RemoteAlbumSourceGroup) {
        mutableState.update { current ->
            val existing = current.remoteGroups
            val idx = existing.indexOfFirst { it.sourceId == group.sourceId }
            val next = if (idx >= 0) {
                existing.toMutableList().apply { set(idx, group) }.toImmutableList()
            } else {
                (existing + group).toImmutableList()
            }
            current.copy(remoteGroups = next)
        }
    }

    private inline fun updateRemoteGroup(
        sourceId: Long,
        transform: (RemoteAlbumSourceGroup) -> RemoteAlbumSourceGroup,
    ) {
        mutableState.update { current ->
            val existing = current.remoteGroups
            val idx = existing.indexOfFirst { it.sourceId == sourceId }
            if (idx < 0) return@update current
            val updated = existing.toMutableList().apply {
                set(idx, transform(get(idx)))
            }.toImmutableList()
            current.copy(remoteGroups = updated)
        }
    }

    fun openLoginPrompt(targetSourceId: Long) {
        val source = activeRemoteSources.firstOrNull { it.id == targetSourceId } ?: return
        if (source !is AnimeFeedLoginSource) return
        mutableState.update {
            it.copy(
                loginPrompt = ReelsAlbumsLoginState(
                    sourceId = source.id,
                    sourceName = source.name,
                ),
            )
        }
    }

    fun dismissLoginPrompt() {
        mutableState.update { it.copy(loginPrompt = null) }
    }

    fun submitLogin(email: String, password: String) {
        val prompt = state.value.loginPrompt ?: return
        val source = activeRemoteSources.firstOrNull { it.id == prompt.sourceId } as? AnimeFeedLoginSource ?: return
        mutableState.update {
            it.copy(loginPrompt = prompt.copy(isLoggingIn = true, error = null, rejected = false))
        }
        screenModelScope.launch(ioDispatcher) {
            val result = runCatching { source.login(email.trim(), password) }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val ok = result.getOrNull()
            when {
                ok == true -> {
                    mutableState.update { it.copy(loginPrompt = null) }
                    retryRemoteSource(prompt.sourceId)
                }
                ok == false -> {
                    mutableState.update {
                        it.copy(
                            loginPrompt = it.loginPrompt?.copy(
                                isLoggingIn = false,
                                error = null,
                                rejected = true,
                            ),
                        )
                    }
                }
                else -> {
                    val msg = result.exceptionOrNull()?.localizedMessage.orEmpty()
                    mutableState.update {
                        it.copy(
                            loginPrompt = it.loginPrompt?.copy(
                                isLoggingIn = false,
                                error = msg.ifBlank { "Login failed" },
                                rejected = false,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun addAlbum(targetSourceId: Long, category: FeedCategory) {
        screenModelScope.launch(ioDispatcher) {
            savedToggleMutex.withLock {
                albumRepository.insert(
                    ReelsAlbum(
                        sourceId = targetSourceId,
                        albumId = category.id,
                        name = category.name,
                        coverUrl = category.imageUrl,
                        addedAt = Date(),
                    ),
                )
            }
        }
    }

    /** Legacy single-source overload preserved for callers passing [sourceId] at construction. */
    fun addAlbum(category: FeedCategory) {
        val target = sourceId ?: state.value.remoteGroups.firstOrNull()?.sourceId ?: return
        addAlbum(target, category)
    }

    fun toggleSaveAlbum(targetSourceId: Long, category: FeedCategory) {
        screenModelScope.launch(ioDispatcher) {
            savedToggleMutex.withLock {
                val exists = albumRepository.getBySource(targetSourceId).any { it.albumId == category.id }
                if (exists) {
                    albumRepository.delete(targetSourceId, category.id)
                } else {
                    albumRepository.insert(
                        ReelsAlbum(
                            sourceId = targetSourceId,
                            albumId = category.id,
                            name = category.name,
                            coverUrl = category.imageUrl,
                            addedAt = Date(),
                        ),
                    )
                }
            }
        }
    }

    fun removeAlbum(album: ReelsAlbum) {
        screenModelScope.launch(ioDispatcher) {
            savedToggleMutex.withLock {
                albumRepository.delete(album.sourceId, album.albumId)
            }
        }
    }

    fun cleanupMissingSources() {
        screenModelScope.launch(ioDispatcher) {
            sourceManager.isInitialized.first { it }
            val allAlbums = albumRepository.getAll()
            val missingSources = allAlbums
                .map { it.sourceId }
                .distinct()
                .filter { sourceManager.get(it) !is AnimeFeedSource }
                .toSet()
            val removedEntries = allAlbums.count { it.sourceId in missingSources }
            missingSources.forEach { albumRepository.deleteBySource(it) }
            mutableState.update { it.copy(cleanupDeletedCount = removedEntries) }
        }
    }

    fun onCleanupNoticeShown() {
        mutableState.update { it.copy(cleanupDeletedCount = null) }
    }

    /** Clears the shared session cache (intended for tests or global resets). */
    suspend fun clearSessionCache() {
        cacheResetMutex.withLock {
            remoteCache.clear()
        }
    }

    private data class ObsSnapshot(
        val albums: List<ReelsAlbum>,
        val sources: List<eu.kanade.tachiyomi.animesource.AnimeSource>,
        val disabledIds: Set<String>,
        val initialized: Boolean,
    )

    private suspend fun currentCoroutineContextEnsureActive() {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
    }

    companion object {
        const val MAX_REMOTE_CONCURRENCY = 4
        const val DEFAULT_REMOTE_CACHE_TTL_MS = 60_000L
        private const val SEARCH_DEBOUNCE_MS = 300L
        internal val sharedRemoteCache = ReelsRemoteAlbumsCache()
    }
}

internal fun selectEligibleRemoteSources(
    sources: List<AnimeFeedSource>,
    query: String,
): List<AnimeFeedSource> {
    val trimmed = query.trim()
    return if (trimmed.isEmpty()) {
        sources.filter { it is AnimeFeedBrowseSource }
    } else {
        sources.filter { it is AnimeAlbumSearchSource || it is AnimeFeedBrowseSource }
    }
}

internal fun resolveRemoteMode(source: AnimeFeedSource, query: String): RemoteSourceKind {
    val trimmed = query.trim()
    return if (trimmed.isNotEmpty() && source is AnimeAlbumSearchSource) {
        RemoteSourceKind.Search
    } else {
        RemoteSourceKind.Browse
    }
}

internal fun filterFeedCategoriesLocally(
    categories: List<FeedCategory>,
    query: String,
): List<FeedCategory> {
    val needle = query.trim().removePrefix("@").trim()
    if (needle.isEmpty()) return categories
    return categories.filter {
        it.name.contains(needle, ignoreCase = true) ||
            it.id.contains(needle, ignoreCase = true)
    }
}

internal fun filterAndSortReelsAlbums(
    albums: List<ReelsAlbum>,
    selectedSourceId: Long?,
    searchQuery: String,
    sort: ReelsAlbumsSort,
    sourceNames: Map<Long, String>,
): List<ReelsAlbum> {
    val needle = searchQuery.trim().removePrefix("@").trim()
    val filtered = albums.asSequence()
        .filter { album -> selectedSourceId == null || album.sourceId == selectedSourceId }
        .filter { album ->
            if (needle.isEmpty()) {
                true
            } else {
                val sourceName = sourceNames[album.sourceId].orEmpty()
                album.name.contains(needle, ignoreCase = true) ||
                    album.albumId.contains(needle, ignoreCase = true) ||
                    sourceName.contains(needle, ignoreCase = true)
            }
        }
        .toList()

    return when (sort) {
        ReelsAlbumsSort.DateDesc -> filtered.sortedByDescending { it.addedAt }
        ReelsAlbumsSort.DateAsc -> filtered.sortedBy { it.addedAt }
        ReelsAlbumsSort.Name -> filtered.sortedWith(
            compareBy<ReelsAlbum> { it.name.lowercase() }.thenByDescending { it.addedAt },
        )
        ReelsAlbumsSort.Source -> filtered.sortedWith(
            compareBy<ReelsAlbum> { sourceNames[it.sourceId].orEmpty().lowercase() }
                .thenBy { it.sourceId }
                .thenByDescending { it.addedAt },
        )
    }
}
