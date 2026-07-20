package eu.kanade.tachiyomi.data.shikimori

import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.track.novel.interactor.AddNovelTracks
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus
import eu.kanade.tachiyomi.novelsource.model.SNovel
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.shikimori.ShikimoriImportEntry
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import java.time.Instant

class ImportShikimoriNovelEntries(
    private val networkToLocalNovel: NetworkToLocalNovel,
    private val getNovelByUrlAndSourceId: GetNovelByUrlAndSourceId,
    private val updateNovel: UpdateNovel,
    private val getNovelCategories: GetNovelCategories,
    private val setNovelCategories: SetNovelCategories,
    private val addNovelTracks: AddNovelTracks,
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
                val existing = getNovelByUrlAndSourceId.await(candidate.url, candidate.sourceId)
                val wasInLibrary = existing?.favorite == true

                val sNovel = SNovel.create().apply {
                    url = candidate.url
                    title = candidate.displayTitle
                    thumbnail_url = candidate.thumbnailUrl
                }
                val localNovel = networkToLocalNovel.await(
                    sNovel.toDomainNovel(candidate.sourceId),
                )

                if (!localNovel.favorite) {
                    updateNovel.await(
                        NovelUpdate(
                            id = localNovel.id,
                            favorite = true,
                            dateAdded = Instant.now().toEpochMilli(),
                        ),
                    )
                }

                if (action.categoryIds.isNotEmpty()) {
                    val current = getNovelCategories.await(localNovel.id).map { it.id }.toSet()
                    val merged = current + action.categoryIds
                    if (merged != current) {
                        setNovelCategories.await(localNovel.id, merged.toList())
                    }
                }

                if (shikimori.isLoggedIn) {
                    // Tracker binding is best-effort: a Shikimori API hiccup must
                    // not mark an already successful library import as failed.
                    try {
                        val track = MangaTrack.create(shikimori.id).apply {
                            manga_id = localNovel.id
                            remote_id = action.entry.remoteId
                            title = action.entry.name
                            total_chapters = action.entry.totalCount ?: 0L
                            score = action.entry.score.toDouble()
                            status = toTrackStatus(action.entry.status)
                            last_chapter_read = action.entry.progress.toDouble()
                        }
                        addNovelTracks.bind(shikimori, track, localNovel.id)
                        trackerBound++
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) {
                            "Shikimori tracker bind failed for '${action.entry.name}'"
                        }
                    }
                }

                if (wasInLibrary) alreadyInLibrary++ else added++
            } catch (e: Exception) {
                failed++
                logcat(LogPriority.ERROR, e) {
                    "Shikimori ranobe import failed for '${action.entry.name}'"
                }
            }
        }
        return Report(added = added, alreadyInLibrary = alreadyInLibrary, failed = failed, trackerBound = trackerBound)
    }
}
