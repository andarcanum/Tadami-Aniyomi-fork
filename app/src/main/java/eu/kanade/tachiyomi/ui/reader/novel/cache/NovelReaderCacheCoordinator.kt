package eu.kanade.tachiyomi.ui.reader.novel.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Lightweight global cache budget enforcer for novel reader caches.
 *
 * Registered caches report their current size. When the global budget is
 * exceeded, the coordinator trims caches starting with the oldest (by
 * registration order) until the budget is satisfied.
 */
class NovelReaderCacheCoordinator(
    private val maxTotalBytes: Long,
    private val nowMs: () -> Long = System::currentTimeMillis,
    budgetExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NovelCacheBudget").apply { isDaemon = true }
    },
) {
    private data class RegisteredCache(
        val reporter: NovelReaderCacheReporter,
        val registeredAtMs: Long,
        // Monotonic tie-break so equal wall-clock timestamps still preserve registration order.
        val registrationSeq: Long,
    )

    private val caches = ConcurrentHashMap<String, RegisteredCache>()
    private val registrationSeq = AtomicLong(0L)

    /**
     * Size accounting touches the disk (every reporter walks its cache directory), so it must
     * never run on the caller's thread: the first cache store initializes on the main thread while
     * a chapter is loading, and a synchronous walk there blocked the reader for tens of seconds
     * (ANR watchdog: NovelBookSectionDiskCacheStore$1.currentBytes on the main thread).
     */
    private val enforcementExecutor = budgetExecutor
    private val shutdownableExecutor = budgetExecutor as? java.util.concurrent.ExecutorService

    fun register(reporter: NovelReaderCacheReporter) {
        caches[reporter.cacheId()] = RegisteredCache(
            reporter = reporter,
            registeredAtMs = nowMs(),
            registrationSeq = registrationSeq.incrementAndGet(),
        )
        enforcementExecutor.execute { enforceBudget() }
    }

    fun unregister(cacheId: String) {
        caches.remove(cacheId)
    }

    fun totalBytes(): Long = caches.values.sumOf { it.reporter.currentBytes() }

    fun enforceBudget() {
        // Snapshot every cache size once: the reporters walk their directories, so re-summing the
        // whole set after each trim would multiply the disk work for nothing.
        val sizes = caches.values.associateTo(mutableMapOf()) { cache ->
            cache.reporter.cacheId() to cache.reporter.currentBytes()
        }
        var total = sizes.values.sum()
        var excess = total - maxTotalBytes
        if (excess <= 0) return

        // Trim oldest-first (by registration time; sequence breaks timestamp ties).
        val sorted = caches.values.sortedWith(
            compareBy({ it.registeredAtMs }, { it.registrationSeq }),
        )
        for (entry in sorted) {
            if (excess <= 0) break
            val before = sizes[entry.reporter.cacheId()] ?: continue
            val target = max(0L, before - excess)
            if (target >= before) continue
            entry.reporter.trimToTargetBytes(target)
            // Only the trimmed cache is re-measured; the running total follows its delta.
            val after = entry.reporter.currentBytes()
            total -= (before - after).coerceAtLeast(0L)
            sizes[entry.reporter.cacheId()] = after
            excess = total - maxTotalBytes
        }
    }

    fun dispose() {
        caches.values.forEach { it.reporter.dispose() }
        caches.clear()
        shutdownableExecutor?.shutdown()
    }
}

/**
 * Implemented by each cache that participates in the global budget.
 */
interface NovelReaderCacheReporter {
    /** Stable identifier for this cache (e.g. "chapter-disk", "translation-disk"). */
    fun cacheId(): String

    /** Current bytes used by this cache (disk or memory). */
    fun currentBytes(): Long

    /** Trim this cache to at most [targetBytes]. */
    fun trimToTargetBytes(targetBytes: Long)

    /** Release all resources. Default no-op. */
    fun dispose() {}
}
