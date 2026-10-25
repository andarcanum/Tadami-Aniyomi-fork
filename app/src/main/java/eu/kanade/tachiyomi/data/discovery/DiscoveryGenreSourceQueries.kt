package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import java.io.IOException

internal const val GENRE_SOURCE_TIMEOUT_MS = 30_000L
private const val MIN_GENRE_SOURCE_TIMEOUT_MS = 5_000L

internal enum class GenreSourceFailure { TIMEOUT, METADATA_UNAVAILABLE, SOURCE_ERROR }

internal class GenreSourceException(val sourceId: Long, val reason: GenreSourceFailure) :
    IOException("Genre source $sourceId: $reason")

private val genreQueryLog = Any()

/**
 * Splits the remaining shared details allowance across the sources that are
 * about to query. Base plus remainder keeps the total exact, while every source
 * gets a share instead of the first few sources consuming everything.
 */
internal fun allocateDetailAllowance(sourceIds: List<Long>, remaining: Int): Map<Long, Int> {
    if (sourceIds.isEmpty()) return emptyMap()
    val total = remaining.coerceAtLeast(0)
    val base = total / sourceIds.size
    val remainder = total % sourceIds.size
    return sourceIds.mapIndexed { index, id ->
        id to base + if (index < remainder) 1 else 0
    }.toMap()
}

/**
 * A source may not own the whole pass when several concurrency waves are
 * needed; otherwise late waves start after the media deadline has expired.
 */
internal fun genreSourceTimeoutMs(
    budget: DiscoveryVerificationBudget?,
    sourceCount: Int,
    maxConcurrency: Int,
): Long {
    if (budget == null || sourceCount <= 0 || maxConcurrency <= 0) return GENRE_SOURCE_TIMEOUT_MS
    val waves = ((sourceCount + maxConcurrency - 1) / maxConcurrency).coerceAtLeast(1)
    return (budget.remainingMs() / waves)
        .coerceIn(MIN_GENRE_SOURCE_TIMEOUT_MS, GENRE_SOURCE_TIMEOUT_MS)
}

internal fun classifySourceStop(
    admitted: Int,
    target: Int,
    timedOut: Boolean,
    failed: Boolean,
    exhausted: Boolean,
    pending: Int,
    budgetExpired: Boolean,
): DiscoveryFillStopReason = when {
    admitted >= target -> DiscoveryFillStopReason.TARGET_REACHED
    timedOut || failed -> DiscoveryFillStopReason.RETRY_AFTER
    exhausted -> DiscoveryFillStopReason.SOURCE_EXHAUSTED
    pending > 0 || budgetExpired -> DiscoveryFillStopReason.PASS_BUDGET_EXHAUSTED
    else -> DiscoveryFillStopReason.PAGE_BUDGET_EXHAUSTED
}

/**
 * Limit concurrency, not participation. Each selected source gets a fair slice
 * of the details allowance and a bounded query attempt; the shared [budget]
 * caps the whole row.
 *
 * When [hydrate] is false the caller only needs catalogue items and must not
 * pay for details lookups (fast TASTE path).
 */
internal suspend fun queryDiscoveryGenreSources(
    catalog: DiscoverySourceCatalog,
    media: DiscoveryMediaType,
    sourceIds: List<Long>,
    genres: List<String>,
    page: Int,
    releaseStatuses: Set<DiscoveryReleaseStatus>,
    policy: DiscoveryCandidatePolicy,
    maxConcurrency: Int = 4,
    timeoutMs: Long = GENRE_SOURCE_TIMEOUT_MS,
    hydrate: Boolean = true,
    targetItems: Int = Int.MAX_VALUE,
    budget: DiscoveryVerificationBudget? = null,
): List<GenreSourceVerification> = supervisorScope {
    require(maxConcurrency > 0 && timeoutMs > 0L && targetItems > 0)
    val ids = sourceIds.filter { it > 0L }.distinct()
    val allowance = if (hydrate && budget != null) {
        allocateDetailAllowance(ids, budget.remainingDetails)
    } else {
        emptyMap()
    }
    val permits = Semaphore(maxConcurrency)
    ids.map { id ->
        async {
            permits.withPermit {
                queryOneSource(
                    catalog = catalog,
                    media = media,
                    sourceId = id,
                    genres = genres,
                    page = page,
                    releaseStatuses = releaseStatuses,
                    policy = policy,
                    timeoutMs = timeoutMs,
                    hydrate = hydrate,
                    targetItems = targetItems,
                    maxDetails = allowance[id] ?: 0,
                    budget = budget,
                )
            }.also { result ->
                result.failure?.let { failure ->
                    genreQueryLog.logcat { "[DiscoveryGenreQuery] source=$id failed=${failure.reason}" }
                }
            }
        }
    }.awaitAll()
}

private suspend fun queryOneSource(
    catalog: DiscoverySourceCatalog,
    media: DiscoveryMediaType,
    sourceId: Long,
    genres: List<String>,
    page: Int,
    releaseStatuses: Set<DiscoveryReleaseStatus>,
    policy: DiscoveryCandidatePolicy,
    timeoutMs: Long,
    hydrate: Boolean,
    targetItems: Int,
    maxDetails: Int,
    budget: DiscoveryVerificationBudget?,
): GenreSourceVerification {
    val queryKey = discoveryGenreQueryKey(media, sourceId, genres, releaseStatuses)
    // Only the verifying path can spend the details allowance, so only it has to
    // stop when the shared budget is gone. The cheap path keeps its old shape.
    if (hydrate && budget != null && budget.isExpired()) {
        // No useful work can be done inside the remaining media budget.
        return GenreSourceVerification(
            sourceId = sourceId,
            summary = GenreVerificationSummary.empty(unattempted = 1),
            stopReason = DiscoveryFillStopReason.PASS_BUDGET_EXHAUSTED,
            pending = 1,
            queryKey = queryKey,
        )
    }
    return try {
        val collected = collectSource(
            catalog = catalog,
            media = media,
            sourceId = sourceId,
            genres = genres,
            page = page,
            releaseStatuses = releaseStatuses,
            policy = policy,
            timeoutMs = timeoutMs,
            hydrate = hydrate,
            targetItems = targetItems,
            maxDetails = maxDetails,
            budget = budget,
        )
        val failure = when {
            collected.timedOutBeforeAnyPage -> GenreSourceException(sourceId, GenreSourceFailure.TIMEOUT)
            collected.summary.outcome == GenreVerificationOutcome.FAILED ->
                GenreSourceException(sourceId, GenreSourceFailure.METADATA_UNAVAILABLE)
            else -> null
        }
        GenreSourceVerification(
            sourceId = sourceId,
            summary = collected.summary,
            failure = failure,
            stopReason = if (failure != null) DiscoveryFillStopReason.RETRY_AFTER else collected.stopReason,
            exhausted = collected.exhausted,
            pending = collected.pending,
            detailsUsed = collected.detailsUsed,
            hasNextPage = collected.hasNextPage,
            queryKey = queryKey,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        GenreSourceVerification(
            sourceId = sourceId,
            summary = GenreVerificationSummary.empty(failed = 1),
            failure = GenreSourceException(sourceId, GenreSourceFailure.SOURCE_ERROR),
            stopReason = DiscoveryFillStopReason.RETRY_AFTER,
            queryKey = queryKey,
        )
    }
}

private data class SourceCollection(
    val summary: GenreVerificationSummary,
    val timedOutBeforeAnyPage: Boolean,
    val stopReason: DiscoveryFillStopReason,
    val exhausted: Boolean,
    val pending: Int,
    val detailsUsed: Int,
    val hasNextPage: Boolean,
    val queryKey: String,
)

private suspend fun collectSource(
    catalog: DiscoverySourceCatalog,
    media: DiscoveryMediaType,
    sourceId: Long,
    genres: List<String>,
    page: Int,
    releaseStatuses: Set<DiscoveryReleaseStatus>,
    policy: DiscoveryCandidatePolicy,
    timeoutMs: Long,
    hydrate: Boolean,
    targetItems: Int,
    maxDetails: Int,
    budget: DiscoveryVerificationBudget?,
): SourceCollection {
    val queryKey = discoveryGenreQueryKey(media, sourceId, genres, releaseStatuses)
    val seen = HashSet<String>()
    val admitted = LinkedHashMap<String, DiscoveryRowItem>()
    var verified = 0
    var unattempted = 0
    var metadataUnavailable = 0
    var transientFailures = 0
    var detailsUsed = 0
    var pendingCount = 0
    var successfulPages = 0
    var timedOut = false
    var exhausted = false
    var budgetExpired = false
    var lastHasNextPage = true
    var sourceAllowance = maxDetails.coerceAtLeast(0)
    val pageBudget = if (policy.isStrictRequired && policy.requiredGenres.isNotEmpty()) 2 else 1

    for (offset in 0 until pageBudget) {
        val remaining = if (hydrate) budget?.remainingMs() ?: timeoutMs else timeoutMs
        if (remaining <= 0L) {
            unattempted += 1
            budgetExpired = true
            break
        }
        // The first page still runs without an allowance: list metadata may
        // already satisfy the query, and cached details are free. Further pages
        // without details cannot admit new cards under a required filter.
        if (hydrate && budget != null && sourceAllowance <= 0 && successfulPages > 0) {
            budgetExpired = true
            break
        }
        val pageResult = withTimeoutOrNull(minOf(timeoutMs, remaining)) {
            if (hydrate) {
                catalog.popularWithGenresAllocated(
                    mediaType = media,
                    sourceId = sourceId,
                    genres = genres,
                    page = page + offset,
                    releaseStatuses = releaseStatuses,
                    budget = budget,
                    maxDetails = sourceAllowance,
                )
            } else {
                val items = catalog.popularWithGenres(
                    mediaType = media,
                    sourceId = sourceId,
                    genres = genres,
                    page = page + offset,
                    releaseStatuses = releaseStatuses,
                )
                DiscoveryGenrePage(
                    items = items,
                    verified = items.count { it.genres.isNotEmpty() },
                    unattempted = items.count { it.genres.isEmpty() },
                    metadataUnavailable = 0,
                    transientFailures = 0,
                    page = page + offset,
                    queryKey = queryKey,
                )
            }
        }
        if (pageResult == null) {
            timedOut = true
            break
        }
        // A page can report coverage without returning rows (every card was
        // rejected upstream). The counters are the evidence we paid for, so they
        // must be accumulated before any early exit.
        successfulPages++
        verified += pageResult.verified
        unattempted += pageResult.unattempted
        metadataUnavailable += pageResult.metadataUnavailable
        transientFailures += pageResult.transientFailures
        detailsUsed += pageResult.detailsUsed
        sourceAllowance = (sourceAllowance - pageResult.detailsUsed).coerceAtLeast(0)
        pendingCount = pageResult.pending.size
        lastHasNextPage = pageResult.hasNextPage
        if (!pageResult.hasNextPage) exhausted = true

        val unique = pageResult.items.filter {
            seen.add(it.sourceUrl?.takeIf(String::isNotBlank) ?: it.cleanTitle)
        }
        if (unique.isEmpty()) break

        val pageAdmitted = policy.filterRowCandidates(unique, { it.genres })
        for (candidate in pageAdmitted) {
            if (admitted.size >= targetItems) break
            admitted.putIfAbsent(candidate.collectionKey(), candidate)
        }
        genreQueryLog.logcat {
            "[DiscoveryGenreQuery] source=$sourceId page=${page + offset} fetched=${pageResult.items.size} " +
                "verified=${pageResult.verified} unattempted=${pageResult.unattempted} " +
                "unavailable=${pageResult.metadataUnavailable} failures=${pageResult.transientFailures} " +
                "admitted=${pageAdmitted.size} total=${admitted.size} details=${pageResult.detailsUsed} " +
                "pending=${pageResult.pending.size} hasNext=${pageResult.hasNextPage} " +
                "required=${policy.requiredGenres} " +
                "sampleGenres=${unique.take(3).map { it.genres.take(8) }}"
        }
        if (admitted.size >= targetItems) break
        if (!pageResult.hasNextPage) break
    }

    if (timedOut) {
        // A timeout after at least one successful page is a coverage gap, not a
        // total failure: we already learned something about the source.
        if (successfulPages == 0) transientFailures += 1 else unattempted += 1
    }
    val summary = GenreVerificationSummary(
        items = admitted.values.toList(),
        verifiedCount = verified,
        unattemptedCount = unattempted,
        metadataUnavailableCount = metadataUnavailable,
        failedCount = transientFailures,
    )
    return SourceCollection(
        summary = summary,
        timedOutBeforeAnyPage = timedOut && successfulPages == 0,
        stopReason = classifySourceStop(
            admitted = admitted.size,
            target = targetItems,
            timedOut = timedOut,
            failed = summary.outcome == GenreVerificationOutcome.FAILED,
            exhausted = exhausted,
            pending = pendingCount,
            budgetExpired = budgetExpired,
        ),
        exhausted = exhausted,
        pending = pendingCount,
        detailsUsed = detailsUsed,
        hasNextPage = lastHasNextPage,
        queryKey = queryKey,
    )
}

private fun DiscoveryRowItem.collectionKey(): String =
    sourceUrl?.takeIf(String::isNotBlank) ?: cleanTitle

/** A transport error during a genre search is not evidence of an empty catalogue. */
internal fun <T> Result<T>.getForDiscoveryGenreQuery(genres: List<String>?): T? =
    if (genres != null) getOrThrow() else getOrNull()
