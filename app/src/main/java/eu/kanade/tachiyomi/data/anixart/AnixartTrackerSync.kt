package eu.kanade.tachiyomi.data.anixart

import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.shikimori.ShikimoriApiRateLimiter
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartRow
import tachiyomi.data.anixart.AnixartStatus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Optionally binds imported library entries to the Shikimori tracker using
 * title search, projecting Anixart status/rating onto the remote list entry.
 *
 * Search results are scored with [AnixartMatcher] against all candidate
 * titles of the row instead of blindly taking the first hit, and all remote
 * search calls go through [ShikimoriApiRateLimiter] so bulk syncs do not run
 * into Shikimori's rate limits (5 rps / 90 rpm).
 */
class AnixartTrackerSync(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val addAnimeTracks: AddAnimeTracks = Injekt.get(),
    private val rateLimiter: ShikimoriApiRateLimiter = ShikimoriApiRateLimiter(),
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
                val titles = row.candidateTitles()
                if (titles.isEmpty()) {
                    skipped++
                    continue
                }
                val best = findBestMatch(shikimori, titles)
                if (best == null) {
                    skipped++
                    continue
                }
                val completed = row.status == AnixartStatus.COMPLETED
                val track = AnimeTrack.create(shikimori.id).apply {
                    anime_id = animeId
                    remote_id = best.remote_id
                    title = best.title
                    total_episodes = best.total_episodes
                    score = row.ratingOutOfTen?.toDouble() ?: 0.0
                    status = mapStatus(row.status)
                    last_episode_seen = if (completed) best.total_episodes.toDouble() else 0.0
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

    /**
     * Searches Shikimori with up to [MAX_QUERIES] candidate titles and returns
     * the best-scoring hit, or null when nothing crosses [MIN_SCORE].
     */
    private suspend fun findBestMatch(
        shikimori: Shikimori,
        titles: List<String>,
    ) = titles.take(MAX_QUERIES).firstNotNullOfOrNull { query ->
        val results = rateLimiter.withRateLimit { shikimori.searchAnime(query) }
        results
            .map { result -> result to bestScore(titles, result.title) }
            .filter { (_, resultScore) -> resultScore >= MIN_SCORE }
            .maxByOrNull { (_, resultScore) -> resultScore }
            ?.first
    }

    private fun bestScore(titles: List<String>, candidateTitle: String): Int =
        titles.maxOf { AnixartMatcher.pairScore(it, candidateTitle) }

    private fun mapStatus(status: AnixartStatus?): Long {
        return when (status) {
            AnixartStatus.WATCHING -> Shikimori.READING
            AnixartStatus.COMPLETED -> Shikimori.COMPLETED
            AnixartStatus.PLAN_TO_WATCH -> Shikimori.PLAN_TO_READ
            AnixartStatus.DROPPED -> Shikimori.DROPPED
            null -> Shikimori.PLAN_TO_READ
        }
    }

    companion object {
        /** Same floor as [AnixartMatcher]'s review threshold. */
        private const val MIN_SCORE = 55

        /** How many candidate titles to try before giving up on a row. */
        private const val MAX_QUERIES = 3
    }
}
