package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import java.io.IOException

internal const val GENRE_SOURCE_TIMEOUT_MS = 30_000L

/**
 * How many accepted cards one row may take from a single productive source per
 * pass. Required mode keeps paging under the shared deadline and page budget;
 * filtered or retired pages do not prove catalogue exhaustion.
 */
internal const val RICH_SOURCE_TARGET = 30

private const val MIN_GENRE_SOURCE_TIMEOUT_MS = 5_000L
internal const val WAVE_SOURCE_TIMEOUT_CAP_MS = 12_000L

/** A required genre may legitimately span many catalogue pages. */
private const val MAX_REQUIRED_SOURCE_PAGES = 20

/** Once the shared details are gone, list metadata may justify at most this many pages. */
private const val MAX_DETAIL_FREE_PAGES = 2

/** First-wave probe: enough to prove a source can match without owning the pool. */
private const val REQUIRED_PROBE_DETAILS = 6

/** Detail-free pages a source without any match may still walk after its probe. */
private const val MAX_DRY_PAGES = 5

/** Per-wave cap once a source has admitted a match; it may verify down to its target. */
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
    budget: DiscoveryVerificationBudget? = null,
    pages: Int = 1,
    excludedKeys: Set<String> = emptySet(),
    matchMode: RequiredMatchMode = policy.requiredMatchMode,
): List<GenreSourceVerification> = supervisorScope {
    require(maxConcurrency > 0 && timeoutMs > 0L && targetItems > 0)
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
    val bestEffortRequired = required && !policy.isStrictRequired
    val target = if (required && ids.size > 1) minOf(targetItems, RICH_SOURCE_TARGET) else targetItems
    val pageBudget = if (required) MAX_REQUIRED_SOURCE_PAGES else pages.coerceAtLeast(1)
    val queries = if (required && matchMode == RequiredMatchMode.ANY && genres.size > 1) {
        genres.distinct().map { listOf(it) }
    } else {
        listOf(genres)
    }
    var allocationId = 0L
    // Genre-first keeps every selected source's probe ahead of deeper alternatives.
    val states = queries.flatMap { queryGenres ->
        ids.map { id ->
            SourceQueryState(
                sourceId = id,
                allocationId = allocationId++,
                queryGenres = queryGenres,
                queryKey = discoveryGenreQueryKey(media, id, queryGenres, releaseStatuses),
                startPage = page,
                target = target,
                pageLimit = pageBudget,
                required = required,
                bestEffortRequired = bestEffortRequired,
                excludedKeys = excludedKeys,
            )
        }
    }
    val permits = Semaphore(maxConcurrency)

    var wave = 0
    while (true) {
        val active = states.filter { it.canContinue() }
        if (active.isEmpty()) break
        if (hydrate && budget?.isExpired() == true) {
            active.forEach { it.budgetExpired = true }
            break
        }
        wave++
        val remaining = if (hydrate) budget?.remainingDetails ?: 0 else 0
        val productive = active.filter {
            it.matchedAdmitted.isNotEmpty() || it.genreFilterApplied == true || it.matchedRequired > 0
        }
        val unprobed = active.filter {
            it.matchedAdmitted.isEmpty() && it.pendingItems.isNotEmpty() &&
                !it.detailsUseless() && !it.secondProbeUsed
        }
        // Wave 1 probes every source. Later waves spend details only on sources
        // that still need their single retry; productive sources share the rest.
        var probeAlloc: Map<Long, Int> = emptyMap()
        val allowance = if (wave == 1) {
            allocateDetailAllowance(
                active.map { it.allocationId },
                minOf(remaining, REQUIRED_PROBE_DETAILS * active.size),
            )
        } else {
            val probeRecipients = unprobed.filterNot { p -> productive.any { it.allocationId == p.allocationId } }
            probeAlloc = allocateDetailAllowance(
                probeRecipients.map { it.allocationId },
                minOf(remaining, REQUIRED_PROBE_DETAILS * probeRecipients.size),
            )
            val refillRemaining = (remaining - probeAlloc.values.sum()).coerceAtLeast(0)
            // Prioritize productive sources that have not yet contributed a fair share
            // so a single source that already reached 20+ items does not starve a source
            // with 1-5 admitted items and dozens of pending cards.
            val hungryProductive = productive.filter { it.matchedAdmitted.size < 12 }
                .ifEmpty { productive }
            val refillAlloc = allocateDetailAllowance(
                hungryProductive.map { it.allocationId },
                minOf(refillRemaining, REQUIRED_REFILL_DETAILS * hungryProductive.size),
            )
            val leftover = (refillRemaining - refillAlloc.values.sum()).coerceAtLeast(0)
            val saturatedProductive = productive.filterNot { p ->
                hungryProductive.any { it.allocationId == p.allocationId }
            }
            val saturatedAlloc = if (leftover > 0 && saturatedProductive.isNotEmpty()) {
                allocateDetailAllowance(
                    saturatedProductive.map { it.allocationId },
                    minOf(leftover, REQUIRED_REFILL_DETAILS * saturatedProductive.size),
                )
            } else {
                emptyMap()
            }
            probeAlloc + refillAlloc + saturatedAlloc
        }
        val before = active.sumOf { it.pagesFetched + it.verified + it.detailsUsed + it.totalAdmittedCount() } to
            active.sumOf { it.pendingItems.size }
        // One page or one hydration batch per source and round: late selected
        // sources get their probe before an early source can own the deadline,
        // while Semaphore(maxConcurrency) prevents a slow source from stalling free slots.
        supervisorScope {
            active.map { state ->
                async {
                    permits.withPermit {
                        val remainingMs = if (hydrate) budget?.remainingMs() else null
                        if (remainingMs == 0L) {
                            state.budgetExpired = true
                            return@withPermit
                        }
                        val callTimeout = remainingMs?.let { minOf(timeoutMs, it) } ?: timeoutMs
                        runWaveSafely(
                            state, catalog, media, state.queryGenres, releaseStatuses,
                            policy, callTimeout, hydrate, budget,
                            allowance[state.allocationId] ?: 0, isProbe = state.allocationId in probeAlloc, wave,
                        )
                    }
                }
            }.awaitAll()
        }
        val after = active.sumOf { it.pagesFetched + it.verified + it.detailsUsed + it.totalAdmittedCount() } to
            active.sumOf { it.pendingItems.size }
        if (before == after) {
            active.forEach { it.budgetExpired = true }
            break
        }
    }
    val results = states.toVerifications()
    if (queries.size == 1) {
        results
    } else {
        results.groupBy { it.sourceId }.map { (sourceId, parts) ->
            val items = interleaveSourceParts(parts.map { it.summary.items }, target).distinctBy { it.collectionKey() }
            val exhausted = parts.all { it.exhausted }
            val branchFailure = parts.firstNotNullOfOrNull { it.failure }
            val totalFailure = if (parts.all { it.failure != null } && items.isEmpty()) branchFailure else null
            val pending = parts.sumOf { it.pending }
            val incomplete = (parts.any { it.summary.hasIncompletePages || it.failure != null }) && items.size < target
            val summary = GenreVerificationSummary(
                items = items,
                verifiedCount = parts.sumOf { it.summary.verifiedCount },
                unattemptedCount = parts.sumOf { it.summary.unattemptedCount },
                metadataUnavailableCount = parts.sumOf { it.summary.metadataUnavailableCount },
                failedCount = parts.sumOf { it.summary.failedCount },
                hasIncompletePages = incomplete,
            )
            GenreSourceVerification(
                sourceId = sourceId,
                summary = summary,
                failure = totalFailure,
                stopReason = if (totalFailure != null) {
                    DiscoveryFillStopReason.RETRY_AFTER
                } else {
                    classifySourceStop(
                        admitted = items.size,
                        target = target,
                        timedOut = parts.any { it.stopReason == DiscoveryFillStopReason.RETRY_AFTER } &&
                            items.isEmpty(),
                        failed = summary.outcome == GenreVerificationOutcome.FAILED,
                        exhausted = exhausted,
                        pending = pending,
                        budgetExpired = parts.any { it.stopReason == DiscoveryFillStopReason.PASS_BUDGET_EXHAUSTED },
                    )
                },
                exhausted = exhausted,
                pending = pending,
                detailsUsed = parts.sumOf { it.detailsUsed },
                hasNextPage = parts.any { it.hasNextPage },
                queryKey = discoveryGenreQueryKey(media, sourceId, genres, releaseStatuses) + "|" + matchMode.name,
                successfulPages = parts.sumOf { it.successfulPages },
            )
        }
    }
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
    isProbe: Boolean,
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
            isProbe = isProbe,
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
    val allocationId: Long,
    val queryGenres: List<String>,
    val queryKey: String,
    startPage: Int,
    val target: Int,
    val pageLimit: Int,
    val required: Boolean,
    val bestEffortRequired: Boolean,
    val excludedKeys: Set<String>,
) {
    val seen = HashSet<String>()
    val matchedAdmitted = LinkedHashMap<String, DiscoveryRowItem>()
    val fallbackAdmitted = LinkedHashMap<String, DiscoveryRowItem>()
    var verified = 0
    var unattempted = 0
    var metadataUnavailable = 0
    var transientFailures = 0
    var detailsUsed = 0
    var pendingItems: List<DiscoveryRowItem> = emptyList()
    var nextPage = startPage
    var pagesFetched = 0
    var successfulPages = 0
    var hasNextPage = true
    var exhausted = false
    var pagesStopped = false
    var timedOut = false
    var budgetExpired = false
    var genreFilterApplied: Boolean? = null
    var matchedRequired = 0
    var failure: GenreSourceException? = null
    var secondProbeUsed = false

    fun totalAdmittedCount(): Int = matchedAdmitted.size + fallbackAdmitted.size

    fun admit(candidate: DiscoveryRowItem, verdict: CandidateVerdict.Admitted) {
        val key = candidate.collectionKey()
        if (!bestEffortRequired || verdict.matchesRequired) {
            if (matchedAdmitted.size < target) {
                matchedAdmitted.putIfAbsent(key, candidate)
            }
        } else {
            if (fallbackAdmitted.size < target && key !in matchedAdmitted) {
                fallbackAdmitted.putIfAbsent(key, candidate)
            }
        }
    }

    /** A full probe produced no genres at all: more details requests would only burn the shared allowance. */
    fun detailsUseless(): Boolean = verified == 0 && detailsUsed >= REQUIRED_PROBE_DETAILS

    /**
     * Pending cards are still worth refilling even when the source has no next
     * page: a single-page catalogue can hide most matches behind details.
     */
    fun canContinue(): Boolean =
        failure == null &&
            !timedOut && !budgetExpired &&
            matchedAdmitted.size < target &&
            (
                pendingItems.isNotEmpty() ||
                    (hasNextPage && !pagesStopped && pagesFetched < pageLimit)
                )

    fun toVerification(): GenreSourceVerification {
        val effectiveItems = buildList {
            addAll(matchedAdmitted.values.take(target))
            if (size < target && fallbackAdmitted.isNotEmpty()) {
                for (item in fallbackAdmitted.values) {
                    if (size >= target) break
                    if (item.collectionKey() !in matchedAdmitted) {
                        add(item)
                    }
                }
            }
        }
        val completenessTarget = if (bestEffortRequired) matchedAdmitted.size else effectiveItems.size
        val summary = GenreVerificationSummary(
            items = effectiveItems,
            verifiedCount = verified,
            unattemptedCount = unattempted,
            metadataUnavailableCount = metadataUnavailable,
            failedCount = transientFailures,
            hasIncompletePages = (timedOut || budgetExpired || !exhausted) && completenessTarget < target,
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
                    admitted = effectiveItems.size,
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
            successfulPages = successfulPages,
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
    isProbe: Boolean,
    wave: Int,
) {
    var allowanceLeft = allowance.coerceAtLeast(0)
    if (hydrate && budget != null && state.detailsUseless()) {
        state.pendingItems = emptyList()
        state.pagesStopped = true
        return
    }
    // A dry source already had its fair probe; do not let it keep fetching
    // pages while the refill pool is reserved for sources that matched. Once
    // the pool is gone, the bounded detail-free page grace still applies.
    if (wave > 1 && hydrate && budget != null && allowanceLeft <= 0 &&
        state.matchedAdmitted.isEmpty() && state.genreFilterApplied != true && state.matchedRequired == 0 &&
        budget.remainingDetails > 0 &&
        (state.pendingItems.isNotEmpty() || state.pagesFetched >= MAX_DRY_PAGES)
    ) {
        state.budgetExpired = true
        return
    }
    if (hydrate && budget != null && state.pendingItems.isNotEmpty()) {
        val oldPending = state.pendingItems
        val pending = oldPending.filterNot { it.isExcludedBy(state.excludedKeys, media) }
        state.unattempted = (state.unattempted - oldPending.size).coerceAtLeast(0)
        state.pendingItems = pending
        if (pending.isNotEmpty()) {
            if (allowanceLeft <= 0 && budget.remainingDetails > 0) {
                return
            }
            if (allowanceLeft > 0) {
                val remaining = if (hydrate) budget.remainingMs() else timeoutMs
                val refilled = withHardTimeoutOrNull(minOf(timeoutMs, remaining.coerceAtLeast(1L))) {
                    catalog.hydrateGenres(media, state.sourceId, pending, budget, allowanceLeft)
                }
                if (refilled == null) {
                    state.transientFailures++
                    state.timedOut = true
                    state.unattempted += pending.size
                    return
                }
                allowanceLeft = (allowanceLeft - refilled.detailsUsed).coerceAtLeast(0)
                state.detailsUsed += refilled.detailsUsed
                if (isProbe && refilled.detailsUsed > 0) state.secondProbeUsed = true
                state.verified += refilled.verified
                state.metadataUnavailable += refilled.metadataUnavailable
                state.transientFailures += refilled.transientFailures
                state.unattempted += refilled.unattempted
                state.pendingItems = refilled.pending.filterNot { it.isExcludedBy(state.excludedKeys, media) }
                if (policy.requiredGenres.isNotEmpty()) {
                    state.matchedRequired += refilled.items.count {
                        it.genres.isNotEmpty() && policy.matchesRequired(it.genres)
                    }
                }
                val verified = refilled.items.filterNot { it.isExcludedBy(state.excludedKeys, media) }
                for (candidate in verified) {
                    val verdict = policy.evaluate(candidate.genres) as? CandidateVerdict.Admitted ?: continue
                    state.admit(candidate, verdict)
                    if (state.matchedAdmitted.size >= state.target) break
                }
                // Settle pending cards without discarding them for a later page.
                if ((state.pendingItems.isNotEmpty() && budget.remainingDetails > 0) ||
                    state.matchedAdmitted.size >= state.target
                ) {
                    return
                }
            }
        }
    }
    if (!state.hasNextPage || state.pagesStopped || state.pagesFetched >= state.pageLimit) return
    // Without details a required-genre query can only use the list metadata it
    // already sees; walking the whole catalogue spends time without finding
    // anything new. Two pages keep a chance for sources that expose genres in lists.
    if (hydrate && budget != null && budget.remainingDetails == 0 &&
        state.pagesFetched >= MAX_DETAIL_FREE_PAGES
    ) {
        state.budgetExpired = true
        return
    }
    val remaining = if (hydrate) budget?.remainingMs() ?: timeoutMs else timeoutMs
    if (remaining <= 0L) {
        state.budgetExpired = true
        return
    }
    val page = state.nextPage
    val pageResult = withHardTimeoutOrNull(minOf(timeoutMs, remaining)) {
        catalog.popularWithGenresAllocated(
            mediaType = media,
            sourceId = state.sourceId,
            genres = genres,
            page = page,
            releaseStatuses = releaseStatuses,
            budget = if (hydrate) budget else null,
            maxDetails = if (hydrate) allowanceLeft else 0,
            excludedKeys = state.excludedKeys,
        )
    }
    if (pageResult == null) {
        state.transientFailures++
        state.timedOut = true
        state.pagesStopped = true
        return
    }
    state.pagesFetched++
    state.successfulPages++
    state.nextPage = page + 1
    state.verified += pageResult.verified
    state.metadataUnavailable += pageResult.metadataUnavailable
    state.transientFailures += pageResult.transientFailures
    state.detailsUsed += pageResult.detailsUsed
    val pending = pageResult.pending.filterNot { it.isExcludedBy(state.excludedKeys, media) }
    val existingPending = state.pendingItems.mapTo(HashSet()) { it.collectionKey() }
    val newPending = pending.filterNot { it.collectionKey() in existingPending }
    state.unattempted += (pageResult.unattempted - pageResult.pending.size).coerceAtLeast(0) + newPending.size
    state.pendingItems += newPending
    state.hasNextPage = pageResult.hasNextPage
    state.exhausted = !pageResult.hasNextPage
    if (pageResult.genreFilterApplied == true) {
        state.genreFilterApplied = true
    }
    val unique = pageResult.items.filter {
        state.seen.add(it.sourceUrl?.takeIf(String::isNotBlank) ?: it.cleanTitle)
    }
    if (policy.requiredGenres.isNotEmpty()) {
        state.matchedRequired += unique.count {
            it.genres.isNotEmpty() && policy.matchesRequired(it.genres)
        }
    }
    val eligible = unique.filterNot { it.isExcludedBy(state.excludedKeys, media) }
    for (candidate in eligible) {
        val verdict = policy.evaluate(candidate.genres) as? CandidateVerdict.Admitted ?: continue
        state.admit(candidate, verdict)
        if (state.matchedAdmitted.size >= state.target) break
    }
    genreQueryLog.logcat {
        "[DiscoveryGenreQuery] source=" + state.sourceId + " wave=" + wave + " page=" + page +
            " fetched=" + pageResult.items.size + " verified=" + pageResult.verified +
            " eligible=" + eligible.size + " total=" + state.totalAdmittedCount() + " target=" + state.target +
            " pending=" + state.pendingItems.size + " hasNext=" + state.hasNextPage +
            " genreFilter=" + pageResult.genreFilterApplied +
            " required=" + policy.requiredGenres +
            " sampleGenres=" + unique.take(3).map { it.genres.take(8) }
    }
}

internal fun GenreSourceVerification.toExhaustionEvidence() = tachiyomi.domain.discovery.model.DiscoveryQueryEvidence(
    queryKey = queryKey,
    transportOk = failure == null && stopReason != DiscoveryFillStopReason.RETRY_AFTER,
    rawEnd = exhausted,
    pending = pending,
    unattempted = summary.unattemptedCount,
    metadataFailures = summary.metadataUnavailableCount + summary.failedCount,
    budgetStopped = stopReason == DiscoveryFillStopReason.PAGE_BUDGET_EXHAUSTED ||
        stopReason == DiscoveryFillStopReason.PASS_BUDGET_EXHAUSTED,
    duplicatePageStopped = false,
)

internal fun DiscoveryRowItem.isExcludedBy(keys: Set<String>, media: DiscoveryMediaType): Boolean =
    keys.isNotEmpty() && (
        cleanTitle in keys || tachiyomi.domain.discovery.model.legacyDiscoveryKey(media, cleanTitle) in keys ||
            altCleanTitles.any { it in keys } ||
            tachiyomi.domain.discovery.model.canonicalDiscoveryKey(media, sourceId, sourceUrl, cleanTitle) in keys ||
            altCleanTitles.any { tachiyomi.domain.discovery.model.legacyDiscoveryKey(media, it) in keys }
        )

private fun DiscoveryRowItem.collectionKey(): String =
    sourceUrl?.takeIf(String::isNotBlank) ?: cleanTitle

/** A transport error during a genre search is not evidence of an empty catalogue. */
internal fun <T> Result<T>.getForDiscoveryGenreQuery(genres: List<String>?): T? =
    if (genres != null) getOrThrow() else getOrNull()
