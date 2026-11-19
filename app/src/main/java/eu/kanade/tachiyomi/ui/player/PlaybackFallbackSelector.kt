package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState

internal object PlaybackFallbackSelector {
    const val MAX_AUTO_FALLBACK_ATTEMPTS = 3

    /**
     * Selects the next viable (hosterIndex, videoIndex) after a runtime MPV playback error.
     *
     * Priority:
     * 1. Another viable video inside the same hoster (preserves user's chosen dub/mirror family).
     * 2. A preferred viable video from another ready hoster.
     * 3. The first viable video from any ready hoster.
     *
     * Returns null once [maxAttempts] is reached or no untried viable videos remain.
     */
    fun selectNext(
        hosterStates: List<HosterState>,
        failedIndex: Pair<Int, Int>,
        attemptedIndices: Set<Pair<Int, Int>>,
        maxAttempts: Int = MAX_AUTO_FALLBACK_ATTEMPTS,
    ): Pair<Int, Int>? {
        if (attemptedIndices.size >= maxAttempts) return null

        val excluded = attemptedIndices + failedIndex

        // 1. Prefer another viable video in the same hoster first.
        val sameHoster = hosterStates.getOrNull(failedIndex.first) as? HosterState.Ready
        if (sameHoster != null) {
            findCandidateVideoIndex(
                hoster = sameHoster,
                hosterIndex = failedIndex.first,
                excluded = excluded,
                preferredOnly = false,
            )?.let { return failedIndex.first to it }
        }

        // 2. Try preferred videos across other ready hosters.
        hosterStates.forEachIndexed { hosterIndex, state ->
            if (hosterIndex == failedIndex.first || state !is HosterState.Ready) return@forEachIndexed
            findCandidateVideoIndex(
                hoster = state,
                hosterIndex = hosterIndex,
                excluded = excluded,
                preferredOnly = true,
            )?.let { return hosterIndex to it }
        }

        // 3. Fall back to any remaining viable video across other ready hosters.
        hosterStates.forEachIndexed { hosterIndex, state ->
            if (hosterIndex == failedIndex.first || state !is HosterState.Ready) return@forEachIndexed
            findCandidateVideoIndex(
                hoster = state,
                hosterIndex = hosterIndex,
                excluded = excluded,
                preferredOnly = false,
            )?.let { return hosterIndex to it }
        }

        return null
    }

    private fun findCandidateVideoIndex(
        hoster: HosterState.Ready,
        hosterIndex: Int,
        excluded: Set<Pair<Int, Int>>,
        preferredOnly: Boolean,
    ): Int? {
        hoster.videoList.forEachIndexed { videoIndex, video ->
            if ((hosterIndex to videoIndex) in excluded) return@forEachIndexed
            val state = hoster.videoState.getOrNull(videoIndex) ?: return@forEachIndexed
            if (state != Video.State.READY && state != Video.State.QUEUE) return@forEachIndexed
            if (video.initialized && video.videoUrl.isEmpty()) return@forEachIndexed
            if (preferredOnly && !video.preferred) return@forEachIndexed
            return videoIndex
        }
        return null
    }
}
