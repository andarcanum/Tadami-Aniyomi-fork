package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Hoster

enum class PlaybackPlayerPreference(val playerId: String?) {
    AUTO(null),
    CDN("cdn"),
    KODIK("kodik"),
    PARLORATE("parlorate"),
    ;

    companion object {
        fun fromPreference(value: String?): PlaybackPlayerPreference {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
        }
    }
}

data class PlaybackSelectionPreferences(
    val preferredPlayer: PlaybackPlayerPreference = PlaybackPlayerPreference.AUTO,
    val preferredDubbingCdn: String = "",
    val preferredDubbingKodik: String = "",
    val preferredDubbingParlorate: String = "",
    val preferredQualityCdn: String = "best",
    val preferredQualityKodik: String = "best",
    val preferredQualityParlorate: String = "best",
)

sealed interface PlaybackSelection {
    data class Selected(val hosterIndex: Int, val videoIndex: Int) : PlaybackSelection

    data object None : PlaybackSelection
}

object PlaybackSelectionResolver {
    private val qualityOrder = listOf("1080p", "720p", "480p", "360p")

    fun resolve(
        hosters: List<Hoster>,
        preferences: PlaybackSelectionPreferences,
    ): PlaybackSelection {
        val metadataHosters = hosters.withIndex().filter { (_, hoster) ->
            !hoster.playerId.isNullOrBlank()
        }
        if (metadataHosters.isEmpty()) return PlaybackSelection.None

        val playerOrder = buildList {
            preferences.preferredPlayer.playerId?.let(::add)
            metadataHosters.mapNotNull { it.value.playerId }
                .distinct()
                .filterNot { it in this }
                .forEach(::add)
        }

        playerOrder.forEach { playerId ->
            val selected = resolveInPlayer(metadataHosters, playerId, preferences)
            if (selected != null) {
                return PlaybackSelection.Selected(selected.first, selected.second)
            }
        }

        return PlaybackSelection.None
    }

    private fun resolveInPlayer(
        hosters: List<IndexedValue<Hoster>>,
        playerId: String,
        preferences: PlaybackSelectionPreferences,
    ): Pair<Int, Int>? {
        val playerHosters = hosters.filter { it.value.playerId.equals(playerId, ignoreCase = true) }
        if (playerHosters.isEmpty()) return null

        val preferredDubbing = when (playerId.lowercase()) {
            "cdn" -> preferences.preferredDubbingCdn
            "kodik" -> preferences.preferredDubbingKodik
            "parlorate" -> preferences.preferredDubbingParlorate
            else -> ""
        }
        val preferredQuality = when (playerId.lowercase()) {
            "cdn" -> preferences.preferredQualityCdn
            "kodik" -> preferences.preferredQualityKodik
            "parlorate" -> preferences.preferredQualityParlorate
            else -> "best"
        }

        val preferredHoster = playerHosters.firstOrNull { indexed ->
            indexed.value.hosterName.equals(preferredDubbing, ignoreCase = true) ||
                indexed.value.dubbingLabel.equals(preferredDubbing, ignoreCase = true) ||
                indexed.value.dubbingId.equals(preferredDubbing, ignoreCase = true)
        }

        if (preferredHoster != null) {
            resolveVideoIndex(preferredHoster.value, preferredQuality)?.let { videoIndex ->
                return preferredHoster.index to videoIndex
            }
        }

        playerHosters.forEach { indexed ->
            resolveVideoIndex(indexed.value, preferredQuality)?.let { videoIndex ->
                return indexed.index to videoIndex
            }
        }

        return null
    }

    private fun resolveVideoIndex(hoster: Hoster, preferredQuality: String): Int? {
        val videos = hoster.videoList.orEmpty()
        if (videos.isEmpty()) return null

        if (preferredQuality.equals("best", ignoreCase = true)) {
            return videos.indices.minByOrNull { index ->
                qualityRank(videos[index].videoTitle)
            }
        }

        val exact = videos.indexOfFirst { it.videoTitle.contains(preferredQuality, ignoreCase = true) }
        if (exact != -1) return exact

        return videos.indices.minByOrNull { index ->
            qualityRank(videos[index].videoTitle)
        }
    }

    private fun qualityRank(title: String): Int {
        val normalized = title.lowercase()
        val index = qualityOrder.indexOfFirst { normalized.contains(it.lowercase()) }
        return if (index == -1) Int.MAX_VALUE else index
    }
}
