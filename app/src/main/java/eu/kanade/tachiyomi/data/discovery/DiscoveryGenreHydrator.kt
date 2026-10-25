package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import java.util.concurrent.atomic.AtomicInteger

/** Per-item outcome of a details lookup, reported separately from admission. */
internal data class HydrationResult(
    val items: List<DiscoveryRowItem>,
    val verified: Int,
    val metadataUnavailable: Int,
    val transientFailures: Int,
    val unattempted: Int,
    /** Cards that still need details: unattempted, addressable and not negatively cached. */
    val pending: List<DiscoveryRowItem> = emptyList(),
    /** Details requests actually charged during this call. */
    val detailsUsed: Int = 0,
)

/**
 * Fills missing genres from source details without ever substituting the genres
 * requested by a search.
 *
 * The cache distinguishes three states so a failed request is not remembered as
 * "this title does not match": resolved genres live long, absent metadata and
 * transient failures live briefly, and cancellation is never cached.
 */
internal class DiscoveryGenreHydrator(
    private val maxDetailsPerCall: Int = 8,
    private val detailTimeoutMs: Long = 1_500L,
    private val positiveTtlMs: Long = 30 * 60_000L,
    private val metadataUnavailableTtlMs: Long = 10 * 60_000L,
    private val transientTtlMs: Long = 2 * 60_000L,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private enum class Kind { RESOLVED, METADATA_UNAVAILABLE, TRANSIENT }
    private data class Key(val media: DiscoveryMediaType, val source: Long, val url: String)
    private data class Entry(val genres: List<String>, val at: Long, val kind: Kind)

    private val cache = DiscoveryLruCache<Key, Entry>(256)
    private val locks = Array(32) { Mutex() }

    init {
        require(maxDetailsPerCall > 0 && detailTimeoutMs > 0L)
        require(positiveTtlMs > 0L && metadataUnavailableTtlMs > 0L && transientTtlMs > 0L)
    }

    suspend fun hydrate(
        media: DiscoveryMediaType,
        sourceId: Long,
        items: List<DiscoveryRowItem>,
        budget: DiscoveryVerificationBudget,
        /** Remaining allowance of the whole source; one call never exceeds [maxDetailsPerCall]. */
        maxDetails: Int = maxDetailsPerCall,
        fetchDetails: suspend (String) -> List<String>,
    ): HydrationResult = coroutineScope {
        require(maxDetails >= 0) { "maxDetails must be >= 0" }
        val batchLimit = minOf(maxDetails, maxDetailsPerCall)
        val outcomes = HashMap<String, Entry>()
        val pending = ArrayList<Pair<String, Key>>()
        val scheduled = HashSet<String>()
        val charged = AtomicInteger()

        for (item in items) {
            if (item.genres.isNotEmpty()) continue
            val url = item.sourceUrl
            if (url.isNullOrBlank()) continue
            val key = Key(media, sourceId, url)
            val cached = cache.get(key)
            if (cached != null && isFresh(cached)) {
                outcomes[url] = cached
                continue
            }
            if (!scheduled.add(url)) continue
            if (budget.isExpired()) continue
            if (pending.size >= batchLimit) continue
            pending += url to key
        }

        val fetched = pending.map { (url, key) ->
            async {
                val mutex = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]
                mutex.withLock {
                    val cached = cache.get(key)
                    if (cached != null && isFresh(cached)) {
                        return@withLock url to cached
                    }
                    val entry: Entry? = budget.detailPermits.withPermit {
                        val remaining = budget.remainingMs()
                        // Charge only the real lookup, after coalescing and after waiting for IO capacity.
                        if (remaining <= 0L || !budget.tryAcquireDetail()) return@withPermit null
                        charged.incrementAndGet()
                        val timeout = minOf(detailTimeoutMs, remaining)
                        try {
                            val observed = withTimeoutOrNull(timeout) { fetchDetails(url) }
                            when {
                                observed == null -> Entry(emptyList(), clock(), Kind.TRANSIENT)
                                else -> {
                                    val clean = observed.map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .distinct()
                                    if (clean.isEmpty()) {
                                        Entry(emptyList(), clock(), Kind.METADATA_UNAVAILABLE)
                                    } else {
                                        Entry(clean, clock(), Kind.RESOLVED)
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            Entry(emptyList(), clock(), Kind.TRANSIENT)
                        }
                    }
                    if (entry != null) cache.put(key, entry)
                    url to entry
                }
            }
        }.awaitAll().toMap()

        var verified = 0
        var metadataUnavailable = 0
        var transientFailures = 0
        var unattempted = 0
        val resolvedItems = items.map { item ->
            when {
                item.genres.isNotEmpty() -> {
                    verified++
                    item
                }
                item.sourceUrl.isNullOrBlank() -> {
                    unattempted++
                    item
                }
                else -> when (val entry = outcomes[item.sourceUrl] ?: fetched[item.sourceUrl]) {
                    null -> {
                        unattempted++
                        item
                    }
                    else -> when (entry.kind) {
                        Kind.RESOLVED -> {
                            verified++
                            item.copy(genres = entry.genres)
                        }
                        Kind.METADATA_UNAVAILABLE -> {
                            metadataUnavailable++
                            item
                        }
                        Kind.TRANSIENT -> {
                            transientFailures++
                            item
                        }
                    }
                }
            }
        }
        // Pending keeps only cards that can still be retried in another batch:
        // cache entries (resolved or negative) already settled their outcome.
        val pendingItems = LinkedHashMap<String, DiscoveryRowItem>()
        for (item in items) {
            if (item.genres.isNotEmpty()) continue
            val url = item.sourceUrl
            if (url.isNullOrBlank()) continue
            if (url in outcomes || url in fetched) continue
            pendingItems.putIfAbsent(url, item)
        }
        HydrationResult(
            items = resolvedItems,
            verified = verified,
            metadataUnavailable = metadataUnavailable,
            transientFailures = transientFailures,
            unattempted = unattempted,
            pending = pendingItems.values.toList(),
            detailsUsed = charged.get(),
        )
    }

    private fun isFresh(entry: Entry): Boolean {
        val ttl = when (entry.kind) {
            Kind.RESOLVED -> positiveTtlMs
            Kind.METADATA_UNAVAILABLE -> metadataUnavailableTtlMs
            Kind.TRANSIENT -> transientTtlMs
        }
        return clock() - entry.at in 0 until ttl
    }
}

/**
 * Spends the source allowance in bounded details batches until it is exhausted
 * or no card can be retried. Pending cards are fed back into the next batch, so
 * a page larger than one batch is verified without re-fetching the page.
 */
internal suspend fun hydrateDiscoveryBatchUntilSettled(
    items: List<DiscoveryRowItem>,
    maxDetails: Int,
    hydrateBatch: suspend (List<DiscoveryRowItem>, Int) -> HydrationResult,
): DiscoveryGenrePage {
    if (items.isEmpty()) return DiscoveryGenrePage(emptyList(), 0, 0, 0, 0)
    val merged = LinkedHashMap<String, DiscoveryRowItem>()
    items.forEach { merged.putIfAbsent(it.hydrationKey(), it) }
    var current = items
    var remaining = maxDetails.coerceAtLeast(0)
    var verified = 0
    var metadataUnavailable = 0
    var transientFailures = 0
    var detailsUsed = 0
    var pending = emptyList<DiscoveryRowItem>()

    while (true) {
        val result = hydrateBatch(current, remaining)
        detailsUsed += result.detailsUsed
        remaining = (remaining - result.detailsUsed).coerceAtLeast(0)
        verified += result.verified
        metadataUnavailable += result.metadataUnavailable
        transientFailures += result.transientFailures
        result.items.forEach { resolved ->
            if (resolved.genres.isNotEmpty()) merged[resolved.hydrationKey()] = resolved
        }
        pending = result.pending
        if (result.detailsUsed == 0 || pending.isEmpty() || remaining == 0) break
        current = pending
    }

    val allItems = items.map { merged[it.hydrationKey()] ?: it }
    val unattempted = pending.size + items.count { it.genres.isEmpty() && it.sourceUrl.isNullOrBlank() }
    return DiscoveryGenrePage(
        items = allItems,
        verified = verified,
        unattempted = unattempted,
        metadataUnavailable = metadataUnavailable,
        transientFailures = transientFailures,
        pending = pending,
        detailsUsed = detailsUsed,
    )
}

private fun DiscoveryRowItem.hydrationKey(): String =
    sourceUrl?.takeIf(String::isNotBlank) ?: cleanTitle
