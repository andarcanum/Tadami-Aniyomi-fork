package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryExternalProvider
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublicationOutcome
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoveryQueryEvidence
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRetirementPolicy
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySourceCursor
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.DiscoveryTopUpReceipt
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface DiscoverySeedSources {
    suspend fun candidates(mediaType: DiscoveryMediaType): List<DiscoverySeedInput>
    suspend fun historyCleanTitles(mediaType: DiscoveryMediaType): Set<String>
}

/**
 * Плагин (расширение) как единица участия в подборках: один ключ (pkgName / id novel-плагина)
 * объединяет все языковые варианты источников расширения — без дублей в пикере и в рядах ленты.
 * [langs] — карта sourceId → ISO-код языка варианта; заполняется провайдером,
 * пустая карта = легаси-провайдер (язык неизвестен, прежняя семантика выбора).
 */
data class DiscoveryInstalledPlugin(
    val key: String,
    val sourceIds: List<Long>,
    val langs: Map<Long, String> = emptyMap(),
)

/**
 * Представительный источник плагина с языковым преференсом.
 *
 * Порядок: (1) варианты на языке приложения или английском имеют абсолютный приоритет —
 * это фикс «случайного языка» мультиязычного плагина при пустой библиотеке (вес 0 у всех
 * вариантов → прежний тай-брейк min(id) выбирал произвольный язык); (2) внутри группы —
 * max вес библиотеки (реально читаемый язык важнее); (3) при равенстве — lang-приоритет
 * (appLang > en); (4) тай-брейк — min id (стабильность). Пустая карта [langs] = легаси:
 * только вес → min id.
 */
internal fun selectPluginRepresentative(
    memberIds: Set<Long>,
    weights: Map<Long, Int>,
    langs: Map<Long, String>,
    appLang: String,
): Long? {
    if (memberIds.isEmpty()) return null
    val langOf: (Long) -> String? = { langs[it]?.lowercase() }
    val langPriority: (Long) -> Int = { id ->
        when (langOf(id)) {
            appLang -> 0
            "en" -> 1
            null -> 2
            else -> 3
        }
    }
    val withReadHistory = memberIds.filter { (weights[it] ?: 0) > 0 }
    val candidates = withReadHistory.ifEmpty {
        val preferred = memberIds.filter { langPriority(it) in 0..1 }
        preferred.ifEmpty { memberIds.toList() }
    }
    return candidates.sortedWith(
        compareByDescending<Long> { weights[it] ?: 0 }
            .thenBy(langPriority)
            .thenBy { it },
    ).firstOrNull()
}

/** Reserve for persistence and teardown when splitting the worker deadline. */
private const val WORKER_BUDGET_MARGIN_MS = 10_000L

/** Even with many media types, one run must keep enough time to be useful. */
private const val MIN_MEDIA_BUDGET_MS = 20_000L

/** Startup must not consume the whole media deadline when external rows can still be useful. */
private const val SOURCE_INVENTORY_WAIT_MS = 5_000L

/**
 * Оркестратор фонового обновления ленты «Для тебя»:
 * сиды → строители рядов → [DiscoveryCoordinator] → персист успешных рядов.
 * Провалившийся ряд НЕ затирает свой кэш (в [DiscoveryFeed.rows] его нет).
 */
class DiscoveryRunner(
    private val repository: DiscoveryRepository,
    private val preferences: DiscoveryPreferences,
    private val seedSources: DiscoverySeedSources,
    private val coordinatorFactory: (
        List<DiscoveryRowBuilder>,
    ) -> DiscoveryCoordinator = { DiscoveryCoordinator(it, rowLimit = 50) },
    private val seedSelector: DiscoverySeedSelector = DiscoverySeedSelector(),
    private val trendingFactory: () -> DiscoveryTrendingSource = { CompositeTrendingSource() },
    private val sourcePreferencesProvider: () -> SourcePreferences = { Injekt.get() },
    private val suggestionCoordinatorFactory: () -> SuggestionCoordinator = {
        SuggestionCoordinator(sourcePreferencesProvider())
    },
    private val sourceCatalog: DiscoverySourceCatalog = AppDiscoverySourceCatalog(),
    /** Production injects the live module; null is reserved for explicit legacy inventory fixtures. */
    private val sourcePolicyProvider: DiscoverySourcePolicyProvider? = null,
    /** Сигнал о провале рядов для UI-баннера «показан кэш»; дефолт персистит CSV в prefs. */
    private val failedRowsSink: suspend (DiscoveryMediaType, Set<DiscoveryRowType>) -> Unit = { media, rows ->
        preferences.lastFailedRows(media).set(rows.joinToString(",") { it.key })
    },
    private val fallbackSourceIdProvider: (DiscoveryMediaType) -> Long = { mediaType ->
        runCatching {
            when (mediaType) {
                DiscoveryMediaType.ANIME -> Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>()
                    .getOnlineSources().firstOrNull()?.id ?: -1L
                DiscoveryMediaType.MANGA -> Injekt.get<tachiyomi.domain.source.manga.service.MangaSourceManager>()
                    .getOnlineSources().firstOrNull()?.id ?: -1L
                DiscoveryMediaType.NOVEL -> Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>()
                    .getOnlineSources().firstOrNull()?.id ?: -1L
            }
        }.getOrDefault(-1L)
    },
    /** Установленные онлайн-источники медиатипа — пул ручного режима участия плагинов. */
    private val installedPluginsProvider: suspend (
        DiscoveryMediaType,
    ) -> List<DiscoveryInstalledPlugin> = { mediaType ->
        runCatching {
            when (mediaType) {
                DiscoveryMediaType.ANIME -> {
                    val catalogueIds = Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>()
                        .getCatalogueSources().mapTo(HashSet()) { it.id }
                    Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>()
                        .installedExtensionsFlow.first().map { ext ->
                            val ids = ext.sources.map { it.id }.filter { it in catalogueIds }
                            DiscoveryInstalledPlugin(
                                key = ext.pkgName,
                                sourceIds = ids,
                                langs = ext.sources.filter { it.id in catalogueIds }.associate { it.id to it.lang },
                            )
                        }.filter { it.sourceIds.isNotEmpty() }
                }
                DiscoveryMediaType.MANGA -> {
                    val catalogueIds = Injekt.get<tachiyomi.domain.source.manga.service.MangaSourceManager>()
                        .getCatalogueSources().mapTo(HashSet()) { it.id }
                    Injekt.get<eu.kanade.tachiyomi.extension.manga.MangaExtensionManager>()
                        .installedExtensionsFlow.first().map { ext ->
                            val ids = ext.sources.map { it.id }.filter { it in catalogueIds }
                            DiscoveryInstalledPlugin(
                                key = ext.pkgName,
                                sourceIds = ids,
                                langs = ext.sources.filter { it.id in catalogueIds }.associate { it.id to it.lang },
                            )
                        }.filter { it.sourceIds.isNotEmpty() }
                }
                DiscoveryMediaType.NOVEL -> {
                    val novelSourceManager = Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>()
                    val catalogueIds = novelSourceManager.getCatalogueSources().mapTo(HashSet()) { it.id }
                    val manager = Injekt.get<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager>()
                    // Только источники с плагином: OmniSource (−42), локальный (0) и сироты не участвуют.
                    groupSourcesByPlugin(catalogueIds) { manager.getPluginId(it) }
                        .map { (key, ids) ->
                            val langs = ids.mapNotNull { id ->
                                runCatching { id to novelSourceManager.getOrStub(id).lang }.getOrNull()
                            }.toMap()
                            DiscoveryInstalledPlugin(key, ids, langs)
                        }
                }
            }
        }.getOrDefault(emptyList())
    },
) {

    suspend fun run(
        mediaTypes: List<DiscoveryMediaType> = DiscoveryMediaType.entries,
        isManualRefresh: Boolean = false,
        totalBudgetMs: Long? = null,
        isConfigurationRefresh: Boolean = false,
        topUp: Boolean = false,
    ): Map<DiscoveryMediaType, DiscoveryRunOutcome> {
        if (!preferences.discoveryEnabled().get()) {
            return mediaTypes.associateWith { mediaType ->
                DiscoveryRunOutcome(
                    mediaType = mediaType,
                    status = DiscoveryRunStatus.SKIPPED,
                )
            }
        }
        // Split the caller's deadline across media types instead of letting each
        // type spend the full worker budget on its own.
        val perMediaBudgetMs = totalBudgetMs?.let { total ->
            ((total - WORKER_BUDGET_MARGIN_MS).coerceAtLeast(MIN_MEDIA_BUDGET_MS)) /
                mediaTypes.size.coerceAtLeast(1)
        }
        val outcomes = mutableMapOf<DiscoveryMediaType, DiscoveryRunOutcome>()
        for (mediaType in mediaTypes) {
            try {
                val outcome = runFor(
                    mediaType,
                    isManualRefresh = isManualRefresh && !isConfigurationRefresh,
                    mediaBudgetMs = perMediaBudgetMs,
                    isConfigurationRefresh = isConfigurationRefresh,
                    topUp = topUp,
                )
                outcomes[mediaType] = outcome
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat { "[DiscoveryRunner] $mediaType FAILED: ${e.message}" }
                preferences.lastGenerationStatus(mediaType).set(DiscoveryRunStatus.FAILED.name)
                preferences.lastGenerationAt(mediaType).set(System.currentTimeMillis())
                outcomes[mediaType] = DiscoveryRunOutcome(
                    mediaType = mediaType,
                    status = DiscoveryRunStatus.FAILED,
                    cause = e,
                )
            }
        }
        return outcomes
    }

    private val mediaRunLocks: java.util.concurrent.ConcurrentHashMap<DiscoveryMediaType, Mutex> =
        java.util.concurrent.ConcurrentHashMap()

    private suspend fun trackTitles(mediaType: DiscoveryMediaType, entryId: Long): List<String> =
        when (mediaType) {
            DiscoveryMediaType.ANIME ->
                Injekt.get<tachiyomi.domain.track.anime.repository.AnimeTrackRepository>()
                    .getTracksByAnimeId(entryId).map { it.title }
            DiscoveryMediaType.MANGA ->
                Injekt.get<tachiyomi.domain.track.manga.repository.MangaTrackRepository>()
                    .getTracksByMangaId(entryId).map { it.title }
            DiscoveryMediaType.NOVEL ->
                Injekt.get<tachiyomi.domain.track.novel.repository.NovelTrackRepository>()
                    .getTracksByNovelId(entryId).map { it.title }
        }

    private suspend fun runFor(
        mediaType: DiscoveryMediaType,
        isManualRefresh: Boolean = false,
        mediaBudgetMs: Long? = null,
        isConfigurationRefresh: Boolean = false,
        topUp: Boolean = false,
    ): DiscoveryRunOutcome =
        mediaRunLocks.getOrPut(mediaType) { Mutex() }
            .withLock {
                try {
                    runForLocked(mediaType, isManualRefresh, mediaBudgetMs, isConfigurationRefresh, topUp)
                } finally {
                    DiscoveryProgressTracker.reset(mediaType)
                }
            }

    private suspend fun runForLocked(
        mediaType: DiscoveryMediaType,
        isManualRefresh: Boolean = false,
        mediaBudgetMs: Long? = null,
        isConfigurationRefresh: Boolean = false,
        topUp: Boolean = false,
        allowColdInventoryRetry: Boolean = true,
    ): DiscoveryRunOutcome {
        val mediaStartedAt = System.nanoTime() / 1_000_000L
        val totalMediaBudgetMs = mediaBudgetMs ?: DiscoveryVerificationBudget.DEFAULT_MEDIA_BUDGET_MS
        fun remainingMediaMs(): Long =
            (totalMediaBudgetMs - (System.nanoTime() / 1_000_000L - mediaStartedAt)).coerceAtLeast(0L)
        DiscoveryProgressTracker.update(
            isRunning = true,
            mediaType = mediaType,
            stageText = "Подготовка рекомендаций...",
            current = 0,
            total = 4,
        )
        val candidates = seedSources.candidates(mediaType)
        val candidateRefreshCount = if (isManualRefresh) {
            preferences.manualRefreshCount(mediaType).get() + 1
        } else {
            preferences.manualRefreshCount(mediaType).get()
        }
        val candidateBackgroundCycle = if (isManualRefresh || isConfigurationRefresh) {
            preferences.backgroundCycleCount(mediaType).get()
        } else {
            preferences.backgroundCycleCount(mediaType).get() + 1
        }
        val seedOffset = if (isManualRefresh) manualSeedOffset(candidateRefreshCount) else 0
        val totalSeedBudget = preferences.seedCount().get().coerceIn(1, 5)
        // Taste Learning Engine: один запрос сигнал-лога на генерацию — и на
        // feedback-сиды, и на source-аффинити участия плагинов, и на fold/merge вкуса,
        // и на «просмотрено»-исключение ниже.
        val allSignals = runCatching { repository.getSignals(mediaType) }.getOrDefault(emptyList())
        val previousPool = runCatching { repository.publication(mediaType) }.getOrNull()
        val previousPoolTitles = previousPool?.suggestions.orEmpty().mapTo(HashSet()) { it.cleanTitle }
        val expectedGeneration = previousPool?.generation ?: 0L
        val need = if (topUp) topUpNeed(previousPool?.eligibleSuggestions.orEmpty().size) else 0
        if (topUp) {
            val now = System.currentTimeMillis()
            val lastTopUp = preferences.lastTopUpAt(mediaType).get()
            val lastAdded = preferences.lastTopUpAdded(mediaType).get()
            if (need == 0 || !shouldAttemptTopUp(now, lastTopUp, lastAdded)) {
                logcat { "[DiscoveryTopUp] skipped need=$need lastTopUp=$lastTopUp lastAdded=$lastAdded" }
                preferences.lastGenerationStatus(mediaType).set(DiscoveryRunStatus.SKIPPED.name)
                return DiscoveryRunOutcome(
                    mediaType = mediaType,
                    status = DiscoveryRunStatus.SKIPPED,
                    attemptedRows = emptySet(),
                )
            }
        }
        val eligibilitySnapshot = repository.eligibility(mediaType)
        val harvestPages = if (isManualRefresh) MAX_MANUAL_HARVEST_PAGES else 1
        val feedbackSeeds = selectFeedbackSeeds(allSignals, mediaType, limit = minOf(2, totalSeedBudget))
        val librarySeedBudget = (totalSeedBudget - feedbackSeeds.size).coerceAtLeast(0)
        val feedbackCleanTitles = feedbackSeeds.mapTo(HashSet()) { it.cleanTitle }
        val libraryCandidates = if (librarySeedBudget > 0) {
            candidates.filterNot { normalizeDiscoveryTitle(it.title) in feedbackCleanTitles }
        } else {
            emptyList()
        }
        val seeds = if (librarySeedBudget > 0) {
            seedSelector.select(
                libraryCandidates,
                SeedSettings(
                    maxSeeds = librarySeedBudget,
                    useCompleted = preferences.seedCompleted().get(),
                    useActive14 = preferences.seedActive14().get(),
                    useAdded = preferences.seedAdded().get(),
                    completedWindowDays = preferences.seedCompletedDays().get().toLong(),
                    activeWindowDays = preferences.seedActiveDays().get().toLong(),
                ),
                offset = seedOffset,
            )
        } else {
            emptyList()
        }
        val sourcePreferences = sourcePreferencesProvider()
        val preferredSourceId = when (mediaType) {
            DiscoveryMediaType.ANIME -> sourcePreferences.lastUsedAnimeSource().get()
            DiscoveryMediaType.MANGA -> sourcePreferences.lastUsedMangaSource().get()
            DiscoveryMediaType.NOVEL -> sourcePreferences.lastUsedNovelSource().get()
        }
        val sourceWeights = candidates.filter { it.sourceId > 0 }.groupingBy { it.sourceId }.eachCount()
        val learnedSourceAffinity = foldLearnedTasteProfile(signals = allSignals).sourceAffinity
        val sourceFailureHistory = parseSourceFailureHistory(preferences.sourceFailureHistory(mediaType).get())
        val runnerNow = System.currentTimeMillis()
        val legacyPlugins = if (sourcePolicyProvider == null) {
            installedPluginsProvider(mediaType).map {
                it.copy(sourceIds = it.sourceIds.toList(), langs = it.langs.toMap())
            }
        } else {
            emptyList()
        }
        if (sourcePolicyProvider != null) {
            (sourcePolicyProvider as? DiscoverySourcePolicyResolver)?.updateRetrievalWeights(
                mediaType,
                sourceWeights,
                learnedSourceAffinity,
                capturedAt = runnerNow,
            )
            val beforeReadiness = sourcePolicyProvider.current(mediaType)
            if (beforeReadiness.inventoryComplete) {
                reconcileDiscoverySourcePreferences(preferences, mediaType, beforeReadiness.pluginMembers.keys)
            } else {
                val waitMs = minOf(SOURCE_INVENTORY_WAIT_MS, remainingMediaMs())
                if (waitMs > 0L) {
                    withTimeoutOrNull(waitMs) {
                        runCatchingUnlessCancelled { sourcePolicyProvider.awaitComplete(mediaType) }.getOrNull()
                    }
                }
            }
        } else if (legacyPlugins.isNotEmpty()) {
            // Explicit legacy fixtures assert a complete ownership list; they never authorize by library/fallback id.
            reconcileDiscoverySourcePreferences(preferences, mediaType, legacyPlugins.map { it.key })
        }
        fun currentSourcePolicy(): DiscoverySourcePolicy =
            sourcePolicyProvider?.current(mediaType) ?: legacySourcePolicy(
                mediaType,
                legacyPlugins,
                sourceWeights,
                learnedSourceAffinity,
                sourceFailureHistory,
                runnerNow,
                sourcePreferences,
            )
        var sourcePolicy = currentSourcePolicy()

        val seedsWithTracks = seeds.map { seed ->
            val trackTitles = runCatching { trackTitles(mediaType, seed.entryId) }.getOrDefault(emptyList())
            if (trackTitles.isEmpty()) seed else seed.copy(altTitles = (seed.altTitles + trackTitles).distinct())
        }

        val useExternal = preferences.externalProvidersEnabled().get()
        val disabledLikeProviders = buildSet {
            if (!useExternal) {
                addAll(listOf("AniList", "Shikimori", "MyAnimeList", "MangaUpdates", "NovelUpdates"))
            } else {
                if (!sourcePreferences.suggestionsUseShikimori().get()) add("Shikimori")
                if (!sourcePreferences.suggestionsUseMangaUpdatesNovel().get()) add("MangaUpdates")
                if (!sourcePreferences.suggestionsUseNovelUpdates().get()) add("NovelUpdates")
            }
        }
        // Full generation replaces the published pool atomically. Cache-first
        // merging, per-row writes and stale backfill are no longer part of the
        // refresh path.
        val currentSuggestions = previousPool?.suggestions.orEmpty()
        val existingRowItems = emptyMap<DiscoveryRowType, List<DiscoveryRowItem>>()
        val shownCutoffMap = runCatching {
            repository.getRecentExclusionsWithTimestamp(mediaType)
        }.getOrDefault(emptyMap())
        val shownTitles = shownCutoffMap.keys
        val recentCleanTitles = shownTitles

        // Taste Learning Engine: fold сигнал-лога и слияние с библиотечным профилем.
        // Плавный старт (blend) — при пустом логе merge возвращает библиотечный профиль как есть.
        // Неявный негатив: тайтл показан ≥3 раз за 48ч без явного сигнала → жанры
        // получают мягкий минус. Синтез на лету (не пишется в БД), явные сигналы важнее.
        val libraryTasteProfile = buildTasteProfile(candidates)
        val libraryProfileGenres = libraryTasteProfile.map { it.first }
        val blacklistedTags = repository.getBlacklistedTags(mediaType)
        val blacklistedVetoSet = expandGenreSet(blacklistedTags.toList())
        val implicitNegatives = runCatching {
            synthesizeImplicitNegatives(
                shownWithCount = repository.getShownWithCount(mediaType),
                explicitSignals = allSignals,
                currentSuggestions = currentSuggestions,
                mediaType = mediaType,
                profileGenres = libraryProfileGenres,
            )
        }.getOrDefault(emptyList())
        val learnedProfile = foldLearnedTasteProfile(
            signals = allSignals + implicitNegatives,
            vetoGenres = blacklistedVetoSet,
        )
        val filteredLibraryProfile = libraryTasteProfile.filterNot { it.first.lowercase() in blacklistedVetoSet }
        val mergedTasteProfile = mergeTasteProfiles(
            libraryProfile = filteredLibraryProfile,
            learned = learnedProfile,
        ).filterNot { it.first.lowercase() in blacklistedVetoSet }
        // «Просмотрено/прочитано»: нейтральное исключение — consumed-тайтлы не
        // попадают в новые генерации ленты (вкусовой профиль они не трогают).
        val consumedCleanTitles = allSignals
            .filter { it.signalType.excludesFromFeed }
            .mapTo(HashSet()) { it.cleanTitle }

        val genreFilterSpec = preferences.genreFilterSpec(mediaType)
        sourcePolicy = currentSourcePolicy()
        val initialInventoryComplete = sourcePolicy.inventoryComplete
        val configurationKey = discoveryConfigurationKey(mediaType, sourcePreferences, sourcePolicy.revision)
        val resumeCursor = previousPool?.cursors.orEmpty()
            .firstOrNull { it.queryKey == configurationKey && !it.exhausted }
        val pageOffset = when {
            isConfigurationRefresh || isManualRefresh -> 1
            resumeCursor != null -> resumeCursor.nextPage.coerceAtLeast(1)
            else -> backgroundPageOffset(candidateBackgroundCycle)
        }
        var configurationSuperseded = false
        suspend fun configurationIsCurrent(): Boolean {
            val livePolicy = currentSourcePolicy()
            val liveKey = discoveryConfigurationKey(mediaType, sourcePreferences, livePolicy.revision)
            if (livePolicy.revision == sourcePolicy.revision && liveKey == configurationKey) return true
            // Only a freshly read desired key may supersede this request; never reinstall its captured old key after IO.
            repository.installConfigurationKey(mediaType, liveKey)
            configurationSuperseded = true
            preferences.lastGenerationStatus(mediaType).set(DiscoveryRunStatus.SKIPPED.name)
            preferences.lastGenerationAt(mediaType).set(System.currentTimeMillis())
            return false
        }
        var remainingBudgetMs = remainingMediaMs()
        if (remainingBudgetMs <= 0L) {
            preferences.lastGenerationStatus(mediaType).set(DiscoveryRunStatus.SKIPPED.name)
            return DiscoveryRunOutcome(mediaType, DiscoveryRunStatus.SKIPPED)
        }
        repository.installConfigurationKey(mediaType, configurationKey)
        if (!configurationIsCurrent()) return DiscoveryRunOutcome(mediaType, DiscoveryRunStatus.SKIPPED)
        remainingBudgetMs = remainingMediaMs()
        if (remainingBudgetMs <= 0L) return DiscoveryRunOutcome(mediaType, DiscoveryRunStatus.SKIPPED)
        if (topUp && previousPool?.configurationKey != configurationKey) {
            return runForLocked(
                mediaType,
                isManualRefresh = false,
                mediaBudgetMs = remainingMediaMs(),
                isConfigurationRefresh = true,
                topUp = false,
            )
        }
        val primary = sourcePolicy.primarySourceId(preferredSourceId)
        val primarySourceId = if (primary > 0L) {
            primary
        } else {
            val fallback = if (sourcePolicyProvider == null) fallbackSourceIdProvider(mediaType) else -1L
            fallback.takeIf { sourcePolicy.allowsNative(it) } ?: -1L
        }
        // One budget for the whole media run: every row shares the deadline and
        // the details allowance, so rows cannot multiply the worker cost.
        // Genre-selected mode verifies deep pages of a few rich sources, so it
        // gets a larger allowance; the deadline remains the real guard.
        val verificationBudget = DiscoveryVerificationBudget(
            budgetMs = remainingBudgetMs,
            maxDetails = if (genreFilterSpec.requiredGenres.isNotEmpty()) {
                DiscoveryVerificationBudget.DEFAULT_REQUIRED_MAX_DETAILS
            } else {
                DiscoveryVerificationBudget.DEFAULT_MAX_DETAILS
            },
        )
        val baseContext = DiscoveryBuildContext(
            mediaType = mediaType,
            seeds = seedsWithTracks,
            feedbackSeeds = feedbackSeeds,
            libraryCleanTitles = candidates.mapTo(HashSet()) { normalizeDiscoveryTitle(it.title) },
            historyCleanTitles = seedSources.historyCleanTitles(mediaType),
            hiddenCleanTitles = repository.getHiddenTitles(mediaType) + consumedCleanTitles,
            tasteProfile = mergedTasteProfile,
            blacklistedTags = blacklistedTags,
            ignoredGenres = genreFilterSpec.ignoredGenres,
            // V1: статус-фильтр трендов — CSV из настроек, пусто = без фильтра.
            releaseStatuses = DiscoveryReleaseStatus.parseCsv(preferences.releaseStatusFilter().get()),
            // V3: жанровые фильтры — полная спецификация с режимами строгости и сопоставления.
            priorityGenres = genreFilterSpec.priorityGenres,
            requiredGenres = genreFilterSpec.requiredGenres,
            genreFilterSpec = genreFilterSpec,
            sourceId = primarySourceId,
            sourceIds = sourcePolicy.representativeSourceIds,
            sourceWeights = sourcePolicy.sourceWeights,
            sourcePolicy = sourcePolicy,
            recentCleanTitles = recentCleanTitles,
            shownCutoffMap = shownCutoffMap,
            // Ручной рефреш: тайтлы текущей ленты не возвращаются stale-добором —
            // наполнение ряда реально сменяется, а не «новые + всё старое».
            currentFeedCleanTitles = previousPoolTitles,
            existingRowItems = existingRowItems,
            pageOffset = pageOffset,
            harvestPages = harvestPages,
            verificationBudget = verificationBudget,
            eligibilitySnapshot = eligibilitySnapshot,
        )
        // TASTE and SOURCE otherwise run the same required-genre catalogue pass
        // twice and then the coordinator dedupes one of them away. Share the
        // pass and give the rows disjoint slices instead.
        val requiredGenres = genreFilterSpec.requiredGenres
        val sharedGenreQuery = if (
            requiredGenres.isNotEmpty() &&
            preferences.rowTasteEnabled().get() &&
            preferences.rowSourceEnabled().get()
        ) {
            val ids = sourcePolicy.representativeSourceIds.filter { sourcePolicy.allowsNative(it) }
                .ifEmpty { listOf(primarySourceId).filter { sourcePolicy.allowsNative(it) } }
            if (ids.isEmpty()) {
                null
            } else {
                val rotation = (pageOffset - 1).coerceAtLeast(0) % ids.size
                val ordered = ids.drop(rotation) + ids.take(rotation)
                SharedRequiredGenreQuery {
                    queryDiscoveryGenreSources(
                        catalog = sourceCatalog,
                        media = mediaType,
                        sourceIds = ordered,
                        genres = requiredGenres.toList(),
                        page = pageOffset,
                        pages = harvestPages,
                        releaseStatuses = baseContext.releaseStatuses,
                        policy = baseContext.candidatePolicy,
                        maxConcurrency = 4,
                        timeoutMs = genreSourceTimeoutMs(
                            verificationBudget,
                            ordered.size,
                            maxConcurrency = 4,
                        ),
                        hydrate = true,
                        targetItems = if (topUp) need * 3 / 2 else RICH_SOURCE_TARGET * 2,
                        budget = verificationBudget,
                        excludedKeys = baseContext.exclusionKeys,
                    )
                }
            }
        } else {
            null
        }
        val context = if (sharedGenreQuery != null) {
            baseContext.copy(sharedGenreQuery = sharedGenreQuery)
        } else {
            baseContext
        }
        logcat {
            "[Discovery] refresh media=$mediaType manual=$isManualRefresh " +
                "configuration=$isConfigurationRefresh page=$pageOffset " +
                "statuses=${context.releaseStatuses} sources=${context.sourceIds} primary=${context.sourceId} " +
                "required=${genreFilterSpec.requiredGenres} enforcement=${genreFilterSpec.requiredEnforcement} " +
                "match=${genreFilterSpec.requiredMatchMode}"
        }
        val builders = buildList {
            if (preferences.rowLikeEnabled().get()) {
                add(
                    DiscoveryLikeRowBuilder(
                        suggestionCoordinator = suggestionCoordinatorFactory(),
                        catalog = sourceCatalog,
                        includeExternal = useExternal,
                    ),
                )
            }
            if (preferences.rowTasteEnabled().get()) {
                add(
                    DiscoveryTasteRowBuilder(
                        trending = trendingFactory(),
                        catalog = sourceCatalog,
                        // «Только плагины»: жанровые рекомендации строятся из каталогов,
                        // внешние жанровые провайдеры не опрашиваются.
                        includeExternal = useExternal,
                        sortProvider = {
                            if (preferences.trendSort().get() == "score") TrendSort.SCORE else TrendSort.POPULARITY
                        },
                    ),
                )
            }
            if (preferences.rowTrendEnabled().get() && useExternal) {
                add(
                    DiscoveryTrendRowBuilder(
                        trending = trendingFactory(),
                        catalog = sourceCatalog,
                        seasonProvider = {
                            when (preferences.trendSeason().get()) {
                                "next" -> TrendSeason.NEXT
                                "both" -> TrendSeason.BOTH
                                else -> TrendSeason.CURRENT
                            }
                        },
                        sortProvider = {
                            if (preferences.trendSort().get() == "score") TrendSort.SCORE else TrendSort.POPULARITY
                        },
                    ),
                )
            }
            if (preferences.rowSourceEnabled().get()) {
                add(DiscoverySourceRowBuilder(sourceCatalog, rowCap = DISCOVERY_POOL_TARGET))
            }
        }
        if (builders.isEmpty()) {
            failedRowsSink(mediaType, emptySet())
            preferences.lastGenerationStatus(mediaType).set(DiscoveryRunStatus.SKIPPED.name)
            preferences.lastGenerationAt(mediaType).set(System.currentTimeMillis())
            return DiscoveryRunOutcome(
                mediaType = mediaType,
                status = DiscoveryRunStatus.SKIPPED,
                attemptedRows = emptySet(),
            )
        }

        val attemptedRows = builders.map { it.rowType }.toSet()
        val coordinator = coordinatorFactory(builders)
        val feed = coordinator.streamFeed(context) { _, _ ->
            // The whole pool is published once, after every row has settled.
        }
        val sourceFailures = feed.failedSourceIds.size
        val sourceDegraded = feed.degradedSourceIds.size
        preferences.lastSourceFailureCount(mediaType).set(sourceFailures)
        val successfulSourceIds = feed.successfulSourceIds - feed.failedSourceIds
        val updatedFailureHistory = updateSourceFailures(
            currentHistory = sourceFailureHistory,
            failedIds = feed.failedSourceIds,
            successfulIds = successfulSourceIds,
            nowMs = System.currentTimeMillis(),
        )
        preferences.sourceFailureHistory(mediaType).set(formatSourceFailureHistory(updatedFailureHistory))
        val fillAudit = buildDiscoveryFillAudit(
            mediaType = mediaType,
            rawCandidates = feed.rawCandidates,
            finalRows = feed.rows,
            context = context,
            detailsUsed = verificationBudget.detailsUsed,
            detailsAllowance = verificationBudget.detailsAllowance,
            sourceFailures = sourceFailures,
            sourceDegraded = sourceDegraded,
        )
        logcat { fillAudit.logLine() }
        logcat {
            "[DiscoveryRunner] $mediaType done: rows=${feed.rows.mapValues { it.value.size }} failed=${feed.failedRows}"
        }
        val actualExecutedRows = feed.rows.keys + feed.failedRows
        val allExecutedFailed = actualExecutedRows.isNotEmpty() && feed.failedRows.containsAll(actualExecutedRows)
        val hasPartialFailures = feed.failedRows.isNotEmpty() ||
            feed.degradedRows.isNotEmpty() ||
            (attemptedRows.isNotEmpty() && actualExecutedRows.size < attemptedRows.size)

        val createdAt = System.currentTimeMillis()
        val pooledItems = feed.rows.flatMap { (rowType, items) ->
            items.map { item -> toSuggestion(mediaType, rowType, item, createdAt) }
        }
        val topUpAdditions = interleaveRowsForTopUp(feed.rows, mediaType, createdAt)
        val exhaustionEvidence = feed.exhaustionEvidence.orEmpty()
        val conclusiveEmpty = feed.failedRows.isEmpty() && feed.degradedRows.isEmpty() &&
            feed.rows.values.all { it.isEmpty() } && exhaustionEvidence.isNotEmpty() &&
            exhaustionEvidence.all {
                it.transportOk && it.rawEnd && it.pending == 0 && it.unattempted == 0 &&
                    it.metadataFailures == 0 && !it.budgetStopped && !it.duplicatePageStopped
            }
        val nextCursorPage = when {
            isManualRefresh -> 1
            pageOffset + 1 > MAX_HARVEST_PAGE -> 1
            else -> pageOffset + 1
        }
        val nextCursors = listOf(
            DiscoverySourceCursor(
                sourceId = 0L,
                queryKey = configurationKey,
                nextPage = nextCursorPage,
                exhausted = false,
            ),
        )
        val prepared = when {
            allExecutedFailed -> null
            pooledItems.isNotEmpty() -> DiscoveryPreparedPool.Fresh(
                suggestions = pooledItems,
                partial = hasPartialFailures,
                cursors = nextCursors,
            )
            conclusiveEmpty -> DiscoveryPreparedPool.VerifiedEmpty(
                expectedQueryKeys = exhaustionEvidence.mapTo(HashSet()) { it.queryKey },
                evidence = exhaustionEvidence,
                cursors = nextCursors,
            )
            else -> null
        }

        if (!configurationIsCurrent()) {
            if (allowColdInventoryRetry && !initialInventoryComplete &&
                currentSourcePolicy().inventoryComplete && remainingMediaMs() >= MIN_MEDIA_BUDGET_MS
            ) {
                return runForLocked(
                    mediaType = mediaType,
                    isManualRefresh = isManualRefresh,
                    mediaBudgetMs = remainingMediaMs(),
                    isConfigurationRefresh = isConfigurationRefresh,
                    topUp = topUp,
                    allowColdInventoryRetry = false,
                )
            }
            if (previousPool == null && !initialInventoryComplete) {
                preferences.bootstrapAttemptAt(mediaType).set(0L)
            }
            return DiscoveryRunOutcome(mediaType, DiscoveryRunStatus.SKIPPED, attemptedRows = attemptedRows)
        }
        if (topUp) {
            val topUpReceipt = repository.topUpPool(
                media = mediaType,
                expectedGeneration = expectedGeneration,
                additions = topUpAdditions,
                cursors = nextCursors,
                maxPoolSize = DISCOVERY_POOL_TARGET,
            )
            val now = System.currentTimeMillis()
            when (topUpReceipt) {
                is DiscoveryTopUpReceipt.ToppedUp -> {
                    preferences.lastTopUpAdded(mediaType).set(topUpReceipt.added)
                    preferences.lastTopUpAt(mediaType).set(now)
                    preferences.lastGenerationStatus(mediaType).set(DiscoveryRunStatus.SUCCESS.name)
                    preferences.lastGenerationAt(mediaType).set(now)
                    val eligibleBefore = previousPool?.eligibleSuggestions.orEmpty().size
                    logcat {
                        "[DiscoveryTopUp] media=$mediaType eligibleBefore=$eligibleBefore " +
                            "need=$need added=${topUpReceipt.added} eligibleAfter=${topUpReceipt.eligibleAfter}"
                    }
                    return DiscoveryRunOutcome(
                        mediaType = mediaType,
                        status = DiscoveryRunStatus.SUCCESS,
                        attemptedRows = attemptedRows,
                        failedRows = feed.failedRows,
                        degradedRows = feed.degradedRows,
                        persistedRows = feed.rows.filterValues { it.isNotEmpty() }.keys,
                    )
                }
                is DiscoveryTopUpReceipt.NothingToAdd -> {
                    preferences.lastTopUpAdded(mediaType).set(0)
                    preferences.lastTopUpAt(mediaType).set(now)
                    if (previousPool?.eligibleSuggestions.isNullOrEmpty()) {
                        logcat {
                            "[DiscoveryTopUp] pool empty and top-up had nothing to add, falling back to full generation"
                        }
                        return runForLocked(
                            mediaType = mediaType,
                            isManualRefresh = false,
                            mediaBudgetMs = remainingMediaMs(),
                            isConfigurationRefresh = isConfigurationRefresh,
                            topUp = false,
                        )
                    }
                    preferences.lastGenerationStatus(mediaType).set(DiscoveryRunStatus.SUCCESS.name)
                    preferences.lastGenerationAt(mediaType).set(now)
                    return DiscoveryRunOutcome(
                        mediaType = mediaType,
                        status = DiscoveryRunStatus.SUCCESS,
                        attemptedRows = attemptedRows,
                        failedRows = feed.failedRows,
                        degradedRows = feed.degradedRows,
                        persistedRows = emptySet(),
                    )
                }
                is DiscoveryTopUpReceipt.Superseded,
                is DiscoveryTopUpReceipt.Busy,
                -> {
                    preferences.lastGenerationStatus(mediaType).set(DiscoveryRunStatus.SKIPPED.name)
                    return DiscoveryRunOutcome(
                        mediaType = mediaType,
                        status = DiscoveryRunStatus.SKIPPED,
                        attemptedRows = attemptedRows,
                    )
                }
            }
        }

        val requestId = java.util.UUID.randomUUID().toString()
        val token = if (prepared != null) {
            repository.reserveGeneration(mediaType, requestId, expectedGeneration, configurationKey)
        } else {
            null
        }
        val publishReceipt = if (prepared != null && token != null && configurationIsCurrent()) {
            val retirement = when {
                isConfigurationRefresh -> DiscoveryRetirementPolicy.NONE
                isManualRefresh -> DiscoveryRetirementPolicy.RETIRE_ALL
                else -> DiscoveryRetirementPolicy.SEEN_ONLY
            }
            repository.publishGeneration(token, prepared, retirement)
        } else {
            if (token != null) repository.revokeGeneration(mediaType, requestId)
            null
        }
        val published = publishReceipt as? DiscoveryPublishReceipt.Published

        var keptTopUpReceipt: DiscoveryTopUpReceipt? = null
        if (publishReceipt is DiscoveryPublishReceipt.KeptExisting && configurationIsCurrent()) {
            val additions = if (prepared is DiscoveryPreparedPool.Fresh) topUpAdditions else emptyList()
            val cursors = when (prepared) {
                is DiscoveryPreparedPool.Fresh -> prepared.cursors
                is DiscoveryPreparedPool.VerifiedEmpty -> prepared.cursors
                else -> emptyList()
            }
            val receipt = repository.topUpPool(
                media = mediaType,
                expectedGeneration = expectedGeneration,
                additions = additions,
                cursors = cursors,
                maxPoolSize = DISCOVERY_POOL_TARGET,
            )
            keptTopUpReceipt = receipt
            val now = System.currentTimeMillis()
            when (receipt) {
                is DiscoveryTopUpReceipt.ToppedUp -> {
                    preferences.lastTopUpAdded(mediaType).set(receipt.added)
                    preferences.lastTopUpAt(mediaType).set(now)
                }
                is DiscoveryTopUpReceipt.NothingToAdd -> {
                    preferences.lastTopUpAdded(mediaType).set(0)
                    preferences.lastTopUpAt(mediaType).set(now)
                }
                is DiscoveryTopUpReceipt.Superseded,
                is DiscoveryTopUpReceipt.Busy,
                -> Unit
            }
            logcat {
                "[DiscoveryPublication] kept existing accepted=${publishReceipt.accepted} " +
                    "oldEligible=${publishReceipt.oldEligible} topUp=$keptTopUpReceipt"
            }
        }
        val topUpSkipped = keptTopUpReceipt is DiscoveryTopUpReceipt.Superseded ||
            keptTopUpReceipt is DiscoveryTopUpReceipt.Busy
        val topUpAdded = keptTopUpReceipt is DiscoveryTopUpReceipt.ToppedUp

        val persistedRows = if (published != null || topUpAdded) {
            feed.rows.filterValues { it.isNotEmpty() }.keys
        } else {
            emptySet()
        }

        val status = when {
            configurationSuperseded -> DiscoveryRunStatus.SKIPPED
            publishReceipt is DiscoveryPublishReceipt.Superseded -> DiscoveryRunStatus.SKIPPED
            allExecutedFailed -> DiscoveryRunStatus.FAILED
            publishReceipt is DiscoveryPublishReceipt.KeptExisting ->
                if (topUpSkipped) {
                    DiscoveryRunStatus.SKIPPED
                } else if (hasPartialFailures) {
                    DiscoveryRunStatus.PARTIAL
                } else {
                    DiscoveryRunStatus.SUCCESS
                }
            publishReceipt is DiscoveryPublishReceipt.NoAdmissibleItems -> DiscoveryRunStatus.FAILED
            prepared == null -> if (hasPartialFailures) DiscoveryRunStatus.PARTIAL else DiscoveryRunStatus.FAILED
            token == null -> DiscoveryRunStatus.SKIPPED
            published?.snapshot?.outcome == DiscoveryPublicationOutcome.PARTIAL -> DiscoveryRunStatus.PARTIAL
            hasPartialFailures -> DiscoveryRunStatus.PARTIAL
            else -> DiscoveryRunStatus.SUCCESS
        }

        val poolAdvanced = !configurationSuperseded &&
            (published != null || topUpAdded || publishReceipt is DiscoveryPublishReceipt.KeptExisting)
        if (actualExecutedRows.isNotEmpty() && !allExecutedFailed && poolAdvanced) {
            if (isManualRefresh) {
                preferences.manualRefreshCount(mediaType).set(candidateRefreshCount)
            } else if (!isConfigurationRefresh) {
                preferences.backgroundCycleCount(mediaType).set(candidateBackgroundCycle)
            }
        }
        failedRowsSink(mediaType, feed.failedRows)
        DiscoveryProgressTracker.update(
            isRunning = true,
            mediaType = mediaType,
            stageText = "Готово",
            current = builders.size,
            total = builders.size,
        )

        preferences.lastGenerationStatus(mediaType).set(status.name)
        preferences.lastGenerationAt(mediaType).set(System.currentTimeMillis())
        if (previousPool == null && !initialInventoryComplete &&
            (status == DiscoveryRunStatus.SKIPPED || status == DiscoveryRunStatus.FAILED)
        ) {
            preferences.bootstrapAttemptAt(mediaType).set(0L)
        }

        return DiscoveryRunOutcome(
            mediaType = mediaType,
            status = status,
            attemptedRows = attemptedRows,
            failedRows = feed.failedRows,
            degradedRows = feed.degradedRows,
            persistedRows = persistedRows,
        )
    }
    private fun legacySourcePolicy(
        mediaType: DiscoveryMediaType,
        installed: List<DiscoveryInstalledPlugin>,
        libraryWeights: Map<Long, Int>,
        learnedAffinity: Map<String, Double>,
        history: Map<Long, SourceFailureRecord>,
        now: Long,
        sourcePreferences: SourcePreferences,
    ): DiscoverySourcePolicy = preferences.withSourcePreferencesLock(mediaType) {
        val plugins = installed.filter { it.key.isNotBlank() }.groupBy { it.key }.map { (key, variants) ->
            DiscoveryInstalledPlugin(
                key,
                variants.flatMap { it.sourceIds }.distinct(),
                variants.flatMap { it.langs.entries }.associate { it.key to it.value },
            )
        }
        val mode = preferences.discoverySourceMode(mediaType).get()
        val excluded = parseKeyCsv(preferences.discoverySourceExcluded(mediaType).get())
        val known = parseKeyCsv(preferences.discoverySourceKnown(mediaType).get())
        val members = plugins.associate { it.key to it.sourceIds.toSet() }
        val allowedKeys = members.keys.filterTo(LinkedHashSet()) {
            it !in excluded && (mode != "manual" || known.isEmpty() || it in known)
        }
        val allExternal = if (preferences.externalProvidersEnabled().get()) {
            DiscoveryExternalProvider.entries.toSet()
        } else {
            emptySet()
        }
        val similarExternal = allExternal - buildSet {
            if (!sourcePreferences.suggestionsUseShikimori().get()) add(DiscoveryExternalProvider.SHIKIMORI)
            if (!sourcePreferences.suggestionsUseMangaUpdatesNovel().get()) add(DiscoveryExternalProvider.MANGA_UPDATES)
            if (!sourcePreferences.suggestionsUseNovelUpdates().get()) add(DiscoveryExternalProvider.NOVEL_UPDATES)
        }
        val external = mapOf(
            DiscoveryRowType.LIKE to similarExternal,
            DiscoveryRowType.TASTE to allExternal,
            DiscoveryRowType.TREND to allExternal,
        )
        val revision = if (installed.isEmpty()) {
            ""
        } else {
            buildString {
                fun value(text: String) {
                    append(text.length).append(':').append(text)
                }
                value("legacy-source")
                value(mediaType.key)
                value(mode)
                excluded.sorted().forEach {
                    value("excluded")
                    value(it)
                }
                known.sorted().forEach {
                    value("known")
                    value(it)
                }
                plugins.sortedBy { it.key }.forEach { plugin ->
                    value("plugin")
                    value(plugin.key)
                    plugin.sourceIds.sorted().forEach { id ->
                        value(id.toString())
                        value(plugin.langs[id].orEmpty())
                    }
                }
                external.entries.sortedBy { it.key.key }.forEach { (row, providers) ->
                    value(row.key)
                    providers.sortedBy { it.name }.forEach { value(it.name) }
                }
            }
        }
        val ownership = DiscoverySourcePolicy(
            mediaType,
            revision,
            installed.isNotEmpty(),
            allowedKeys,
            members,
            allowedExternalProviders = external,
        )
        val ranked = plugins.filter { it.key in allowedKeys }.mapNotNull { plugin ->
            val representative = selectPluginRepresentative(
                plugin.sourceIds.filterTo(LinkedHashSet()) { ownership.allowsNative(it) },
                libraryWeights,
                plugin.langs,
                java.util.Locale.getDefault().language.lowercase(),
            ) ?: return@mapNotNull null
            val libraryWeight = if (mode == "manual") 1 else plugin.sourceIds.sumOf { libraryWeights[it] ?: 0 }
            DiscoveryPluginStat(
                plugin.key,
                representative,
                plugin.sourceIds.toSet(),
                libraryWeight,
                learnedAffinity[plugin.key] ?: 0.0,
            )
        }.sortedWith(
            compareByDescending<DiscoveryPluginStat> {
                applySourceAffinity(it.weight, it.affinity, isManualMode = mode == "manual") *
                    calculateSourceReliabilityFactor(it.representative, history, now)
            }.thenBy { it.representative },
        )
        DiscoverySourcePolicy(
            mediaType = mediaType,
            revision = revision,
            inventoryComplete = installed.isNotEmpty(),
            allowedPluginKeys = allowedKeys,
            pluginMembers = members,
            representativeSourceIds = (if (mode == "manual") ranked else ranked.take(3)).map { it.representative },
            sourceWeights = ranked.associate {
                it.representative to (
                    applySourceAffinity(it.weight, it.affinity, isManualMode = mode == "manual") *
                        calculateSourceReliabilityFactor(it.representative, history, now)
                    )
            },
            allowedExternalProviders = external,
        )
    }

    private fun discoveryConfigurationKey(
        mediaType: DiscoveryMediaType,
        sourcePreferences: SourcePreferences,
        sourcePolicyRevision: String,
    ): String = computeDiscoveryConfigurationKey(preferences, mediaType, sourcePreferences, sourcePolicyRevision)
}

/**
 * Статистика плагина для резолва участия: репрезентативный источник, суммарный вес
 * библиотеки и Taste-аффинити из сигнал-лога (см. [applySourceAffinity]).
 */
private data class DiscoveryPluginStat(
    val key: String,
    val representative: Long,
    val memberIds: Set<Long>,
    val weight: Int,
    val affinity: Double = 0.0,
)

/**
 * Оффсет ротации сидов ручного рефреша: шаг 1 (свободный счётчик, без cap).
 * Шаг 2 с предварительным % 20 посещал только половину позиций окна при чётных
 * размерах пула и циклился на 20 — селектор сам приводится по distinct.size,
 * шаг 1 гарантированно обходит все позиции при любом размере.
 */
internal fun manualSeedOffset(refreshCount: Int): Int = refreshCount

/**
 * Страница провайдеров/каталогов ручного рефреша: цикл 10 страниц (2..11).
 * Цикл 4 полностью повторял выдачу уже к 5-му ручному рефрешу.
 */
internal fun manualPageOffset(refreshCount: Int): Int = ((refreshCount - 1) % 10) + 2

/**
 * Страница фонового цикла: 1..10, шагом по номеру цикла. Первый фон-прогон — страница 1
 * (совпадает с прежним поведением), далее каждый цикл уходит глубже выдачи провайдера.
 */
internal fun backgroundPageOffset(cycleCount: Int): Int = ((cycleCount - 1).mod(10)) + 1

internal fun computeDiscoveryConfigurationKey(
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
    sourcePreferences: SourcePreferences? = null,
    sourcePolicyRevision: String = "",
): String {
    val spec = preferences.genreFilterSpec(mediaType)
    val parts = listOf(
        mediaType.key,
        "rows", preferences.rowLikeEnabled().get(), preferences.rowTasteEnabled().get(),
        preferences.rowTrendEnabled().get(), preferences.rowSourceEnabled().get(),
        "seeds", preferences.seedCount().get(), preferences.seedCompleted().get(),
        preferences.seedActive14().get(), preferences.seedAdded().get(), preferences.seedRated().get(),
        preferences.seedCompletedDays().get(), preferences.seedActiveDays().get(),
        "trend", preferences.trendSeason().get(), preferences.trendSort().get(),
        "status", preferences.releaseStatusFilter().get(),
        "genres", spec.ignoredGenres.sorted(), spec.requiredGenres.sorted(), spec.priorityGenres.sorted(),
        spec.requiredMatchMode.name, spec.requiredEnforcement.name, spec.unknownGenrePolicy.name,
        "sources", preferences.externalProvidersEnabled().get(),
        preferences.discoverySourceMode(mediaType).get(),
        parseKeyCsv(preferences.discoverySourceExcluded(mediaType).get()).sorted(),
        parseKeyCsv(preferences.discoverySourceKnown(mediaType).get()).sorted(),
        "source-policy", sourcePolicyRevision,
        "similar-providers", sourcePreferences?.suggestionsUseShikimori()?.get(),
        if (mediaType !=
            DiscoveryMediaType.ANIME
        ) {
            sourcePreferences?.suggestionsUseMangaUpdatesNovel()?.get()
        } else {
            null
        },
        if (mediaType == DiscoveryMediaType.NOVEL) sourcePreferences?.suggestionsUseNovelUpdates()?.get() else null,
        "nsfw", preferences.filterNsfw().get(),
    )
    val canonical = buildString {
        parts.forEach { value ->
            val text = value.toString()
            append(text.length).append(':').append(text)
        }
    }
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

/** Page budget for a manual refresh: the loop stops as soon as enough fresh titles are collected. */
private const val MAX_MANUAL_HARVEST_PAGES = 20

/** Page depth at which the persisted cursor wraps back to the first page. */
private const val MAX_HARVEST_PAGE = 30

private fun toSuggestion(
    mediaType: DiscoveryMediaType,
    rowType: DiscoveryRowType,
    item: DiscoveryRowItem,
    createdAt: Long,
): DiscoverySuggestion = DiscoverySuggestion(
    id = 0L,
    mediaType = mediaType,
    rowType = rowType,
    title = item.title,
    cleanTitle = item.cleanTitle,
    coverUrl = item.coverUrl,
    reason = item.reason,
    seedTitle = item.seedTitle,
    provider = item.provider,
    score = item.score,
    position = 0L,
    createdAt = createdAt,
    sourceId = item.sourceId,
    sourceUrl = item.sourceUrl,
    genres = item.genres,
)

private fun interleaveRowsForTopUp(
    rows: Map<DiscoveryRowType, List<DiscoveryRowItem>>,
    mediaType: DiscoveryMediaType,
    createdAt: Long,
): List<DiscoverySuggestion> {
    val orderedRows = DiscoveryRowType.entries.mapNotNull { rowType ->
        rows[rowType]?.takeIf { it.isNotEmpty() }?.let { rowType to it }
    }
    if (orderedRows.isEmpty()) return emptyList()
    val maxLen = orderedRows.maxOf { it.second.size }
    val result = ArrayList<DiscoverySuggestion>(orderedRows.sumOf { it.second.size })
    for (index in 0 until maxLen) {
        for ((rowType, items) in orderedRows) {
            if (index < items.size) {
                result += toSuggestion(mediaType, rowType, items[index], createdAt)
            }
        }
    }
    return result
}
