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

/** Per-item outcome of a details lookup, reported separately from admission. */
internal data class HydrationResult(
    val items: List<DiscoveryRowItem>,
    val verified: Int,
    val metadataUnavailable: Int,
    val transientFailures: Int,
    val unattempted: Int,
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
        fetchDetails: suspend (String) -> List<String>,
    ): HydrationResult = coroutineScope {
        val outcomes = HashMap<String, Entry>()
        val pending = ArrayList<Pair<String, Key>>()
        val scheduled = HashSet<String>()

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
            if (pending.size >= maxDetailsPerCall || !budget.tryAcquireDetail()) continue
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
                    val entry = budget.detailPermits.withPermit {
                        val timeout = minOf(detailTimeoutMs, budget.remainingMs().coerceAtLeast(1L))
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
                    cache.put(key, entry)
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
        HydrationResult(
            items = resolvedItems,
            verified = verified,
            metadataUnavailable = metadataUnavailable,
            transientFailures = transientFailures,
            unattempted = unattempted,
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
