package eu.kanade.tachiyomi.extension.novel.runtime

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory
import logcat.LogPriority
import logcat.logcat

/**
 * [ComponentCallbacks2] that trims novel plugin runtime caches when the
 * system requests memory to be freed.
 *
 * Registered once from [Application.onCreate] and survives for the process
 * lifetime.  Calls [NovelPluginSourceFactory.clearRuntimeCaches] on
 * [onTrimMemory] levels that indicate real memory pressure
 * ([TRIM_MEMORY_RUNNING_LOW], [TRIM_MEMORY_RUNNING_CRITICAL],
 * [TRIM_MEMORY_MODERATE]) and on [onLowMemory].
 *
 * The manual settings action (`SettingsAdvancedScreen`) is kept as-is;
 * this callback adds automatic cleanup.
 */
class NovelRuntimeCacheTrimCallbacks(
    private val sourceFactory: NovelPluginSourceFactory,
) : ComponentCallbacks2 {

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        logcat(LogPriority.DEBUG) {
            "NovelRuntimeCacheTrimCallbacks.onTrimMemory(level=$level): clearing plugin runtime caches"
        }
        sourceFactory.clearRuntimeCaches()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() {
        logcat(LogPriority.DEBUG) {
            "NovelRuntimeCacheTrimCallbacks.onLowMemory(): clearing plugin runtime caches"
        }
        sourceFactory.clearRuntimeCaches()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op: only interested in memory-pressure events.
    }
}
