package eu.kanade.tachiyomi.data.shikimori

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import tachiyomi.data.shikimori.ShikimoriImportEntry

class FetchShikimoriImportEntries(
    private val trackerManager: TrackerManager,
) {

    class NotLoggedInException : Exception()

    suspend fun await(): List<ShikimoriImportEntry> {
        val shikimori = trackerManager.shikimori
        if (!shikimori.isLoggedIn) throw NotLoggedInException()

        val userId = shikimori.api.getCurrentUser()
        val rates = shikimori.api.getAllUserAnimeRates(userId)
        val animeById = rates
            .map { it.targetId }
            .distinct()
            .chunked(50)
            .flatMap { chunk -> shikimori.api.getAnimesByIds(chunk) }
            .associateBy { it.id }

        return rates.mapNotNull { rate ->
            val anime = animeById[rate.targetId] ?: return@mapNotNull null
            ShikimoriImportEntry(
                rateId = rate.id,
                remoteAnimeId = rate.targetId,
                name = anime.name,
                russian = anime.russian,
                status = rate.status,
                score = rate.score,
                episodes = rate.episodes,
                totalEpisodes = anime.episodes,
                thumbnailUrl = ShikimoriApi.BASE_URL + anime.image.original,
            )
        }
    }
}
