package eu.kanade.tachiyomi.ui.home

import android.app.Application
import coil3.SingletonImageLoader
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Warms Coil's caches (memory + disk / library cover cache) for home hub
 * covers so a cold start without network can still render them instead of
 * falling back to placeholders.
 *
 * Requests are enqueued on the image loader's own scope, so this is safe to
 * call from any thread and survives the caller's lifecycle.
 */
internal fun prefetchHomeHubCovers(covers: List<Any?>) {
    val appContext = runCatching { Injekt.get<Application>() }.getOrNull() ?: return
    val imageLoader = runCatching { SingletonImageLoader.get(appContext) }.getOrNull() ?: return
    covers.asSequence()
        .filterNotNull()
        .distinct()
        .take(MAX_PREFETCH_COVERS)
        .forEach { data ->
            runCatching {
                imageLoader.enqueue(buildAuroraCoverImageRequest(appContext, data))
            }
        }
}

private const val MAX_PREFETCH_COVERS = 60
