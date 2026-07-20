package eu.kanade.tachiyomi.data.anilist

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anilist.AnilistImportEntry
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime

class ImportAnilistEntries(
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId,
    private val updateAnime: UpdateAnime,
    private val getAnimeCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
    private val addAnimeTracks: AddAnimeTracks,
    private val trackerManager: TrackerManager,
) {

    data class Action(
        val entry: AnilistImportEntry,
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
        val aniList = trackerManager.aniList

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

                if (aniList.isLoggedIn) {
                    // Tracker binding is best-effort: an AniList API hiccup must
                    // not mark an already successful library import as failed.
                    try {
                        val track = AnimeTrack.create(aniList.id).apply {
                            anime_id = localAnime.id
                            remote_id = action.entry.remoteId
                            title = action.entry.name
                            total_episodes = action.entry.totalCount ?: 0L
                            score = action.entry.score.toDouble()
                            status = toAnilistAnimeTrackStatus(action.entry.status)
                            last_episode_seen = action.entry.progress.toDouble()
                        }
                        addAnimeTracks.bind(aniList, track, localAnime.id)
                        trackerBound++
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) {
                            "AniList tracker bind failed for '${action.entry.name}'"
                        }
                    }
                }

                if (wasInLibrary) alreadyInLibrary++ else added++
            } catch (e: Exception) {
                failed++
                logcat(LogPriority.ERROR, e) {
                    "AniList anime import failed for '${action.entry.name}'"
                }
            }
        }
        return Report(added = added, alreadyInLibrary = alreadyInLibrary, failed = failed, trackerBound = trackerBound)
    }
}

internal fun toAnilistAnimeTrackStatus(status: String): Long = when (status.trim().uppercase()) {
    "CURRENT" -> Anilist.WATCHING
    "COMPLETED" -> Anilist.COMPLETED
    "PAUSED" -> Anilist.ON_HOLD
    "DROPPED" -> Anilist.DROPPED
    "PLANNING" -> Anilist.PLAN_TO_WATCH
    "REPEATING" -> Anilist.REWATCHING
    else -> Anilist.PLAN_TO_WATCH
}
