package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import java.io.IOException

internal const val SOURCE_FETCH_TIMEOUT_MS = 6_000L

/** Кандидаты LIKE-сида: resolver дополняет сырой тайтл оригиналом из описания и нормализованными вариантами. */
internal fun likeSeedCandidateTitles(seed: DiscoverySeedInput): List<String> =
    (SuggestionTitleResolver.resolveCandidates(seed.title, seed.description) + seed.altTitles)
        .filter { it.isNotBlank() }
        .distinct()

/**
 * Ряд «Похоже на X»: переиспользует существующий [SuggestionCoordinator]
 * (AniList / MAL-Jikan / MangaUpdates / NovelUpdates) на каждый выбранный сид.
 */
class DiscoveryLikeRowBuilder(
    private val suggestionCoordinator: SuggestionCoordinator,
    private val catalog: DiscoverySourceCatalog? = null,
    private val includeExternal: Boolean = true,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.LIKE
    override var isDegraded: Boolean = false
        private set

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val mediaType = when (context.mediaType) {
            DiscoveryMediaType.ANIME -> SuggestionMediaType.ANIME
            DiscoveryMediaType.MANGA -> SuggestionMediaType.MANGA
            DiscoveryMediaType.NOVEL -> SuggestionMediaType.NOVEL
        }
        if (includeExternal) {
            var fullyFailedSeeds = 0
            var hasFailedSources = false
            val feedbackPerSeed = context.feedbackSeeds.map { feedbackSeed ->
                val suggestionSeed = SuggestionSeed(
                    mediaType = mediaType,
                    primaryTitle = feedbackSeed.title,
                    candidateTitles = SuggestionTitleResolver.resolveCandidates(feedbackSeed.title, null),
                    description = null,
                    author = null,
                    genres = feedbackSeed.genres.ifEmpty { null },
                )
                val result = suggestionCoordinator.fetchSuggestions(
                    suggestionSeed,
                    limit = 25,
                    releaseStatuses = context.releaseStatuses,
                )
                if (result.failedSources > 0) {
                    hasFailedSources = true
                }
                if (result.items.isEmpty() && result.attemptedSources > 0 &&
                    result.failedSources == result.attemptedSources
                ) {
                    fullyFailedSeeds++
                }
                result.items.map { item ->
                    DiscoveryRowItem(
                        title = item.title,
                        cleanTitle = normalizeDiscoveryTitle(item.title),
                        coverUrl = item.thumbnailUrl,
                        reason = null,
                        seedTitle = feedbackSeed.title,
                        provider = item.providerName,
                        score = item.bestMatchScoreFor(suggestionSeed).toDouble(),
                        genres = item.genres,
                    )
                }
            }
            val libraryPerSeed = context.seeds.map { seed ->
                val suggestionSeed = SuggestionSeed(
                    mediaType = mediaType,
                    primaryTitle = seed.title,
                    candidateTitles = likeSeedCandidateTitles(seed),
                    description = seed.description,
                    author = seed.author,
                    genres = seed.genres.ifEmpty { null },
                )
                val result = suggestionCoordinator.fetchSuggestions(
                    suggestionSeed,
                    limit = 25,
                    releaseStatuses = context.releaseStatuses,
                )
                if (result.failedSources > 0) {
                    hasFailedSources = true
                }
                if (result.items.isEmpty() && result.attemptedSources > 0 &&
                    result.failedSources == result.attemptedSources
                ) {
                    fullyFailedSeeds++
                }
                result.items.map { item ->
                    DiscoveryRowItem(
                        title = item.title,
                        cleanTitle = normalizeDiscoveryTitle(item.title),
                        coverUrl = item.thumbnailUrl,
                        // Локализованный текст «Похоже на „X“» композится на рендере из seedTitle.
                        reason = null,
                        seedTitle = seed.title,
                        provider = item.providerName,
                        score = item.bestMatchScoreFor(suggestionSeed).toDouble(),
                        genres = item.genres,
                    )
                }
            }
            val perSeed = feedbackPerSeed + libraryPerSeed
            val merged = mergeSeedResults(perSeed)
            val totalSeeds = context.seeds.size + context.feedbackSeeds.size
            if (hasFailedSources && merged.isNotEmpty()) {
                isDegraded = true
            }
            if (merged.isEmpty() && totalSeeds > 0 && (fullyFailedSeeds == totalSeeds || hasFailedSources)) {
                throw IOException("all suggestion providers failed for every seed")
            }
            return context.candidatePolicy.filterRowCandidates(merged, { it.genres })
        } else {
            if (catalog == null) return emptyList()
            val sourceIds = context.sourceIds.filter { it > 0 }
                .ifEmpty { listOf(context.sourceId).filter { it > 0 } }
            if (sourceIds.isEmpty()) return emptyList()

            val allSeeds = (
                context.feedbackSeeds.map { it.title to it.genres } +
                    context.seeds.map { it.title to it.genres }
                ).filter { it.second.isNotEmpty() }
            if (allSeeds.isEmpty()) return emptyList()

            val perSeed = supervisorScope {
                allSeeds.mapIndexed { seedIndex, (seedTitle, genres) ->
                    async {
                        val seedSources = if (sourceIds.size <= 2) {
                            sourceIds
                        } else {
                            val start = (seedIndex * 2) % sourceIds.size
                            listOf(sourceIds[start], sourceIds[(start + 1) % sourceIds.size])
                        }
                        seedSources.map { sourceId ->
                            async {
                                runCatchingUnlessCancelled {
                                    withTimeoutOrNull(SOURCE_FETCH_TIMEOUT_MS) {
                                        catalog.popularWithGenres(
                                            context.mediaType,
                                            sourceId,
                                            genres = genres.take(3),
                                            page = 1,
                                            releaseStatuses = context.releaseStatuses,
                                        )
                                    }
                                }.getOrNull().orEmpty()
                            }
                        }.awaitAll().flatten().map { item ->
                            item.copy(
                                seedTitle = seedTitle,
                                reason = null,
                            )
                        }
                    }
                }.awaitAll()
            }
            val merged = mergeSeedResults(perSeed)
            return context.candidatePolicy.filterRowCandidates(merged, { it.genres })
        }
    }
}

/**
 * Ряд «Тренды и сезон»: агрегатор трендов (Shikimori, MangaDex, Jikan, AniList)
 * и свежие обновления установленных источников.
 */
class DiscoveryTrendRowBuilder(
    private val trending: DiscoveryTrendingSource,
    private val catalog: DiscoverySourceCatalog,
    private val seasonProvider: () -> TrendSeason,
    private val sortProvider: () -> TrendSort,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.TREND

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val effectiveSort = when (context.mediaType) {
            DiscoveryMediaType.ANIME -> sortProvider()
            // Для манги и новелл запрашиваем реальные тренды (TRENDING_DESC),
            // чтобы не дублировать статический all-time топ ряда TASTE
            DiscoveryMediaType.MANGA, DiscoveryMediaType.NOVEL -> TrendSort.TRENDING
        }
        // C1: приоритет — топ-взвешенный источник библиотеки, иначе lastUsed/fallback.
        val preferredSourceId = context.sourceIds.firstOrNull() ?: context.sourceId
        val policy = context.candidatePolicy

        val fromSource = if (context.mediaType == DiscoveryMediaType.NOVEL && preferredSourceId > 0) {
            runCatchingUnlessCancelled {
                catalog.latest(
                    context.mediaType,
                    preferredSourceId,
                    page = context.pageOffset,
                    releaseStatuses = context.releaseStatuses,
                )
            }.getOrNull().orEmpty().map { item ->
                item.copy(
                    reason = "source",
                    score = 0.0,
                )
            }.let { items -> policy.filterRowCandidates(items, { it.genres }) }
        } else {
            emptyList()
        }

        val fromTrending = runCatchingUnlessCancelled {
            trending.fetch(
                mediaType = context.mediaType,
                season = seasonProvider(),
                sort = effectiveSort,
                page = context.pageOffset,
                releaseStatuses = context.releaseStatuses,
            )
        }.getOrNull().orEmpty()
            .let { items -> policy.filterRowCandidates(items, { it.genres }) }
            .map { item ->
                DiscoveryRowItem(
                    title = item.title,
                    cleanTitle = item.cleanTitle,
                    altCleanTitles = listOfNotNull(
                        item.altTitle?.let {
                            normalizeDiscoveryTitle(it)
                        },
                    ).filter { it.isNotBlank() },
                    coverUrl = item.coverUrl,
                    // Payload "current"/"next"/"source"/null — шаблон строки выбирается на рендере.
                    reason = item.seasonLabel,
                    seedTitle = null,
                    provider = item.provider,
                    score = 0.0,
                    genres = item.genres,
                )
            }

        if (context.releaseStatuses.isNotEmpty()) {
            logcat {
                "[Discovery] trend row media=${context.mediaType} trending=${fromTrending.size} " +
                    "sourceLatest=${fromSource.size} statuses=${context.releaseStatuses}"
            }
        }

        // Приоритетные жанры ранжируются единым within-row ranker'ом в policy.filterRowCandidates.

        if (context.mediaType == DiscoveryMediaType.NOVEL) {
            val combinedNovels = (fromSource + fromTrending).distinctBy { it.cleanTitle }
            val filteredNovels = policy.filterRowCandidates(combinedNovels, { it.genres })
            if (filteredNovels.isNotEmpty()) {
                return filteredNovels
            }
        } else {
            if (fromTrending.isNotEmpty()) {
                return fromTrending
            }

            // Фолбэк при недоступности трендов:
            // Запрашиваем «Свежее» (latest updates) из активного/установленного источника пользователя!
            if (preferredSourceId > 0) {
                val fallbackSource = runCatchingUnlessCancelled {
                    catalog.latest(
                        context.mediaType,
                        preferredSourceId,
                        page = context.pageOffset,
                        releaseStatuses = context.releaseStatuses,
                    )
                }.getOrNull().orEmpty().map { item ->
                    item.copy(
                        reason = "source",
                        score = 0.0,
                    )
                }.let { items -> policy.filterRowCandidates(items, { it.genres }) }
                if (fallbackSource.isNotEmpty()) {
                    return fallbackSource
                }
            }
        }

        return emptyList()
    }
}

/**
 * Ряд «Твой вкус»: жанровый профиль библиотеки/истории → тренды по жанрам
 * + жанровый фильтр каталога выбранного источника; скор = совпадение жанров.
 */
class DiscoveryTasteRowBuilder(
    private val trending: DiscoveryTrendingSource,
    private val catalog: DiscoverySourceCatalog,
    private val sortProvider: () -> TrendSort,
    // «Только плагины»: false — внешние жанровые провайдеры не опрашиваются,
    // ряд строится исключительно из каталога primary-источника.
    private val includeExternal: Boolean = true,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.TASTE

    override var isDegraded: Boolean = false
        private set

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        isDegraded = false
        val policy = context.candidatePolicy
        val activeProfile = resolveDiscoveryTasteProfile(context.tasteProfile, policy)
        if (activeProfile.isEmpty()) return emptyList()
        val required = policy.requiredGenres.isNotEmpty()

        // A required query must not be narrowed by unrelated priority/library genres.
        val genreNames = if (policy.requiredGenres.isNotEmpty()) {
            policy.requiredGenres.toList()
        } else {
            (policy.priorityGenres + activeProfile.map { it.first })
                .filterNot { policy.isForbidden(listOf(it)) }.distinct().take(6)
        }
        val sourceGenreNames = genreNames
        val trendingResult = if (includeExternal) {
            runCatchingUnlessCancelled {
                trending.fetchByGenres(
                    context.mediaType,
                    genreNames,
                    sortProvider(),
                    page = context.pageOffset,
                    releaseStatuses = context.releaseStatuses,
                )
            }
        } else {
            null
        }

        var trendingItems = trendingResult?.getOrNull().orEmpty()
        var filteredTrending = policy.filterRowCandidates(trendingItems, { it.genres })
        // Добор со 2-й страницы при строгом фильтре, если 1-я страница вернула результаты, но ни один не прошёл строгий фильтр
        if (filteredTrending.isEmpty() && policy.isStrictRequired && trendingItems.isNotEmpty() && includeExternal) {
            val page2Result = runCatchingUnlessCancelled {
                trending.fetchByGenres(
                    context.mediaType,
                    genreNames,
                    sortProvider(),
                    page = context.pageOffset + 1,
                    releaseStatuses = context.releaseStatuses,
                )
            }
            val page2Items = page2Result.getOrNull().orEmpty()
            if (page2Items.isNotEmpty()) {
                trendingItems = (trendingItems + page2Items).distinctBy { it.cleanTitle }
                filteredTrending = policy.filterRowCandidates(trendingItems, { it.genres })
            }
        }

        val sourceIds = context.sourceIds.filter { it > 0 }
            .ifEmpty { listOf(context.sourceId).filter { it > 0 } }
        val sourceResults = if (sourceIds.isNotEmpty()) {
            val rotation = (context.pageOffset - 1).coerceAtLeast(0) % sourceIds.size
            val ordered = sourceIds.drop(rotation) + sourceIds.take(rotation)
            // Required mode pays for verification; the ordinary taste path keeps
            // its previous cheap shape (six sources, no details hydration).
            val toQuery = if (required) ordered else ordered.take(6)
            queryDiscoveryGenreSources(
                catalog = catalog,
                media = context.mediaType,
                sourceIds = toQuery,
                genres = sourceGenreNames,
                page = context.pageOffset,
                releaseStatuses = context.releaseStatuses,
                policy = policy,
                timeoutMs = if (required) GENRE_SOURCE_TIMEOUT_MS else SOURCE_FETCH_TIMEOUT_MS,
                hydrate = required,
                budget = context.verificationBudget,
            )
        } else {
            emptyList()
        }
        // In the cheap path cards without genres were never going to be hydrated,
        // so an "unattempted" count there is by design and not a degraded row.
        isDegraded = sourceResults.any {
            it.failure != null || (required && it.summary.outcome == GenreVerificationOutcome.INCOMPLETE)
        }
        val fromTrending = filteredTrending.map { item ->
            DiscoveryRowItem(
                title = item.title,
                cleanTitle = item.cleanTitle,
                altCleanTitles = listOfNotNull(
                    item.altTitle?.let {
                        normalizeDiscoveryTitle(it)
                    },
                ).filter { it.isNotBlank() },
                coverUrl = item.coverUrl,
                reason = matchedGenres(item.genres, activeProfile).ifEmpty { genreNames.take(2) }.joinToString(", "),
                seedTitle = null,
                provider = item.provider,
                // Базовый скор совпадения жанров; приоритетные жанры бустятся единым within-row ranker'ом в policy.
                score = tasteScore(item.genres, activeProfile),
                genres = item.genres,
            )
        }
        val sourceParts = sourceResults.map { res ->
            res.summary.items.map { item ->
                item.copy(
                    reason = genreNames.take(2).joinToString(", "),
                    score = 0.5 + item.score * 0.1,
                )
            }
        }
        val fromSource = if (sourceParts.size > 1) {
            interleaveSourceParts(sourceParts, cap = 50).distinctBy { it.cleanTitle }
        } else {
            sourceParts.flatten()
        }.let { items -> policy.filterRowCandidates(items, { it.genres }) }
        val combined = if (fromTrending.isEmpty()) fromSource else mergeNormalized(fromTrending, fromSource)
        val filteredCombined = policy.filterRowCandidates(
            combined,
            { it.genres },
        )
        // A verified empty source set is a useful result, not a failure. Fail the
        // row only when nothing useful could be verified at all.
        val sourcesFailed = sourceResults.isNotEmpty() &&
            sourceResults.all { it.summary.outcome == GenreVerificationOutcome.FAILED }
        val anyFailed = if (sourceResults.isEmpty()) {
            trendingResult?.isFailure ?: false
        } else {
            sourcesFailed
        }
        if (filteredCombined.isEmpty() && anyFailed) {
            throw IOException("taste sources failed without results")
        }
        return filterGenreClustering(filteredCombined, maxPerGenre = 6).take(50)
    }
}

/**
 * Ряд «Источник» (C1): микс ⅘ Latest + ⅕ Popular топ-N источников по весу
 * библиотеки (число тайтлов пользователя), round-robin интерлив; на ручном
 * рефреше порядок источников ротируется, а latest шагается страницей.
 * Единственный источник остаётся в силе — квота растягивается на весь ряд;
 * при скудном latest квота добирается popular'ом.
 */
class DiscoverySourceRowBuilder(
    private val catalog: DiscoverySourceCatalog,
    private val rowCap: Int = 20,
    private val sourceTimeoutMs: Long = SOURCE_FETCH_TIMEOUT_MS,
    private val maxConcurrency: Int = 4,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.SOURCE
    override var isDegraded: Boolean = false
        private set

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        isDegraded = false
        val ids = context.sourceIds.filter { it > 0 }
            .ifEmpty { listOf(context.sourceId).filter { it > 0 } }
        if (ids.isEmpty()) return emptyList()
        val rotation = (context.pageOffset - 1).coerceAtLeast(0) % ids.size
        val ordered = ids.drop(rotation) + ids.take(rotation)
        val policy = context.candidatePolicy
        if (policy.requiredGenres.isNotEmpty()) {
            val results = queryDiscoveryGenreSources(
                catalog = catalog,
                media = context.mediaType,
                sourceIds = ordered,
                genres = policy.requiredGenres.toList(),
                page = context.pageOffset,
                releaseStatuses = context.releaseStatuses,
                policy = policy,
                maxConcurrency = maxConcurrency,
                timeoutMs = GENRE_SOURCE_TIMEOUT_MS,
                hydrate = true,
                budget = context.verificationBudget,
            )
            isDegraded = results.any {
                it.failure != null || it.summary.outcome == GenreVerificationOutcome.INCOMPLETE
            }
            val merged = interleaveSourceParts(results.map { it.summary.items }, rowCap)
                .distinctBy { it.cleanTitle }
            val usefulVerification = results.any { it.summary.outcome != GenreVerificationOutcome.FAILED }
            if (merged.isEmpty() && results.isNotEmpty() && !usefulVerification) {
                throw IOException("required genre sources could not verify any metadata")
            }
            return policy.filterRowCandidates(merged, { it.genres }).take(rowCap)
        }
        val slotAllocations = allocateSourceSlots(ordered, context.sourceWeights, rowCap)
        val activeSources = ordered.filter { (slotAllocations[it] ?: 0) > 0 }
        if (activeSources.isEmpty()) return emptyList()

        val semaphore = Semaphore(maxConcurrency)
        val results = supervisorScope {
            ordered.map { id ->
                async {
                    val perSource = slotAllocations[id] ?: 0
                    if (perSource <= 0) {
                        return@async Triple(emptyList<DiscoveryRowItem>(), emptyList<DiscoveryRowItem>(), false)
                    }
                    semaphore.withPermit {
                        val latestQuota = perSource - perSource / 5
                        val latestDeferred = async {
                            runCatchingUnlessCancelled {
                                withTimeoutOrNull(sourceTimeoutMs) {
                                    catalog.latest(
                                        context.mediaType,
                                        id,
                                        page = context.pageOffset,
                                        releaseStatuses = context.releaseStatuses,
                                    )
                                }
                            }
                        }
                        val popularDeferred = async {
                            runCatchingUnlessCancelled {
                                withTimeoutOrNull(sourceTimeoutMs) {
                                    catalog.popular(
                                        context.mediaType,
                                        id,
                                        page = context.pageOffset,
                                        releaseStatuses = context.releaseStatuses,
                                    )
                                }
                            }
                        }

                        val latestResult = latestDeferred.await()
                        val popularResult = popularDeferred.await()

                        val latestFetched = latestResult.getOrNull().orEmpty()
                        val latestItems = latestFetched.take(latestQuota)
                        val latestTitles = latestItems.mapTo(HashSet()) { it.cleanTitle }

                        val popularFetched = popularResult.getOrNull().orEmpty()
                        val popularItems = popularFetched
                            .filterNot { it.cleanTitle in latestTitles }
                            .take(perSource - latestItems.size)

                        val popularFailed = popularResult.isFailure || popularResult.getOrNull() == null
                        val isFailed = popularFailed && latestItems.isEmpty()

                        val selected = latestItems + popularItems

                        val selectedTitles = selected.mapTo(HashSet()) { it.cleanTitle }
                        val surplus = (latestFetched + popularFetched)
                            .filterNot { it.cleanTitle in selectedTitles }
                            .distinctBy { it.cleanTitle }
                            .take(rowCap)

                        Triple(selected, surplus, isFailed)
                    }
                }
            }.awaitAll()
        }

        val failures = results.count { it.third }
        if (failures == activeSources.size) {
            throw IOException("all source catalog fetches failed")
        }

        val parts = results.map { it.first }
        val surplusParts = results.map { it.second }

        val selected = interleaveSourceParts(parts, rowCap).distinctBy { it.cleanTitle }
        val filteredSelected = context.candidatePolicy.filterRowCandidates(selected, { it.genres })
        if (filteredSelected.size >= rowCap) return filteredSelected
        val selectedTitles = filteredSelected.mapTo(HashSet()) { it.cleanTitle }
        val backfill = interleaveSourceParts(
            surplusParts.map { items -> items.filterNot { it.cleanTitle in selectedTitles } },
            rowCap,
        ).distinctBy { it.cleanTitle }
        val filteredBackfill = context.candidatePolicy.filterRowCandidates(backfill, { it.genres })
        return (filteredSelected + filteredBackfill).take(rowCap)
    }
}

/** Round-robin по частям (источникам): топ каждого источника виден сразу. */
internal fun interleaveSourceParts(
    parts: List<List<DiscoveryRowItem>>,
    cap: Int = 50,
): List<DiscoveryRowItem> {
    val queues = parts.filter { it.isNotEmpty() }.map { ArrayDeque(it) }
    val out = mutableListOf<DiscoveryRowItem>()
    var progressed = true
    while (out.size < cap && progressed) {
        progressed = false
        for (queue in queues) {
            if (out.size >= cap) break
            if (queue.isNotEmpty()) {
                out += queue.removeFirst()
                progressed = true
            }
        }
    }
    return out
}
