package eu.kanade.tachiyomi.ui.discovery

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.CompositeTrendingSource
import eu.kanade.tachiyomi.data.discovery.DiscoveryLibraryAdder
import eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem
import eu.kanade.tachiyomi.data.discovery.DiscoveryTrendingSource
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.META_PREFETCH_COUNT
import eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder
import eu.kanade.tachiyomi.data.discovery.dedupeCrossRow
import eu.kanade.tachiyomi.data.discovery.expandGenreSet
import eu.kanade.tachiyomi.data.discovery.interleaveMix
import eu.kanade.tachiyomi.data.discovery.isBlacklisted
import eu.kanade.tachiyomi.data.discovery.recoverMissingCovers
import eu.kanade.tachiyomi.data.discovery.rrfScores
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID
import tachiyomi.core.common.i18n.stringResource as contextStringResource

internal data class DiscoveryFeedUiState(
    val mediaType: DiscoveryMediaType = DiscoveryMediaType.ANIME,
    val rows: Map<DiscoveryRowType, List<DiscoverySuggestion>> = emptyMap(),
    val mix: List<DiscoverySuggestion> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val lastUpdatedAt: Long? = null,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = true,
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
    val expanded = expandGenreSet(listOf(tag))
    return items.count { isBlacklisted(it, expanded) }
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

internal fun manualRefreshFlow(workManager: WorkManager, mediaType: DiscoveryMediaType): Flow<Boolean> =
    workManager.isRunningOrEnqueuedFlow(DiscoveryUpdateJob.manualWorkTag(mediaType))

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

internal fun DiscoverySuggestion.toSuggestionItem(): SuggestionItem = SuggestionItem(
    title = title,
    searchQueries = listOf(title),
    thumbnailUrl = coverUrl,
    providerName = provider,
    providerUrl = "",
    providerId = null,
    mediaType = when (mediaType) {
        DiscoveryMediaType.ANIME -> SuggestionMediaType.ANIME
        DiscoveryMediaType.MANGA -> SuggestionMediaType.MANGA
        DiscoveryMediaType.NOVEL -> SuggestionMediaType.NOVEL
    },
    reason = when (provider.lowercase()) {
        "anilist" -> SuggestionReason.EXTERNAL_ANILIST
        "myanimelist", "mal" -> SuggestionReason.EXTERNAL_MAL
        "mangaupdates" -> SuggestionReason.EXTERNAL_MU
        "novelupdates" -> SuggestionReason.EXTERNAL_NU
        "shikimori" -> SuggestionReason.EXTERNAL_SHIKIMORI
        else -> SuggestionReason.SEARCH_TITLE
    },
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
    private val adder: DiscoveryLibraryAdder = DiscoveryLibraryAdder(),
    private val preferences: DiscoveryPreferences = Injekt.get(),
    private val trendingSource: DiscoveryTrendingSource = CompositeTrendingSource(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
) : StateScreenModel<DiscoveryFeedUiState>(DiscoveryFeedUiState(mediaType = initialMedia)) {

    private var observeJob: Job? = null
    private var progressJob: Job? = null
    private var failureJob: Job? = null
    private var prefetchJob: Job? = null
    private var lastHidden: DiscoverySuggestion? = null
    private var lastHiddenTab: FeedSignalTab = FeedSignalTab.MIX
    private var lastBlacklistedTag: String? = null

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
        screenModelScope.launchIO {
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
            combine(
                repository.subscribe(mediaType),
                refreshingFlow(),
                // «Просмотрено/прочитано»: мгновенно уходит из ленты (в т.ч. инкогнито).
                combine(
                    repository.subscribeHidden(mediaType),
                    repository.subscribeConsumed(mediaType),
                ) { hidden, consumed -> hidden to consumed },
                preferences.lastFailedRows(mediaType).changes(),
                repository.subscribeBlacklist(mediaType),
            ) { all, refreshing, hiddenAndConsumed, failedCsv, blacklist ->
                FeedSources(all, refreshing, hiddenAndConsumed.first, failedCsv, blacklist, hiddenAndConsumed.second)
            }
                .collectLatest { (all, refreshing, hidden, failedCsv, blacklist, consumed) ->
                    // Кросс-рядовой дедуп: упавший ряд живёт старым кэшем и может
                    // содержать тайтлы свежих рядов — приоритет у rowType.ordinal.
                    val expandedBlacklist = expandGenreSet(blacklist.toList())
                    val visible = dedupeCrossRow(
                        all.filterNot {
                            it.cleanTitle in hidden || it.cleanTitle in consumed ||
                                isBlacklisted(it, expandedBlacklist)
                        },
                    )
                    val rows = groupFeedRows(visible)
                    val rowItems = rows.mapValues { (_, items) -> items.map { it.toRowItem() } }
                    // RRF для fill-фазы: сырые скоры сигналов несопоставимы (LIKE 0..100 vs TREND 0.0).
                    val mix = interleaveMix(rowItems, rrf = rrfScores(rowItems))
                        .mapNotNull { row -> visible.firstOrNull { it.cleanTitle == row.cleanTitle } }
                    mutableState.update { current ->
                        val currentWindows = current.activeWindows
                        val updatedWindows = if (currentWindows.isEmpty() ||
                            currentWindows[FeedSignalTab.MIX].isNullOrEmpty()
                        ) {
                            if (mix.isNotEmpty()) {
                                val initialMixWindow = sliceFeedWindow(mix, 0, FEED_WINDOW_SIZE)
                                screenModelScope.launchIO {
                                    repository.markShown(mediaType, initialMixWindow.map { it.cleanTitle })
                                }
                                mapOf(FeedSignalTab.MIX to initialMixWindow)
                            } else {
                                emptyMap()
                            }
                        } else {
                            currentWindows.mapValues { (tab, currentWindow) ->
                                val pool = if (tab == FeedSignalTab.MIX) mix else rows[tab.rowType()].orEmpty()
                                val valid = currentWindow.filter { w -> pool.any { it.cleanTitle == w.cleanTitle } }
                                if (valid.isEmpty() && pool.isNotEmpty()) {
                                    val offset = current.tabOffsets[tab] ?: 0
                                    val window = sliceFeedWindow(pool, offset, FEED_WINDOW_SIZE)
                                    screenModelScope.launchIO {
                                        repository.markShown(mediaType, window.map { it.cleanTitle })
                                    }
                                    window
                                } else {
                                    valid
                                }
                            }
                        }
                        current.copy(
                            rows = rows,
                            mix = mix,
                            hidden = hidden,
                            lastUpdatedAt = all.maxOfOrNull { s -> s.createdAt },
                            isRefreshing = refreshing,
                            isLoading = false,
                            failedRows = parseFailedRows(failedCsv),
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

    private data class FeedSources(
        val all: List<DiscoverySuggestion>,
        val refreshing: Boolean,
        val hidden: Set<String>,
        val failedCsv: String,
        val blacklist: Set<String>,
        val consumed: Set<String>,
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

    private fun refreshingFlow(): Flow<Boolean> =
        manualRefreshFlow(context.workManager, state.value.mediaType)

    fun refreshNow(): Long {
        val now = System.currentTimeMillis()
        val media = state.value.mediaType
        val remaining = remainingCooldownSeconds(
            preferences.manualRefreshAt(media).get().takeIf { it > 0L },
            now,
        )
        when (resolveManualRefreshAction(state.value.isRefreshing, remaining)) {
            ManualRefreshAction.CANCEL -> cancelRefresh()
            ManualRefreshAction.ROTATE -> return remaining
            ManualRefreshAction.REFRESH -> {
                if (!preferences.tryStartManualRefresh(media, now)) {
                    return remainingCooldownSeconds(preferences.manualRefreshAt(media).get(), now)
                }
                mutableState.update { it.copy(isRefreshing = true) }
                DiscoveryUpdateJob.refreshNow(context, media)
            }
        }
        return 0L
    }

    fun displayedItems(tab: FeedSignalTab, provider: String?): List<DiscoverySuggestion> {
        val window = state.value.activeWindows[tab] ?: run {
            val pool = itemsForTab(state.value, tab)
            val offset = state.value.tabOffsets[tab] ?: 0
            sliceFeedWindow(pool, offset, FEED_WINDOW_SIZE)
        }
        return filterByProvider(window, provider)
    }

    fun onTabSelected(tab: FeedSignalTab) {
        val currentState = state.value
        if (currentState.activeWindows[tab] == null) {
            val pool = itemsForTab(currentState, tab)
            val offset = currentState.tabOffsets[tab] ?: 0
            val window = sliceFeedWindow(pool, offset, FEED_WINDOW_SIZE)
            mutableState.update { it.copy(activeWindows = it.activeWindows + (tab to window)) }
            if (window.isNotEmpty()) {
                screenModelScope.launchIO {
                    repository.markShown(currentState.mediaType, window.map { it.cleanTitle })
                }
            }
        }
    }

    fun rotateOrRefreshFeed(currentTab: FeedSignalTab): Long {
        val currentState = state.value
        val pool = itemsForTab(currentState, currentTab)
        val currentOffset = currentState.tabOffsets[currentTab] ?: 0
        val now = System.currentTimeMillis()
        val media = currentState.mediaType
        val remaining = remainingCooldownSeconds(
            preferences.manualRefreshAt(media).get().takeIf { it > 0L },
            now,
        )
        val action = resolveManualRefreshAction(currentState.isRefreshing, remaining)
        if (action == ManualRefreshAction.CANCEL) {
            cancelRefresh()
            return 0L
        }
        val canNetworkRefresh = action == ManualRefreshAction.REFRESH
        val (nextOffset, shouldNetworkRefresh) = resolveNextFeedOffset(
            currentOffset = currentOffset,
            poolSize = pool.size,
            windowSize = FEED_WINDOW_SIZE,
            canNetworkRefresh = canNetworkRefresh,
        )

        if (shouldNetworkRefresh) {
            mutableState.update { it.copy(tabOffsets = emptyMap(), activeWindows = emptyMap()) }
            return refreshNow()
        }

        if (pool.size <= FEED_WINDOW_SIZE && remaining > 0L) {
            return remaining
        }

        val newWindow = sliceFeedWindow(pool, nextOffset, FEED_WINDOW_SIZE)
        mutableState.update {
            it.copy(
                tabOffsets = it.tabOffsets + (currentTab to nextOffset),
                activeWindows = it.activeWindows + (currentTab to newWindow),
            )
        }
        if (newWindow.isNotEmpty()) {
            screenModelScope.launchIO {
                repository.markShown(currentState.mediaType, newWindow.map { it.cleanTitle })
            }
        }
        return 0L
    }

    private suspend fun backfillRemovedItem(removed: DiscoverySuggestion, tab: FeedSignalTab) {
        val currentState = state.value
        val pool = itemsForTab(currentState, tab)
        val currentOffset = currentState.tabOffsets[tab] ?: 0
        val currentWindow = currentState.activeWindows[tab]
            ?: sliceFeedWindow(pool, currentOffset, FEED_WINDOW_SIZE)
        val currentWindowTitles = currentWindow.map { it.cleanTitle }.toSet()
        val shownTitles = repository.getShownTitles(currentState.mediaType)

        val candidate = selectBackfillCandidate(
            removedItem = removed,
            pool = pool,
            currentWindowTitles = currentWindowTitles,
            shownTitles = shownTitles,
        )
        val patchedWindow = patchFeedWindowAfterRemoval(
            currentWindow = currentWindow,
            removedTitle = removed.cleanTitle,
            replacement = candidate,
        )
        mutableState.update { current ->
            current.copy(
                activeWindows = current.activeWindows + (tab to patchedWindow),
            )
        }
        if (candidate != null) {
            repository.markShown(currentState.mediaType, listOf(candidate.cleanTitle))
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
        mutableState.update { it.copy(hiddenSnackbarTitle = item.title) }
        screenModelScope.launchIO {
            backfillRemovedItem(item, activeTab)
            // Taste Engine: скрытие = сильный негативный сигнал (undo удаляет строку лога).
            TasteSignalRecorder.record(repository, item, DiscoverySignalType.HIDE)
            repository.hide(item.mediaType, item.cleanTitle)
        }
    }

    fun undoHide() {
        val item = lastHidden ?: return
        val tab = lastHiddenTab
        lastHidden = null
        mutableState.update { current ->
            val window = current.activeWindows[tab]
            val updatedWindow = if (window != null && window.none { it.cleanTitle == item.cleanTitle }) {
                listOf(item) + window.dropLast(1)
            } else {
                window
            }
            current.copy(
                hiddenSnackbarTitle = null,
                activeWindows = if (updatedWindow !=
                    null
                ) {
                    current.activeWindows + (tab to updatedWindow)
                } else {
                    current.activeWindows
                },
            )
        }
        screenModelScope.launchIO {
            repository.unhide(item.mediaType, item.cleanTitle)
            // Taste Engine: undo отменяет и сигнал (профиль возвращается к «до скрытия»).
            repository.removeSignal(item.mediaType, item.cleanTitle)
        }
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
                adder.addFromProvider(state.value.mediaType, item.title, item.provider)
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

    /** Taste Engine: «Просмотрено» — нейтральное исключение (вкус не трогает) + тост. */
    fun recordConsumed(item: DiscoverySuggestion, activeTab: FeedSignalTab = FeedSignalTab.MIX) {
        screenModelScope.launchIO {
            backfillRemovedItem(item, activeTab)
            TasteSignalRecorder.record(repository, item, DiscoverySignalType.CONSUMED)
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
    )
}
