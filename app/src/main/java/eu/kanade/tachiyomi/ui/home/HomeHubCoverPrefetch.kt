package eu.kanade.tachiyomi.ui.home

import android.app.Application
import coil3.SingletonImageLoader
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Warms Coil's caches (memory + disk / library cover cache) for home hub
 * covers so a cold start without network can still render them instead of
 * falling back to placeholders.
 *
 * Requests are enqueued in small batches: the first batch goes out right away
 * so above-the-fold covers start loading immediately, while later batches are
 * deferred so background prefetch never monopolizes the fetcher slots needed
 * by covers the user is actively scrolling through (browse, library, search).
 *
 * Safe to call from any thread and survives the caller's lifecycle: the
 * deferred batches run on their own scope via enqueued requests.
 */
internal fun prefetchHomeHubCovers(
    covers: List<Any?>,
    staggerDelayMs: Long = BATCH_DELAY_MS,
    scope: CoroutineScope = PREFETCH_SCOPE,
) {
    val appContext = runCatching { Injekt.get<Application>() }.getOrNull() ?: return
    val imageLoader = runCatching { SingletonImageLoader.get(appContext) }.getOrNull() ?: return

    val candidates = covers.asSequence()
        .filterNotNull()
        .distinct()
        .map { data -> buildAuroraCoverImageRequest(appContext, data) }
        .toList()

    val (firstBatch, deferredBatches) = splitIntoPrefetchBatches(candidates)

    // First batch: synchronous enqueue, no startup delay.
    firstBatch.forEach { request ->
        runCatching { imageLoader.enqueue(request) }
    }

    // Later batches: staggered so each wave starts only after the previous
    // one had time to finish, keeping fetcher slots free for foreground loads.
    if (deferredBatches.isNotEmpty()) {
        scope.launch {
            deferredBatches.forEachIndexed { index, batch ->
                delay(staggerDelayMs * (index + 1))
                batch.forEach { request ->
                    runCatching { imageLoader.enqueue(request) }
                }
            }
        }
    }
}

/**
 * Splits prefetch candidates into the immediate first batch and staggered
 * follow-up batches, applying the overall prefetch cap. Pure function so the
 * batching policy stays unit-testable without coroutines or Android.
 */
internal fun <T> splitIntoPrefetchBatches(candidates: List<T>): Pair<List<T>, List<List<T>>> {
    val capped = candidates.take(MAX_PREFETCH_COVERS)
    return Pair(
        first = capped.take(PREFETCH_FIRST_BATCH_SIZE),
        second = capped.drop(PREFETCH_FIRST_BATCH_SIZE).chunked(PREFETCH_BATCH_SIZE),
    )
}

private val PREFETCH_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Covers enqueued immediately, without the stagger delay. */
internal const val PREFETCH_FIRST_BATCH_SIZE = 12

/** Covers per deferred batch. */
private const val PREFETCH_BATCH_SIZE = 12

/** Base delay between deferred batches. */
private const val BATCH_DELAY_MS = 750L

private const val MAX_PREFETCH_COVERS = 60
