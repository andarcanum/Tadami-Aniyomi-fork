package eu.kanade.tachiyomi.data.shikimori

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.shikimori.ShikimoriImportEntry
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime

class ImportShikimoriEntries(
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId,
    private val updateAnime: UpdateAnime,
    private val getAnimeCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
    private val addAnimeTracks: AddAnimeTracks,
    private val trackerManager: TrackerManager,
) {

    data class Action(
        val entry: ShikimoriImportEntry,
        val candidate: AnixartMatcher.SearchCandidate,
        val categoryIds: Set<Long>,
    )

    data class Report(
        val added: Int,
        val alreadyInLibrary: Int,
        val failed: Int,
        val trackerBound: Int,
    )

    suspend fun await(
        actions: List<Action>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Report {
        var added = 0
        var alreadyInLibrary = 0
        var failed = 0
        var trackerBound = 0
        val shikimori = trackerManager.shikimori

        actions.forEachIndexed { index, action ->
            onProgress(index + 1, actions.size)
            try {
                val candidate = action.candidate
                val existing = getAnimeByUrlAndSourceId.await(candidate.url, candidate.sourceId)
                val wasInLibrary = existing?.favorite == true

                val sAnime = SAnime.create().apply {
                    url = candidate.url
                    title = candidate.displayTitle
                    thumbnail_url = candidate.thumbnailUrl
                }
                val localAnime = networkToLocalAnime.await(
                    sAnime.toDomainAnime(candidate.sourceId),
                )

                if (!localAnime.favorite) {
                    updateAnime.awaitUpdateFavorite(localAnime.id, favorite = true)
                }

                if (action.categoryIds.isNotEmpty()) {
                    val current = getAnimeCategories.await(localAnime.id).map { it.id }.toSet()
                    val merged = current + action.categoryIds
                    if (merged != current) {
                        setAnimeCategories.await(localAnime.id, merged.toList())
                    }
                }

                if (shikimori.isLoggedIn) {
                    val track = AnimeTrack.create(shikimori.id).apply {
                        anime_id = localAnime.id
                        remote_id = action.entry.remoteAnimeId
                        title = action.entry.name
                        total_episodes = action.entry.totalEpisodes ?: 0L
                        score = action.entry.score.toDouble()
                        status = toTrackStatus(action.entry.status)
                        last_episode_seen = action.entry.episodes.toDouble()
                    }
                    addAnimeTracks.bind(shikimori, track, localAnime.id)
                    trackerBound++
                }

                if (wasInLibrary) alreadyInLibrary++ else added++
            } catch (e: Exception) {
                failed++
                logcat(LogPriority.ERROR, e) {
                    "Shikimori import failed for '${action.entry.name}'"
                }
            }
        }
        return Report(added = added, alreadyInLibrary = alreadyInLibrary, failed = failed, trackerBound = trackerBound)
    }
}
