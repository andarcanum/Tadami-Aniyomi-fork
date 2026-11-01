package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryPreparedPool
import tachiyomi.domain.discovery.model.DiscoveryPublicationOutcome
import tachiyomi.domain.discovery.model.DiscoveryPublishReceipt
import tachiyomi.domain.discovery.model.DiscoveryQueryEvidence
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySourceCursor
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.discovery.repository.DiscoveryRepository
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
    ): DiscoveryRunOutcome =
        mediaRunLocks.getOrPut(mediaType) { Mutex() }
            .withLock {
                try {
                    runForLocked(mediaType, isManualRefresh, mediaBudgetMs, isConfigurationRefresh)
                } finally {
                    DiscoveryProgressTracker.reset(mediaType)
                }
            }

    private suspend fun runForLocked(
        mediaType: DiscoveryMediaType,
        isManualRefresh: Boolean = false,
        mediaBudgetMs: Long? = null,
        isConfigurationRefresh: Boolean = false,
    ): DiscoveryRunOutcome {
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
        val eligibilitySnapshot = repository.eligibility(mediaType)
        val configurationKey = discoveryConfigurationKey(mediaType)
        val harvestPages = if (isManualRefresh) MAX_MANUAL_HARVEST_PAGES else 1
        val resumeCursor = previousPool?.cursors.orEmpty()
            .firstOrNull { it.queryKey == configurationKey && !it.exhausted }
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
        // Участие плагинов: единица — расширение (все языковые варианты), не источник.
        // auto = топ-3 плагинов по весу библиотеки, manual = набор пользователя (cap 8).
        val plugins = installedPluginsProvider(mediaType)
        val sourceWeights = candidates.filter { it.sourceId > 0 }.groupingBy { it.sourceId }.eachCount()
        val isManualMode = preferences.discoverySourceMode(mediaType).get() == "manual"
        val learnedSourceAffinity = foldLearnedTasteProfile(signals = allSignals).sourceAffinity
        val pluginStats = plugins.mapNotNull { plugin ->
            val representative = selectPluginRepresentative(
                memberIds = plugin.sourceIds.toSet(),
                weights = sourceWeights,
                langs = plugin.langs,
                appLang = java.util.Locale.getDefault().language.lowercase(),
            ) ?: return@mapNotNull null
            DiscoveryPluginStat(
                key = plugin.key,
                representative = representative,
                memberIds = plugin.sourceIds.toSet(),
                weight = if (isManualMode) 1 else plugin.sourceIds.sumOf { sourceWeights[it] ?: 0 },
                affinity = learnedSourceAffinity[plugin.key] ?: 0.0,
            )
        }.sortedWith(
            compareByDescending<DiscoveryPluginStat> {
                applySourceAffinity(it.weight, it.affinity, isManualMode = isManualMode)
            }.thenBy { it.representative },
        )
        val effectiveSourceWeights = pluginStats.associate {
            it.representative to applySourceAffinity(it.weight, it.affinity, isManualMode = isManualMode)
        }
        val excludedKeys = parseKeyCsv(preferences.discoverySourceExcluded(mediaType).get())
        val participation = if (pluginStats.isEmpty()) {
            // Плагины неизвестны (юнит-тесты / расширения ещё не загружены при раннем старте):
            // легаси-порядок по весу библиотеки без исключений (нет маппинга ключ→источник).
            resolveSourceParticipation(
                mode = preferences.discoverySourceMode(mediaType).get(),
                excludedIds = emptySet(),
                weightOrder = rankSourceIds(candidates, limit = Int.MAX_VALUE),
                lastUsedId = preferredSourceId,
                fallbackId = fallbackSourceIdProvider(mediaType),
            )
        } else {
            val excludedIds = pluginStats.filter { it.key in excludedKeys }.mapTo(HashSet()) { it.representative }
            // lastUsed/fallback исключённого плагина не должен просочиться в primary.
            val lastUsedPlugin = pluginStats.firstOrNull { preferredSourceId in it.memberIds }
            val effectiveLastUsed = if (lastUsedPlugin == null || lastUsedPlugin.key !in excludedKeys) {
                preferredSourceId
            } else {
                -1L
            }
            val rawFallback = fallbackSourceIdProvider(mediaType)
            val fallbackPlugin = pluginStats.firstOrNull { rawFallback in it.memberIds }
            val effectiveFallback = when {
                rawFallback <= 0 -> rawFallback
                fallbackPlugin == null -> rawFallback
                fallbackPlugin.key in excludedKeys -> -1L
                else -> fallbackPlugin.representative
            }
            resolveSourceParticipation(
                mode = preferences.discoverySourceMode(mediaType).get(),
                excludedIds = excludedIds,
                weightOrder = pluginStats.map { it.representative },
                lastUsedId = effectiveLastUsed,
                fallbackId = effectiveFallback,
            )
        }

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
        val currentSuggestions = emptyList<DiscoverySuggestion>()
        val existingRowItems = emptyMap<DiscoveryRowType, List<DiscoveryRowItem>>()
        val shownCutoffMap = runCatching {
            repository.getRecentExclusionsWithTimestamp(mediaType)
        }.getOrDefault(emptyMap())
        val shownTitles = shownCutoffMap.keys
        val recentCleanTitles = shownTitles
        val pageOffset = when {
            isConfigurationRefresh -> 1
            // A manual refresh scans from the top until enough genuinely new
            // titles are collected; the persisted cursor is for background cycles.
            isManualRefresh -> 1
            resumeCursor != null -> resumeCursor.nextPage.coerceAtLeast(1)
            else -> backgroundPageOffset(candidateBackgroundCycle)
        }

        // Taste Learning Engine: fold сигнал-лога и слияние с библиотечным профилем.
        // Плавный старт (blend) — при пустом логе merge возвращает библиотечный профиль как есть.
        // Неявный негатив: тайтл показан ≥3 раз за 48ч без явного сигнала → жанры
        // получают мягкий минус. Синтез на лету (не пишется в БД), явные сигналы важнее.
        val libraryTasteProfile = buildTasteProfile(candidates)
        val libraryProfileGenres = libraryTasteProfile.map { it.first }
        val implicitNegatives = runCatching {
            synthesizeImplicitNegatives(
                shownWithCount = repository.getShownWithCount(mediaType),
                explicitSignals = allSignals,
                currentSuggestions = currentSuggestions,
                mediaType = mediaType,
                profileGenres = libraryProfileGenres,
            )
        }.getOrDefault(emptyList())
        val learnedProfile = foldLearnedTasteProfile(signals = allSignals + implicitNegatives)
        val mergedTasteProfile = mergeTasteProfiles(
            libraryProfile = libraryTasteProfile,
            learned = learnedProfile,
        )
        // «Просмотрено/прочитано»: нейтральное исключение — consumed-тайтлы не
        // попадают в новые генерации ленты (вкусовой профиль они не трогают).
        val consumedCleanTitles = allSignals
            .filter { it.signalType.excludesFromFeed }
            .mapTo(HashSet()) { it.cleanTitle }

        val genreFilterSpec = preferences.genreFilterSpec()
        // One budget for the whole media run: every row shares the deadline and
        // the details allowance, so rows cannot multiply the worker cost.
        // Genre-selected mode verifies deep pages of a few rich sources, so it
        // gets a larger allowance; the deadline remains the real guard.
        val verificationBudget = DiscoveryVerificationBudget(
            budgetMs = mediaBudgetMs ?: DiscoveryVerificationBudget.DEFAULT_MEDIA_BUDGET_MS,
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
            blacklistedTags = repository.getBlacklistedTags(mediaType),
            ignoredGenres = genreFilterSpec.ignoredGenres,
            // V1: статус-фильтр трендов — CSV из настроек, пусто = без фильтра.
            releaseStatuses = DiscoveryReleaseStatus.parseCsv(preferences.releaseStatusFilter().get()),
            // V3: жанровые фильтры — полная спецификация с режимами строгости и сопоставления.
            priorityGenres = genreFilterSpec.priorityGenres,
            requiredGenres = genreFilterSpec.requiredGenres,
            genreFilterSpec = genreFilterSpec,
            sourceId = participation.primarySourceId,
            // C1 + участие плагинов: состав определяет resolveSourceParticipation
            // (auto — топ-3 по весу библиотеки, manual — все выбранные).
            sourceIds = participation.sourceIds,
            sourceWeights = effectiveSourceWeights,
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
            val ids = participation.sourceIds.filter { it > 0L }
                .ifEmpty { listOf(participation.primarySourceId).filter { it > 0L } }
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
                        targetItems = RICH_SOURCE_TARGET * 2,
                        perSourceCap = RICH_SOURCE_TARGET * 2,
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
                add(DiscoverySourceRowBuilder(sourceCatalog, rowCap = 50))
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
        val fillAudit = buildDiscoveryFillAudit(
            mediaType = mediaType,
            rawCandidates = feed.rawCandidates,
            finalRows = feed.rows,
            context = context,
            detailsUsed = verificationBudget.detailsUsed,
            detailsAllowance = verificationBudget.detailsAllowance,
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

        val pooledItems = feed.rows.flatMap { (rowType, items) ->
            items.map { item ->
                DiscoverySuggestion(
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
                    createdAt = System.currentTimeMillis(),
                    sourceId = item.sourceId,
                    sourceUrl = item.sourceUrl,
                    genres = item.genres,
                )
            }
        }
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

        repository.installConfigurationKey(mediaType, configurationKey)
        val requestId = java.util.UUID.randomUUID().toString()
        val token = if (prepared != null) {
            repository.reserveGeneration(mediaType, requestId, expectedGeneration, configurationKey)
        } else {
            null
        }
        val publishReceipt = if (prepared != null && token != null) {
            repository.publishGeneration(token, prepared)
        } else {
            null
        }
        val published = publishReceipt as? DiscoveryPublishReceipt.Published
        val persistedRows = if (published != null) {
            feed.rows.filterValues { it.isNotEmpty() }.keys
        } else {
            emptySet()
        }

        val status = when {
            publishReceipt is DiscoveryPublishReceipt.Superseded -> DiscoveryRunStatus.SKIPPED
            allExecutedFailed -> DiscoveryRunStatus.FAILED
            publishReceipt is DiscoveryPublishReceipt.NoAdmissibleItems -> DiscoveryRunStatus.FAILED
            prepared == null -> if (hasPartialFailures) DiscoveryRunStatus.PARTIAL else DiscoveryRunStatus.FAILED
            token == null -> DiscoveryRunStatus.SKIPPED
            published?.snapshot?.outcome == DiscoveryPublicationOutcome.PARTIAL -> DiscoveryRunStatus.PARTIAL
            hasPartialFailures -> DiscoveryRunStatus.PARTIAL
            else -> DiscoveryRunStatus.SUCCESS
        }

        if (actualExecutedRows.isNotEmpty() && !allExecutedFailed && published != null) {
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

        return DiscoveryRunOutcome(
            mediaType = mediaType,
            status = status,
            attemptedRows = attemptedRows,
            failedRows = feed.failedRows,
            degradedRows = feed.degradedRows,
            persistedRows = persistedRows,
        )
    }
    private fun discoveryConfigurationKey(mediaType: DiscoveryMediaType): String =
        computeDiscoveryConfigurationKey(preferences, mediaType)
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
): String {
    val spec = preferences.genreFilterSpec()
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
        preferences.discoverySourceExcluded(mediaType).get(),
        "nsfw", preferences.filterNsfw().get(),
    )
    return parts.joinToString("|").hashCode().toUInt().toString(16)
}

/** Page budget for a manual refresh: the loop stops as soon as enough fresh titles are collected. */
private const val MAX_MANUAL_HARVEST_PAGES = 20

/** Page depth at which the persisted cursor wraps back to the first page. */
private const val MAX_HARVEST_PAGE = 30
