package eu.kanade.tachiyomi.data.library

import android.app.ActivityManager
import android.content.Context

/**
 * Chooses how many source update workers run concurrently during background library updates.
 * Low-RAM / small-heap devices drop from 5 to 3 parallel sources to keep simultaneous HTML/JS
 * extension parsers and HTTP buffers within memory limits without starving throughput on
 * standard devices.
 */
internal object LibraryUpdateConcurrency {
    const val STANDARD_MAX_CONCURRENT_SOURCES = 5
    const val LOW_RAM_MAX_CONCURRENT_SOURCES = 3
    const val LOW_HEAP_THRESHOLD_BYTES = 256L * 1024L * 1024L

    fun maxConcurrentSources(context: Context): Int {
        val isLowRam = runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        }.getOrDefault(false)
        return resolveMaxConcurrentSources(
            isLowRamDevice = isLowRam,
            maxHeapBytes = Runtime.getRuntime().maxMemory(),
        )
    }

    internal fun resolveMaxConcurrentSources(
        isLowRamDevice: Boolean,
        maxHeapBytes: Long,
    ): Int {
        return if (isLowRamDevice || (maxHeapBytes in 1L..LOW_HEAP_THRESHOLD_BYTES)) {
            LOW_RAM_MAX_CONCURRENT_SOURCES
        } else {
            STANDARD_MAX_CONCURRENT_SOURCES
        }
    }
}
