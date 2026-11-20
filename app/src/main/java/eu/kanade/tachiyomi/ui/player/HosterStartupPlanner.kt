package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState

/**
 * Ranks hosters for fast startup using metadata already available on [Hoster] (before any
 * per-hoster network/JS extraction), so playback can start as soon as the highest-priority
 * viable hoster resolves instead of blocking on all hosters via awaitAll().
 */
internal object HosterStartupPlanner {

    /**
     * Episodes with more than this many hosters load only the top-ranked candidate(s) needed
     * to start playback and keep the remaining hosters [HosterState.Idle] until tapped or
     * needed for runtime fallback.
     */
    const val EAGER_HOSTER_LOAD_THRESHOLD = 2

    private val qualityOrder = listOf("1080p", "720p", "480p", "360p")

    fun orderIndicesForStartup(
        hosters: List<Hoster>,
        preferences: PlaybackSelectionPreferences,
    ): List<Int> {
        val preferredPlayerId = preferences.preferredPlayer.playerId
        val legacyDubbing = preferences.preferredDubbingCdn.ifBlank {
            preferences.preferredDubbingKodik
        }

        return hosters.withIndex()
            .filterNot { (_, hoster) -> hoster.lazy }
            .sortedWith(
                compareBy<IndexedValue<Hoster>>(
                    { (_, hoster) ->
                        startupTier(
                            hoster = hoster,
                            preferredPlayerId = preferredPlayerId,
                            preferences = preferences,
                            legacyDubbing = legacyDubbing,
                        )
                    },
                    { it.index },
                ),
            )
            .map { it.index }
    }

    fun selectVideoIndexForHoster(
        hoster: HosterState.Ready,
        preferences: PlaybackSelectionPreferences,
    ): Int? {
        val viableIndices = hoster.videoList.indices.filter { idx ->
            val video = hoster.videoList[idx]
            val state = hoster.videoState.getOrNull(idx)
            val stateOk = state == Video.State.READY || state == Video.State.QUEUE
            val urlOk = !video.initialized || video.videoUrl.isNotEmpty()
            stateOk && urlOk
        }
        if (viableIndices.isEmpty()) return null

        val preferredQuality = preferredQualityForPlayer(hoster.playerId, preferences)
        if (!preferredQuality.equals("best", ignoreCase = true)) {
            viableIndices.firstOrNull { idx ->
                hoster.videoList[idx].videoTitle.contains(preferredQuality, ignoreCase = true)
            }?.let { return it }
        }

        // Respect source-marked preferred video when no stricter quality match forced another choice.
        viableIndices.firstOrNull { idx -> hoster.videoList[idx].preferred }?.let { return it }

        val rankedByQuality = viableIndices.minByOrNull { idx ->
            qualityRank(hoster.videoList[idx].videoTitle)
        }
        if (rankedByQuality != null && qualityRank(hoster.videoList[rankedByQuality].videoTitle) != Int.MAX_VALUE) {
            return rankedByQuality
        }

        return viableIndices.first()
    }

    fun selectNextIdleHosterIndex(
        hosterStates: List<HosterState>,
        excludedHosterIndices: Set<Int>,
    ): Int? {
        return hosterStates.indices.firstOrNull { idx ->
            idx !in excludedHosterIndices && hosterStates[idx] is HosterState.Idle
        }
    }

    private fun startupTier(
        hoster: Hoster,
        preferredPlayerId: String?,
        preferences: PlaybackSelectionPreferences,
        legacyDubbing: String,
    ): Int {
        val playerId = hoster.playerId
        val matchesPreferredPlayer = preferredPlayerId != null &&
            playerId.equals(preferredPlayerId, ignoreCase = true)
        val playerDubbing = preferredDubbingForPlayer(playerId, preferences)
        val matchesPlayerDubbing = playerDubbing.isNotBlank() && matchesDubbing(hoster, playerDubbing)

        return when {
            matchesPreferredPlayer && matchesPlayerDubbing -> 0
            preferredPlayerId == null && matchesPlayerDubbing -> 0
            playerId.isNullOrBlank() && legacyDubbing.isNotBlank() && matchesDubbing(hoster, legacyDubbing) -> 1
            matchesPreferredPlayer -> 2
            hoster.videoList != null -> 3
            else -> 4
        }
    }

    private fun matchesDubbing(hoster: Hoster, preferredDubbing: String): Boolean {
        return hoster.hosterName.equals(preferredDubbing, ignoreCase = true) ||
            hoster.dubbingLabel.equals(preferredDubbing, ignoreCase = true) ||
            hoster.dubbingId.equals(preferredDubbing, ignoreCase = true)
    }

    private fun preferredDubbingForPlayer(
        playerId: String?,
        preferences: PlaybackSelectionPreferences,
    ): String {
        return when (playerId?.lowercase()) {
            "cdn" -> preferences.preferredDubbingCdn
            "kodik" -> preferences.preferredDubbingKodik
            "parlorate" -> preferences.preferredDubbingParlorate
            else -> ""
        }
    }

    private fun preferredQualityForPlayer(
        playerId: String?,
        preferences: PlaybackSelectionPreferences,
    ): String {
        return when (playerId?.lowercase()) {
            "cdn" -> preferences.preferredQualityCdn
            "kodik" -> preferences.preferredQualityKodik
            "parlorate" -> preferences.preferredQualityParlorate
            else -> preferences.preferredQualityCdn.ifBlank {
                preferences.preferredQualityKodik.ifBlank { "best" }
            }
        }
    }

    private fun qualityRank(title: String): Int {
        val normalized = title.lowercase()
        val idx = qualityOrder.indexOfFirst { normalized.contains(it) }
        return if (idx == -1) Int.MAX_VALUE else idx
    }
}
