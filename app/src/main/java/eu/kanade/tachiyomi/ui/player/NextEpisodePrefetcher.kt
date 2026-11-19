package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Hoster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout

/**
 * Silently prefetches hosters for the next episode near the end of the current episode so
 * next-episode transitions and autoplay start without waiting on source HTTP round-trips.
 */
internal class NextEpisodePrefetcher(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private data class CachedHosters(
        val episodeId: Long,
        val hosters: List<Hoster>,
        val cachedAtMs: Long,
    )

    private val lock = Any()
    private var cached: CachedHosters? = null
    private var inFlightEpisodeId: Long? = null
    private var inFlightJob: Deferred<List<Hoster>?>? = null

    fun prefetchIfNeeded(
        scope: CoroutineScope,
        episodeId: Long,
        loader: suspend () -> List<Hoster>,
    ) {
        if (episodeId <= 0L) return
        val now = clockMs()
        synchronized(lock) {
            val currentCached = cached
            if (currentCached != null && currentCached.episodeId == episodeId && (now - currentCached.cachedAtMs) < ttlMs) {
                return
            }
            if (inFlightEpisodeId == episodeId && inFlightJob?.isActive == true) {
                return
            }
            inFlightJob?.cancel()
            inFlightEpisodeId = episodeId
            inFlightJob = scope.async(ioDispatcher) {
                try {
                    val result = withTimeout(timeoutMs) {
                        loader()
                    }
                    if (result.isNotEmpty()) {
                        synchronized(lock) {
                            cached = CachedHosters(
                                episodeId = episodeId,
                                hosters = result,
                                cachedAtMs = clockMs(),
                            )
                        }
                        result
                    } else {
                        null
                    }
                } catch (_: TimeoutCancellationException) {
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                } finally {
                    synchronized(lock) {
                        if (inFlightEpisodeId == episodeId) {
                            inFlightEpisodeId = null
                            inFlightJob = null
                        }
                    }
                }
            }
        }
    }

    suspend fun consumeOrNull(episodeId: Long): List<Hoster>? {
        if (episodeId <= 0L) return null
        val now = clockMs()
        val pendingJob: Deferred<List<Hoster>?>?
        synchronized(lock) {
            val currentCached = cached
            if (currentCached != null && currentCached.episodeId == episodeId) {
                cached = null
                if ((now - currentCached.cachedAtMs) < ttlMs) {
                    return currentCached.hosters
                }
            }
            pendingJob = if (inFlightEpisodeId == episodeId) inFlightJob else null
        }

        val result = try {
            pendingJob?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }

        if (result != null) {
            synchronized(lock) {
                if (cached?.episodeId == episodeId) {
                    cached = null
                }
            }
        }
        return result
    }

    fun cancelAndClear() {
        synchronized(lock) {
            inFlightJob?.cancel()
            inFlightJob = null
            inFlightEpisodeId = null
            cached = null
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 10L * 60L * 1000L
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val PREFETCH_PROGRESS_THRESHOLD = 0.75
    }
}
