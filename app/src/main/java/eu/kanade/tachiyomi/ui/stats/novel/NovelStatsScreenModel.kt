package eu.kanade.tachiyomi.ui.stats.novel

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMapNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.fastCountNot
import eu.kanade.core.util.fastFilterNot
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.stats.StatsCalculations
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.history.novel.interactor.GetTotalNovelReadDuration
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.model.NovelTrack
import tachiyomi.source.local.entries.novel.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelStatsScreenModel(
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val getTotalNovelReadDuration: GetTotalNovelReadDuration = Injekt.get(),
    private val getTracks: GetNovelTracks = Injekt.get(),
    private val preferences: LibraryPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    private val loggedInTrackers by lazy { trackerManager.loggedInNovelTrackers() }

    init {
        screenModelScope.launchIO {
            val libraryNovels = getLibraryNovel.await()
            val distinctLibraryNovels = libraryNovels.fastDistinctBy { it.id }

            val novelTrackMap = getNovelTrackMap(distinctLibraryNovels)

            val overviewStatData = StatsData.NovelOverview(
                libraryNovelCount = distinctLibraryNovels.size,
                completedNovelCount = distinctLibraryNovels.count {
                    StatsCalculations.isCompletedByUserConsumption(
                        sourceStatus = it.novel.status.toInt(),
                        customStatus = it.novel.customStatus?.toInt(),
                        completedStatus = SManga.COMPLETED,
                        terminalFallbackStatuses = setOf(
                            SManga.PUBLISHING_FINISHED,
                            SManga.CANCELLED,
                            SManga.ON_HIATUS,
                        ),
                        consumedCount = it.readCount,
                        totalCount = it.totalChapters,
                    )
                },
                totalReadDuration = getTotalNovelReadDuration.await(),
            )

            val titlesStatData = StatsData.NovelTitles(
                globalUpdateItemCount = getGlobalUpdateItemCount(libraryNovels),
                startedNovelCount = distinctLibraryNovels.count { it.hasStarted },
                localNovelCount = distinctLibraryNovels.count { it.novel.isLocal() },
            )

            val chaptersStatData = StatsData.Chapters(
                totalChapterCount = distinctLibraryNovels.sumOf { it.totalChapters }.toInt(),
                readChapterCount = distinctLibraryNovels.sumOf { it.readCount }.toInt(),
                downloadCount = downloadManager.getDownloadCount(),
            )

            // Was hardcoded to 0/NaN/0: the trackers card stayed empty even with logged-in
            // trackers and tracked titles in the library.
            val trackersStatData = computeNovelTrackersStat(
                trackMap = novelTrackMap,
                tenPointScoreOf = ::get10PointScore,
                loggedInTrackerCount = loggedInTrackers.size,
            )

            mutableState.update {
                StatsScreenState.SuccessNovel(
                    overview = overviewStatData,
                    titles = titlesStatData,
                    chapters = chaptersStatData,
                    trackers = trackersStatData,
                )
            }
        }
    }

    private fun getGlobalUpdateItemCount(libraryNovel: List<LibraryNovel>): Int {
        val includedCategories = preferences.novelUpdateCategories().get().map { it.toLong() }
        val includedNovel = if (includedCategories.isNotEmpty()) {
            libraryNovel.filter { it.category in includedCategories }
        } else {
            libraryNovel
        }

        val excludedCategories = preferences.novelUpdateCategoriesExclude().get().map { it.toLong() }
        val excludedNovelIds = if (excludedCategories.isNotEmpty()) {
            libraryNovel.fastMapNotNull { novel ->
                novel.id.takeIf { novel.category in excludedCategories }
            }
        } else {
            emptyList()
        }

        val updateRestrictions = preferences.autoUpdateItemRestrictions().get()
        return includedNovel
            .fastFilterNot { it.novel.id in excludedNovelIds }
            .fastDistinctBy { it.novel.id }
            .fastCountNot {
                (ENTRY_NON_COMPLETED in updateRestrictions && it.novel.status.toInt() == SManga.COMPLETED) ||
                    (ENTRY_HAS_UNVIEWED in updateRestrictions && it.unreadCount != 0L) ||
                    (ENTRY_NON_VIEWED in updateRestrictions && it.totalChapters > 0 && !it.hasStarted)
            }
    }

    private suspend fun getNovelTrackMap(libraryNovel: List<LibraryNovel>): Map<Long, List<NovelTrack>> {
        val loggedInTrackerIds = loggedInTrackers.map { it.id }.toHashSet()
        return libraryNovel.associate { novel ->
            val tracks = getTracks.await(novel.id)
                .fastFilter { it.trackerId in loggedInTrackerIds }

            novel.id to tracks
        }
    }

    private fun get10PointScore(track: NovelTrack): Double {
        // Novel trackers implement the manga-side service interface; the conversion mirrors the
        // one the shared track dialog uses.
        val service = trackerManager.get(track.trackerId) ?: return track.score
        return service.mangaService.get10PointScore(track.toMangaTrack())
    }
}

/**
 * Trackers card of the novel stats screen, extracted so the calculation is testable without the
 * screen model's coroutine/dispatcher setup. [tenPointScoreOf] normalizes a raw track score to the
 * 10-point scale of its service.
 */
internal fun computeNovelTrackersStat(
    trackMap: Map<Long, List<NovelTrack>>,
    tenPointScoreOf: (NovelTrack) -> Double,
    loggedInTrackerCount: Int,
): StatsData.Trackers {
    val scoredTitles = trackMap.values
        .map { tracks -> tracks.mapNotNull { track -> track.takeIf { it.score > 0.0 } } }
        .filter { it.isNotEmpty() }
    return StatsData.Trackers(
        trackedTitleCount = trackMap.count { it.value.isNotEmpty() },
        meanScore = StatsCalculations.meanTitleScore(
            scoredTitles.map { tracks -> tracks.map(tenPointScoreOf) },
        ),
        trackerCount = loggedInTrackerCount,
    )
}

private fun NovelTrack.toMangaTrack(): MangaTrack {
    return MangaTrack(
        id = id,
        mangaId = novelId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate,
        finishDate = finishDate,
        private = private,
    )
}
