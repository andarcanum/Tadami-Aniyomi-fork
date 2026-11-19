package eu.kanade.tachiyomi.data.cache

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import logcat.LogPriority
import logcat.logcat

/**
 * [ComponentCallbacks2] that evicts static in-memory entry screen state caches and
 * similar-title session caches under OS memory pressure ([TRIM_MEMORY_RUNNING_LOW]
 * and higher, or [onLowMemory]).
 *
 * Prevents process-lifetime caches holding large chapter/episode lists from pushing
 * low-RAM devices into OOM kills during backgrounding or heavy scrolling.
 */
class EntryMemoryCacheTrimCallbacks(
    private val clearCaches: () -> Unit = {
        MangaScreenModel.clearMemoryCaches()
        AnimeScreenModel.clearMemoryCaches()
        NovelScreenModel.clearMemoryCaches()
    },
) : ComponentCallbacks2 {

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        logcat(LogPriority.DEBUG) {
            "EntryMemoryCacheTrimCallbacks.onTrimMemory(level=$level): clearing entry state & suggestion caches"
        }
        clearCaches()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() {
        logcat(LogPriority.DEBUG) {
            "EntryMemoryCacheTrimCallbacks.onLowMemory(): clearing entry state & suggestion caches"
        }
        clearCaches()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op: only interested in memory-pressure events.
    }
}
