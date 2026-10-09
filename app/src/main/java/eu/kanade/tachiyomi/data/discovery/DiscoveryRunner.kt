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
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
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
 */
data class DiscoveryInstalledPlugin(
    val key: String,
    val sourceIds: List<Long>,
)

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
                            DiscoveryInstalledPlugin(
                                key = ext.pkgName,
                                sourceIds = ext.sources.map { it.id }.filter { it in catalogueIds },
                            )
                        }.filter { it.sourceIds.isNotEmpty() }
                }
                DiscoveryMediaType.MANGA -> {
                    val catalogueIds = Injekt.get<tachiyomi.domain.source.manga.service.MangaSourceManager>()
                        .getCatalogueSources().mapTo(HashSet()) { it.id }
                    Injekt.get<eu.kanade.tachiyomi.extension.manga.MangaExtensionManager>()
                        .installedExtensionsFlow.first().map { ext ->
                            DiscoveryInstalledPlugin(
                                key = ext.pkgName,
                                sourceIds = ext.sources.map { it.id }.filter { it in catalogueIds },
                            )
                        }.filter { it.sourceIds.isNotEmpty() }
                }
                DiscoveryMediaType.NOVEL -> {
                    val catalogueIds = Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>()
                        .getCatalogueSources().mapTo(HashSet()) { it.id }
                    val manager = Injekt.get<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager>()
                    // Только источники с плагином: OmniSource (−42), локальный (0) и сироты не участвуют.
                    groupSourcesByPlugin(catalogueIds) { manager.getPluginId(it) }
                        .map { (key, ids) -> DiscoveryInstalledPlugin(key, ids) }
                }
            }
        }.getOrDefault(emptyList())
    },
) {

    suspend fun run(
        mediaTypes: List<DiscoveryMediaType> = DiscoveryMediaType.entries,
        isManualRefresh: Boolean = false,
    ) {
        if (!preferences.discoveryEnabled().get()) return
        for (mediaType in mediaTypes) {
            try {
                runFor(mediaType, isManualRefresh = isManualRefresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat { "[DiscoveryRunner] $mediaType FAILED: ${e.message}" }
            }
        }
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

    private suspend fun runFor(mediaType: DiscoveryMediaType, isManualRefresh: Boolean = false) =
        mediaRunLocks.getOrPut(mediaType) { Mutex() }
            .withLock { runForLocked(mediaType, isManualRefresh) }

    private suspend fun runForLocked(mediaType: DiscoveryMediaType, isManualRefresh: Boolean = false) {
        val candidates = seedSources.candidates(mediaType)
        val candidateRefreshCount = if (isManualRefresh) {
            preferences.manualRefreshCount(mediaType).get() + 1
        } else {
            preferences.manualRefreshCount(mediaType).get()
        }
        val candidateBackgroundCycle = if (isManualRefresh) {
            preferences.backgroundCycleCount(mediaType).get()
        } else {
            preferences.backgroundCycleCount(mediaType).get() + 1
        }
        val seedOffset = if (isManualRefresh) manualSeedOffset(candidateRefreshCount) else 0
        val seeds = seedSelector.select(
            candidates,
            SeedSettings(
                maxSeeds = preferences.seedCount().get(),
                useCompleted = preferences.seedCompleted().get(),
                useActive14 = preferences.seedActive14().get(),
                useAdded = preferences.seedAdded().get(),
                completedWindowDays = preferences.seedCompletedDays().get().toLong(),
                activeWindowDays = preferences.seedActiveDays().get().toLong(),
            ),
            offset = seedOffset,
        )
        val sourcePreferences = sourcePreferencesProvider()
        // Taste Learning Engine: один запрос сигнал-лога на генерацию — и на
        // source-аффинити участия плагинов, и на fold/merge вкуса, и на
        // «просмотрено»-исключение ниже.
        val allSignals = runCatching { repository.getSignals(mediaType) }.getOrDefault(emptyList())
        val preferredSourceId = when (mediaType) {
            DiscoveryMediaType.ANIME -> sourcePreferences.lastUsedAnimeSource().get()
            DiscoveryMediaType.MANGA -> sourcePreferences.lastUsedMangaSource().get()
            DiscoveryMediaType.NOVEL -> sourcePreferences.lastUsedNovelSource().get()
        }
        // Участие плагинов: единица — расширение (все языковые варианты), не источник.
        // auto = топ-3 плагинов по весу библиотеки, manual = набор пользователя (cap 8).
        val plugins = installedPluginsProvider(mediaType)
        val sourceWeights = candidates.filter { it.sourceId > 0 }.groupingBy { it.sourceId }.eachCount()
        // Taste Learning Engine: аффинити источников из сигнал-лога бустит вес участия
        // плагина (плавный tanh-буст, порядок честен и при негативе — кламп 0.4..2.0x).
        val learnedSourceAffinity = foldLearnedTasteProfile(signals = allSignals).sourceAffinity
        val pluginStats = plugins.mapNotNull { plugin ->
            val representative = plugin.sourceIds.sortedWith(
                compareByDescending<Long> { sourceWeights[it] ?: 0 }.thenBy { it },
            ).firstOrNull() ?: return@mapNotNull null
            DiscoveryPluginStat(
                key = plugin.key,
                representative = representative,
                memberIds = plugin.sourceIds.toSet(),
                weight = plugin.sourceIds.sumOf { sourceWeights[it] ?: 0 },
                affinity = learnedSourceAffinity[plugin.key] ?: 0.0,
            )
        }.sortedWith(
            compareByDescending<DiscoveryPluginStat> {
                applySourceAffinity(it.weight, it.affinity)
            }.thenBy { it.representative },
        )
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

        val currentSuggestions = runCatching { repository.subscribe(mediaType).firstOrNull() }.getOrNull().orEmpty()
        val shownCutoffMap = runCatching { repository.getShownTitlesWithTimestamp(mediaType) }.getOrDefault(emptyMap())
        val shownTitles = shownCutoffMap.keys
        val recentCleanTitles = if (isManualRefresh) {
            currentSuggestions.mapTo(HashSet()) { it.cleanTitle } + shownTitles
        } else {
            shownTitles
        }
        val pageOffset = if (isManualRefresh) {
            manualPageOffset(candidateRefreshCount)
        } else {
            backgroundPageOffset(candidateBackgroundCycle)
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

        val context = DiscoveryBuildContext(
            mediaType = mediaType,
            seeds = seedsWithTracks,
            libraryCleanTitles = candidates.mapTo(HashSet()) { normalizeDiscoveryTitle(it.title) },
            historyCleanTitles = seedSources.historyCleanTitles(mediaType),
            hiddenCleanTitles = repository.getHiddenTitles(mediaType) + consumedCleanTitles,
            tasteProfile = mergedTasteProfile,
            // V3: глобальный игнор-список жанров (преф) поверх per-media блэклиста тегов.
            blacklistedTags = repository.getBlacklistedTags(mediaType) +
                parseGenreFilterCsv(preferences.ignoredGenres().get()).let { (canon, raw) -> canon + raw },
            // V1: статус-фильтр трендов — CSV из настроек, пусто = без фильтра.
            releaseStatuses = DiscoveryReleaseStatus.parseCsv(preferences.releaseStatusFilter().get()),
            // V3: жанровые фильтры — CSV с raw-префиксом; canonical + raw матчатся одинаково.
            priorityGenres = parseGenreFilterCsv(preferences.priorityGenres().get())
                .let { (canon, raw) -> canon + raw.toSet() },
            requiredGenres = parseGenreFilterCsv(preferences.requiredGenres().get())
                .let { (canon, raw) -> canon + raw.toSet() },
            sourceId = participation.primarySourceId,
            // C1 + участие плагинов: состав определяет resolveSourceParticipation
            // (auto — топ-3 по весу библиотеки, manual — все выбранные, cap 8).
            sourceIds = participation.sourceIds,
            recentCleanTitles = recentCleanTitles,
            shownCutoffMap = shownCutoffMap,
            // Ручной рефреш: тайтлы текущей ленты не возвращаются stale-добором —
            // наполнение ряда реально сменяется, а не «новые + всё старое».
            currentFeedCleanTitles = if (isManualRefresh) {
                currentSuggestions.mapTo(HashSet()) { it.cleanTitle }
            } else {
                emptySet()
            },
            pageOffset = pageOffset,
        )
        logcat {
            "[Discovery] refresh media=$mediaType manual=$isManualRefresh " +
                "statuses=${context.releaseStatuses} sources=${context.sourceIds} primary=${context.sourceId}"
        }
        // Выключенные в настройках ряды: чистим их записи в БД, чтобы UI не показывал «зомби».
        // LIKE/TREND — чисто внешние ряды: при выключенных внешних провайдерах их тоже не строим.
        val useExternal = preferences.externalProvidersEnabled().get()
        if (!preferences.rowLikeEnabled().get() || !useExternal) {
            repository.replaceRows(mediaType, tachiyomi.domain.discovery.model.DiscoveryRowType.LIKE, emptyList())
        }
        if (!preferences.rowTasteEnabled().get()) {
            repository.replaceRows(mediaType, tachiyomi.domain.discovery.model.DiscoveryRowType.TASTE, emptyList())
        }
        if (!preferences.rowTrendEnabled().get() || !useExternal) {
            repository.replaceRows(mediaType, tachiyomi.domain.discovery.model.DiscoveryRowType.TREND, emptyList())
        }
        if (!preferences.rowSourceEnabled().get()) {
            repository.replaceRows(mediaType, tachiyomi.domain.discovery.model.DiscoveryRowType.SOURCE, emptyList())
        }
        val builders = buildList {
            if (preferences.rowLikeEnabled().get() && useExternal) {
                add(DiscoveryLikeRowBuilder(suggestionCoordinatorFactory()))
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
                add(DiscoverySourceRowBuilder(sourceCatalog, maxSources = 8, rowCap = 50))
            }
        }
        if (builders.isEmpty()) {
            failedRowsSink(mediaType, emptySet())
            return
        }

        val coordinator = coordinatorFactory(builders)
        val feed = coordinator.streamFeed(context) { rowType, items ->
            // Пустой ряд НЕ перезаписывает кэш: прежняя подборка живёт до следующего
            // успешного непустого результата (защита от «всё пропало» при деградации провайдеров).
            if (items.isEmpty()) {
                logcat { "[DiscoveryRunner] $mediaType row $rowType empty — cache preserved" }
                return@streamFeed
            }
            repository.replaceRows(
                mediaType = mediaType,
                rowType = rowType,
                items = items.map { item ->
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
                        // Перезаписывается индексом списка внутри replaceRows.
                        position = 0L,
                        createdAt = System.currentTimeMillis(),
                        sourceId = item.sourceId,
                        sourceUrl = item.sourceUrl,
                        genres = item.genres,
                    )
                },
            )
        }
        logcat {
            "[DiscoveryRunner] $mediaType done: rows=${feed.rows.mapValues { it.value.size }} failed=${feed.failedRows}"
        }
        // «Только плагины»: пустой TASTE-ряд (нет подходящих плагин-тайтлов) не должен
        // оставлять в кэше внешние тайтлы — «пустой ряд не перезаписывает» здесь
        // мешает явному выбору пользователя, чистим вручную. НО: упавший ряд (network)
        // — не «честно пустой», его кэш не трогаем (feed.rows[T]=null при падении).
        if (!useExternal && feed.rows[tachiyomi.domain.discovery.model.DiscoveryRowType.TASTE].isNullOrEmpty() &&
            tachiyomi.domain.discovery.model.DiscoveryRowType.TASTE !in feed.failedRows
        ) {
            repository.replaceRows(mediaType, tachiyomi.domain.discovery.model.DiscoveryRowType.TASTE, emptyList())
        }
        // Статус-фильтр активен: пустой ряд (нет тайтлов выбранного статуса) не должен
        // жить старым кэшем с НЕсоответствующими статусами — иначе «выбран Завершённый,
        // а показывается всякое» из старой генерации. Чистим явно. НО: упавший ряд
        // (провайдер/сеть отвалились) — не «честно пустой», его кэш сохраняем, иначе
        // при отвале сети с включённым фильтром пропадала бы вся лента.
        if (context.releaseStatuses.isNotEmpty() || context.requiredGenres.isNotEmpty()) {
            tachiyomi.domain.discovery.model.DiscoveryRowType.entries.forEach { rowType ->
                if (feed.rows[rowType].isNullOrEmpty() && rowType !in feed.failedRows) {
                    repository.replaceRows(mediaType, rowType, emptyList())
                }
            }
        }
        // Замороженные ряды: «честно пустой» LIKE при пустых сидах и TASTE при пустом профиле
        // не должны вечно таскать кэш прошлых генераций.
        if (feed.rows[DiscoveryRowType.LIKE].isNullOrEmpty() &&
            DiscoveryRowType.LIKE !in feed.failedRows &&
            context.seeds.isEmpty()
        ) {
            repository.replaceRows(mediaType, DiscoveryRowType.LIKE, emptyList())
        }
        if (feed.rows[DiscoveryRowType.TASTE].isNullOrEmpty() &&
            DiscoveryRowType.TASTE !in feed.failedRows &&
            context.tasteProfile.isEmpty()
        ) {
            repository.replaceRows(mediaType, DiscoveryRowType.TASTE, emptyList())
        }
        val anyRowPersisted = feed.rows.values.any { it.isNotEmpty() }
        if (anyRowPersisted) {
            if (isManualRefresh) {
                preferences.manualRefreshCount(mediaType).set(candidateRefreshCount)
            } else {
                preferences.backgroundCycleCount(mediaType).set(candidateBackgroundCycle)
            }
        }
        failedRowsSink(mediaType, feed.failedRows)
    }
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
