package eu.kanade.tachiyomi.data.download.engine

/**
 * Aggregated download engine state exposed to the shared UI.
 *
 * All numeric fields are derived from the three backend queue states plus
 * the completion and speed trackers.  Fields are nullable when the
 * corresponding backend cannot truthfully provide the value (e.g. novel
 * byte throughput, anime-only sessions without a byte signal).
 */
data class DownloadEngineSnapshot(
    // --- queue counts ---
    val animeItems: Int = 0,
    val animeActive: Int = 0,
    val animeQueued: Int = 0,
    val animeFailed: Int = 0,
    val mangaItems: Int = 0,
    val mangaActive: Int = 0,
    val mangaQueued: Int = 0,
    val mangaFailed: Int = 0,
    val novelPending: Int = 0,
    val novelActive: Int = 0,
    val novelFailed: Int = 0,

    // --- running status ---
    val animeRunning: Boolean = false,
    val mangaRunning: Boolean = false,
    val novelRunning: Boolean = false,

    // --- session completion tally (persisted across queue removals) ---
    val sessionCompleted: Int = 0,

    // --- live speed & ETA ---
    val currentSpeedBps: Long? = null,
    val averageSpeedBps: Long? = null,
    val etaMillis: Long? = null,
    val speedHistoryBps: List<Long> = emptyList(),

    // --- per-section storage ---
    val animeStorageBytes: Long = 0L,
    val mangaStorageBytes: Long = 0L,
    val novelStorageBytes: Long = 0L,
    val freeSpaceBytes: Long? = null,
) {
    /** Whether any of the backends are currently running. */
    val isRunning: Boolean
        get() = (animeRunning && animeItems > 0) ||
            (mangaRunning && mangaItems > 0) ||
            (novelRunning && (novelPending + novelActive > 0))

    /** Items currently being processed across all sections. */
    val activeCount: Int
        get() = animeActive + mangaActive + novelActive

    /** Items waiting to be processed across all sections. */
    val queuedCount: Int
        get() = animeQueued + mangaQueued + novelPending

    /** Completed items that were removed from live queues this session. */
    val completedCount: Int
        get() = sessionCompleted

    /** Failed items across all sections. */
    val failedCount: Int
        get() = animeFailed + mangaFailed + novelFailed

    /** Downloaded storage currently accounted by all backends. */
    val totalStorageBytes: Long
        get() = animeStorageBytes + mangaStorageBytes + novelStorageBytes

    val hasWork: Boolean
        get() = activeCount + queuedCount + failedCount > 0
}
