package eu.kanade.tachiyomi.data.anilist

import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.source.model.SManga
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anilist.AnilistImportEntry
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga

class ImportAnilistMangaEntries(
    private val networkToLocalManga: NetworkToLocalManga,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val updateManga: UpdateManga,
    private val getMangaCategories: GetMangaCategories,
    private val setMangaCategories: SetMangaCategories,
    private val addMangaTracks: AddMangaTracks,
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
                val existing = getMangaByUrlAndSourceId.await(candidate.url, candidate.sourceId)
                val wasInLibrary = existing?.favorite == true

                val sManga = SManga.create().apply {
                    url = candidate.url
                    title = candidate.displayTitle
                    thumbnail_url = candidate.thumbnailUrl
                }
                val localManga = networkToLocalManga.await(
                    sManga.toDomainManga(candidate.sourceId),
                )

                if (!localManga.favorite) {
                    updateManga.awaitUpdateFavorite(localManga.id, favorite = true)
                }

                if (action.categoryIds.isNotEmpty()) {
                    val current = getMangaCategories.await(localManga.id).map { it.id }.toSet()
                    val merged = current + action.categoryIds
                    if (merged != current) {
                        setMangaCategories.await(localManga.id, merged.toList())
                    }
                }

                if (aniList.isLoggedIn) {
                    // Tracker binding is best-effort: an AniList API hiccup must
                    // not mark an already successful library import as failed.
                    try {
                        val track = MangaTrack.create(aniList.id).apply {
                            manga_id = localManga.id
                            remote_id = action.entry.remoteId
                            title = action.entry.name
                            total_chapters = action.entry.totalCount ?: 0L
                            score = action.entry.score.toDouble()
                            status = toAnilistMangaTrackStatus(action.entry.status)
                            last_chapter_read = action.entry.progress.toDouble()
                        }
                        addMangaTracks.bind(aniList, track, localManga.id)
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
                    "AniList manga import failed for '${action.entry.name}'"
                }
            }
        }
        return Report(added = added, alreadyInLibrary = alreadyInLibrary, failed = failed, trackerBound = trackerBound)
    }
}

internal fun toAnilistMangaTrackStatus(status: String): Long = when (status.trim().uppercase()) {
    "CURRENT" -> Anilist.READING
    "COMPLETED" -> Anilist.COMPLETED
    "PAUSED" -> Anilist.ON_HOLD
    "DROPPED" -> Anilist.DROPPED
    "PLANNING" -> Anilist.PLAN_TO_READ
    "REPEATING" -> Anilist.REREADING
    else -> Anilist.PLAN_TO_READ
}
