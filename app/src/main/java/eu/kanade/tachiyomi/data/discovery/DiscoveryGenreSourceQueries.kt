package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import java.io.IOException

internal const val GENRE_SOURCE_TIMEOUT_MS = 30_000L

/**
 * How many accepted cards one row may take from a single productive source per
 * pass. Required mode pages past the first page up to this target and stops
 * sources without matches after [MAX_EMPTY_REQUIRED_PAGES] empty pages.
 */
internal const val RICH_SOURCE_TARGET = 30
private const val MIN_GENRE_SOURCE_TIMEOUT_MS = 5_000L

/** A required genre may legitimately span many catalogue pages. */
private const val MAX_REQUIRED_SOURCE_PAGES = 20
private const val MAX_EMPTY_REQUIRED_PAGES = 2

/** First-wave probe: enough to prove a source can match without owning the pool. */
private const val REQUIRED_PROBE_DETAILS = 6

/** Second-wave cap per productive source; a rich source may verify down to its target. */
private const val REQUIRED_REFILL_DETAILS = 60

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
 * One required-genre query shared by TASTE and SOURCE when both rows are on.
 * The catalogue work (and its details cost) is paid once; the rows then take
 * disjoint slices, so cross-row dedupe in the coordinator cannot starve SOURCE.
 */
class SharedRequiredGenreQuery internal constructor(
    private val query: suspend () -> List<GenreSourceVerification>,
) {
    private val mutex = Mutex()
    private var memo: List<GenreSourceVerification>? = null

    internal suspend fun results(): List<GenreSourceVerification> = mutex.withLock {
        memo ?: query().also { memo = it }
    }
}

/** Even/odd split of one source result; [parity] 0 = TASTE, 1 = SOURCE. */
internal fun GenreSourceVerification.sliceForRow(parity: Int): GenreSourceVerification = copy(
    summary = summary.copy(
        items = summary.items.filterIndexed { index, _ -> index % 2 == parity },
    ),
)

/**
 * Limit concurrency, not participation. Each selected source gets a small
 * probe of the details allowance; sources that already proved they can match
 * then share the rest, so a rich source can fill its row instead of being
 * capped by an equal split. The shared [budget] caps the whole row.
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
    perSourceCap: Int = RICH_SOURCE_TARGET,
    budget: DiscoveryVerificationBudget? = null,
): List<GenreSourceVerification> = supervisorScope {
    require(maxConcurrency > 0 && timeoutMs > 0L && targetItems > 0 && perSourceCap > 0)
    val requestedIds = sourceIds.filter { it > 0L }.distinct()
    // Selected ids may be stubs or installed for another media type. Reserving
    // a details share for a source that cannot answer starves the productive one.
    val ids = catalog.queryableSourceIds(media, requestedIds)
    val skipped = requestedIds.filterNot { it in ids }
    if (skipped.isNotEmpty()) {
        genreQueryLog.logcat { "[DiscoveryGenreQuery] skippedNonCatalogue=$skipped" }
    }
    if (ids.isEmpty()) return@supervisorScope emptyList()

    val required = policy.requiredGenres.isNotEmpty()
    val target = if (required) minOf(targetItems, perSourceCap) else targetItems
    val pageBudget = if (required) MAX_REQUIRED_SOURCE_PAGES else 1
    val states = ids.map { id ->
        SourceQueryState(
            sourceId = id,
            queryKey = discoveryGenreQueryKey(media, id, genres, releaseStatuses),
            startPage = page,
            target = target,
            pageLimit = pageBudget,
            required = required,
        )
    }
    val permits = Semaphore(maxConcurrency)

    if (!required || !hydrate || budget == null) {
        // Cheap and legacy paths keep their single-pass shape.
        val allowance = if (hydrate && budget != null) {
            allocateDetailAllowance(ids, budget.remainingDetails)
        } else {
            emptyMap()
        }
        states.map { state ->
            async {
                permits.withPermit {
                    runWaveSafely(
                        state, catalog, media, genres, releaseStatuses, policy, timeoutMs, hydrate, budget,
                        allowance[state.sourceId] ?: 0, wave = 1,
                    )
                }
            }
        }.awaitAll()
        return@supervisorScope states.toVerifications()
    }

    // Wave 1: a bounded probe of every source. Equal shares are only the entry
    // ticket; the probe is what separates rich sources from empty ones.
    val probeTotal = minOf(budget.remainingDetails, REQUIRED_PROBE_DETAILS * ids.size)
    val probe = allocateDetailAllowance(ids, probeTotal)
    states.map { state ->
        async {
            permits.withPermit {
                runWaveSafely(
                    state, catalog, media, genres, releaseStatuses, policy, timeoutMs, hydrate, budget,
                    probe[state.sourceId] ?: 0, wave = 1,
                )
            }
        }
    }.awaitAll()

    // Wave 2: only sources that already admitted a match may spend the rest.
    // A zero-match source does not get another 30 details just for participating.
    val refillIds = states.filter { it.canContinue() }
        .sortedByDescending { it.admitted.size }
        .map { it.sourceId }
    if (refillIds.isNotEmpty() && budget.remainingDetails > 0 && !budget.isExpired()) {
        val pool = minOf(budget.remainingDetails, REQUIRED_REFILL_DETAILS * refillIds.size)
        val refill = allocateDetailAllowance(refillIds, pool)
        val byId = states.associateBy { it.sourceId }
        refillIds.map { id ->
            async {
                permits.withPermit {
                    runWaveSafely(
                        byId.getValue(id),
                        catalog,
                        media,
                        genres,
                        releaseStatuses,
                        policy,
                        timeoutMs,
                        hydrate,
                        budget,
                        refill[id] ?: 0,
                        wave = 2,
                    )
                }
            }
        }.awaitAll()
    }
    states.toVerifications()
}

private suspend fun runWaveSafely(
    state: SourceQueryState,
    catalog: DiscoverySourceCatalog,
    media: DiscoveryMediaType,
    genres: List<String>,
    releaseStatuses: Set<DiscoveryReleaseStatus>,
    policy: DiscoveryCandidatePolicy,
    timeoutMs: Long,
    hydrate: Boolean,
    budget: DiscoveryVerificationBudget?,
    allowance: Int,
    wave: Int,
) {
    try {
        advanceSource(
            state = state,
            catalog = catalog,
            media = media,
            genres = genres,
            releaseStatuses = releaseStatuses,
            policy = policy,
            timeoutMs = timeoutMs,
            hydrate = hydrate,
            budget = budget,
            allowance = allowance,
            wave = wave,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        state.failure = GenreSourceException(state.sourceId, GenreSourceFailure.SOURCE_ERROR)
        // A transport error is an operational failure, not "no matches found":
        // the summary must report it so the row can fail instead of caching empty.
        state.transientFailures += 1
        state.pagesStopped = true
        genreQueryLog.logcat { "[DiscoveryGenreQuery] source=${state.sourceId} wave=$wave failed=SOURCE_ERROR" }
    }
}

private class SourceQueryState(
    val sourceId: Long,
    val queryKey: String,
    startPage: Int,
    val target: Int,
    val pageLimit: Int,
    val required: Boolean,
) {
    val seen = HashSet<String>()
    val admitted = LinkedHashMap<String, DiscoveryRowItem>()
    var verified = 0
    var unattempted = 0
    var metadataUnavailable = 0
    var transientFailures = 0
    var detailsUsed = 0
    var pendingItems: List<DiscoveryRowItem> = emptyList()
    var nextPage = startPage
    var pagesFetched = 0
    var successfulPages = 0
    var emptyPages = 0
    var hasNextPage = true
    var exhausted = false
    var pagesStopped = false
    var timedOut = false
    var budgetExpired = false
    var failure: GenreSourceException? = null

    /**
     * Pending cards are still worth refilling even when the source has no next
     * page: a single-page catalogue can hide most matches behind details.
     */
    fun canContinue(): Boolean =
        failure == null &&
            admitted.isNotEmpty() &&
            admitted.size < target &&
            (
                pendingItems.isNotEmpty() ||
                    (hasNextPage && !pagesStopped && pagesFetched < pageLimit)
                )

    fun toVerification(): GenreSourceVerification {
        val summary = GenreVerificationSummary(
            items = admitted.values.toList(),
            verifiedCount = verified,
            unattemptedCount = unattempted,
            metadataUnavailableCount = metadataUnavailable,
            failedCount = transientFailures,
        )
        val effectiveFailure = failure ?: when {
            timedOut && successfulPages == 0 -> GenreSourceException(sourceId, GenreSourceFailure.TIMEOUT)
            summary.outcome == GenreVerificationOutcome.FAILED ->
                GenreSourceException(sourceId, GenreSourceFailure.METADATA_UNAVAILABLE)
            else -> null
        }
        return GenreSourceVerification(
            sourceId = sourceId,
            summary = summary,
            failure = effectiveFailure,
            stopReason = if (effectiveFailure != null) {
                DiscoveryFillStopReason.RETRY_AFTER
            } else {
                classifySourceStop(
                    admitted = admitted.size,
                    target = target,
                    timedOut = timedOut,
                    failed = summary.outcome == GenreVerificationOutcome.FAILED,
                    exhausted = exhausted,
                    pending = pendingItems.size,
                    budgetExpired = budgetExpired,
                )
            },
            exhausted = exhausted,
            pending = pendingItems.size,
            detailsUsed = detailsUsed,
            hasNextPage = hasNextPage,
            queryKey = queryKey,
        )
    }
}

private fun List<SourceQueryState>.toVerifications(): List<GenreSourceVerification> =
    map { state ->
        state.toVerification().also { result ->
            result.failure?.let { failure ->
                genreQueryLog.logcat {
                    "[DiscoveryGenreQuery] source=${result.sourceId} failed=${failure.reason}"
                }
            }
        }
    }

private suspend fun advanceSource(
    state: SourceQueryState,
    catalog: DiscoverySourceCatalog,
    media: DiscoveryMediaType,
    genres: List<String>,
    releaseStatuses: Set<DiscoveryReleaseStatus>,
    policy: DiscoveryCandidatePolicy,
    timeoutMs: Long,
    hydrate: Boolean,
    budget: DiscoveryVerificationBudget?,
    allowance: Int,
    wave: Int,
) {
    var allowanceLeft = allowance.coerceAtLeast(0)

    // A previous wave may have left the last page's cards unverified. Fetching
    // the next page before settling them would waste the richest opportunity.
    if (hydrate && budget != null && allowanceLeft > 0 && state.pendingItems.isNotEmpty()) {
        val pending = state.pendingItems
        val refilled = try {
            catalog.hydrateGenres(
                mediaType = media,
                sourceId = state.sourceId,
                items = pending,
                budget = budget,
                maxDetails = allowanceLeft,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (refilled != null) {
            allowanceLeft = (allowanceLeft - refilled.detailsUsed).coerceAtLeast(0)
            state.detailsUsed += refilled.detailsUsed
            state.verified += refilled.verified
            state.metadataUnavailable += refilled.metadataUnavailable
            state.transientFailures += refilled.transientFailures
            val stillPending = LinkedHashMap<String, DiscoveryRowItem>()
            for (item in refilled.items) {
                if (item.genres.isNotEmpty()) {
                    val candidate = policy.filterRowCandidates(listOf(item), { it.genres }).firstOrNull()
                    if (candidate != null && state.admitted.size < state.target) {
                        state.admitted.putIfAbsent(candidate.collectionKey(), candidate)
                    }
                } else if (!item.sourceUrl.isNullOrBlank()) {
                    stillPending[item.collectionKey()] = item
                }
            }
            state.pendingItems = stillPending.values.toList()
            genreQueryLog.logcat {
                "[DiscoveryGenreQuery] source=${state.sourceId} wave=$wave refill=" +
                    "pending=${pending.size} verified=${refilled.verified} " +
                    "details=${refilled.detailsUsed} total=${state.admitted.size} " +
                    "pendingAfter=${state.pendingItems.size}"
            }
        }
    }

    while (state.hasNextPage && !state.pagesStopped && state.pagesFetched < state.pageLimit) {
        // The first page still runs without an allowance: list metadata may
        // already satisfy the query, and cached details are free. Further pages
        // without details cannot admit new cards under a required filter.
        if (hydrate && budget != null && allowanceLeft <= 0 && state.successfulPages > 0) {
            state.budgetExpired = true
            break
        }
        val remaining = if (hydrate) budget?.remainingMs() ?: timeoutMs else timeoutMs
        if (remaining <= 0L) {
            state.unattempted += 1
            state.budgetExpired = true
            break
        }
        val page = state.nextPage
        val pageResult = withTimeoutOrNull(minOf(timeoutMs, remaining)) {
            if (hydrate) {
                catalog.popularWithGenresAllocated(
                    mediaType = media,
                    sourceId = state.sourceId,
                    genres = genres,
                    page = page,
                    releaseStatuses = releaseStatuses,
                    budget = budget,
                    maxDetails = allowanceLeft,
                )
            } else {
                val items = catalog.popularWithGenres(
                    mediaType = media,
                    sourceId = state.sourceId,
                    genres = genres,
                    page = page,
                    releaseStatuses = releaseStatuses,
                )
                DiscoveryGenrePage(
                    items = items,
                    verified = items.count { it.genres.isNotEmpty() },
                    unattempted = items.count { it.genres.isEmpty() },
                    metadataUnavailable = 0,
                    transientFailures = 0,
                    page = page,
                    queryKey = state.queryKey,
                )
            }
        }
        if (pageResult == null) {
            state.timedOut = true
            state.transientFailures += 1
            state.pagesStopped = true
            break
        }
        // A page can report coverage without returning rows (every card was
        // rejected upstream). The counters are the evidence we paid for, so they
        // must be accumulated before any early exit.
        state.pagesFetched++
        state.nextPage = page + 1
        state.successfulPages++
        state.verified += pageResult.verified
        state.unattempted += pageResult.unattempted
        state.metadataUnavailable += pageResult.metadataUnavailable
        state.transientFailures += pageResult.transientFailures
        state.detailsUsed += pageResult.detailsUsed
        allowanceLeft = (allowanceLeft - pageResult.detailsUsed).coerceAtLeast(0)
        state.pendingItems = pageResult.pending
        state.hasNextPage = pageResult.hasNextPage
        if (!pageResult.hasNextPage) {
            state.exhausted = true
            state.pagesStopped = true
        }

        val unique = pageResult.items.filter {
            state.seen.add(it.sourceUrl?.takeIf(String::isNotBlank) ?: it.cleanTitle)
        }
        val pageAdmitted = policy.filterRowCandidates(unique, { it.genres })
        for (candidate in pageAdmitted) {
            if (state.admitted.size >= state.target) break
            state.admitted.putIfAbsent(candidate.collectionKey(), candidate)
        }
        genreQueryLog.logcat {
            "[DiscoveryGenreQuery] source=${state.sourceId} wave=$wave page=$page " +
                "fetched=${pageResult.items.size} verified=${pageResult.verified} " +
                "unattempted=${pageResult.unattempted} unavailable=${pageResult.metadataUnavailable} " +
                "failures=${pageResult.transientFailures} admitted=${pageAdmitted.size} " +
                "total=${state.admitted.size} target=${state.target} " +
                "details=${pageResult.detailsUsed} pending=${pageResult.pending.size} " +
                "hasNext=${pageResult.hasNextPage} required=${policy.requiredGenres} " +
                "sampleGenres=${unique.take(3).map { it.genres.take(8) }}"
        }
        if (state.admitted.size >= state.target) {
            state.pagesStopped = true
            break
        }
        if (!pageResult.hasNextPage) break
        if (state.required) {
            // Empty and duplicate pages are a grace budget, not an instant stop:
            // a productive source may still have new matches on the next page.
            if (pageAdmitted.isEmpty()) state.emptyPages++ else state.emptyPages = 0
            if (state.emptyPages >= MAX_EMPTY_REQUIRED_PAGES) {
                state.pagesStopped = true
                break
            }
        }
    }
}

private fun DiscoveryRowItem.collectionKey(): String =
    sourceUrl?.takeIf(String::isNotBlank) ?: cleanTitle

/** A transport error during a genre search is not evidence of an empty catalogue. */
internal fun <T> Result<T>.getForDiscoveryGenreQuery(genres: List<String>?): T? =
    if (genres != null) getOrThrow() else getOrNull()
