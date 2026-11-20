package eu.kanade.tachiyomi.ui.discovery

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.CandidateVerdict
import eu.kanade.tachiyomi.data.discovery.CompositeTrendingSource
import eu.kanade.tachiyomi.data.discovery.DISCOVERY_POOL_TARGET
import eu.kanade.tachiyomi.data.discovery.DiscoveryCandidatePolicy
import eu.kanade.tachiyomi.data.discovery.DiscoveryLibraryAdder
import eu.kanade.tachiyomi.data.discovery.DiscoveryMixQuotas
import eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem
import eu.kanade.tachiyomi.data.discovery.DiscoverySourcePolicyResolver
import eu.kanade.tachiyomi.data.discovery.DiscoveryTrendingSource
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.META_PREFETCH_COUNT
import eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder
import eu.kanade.tachiyomi.data.discovery.dedupeCrossRow
import eu.kanade.tachiyomi.data.discovery.interleaveMix
import eu.kanade.tachiyomi.data.discovery.recoverMissingCovers
import eu.kanade.tachiyomi.data.discovery.rrfScores
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.ui.home.allowsColdStartDisplay
import eu.kanade.tachiyomi.ui.home.selectColdStartVisibleCandidates
import eu.kanade.tachiyomi.util.system.isRunningOrEnqueuedFlow
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPublicationSnapshot
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import tachiyomi.core.common.i18n.stringResource as contextStringResource

internal data class DiscoveryFeedUiState(
    val mediaType: DiscoveryMediaType = DiscoveryMediaType.ANIME,
    val rows: Map<DiscoveryRowType, List<DiscoverySuggestion>> = emptyMap(),
    val mix: List<DiscoverySuggestion> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val lastUpdatedAt: Long? = null,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = true,
    val isProvisionalColdStart: Boolean = false,
    val hiddenSnackbarTitle: String? = null,
    val addedSnackbarTitle: String? = null,
    // «+» промахнулся (внешний провайдер или нет точного совпадения): экран
    // открывает каталог источника/глобальный поиск вместо тупика.
    val searchFallbackItem: DiscoverySuggestion? = null,
    // Прямое открытие plugin-bound карточки: item, который сейчас резолвится (guard повторного тапа).
    val openingItem: DiscoverySuggestion? = null,
    val addingTitles: Set<String> = emptySet(),
    // Ряды, упавшие при последней генерации: в ленте показан устаревший кэш —
    // surfaced баннером вместо молчаливой стужи.
    val failedRows: Set<DiscoveryRowType> = emptySet(),
    val poolSize: Int = 0,
    val failedSourceCount: Int = 0,
    val lastGenerationStatus: String = "",
    // B2: «Скрыть всё с тегом X» — tag к числу затронутых подборок (undo-snackbar).
    val tagSnackbar: Pair<String, Int>? = null,
    // Lazy cover recovery: инкремент на каждое восстановленное покрытие — ключ рекомпозиции обложек.
    val coverRecoveryTick: Int = 0,
    // Табы, видимые при текущих настройках рядов/провайдеров (выключенный ряд = мёртвый таб).
    val visibleTabs: Set<FeedSignalTab> = FeedSignalTab.entries.toSet(),
    val progressText: String? = null,
    val tabOffsets: Map<FeedSignalTab, Int> = emptyMap(),
    val activeWindows: Map<FeedSignalTab, List<DiscoverySuggestion>> = emptyMap(),
)

/** CSV ключей упавших рядов из prefs → набор [DiscoveryRowType]. */
internal fun parseFailedRows(csv: String): Set<DiscoveryRowType> = csv
    .splitToSequence(",")
    .filter { it.isNotBlank() }
    .mapNotNull { DiscoveryRowType.fromKey(it) }
    .toSet()

/** Сколько текущих подборок скроется при блэклисте [tag] (для undo-snackbar «Скрыто N»). */
internal fun countBlacklistImpact(items: List<DiscoverySuggestion>, tag: String): Int {
    val strictPolicy = DiscoveryCandidatePolicy(blacklistedTags = setOf(tag))
    return items.count { item ->
        when {
            item.genres.isNotEmpty() -> strictPolicy.isForbidden(item)
            item.rowType == DiscoveryRowType.TASTE && !item.reason.isNullOrBlank() ->
                strictPolicy.evaluate(emptyList(), DiscoveryCandidatePolicy.extractFallbackTags(item.reason)) is
                    CandidateVerdict.Rejected
            else -> false
        }
    }
}

internal enum class FeedSignalTab { MIX, SIMILAR, TASTE, FRESH, SOURCE }

internal fun FeedSignalTab.rowType(): DiscoveryRowType? = when (this) {
    FeedSignalTab.MIX -> null
    FeedSignalTab.SIMILAR -> DiscoveryRowType.LIKE
    FeedSignalTab.TASTE -> DiscoveryRowType.TASTE
    FeedSignalTab.FRESH -> DiscoveryRowType.TREND
    FeedSignalTab.SOURCE -> DiscoveryRowType.SOURCE
}

internal fun itemsForTab(state: DiscoveryFeedUiState, tab: FeedSignalTab): List<DiscoverySuggestion> =
    if (tab == FeedSignalTab.MIX) state.mix else state.rows[tab.rowType()].orEmpty()

internal fun filterByProvider(items: List<DiscoverySuggestion>, provider: String?): List<DiscoverySuggestion> =
    if (provider.isNullOrEmpty()) items else items.filter { it.provider == provider }

internal fun providerOptions(state: DiscoveryFeedUiState): List<String> =
    state.rows.values.flatten().map { it.provider }.distinct().sorted()

internal const val DISCOVERY_REFRESH_COOLDOWN_MS = DiscoveryPreferences.MANUAL_REFRESH_COOLDOWN_MS

/** Legacy name: user-visible refresh activity includes configuration work, but ownership stays separate. */
internal fun manualRefreshFlow(workManager: WorkManager, mediaType: DiscoveryMediaType): Flow<Boolean> = combine(
    workManager.isRunningOrEnqueuedFlow(DiscoveryUpdateJob.manualWorkTag(mediaType)),
    workManager.isRunningOrEnqueuedFlow(DiscoveryUpdateJob.configurationWorkName(mediaType)),
) { manual, configuration -> manual || configuration }.distinctUntilChanged()

/**
 * Observes user-visible discovery generation work for [mediaType]: manual refreshes,
 * debounced configuration refreshes, first-launch bootstrap, and upgrade binding backfill.
 * Extra tags degrade safely to false when unit test doubles stub only manual/configuration tags.
 */
internal fun activeDiscoveryRefreshFlow(
    workManager: WorkManager,
    mediaType: DiscoveryMediaType,
): Flow<Boolean> {
    val bootstrapFlow = runCatching {
        workManager.isRunningOrEnqueuedFlow(DiscoveryUpdateJob.bootstrapWorkName(mediaType))
    }.getOrElse { flowOf(false) }
    val backfillFlow = runCatching {
        workManager.isRunningOrEnqueuedFlow(DiscoveryUpdateJob.TAG_BACKFILL)
    }.getOrElse { flowOf(false) }
    return combine(
        manualRefreshFlow(workManager, mediaType),
        bootstrapFlow,
        backfillFlow,
    ) { manualOrConfig, bootstrap, backfill ->
        manualOrConfig || bootstrap || backfill
    }.distinctUntilChanged()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun manualRefreshWorkInfoFlow(
    workManager: WorkManager,
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
): Flow<WorkInfo?> = preferences.manualRefreshWorkId(mediaType).changes()
    .distinctUntilChanged()
    .flatMapLatest { requestId ->
        val uuid = runCatching { UUID.fromString(requestId) }.getOrNull()
        if (uuid == null) flowOf(null) else workManager.getWorkInfoByIdFlow(uuid)
    }

internal fun claimManualRefreshFailure(
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
    workInfo: WorkInfo?,
): Boolean = workInfo?.state == WorkInfo.State.FAILED &&
    preferences.claimManualRefreshFailure(mediaType, workInfo.id.toString())

internal fun remainingCooldownSeconds(
    lastRefreshAt: Long?,
    now: Long,
    cooldownMs: Long = DISCOVERY_REFRESH_COOLDOWN_MS,
): Long {
    if (lastRefreshAt == null) return 0L
    val elapsed = now - lastRefreshAt
    return if (elapsed in 0 until cooldownMs) {
        val remainingMs = cooldownMs - elapsed
        (remainingMs + 999L) / 1000L
    } else {
        0L
    }
}

internal fun canManualRefresh(
    lastRefreshAt: Long?,
    now: Long,
    cooldownMs: Long = DISCOVERY_REFRESH_COOLDOWN_MS,
): Boolean = remainingCooldownSeconds(lastRefreshAt, now, cooldownMs) == 0L

internal enum class UpdatedLabelKind { MINUTES, HOURS, NEVER }

internal fun resolveUpdatedLabel(lastUpdatedAt: Long?, now: Long): Pair<UpdatedLabelKind, Long?> {
    if (lastUpdatedAt == null) return UpdatedLabelKind.NEVER to null
    val diff = (now - lastUpdatedAt).coerceAtLeast(0)
    val minutes = diff / 60_000
    return when {
        minutes < 60 -> UpdatedLabelKind.MINUTES to minutes
        else -> UpdatedLabelKind.HOURS to (minutes / 60)
    }
}

internal fun groupFeedRows(items: List<DiscoverySuggestion>): Map<DiscoveryRowType, List<DiscoverySuggestion>> =
    items.groupBy { it.rowType }
        .toSortedMap(compareBy { it.ordinal })

internal fun feedTabPool(
    state: DiscoveryFeedUiState,
    tab: FeedSignalTab,
    provider: String?,
    policy: DiscoverySourcePolicy? = null,
): List<DiscoverySuggestion> {
    val tabItems = itemsForTab(state, tab)
    val allowed = if (policy != null) {
        val allowProvisional = state.isProvisionalColdStart && !policy.inventoryComplete
        tabItems.filter { allowsColdStartDisplay(it, policy, allowProvisional) }
    } else {
        tabItems
    }
    return filterByProvider(allowed, provider)
}

internal fun sliceFeedForGenre(
    state: DiscoveryFeedUiState,
    tab: FeedSignalTab,
    provider: String?,
    selectedGenre: String?,
    policy: DiscoverySourcePolicy? = null,
): List<DiscoverySuggestion> {
    if (selectedGenre != null) {
        val fullPool = feedTabPool(state, tab, provider, policy)
        return filterDiscoveryFeed(fullPool, selectedGenre)
    }
    val window = state.activeWindows[tab] ?: itemsForTab(state, tab)
    val allowed = if (policy != null) {
        val allowProvisional = state.isProvisionalColdStart && !policy.inventoryComplete
        window.filter { allowsColdStartDisplay(it, policy, allowProvisional) }
    } else {
        window
    }
    return filterByProvider(allowed, provider)
}

internal fun mapDiscoveryProviderToReason(provider: String): SuggestionReason = when (provider.lowercase()) {
    "anilist", "anilist_trend" -> SuggestionReason.EXTERNAL_ANILIST
    "myanimelist", "mal", "jikan", "jikan_trend" -> SuggestionReason.EXTERNAL_MAL
    "mangaupdates" -> SuggestionReason.EXTERNAL_MU
    "novelupdates" -> SuggestionReason.EXTERNAL_NU
    "shikimori", "shikimori_trend" -> SuggestionReason.EXTERNAL_SHIKIMORI
    else -> SuggestionReason.SEARCH_TITLE
}

internal fun buildDiscoverySuggestionItem(
    title: String,
    trustedAliases: Set<String>,
    coverUrl: String?,
    provider: String,
    mediaType: DiscoveryMediaType,
): SuggestionItem {
    val queries = (listOf(title) + trustedAliases)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return SuggestionItem(
        title = title,
        searchQueries = queries.ifEmpty { listOf(title) },
        thumbnailUrl = coverUrl,
        providerName = provider,
        providerUrl = "",
        providerId = null,
        mediaType = when (mediaType) {
            DiscoveryMediaType.ANIME -> SuggestionMediaType.ANIME
            DiscoveryMediaType.MANGA -> SuggestionMediaType.MANGA
            DiscoveryMediaType.NOVEL -> SuggestionMediaType.NOVEL
        },
        reason = mapDiscoveryProviderToReason(provider),
    )
}

internal fun DiscoverySuggestion.toSuggestionItem(): SuggestionItem = buildDiscoverySuggestionItem(
    title = title,
    trustedAliases = trustedAliases,
    coverUrl = coverUrl,
    provider = provider,
    mediaType = mediaType,
)

/** SWR-прогрев globalMetaCache: шторка карточки открывается без спиннера. */
internal suspend fun prefetchDiscoveryMeta(
    items: List<DiscoverySuggestion>,
    mediaType: DiscoveryMediaType,
    trendingSource: DiscoveryTrendingSource,
    limit: Int = META_PREFETCH_COUNT,
) {
    items.take(limit).forEach { item ->
        try {
            trendingSource.fetchMeta(item.title, mediaType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Одна карточка без меты не должна останавливать прогрев остальных.
        }
    }
}

/**
 * Полный экран «Для тебя» v3: читает только кэш ленты из БД (ноль сети на рендер);
 *_mix_ = интерлив квот сигналов; табы/чипсы фильтруют поток; «+» добавляет в
 * библиотеку точным поиском в источнике рекомендации (для внешних провайдеров
 * и при промахе открывает поиск); лонг-пресс скрывает с Undo.
 */
internal class DiscoveryFeedScreenModel(
    initialMedia: DiscoveryMediaType,
    private val context: Context,
    private val repository: DiscoveryRepository = Injekt.get(),
    private val preferences: DiscoveryPreferences = Injekt.get(),
    private val trendingSource: DiscoveryTrendingSource = CompositeTrendingSource(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    sourcePolicyProvider: DiscoverySourcePolicyProvider? = null,
    private val adder: DiscoveryLibraryAdder = DiscoveryLibraryAdder(
        sourcePolicyProvider = { sourcePolicyProvider ?: Injekt.get() },
    ),
) : StateScreenModel<DiscoveryFeedUiState>(DiscoveryFeedUiState(mediaType = initialMedia)) {

    private val sourcePolicies by lazy { sourcePolicyProvider ?: Injekt.get<DiscoverySourcePolicyProvider>() }
    private var observeJob: Job? = null
    private var progressJob: Job? = null
    private var failureJob: Job? = null
    private var visibleTabsJob: Job? = null
    private var prefetchJob: Job? = null
    private val hideUndoOperations = DiscoveryHideUndoQueue(screenModelScope)
    private var lastHidden: DiscoverySuggestion? = null
    private var lastHiddenTab: FeedSignalTab = FeedSignalTab.MIX
    private var lastBlacklistedTag: String? = null
    private var lastObservedPriorityGenres: Set<String>? = null
    private var lastObservedInventoryComplete: Boolean? = null
    private var lastObservedUsedProvisionalColdStart: Boolean = false
    private val optimisticExcludedTitles: MutableSet<Pair<DiscoveryMediaType, String>> = ConcurrentHashMap.newKeySet()

    fun start() {
        observeMedia(state.value.mediaType)
        observeVisibleTabs()
    }

    private fun observeProgress(mediaType: DiscoveryMediaType) {
        progressJob?.cancel()
        progressJob = screenModelScope.launchIO {
            eu.kanade.tachiyomi.data.discovery.DiscoveryProgressTracker.progress(mediaType).collectLatest { progress ->
                mutableState.update { current ->
                    current.copy(
                        progressText = if (progress.isRunning &&
                            (progress.mediaType == null || progress.mediaType == current.mediaType)
                        ) {
                            progress.stageText
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    /** Табы по настройкам рядов/провайдеров: выключенный ряд — мёртвый таб, скрываем. */
    private fun observeVisibleTabs() {
        visibleTabsJob?.cancel()
        visibleTabsJob = screenModelScope.launchIO {
            combine(
                preferences.rowLikeEnabled().changes(),
                preferences.rowTrendEnabled().changes(),
                preferences.rowTasteEnabled().changes(),
                preferences.rowSourceEnabled().changes(),
                preferences.externalProvidersEnabled().changes(),
            ) { like, trend, taste, source, external ->
                buildSet {
                    add(FeedSignalTab.MIX)
                    if (like) add(FeedSignalTab.SIMILAR)
                    if (taste) add(FeedSignalTab.TASTE)
                    if (trend && external) add(FeedSignalTab.FRESH)
                    if (source) add(FeedSignalTab.SOURCE)
                }
            }.collectLatest { tabs ->
                mutableState.update { it.copy(visibleTabs = tabs) }
            }
        }
    }

    private fun observeMedia(mediaType: DiscoveryMediaType) {
        lastObservedPriorityGenres = null
        lastObservedInventoryComplete = null
        lastObservedUsedProvisionalColdStart = false
        observeProgress(mediaType)
        failureJob?.cancel()
        failureJob = screenModelScope.launchIO {
            manualRefreshWorkInfoFlow(context.workManager, preferences, mediaType).collectLatest { workInfo ->
                withContext(Dispatchers.Main.immediate) {
                    if (claimManualRefreshFailure(preferences, mediaType, workInfo)) {
                        context.toast(context.contextStringResource(AYMR.strings.for_you_refresh_failed))
                    }
                }
            }
        }
        observeJob?.cancel()
        observeJob = screenModelScope.launchIO {
            val inventoryFailureFlow = (sourcePolicies as? DiscoverySourcePolicyResolver)
                ?.observeInventoryFailure(mediaType)
                ?: flowOf(null)
            combine(
                repository.observePublication(mediaType),
                refreshingFlow(mediaType),
                repository.subscribeHidden(mediaType),
                preferences.lastFailedRows(mediaType).changes(),
                combine(
                    repository.subscribeBlacklist(mediaType),
                    preferences.observeGenreFilterSpec(mediaType),
                    sourcePolicies.observe(mediaType),
                    combine(
                        preferences.lastSourceFailureCount(mediaType).changes(),
                        preferences.lastGenerationStatus(mediaType).changes(),
                        inventoryFailureFlow,
                    ) { failureCount, genStatus, inventoryFailure ->
                        Triple(failureCount, genStatus, inventoryFailure)
                    },
                ) { blacklist, filterSpec, sourcePolicy, statusTriple ->
                    Triple(
                        DiscoveryCandidatePolicy(
                            spec = filterSpec,
                            blacklistedTags = blacklist,
                        ),
                        sourcePolicy,
                        statusTriple,
                    )
                },
            ) { snapshot, refreshing, hidden, failedCsv, policyTriple ->
                FeedSources(
                    snapshot = snapshot,
                    refreshing = refreshing,
                    hidden = hidden,
                    failedCsv = failedCsv,
                    policy = policyTriple.first,
                    sourcePolicy = policyTriple.second,
                    failedSourceCount = policyTriple.third.first,
                    lastGenerationStatus = policyTriple.third.second,
                    inventoryFailure = policyTriple.third.third,
                )
            }
                .collectLatest { feedSources ->
                    val (
                        snapshot,
                        refreshing,
                        hidden,
                        failedCsv,
                        policy,
                        sources,
                        failedSourceCount,
                        lastGenStatus,
                        inventoryFailure,
                    ) = feedSources
                    val priorityChanged = lastObservedPriorityGenres != null &&
                        lastObservedPriorityGenres != policy.priorityGenres
                    lastObservedPriorityGenres = policy.priorityGenres
                    val wasProvisionalColdStart = lastObservedUsedProvisionalColdStart
                    val inventoryJustCompleted =
                        lastObservedInventoryComplete == false && sources.inventoryComplete
                    lastObservedInventoryComplete = sources.inventoryComplete
                    val allowProvisionalNative = !sources.inventoryComplete && snapshot.generation > 0L
                    val resetWindows = priorityChanged || (inventoryJustCompleted && !wasProvisionalColdStart)
                    val eligible = selectColdStartVisibleCandidates(snapshot, sources)
                        .filterNot { (mediaType to it.cleanTitle) in optimisticExcludedTitles }
                    val isProvisionalColdStart = allowProvisionalNative && eligible.any { it.sourceId != null }
                    lastObservedUsedProvisionalColdStart = isProvisionalColdStart
                    val visible = projectCandidates(eligible, policy, sources, allowProvisionalNative)
                    val rows = groupFeedRows(visible)
                    // RRF для fill-фазы: сырые скоры сигналов несопоставимы (LIKE 0..100 vs TREND 0.0).
                    val mixTotal = DiscoveryMixQuotas().total
                    val mix = if (policy.priorityGenres.isNotEmpty()) {
                        val (boosted, normal) = visible.partition { policy.hasPriorityBoost(it) }
                        if (boosted.isNotEmpty() && normal.isNotEmpty()) {
                            val boostedMix = interleaveTier(boosted, mixTotal)
                            val remaining = (mixTotal - boostedMix.size).coerceAtLeast(0)
                            val normalMix = if (remaining > 0) interleaveTier(normal, remaining) else emptyList()
                            boostedMix + normalMix
                        } else {
                            interleaveTier(visible, mixTotal)
                        }
                    } else {
                        interleaveTier(visible, mixTotal)
                    }
                    mutableState.update { current ->
                        if (current.mediaType != mediaType) return@update current
                        val liveAllowProvisional = isProvisionalColdStart && !sources.inventoryComplete
                        val liveMix = mix.filter {
                            allowsColdStartDisplay(it, sources, liveAllowProvisional) &&
                                (mediaType to it.cleanTitle) !in optimisticExcludedTitles
                        }
                        val liveRows = rows.mapValues { (_, row) ->
                            row.filter {
                                allowsColdStartDisplay(it, sources, liveAllowProvisional) &&
                                    (mediaType to it.cleanTitle) !in optimisticExcludedTitles
                            }
                        }.filterValues {
                            it.isNotEmpty()
                        }
                        val currentWindows = current.activeWindows
                        val updatedWindows = if (currentWindows.isEmpty() ||
                            currentWindows[FeedSignalTab.MIX].isNullOrEmpty()
                        ) {
                            if (liveMix.isNotEmpty()) {
                                mapOf(FeedSignalTab.MIX to liveMix)
                            } else {
                                emptyMap()
                            }
                        } else if (resetWindows) {
                            currentWindows.mapValues { (tab, _) ->
                                if (tab == FeedSignalTab.MIX) liveMix else liveRows[tab.rowType()].orEmpty()
                            }
                        } else {
                            currentWindows.mapValues { (tab, currentWindow) ->
                                val pool = if (tab == FeedSignalTab.MIX) liveMix else liveRows[tab.rowType()].orEmpty()
                                reconcileFeedWindow(
                                    currentWindow = currentWindow,
                                    pool = pool,
                                    targetSize = pool.size,
                                )
                            }
                        }
                        val snapshotPendingSync = snapshot.sourcePolicyRevision.isNotEmpty() &&
                            snapshot.sourcePolicyRevision != sources.revision
                        val waitingInventory = liveMix.isEmpty() && (
                            (!sources.inventoryComplete && inventoryFailure == null) ||
                                snapshotPendingSync
                            )
                        val effectiveStatus = if (
                            inventoryFailure != null && !sources.inventoryComplete && liveMix.isEmpty()
                        ) {
                            "FAILED"
                        } else {
                            lastGenStatus
                        }
                        current.copy(
                            rows = liveRows,
                            mix = liveMix,
                            hidden = hidden,
                            lastUpdatedAt = snapshot.suggestions.maxOfOrNull { s -> s.createdAt }
                                ?: snapshot.committedAt.takeIf { it > 0L },
                            isRefreshing = refreshing,
                            isLoading = waitingInventory,
                            isProvisionalColdStart = liveAllowProvisional && liveMix.isNotEmpty(),
                            failedRows = parseFailedRows(failedCsv),
                            poolSize = visible.size,
                            failedSourceCount = failedSourceCount,
                            lastGenerationStatus = effectiveStatus,
                            tabOffsets = if (resetWindows) emptyMap() else current.tabOffsets,
                            activeWindows = updatedWindows,
                        )
                    }
                    prefetchJob?.cancel()
                    prefetchJob = screenModelScope.launchIO {
                        kotlinx.coroutines.delay(500) // коалесцируем всплески эмиссий при регенерации рядов
                        prefetchDiscoveryMeta(mix, mediaType, trendingSource)
                        recoverMissingCovers(
                            mix,
                            mediaType,
                            trendingSource,
                            onRecovered = {
                                mutableState.update { s -> s.copy(coverRecoveryTick = s.coverRecoveryTick + 1) }
                            },
                        )
                    }
                }
        }
    }

    private fun interleaveTier(items: List<DiscoverySuggestion>, total: Int): List<DiscoverySuggestion> {
        if (items.isEmpty() || total <= 0) return emptyList()
        val tierRows = groupFeedRows(items).mapValues { (_, list) -> list.map { it.toRowItem() } }
        val byCleanTitle = items.associateBy { it.cleanTitle }
        return interleaveMix(tierRows, total = total, rrf = rrfScores(tierRows))
            .mapNotNull { row -> byCleanTitle[row.cleanTitle] }
    }

    private fun projectCandidates(
        items: List<DiscoverySuggestion>,
        policy: DiscoveryCandidatePolicy,
        sources: DiscoverySourcePolicy = sourcePolicies.current(state.value.mediaType),
        allowProvisionalNative: Boolean = false,
    ): List<DiscoverySuggestion> {
        val eligible = items.filter {
            allowsColdStartDisplay(it, sources, allowProvisionalNative) && policy.isAllowed(it)
        }
        return dedupeCrossRow(
            eligible.groupBy { it.rowType }.flatMap { (_, row) ->
                policy.filterRowCandidates(
                    row,
                    { it.genres },
                    scoreSelector = { it.score },
                    fillTarget = if (!policy.isStrictRequired) DISCOVERY_POOL_TARGET else null,
                )
            },
        )
    }

    private data class FeedSources(
        val snapshot: DiscoveryPublicationSnapshot,
        val refreshing: Boolean,
        val hidden: Set<String>,
        val failedCsv: String,
        val policy: DiscoveryCandidatePolicy,
        val sourcePolicy: DiscoverySourcePolicy,
        val failedSourceCount: Int,
        val lastGenerationStatus: String,
        val inventoryFailure: Throwable? = null,
    )

    private fun DiscoverySuggestion.toRowItem() = DiscoveryRowItem(
        title = title,
        cleanTitle = cleanTitle,
        coverUrl = coverUrl,
        reason = reason,
        seedTitle = seedTitle,
        provider = provider,
        score = score,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        genres = genres,
    )

    private fun refreshingFlow(mediaType: DiscoveryMediaType): Flow<Boolean> =
        activeDiscoveryRefreshFlow(context.workManager, mediaType)

    fun refreshNow(): Long {
        if (state.value.isRefreshing) return 0L
        val media = state.value.mediaType
        if (!sourcePolicies.current(media).inventoryComplete) {
            (sourcePolicies as? DiscoverySourcePolicyResolver)?.retryInventoryLoad(media)
        }
        val now = System.currentTimeMillis()
        val remaining = remainingCooldownSeconds(preferences.manualRefreshAt(media).get(), now)
        if (remaining > 0L) return remaining
        if (!preferences.tryStartManualRefresh(media, now)) {
            return remainingCooldownSeconds(preferences.manualRefreshAt(media).get(), now).coerceAtLeast(1L)
        }
        mutableState.update { it.copy(isRefreshing = true) }
        DiscoveryUpdateJob.refreshNow(context, media)
        return 0L
    }

    fun availableGenres(tab: FeedSignalTab, provider: String?): List<String> =
        extractAvailableGenres(feedTabPool(state.value, tab, provider, sourcePolicies.current(state.value.mediaType)))

    fun displayedItems(
        tab: FeedSignalTab,
        provider: String?,
        selectedGenre: String? = null,
    ): List<DiscoverySuggestion> = sliceFeedForGenre(
        state = state.value,
        tab = tab,
        provider = provider,
        selectedGenre = selectedGenre,
        policy = sourcePolicies.current(state.value.mediaType),
    )

    fun onTabSelected(tab: FeedSignalTab) {
        val currentState = state.value
        if (currentState.activeWindows[tab] == null) {
            val pool = itemsForTab(currentState, tab)
            mutableState.update { it.copy(activeWindows = it.activeWindows + (tab to pool)) }
        }
    }

    fun rotateOrRefreshFeed(currentTab: FeedSignalTab): Long {
        val currentState = state.value
        if (currentState.isRefreshing) return 0L
        val cooldown = refreshNow()
        if (cooldown > 0L) return cooldown
        // Full screen: every accepted tap requests a fresh source pool; keep the
        // current windows intact until the new publication arrives so cancelled or
        // offline refreshes do not drop visible cards.
        mutableState.update { it.copy(tabOffsets = emptyMap()) }
        return 0L
    }

    private fun applyOptimisticRemoval(
        removed: DiscoverySuggestion,
        activeTab: FeedSignalTab,
        markHidden: Boolean,
    ) {
        val cleanTitle = removed.cleanTitle
        if (cleanTitle.isBlank()) return
        val exclusionKey = removed.mediaType to cleanTitle
        optimisticExcludedTitles += exclusionKey
        val mediaOptimisticTitles = optimisticExcludedTitles
            .mapNotNullTo(HashSet()) { (media, title) -> title.takeIf { media == removed.mediaType } }
        mutableState.update { current ->
            if (current.mediaType != removed.mediaType) return@update current
            val updatedMix = current.mix.filterNot { it.cleanTitle == cleanTitle }
            val updatedRows = current.rows.mapValues { (_, list) ->
                list.filterNot { it.cleanTitle == cleanTitle }
            }.filterValues { it.isNotEmpty() }
            val pool = itemsForTab(current, activeTab)
            val currentWindow = current.activeWindows[activeTab] ?: pool
            val currentWindowTitles = currentWindow.mapTo(HashSet()) { it.cleanTitle }
            val candidate = selectBackfillCandidate(
                removedItem = removed,
                pool = pool,
                currentWindowTitles = currentWindowTitles,
                shownTitles = mediaOptimisticTitles,
            )
            val patchedActiveWindow = patchFeedWindowAfterRemoval(
                currentWindow = currentWindow,
                removedTitle = cleanTitle,
                replacement = candidate,
            )
            val updatedWindows = current.activeWindows
                .mapValues { (_, w) -> w.filterNot { it.cleanTitle == cleanTitle } } +
                (activeTab to patchedActiveWindow)
            current.copy(
                mix = updatedMix,
                rows = updatedRows,
                hidden = if (markHidden) current.hidden + cleanTitle else current.hidden,
                activeWindows = updatedWindows,
                hiddenSnackbarTitle = if (markHidden) removed.title else current.hiddenSnackbarTitle,
            )
        }
    }

    fun markItemExposed(cleanTitle: String) {
        if (cleanTitle.isBlank()) return
        screenModelScope.launchIO {
            repository.recordExposure(
                mediaType = state.value.mediaType,
                cleanTitles = listOf(cleanTitle),
            )
        }
    }

    fun cancelRefresh() {
        val media = state.value.mediaType
        DiscoveryUpdateJob.stop(context, media)
        mutableState.update { it.copy(isRefreshing = false, progressText = null) }
    }

    fun hide(item: DiscoverySuggestion, activeTab: FeedSignalTab = FeedSignalTab.MIX) {
        lastHidden = item
        lastHiddenTab = activeTab
        // Remove synchronously before queued persistence so the card vanishes in <1ms.
        applyOptimisticRemoval(item, activeTab, markHidden = true)
        hideUndoOperations.enqueue {
            // Taste Engine: скрытие = сильный негативный сигнал (undo удаляет строку лога).
            TasteSignalRecorder.record(
                repository = repository,
                item = item,
                signalType = DiscoverySignalType.HIDE,
                altKeyEnrichmentScope = screenModelScope,
            )
        }
    }

    fun undoHide() {
        val item = lastHidden ?: return
        val tab = lastHiddenTab
        lastHidden = null
        optimisticExcludedTitles -= (item.mediaType to item.cleanTitle)
        hideUndoOperations.enqueue {
            repository.undoHide(item.mediaType, item.cleanTitle)
            if (!preferences.discoveryEnabled().get() || state.value.mediaType != item.mediaType) return@enqueue
            val snapshot = eu.kanade.tachiyomi.data.discovery.runCatchingUnlessCancelled {
                repository.publication(item.mediaType)
            }.getOrNull()?.takeIf { it.generation > 0L || it.suggestions.isNotEmpty() }
                ?: repository.observePublication(item.mediaType).first()
            val blacklist = repository.getBlacklistedTags(item.mediaType)
            val policy =
                DiscoveryCandidatePolicy(
                    spec = preferences.genreFilterSpec(item.mediaType),
                    blacklistedTags = blacklist,
                )
            val projected = projectCandidates(
                snapshot.eligibleSuggestions,
                policy,
                sourcePolicies.current(item.mediaType),
            )
            val tabPool = if (tab == FeedSignalTab.MIX) projected else projected.filter { it.rowType == tab.rowType() }
            val freshItem = tabPool.firstOrNull { it.cleanTitle == item.cleanTitle } ?: return@enqueue
            mutableState.update { current ->
                if (current.mediaType != item.mediaType) return@update current
                val window = current.activeWindows[tab]
                val validWindow = window?.let { reconcileFeedWindow(it, tabPool, tabPool.size) }
                val updatedWindow = if (validWindow != null && validWindow.none {
                        it.cleanTitle == freshItem.cleanTitle
                    }
                ) {
                    (listOf(freshItem) + validWindow).take(tabPool.size.coerceAtLeast(1))
                } else {
                    validWindow
                }
                current.copy(
                    activeWindows = if (updatedWindow !=
                        null
                    ) {
                        current.activeWindows + (tab to updatedWindow)
                    } else {
                        current.activeWindows
                    },
                )
            }
        }
        mutableState.update { it.copy(hiddenSnackbarTitle = null) }
    }

    fun dismissHiddenSnackbar() = mutableState.update { it.copy(hiddenSnackbarTitle = null) }

    /** B2: теговый блэклист — мгновенно фильтрует TASTE-ряд (read-time) и будущие генерации. */
    fun blacklistTag(tag: String) {
        val impacted = countBlacklistImpact(state.value.rows.values.flatten(), tag)
        lastBlacklistedTag = tag
        mutableState.update { it.copy(tagSnackbar = tag to impacted) }
        screenModelScope.launchIO {
            repository.blacklistTag(state.value.mediaType, tag)
        }
    }

    fun undoBlacklistTag() {
        val tag = lastBlacklistedTag ?: return
        mutableState.update { it.copy(tagSnackbar = null) }
        screenModelScope.launchIO {
            repository.unblacklistTag(state.value.mediaType, tag)
        }
    }

    fun dismissTagSnackbar() = mutableState.update { it.copy(tagSnackbar = null) }

    fun addToLibrary(item: DiscoverySuggestion) {
        if (item.title in state.value.addingTitles) return
        mutableState.update { it.copy(addingTitles = it.addingTitles + item.title) }
        screenModelScope.launchIO {
            val ok = try {
                adder.add(item)
            } finally {
                mutableState.update { it.copy(addingTitles = it.addingTitles - item.title) }
            }
            if (ok) {
                // Taste Engine: добавление в библиотеку = сильнейший позитивный сигнал.
                TasteSignalRecorder.record(repository, item, DiscoverySignalType.ADD)
            }
            mutableState.update {
                if (ok) {
                    it.copy(addedSnackbarTitle = item.title, searchFallbackItem = null)
                } else {
                    it.copy(searchFallbackItem = item)
                }
            }
        }
    }

    /** Taste Engine: клик по карточке — слабый позитивный сигнал (guard повторного тапа уже есть). */
    fun recordClick(item: DiscoverySuggestion) {
        screenModelScope.launchIO {
            TasteSignalRecorder.record(repository, item, DiscoverySignalType.CLICK)
        }
    }

    /** Taste Engine: «Больше такого» — сильный позитивный сигнал + тост-подтверждение. */
    fun recordLike(item: DiscoverySuggestion) {
        screenModelScope.launchIO {
            TasteSignalRecorder.record(repository, item, DiscoverySignalType.LIKE)
        }
        context.toast(
            context.contextStringResource(AYMR.strings.for_you_more_like_this_toast),
        )
    }

    /** Taste Engine: «Просмотрено» — мгновенное удаление карточки + фоновое исключение. */
    fun recordConsumed(item: DiscoverySuggestion, activeTab: FeedSignalTab = FeedSignalTab.MIX) {
        applyOptimisticRemoval(item, activeTab, markHidden = false)
        screenModelScope.launchIO {
            TasteSignalRecorder.record(
                repository = repository,
                item = item,
                signalType = DiscoverySignalType.CONSUMED,
                altKeyEnrichmentScope = screenModelScope,
            )
        }
        context.toast(
            context.contextStringResource(AYMR.strings.for_you_mark_consumed_toast),
        )
    }

    fun dismissSearchFallback() = mutableState.update { it.copy(searchFallbackItem = null) }

    fun dismissAddedSnackbar() = mutableState.update { it.copy(addedSnackbarTitle = null) }

    /** Отметить карточку как резолвящуюся для прямого открытия (guard повторного тапа). */
    fun setOpenPending(item: DiscoverySuggestion?) = mutableState.update { it.copy(openingItem = item) }

    /**
     * Прямое открытие plugin-bound карточки: materialize тайтла в локальной БД
     * по связке (sourceId, sourceUrl). Null при неполной привязке, неустановленном
     * (или стаб) источнике и при любой ошибке — вызывающий экран уходит в шторку.
     */
    suspend fun resolveEntryId(item: DiscoverySuggestion): Long? =
        directOpenResolver.resolveEntryId(item)

    private val directOpenResolver = DiscoveryDirectOpenResolver(
        networkToLocalManga = networkToLocalManga,
        networkToLocalAnime = networkToLocalAnime,
        networkToLocalNovel = networkToLocalNovel,
        sourcePolicyProvider = sourcePolicies,
    )
}
