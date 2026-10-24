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

internal enum class GenreSourceFailure { TIMEOUT, METADATA_UNAVAILABLE, SOURCE_ERROR }

internal class GenreSourceException(val sourceId: Long, val reason: GenreSourceFailure) :
    IOException("Genre source $sourceId: $reason")

private val genreQueryLog = Any()

/**
 * Limit concurrency, not participation. Each selected source gets a bounded
 * query attempt; the shared [budget] caps the whole row.
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
    budget: DiscoveryVerificationBudget? = null,
): List<GenreSourceVerification> = supervisorScope {
    require(maxConcurrency > 0 && timeoutMs > 0L)
    val permits = Semaphore(maxConcurrency)
    sourceIds.filter { it > 0L }.distinct().map { id ->
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
    budget: DiscoveryVerificationBudget?,
): GenreSourceVerification {
    // Only the verifying path can spend the details allowance, so only it has to
    // stop when the shared budget is gone. The cheap path keeps its old shape.
    if (hydrate && budget != null && budget.isExpired()) {
        // No useful work can be done inside the remaining media budget.
        return GenreSourceVerification(
            sourceId = sourceId,
            summary = GenreVerificationSummary.empty(unattempted = 1),
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
            budget = budget,
        )
        val failure = when {
            collected.timedOutBeforeAnyPage -> GenreSourceException(sourceId, GenreSourceFailure.TIMEOUT)
            collected.summary.outcome == GenreVerificationOutcome.FAILED ->
                GenreSourceException(sourceId, GenreSourceFailure.METADATA_UNAVAILABLE)
            else -> null
        }
        GenreSourceVerification(sourceId, collected.summary, failure)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        GenreSourceVerification(
            sourceId = sourceId,
            summary = GenreVerificationSummary.empty(failed = 1),
            failure = GenreSourceException(sourceId, GenreSourceFailure.SOURCE_ERROR),
        )
    }
}

private data class SourceCollection(
    val summary: GenreVerificationSummary,
    val timedOutBeforeAnyPage: Boolean,
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
    budget: DiscoveryVerificationBudget?,
): SourceCollection {
    val seen = HashSet<String>()
    var verified = 0
    var unattempted = 0
    var metadataUnavailable = 0
    var transientFailures = 0
    var successfulPages = 0
    var timedOut = false
    var selected = emptyList<DiscoveryRowItem>()
    val pageBudget = if (policy.isStrictRequired && policy.requiredGenres.isNotEmpty()) 2 else 1

    for (offset in 0 until pageBudget) {
        val remaining = if (hydrate) budget?.remainingMs() ?: timeoutMs else timeoutMs
        if (remaining <= 0L) {
            unattempted += 1
            break
        }
        val pageResult = withTimeoutOrNull(minOf(timeoutMs, remaining)) {
            if (hydrate) {
                catalog.popularWithGenresVerified(
                    mediaType = media,
                    sourceId = sourceId,
                    genres = genres,
                    page = page + offset,
                    releaseStatuses = releaseStatuses,
                    budget = budget,
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

        val unique = pageResult.items.filter {
            seen.add(it.sourceUrl?.takeIf(String::isNotBlank) ?: it.cleanTitle)
        }
        if (unique.isEmpty()) break

        selected = policy.filterRowCandidates(unique, { it.genres })
        genreQueryLog.logcat {
            "[DiscoveryGenreQuery] source=$sourceId page=${page + offset} fetched=${pageResult.items.size} " +
                "verified=${pageResult.verified} unattempted=${pageResult.unattempted} " +
                "unavailable=${pageResult.metadataUnavailable} failures=${pageResult.transientFailures} " +
                "admitted=${selected.size} required=${policy.requiredGenres} " +
                "sampleGenres=${unique.take(3).map { it.genres.take(8) }}"
        }
        if (selected.isNotEmpty()) break
    }

    if (timedOut) {
        // A timeout after at least one successful page is a coverage gap, not a
        // total failure: we already learned something about the source.
        if (successfulPages == 0) transientFailures += 1 else unattempted += 1
    }
    return SourceCollection(
        summary = GenreVerificationSummary(
            items = selected,
            verifiedCount = verified,
            unattemptedCount = unattempted,
            metadataUnavailableCount = metadataUnavailable,
            failedCount = transientFailures,
        ),
        timedOutBeforeAnyPage = timedOut && successfulPages == 0,
    )
}

/** A transport error during a genre search is not evidence of an empty catalogue. */
internal fun <T> Result<T>.getForDiscoveryGenreQuery(genres: List<String>?): T? =
    if (genres != null) getOrThrow() else getOrNull()
