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

/** A provider with plenty of matching titles should fill a required row in one pass. */
private const val MAX_REQUIRED_EXTERNAL_PAGES = 3

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

    override var exhaustionEvidence: List<tachiyomi.domain.discovery.model.DiscoveryQueryEvidence>? = null
        private set

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        if (context.seeds.isEmpty() && context.feedbackSeeds.isEmpty()) {
            exhaustionEvidence = emptyList()
            return emptyList()
        }
        val mediaType = when (context.mediaType) {
            DiscoveryMediaType.ANIME -> SuggestionMediaType.ANIME
            DiscoveryMediaType.MANGA -> SuggestionMediaType.MANGA
            DiscoveryMediaType.NOVEL -> SuggestionMediaType.NOVEL
        }
        if (includeExternal) {
            var fullyFailedSeeds = 0
            var hasFailedSources = false
            val feedbackPerSeed = buildList {
                for (feedbackSeed in context.feedbackSeeds) {
                    if (context.verificationBudget?.isExpired() == true) break
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
                    add(
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
                        },
                    )
                }
            }
            val libraryPerSeed = buildList {
                for (seed in context.seeds) {
                    if (context.verificationBudget?.isExpired() == true) break
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
                    add(
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
                        },
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
            val hydrated = hydrateEmptyGenreItems(
                catalog = catalog,
                mediaType = context.mediaType,
                items = merged,
                budget = context.verificationBudget,
                maxDetails = 6,
            )
            return context.candidatePolicy.filterRowCandidates(hydrated, { it.genres })
        } else {
            if (catalog == null) return emptyList()
            val sourceIds = context.sourceIds.filter { it > 0 }
                .ifEmpty { listOf(context.sourceId).filter { it > 0 } }
            if (sourceIds.isEmpty()) return emptyList()

            val allSeeds = (
                context.feedbackSeeds.map { it.title to it.genres } +
                    context.seeds.map { it.title to it.genres }
                ).filter { it.second.isNotEmpty() }
            if (allSeeds.isEmpty()) {
                exhaustionEvidence = emptyList()
                return emptyList()
            }

            val policy = context.candidatePolicy
            if (policy.requiredGenres.isNotEmpty() && policy.isStrictRequired &&
                (context.verificationBudget == null || context.verificationBudget.isExpired())
            ) {
                exhaustionEvidence = emptyList()
                return emptyList()
            }

            val perSeed = supervisorScope {
                allSeeds.mapIndexed { seedIndex, (seedTitle, genres) ->
                    async {
                        val seedSources = if (sourceIds.size <= 2) {
                            sourceIds
                        } else {
                            val start = (seedIndex * 2) % sourceIds.size
                            listOf(sourceIds[start], sourceIds[(start + 1) % sourceIds.size])
                        }
                        seedSources.mapIndexed { sourceIdx, sourceId ->
                            async {
                                val queryGenres = if (policy.requiredGenres.isNotEmpty()) {
                                    if (policy.requiredMatchMode == RequiredMatchMode.ANY &&
                                        policy.requiredGenres.size > 1
                                    ) {
                                        val reqList = policy.requiredGenres.toList()
                                        listOf(reqList[(seedIndex + sourceIdx) % reqList.size])
                                    } else {
                                        policy.requiredGenres.take(3)
                                    }
                                } else {
                                    genres.take(3)
                                }
                                runCatchingUnlessCancelled {
                                    withTimeoutOrNull(SOURCE_FETCH_TIMEOUT_MS) {
                                        catalog.popularWithGenres(
                                            context.mediaType,
                                            sourceId,
                                            genres = queryGenres,
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
                .filterNot { it.isExcludedBy(context.exclusionKeys, context.mediaType) }
            val hydrated = hydrateEmptyGenreItems(
                catalog = catalog,
                mediaType = context.mediaType,
                items = merged,
                budget = context.verificationBudget,
                maxDetails = 6,
            )
            return policy.filterRowCandidates(hydrated, { it.genres })
        }
    }

    private suspend fun hydrateEmptyGenreItems(
        catalog: DiscoverySourceCatalog?,
        mediaType: DiscoveryMediaType,
        items: List<DiscoveryRowItem>,
        budget: DiscoveryVerificationBudget?,
        maxDetails: Int,
    ): List<DiscoveryRowItem> {
        if (catalog == null || budget == null || budget.isExpired()) return items
        if (items.none { it.genres.isEmpty() && it.sourceId != null }) return items
        val bySource = items.groupBy { it.sourceId }
        val hydratedByKey = HashMap<String, DiscoveryRowItem>()
        for ((srcId, group) in bySource) {
            if (srcId != null && srcId > 0L && !budget.isExpired()) {
                val page = runCatchingUnlessCancelled {
                    catalog.hydrateGenres(
                        mediaType = mediaType,
                        sourceId = srcId,
                        items = group,
                        budget = budget,
                        maxDetails = maxDetails,
                    )
                }.getOrNull()
                page?.items?.forEach { hydratedByKey[it.cleanTitle] = it }
            }
        }
        return items.map { hydratedByKey[it.cleanTitle] ?: it }
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
            }.let { items ->
                hydrateLatestIfPermitted(items, context.mediaType, preferredSourceId, context.verificationBudget)
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
                }.let { items ->
                    hydrateLatestIfPermitted(items, context.mediaType, preferredSourceId, context.verificationBudget)
                }.let { items -> policy.filterRowCandidates(items, { it.genres }) }
                if (fallbackSource.isNotEmpty()) {
                    return fallbackSource
                }
            }
        }

        return emptyList()
    }

    private suspend fun hydrateLatestIfPermitted(
        items: List<DiscoveryRowItem>,
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        budget: DiscoveryVerificationBudget?,
    ): List<DiscoveryRowItem> {
        if (budget == null || budget.isExpired() || items.none { it.genres.isEmpty() }) return items
        return runCatchingUnlessCancelled {
            catalog.hydrateGenres(
                mediaType = mediaType,
                sourceId = sourceId,
                items = items,
                budget = budget,
                maxDetails = 6,
            ).items
        }.getOrDefault(items)
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

    override var failedSourceIds: Set<Long> = emptySet()
        private set

    override var degradedSourceIds: Set<Long> = emptySet()
        private set

    override var exhaustionEvidence: List<tachiyomi.domain.discovery.model.DiscoveryQueryEvidence>? = null
        private set

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        isDegraded = false
        failedSourceIds = emptySet()
        degradedSourceIds = emptySet()
        val policy = context.candidatePolicy
        val activeProfile = resolveDiscoveryTasteProfile(context.tasteProfile, policy)
        if (activeProfile.isEmpty()) {
            exhaustionEvidence = emptyList()
            return emptyList()
        }
        val required = policy.requiredGenres.isNotEmpty()

        // A required query must not be narrowed by unrelated priority/library genres.
        val genreNames = if (policy.requiredGenres.isNotEmpty()) {
            policy.requiredGenres.toList()
        } else {
            (policy.priorityGenres.take(3) + activeProfile.map { it.first })
                .filterNot { policy.isForbidden(listOf(it)) }.distinct().take(6)
        }
        val sourceGenreNames = genreNames
        val splitExternalAny = required && policy.requiredMatchMode == RequiredMatchMode.ANY && genreNames.size > 1
        val trendingResult = if (includeExternal && !splitExternalAny) {
            runCatchingUnlessCancelled {
                trending.fetchByGenres(
                    context.mediaType,
                    genreNames,
                    sortProvider(),
                    page = context.pageOffset,
                    releaseStatuses = context.releaseStatuses,
                    matchMode = policy.requiredMatchMode,
                )
            }
        } else {
            null
        }

        val splitResults = if (includeExternal && splitExternalAny) {
            fetchSplitExternalGenres(context, genreNames, context.pageOffset, policy.requiredMatchMode)
        } else {
            emptyList()
        }
        val splitExternalFailed = splitResults.isNotEmpty() && splitResults.all { it.isFailure }

        var trendingItems = if (includeExternal && splitExternalAny) {
            splitResults.flatMap { it.getOrNull().orEmpty() }.distinctBy { it.cleanTitle }
        } else {
            val initial = trendingResult?.getOrNull().orEmpty()
            if (includeExternal && !required && genreNames.size > 1 && trendingResult?.isSuccess == true &&
                initial.isEmpty()
            ) {
                fetchSplitExternalGenres(context, genreNames, context.pageOffset, policy.requiredMatchMode)
                    .flatMap { it.getOrNull().orEmpty() }
                    .distinctBy { it.cleanTitle }
            } else {
                initial
            }
        }
        var filteredTrending = policy.filterRowCandidates(trendingItems, { it.genres })
        // Rich provider, rich row: a strict required query keeps paging while the
        // provider still exposes new matches. Fetching only page 2, and only when
        // page 1 was fully rejected, was what turned a rich genre into slow 9→12
        // accumulation across refreshes.
        val hasSuccessfulInitialExternal = (trendingResult?.isSuccess == true) ||
            (splitExternalAny && splitResults.any { it.isSuccess })
        if (includeExternal && required && hasSuccessfulInitialExternal) {
            var fetchedPages = 1
            while (
                filteredTrending.size < RICH_SOURCE_TARGET &&
                fetchedPages < MAX_REQUIRED_EXTERNAL_PAGES &&
                context.verificationBudget?.isExpired() != true
            ) {
                val nextPage = context.pageOffset + fetchedPages
                val nextItems = if (splitExternalAny) {
                    fetchSplitExternalGenres(context, genreNames, nextPage, policy.requiredMatchMode)
                        .flatMap { it.getOrNull().orEmpty() }
                } else {
                    runCatchingUnlessCancelled {
                        trending.fetchByGenres(
                            context.mediaType,
                            genreNames,
                            sortProvider(),
                            page = nextPage,
                            releaseStatuses = context.releaseStatuses,
                            matchMode = policy.requiredMatchMode,
                        )
                    }.getOrNull().orEmpty()
                }
                if (nextItems.isEmpty()) break
                val before = trendingItems.size
                trendingItems = (trendingItems + nextItems).distinctBy { it.cleanTitle }
                if (trendingItems.size == before) break
                filteredTrending = policy.filterRowCandidates(trendingItems, { it.genres })
                fetchedPages++
            }
        }

        val sourceIds = context.sourceIds.filter { it > 0 }
            .ifEmpty { listOf(context.sourceId).filter { it > 0 } }
        val shared = context.sharedGenreQuery
        val sourceResults = if (sourceIds.isNotEmpty()) {
            if (required && shared != null) {
                // SOURCE shares the same catalogue pass; TASTE takes the even
                // slice and SOURCE the odd one, so neither row starves the other.
                shared.results().map { it.sliceForRow(parity = 0) }
            } else {
                val rotation = (context.pageOffset - 1).coerceAtLeast(0) % sourceIds.size
                val ordered = sourceIds.drop(rotation) + sourceIds.take(rotation)
                // Required mode pays for verification; the ordinary taste path keeps
                // its previous cheap shape (six sources, no details hydration) unless budget allows.
                val toQuery = if (required) ordered else ordered.take(6)
                queryDiscoveryGenreSources(
                    catalog = catalog,
                    media = context.mediaType,
                    sourceIds = toQuery,
                    genres = sourceGenreNames,
                    page = context.pageOffset,
                    pages = context.harvestPages,
                    releaseStatuses = context.releaseStatuses,
                    policy = policy,
                    timeoutMs = if (required) {
                        genreSourceTimeoutMs(context.verificationBudget, toQuery.size, maxConcurrency = 4)
                    } else {
                        SOURCE_FETCH_TIMEOUT_MS
                    },
                    hydrate = required ||
                        (context.verificationBudget != null && !context.verificationBudget.isExpired()),
                    targetItems = 50,
                    budget = context.verificationBudget,
                    excludedKeys = context.exclusionKeys,
                )
            }
        } else {
            emptyList()
        }
        failedSourceIds = sourceResults.filter { it.failure != null }.map { it.sourceId }.toSet()
        degradedSourceIds = sourceResults.filter {
            it.failure == null && required && (
                it.summary.hasCoverageGap ||
                    it.stopReason == DiscoveryFillStopReason.PAGE_BUDGET_EXHAUSTED ||
                    it.stopReason == DiscoveryFillStopReason.PASS_BUDGET_EXHAUSTED
                )
        }.map { it.sourceId }.toSet()
        exhaustionEvidence = if (includeExternal) null else sourceResults.map { it.toExhaustionEvidence() }
        // In the cheap path cards without genres were never going to be hydrated,
        // so an "unattempted" count there is by design and not a degraded row.
        isDegraded = sourceResults.any {
            it.failure != null || (
                required && (
                    it.summary.hasCoverageGap ||
                        it.stopReason == DiscoveryFillStopReason.PAGE_BUDGET_EXHAUSTED ||
                        it.stopReason == DiscoveryFillStopReason.PASS_BUDGET_EXHAUSTED
                    )
                )
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
                score = boostedTasteScore(item.genres, activeProfile, policy.priorityGenres),
                genres = item.genres,
            )
        }
        val sourceParts = sourceResults.map { res ->
            res.summary.items.map { item ->
                val baseScore = 0.5 + item.score * 0.1
                val hasPriority = policy.priorityGenres.isNotEmpty() &&
                    (policy.evaluate(item.genres) as? CandidateVerdict.Admitted)?.hasPriorityBoost == true
                item.copy(
                    reason = item.genres.takeIf { it.isNotEmpty() }?.take(2)?.joinToString(", ")
                        ?: item.reason
                        ?: genreNames.take(2).joinToString(", "),
                    score = if (hasPriority) baseScore * 1.5 else baseScore,
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
            trendingResult?.isFailure ?: splitExternalFailed
        } else {
            sourcesFailed
        }
        if (filteredCombined.isEmpty() && anyFailed) {
            throw IOException("taste sources failed without results")
        }
        val fresh = if (policy.priorityGenres.isNotEmpty()) {
            val (boosted, normal) = filteredCombined.partition {
                (policy.evaluate(it.genres) as? CandidateVerdict.Admitted)?.hasPriorityBoost == true
            }
            (boosted + filterGenreClustering(normal, maxPerGenre = 6)).take(50)
        } else {
            filterGenreClustering(filteredCombined, maxPerGenre = 6).take(50)
        }
        return mergeExistingRowItems(
            existing = context.existingRowItems[DiscoveryRowType.TASTE].orEmpty(),
            fresh = fresh,
            policy = policy,
            cap = 50,
        )
    }

    private suspend fun fetchSplitExternalGenres(
        context: DiscoveryBuildContext,
        genreNames: List<String>,
        page: Int,
        matchMode: RequiredMatchMode,
    ): List<Result<List<DiscoveryTrendingItem>>> = supervisorScope {
        genreNames.take(3).map { genre ->
            async {
                runCatchingUnlessCancelled {
                    trending.fetchByGenres(
                        context.mediaType,
                        listOf(genre),
                        sortProvider(),
                        page = page,
                        releaseStatuses = context.releaseStatuses,
                        matchMode = matchMode,
                    )
                }
            }
        }.awaitAll()
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

    override var failedSourceIds: Set<Long> = emptySet()
        private set

    override var degradedSourceIds: Set<Long> = emptySet()
        private set

    override var exhaustionEvidence: List<tachiyomi.domain.discovery.model.DiscoveryQueryEvidence>? = null
        private set

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        isDegraded = false
        failedSourceIds = emptySet()
        degradedSourceIds = emptySet()
        val ids = context.sourceIds.filter { it > 0 }
            .ifEmpty { listOf(context.sourceId).filter { it > 0 } }
        if (ids.isEmpty()) return emptyList()
        val rotation = (context.pageOffset - 1).coerceAtLeast(0) % ids.size
        val ordered = ids.drop(rotation) + ids.take(rotation)
        val policy = context.candidatePolicy
        if (policy.requiredGenres.isNotEmpty()) {
            val shared = context.sharedGenreQuery
            val results = if (shared != null) {
                // TASTE ran the same required query; take its odd slice instead
                // of paying for a second catalogue pass only to be deduped away,
                // falling back to unsliced results when a source yields a single match.
                shared.results().map { res ->
                    val sliced = res.sliceForRow(parity = 1)
                    if (sliced.summary.items.isNotEmpty()) sliced else res
                }
            } else {
                queryDiscoveryGenreSources(
                    catalog = catalog,
                    media = context.mediaType,
                    sourceIds = ordered,
                    genres = policy.requiredGenres.toList(),
                    page = context.pageOffset,
                    pages = context.harvestPages,
                    releaseStatuses = context.releaseStatuses,
                    policy = policy,
                    maxConcurrency = maxConcurrency,
                    timeoutMs = genreSourceTimeoutMs(context.verificationBudget, ordered.size, maxConcurrency),
                    hydrate = true,
                    targetItems = rowCap,
                    budget = context.verificationBudget,
                    excludedKeys = context.exclusionKeys,
                )
            }
            failedSourceIds = results.filter { it.failure != null }.map { it.sourceId }.toSet()
            degradedSourceIds = results.filter {
                it.failure == null && (
                    it.summary.hasCoverageGap ||
                        it.stopReason == DiscoveryFillStopReason.PAGE_BUDGET_EXHAUSTED ||
                        it.stopReason == DiscoveryFillStopReason.PASS_BUDGET_EXHAUSTED
                    )
            }.map { it.sourceId }.toSet()
            exhaustionEvidence = results.map { it.toExhaustionEvidence() }
            isDegraded = results.any {
                it.failure != null || it.summary.hasCoverageGap ||
                    it.stopReason == DiscoveryFillStopReason.PAGE_BUDGET_EXHAUSTED ||
                    it.stopReason == DiscoveryFillStopReason.PASS_BUDGET_EXHAUSTED
            }
            val merged = interleaveSourceParts(results.map { it.summary.items }, rowCap)
                .distinctBy { it.cleanTitle }
            val usefulVerification = results.any { it.summary.outcome != GenreVerificationOutcome.FAILED }
            if (merged.isEmpty() && results.isNotEmpty() && !usefulVerification) {
                throw IOException("required genre sources could not verify any metadata")
            }
            return mergeExistingRowItems(
                existing = context.existingRowItems[DiscoveryRowType.SOURCE].orEmpty(),
                fresh = policy.filterRowCandidates(merged, { it.genres }).take(rowCap),
                policy = policy,
                cap = rowCap,
                sourceIds = ordered.toSet(),
            )
        }
        val slotAllocations = allocateSourceSlots(ordered, context.sourceWeights, rowCap)
        val activeSources = ordered.filter { (slotAllocations[it] ?: 0) > 0 }
        if (activeSources.isEmpty()) return emptyList()
        class Harvest(val id: Long) {
            val latest = mutableListOf<DiscoveryRowItem>()
            val popular = mutableListOf<DiscoveryRowItem>()
            var latestNext: Boolean? = true
            var popularNext: Boolean? = true
            var latestFailed = false
            var popularFailed = false
            var queried = false
            fun finished() = (latestNext == false || latestFailed) && (popularNext == false || popularFailed)
            fun hasFresh() = latest.isNotEmpty() || popular.isNotEmpty()
        }
        val states = activeSources.map { Harvest(it) }
        val semaphore = Semaphore(maxConcurrency)
        val pageCount = context.harvestPages.coerceAtLeast(1)
        for (offset in 0 until pageCount) {
            val active = states.filterNot { it.finished() }
            if (active.isEmpty() || context.verificationBudget?.isExpired() == true) break
            supervisorScope {
                active.map { state ->
                    async {
                        semaphore.withPermit {
                            // A source may wait for a permit past the shared deadline; a timeout
                            // computed before the wait would let it start work after expiry.
                            val timeout = minOf(
                                sourceTimeoutMs,
                                context.verificationBudget?.remainingMs() ?: sourceTimeoutMs,
                            )
                            if (timeout <= 0L) return@withPermit
                            val page = context.pageOffset + offset
                            state.queried = true
                            val latest = async {
                                if (state.latestNext == false ||
                                    state.latestFailed
                                ) {
                                    null
                                } else {
                                    runCatchingUnlessCancelled {
                                        withTimeoutOrNull(timeout) {
                                            catalog.latestPage(
                                                context.mediaType,
                                                state.id,
                                                page,
                                                context.releaseStatuses,
                                                context.verificationBudget,
                                            )
                                        }
                                    }
                                }
                            }
                            val popular = async {
                                if (state.popularNext == false ||
                                    state.popularFailed
                                ) {
                                    null
                                } else {
                                    runCatchingUnlessCancelled {
                                        withTimeoutOrNull(timeout) {
                                            catalog.popularPage(
                                                context.mediaType,
                                                state.id,
                                                page,
                                                context.releaseStatuses,
                                                context.verificationBudget,
                                                context.exclusionKeys,
                                            )
                                        }
                                    }
                                }
                            }
                            val latestResult = latest.await()
                            val popularResult = popular.await()
                            val budget = context.verificationBudget
                            val needsGenreHydration = budget != null && !budget.isExpired()
                            suspend fun filterAndHydrate(items: List<DiscoveryRowItem>): List<DiscoveryRowItem> {
                                val candidates = items
                                    .filterNot { it.isExcludedBy(context.exclusionKeys, context.mediaType) }
                                val hydrated = if (
                                    needsGenreHydration &&
                                    budget != null &&
                                    !budget.isExpired() &&
                                    candidates.any { it.genres.isEmpty() }
                                ) {
                                    runCatchingUnlessCancelled {
                                        catalog.hydrateGenres(
                                            mediaType = context.mediaType,
                                            sourceId = state.id,
                                            items = candidates,
                                            budget = budget,
                                            maxDetails = 8,
                                        ).items
                                    }.getOrDefault(candidates)
                                } else {
                                    candidates
                                }
                                return policy.filterRowCandidates(hydrated, { it.genres })
                            }
                            if (latestResult != null) {
                                val response = latestResult.getOrNull()
                                if (response == null) {
                                    state.latestFailed = true
                                } else {
                                    state.latestNext = response.hasNextPage
                                    state.latest += filterAndHydrate(response.items)
                                }
                            }
                            if (popularResult != null) {
                                val response = popularResult.getOrNull()
                                if (response == null) {
                                    state.popularFailed = true
                                } else {
                                    state.popularNext = response.hasNextPage
                                    state.popular += filterAndHydrate(response.items)
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
            val freshCount = states.flatMap { it.latest + it.popular }.distinctBy { it.cleanTitle }.size
            // A full row from one producer must not end another selected source's probe.
            if (freshCount >= rowCap && states.all { it.hasFresh() || it.finished() }) break
        }
        exhaustionEvidence = states.flatMap { state ->
            listOf("latest" to state.latestNext, "popular" to state.popularNext).map { (family, next) ->
                val failed = if (family == "latest") state.latestFailed else state.popularFailed
                tachiyomi.domain.discovery.model.DiscoveryQueryEvidence(
                    queryKey =
                    discoveryGenreQueryKey(
                        context.mediaType,
                        state.id,
                        emptyList(),
                        context.releaseStatuses,
                    ) + "|" +
                        family,
                    transportOk = state.queried && !failed,
                    rawEnd = next == false && !failed,
                    pending = 0,
                    unattempted = 0,
                    metadataFailures = 0,
                    budgetStopped = next != false && !failed,
                    duplicatePageStopped = false,
                )
            }
        }
        val failedStates = states.filter { !it.hasFresh() && (it.latestFailed || it.popularFailed) }
        failedSourceIds = failedStates.map { it.id }.toSet()
        if (failedStates.size == states.size) throw IOException("all source catalog fetches failed")
        val parts = states.map { state ->
            val quota = slotAllocations.getValue(state.id)
            val latest = state.latest.distinctBy { it.cleanTitle }.take(quota - quota / 5)
            val latestTitles = latest.mapTo(HashSet()) { it.cleanTitle }
            latest + state.popular.distinctBy { it.cleanTitle }
                .filterNot { it.cleanTitle in latestTitles }.take(quota - latest.size)
        }
        val selected = policy.filterRowCandidates(interleaveSourceParts(parts, rowCap), { it.genres })
        val selectedTitles = selected.mapTo(HashSet()) { it.cleanTitle }
        val surplus = states.map { state ->
            (state.latest + state.popular).filterNot { it.cleanTitle in selectedTitles }.distinctBy { it.cleanTitle }
        }
        val backfill = policy.filterRowCandidates(interleaveSourceParts(surplus, rowCap - selected.size), { it.genres })
        val fresh = (selected + backfill).take(rowCap)
        isDegraded = states.any { it.latestFailed || it.popularFailed } ||
            (fresh.size < rowCap && states.any { !it.finished() })
        return mergeExistingRowItems(
            existing = context.existingRowItems[DiscoveryRowType.SOURCE].orEmpty(),
            fresh = fresh,
            policy = policy,
            cap = rowCap,
            sourceIds = ordered.toSet(),
        )
    }
}

/**
 * Keeps compatible persisted cards in front of a thin refresh and appends only
 * genuinely new cards. Duplicate titles are dropped, and the result respects
 * the same cap as a fully fresh row.
 */
internal fun mergeExistingRowItems(
    existing: List<DiscoveryRowItem>,
    fresh: List<DiscoveryRowItem>,
    policy: DiscoveryCandidatePolicy,
    cap: Int,
    sourceIds: Set<Long>? = null,
): List<DiscoveryRowItem> {
    if (cap <= 0) return emptyList()
    val compatibleExisting = if (existing.isEmpty()) {
        emptyList()
    } else {
        val eligible = if (sourceIds == null) {
            existing
        } else {
            existing.filter { it.sourceId == null || it.sourceId in sourceIds }
        }
        policy.filterRowCandidates(eligible, { it.genres })
    }
    val seen = HashSet<String>()
    val merged = ArrayList<DiscoveryRowItem>(cap)
    for (item in compatibleExisting + fresh) {
        val keys = listOf(item.cleanTitle) + item.altCleanTitles
        if (keys.any { it in seen }) continue
        seen += keys
        merged += item
        if (merged.size >= cap) break
    }
    return merged
}

/** Round-robin по частям (источникам): топ каждого источника виден сразу. */
internal fun interleaveSourceParts(
    parts: List<List<DiscoveryRowItem>>,
    cap: Int = 50,
): List<DiscoveryRowItem> {
    val queues = parts.filter { it.isNotEmpty() }.map { ArrayDeque(it) }
    val out = mutableListOf<DiscoveryRowItem>()
    val seen = HashSet<String>()
    var progressed = true
    while (out.size < cap && progressed) {
        progressed = false
        for (queue in queues) {
            if (out.size >= cap) break
            while (queue.isNotEmpty()) {
                val candidate = queue.removeFirst()
                progressed = true
                if (!seen.add(candidate.cleanTitle)) continue
                out += candidate
                break
            }
        }
    }
    return out
}
