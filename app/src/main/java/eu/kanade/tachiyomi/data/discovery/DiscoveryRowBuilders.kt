package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
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
private const val LIKE_ANY_QUERY_CONCURRENCY = 3
internal const val MIN_NATIVE_LIKE_GENRE_OVERLAP = 4
private const val NATIVE_LIKE_RICH_GENRE_OVERLAP = 5
private const val NATIVE_LIKE_QUERY_GENRE_CAP = 5
private const val NATIVE_LIKE_HYDRATE_MAX_DETAILS = 15

/** A provider with plenty of matching titles should fill a required row in one pass. */
private const val MAX_REQUIRED_EXTERNAL_PAGES = 3

internal data class NativeSeedSimilarity(
    val sharedGenres: Int,
    val jaccard: Double,
) {
    val score: Double get() = sharedGenres * 10.0 + jaccard * 5.0
}

private fun canonicalGenreKeySet(genres: List<String>): Set<String> =
    genres.asSequence()
        .map { GenreMatcher.identityKey(it) }
        .filter { it.isNotEmpty() }
        .toSet()

/**
 * Verifies genuine genre-level similarity between a library seed and a native catalogue
 * candidate so "Similar to <title>" never labels unrelated works: requires empty-free genres
 * and >=5 shared genres when both titles expose rich (>=6) genre sets, or >=4 shared genres
 * for smaller 4-5 genre sets.
 */
internal fun verifyNativeSeedSimilarity(
    seedGenres: List<String>,
    candidateGenres: List<String>,
): NativeSeedSimilarity? {
    val seedKeys = canonicalGenreKeySet(seedGenres)
    val itemKeys = canonicalGenreKeySet(candidateGenres)
    if (seedKeys.size < MIN_NATIVE_LIKE_GENRE_OVERLAP || itemKeys.size < MIN_NATIVE_LIKE_GENRE_OVERLAP) {
        return null
    }
    val shared = seedKeys.intersect(itemKeys).size
    val requiredOverlap = if (minOf(seedKeys.size, itemKeys.size) >= 6) {
        NATIVE_LIKE_RICH_GENRE_OVERLAP
    } else {
        MIN_NATIVE_LIKE_GENRE_OVERLAP
    }
    if (shared < requiredOverlap) return null
    val union = seedKeys.union(itemKeys).size.coerceAtLeast(1)
    return NativeSeedSimilarity(
        sharedGenres = shared,
        jaccard = shared.toDouble() / union.toDouble(),
    )
}

/**
 * Sanity check for external LIKE recommendations: when both the seed and the external
 * item provide rich genre lists (>=3 genres), reject obvious junk matches with <2 shared
 * genres, while preserving items from providers without genre metadata (MAL, NU).
 */
internal fun verifyExternalSeedCompatibility(
    seedGenres: List<String>,
    candidateGenres: List<String>,
): Boolean {
    val seedKeys = canonicalGenreKeySet(seedGenres)
    val itemKeys = canonicalGenreKeySet(candidateGenres)
    if (seedKeys.size < 3 || itemKeys.size < 3) return true
    return seedKeys.intersect(itemKeys).size >= 2
}

/** Selects up to 5 seed genres for native catalogue filtering (retaining ALL required genres when set). */
internal fun selectNativeLikeQueryGenres(
    seedGenres: List<String>,
    policy: DiscoveryCandidatePolicy,
): List<String> {
    val distinctSeed = seedGenres
        .filter { it.isNotBlank() }
        .distinctBy { GenreMatcher.identityKey(it).ifEmpty { it.lowercase() } }
    if (policy.requiredGenres.isEmpty()) {
        return distinctSeed.take(NATIVE_LIKE_QUERY_GENRE_CAP)
    }
    return (policy.requiredGenres.toList() + distinctSeed)
        .filter { it.isNotBlank() }
        .distinctBy { GenreMatcher.identityKey(it).ifEmpty { it.lowercase() } }
        .take(maxOf(NATIVE_LIKE_QUERY_GENRE_CAP, policy.requiredGenres.size))
}

/** Кандидаты LIKE-сида: resolver дополняет сырой тайтл оригиналом из описания и нормализованными вариантами. */
internal fun parseDiscoveryReleaseStatus(
    raw: String?,
): tachiyomi.domain.discovery.model.DiscoveryReleaseStatus? {
    val norm = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        norm in setOf("releasing", "ongoing") -> tachiyomi.domain.discovery.model.DiscoveryReleaseStatus.ONGOING
        norm in setOf("finished", "released", "completed") ->
            tachiyomi.domain.discovery.model.DiscoveryReleaseStatus.FINISHED
        norm in setOf("not_yet_released", "anons", "announced") ->
            tachiyomi.domain.discovery.model.DiscoveryReleaseStatus.ANONS
        norm in setOf("hiatus", "on_hiatus", "paused") ->
            tachiyomi.domain.discovery.model.DiscoveryReleaseStatus.PAUSED
        else -> tachiyomi.domain.discovery.model.DiscoveryReleaseStatus.fromKey(norm)
    }
}

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
        isDegraded = false
        exhaustionEvidence = null
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
            var attemptedSeeds = 0
            var fullyFailedSeeds = 0
            var hasFailedSources = false
            var hasRawResults = false
            val feedbackGroups = buildList {
                for (feedbackSeed in context.feedbackSeeds) {
                    if (context.verificationBudget?.isExpired() == true) break
                    attemptedSeeds++
                    val suggestionSeed = SuggestionSeed(
                        mediaType = mediaType,
                        primaryTitle = feedbackSeed.title,
                        candidateTitles = SuggestionTitleResolver.resolveCandidates(feedbackSeed.title, null),
                        description = null,
                        author = null,
                        genres = feedbackSeed.genres.ifEmpty { null },
                    )
                    val result = withBudgetTimeoutOrNull(context.verificationBudget) {
                        suggestionCoordinator.fetchSuggestions(
                            suggestionSeed,
                            limit = 25,
                            releaseStatuses = context.releaseStatuses,
                        )
                    } ?: break
                    if (result.items.isNotEmpty()) hasRawResults = true
                    if (result.failedSources > 0) {
                        hasFailedSources = true
                    }
                    if (result.items.isEmpty() && result.attemptedSources > 0 &&
                        result.failedSources == result.attemptedSources
                    ) {
                        fullyFailedSeeds++
                    }
                    add(
                        SeedCandidateGroup(
                            seedTitle = feedbackSeed.title,
                            seedGenres = feedbackSeed.genres,
                            items = result.items.map { item ->
                                val cleanTitle = normalizeDiscoveryTitle(item.title)
                                val altCleanTitles = item.searchQueries
                                    .asSequence()
                                    .map(::normalizeDiscoveryTitle)
                                    .filter { it.isNotBlank() && it != cleanTitle }
                                    .distinct()
                                    .toList()
                                DiscoveryRowItem(
                                    title = item.title,
                                    cleanTitle = cleanTitle,
                                    coverUrl = item.thumbnailUrl,
                                    reason = null,
                                    seedTitle = feedbackSeed.title,
                                    provider = item.providerName,
                                    score = item.bestMatchScoreFor(suggestionSeed).toDouble(),
                                    genres = item.genres,
                                    altCleanTitles = altCleanTitles,
                                    releaseStatus = parseDiscoveryReleaseStatus(item.releaseStatus),
                                )
                            },
                        ),
                    )
                }
            }
            val libraryGroups = buildList {
                for (seed in context.seeds) {
                    if (context.verificationBudget?.isExpired() == true) break
                    attemptedSeeds++
                    val suggestionSeed = SuggestionSeed(
                        mediaType = mediaType,
                        primaryTitle = seed.title,
                        candidateTitles = likeSeedCandidateTitles(seed),
                        description = seed.description,
                        author = seed.author,
                        genres = seed.genres.ifEmpty { null },
                    )
                    val result = withBudgetTimeoutOrNull(context.verificationBudget) {
                        suggestionCoordinator.fetchSuggestions(
                            suggestionSeed,
                            limit = 25,
                            releaseStatuses = context.releaseStatuses,
                        )
                    } ?: break
                    if (result.items.isNotEmpty()) hasRawResults = true
                    if (result.failedSources > 0) {
                        hasFailedSources = true
                    }
                    if (result.items.isEmpty() && result.attemptedSources > 0 &&
                        result.failedSources == result.attemptedSources
                    ) {
                        fullyFailedSeeds++
                    }
                    add(
                        SeedCandidateGroup(
                            seedTitle = seed.title,
                            seedGenres = seed.genres,
                            items = result.items.map { item ->
                                val cleanTitle = normalizeDiscoveryTitle(item.title)
                                val altCleanTitles = item.searchQueries
                                    .asSequence()
                                    .map(::normalizeDiscoveryTitle)
                                    .filter { it.isNotBlank() && it != cleanTitle }
                                    .distinct()
                                    .toList()
                                DiscoveryRowItem(
                                    title = item.title,
                                    cleanTitle = cleanTitle,
                                    coverUrl = item.thumbnailUrl,
                                    // Локализованный текст «Похоже на „X“» композится на рендере из seedTitle.
                                    reason = null,
                                    seedTitle = seed.title,
                                    provider = item.providerName,
                                    score = item.bestMatchScoreFor(suggestionSeed).toDouble(),
                                    genres = item.genres,
                                    altCleanTitles = altCleanTitles,
                                    releaseStatus = parseDiscoveryReleaseStatus(item.releaseStatus),
                                )
                            },
                        ),
                    )
                }
            }
            val filteredGroups = (feedbackGroups + libraryGroups).map {
                it.copy(items = context.eligibleCandidates(rowType, it.items))
            }
            val hasEligibleResults = filteredGroups.any { it.items.isNotEmpty() }
            if (hasFailedSources) isDegraded = true
            if (!hasEligibleResults && !hasRawResults && attemptedSeeds > 0 && fullyFailedSeeds == attemptedSeeds) {
                throw IOException("all suggestion providers failed for every seed")
            }
            return prepareSeedResults(filteredGroups, context)
        } else {
            if (catalog == null) return emptyList()
            val sourceIds = context.nativeQuerySourceIds
            if (sourceIds.isEmpty()) return emptyList()

            val allSeeds = (
                context.feedbackSeeds.map { it.title to it.genres } +
                    context.seeds.map { it.title to it.genres }
                ).filter { canonicalGenreKeySet(it.second).size >= MIN_NATIVE_LIKE_GENRE_OVERLAP }
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

            val anyQueryPermits = Semaphore(LIKE_ANY_QUERY_CONCURRENCY)
            val budget = context.verificationBudget
            val seedGroups = supervisorScope {
                allSeeds.mapIndexed { seedIndex, (seedTitle, genres) ->
                    async {
                        val seedSources = if (sourceIds.size <= 2) {
                            sourceIds
                        } else {
                            val start = (seedIndex * 2) % sourceIds.size
                            listOf(sourceIds[start], sourceIds[(start + 1) % sourceIds.size])
                        }
                        val items = seedSources.map { sourceId ->
                            async {
                                if (policy.requiredMatchMode == RequiredMatchMode.ANY &&
                                    policy.requiredGenres.size > 1
                                ) {
                                    fetchNativeAnyGenres(
                                        catalog = catalog,
                                        context = context,
                                        sourceId = sourceId,
                                        genres = policy.requiredGenres.toList(),
                                        permits = anyQueryPermits,
                                    )
                                } else {
                                    val queryGenres = selectNativeLikeQueryGenres(genres, policy)
                                    val primary = fetchNativeSeedPage(
                                        catalog = catalog,
                                        context = context,
                                        sourceId = sourceId,
                                        genres = queryGenres,
                                        budget = budget,
                                    )
                                    if (primary.isEmpty() && policy.requiredGenres.isEmpty() && queryGenres.size >= 4) {
                                        // Fallback on narrow 4-5 checkbox conjunctions: narrow to top 3 and
                                        // still enforce strict 4-5 genre verification in prepareSeedResults.
                                        fetchNativeSeedPage(
                                            catalog = catalog,
                                            context = context,
                                            sourceId = sourceId,
                                            genres = queryGenres.take(3),
                                            budget = budget,
                                        )
                                    } else {
                                        primary
                                    }
                                }
                            }
                        }.awaitAll().flatten().map { item ->
                            item.copy(
                                seedTitle = seedTitle,
                                reason = null,
                            )
                        }
                        SeedCandidateGroup(seedTitle = seedTitle, seedGenres = genres, items = items)
                    }
                }.awaitAll()
            }
            return prepareSeedResults(
                seedGroups.map { it.copy(items = context.eligibleCandidates(rowType, it.items)) },
                context,
            )
        }
    }

    private data class SeedCandidateGroup(
        val seedTitle: String,
        val seedGenres: List<String>,
        val items: List<DiscoveryRowItem>,
    )

    private suspend fun fetchNativeSeedPage(
        catalog: DiscoverySourceCatalog,
        context: DiscoveryBuildContext,
        sourceId: Long,
        genres: List<String>,
        budget: DiscoveryVerificationBudget?,
    ): List<DiscoveryRowItem> {
        val remainingMs = budget?.remainingMs() ?: SOURCE_FETCH_TIMEOUT_MS
        if (remainingMs <= 0L) return emptyList()
        val timeoutMs = minOf(SOURCE_FETCH_TIMEOUT_MS, remainingMs)
        return runCatchingUnlessCancelled {
            withHardTimeoutOrNull(timeoutMs) {
                if (budget == null) {
                    catalog.popularWithGenres(
                        context.mediaType,
                        sourceId,
                        genres = genres,
                        page = 1,
                        releaseStatuses = context.releaseStatuses,
                    )
                } else {
                    catalog.popularWithGenresAllocated(
                        context.mediaType,
                        sourceId,
                        genres = genres,
                        page = 1,
                        releaseStatuses = context.releaseStatuses,
                        budget = budget,
                        maxDetails = minOf(NATIVE_LIKE_HYDRATE_MAX_DETAILS, budget.remainingDetails),
                        excludedKeys = context.exclusionKeys,
                    ).items
                }
            }
        }.getOrNull().orEmpty()
    }

    private suspend fun prepareSeedResults(
        groups: List<SeedCandidateGroup>,
        context: DiscoveryBuildContext,
    ): List<DiscoveryRowItem> {
        val policy = context.candidatePolicy
        val needsTierSort = policy.requiredGenres.isNotEmpty() || policy.priorityGenres.isNotEmpty()
        val representatives = seedResultRepresentatives(groups.map { it.items }).values.toList()
        val hydrateCap = if (includeExternal) 6 else NATIVE_LIKE_HYDRATE_MAX_DETAILS
        val hydrated = hydrateEmptyGenreItems(
            catalog,
            context.mediaType,
            representatives,
            context.verificationBudget,
            hydrateCap,
        )
        // Admission precedes the per-seed quota; preserve required/priority tier order before perSeedCap cuts.
        val admitted = context.admittedCandidates(rowType, hydrated).associateBy { it.cleanTitle }
        val eligiblePerSeed = groups.map { group ->
            val mapped = group.items.mapNotNull { original ->
                val resolved = admitted[original.cleanTitle] ?: return@mapNotNull null
                if (!includeExternal) {
                    val similarity = verifyNativeSeedSimilarity(group.seedGenres, resolved.genres)
                        ?: return@mapNotNull null
                    resolved.copy(score = similarity.score, seedTitle = group.seedTitle)
                } else {
                    if (!verifyExternalSeedCompatibility(group.seedGenres, resolved.genres)) {
                        return@mapNotNull null
                    }
                    resolved.copy(score = original.score, seedTitle = group.seedTitle)
                }
            }
            if (!needsTierSort) {
                mapped
            } else {
                mapped.sortedWith(
                    compareByDescending<DiscoveryRowItem> {
                        (policy.evaluate(it.genres) as? CandidateVerdict.Admitted)?.matchesRequired == true
                    }.thenByDescending { item ->
                        if (policy.requiredGenres.isNotEmpty() && !policy.isStrictRequired) {
                            policy.countRequiredMatchTier(item.genres)
                        } else {
                            0
                        }
                    }.thenByDescending {
                        (policy.evaluate(it.genres) as? CandidateVerdict.Admitted)?.hasPriorityBoost == true
                    },
                )
            }
        }
        return context.admittedCandidates(rowType, mergeSeedResults(eligiblePerSeed))
    }

    private suspend fun fetchNativeAnyGenres(
        catalog: DiscoverySourceCatalog,
        context: DiscoveryBuildContext,
        sourceId: Long,
        genres: List<String>,
        permits: Semaphore,
    ): List<DiscoveryRowItem> = supervisorScope {
        val budget = context.verificationBudget
        val timeoutMs = minOf(SOURCE_FETCH_TIMEOUT_MS, budget?.remainingMs() ?: SOURCE_FETCH_TIMEOUT_MS)
        if (timeoutMs <= 0L) return@supervisorScope emptyList()
        val waves = genres.chunked(LIKE_ANY_QUERY_CONCURRENCY)
        // Each child owns a distinct slot; structured completion precedes reading the results.
        // Keep finished queries even if another query owns the end of the time window.
        val completed = arrayOfNulls<List<DiscoveryRowItem>>(genres.size)
        withTimeoutOrNull(timeoutMs) {
            for ((waveIndex, wave) in waves.withIndex()) {
                coroutineContext.ensureActive()
                val remainingMs = budget?.remainingMs() ?: timeoutMs
                if (remainingMs <= 0L) break
                val waveTimeoutMs = (minOf(timeoutMs, remainingMs) / (waves.size - waveIndex)).coerceAtLeast(1L)
                wave.mapIndexed { index, genre ->
                    async {
                        val items = runCatchingUnlessCancelled {
                            withHardTimeoutOrNull(waveTimeoutMs) {
                                permits.withPermit {
                                    coroutineContext.ensureActive()
                                    if (budget?.isExpired() == true) return@withPermit emptyList()
                                    if (budget == null) {
                                        catalog.popularWithGenres(
                                            context.mediaType,
                                            sourceId,
                                            genres = listOf(genre),
                                            page = 1,
                                            releaseStatuses = context.releaseStatuses,
                                        )
                                    } else {
                                        // Fan-out must not multiply uncharged status/details lookups.
                                        catalog.popularWithGenresAllocated(
                                            context.mediaType,
                                            sourceId,
                                            genres = listOf(genre),
                                            page = 1,
                                            releaseStatuses = context.releaseStatuses,
                                            budget = budget,
                                            maxDetails = 6,
                                        ).items
                                    }
                                }
                            }
                        }.getOrNull().orEmpty()
                        completed[waveIndex * LIKE_ANY_QUERY_CONCURRENCY + index] = items
                    }
                }.awaitAll()
            }
        }
        completed.filterNotNull().flatten()
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
        val hydratedByKey = HashMap<Triple<Long?, String?, String>, DiscoveryRowItem>()
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
                page?.items?.forEach { hydratedByKey[Triple(it.sourceId, it.sourceUrl, it.cleanTitle)] = it }
            }
        }
        return items.map { hydratedByKey[Triple(it.sourceId, it.sourceUrl, it.cleanTitle)] ?: it }
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
    override var isDegraded: Boolean = false
        private set

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        isDegraded = false
        val effectiveSort = when (context.mediaType) {
            DiscoveryMediaType.ANIME -> sortProvider()
            // Для манги и новелл запрашиваем реальные тренды (TRENDING_DESC),
            // чтобы не дублировать статический all-time топ ряда TASTE
            DiscoveryMediaType.MANGA, DiscoveryMediaType.NOVEL -> TrendSort.TRENDING
        }
        // C1: приоритет — топ-взвешенный источник библиотеки, иначе lastUsed/fallback.
        val preferredSourceId = context.nativeQuerySourceIds.firstOrNull() ?: -1L
        val policy = context.candidatePolicy

        val fromSource = if (context.mediaType == DiscoveryMediaType.NOVEL && preferredSourceId > 0) {
            runCatchingUnlessCancelled {
                withBudgetTimeoutOrNull(context.verificationBudget) {
                    catalog.latest(
                        context.mediaType,
                        preferredSourceId,
                        page = context.pageOffset,
                        releaseStatuses = context.releaseStatuses,
                    )
                }.orEmpty()
            }.getOrNull().orEmpty().map { item ->
                item.copy(
                    reason = "source",
                    score = 0.0,
                )
            }.let { items ->
                hydrateLatestIfPermitted(items, context.mediaType, preferredSourceId, context.verificationBudget)
            }.let { items -> context.admittedCandidates(rowType, items) }
        } else {
            emptyList()
        }

        val trendingResult = runCatchingUnlessCancelled {
            withBudgetTimeoutOrNull(context.verificationBudget) {
                trending.fetch(
                    mediaType = context.mediaType,
                    season = seasonProvider(),
                    sort = effectiveSort,
                    page = context.pageOffset,
                    releaseStatuses = context.releaseStatuses,
                )
            } ?: throw IOException("trend provider exceeded shared budget")
        }
        val fromTrending = trendingResult.getOrNull().orEmpty()
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
                    releaseStatus = parseDiscoveryReleaseStatus(item.releaseStatus),
                )
            }.let { items -> context.admittedCandidates(rowType, items) }

        if (context.releaseStatuses.isNotEmpty()) {
            logcat {
                "[Discovery] trend row media=${context.mediaType} trending=${fromTrending.size} " +
                    "sourceLatest=${fromSource.size} statuses=${context.releaseStatuses}"
            }
        }

        // Приоритетные жанры ранжируются единым within-row ranker'ом в policy.filterRowCandidates.

        if (context.mediaType == DiscoveryMediaType.NOVEL) {
            val combinedNovels = (fromSource + fromTrending).distinctBy { it.cleanTitle }
            val filteredNovels = context.admittedCandidates(rowType, combinedNovels)
            if (filteredNovels.isNotEmpty()) {
                if (trendingResult.isFailure) isDegraded = true
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
                    withBudgetTimeoutOrNull(context.verificationBudget) {
                        catalog.latest(
                            context.mediaType,
                            preferredSourceId,
                            page = context.pageOffset,
                            releaseStatuses = context.releaseStatuses,
                        )
                    }.orEmpty()
                }.getOrNull().orEmpty().map { item ->
                    item.copy(
                        reason = "source",
                        score = 0.0,
                    )
                }.let { items ->
                    hydrateLatestIfPermitted(items, context.mediaType, preferredSourceId, context.verificationBudget)
                }.let { items -> context.admittedCandidates(rowType, items) }
                if (fallbackSource.isNotEmpty()) {
                    if (trendingResult.isFailure) isDegraded = true
                    return fallbackSource
                }
            }
        }

        trendingResult.exceptionOrNull()?.let { throw it }
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

    override var successfulSourceIds: Set<Long> = emptySet()
        private set

    override var exhaustionEvidence: List<tachiyomi.domain.discovery.model.DiscoveryQueryEvidence>? = null
        private set

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        isDegraded = false
        failedSourceIds = emptySet()
        degradedSourceIds = emptySet()
        successfulSourceIds = emptySet()
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
        val externalMatchMode = if (required) policy.requiredMatchMode else RequiredMatchMode.ANY
        val nativeMatchMode = if (required) policy.requiredMatchMode else RequiredMatchMode.ALL
        val splitExternalAny = externalMatchMode == RequiredMatchMode.ANY && genreNames.size > 1
        val externalBudget = if (required) {
            context.verificationBudget
        } else {
            context.verificationBudget?.takeUnless { it.isExpired() }
        }
        val trendingResult = if (includeExternal && !splitExternalAny) {
            runCatchingUnlessCancelled {
                withBudgetTimeoutOrNull(externalBudget) {
                    trending.fetchByGenres(
                        context.mediaType,
                        genreNames,
                        sortProvider(),
                        page = context.pageOffset,
                        releaseStatuses = context.releaseStatuses,
                        matchMode = externalMatchMode,
                    )
                } ?: throw IOException("external genre query exceeded shared budget")
            }
        } else {
            null
        }

        val queriedGenresByTitle = LinkedHashMap<String, MutableList<String>>()
        fun recordSplit(
            results: List<Pair<String, Result<List<DiscoveryTrendingItem>>>>,
        ): List<DiscoveryTrendingItem> = buildList {
            for ((genre, result) in results) {
                val items = result.getOrNull().orEmpty()
                for (item in items) {
                    queriedGenresByTitle.getOrPut(item.cleanTitle) { mutableListOf() }.add(genre)
                    add(item)
                }
            }
        }

        val splitResults = if (includeExternal && splitExternalAny) {
            fetchSplitExternalGenres(context, genreNames, context.pageOffset, externalMatchMode)
        } else {
            emptyList()
        }
        val splitExternalFailed = splitResults.isNotEmpty() && splitResults.all { (_, r) -> r.isFailure }

        var trendingItems = if (includeExternal && splitExternalAny) {
            recordSplit(splitResults).distinctBy { it.cleanTitle }
        } else {
            val initial = trendingResult?.getOrNull().orEmpty()
            if (includeExternal && !required && genreNames.size > 1 && trendingResult?.isSuccess == true &&
                initial.isEmpty()
            ) {
                recordSplit(
                    fetchSplitExternalGenres(context, genreNames.take(3), context.pageOffset, externalMatchMode),
                ).distinctBy { it.cleanTitle }
            } else {
                if (genreNames.size == 1) {
                    val onlyGenre = genreNames.single()
                    initial.forEach {
                        queriedGenresByTitle.getOrPut(it.cleanTitle) { mutableListOf() }.add(onlyGenre)
                    }
                }
                initial
            }
        }
        var filteredTrending = trendingItems.filter { policy.evaluate(it.genres) is CandidateVerdict.Admitted }
        var matchedTrendingCount = if (!required || policy.isStrictRequired) {
            filteredTrending.size
        } else {
            filteredTrending.count {
                (policy.evaluate(it.genres) as? CandidateVerdict.Admitted)?.matchesRequired == true
            }
        }
        // Rich provider, rich row: a required query keeps paging while the
        // provider still exposes new matches (in BEST_EFFORT don't stop early on Page-1 non-matches).
        val hasSuccessfulInitialExternal = (trendingResult?.isSuccess == true) ||
            (splitExternalAny && splitResults.any { (_, r) -> r.isSuccess })
        if (includeExternal && required && hasSuccessfulInitialExternal) {
            var fetchedPages = 1
            while (
                matchedTrendingCount < RICH_SOURCE_TARGET &&
                fetchedPages < MAX_REQUIRED_EXTERNAL_PAGES &&
                context.verificationBudget?.isExpired() != true
            ) {
                val nextPage = context.pageOffset + fetchedPages
                val nextItems = if (splitExternalAny) {
                    recordSplit(fetchSplitExternalGenres(context, genreNames, nextPage, externalMatchMode))
                } else {
                    val fetched = runCatchingUnlessCancelled {
                        withBudgetTimeoutOrNull(context.verificationBudget) {
                            trending.fetchByGenres(
                                context.mediaType,
                                genreNames,
                                sortProvider(),
                                page = nextPage,
                                releaseStatuses = context.releaseStatuses,
                                matchMode = externalMatchMode,
                            )
                        }.orEmpty()
                    }.getOrNull().orEmpty()
                    if (genreNames.size == 1) {
                        val onlyGenre = genreNames.single()
                        fetched.forEach {
                            queriedGenresByTitle.getOrPut(it.cleanTitle) { mutableListOf() }.add(onlyGenre)
                        }
                    }
                    fetched
                }
                if (nextItems.isEmpty()) break
                val before = trendingItems.size
                trendingItems = (trendingItems + nextItems).distinctBy { it.cleanTitle }
                if (trendingItems.size == before) break
                filteredTrending = trendingItems.filter { policy.evaluate(it.genres) is CandidateVerdict.Admitted }
                matchedTrendingCount = if (policy.isStrictRequired) {
                    filteredTrending.size
                } else {
                    filteredTrending.count {
                        (policy.evaluate(it.genres) as? CandidateVerdict.Admitted)?.matchesRequired == true
                    }
                }
                fetchedPages++
            }
        }

        val sourceIds = context.nativeQuerySourceIds
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
                // its previous cheap shape (six sources, no details hydration).
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
                    hydrate = required,
                    targetItems = 50,
                    budget = context.verificationBudget,
                    excludedKeys = context.exclusionKeys,
                    matchMode = nativeMatchMode,
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
        successfulSourceIds = sourceResults.filter { it.canRewardReliability }.map { it.sourceId }.toSet()
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
        val tasteMatchBasis = buildList {
            addAll(activeProfile)
            val seen = activeProfile.mapTo(HashSet()) { GenreMatcher.identityKey(it.first) }
            (policy.priorityGenres + policy.requiredGenres).forEach { genre ->
                val key = GenreMatcher.identityKey(genre)
                if (key.isNotEmpty() && seen.add(key)) add(genre to 1.0)
            }
        }
        val fromTrending = filteredTrending.map { item ->
            val matchedFromItem = if (item.genres.isNotEmpty()) {
                matchedGenres(item.genres, activeProfile).ifEmpty { matchedGenres(item.genres, tasteMatchBasis) }
            } else {
                emptyList()
            }
            val reasonGenres = when {
                matchedFromItem.isNotEmpty() -> matchedFromItem
                item.genres.isNotEmpty() -> emptyList()
                else -> queriedGenresByTitle[item.cleanTitle].orEmpty().distinct().take(2)
            }
            DiscoveryRowItem(
                title = item.title,
                cleanTitle = item.cleanTitle,
                altCleanTitles = listOfNotNull(
                    item.altTitle?.let {
                        normalizeDiscoveryTitle(it)
                    },
                ).filter { it.isNotBlank() },
                coverUrl = item.coverUrl,
                reason = reasonGenres.takeIf { it.isNotEmpty() }?.joinToString(", "),
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
                val matchedSourceGenres = if (item.genres.isNotEmpty()) {
                    matchedGenres(item.genres, activeProfile)
                        .ifEmpty { matchedGenres(item.genres, tasteMatchBasis) }
                } else {
                    emptyList()
                }
                val resolvedReason = when {
                    matchedSourceGenres.isNotEmpty() -> matchedSourceGenres.joinToString(", ")
                    item.genres.isNotEmpty() -> item.reason?.takeIf { it.isNotBlank() }
                    !item.reason.isNullOrBlank() -> item.reason
                    genreNames.size == 1 -> genreNames.single()
                    else -> null
                }
                item.copy(
                    reason = resolvedReason,
                    score = if (hasPriority) baseScore * 1.5 else baseScore,
                )
            }
        }
        val fromSource = if (sourceParts.size > 1) {
            interleaveSourceParts(sourceParts, cap = 50).distinctBy { it.cleanTitle }
        } else {
            sourceParts.flatten()
        }.let { items -> context.admittedCandidates(rowType, items) }
        val combined = if (fromTrending.isEmpty()) fromSource else mergeNormalized(fromTrending, fromSource)
        val filteredCombined = context.admittedCandidates(rowType, combined)
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
        val clusterAndBoost: (List<DiscoveryRowItem>) -> List<DiscoveryRowItem> = { list ->
            if (policy.priorityGenres.isNotEmpty()) {
                val (boosted, normal) = list.partition {
                    (policy.evaluate(it.genres) as? CandidateVerdict.Admitted)?.hasPriorityBoost == true
                }
                boosted + filterGenreClustering(normal, maxPerGenre = 6)
            } else {
                filterGenreClustering(list, maxPerGenre = 6)
            }
        }
        val fresh = if (required && !policy.isStrictRequired) {
            val (exact, nonExact) = filteredCombined.partition {
                (policy.evaluate(it.genres) as? CandidateVerdict.Admitted)?.matchesRequired == true
            }
            val (partial, fallback) = nonExact.partition { policy.countRequiredMatchTier(it.genres) > 0 }
            val orderedPartial = partial.sortedByDescending { policy.countRequiredMatchTier(it.genres) }
            (clusterAndBoost(exact) + clusterAndBoost(orderedPartial) + clusterAndBoost(fallback)).take(50)
        } else {
            clusterAndBoost(filteredCombined).take(50)
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
    ): List<Pair<String, Result<List<DiscoveryTrendingItem>>>> = supervisorScope {
        val waves = genreNames.chunked(3)
        val budget = context.verificationBudget
        val results = ArrayList<Pair<String, Result<List<DiscoveryTrendingItem>>>>(genreNames.size)
        for ((waveIndex, genres) in waves.withIndex()) {
            coroutineContext.ensureActive()
            val remainingMs = budget?.remainingMs()
            if (remainingMs == 0L) break
            // Reserve time for later genres instead of letting slow head queries own the whole pass.
            val waveTimeoutMs = remainingMs?.let { (it / (waves.size - waveIndex)).coerceAtLeast(1L) }
            val waveResults = genres.map { genre ->
                async {
                    genre to runCatchingUnlessCancelled {
                        coroutineContext.ensureActive()
                        val fetch = suspend {
                            trending.fetchByGenres(
                                context.mediaType,
                                listOf(genre),
                                sortProvider(),
                                page = page,
                                releaseStatuses = context.releaseStatuses,
                                matchMode = matchMode,
                            )
                        }
                        if (waveTimeoutMs == null) {
                            fetch()
                        } else {
                            // A scheduled coroutine may start after the wave's original budget snapshot.
                            val remaining = budget?.remainingMs() ?: waveTimeoutMs
                            withHardTimeoutOrNull(minOf(waveTimeoutMs, remaining)) {
                                fetch()
                            } ?: throw IOException("external genre query exceeded its shared budget")
                        }
                    }
                }
            }.awaitAll()
            results += waveResults
        }
        results
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
    private val maxConcurrency: Int = 6,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.SOURCE
    override var isDegraded: Boolean = false
        private set

    override var failedSourceIds: Set<Long> = emptySet()
        private set

    override var degradedSourceIds: Set<Long> = emptySet()
        private set

    override var successfulSourceIds: Set<Long> = emptySet()
        private set

    override var exhaustionEvidence: List<tachiyomi.domain.discovery.model.DiscoveryQueryEvidence>? = null
        private set

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        isDegraded = false
        failedSourceIds = emptySet()
        degradedSourceIds = emptySet()
        successfulSourceIds = emptySet()
        val ids = context.nativeQuerySourceIds
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
                    timeoutMs = minOf(
                        genreSourceTimeoutMs(context.verificationBudget, ordered.size, maxConcurrency),
                        WAVE_SOURCE_TIMEOUT_CAP_MS,
                    ),
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
            successfulSourceIds = results.filter { it.canRewardReliability }.map { it.sourceId }.toSet()
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
                fresh = context.admittedCandidates(rowType, merged).take(rowCap),
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
            var successfulFetches = 0
            var metadataIncomplete = false
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
                                        withHardTimeoutOrNull(timeout) {
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
                                        withHardTimeoutOrNull(timeout) {
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
                                val missingGenres = candidates.any { it.genres.isEmpty() }
                                val hydrated = if (
                                    needsGenreHydration &&
                                    budget != null &&
                                    !budget.isExpired() &&
                                    missingGenres
                                ) {
                                    val page = runCatchingUnlessCancelled {
                                        catalog.hydrateGenres(
                                            mediaType = context.mediaType,
                                            sourceId = state.id,
                                            items = candidates,
                                            budget = budget,
                                            maxDetails = 8,
                                        )
                                    }.getOrNull()
                                    if (page == null || page.unattempted > 0 || page.pending.isNotEmpty() ||
                                        page.metadataUnavailable > 0 || page.transientFailures > 0
                                    ) {
                                        state.metadataIncomplete = true
                                    }
                                    page?.items ?: candidates
                                } else {
                                    if (budget != null && missingGenres) state.metadataIncomplete = true
                                    candidates
                                }
                                return context.admittedCandidates(rowType, hydrated)
                            }
                            if (latestResult != null) {
                                val response = latestResult.getOrNull()
                                if (response == null) {
                                    state.latestFailed = true
                                } else {
                                    state.latestNext = response.hasNextPage
                                    state.successfulFetches++
                                    state.latest += filterAndHydrate(response.items)
                                }
                            }
                            if (popularResult != null) {
                                val response = popularResult.getOrNull()
                                if (response == null) {
                                    state.popularFailed = true
                                } else {
                                    state.popularNext = response.hasNextPage
                                    state.successfulFetches++
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
        val selected = context.admittedCandidates(rowType, interleaveSourceParts(parts, rowCap))
        val selectedTitles = selected.mapTo(HashSet()) { it.cleanTitle }
        val surplus = states.map { state ->
            (state.latest + state.popular).filterNot { it.cleanTitle in selectedTitles }.distinctBy { it.cleanTitle }
        }
        val backfill = context.admittedCandidates(rowType, interleaveSourceParts(surplus, rowCap - selected.size))
        val fresh = (selected + backfill).take(rowCap)
        degradedSourceIds = states.filter { state ->
            state.latestFailed || state.popularFailed || state.metadataIncomplete ||
                (
                    !state.finished() && (state.latest + state.popular).distinctBy { it.cleanTitle }.size <
                        slotAllocations.getValue(state.id)
                    )
        }.map { it.id }.toSet()
        successfulSourceIds = states.filter { state ->
            state.successfulFetches > 0 && state.id !in degradedSourceIds
        }.map { it.id }.toSet()
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

private suspend fun <T> withBudgetTimeoutOrNull(
    budget: DiscoveryVerificationBudget?,
    block: suspend () -> T,
): T? {
    if (budget == null) return block()
    val remaining = budget.remainingMs()
    if (remaining <= 0L) return null
    return withHardTimeoutOrNull(remaining) { block() }
}
