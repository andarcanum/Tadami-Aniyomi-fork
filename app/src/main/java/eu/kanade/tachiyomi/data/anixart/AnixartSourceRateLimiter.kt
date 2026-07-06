package eu.kanade.tachiyomi.data.anixart

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-source request spacing for catalogue search during bulk import.
 * Each [sourceId] has its own [Mutex] so parallel row matching cannot
 * race on read-modify-write of last-request timestamps.
 */
class AnixartSourceRateLimiter(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
) {
    private val mutexBySource = ConcurrentHashMap<Long, Mutex>()
    private val lastRequestAt = ConcurrentHashMap<Long, Long>()

    suspend fun <T> withRateLimit(sourceId: Long, block: suspend () -> T): T {
        val mutex = mutexBySource.computeIfAbsent(sourceId) { Mutex() }
        return mutex.withLock {
            val last = lastRequestAt[sourceId] ?: 0L
            val wait = minIntervalMs - (System.currentTimeMillis() - last)
            if (wait > 0) delay(wait)
            try {
                block()
            } finally {
                lastRequestAt[sourceId] = System.currentTimeMillis()
            }
        }
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 400L
    }
}
