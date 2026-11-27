package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import kotlin.math.abs

/**
 * Throttles periodic database writes during active playback while allowing immediate persistence
 * on episode switch, large seeks, pause, completion, or player teardown.
 */
internal class PlaybackProgressPersistencePolicy(
    private val minPersistIntervalMs: Long = DEFAULT_MIN_PERSIST_INTERVAL_MS,
    private val seekJumpThresholdMs: Long = DEFAULT_SEEK_JUMP_THRESHOLD_MS,
) {
    private var lastPersistedEpisodeId: Long = -1L
    private var lastPersistedPositionMs: Long = -1L
    private var lastPersistedElapsedMs: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldPersist(
        episodeId: Long,
        positionMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        if (episodeId <= 0L) return false
        if (episodeId != lastPersistedEpisodeId) return true
        if (lastPersistedElapsedMs == Long.MIN_VALUE) return true
        if (nowElapsedMs - lastPersistedElapsedMs >= minPersistIntervalMs) return true
        if (abs(positionMs - lastPersistedPositionMs) >= seekJumpThresholdMs) return true
        return false
    }

    @Synchronized
    fun recordPersisted(
        episodeId: Long,
        positionMs: Long,
        nowElapsedMs: Long,
    ) {
        lastPersistedEpisodeId = episodeId
        lastPersistedPositionMs = positionMs
        lastPersistedElapsedMs = nowElapsedMs
    }

    @Synchronized
    fun reset() {
        lastPersistedEpisodeId = -1L
        lastPersistedPositionMs = -1L
        lastPersistedElapsedMs = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_MIN_PERSIST_INTERVAL_MS = 10_000L
        const val DEFAULT_SEEK_JUMP_THRESHOLD_MS = 15_000L

        /**
         * Resolves the seek destination when skipping a chapter (OP/ED/Recap).
         * If the skipped chapter is the last chapter in the episode (commonly an Ending),
         * jumps near [duration] instead of seeking back to [currentPosition].
         */
        fun resolveChapterSkipTarget(
            chapters: List<IndexedSegment>,
            chapterIndex: Int,
            currentPosition: Float,
            duration: Float,
        ): Float {
            chapters.getOrNull(chapterIndex + 1)?.let { nextChapter ->
                return nextChapter.start
            }
            if (duration > 0f && duration > currentPosition + 1f) {
                return (duration - 1f).coerceAtLeast(currentPosition)
            }
            return duration.takeIf { it > currentPosition } ?: currentPosition
        }
    }
}
