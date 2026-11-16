package eu.kanade.tachiyomi.ui.home

import android.content.Context
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryCandidatePolicy
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder
import eu.kanade.tachiyomi.data.discovery.dedupeCrossRow
import eu.kanade.tachiyomi.data.discovery.expandGenreSet
import eu.kanade.tachiyomi.data.discovery.isBlacklisted
import eu.kanade.tachiyomi.data.discovery.runCatchingUnlessCancelled
import eu.kanade.tachiyomi.ui.discovery.manualRefreshFlow
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.discovery.model.DiscoveryBatchReceipt
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPublicationSnapshot
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTitleMemory
import tachiyomi.domain.discovery.model.identityKeys
import tachiyomi.domain.discovery.model.legacyDiscoveryKey
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Единый контроллер Home-discovery (PR 3):
 * Инкапсулирует наблюдение за пулом, расчет тизера, ротацию, fast-replace карточек
 * и сериализует операции через Mutex для предотвращения гонок.
 */
internal class HomeDiscoveryController(
    val mediaType: DiscoveryMediaType,
    private val repository: DiscoveryRepository,
    private val preferences: DiscoveryPreferences,
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStateUpdate: ((HomeHubUiState) -> HomeHubUiState) -> Unit,
    private val currentState: () -> HomeHubUiState,
    private val onEmitEffect: (HomeHubEffect) -> Unit,
    sourcePolicyProvider: DiscoverySourcePolicyProvider? = null,
) {
    private val sourcePolicies by lazy { sourcePolicyProvider ?: Injekt.get<DiscoverySourcePolicyProvider>() }
    private val mutex = Mutex()
    private var cachedDiscoveryPool: List<DiscoverySuggestion> = emptyList()
    private var cachedPolicy: DiscoveryCandidatePolicy = DiscoveryCandidatePolicy()
    private var hasObservedPolicy: Boolean = false
    private var lastObservedInventoryComplete: Boolean? = null
    private var discoveryOffset: Int = 0
    private var lastReentryTime: Long = 0L
    private var lastRefreshClickTime: Long = 0L
    private var dismissedSessionTitles = mutableSetOf<String>()

    private fun publishState(updater: (HomeHubUiState) -> HomeHubUiState) {
        onStateUpdate { current ->
            val updated = updater(current)
            val policy = sourcePolicies.current(mediaType)
            updated.copy(
                discovery = updated.discovery.filter { item ->
                    item.mediaType != mediaType || policy.allows(item.toDiscoverySuggestion())
                },
                discoveryPool = updated.discoveryPool.filter { item ->
                    item.mediaType != mediaType || policy.allows(item.toDiscoverySuggestion())
                },
            )
        }
    }

    private fun projectPool(
        items: List<DiscoverySuggestion>,
        policy: DiscoveryCandidatePolicy,
    ): List<DiscoverySuggestion> {
        val sources = sourcePolicies.current(mediaType)
        val eligible = items.filter { sources.allows(it) && it.cleanTitle !in dismissedSessionTitles }
        val filtered = eligible.groupBy { it.rowType }.flatMap { (_, row) ->
            policy.filterRowCandidates(row, { it.genres }, scoreSelector = { it.score }).map { item ->
                if (policy.hasPriorityBoost(item)) {
                    item.copy(
                        score = maxOf(
                            item.score * 1.5,
                            item.score + 10.0,
                        ),
                    )
                } else {
                    item
                }
            }
        }
        val deduped = dedupeCrossRow(filtered)
        return if (policy.priorityGenres.isNotEmpty()) {
            val (boosted, normal) = deduped.partition { policy.hasPriorityBoost(it) }
            boosted + normal
        } else {
            deduped
        }
    }

    fun startObserving() {
        scope.launchIO {
            var wasRefreshing = false
            val refreshFlow = runCatching {
                manualRefreshFlow(
                    context.workManager,
                    mediaType,
                )
            }.getOrNull() ?: return@launchIO

            refreshFlow.collectLatest { refreshing ->
                publishState { it.copy(isDiscoveryRefreshing = refreshing) }
                if (wasRefreshing && !refreshing) {
                    mutex.withLock {
                        val failedCsv = preferences.lastFailedRows(mediaType).get()
                        val lastStatus = preferences.lastGenerationStatus(mediaType).get()
                        val isFailed =
                            (lastStatus == "FAILED") || (failedCsv.isNotBlank() && cachedDiscoveryPool.isEmpty())
                        publishState { it.copy(isDiscoveryFailed = isFailed) }
                        updateDiscoveryTeaserLocked(
                            advanceOffset = false,
                            forceUpdate = true,
                            notifyIfLimited = true,
                        )
                    }
                }
                wasRefreshing = refreshing
            }
        }

        scope.launchIO {
            combine(
                repository.subscribe(mediaType),
                preferences.discoveryEnabled().changes(),
                preferences.teaserCount().changes(),
                combine(
                    repository.subscribeHidden(mediaType),
                    repository.subscribeConsumed(mediaType),
                    repository.observeTitleMemory(mediaType),
                ) { hidden, consumed, memory -> Triple(hidden, consumed, memory) },
                combine(
                    repository.subscribeBlacklist(mediaType),
                    preferences.observeGenreFilterSpec(mediaType),
                    sourcePolicies.observe(mediaType),
                    combine(
                        preferences.lastFailedRows(mediaType).changes(),
                        preferences.lastGenerationStatus(mediaType).changes(),
                        preferences.lastGenerationAt(mediaType).changes(),
                        preferences.lastSourceFailureCount(mediaType).changes(),
                    ) { failedCsv, lastStatus, lastGenAt, sourceFailureCount ->
                        DiscoveryStatusSnapshot(failedCsv, lastStatus, lastGenAt, sourceFailureCount)
                    },
                ) { blacklist, filterSpec, sourcePolicy, status ->
                    DiscoveryCandidatePolicy(
                        spec = filterSpec,
                        blacklistedTags = blacklist,
                    ).let { policy -> Triple(policy, status, sourcePolicy) }
                },
            ) { items, enabled, count, exclusions, (policy, status, sourcePolicy) ->
                val (hidden, consumed, memory) = exclusions
                val visible = filterVisibleByExclusions(
                    items = items.filter(sourcePolicy::allows),
                    mediaType = mediaType,
                    hidden = hidden,
                    consumed = consumed,
                    memory = memory,
                )
                DiscoveryObservationState(
                    visible = visible,
                    enabled = enabled,
                    count = count,
                    policy = policy,
                    failedCsv = status.failedCsv,
                    lastStatus = status.lastStatus,
                    lastGenAt = status.lastGenAt,
                    sourceFailureCount = status.sourceFailureCount,
                    inventoryComplete = sourcePolicy.inventoryComplete,
                )
            }.collectLatest { obs ->
                mutex.withLock {
                    val previousPriorityGenres = cachedPolicy.priorityGenres
                    val hadObservedPolicy = hasObservedPolicy
                    val inventoryJustCompleted =
                        lastObservedInventoryComplete == false && obs.inventoryComplete
                    lastObservedInventoryComplete = obs.inventoryComplete
                    cachedPolicy = obs.policy
                    hasObservedPolicy = true
                    if (!obs.enabled) {
                        cachedDiscoveryPool = emptyList()
                        publishState {
                            it.copy(
                                discovery = emptyList(),
                                discoveryEnabled = false,
                                isDiscoveryLoading = false,
                                discoveryPool = emptyList(),
                                isDiscoveryFailed = false,
                                discoveryFailedSourceCount = 0,
                            )
                        }
                        return@collectLatest
                    }
                    cachedDiscoveryPool = projectPool(obs.visible, obs.policy)
                    val shownAtByTitle = eu.kanade.tachiyomi.data.discovery.runCatchingUnlessCancelled {
                        repository.getRecentExclusionsWithTimestamp(mediaType)
                    }.getOrDefault(emptyMap())

                    val updatedPool = cachedDiscoveryPool.map { item ->
                        item.toHomeHubDiscoveryItem(
                            shownAt = shownAtByTitle[item.cleanTitle],
                            hasPriorityBoost = obs.policy.hasPriorityBoost(item),
                        )
                    }
                    val lastUpdated = obs.visible.maxOfOrNull { it.createdAt }
                        ?: eu.kanade.tachiyomi.data.discovery.runCatchingUnlessCancelled {
                            repository.lastUpdatedAt(mediaType)
                        }.getOrNull()
                        ?: obs.lastGenAt.takeIf { it > 0L }
                    val isFailed =
                        (obs.lastStatus == "FAILED") || (obs.failedCsv.isNotBlank() && cachedDiscoveryPool.isEmpty())
                    val waitingInventory = !obs.inventoryComplete && cachedDiscoveryPool.isEmpty()
                    publishState {
                        it.copy(
                            discoveryPool = updatedPool,
                            discoveryEnabled = true,
                            isDiscoveryLoading = waitingInventory,
                            discoveryLastUpdated = lastUpdated,
                            isDiscoveryFailed = isFailed,
                            discoveryFailedSourceCount = obs.sourceFailureCount,
                        )
                    }

                    val isFirstTeaserLoad = currentState().discovery.isEmpty()
                    val priorityChanged = hadObservedPolicy && obs.policy.priorityGenres != previousPriorityGenres
                    val forceRebalance = isFirstTeaserLoad || priorityChanged || inventoryJustCompleted
                    updateDiscoveryTeaserLocked(
                        advanceOffset = false,
                        forceUpdate = forceRebalance,
                        teaserCount = obs.count,
                        avoidCurrentTitles = !(priorityChanged || inventoryJustCompleted),
                    )
                }
            }
        }
    }

    private suspend fun updateDiscoveryTeaserLocked(
        advanceOffset: Boolean = false,
        forceUpdate: Boolean = false,
        notifyIfLimited: Boolean = false,
        teaserCount: Int? = null,
        avoidCurrentTitles: Boolean = true,
    ) {
        if (!preferences.discoveryEnabled().get()) {
            publishState { it.copy(discovery = emptyList(), discoveryEnabled = false, discoveryPool = emptyList()) }
            return
        }
        val count = teaserCount ?: preferences.teaserCount().get().coerceIn(3, 20)
        val pool = cachedDiscoveryPool
        if (pool.isEmpty()) {
            publishState { it.copy(discovery = emptyList(), discoveryEnabled = true, discoveryPool = emptyList()) }
            return
        }

        val state = currentState()
        val activePolicy = cachedPolicy
        val priorityCheck: (DiscoverySuggestion) -> Boolean = { activePolicy.hasPriorityBoost(it) }
        if (!forceUpdate && !advanceOffset && state.discovery.isNotEmpty()) {
            val validCleanTitles = pool.mapTo(HashSet()) { it.cleanTitle }
            val shownMap = runCatchingUnlessCancelled {
                repository.getRecentExclusionsWithTimestamp(mediaType)
            }.getOrDefault(emptyMap())

            val patched = patchTeaserItems(
                currentItems = state.discovery,
                pool = pool,
                validCleanTitles = validCleanTitles,
                targetCount = count,
                shownTitles = shownMap.keys,
                shownCutoffMap = shownMap,
                dismissedTitles = dismissedSessionTitles,
                hasPriorityBoost = priorityCheck,
            )

            val updatedPool = pool.map { p ->
                p.toHomeHubDiscoveryItem(
                    shownAt = shownMap[p.cleanTitle],
                    hasPriorityBoost = priorityCheck(p),
                )
            }

            if (patched != state.discovery || updatedPool != state.discoveryPool) {
                publishState {
                    it.copy(
                        discovery = patched,
                        discoveryEnabled = true,
                        discoveryPool = updatedPool,
                    )
                }
            }
            return
        }

        if (advanceOffset) {
            val step = if (pool.size > count) count else 1
            discoveryOffset = (discoveryOffset + step) % pool.size
        }

        val shownMap = runCatchingUnlessCancelled {
            repository.getRecentExclusionsWithTimestamp(mediaType)
        }.getOrDefault(emptyMap())
        val shownTitles = shownMap.keys
        val currentTitles = if (avoidCurrentTitles) {
            state.discovery.mapTo(HashSet()) { it.cleanTitle }
        } else {
            emptySet()
        }

        val teaser = selectFreshTeaserItems(
            pool = pool.filterNot { it.cleanTitle in dismissedSessionTitles },
            shownTitles = shownTitles,
            count = count,
            offset = discoveryOffset,
            shownCutoffMap = shownMap,
            currentTitles = currentTitles,
            hasPriorityBoost = priorityCheck,
        )

        if (notifyIfLimited && currentTitles.isNotEmpty()) {
            val distinctPoolSize = pool.distinctBy { it.cleanTitle }.size
            if (shouldNotifyDiscoveryPoolLimited(teaser, currentTitles, distinctPoolSize, count)) {
                onEmitEffect(HomeHubEffect.DiscoveryPoolLimited)
            }
        }

        val updatedPool = pool.map { p ->
            p.toHomeHubDiscoveryItem(
                shownAt = shownMap[p.cleanTitle],
                hasPriorityBoost = priorityCheck(p),
            )
        }
        publishState {
            it.copy(
                discovery = teaser,
                discoveryEnabled = true,
                discoveryPool = updatedPool,
            )
        }
    }

    fun refreshTeaser(
        advanceOffset: Boolean = false,
        forceUpdate: Boolean = true,
        notifyIfLimited: Boolean = false,
    ) {
        scope.launchIO {
            mutex.withLock {
                updateDiscoveryTeaserLocked(
                    advanceOffset = advanceOffset,
                    forceUpdate = forceUpdate,
                    notifyIfLimited = notifyIfLimited,
                )
            }
        }
    }

    fun onScreenReentry() {
        val now = System.currentTimeMillis()
        if (!shouldAdvanceDiscoveryTeaserOnReentry(now, lastReentryTime)) return
        lastReentryTime = now
        scope.launchIO {
            mutex.withLock {
                updateDiscoveryTeaserLocked(advanceOffset = false)
            }
        }
    }

    fun rotateOrRefreshDiscovery() {
        if (!preferences.discoveryEnabled().get()) return
        val now = System.currentTimeMillis()
        if (now - lastRefreshClickTime < 500L) return
        lastRefreshClickTime = now

        if (currentState().isDiscoveryRefreshing) return

        scope.launchIO {
            mutex.withLock {
                val snapshot = runCatchingUnlessCancelled { repository.publication(mediaType) }.getOrNull()
                val rankedKeys = cachedDiscoveryPool
                    .flatMap { suggestion -> suggestion.identityKeys().toList() }
                val receipt = if (snapshot == null) {
                    null
                } else {
                    repository.nextHomeBatch(mediaType, snapshot.generation, rankedKeys)
                }
                when (receipt) {
                    is DiscoveryBatchReceipt.Available -> {
                        applyHomeBatch(receipt.snapshot)
                    }

                    is DiscoveryBatchReceipt.Superseded -> {
                        updateDiscoveryTeaserLocked(advanceOffset = false, forceUpdate = true, notifyIfLimited = true)
                    }

                    is DiscoveryBatchReceipt.NeedsSourceRefresh, null -> {
                        publishState { it.copy(isDiscoveryRefreshing = true) }
                        DiscoveryUpdateJob.refreshNow(context, mediaType)
                    }
                }
            }
        }
    }

    private suspend fun applyHomeBatch(snapshot: DiscoveryPublicationSnapshot) {
        val byKey = HashMap<String, DiscoverySuggestion>()
        snapshot.eligibleSuggestions.forEach { suggestion ->
            suggestion.identityKeys().forEach { key -> byKey.putIfAbsent(key, suggestion) }
        }
        val sourcePolicy = sourcePolicies.current(mediaType)
        val selected = snapshot.homeBatchKeys
            .mapNotNull { byKey[it] }
            .distinctBy { it.cleanTitle }
            .filter {
                cachedPolicy.isAllowed(it) &&
                    sourcePolicy.allows(it) &&
                    it.cleanTitle !in dismissedSessionTitles
            }
        if (selected.isEmpty()) {
            updateDiscoveryTeaserLocked(advanceOffset = false, forceUpdate = true, notifyIfLimited = true)
            return
        }
        val selectedKeys = selected.mapTo(HashSet()) { it.cleanTitle }
        val combined = selected + cachedDiscoveryPool.filterNot { it.cleanTitle in selectedKeys }
        cachedDiscoveryPool = if (cachedPolicy.priorityGenres.isNotEmpty()) {
            val (boosted, normal) = combined.partition { cachedPolicy.hasPriorityBoost(it) }
            boosted + normal
        } else {
            combined
        }
        discoveryOffset = 0
        updateDiscoveryTeaserLocked(advanceOffset = false, forceUpdate = true, notifyIfLimited = true)
    }

    fun likeDiscoveryItem(item: HomeHubDiscoveryItem) {
        if (item.cleanTitle.isBlank()) return
        scope.launchIO {
            val suggestion = item.toDiscoverySuggestion()
            // Taste feedback only: the card stays in place, no dismissal, no retirement.
            TasteSignalRecorder.record(
                repository,
                suggestion,
                DiscoverySignalType.LIKE,
            )
        }
    }

    fun restoreHiddenTeaserItem(item: HomeHubDiscoveryItem) {
        if (item.cleanTitle.isBlank() || item.mediaType != mediaType) return
        scope.launchIO {
            repository.undoHide(item.mediaType, item.cleanTitle)
            mutex.withLock {
                dismissedSessionTitles -= item.cleanTitle
                val rawPool = repository.subscribe(mediaType).first()
                val consumed = repository.getSignals(mediaType).filter {
                    it.signalType.excludesFromFeed
                }.mapTo(HashSet()) {
                    it.cleanTitle
                }
                val hidden = repository.getHiddenTitles(mediaType)
                val eligibility = eu.kanade.tachiyomi.data.discovery.runCatchingUnlessCancelled {
                    repository.eligibility(mediaType)
                }.getOrNull()
                val blacklist = repository.getBlacklistedTags(mediaType)
                val shownMap = repository.getRecentExclusionsWithTimestamp(mediaType)
                val policy = DiscoveryCandidatePolicy(
                    spec = preferences.genreFilterSpec(mediaType),
                    blacklistedTags = blacklist,
                )
                if (!preferences.discoveryEnabled().get()) return@withLock
                val excludedIdentities = eligibility?.excludedIdentityKeys.orEmpty()
                val excludedLookups = eligibility?.excludedLookupKeys.orEmpty()
                val eligible = rawPool.filterNot { candidate ->
                    candidate.cleanTitle in consumed || candidate.cleanTitle in hidden ||
                        candidate.cleanTitle in excludedIdentities ||
                        legacyDiscoveryKey(mediaType, candidate.cleanTitle) in excludedLookups ||
                        candidate.identityKeys().any { it in excludedIdentities }
                }
                val projected = projectPool(eligible, policy)
                val suggestion = projected.firstOrNull { it.cleanTitle == item.cleanTitle } ?: return@withLock
                val freshItem = suggestion.toHomeHubDiscoveryItem(
                    shownAt = shownMap[item.cleanTitle],
                    hasPriorityBoost = policy.hasPriorityBoost(suggestion),
                )
                cachedPolicy = policy
                cachedDiscoveryPool = projected
                val allowedTitles = projected.mapTo(HashSet()) { it.cleanTitle }
                publishState { current ->
                    current.copy(
                        discovery = restoreTeaserItem(
                            current = current.discovery.filter { it.cleanTitle in allowedTitles },
                            restored = freshItem,
                            targetCount = current.discovery.size,
                        ),
                        discoveryPool = projected.map { candidate ->
                            candidate.toHomeHubDiscoveryItem(
                                shownAt = shownMap[candidate.cleanTitle],
                                hasPriorityBoost = policy.hasPriorityBoost(candidate),
                            )
                        },
                        discoveryEnabled = true,
                    )
                }
            }
        }
    }

    fun onItemDismissedFromPool(cleanTitle: String) {
        if (cleanTitle.isBlank()) return
        scope.launchIO {
            mutex.withLock {
                dismissedSessionTitles += cleanTitle
                dismissedSessionTitles = pruneDismissedSessionTitles(dismissedSessionTitles).toMutableSet()
                cachedDiscoveryPool = cachedDiscoveryPool.filterNot { it.cleanTitle == cleanTitle }
            }
        }
    }

    fun recordExposure(item: HomeHubDiscoveryItem) {
        if (item.cleanTitle.isBlank()) return
        scope.launchIO {
            runCatching {
                repository.recordExposure(item.mediaType, listOf(item.cleanTitle))
            }
        }
    }

    fun markDiscoveryItemShown(item: HomeHubDiscoveryItem) {
        recordExposure(item)
    }
}

private data class DiscoveryStatusSnapshot(
    val failedCsv: String,
    val lastStatus: String,
    val lastGenAt: Long,
    val sourceFailureCount: Int,
)

private data class DiscoveryObservationState(
    val visible: List<DiscoverySuggestion>,
    val enabled: Boolean,
    val count: Int,
    val policy: DiscoveryCandidatePolicy,
    val failedCsv: String,
    val lastStatus: String,
    val lastGenAt: Long,
    val sourceFailureCount: Int,
    val inventoryComplete: Boolean = true,
)

private fun filterVisibleByExclusions(
    items: List<DiscoverySuggestion>,
    mediaType: DiscoveryMediaType,
    hidden: Set<String>,
    consumed: Set<String>,
    memory: List<DiscoveryTitleMemory>,
    now: Long = System.currentTimeMillis(),
): List<DiscoverySuggestion> {
    if (items.isEmpty()) return emptyList()
    val excludedIdentityKeys = HashSet<String>(hidden.size + consumed.size + memory.size * 2)
    val excludedLookupKeys = HashSet<String>(hidden.size + consumed.size + memory.size)
    hidden.forEach { title ->
        excludedIdentityKeys += title
        excludedLookupKeys += legacyDiscoveryKey(mediaType, title)
    }
    consumed.forEach { title ->
        excludedIdentityKeys += title
        excludedLookupKeys += legacyDiscoveryKey(mediaType, title)
    }
    memory.forEach { record ->
        val excluded = record.opened.active ||
            record.started.active ||
            record.consumed.active ||
            record.blockedUntil > now
        if (excluded) {
            excludedIdentityKeys += record.identity.identityKey
            excludedIdentityKeys += record.identity.trustedAliases
            excludedLookupKeys += legacyDiscoveryKey(record.identity.mediaType, record.identity.primaryCleanTitle)
        }
    }
    if (excludedIdentityKeys.isEmpty() && excludedLookupKeys.isEmpty()) return items
    return items.filterNot { item ->
        item.cleanTitle in excludedIdentityKeys ||
            legacyDiscoveryKey(mediaType, item.cleanTitle) in excludedLookupKeys ||
            item.identityKeys().any { it in excludedIdentityKeys }
    }
}
