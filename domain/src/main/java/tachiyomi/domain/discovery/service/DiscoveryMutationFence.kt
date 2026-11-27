package tachiyomi.domain.discovery.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared per-media mutation fence. Native library/history snapshots are taken
 * under this fence before a shared Discovery transaction, and every write that
 * can change the known-title index takes it too.
 *
 * One process-wide instance avoids per-call-site mutexes that would not
 * exclude each other. The lock is not reentrant: callers must not nest
 * acquisitions for the same media type.
 */
object DiscoveryMutationFence {
    private val locks = ConcurrentHashMap<DiscoveryMediaType, Mutex>()

    private fun lockFor(media: DiscoveryMediaType): Mutex = locks.computeIfAbsent(media) { Mutex() }

    suspend fun <T> withMutation(media: DiscoveryMediaType, block: suspend () -> T): T =
        lockFor(media).withLock { block() }

    suspend fun <T> withMutations(media: Set<DiscoveryMediaType>, block: suspend () -> T): T {
        val ordered = media.sortedBy { it.key }.map(::lockFor)
        return acquireInOrder(ordered, 0, block)
    }

    private suspend fun <T> acquireInOrder(locks: List<Mutex>, index: Int, block: suspend () -> T): T =
        if (index == locks.size) {
            block()
        } else {
            locks[index].withLock { acquireInOrder(locks, index + 1, block) }
        }
}
