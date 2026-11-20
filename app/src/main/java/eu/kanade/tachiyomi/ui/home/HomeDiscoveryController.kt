package eu.kanade.tachiyomi.ui.home

import android.content.Context
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.DISCOVERY_POOL_TARGET
import eu.kanade.tachiyomi.data.discovery.DiscoveryCandidatePolicy
import eu.kanade.tachiyomi.data.discovery.DiscoveryCoverRecovery
import eu.kanade.tachiyomi.data.discovery.DiscoverySourcePolicyResolver
import eu.kanade.tachiyomi.data.discovery.DiscoveryTrendingSource
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder
import eu.kanade.tachiyomi.data.discovery.dedupeCrossRow
import eu.kanade.tachiyomi.data.discovery.expandGenreSet
import eu.kanade.tachiyomi.data.discovery.isBlacklisted
import eu.kanade.tachiyomi.data.discovery.recoverMissingCovers
import eu.kanade.tachiyomi.data.discovery.runCatchingUnlessCancelled
import eu.kanade.tachiyomi.ui.discovery.activeDiscoveryRefreshFlow
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.discovery.model.DiscoveryBatchReceipt
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPublicationSnapshot
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTitleMemory
import tachiyomi.domain.discovery.model.identityKeys
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

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
    private val trendingSource: DiscoveryTrendingSource? = null,
    private val onTeaserPersisted: (List<HomeHubDiscoveryItem>) -> Unit = {},
) {
    private val sourcePolicies by lazy { sourcePolicyProvider ?: Injekt.get<DiscoverySourcePolicyProvider>() }
    private val mutex = Mutex()
    private var cachedDiscoveryPool: List<DiscoverySuggestion> = emptyList()
    private var cachedPolicy: DiscoveryCandidatePolicy = DiscoveryCandidatePolicy()
    private var hasObservedPolicy: Boolean = false
    private var lastObservedInventoryComplete: Boolean? = null
    private var lastObservedUsedProvisionalColdStart: Boolean = false

    @Volatile
    private var allowProvisionalColdStartNative: Boolean = false
    private var discoveryOffset: Int = 0
    private var lastReentryTime: Long = 0L
    private var lastRefreshClickTime: Long = 0L
    private var dismissedSessionTitles = mutableSetOf<String>()
    private val optimisticDismissedTitles: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var lastReconciledBatchSignature: Triple<Long, Long, List<String>>? = null
    private var coverRecoveryJob: Job? = null
    private var lastCoverRecoveryTargets: List<String> = emptyList()

    private fun HomeHubDiscoveryItem.withRecoveredCover(): HomeHubDiscoveryItem {
        if (!coverUrl.isNullOrBlank()) return this
        val recovered = DiscoveryCoverRecovery.get(mediaType, cleanTitle) ?: return this
        return copy(coverUrl = recovered)
    }

    private fun scheduleCoverRecovery(items: List<HomeHubDiscoveryItem>) {
        val source = trendingSource ?: return
        val targets = items.filter { it.coverUrl.isNullOrBlank() }
        if (targets.isEmpty()) return
        val targetKeys = targets.map { it.cleanTitle }
        if (coverRecoveryJob?.isActive == true && lastCoverRecoveryTargets == targetKeys) return
        lastCoverRecoveryTargets = targetKeys
        coverRecoveryJob?.cancel()
        coverRecoveryJob = scope.launchIO {
            delay(500L)
            recoverMissingCovers(
                items = targets.map { it.toDiscoverySuggestion() },
                mediaType = mediaType,
                trendingSource = source,
                onRecovered = {
                    publishState { current ->
                        current.copy(
                            discovery = current.discovery.map { it.withRecoveredCover() },
                            discoveryPool = current.discoveryPool.map { it.withRecoveredCover() },
                        )
                    }
                    val updatedTeaser = currentState().discovery
                    if (updatedTeaser.isNotEmpty()) {
                        onTeaserPersisted(updatedTeaser)
                    }
                },
            )
        }
    }

    private fun publishState(updater: (HomeHubUiState) -> HomeHubUiState) {
        onStateUpdate { current ->
            val updated = updater(current)
            val policy = sourcePolicies.current(mediaType)
            val preserveFastCacheBeforeObserve = !policy.inventoryComplete &&
                !hasObservedPolicy &&
                current.discovery.isNotEmpty()
            val allowProvisionalNative = allowProvisionalColdStartNative || preserveFastCacheBeforeObserve
            updated.copy(
                discovery = updated.discovery.mapNotNull { item ->
                    if (item.cleanTitle in optimisticDismissedTitles) return@mapNotNull null
                    if (item.mediaType != mediaType ||
                        allowsColdStartDisplay(item.toDiscoverySuggestion(), policy, allowProvisionalNative)
                    ) {
                        item.withRecoveredCover()
                    } else {
                        null
                    }
                },
                discoveryPool = updated.discoveryPool.mapNotNull { item ->
                    if (item.cleanTitle in optimisticDismissedTitles) return@mapNotNull null
                    if (item.mediaType != mediaType ||
                        allowsColdStartDisplay(item.toDiscoverySuggestion(), policy, allowProvisionalNative)
                    ) {
                        item.withRecoveredCover()
                    } else {
                        null
                    }
                },
            )
        }
    }

    private fun projectPool(
        items: List<DiscoverySuggestion>,
        policy: DiscoveryCandidatePolicy,
        sources: DiscoverySourcePolicy = sourcePolicies.current(mediaType),
        allowProvisionalNative: Boolean = allowProvisionalColdStartNative,
    ): List<DiscoverySuggestion> {
        val eligible = items.filter {
            allowsColdStartDisplay(it, sources, allowProvisionalNative) &&
                it.cleanTitle !in dismissedSessionTitles &&
                it.cleanTitle !in optimisticDismissedTitles
        }
        val filtered = eligible.groupBy { it.rowType }.flatMap { (_, row) ->
            policy.filterRowCandidates(
                row,
                { it.genres },
                scoreSelector = { it.score },
                fillTarget = if (!policy.isStrictRequired) DISCOVERY_POOL_TARGET else null,
            ).map { item ->
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
                activeDiscoveryRefreshFlow(
                    context.workManager,
                    mediaType,
                )
            }.getOrNull() ?: return@launchIO

            refreshFlow.collectLatest { refreshing ->
                publishState { it.copy(isDiscoveryRefreshing = refreshing) }
                if (wasRefreshing && !refreshing) {
                    mutex.withLock {
                        // Guard against WorkManager marking SUCCEEDED slightly before
                        // observePublication() re-emits the freshly committed SQLite pool.
                        val livePolicy = sourcePolicies.current(mediaType)
                        if (cachedDiscoveryPool.isEmpty() && livePolicy.inventoryComplete) {
                            val freshSnapshot = runCatchingUnlessCancelled {
                                repository.publication(mediaType)
                            }.getOrNull()
                            if (freshSnapshot != null) {
                                val recovered = projectPool(
                                    freshSnapshot.eligibleSuggestions.filter(livePolicy::allows),
                                    cachedPolicy,
                                )
                                if (recovered.isNotEmpty()) {
                                    cachedDiscoveryPool = recovered
                                }
                            }
                        }
                        val failedCsv = preferences.lastFailedRows(mediaType).get()
                        val lastStatus = preferences.lastGenerationStatus(mediaType).get()
                        val failedSources = preferences.lastSourceFailureCount(mediaType).get()
                        val inventoryFailure = (sourcePolicies as? DiscoverySourcePolicyResolver)
                            ?.lastInventoryFailure(mediaType)
                        val inventoryComplete = livePolicy.inventoryComplete
                        val isFailed = cachedDiscoveryPool.isEmpty() && (
                            (!inventoryComplete && inventoryFailure != null) ||
                                (
                                    inventoryComplete && (
                                        (lastStatus == "FAILED") ||
                                            failedCsv.isNotBlank() ||
                                            failedSources > 0
                                        )
                                    )
                            )
                        publishState {
                            it.copy(
                                isDiscoveryFailed = isFailed,
                                isDiscoveryLoading = cachedDiscoveryPool.isEmpty() &&
                                    !inventoryComplete &&
                                    inventoryFailure == null,
                            )
                        }
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
            val inventoryFailureFlow = (sourcePolicies as? DiscoverySourcePolicyResolver)
                ?.observeInventoryFailure(mediaType)
                ?: flowOf(null)
            combine(
                repository.observePublication(mediaType),
                preferences.discoveryEnabled().changes(),
                preferences.teaserCount().changes(),
                combine(
                    repository.subscribeBlacklist(mediaType),
                    preferences.observeGenreFilterSpec(mediaType),
                    sourcePolicies.observe(mediaType),
                    combine(
                        preferences.lastFailedRows(mediaType).changes(),
                        preferences.lastGenerationStatus(mediaType).changes(),
                        preferences.lastGenerationAt(mediaType).changes(),
                        preferences.lastSourceFailureCount(mediaType).changes(),
                        inventoryFailureFlow,
                    ) { failedCsv, lastStatus, lastGenAt, sourceFailureCount, inventoryFailure ->
                        DiscoveryStatusSnapshot(
                            failedCsv = failedCsv,
                            lastStatus = lastStatus,
                            lastGenAt = lastGenAt,
                            sourceFailureCount = sourceFailureCount,
                            inventoryFailure = inventoryFailure,
                        )
                    },
                ) { blacklist, filterSpec, sourcePolicy, status ->
                    DiscoveryCandidatePolicy(
                        spec = filterSpec,
                        blacklistedTags = blacklist,
                    ).let { policy -> Triple(policy, status, sourcePolicy) }
                },
            ) { snapshot, enabled, count, (policy, status, sourcePolicy) ->
                val allowProvisionalNative = !sourcePolicy.inventoryComplete && snapshot.generation > 0L
                val visible = selectColdStartVisibleCandidates(snapshot, sourcePolicy)
                val snapshotPendingSync = snapshot.sourcePolicyRevision.isNotEmpty() &&
                    snapshot.sourcePolicyRevision != sourcePolicy.revision
                DiscoveryObservationState(
                    snapshot = snapshot,
                    visible = visible,
                    enabled = enabled,
                    count = count,
                    policy = policy,
                    sourcePolicy = sourcePolicy,
                    failedCsv = status.failedCsv,
                    lastStatus = status.lastStatus,
                    lastGenAt = status.lastGenAt,
                    sourceFailureCount = status.sourceFailureCount,
                    inventoryComplete = sourcePolicy.inventoryComplete,
                    inventoryFailure = status.inventoryFailure,
                    snapshotPendingSync = snapshotPendingSync,
                    usedProvisionalColdStart = allowProvisionalNative && visible.any { it.sourceId != null },
                )
            }.collectLatest { obs ->
                mutex.withLock {
                    val previousPriorityGenres = cachedPolicy.priorityGenres
                    val hadObservedPolicy = hasObservedPolicy
                    val wasProvisionalColdStart = lastObservedUsedProvisionalColdStart
                    val inventoryJustCompleted =
                        lastObservedInventoryComplete == false && obs.inventoryComplete
                    lastObservedInventoryComplete = obs.inventoryComplete
                    lastObservedUsedProvisionalColdStart = obs.usedProvisionalColdStart
                    allowProvisionalColdStartNative = obs.usedProvisionalColdStart
                    cachedPolicy = obs.policy
                    hasObservedPolicy = true
                    if (!obs.enabled) {
                        coverRecoveryJob?.cancel()
                        cachedDiscoveryPool = emptyList()
                        lastReconciledBatchSignature = null
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
                        onTeaserPersisted(emptyList())
                        return@collectLatest
                    }
                    val projected = projectPool(
                        items = obs.visible,
                        policy = obs.policy,
                        sources = obs.sourcePolicy,
                        allowProvisionalNative = obs.usedProvisionalColdStart,
                    )
                    val snapshot = obs.snapshot
                    val batchKeys = snapshot.homeBatchKeys
                    if (snapshot.generation > 0L &&
                        snapshot.traversal == tachiyomi.domain.discovery.model.DiscoveryTraversalState.VALID &&
                        batchKeys.isNotEmpty()
                    ) {
                        val byKey = HashMap<String, DiscoverySuggestion>()
                        projected.forEach { item ->
                            item.identityKeys().forEach { k -> byKey.putIfAbsent(k, item) }
                        }
                        val batchKeySet = batchKeys.toHashSet()
                        val survivingBatchCount = batchKeys.count { it in byKey }
                        val hasReplacementCandidate = projected.any { item ->
                            item.identityKeys().none { it in batchKeySet }
                        }
                        val batchSignature = Triple(snapshot.generation, snapshot.homeBatchVersion, batchKeys)
                        if (survivingBatchCount < batchKeys.size &&
                            hasReplacementCandidate &&
                            lastReconciledBatchSignature != batchSignature
                        ) {
                            lastReconciledBatchSignature = batchSignature
                            val rankedKeys = projected.flatMap { it.identityKeys().toList() }
                            runCatchingUnlessCancelled {
                                repository.reconcileHomeBatch(mediaType, snapshot.generation, rankedKeys)
                            }
                        }
                    }
                    cachedDiscoveryPool = if (batchKeys.isNotEmpty()) {
                        val batchOrder = HashMap<String, Int>()
                        batchKeys.forEachIndexed { idx, key -> batchOrder.putIfAbsent(key, idx) }
                        val (inBatch, rest) = projected.partition { item ->
                            item.identityKeys().any { it in batchOrder }
                        }
                        val orderedBatch = inBatch.sortedBy { item ->
                            item.identityKeys().mapNotNull { batchOrder[it] }.minOrNull() ?: Int.MAX_VALUE
                        }
                        orderedBatch + rest
                    } else {
                        projected
                    }
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
                        ?: snapshot.committedAt.takeIf { it > 0L }
                        ?: eu.kanade.tachiyomi.data.discovery.runCatchingUnlessCancelled {
                            repository.lastUpdatedAt(mediaType)
                        }.getOrNull()
                        ?: obs.lastGenAt.takeIf { it > 0L }
                    val waitingInventory = cachedDiscoveryPool.isEmpty() && (
                        (!obs.inventoryComplete && obs.inventoryFailure == null) ||
                            obs.snapshotPendingSync
                        )
                    val preserveCachedTeaser = waitingInventory && currentState().discovery.any { item ->
                        item.cleanTitle !in dismissedSessionTitles &&
                            item.cleanTitle !in optimisticDismissedTitles &&
                            obs.policy.isAllowed(item.toDiscoverySuggestion())
                    }
                    if (preserveCachedTeaser) {
                        allowProvisionalColdStartNative = true
                    }
                    val isFailed = !waitingInventory && cachedDiscoveryPool.isEmpty() && (
                        (!obs.inventoryComplete && obs.inventoryFailure != null) ||
                            (obs.lastStatus == "FAILED") ||
                            obs.failedCsv.isNotBlank() ||
                            obs.sourceFailureCount > 0
                        )
                    publishState {
                        it.copy(
                            discoveryPool = if (preserveCachedTeaser && updatedPool.isEmpty()) {
                                it.discoveryPool
                            } else {
                                updatedPool
                            },
                            discoveryEnabled = true,
                            isDiscoveryLoading = waitingInventory && !preserveCachedTeaser,
                            discoveryLastUpdated = lastUpdated,
                            isDiscoveryFailed = isFailed,
                            discoveryFailedSourceCount = obs.sourceFailureCount,
                        )
                    }

                    val isFirstTeaserLoad = currentState().discovery.isEmpty()
                    val priorityChanged = hadObservedPolicy && obs.policy.priorityGenres != previousPriorityGenres
                    val needsInventoryRebalance = inventoryJustCompleted && !wasProvisionalColdStart
                    val forceRebalance = isFirstTeaserLoad || priorityChanged || needsInventoryRebalance
                    updateDiscoveryTeaserLocked(
                        advanceOffset = false,
                        forceUpdate = forceRebalance,
                        teaserCount = obs.count,
                        avoidCurrentTitles = !(priorityChanged || needsInventoryRebalance),
                        preserveCachedEmpty = waitingInventory,
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
        preserveCachedEmpty: Boolean = false,
    ) {
        if (!preferences.discoveryEnabled().get()) {
            publishState { it.copy(discovery = emptyList(), discoveryEnabled = false, discoveryPool = emptyList()) }
            onTeaserPersisted(emptyList())
            return
        }
        val count = teaserCount ?: preferences.teaserCount().get().coerceIn(3, 20)
        val pool = cachedDiscoveryPool
        if (pool.isEmpty()) {
            if (preserveCachedEmpty && currentState().discovery.isNotEmpty()) {
                val surviving = currentState().discovery.filter { item ->
                    item.cleanTitle !in dismissedSessionTitles &&
                        item.cleanTitle !in optimisticDismissedTitles &&
                        cachedPolicy.isAllowed(item.toDiscoverySuggestion())
                }
                if (surviving.isNotEmpty()) {
                    publishState {
                        it.copy(
                            discovery = surviving,
                            discoveryEnabled = true,
                            isDiscoveryLoading = false,
                        )
                    }
                    return
                }
            }
            publishState { it.copy(discovery = emptyList(), discoveryEnabled = true, discoveryPool = emptyList()) }
            if (!preserveCachedEmpty) {
                onTeaserPersisted(emptyList())
            }
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
                val liveTeaser = currentState().discovery
                onTeaserPersisted(liveTeaser)
                scheduleCoverRecovery(liveTeaser)
            } else if (state.discovery.any { it.coverUrl.isNullOrBlank() }) {
                scheduleCoverRecovery(state.discovery)
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
        val liveTeaser = currentState().discovery
        onTeaserPersisted(liveTeaser)
        scheduleCoverRecovery(liveTeaser)
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
                val livePolicy = sourcePolicies.current(mediaType)
                if (!livePolicy.inventoryComplete) {
                    (sourcePolicies as? DiscoverySourcePolicyResolver)?.retryInventoryLoad(mediaType)
                }
                val snapshot = runCatchingUnlessCancelled { repository.publication(mediaType) }.getOrNull()
                if (cachedDiscoveryPool.isEmpty() && snapshot != null && livePolicy.inventoryComplete) {
                    val recovered = projectPool(
                        snapshot.eligibleSuggestions.filter(livePolicy::allows),
                        cachedPolicy,
                    )
                    if (recovered.isNotEmpty()) {
                        cachedDiscoveryPool = recovered
                        publishState { it.copy(isDiscoveryLoading = false, isDiscoveryFailed = false) }
                        updateDiscoveryTeaserLocked(advanceOffset = false, forceUpdate = true)
                    }
                }
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
                        if (cachedDiscoveryPool.isNotEmpty() && currentState().discovery.isEmpty()) {
                            updateDiscoveryTeaserLocked(advanceOffset = false, forceUpdate = true)
                        }
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

    fun hideDiscoveryItem(item: HomeHubDiscoveryItem) {
        dismissDiscoveryItemOptimistically(item, DiscoverySignalType.HIDE)
    }

    fun markDiscoveryItemConsumed(item: HomeHubDiscoveryItem) {
        dismissDiscoveryItemOptimistically(item, DiscoverySignalType.CONSUMED)
    }

    private fun dismissDiscoveryItemOptimistically(
        item: HomeHubDiscoveryItem,
        signalType: DiscoverySignalType,
    ) {
        val cleanTitle = item.cleanTitle
        if (cleanTitle.isBlank() || item.mediaType != mediaType) return
        optimisticDismissedTitles += cleanTitle
        val targetCount = preferences.teaserCount().get().coerceIn(3, 20)
        // Synchronous UI removal + local in-place backfill (<1ms) before any DB/network work.
        publishState { current ->
            val updatedPool = current.discoveryPool.filterNot { it.cleanTitle == cleanTitle }
            val patchedTeaser = patchAfterLocalLike(
                currentTeaser = current.discovery,
                persistedPool = updatedPool,
                likedCleanTitle = cleanTitle,
                targetCount = maxOf(targetCount, current.discovery.size),
                dismissedTitles = optimisticDismissedTitles,
            )
            current.copy(
                discovery = patchedTeaser,
                discoveryPool = updatedPool,
            )
        }
        scope.launchIO {
            mutex.withLock {
                dismissedSessionTitles += cleanTitle
                dismissedSessionTitles = pruneDismissedSessionTitles(dismissedSessionTitles).toMutableSet()
                cachedDiscoveryPool = cachedDiscoveryPool.filterNot { it.cleanTitle == cleanTitle }
                onTeaserPersisted(currentState().discovery)
            }
            TasteSignalRecorder.record(
                repository = repository,
                item = item.toDiscoverySuggestion(),
                signalType = signalType,
                altKeyEnrichmentScope = scope,
            )
        }
    }

    fun restoreHiddenTeaserItem(item: HomeHubDiscoveryItem) {
        if (item.cleanTitle.isBlank() || item.mediaType != mediaType) return
        optimisticDismissedTitles -= item.cleanTitle
        scope.launchIO {
            repository.undoHide(item.mediaType, item.cleanTitle)
            mutex.withLock {
                dismissedSessionTitles -= item.cleanTitle
                if (!preferences.discoveryEnabled().get()) return@withLock
                val snapshot = runCatchingUnlessCancelled {
                    repository.publication(mediaType)
                }.getOrNull()?.takeIf { it.generation > 0L || it.suggestions.isNotEmpty() }
                    ?: repository.observePublication(mediaType).first()
                val blacklist = repository.getBlacklistedTags(mediaType)
                val shownMap = repository.getRecentExclusionsWithTimestamp(mediaType)
                val policy = DiscoveryCandidatePolicy(
                    spec = preferences.genreFilterSpec(mediaType),
                    blacklistedTags = blacklist,
                )
                val projected = projectPool(snapshot.eligibleSuggestions, policy)
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
                val liveTeaser = currentState().discovery
                onTeaserPersisted(liveTeaser)
                scheduleCoverRecovery(liveTeaser)
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
    val inventoryFailure: Throwable? = null,
)

/**
 * Shared cold-start visibility rule for HomeHub and DiscoveryFeed.
 * Once [DiscoverySourcePolicy.inventoryComplete] is true, strict live source authorization applies.
 * While extensions/sources are still cold-booting ([DiscoverySourcePolicy.inventoryComplete] == false),
 * native cards from an already-committed SQLite publication ([DiscoveryPublicationSnapshot.generation] > 0L)
 * are shown provisionally instead of blocking the UI behind a 5–10s skeleton, then reconciled as
 * soon as the inventory finishes loading. External providers are always enforced synchronously.
 */
internal fun allowsColdStartDisplay(
    candidate: DiscoverySuggestion,
    sourcePolicy: DiscoverySourcePolicy,
    allowProvisionalNative: Boolean,
): Boolean {
    if (sourcePolicy.inventoryComplete) return sourcePolicy.allows(candidate)
    if (allowProvisionalNative && candidate.sourceId != null) {
        return candidate.mediaType == sourcePolicy.mediaType
    }
    return sourcePolicy.allows(candidate)
}

internal fun selectColdStartVisibleCandidates(
    snapshot: DiscoveryPublicationSnapshot,
    sourcePolicy: DiscoverySourcePolicy,
): List<DiscoverySuggestion> {
    if (sourcePolicy.inventoryComplete) {
        return snapshot.eligibleSuggestions.filter(sourcePolicy::allows)
    }
    val allowProvisionalNative = snapshot.generation > 0L
    return snapshot.eligibleSuggestions.filter { candidate ->
        allowsColdStartDisplay(candidate, sourcePolicy, allowProvisionalNative)
    }
}

private data class DiscoveryObservationState(
    val snapshot: DiscoveryPublicationSnapshot,
    val visible: List<DiscoverySuggestion>,
    val enabled: Boolean,
    val count: Int,
    val policy: DiscoveryCandidatePolicy,
    val sourcePolicy: DiscoverySourcePolicy,
    val failedCsv: String,
    val lastStatus: String,
    val lastGenAt: Long,
    val sourceFailureCount: Int,
    val inventoryComplete: Boolean = true,
    val inventoryFailure: Throwable? = null,
    val snapshotPendingSync: Boolean = false,
    val usedProvisionalColdStart: Boolean = false,
)
