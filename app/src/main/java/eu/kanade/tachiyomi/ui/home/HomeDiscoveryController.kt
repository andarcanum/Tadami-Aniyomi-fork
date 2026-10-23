package eu.kanade.tachiyomi.ui.home

import android.content.Context
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryCandidatePolicy
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder
import eu.kanade.tachiyomi.data.discovery.dedupeCrossRow
import eu.kanade.tachiyomi.data.discovery.expandGenreSet
import eu.kanade.tachiyomi.data.discovery.isBlacklisted
import eu.kanade.tachiyomi.ui.discovery.ManualRefreshAction
import eu.kanade.tachiyomi.ui.discovery.manualRefreshFlow
import eu.kanade.tachiyomi.ui.discovery.remainingCooldownSeconds
import eu.kanade.tachiyomi.ui.discovery.resolveManualRefreshAction
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository

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
) {
    private val mutex = Mutex()
    private var cachedDiscoveryPool: List<DiscoverySuggestion> = emptyList()
    private var discoveryOffset: Int = 0
    private var lastReentryTime: Long = 0L
    private var lastRefreshClickTime: Long = 0L
    private var dismissedSessionTitles = mutableSetOf<String>()

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
                onStateUpdate { it.copy(isDiscoveryRefreshing = refreshing) }
                if (wasRefreshing && !refreshing) {
                    mutex.withLock {
                        val failedCsv = preferences.lastFailedRows(mediaType).get()
                        val lastStatus = preferences.lastGenerationStatus(mediaType).get()
                        val isFailed =
                            (lastStatus == "FAILED") || (failedCsv.isNotBlank() && cachedDiscoveryPool.isEmpty())
                        onStateUpdate { it.copy(isDiscoveryFailed = isFailed) }
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
                ) { hidden, consumed -> hidden to consumed },
                combine(
                    repository.subscribeBlacklist(mediaType),
                    preferences.observeGenreFilterSpec(),
                    preferences.lastFailedRows(mediaType).changes(),
                    preferences.lastGenerationStatus(mediaType).changes(),
                    preferences.lastGenerationAt(mediaType).changes(),
                ) { blacklist, filterSpec, failedCsv, lastStatus, lastGenAt ->
                    val policy = DiscoveryCandidatePolicy(
                        spec = filterSpec,
                        blacklistedTags = blacklist,
                    )
                    Triple(policy, failedCsv, lastStatus to lastGenAt)
                },
            ) { items, enabled, count, hiddenAndConsumed, (policy, failedCsv, statusAndAt) ->
                val (hidden, consumed) = hiddenAndConsumed
                val (lastStatus, lastGenAt) = statusAndAt
                val visible = items.filterNot { it.cleanTitle in hidden || it.cleanTitle in consumed }
                DiscoveryObservationState(
                    visible = visible,
                    enabled = enabled,
                    count = count,
                    policy = policy,
                    failedCsv = failedCsv,
                    lastStatus = lastStatus,
                    lastGenAt = lastGenAt,
                )
            }.collectLatest { obs ->
                mutex.withLock {
                    if (!obs.enabled) {
                        cachedDiscoveryPool = emptyList()
                        onStateUpdate {
                            it.copy(
                                discovery = emptyList(),
                                discoveryEnabled = false,
                                discoveryPool = emptyList(),
                                isDiscoveryFailed = false,
                            )
                        }
                        return@collectLatest
                    }
                    val filtered = obs.visible.filter { obs.policy.isAllowed(it) }
                    cachedDiscoveryPool = dedupeCrossRow(filtered).filterNot { it.cleanTitle in dismissedSessionTitles }
                    val shownAtByTitle = runCatching {
                        repository.getRecentExclusionsWithTimestamp(mediaType)
                    }.getOrDefault(emptyMap())

                    val updatedPool = cachedDiscoveryPool.map { item ->
                        item.toHomeHubDiscoveryItem(shownAtByTitle[item.cleanTitle])
                    }
                    val lastUpdated = obs.visible.maxOfOrNull { it.createdAt }
                        ?: runCatching { repository.lastUpdatedAt(mediaType) }.getOrNull()
                        ?: obs.lastGenAt.takeIf { it > 0L }
                    val isFailed =
                        (obs.lastStatus == "FAILED") || (obs.failedCsv.isNotBlank() && cachedDiscoveryPool.isEmpty())
                    onStateUpdate {
                        it.copy(
                            discoveryPool = updatedPool,
                            discoveryEnabled = true,
                            discoveryLastUpdated = lastUpdated,
                            isDiscoveryFailed = isFailed,
                        )
                    }

                    val isFirstTeaserLoad = currentState().discovery.isEmpty()
                    updateDiscoveryTeaserLocked(
                        advanceOffset = false,
                        forceUpdate = isFirstTeaserLoad,
                        teaserCount = obs.count,
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
    ) {
        if (!preferences.discoveryEnabled().get()) {
            onStateUpdate { it.copy(discovery = emptyList(), discoveryEnabled = false, discoveryPool = emptyList()) }
            return
        }
        val count = teaserCount ?: preferences.teaserCount().get().coerceIn(3, 20)
        val pool = cachedDiscoveryPool
        if (pool.isEmpty()) {
            onStateUpdate { it.copy(discovery = emptyList(), discoveryEnabled = true, discoveryPool = emptyList()) }
            return
        }

        val state = currentState()
        if (!forceUpdate && !advanceOffset && state.discovery.isNotEmpty()) {
            val validCleanTitles = pool.mapTo(HashSet()) { it.cleanTitle }
            val shownMap = runCatching {
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
            )

            val updatedPool = pool.map { p ->
                p.toHomeHubDiscoveryItem(shownMap[p.cleanTitle])
            }

            if (patched != state.discovery || updatedPool != state.discoveryPool) {
                onStateUpdate {
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

        val shownMap = runCatching {
            repository.getRecentExclusionsWithTimestamp(mediaType)
        }.getOrDefault(emptyMap())
        val shownTitles = shownMap.keys
        val currentTitles = state.discovery.mapTo(HashSet()) { it.cleanTitle }

        val teaser = selectFreshTeaserItems(
            pool = pool.filterNot { it.cleanTitle in dismissedSessionTitles },
            shownTitles = shownTitles,
            count = count,
            offset = discoveryOffset,
            shownCutoffMap = shownMap,
            currentTitles = currentTitles,
        )

        if (notifyIfLimited && currentTitles.isNotEmpty()) {
            val distinctPoolSize = pool.distinctBy { it.cleanTitle }.size
            if (shouldNotifyDiscoveryPoolLimited(teaser, currentTitles, distinctPoolSize, count)) {
                onEmitEffect(HomeHubEffect.DiscoveryPoolLimited)
            }
        }

        val updatedPool = pool.map { p ->
            p.toHomeHubDiscoveryItem(shownMap[p.cleanTitle])
        }
        onStateUpdate {
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
                updateDiscoveryTeaserLocked(advanceOffset = true)
            }
        }
    }

    fun rotateOrRefreshDiscovery() {
        if (!preferences.discoveryEnabled().get()) return
        val now = System.currentTimeMillis()
        if (now - lastRefreshClickTime < 500L) return
        lastRefreshClickTime = now

        val cooldown = remainingCooldownSeconds(
            preferences.manualRefreshAt(mediaType).get().takeIf { it > 0L },
            now,
        )
        val action = resolveManualRefreshAction(currentState().isDiscoveryRefreshing, cooldown)
        if (action == ManualRefreshAction.CANCEL) {
            DiscoveryUpdateJob.stop(context, mediaType)
            onStateUpdate { it.copy(isDiscoveryRefreshing = false) }
            return
        }

        if (action == ManualRefreshAction.ROTATE || !preferences.tryStartManualRefresh(mediaType, now)) {
            scope.launchIO {
                mutex.withLock {
                    updateDiscoveryTeaserLocked(advanceOffset = true, forceUpdate = true, notifyIfLimited = true)
                }
            }
            return
        }

        onStateUpdate { it.copy(isDiscoveryRefreshing = true) }
        DiscoveryUpdateJob.refreshNow(context, mediaType)
    }

    fun likeDiscoveryItem(item: HomeHubDiscoveryItem) {
        if (item.cleanTitle.isBlank()) return
        scope.launchIO {
            val suggestion = item.toDiscoverySuggestion()
            val recorded = TasteSignalRecorder.record(
                repository,
                suggestion,
                DiscoverySignalType.LIKE,
            )
            if (recorded) {
                mutex.withLock {
                    dismissedSessionTitles += item.cleanTitle
                    dismissedSessionTitles = pruneDismissedSessionTitles(dismissedSessionTitles).toMutableSet()
                    val dismissedTitles = dismissedSessionTitles.toSet()
                    cachedDiscoveryPool = cachedDiscoveryPool.filterNot { it.cleanTitle == item.cleanTitle }
                    onStateUpdate { current ->
                        val updated = current.reduceDismissTeaserCard(
                            likedCleanTitle = item.cleanTitle,
                            targetCount = current.discovery.size,
                            dismissedTitles = dismissedTitles,
                        )
                        val updatedPool = current.discoveryPool.filterNot { it.cleanTitle == item.cleanTitle }
                        updated.copy(discoveryPool = updatedPool)
                    }
                }
            }
        }
    }

    fun restoreHiddenTeaserItem(item: HomeHubDiscoveryItem) {
        if (item.cleanTitle.isBlank()) return
        scope.launchIO {
            repository.unhide(item.mediaType, item.cleanTitle)
            repository.removeSignal(item.mediaType, item.cleanTitle)
            mutex.withLock {
                dismissedSessionTitles -= item.cleanTitle
                val enabled = preferences.discoveryEnabled().get()
                if (!enabled) return@withLock
                val blacklist = repository.getBlacklistedTags(mediaType)
                val filterSpec = preferences.genreFilterSpec()
                val policy = DiscoveryCandidatePolicy(spec = filterSpec, blacklistedTags = blacklist)
                val suggestion = item.toDiscoverySuggestion()
                if (!policy.isAllowed(suggestion)) return@withLock
                val consumedTitles = runCatching {
                    repository.getSignals(mediaType)
                        .filter { it.signalType.excludesFromFeed }
                        .mapTo(HashSet()) { it.cleanTitle }
                }.getOrDefault(emptySet())
                if (item.cleanTitle in consumedTitles) return@withLock

                val shownMap = runCatching {
                    repository.getRecentExclusionsWithTimestamp(mediaType)
                }.getOrDefault(emptyMap())
                val freshItem = item.copy(shownAt = shownMap[item.cleanTitle])
                onStateUpdate { current ->
                    val updatedDiscovery = restoreTeaserItem(
                        current = current.discovery,
                        restored = freshItem,
                        targetCount = current.discovery.size,
                    )
                    val updatedPool =
                        listOf(freshItem) + current.discoveryPool.filterNot { it.cleanTitle == freshItem.cleanTitle }
                    current.copy(
                        discovery = updatedDiscovery,
                        discoveryPool = updatedPool,
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

private data class DiscoveryObservationState(
    val visible: List<DiscoverySuggestion>,
    val enabled: Boolean,
    val count: Int,
    val policy: DiscoveryCandidatePolicy,
    val failedCsv: String,
    val lastStatus: String,
    val lastGenAt: Long,
)
