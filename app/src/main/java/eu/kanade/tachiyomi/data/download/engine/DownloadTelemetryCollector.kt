package eu.kanade.tachiyomi.data.download.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Converts backend byte telemetry into rolling speed samples.
 *
 * Backends report cumulative bytes for a given download key. The collector
 * converts those cumulative values into deltas before pushing them into the
 * shared [DownloadSpeedTracker].
 */
class DownloadTelemetryCollector(
    private val speedTracker: DownloadSpeedTracker,
) : DownloadTelemetryEmitter {

    // C-L: the manga backend reports bytesTotal=0, so the completion-based removal at the end
    // of record() never ran for it and the map grew for the whole session. Bound it with an
    // access-order LRU - evicted entries are stale downloads whose next record simply re-baselines.
    private val lastBytesByKey = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_TRACKED_KEYS
    }
    private val _version = MutableStateFlow(0L)
    val version = _version.asStateFlow()

    @Synchronized
    override fun record(
        section: DownloadSection,
        downloadKey: String,
        bytesDownloaded: Long,
        bytesTotal: Long,
        timestampMs: Long,
    ) {
        if (bytesDownloaded <= 0L) return

        val telemetryKey = buildString {
            append(section.name)
            append(':')
            append(downloadKey)
        }

        val previousBytes = lastBytesByKey[telemetryKey]
        if (previousBytes == null || bytesDownloaded < previousBytes) {
            lastBytesByKey[telemetryKey] = bytesDownloaded
            return
        }

        val deltaBytes = bytesDownloaded - previousBytes
        if (deltaBytes <= 0L) return

        lastBytesByKey[telemetryKey] = bytesDownloaded
        speedTracker.pushSample(
            DownloadSpeedSample(
                section = section,
                bytesDelta = deltaBytes,
                timestampMs = timestampMs,
            ),
        )
        _version.update { it + 1L }

        if (bytesTotal > 0L && bytesDownloaded >= bytesTotal) {
            lastBytesByKey.remove(telemetryKey)
        }
    }

    private companion object {
        const val MAX_TRACKED_KEYS = 1024
    }
}
