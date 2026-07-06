package eu.kanade.tachiyomi.data.anixart

import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anixart.AnixartRow
import tachiyomi.data.anixart.AnixartStatus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Optionally binds imported library entries to the Shikimori tracker using
 * title search, projecting Anixart status/rating onto the remote list entry.
 */
class AnixartTrackerSync(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val addAnimeTracks: AddAnimeTracks = Injekt.get(),
) {

    data class Report(
        val synced: Int,
        val skipped: Int,
        val failed: Int,
    )

    suspend fun syncToShikimori(
        rows: List<Pair<AnixartRow, Long>>,
    ): Report {
        val shikimori = trackerManager.shikimori
        if (!shikimori.isLoggedIn) {
            return Report(synced = 0, skipped = rows.size, failed = 0)
        }

        var synced = 0
        var skipped = 0
        var failed = 0

        for ((row, animeId) in rows) {
            try {
                val query = row.candidateTitles().firstOrNull()
                if (query.isNullOrBlank()) {
                    skipped++
                    continue
                }
                val results = shikimori.searchAnime(query)
                val best = results.firstOrNull() ?: run {
                    skipped++
                    continue
                }
                val track = AnimeTrack.create(shikimori.id).apply {
                    anime_id = animeId
                    remote_id = best.remote_id
                    title = best.title
                    total_episodes = best.total_episodes
                    score = row.ratingOutOfTen?.toDouble() ?: 0.0
                    status = mapStatus(row.status)
                    last_episode_seen = 0.0
                }
                addAnimeTracks.bind(shikimori, track, animeId)
                synced++
            } catch (e: Exception) {
                failed++
                logcat(LogPriority.WARN, e) { "Anixart Shikimori sync failed for animeId=$animeId" }
            }
        }
        return Report(synced = synced, skipped = skipped, failed = failed)
    }

    private fun mapStatus(status: AnixartStatus?): Long {
        return when (status) {
            AnixartStatus.WATCHING -> Shikimori.READING
            AnixartStatus.COMPLETED -> Shikimori.COMPLETED
            AnixartStatus.PLAN_TO_WATCH -> Shikimori.PLAN_TO_READ
            AnixartStatus.DROPPED -> Shikimori.DROPPED
            null -> Shikimori.PLAN_TO_READ
        }
    }
}
