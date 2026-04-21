package eu.kanade.tachiyomi.source.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal object NovelPluginImageWarmup {
    private const val MAX_FIRST_WINDOW = 12

    fun collectTargets(urls: Iterable<String?>): List<String> {
        return urls.asSequence()
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter(NovelPluginImage::isSupported)
            .distinct()
            .take(MAX_FIRST_WINDOW)
            .toList()
    }

    suspend fun warmup(
        urls: Iterable<String?>,
        resolve: suspend (String) -> Unit = { url ->
            NovelPluginImageResolver.resolve(url)
            Unit
        },
    ) {
        val targets = collectTargets(urls)
        if (targets.isEmpty()) return

        coroutineScope {
            targets
                .map { url ->
                    async(Dispatchers.IO) {
                        resolve(url)
                    }
                }
                .awaitAll()
        }
    }
}

@Composable
internal fun NovelPluginImageWarmupEffect(url: String?, lastModified: Long) {
    LaunchedEffect(url, lastModified) {
        NovelPluginImageWarmup.warmup(listOf(url))
    }
}
