package eu.kanade.tachiyomi.data.download.engine

/**
 * Maintains a rolling window of throughput samples and computes aggregate
 * current speed, average speed, and ETA.
 *
 * Current speed is derived from the total bytes / total time across the
 * rolling window, which naturally smooths jitter and outlier spikes.
 * Average speed is cumulative across all samples ever recorded.
 *
 * @param windowSize maximum number of samples kept in each section's rolling window
 */
class DownloadSpeedTracker(
    private val windowSize: Int = 60,
) {

    /** Per-section ring buffer of samples. */
    private val samplesBySection = mutableMapOf<DownloadSection, ArrayDeque<DownloadSpeedSample>>()

    /** Per-section cumulative totals for average calculation. */
    private val totalBytesBySection = mutableMapOf<DownloadSection, Long>()
    private val totalDurationMsBySection = mutableMapOf<DownloadSection, Long>()
    private val totalSamplesBySection = mutableMapOf<DownloadSection, Int>()

    /** Aggregate rolling history used by the sparkline. */
    private val aggregateSpeedHistory = ArrayDeque<Long>(windowSize)

    /**
     * Record a single throughput sample.
     *
     * @param sample one delta sample from a backend byte loop
     */
    @Synchronized
    fun pushSample(sample: DownloadSpeedSample) {
        // Ignore invalid deltas
        if (sample.bytesDelta <= 0L) return

        val section = sample.section
        val deque = samplesBySection.getOrPut(section) { ArrayDeque(windowSize) }

        // Maintain rolling window
        while (deque.size >= windowSize) {
            deque.removeFirst()
        }
        deque.addLast(sample)

        // Update cumulative stats for average
        totalBytesBySection[section] = (totalBytesBySection[section] ?: 0L) + sample.bytesDelta
        totalSamplesBySection[section] = (totalSamplesBySection[section] ?: 0) + 1

        // Update duration: time between this and the previous sample
        if (deque.size >= 2) {
            val prev = deque[deque.size - 2]
            val deltaMs = sample.timestampMs - prev.timestampMs
            if (deltaMs > 0L) {
                totalDurationMsBySection[section] = (totalDurationMsBySection[section] ?: 0L) + deltaMs
            }
        }

        windowCurrentSpeed(samplesBySection.values.flatten().sortedBy { it.timestampMs }).let { currentSpeed ->
            if (currentSpeed != null) {
                while (aggregateSpeedHistory.size >= windowSize) {
                    aggregateSpeedHistory.removeFirst()
                }
                aggregateSpeedHistory.addLast(currentSpeed)
            }
        }
    }

    /**
     * Compute and return a [TrackerSnapshot] from the current state.
     *
     * @param remainingBytes if non-null and positive, ETA is computed
     */
    @Synchronized
    fun snapshot(remainingBytes: Long? = null): TrackerSnapshot {
        // Sample count from window deques, not cumulative totals
        val totalSamples = samplesBySection.values.sumOf { it.size }

        // Per-section current speed: total bytes in window / total time in window
        val perSectionSpeed = samplesBySection.mapValues { (_, deque) ->
            windowCurrentSpeed(deque)
        }.filterValues { it != null }.mapValues { it.value!! }

        // Aggregate current speed: total bytes across all section windows / total time
        val aggregateCurrentSpeed = if (totalSamples > 0) {
            val allSamples = samplesBySection.values.flatten().sortedBy { it.timestampMs }
            if (allSamples.size >= 2) {
                windowCurrentSpeed(allSamples)
            } else {
                null
            }
        } else {
            null
        }

        // Aggregate average speed: total bytes / total duration (cumulative)
        val aggregateDurationMs = totalDurationMsBySection.values.sum()
        val aggregateAverageSpeed = if (totalSamples > 0 && aggregateDurationMs > 0L) {
            val totalBytes = totalBytesBySection.values.sum()
            (totalBytes.toDouble() * 1000.0 / aggregateDurationMs.toDouble()).toLong()
        } else {
            null
        }

        val eta = if (remainingBytes != null &&
            remainingBytes > 0L &&
            aggregateAverageSpeed != null &&
            aggregateAverageSpeed > 0L
        ) {
            remainingBytes * 1000L / aggregateAverageSpeed
        } else {
            null
        }

        return TrackerSnapshot(
            sampleCount = totalSamples,
            currentSpeedBps = aggregateCurrentSpeed,
            averageSpeedBps = aggregateAverageSpeed,
            etaMillis = eta,
            perSectionSpeed = perSectionSpeed,
            speedHistoryBps = aggregateSpeedHistory.toList(),
        )
    }

    /**
     * Clears all rolling and cumulative statistics. Call when the queue becomes
     * empty or when starting a new user-visible download session.
     */
    @Synchronized
    fun reset() {
        samplesBySection.clear()
        totalBytesBySection.clear()
        totalDurationMsBySection.clear()
        totalSamplesBySection.clear()
        aggregateSpeedHistory.clear()
    }

    /**
     * Compute current speed from a list of samples: total bytes / total time.
     * Returns null if fewer than 2 samples.
     */
    private fun windowCurrentSpeed(samples: List<DownloadSpeedSample>): Long? {
        if (samples.size < 2) return null
        val orderedSamples = samples.sortedBy { it.timestampMs }
        val totalBytes = orderedSamples.sumOf { it.bytesDelta }
        val totalTime = orderedSamples.last().timestampMs - orderedSamples.first().timestampMs
        return if (totalTime > 0L) totalBytes * 1000L / totalTime else null
    }

    data class TrackerSnapshot(
        val sampleCount: Int,
        val currentSpeedBps: Long?,
        val averageSpeedBps: Long?,
        val etaMillis: Long?,
        val perSectionSpeed: Map<DownloadSection, Long>,
        val speedHistoryBps: List<Long>,
    )
}
