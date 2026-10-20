package eu.kanade.tachiyomi.data.suggestions

import kotlinx.coroutines.sync.Semaphore

/**
 * App-wide limit on extension search requests issued by the entry-screen suggestion fallbacks.
 *
 * Each engine already bounds its own fan-out, but those bounds are per fetch. Several screens
 * (manga, anime, novel) can have fallback work in flight at the same time — including screens
 * that were popped while a blocking extension call kept running — and the combined peak heap
 * inside extension code is what pushed the process over its limit in the 0.62.x crash logs.
 * Suggestions are decorative background work, so serializing the expensive part across media
 * types is cheaper than another fatal OOM.
 *
 * Only suggestion searches go through this gate; user-initiated global search and library
 * updates keep their own concurrency policies.
 */
internal object SuggestionSearchGate {

    private const val MAX_CONCURRENT_SEARCHES = 3

    private val semaphore = Semaphore(MAX_CONCURRENT_SEARCHES)

    suspend fun <T> withPermit(block: suspend () -> T): T {
        semaphore.acquire()
        try {
            return block()
        } finally {
            semaphore.release()
        }
    }
}
