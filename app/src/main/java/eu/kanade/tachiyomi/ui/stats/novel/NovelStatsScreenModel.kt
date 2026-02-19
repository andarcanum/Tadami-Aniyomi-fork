package eu.kanade.tachiyomi.ui.stats.novel

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastMapNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.fastCountNot
import eu.kanade.core.util.fastFilterNot
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.history.novel.interactor.GetTotalNovelReadDuration
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelStatsScreenModel(
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val getTotalNovelReadDuration: GetTotalNovelReadDuration = Injekt.get(),
    private val preferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    init {
        screenModelScope.launchIO {
            val libraryNovels = getLibraryNovel.await()
            val distinctLibraryNovels = libraryNovels.fastDistinctBy { it.id }

            val overviewStatData = StatsData.NovelOverview(
                libraryNovelCount = distinctLibraryNovels.size,
                completedNovelCount = distinctLibraryNovels.count {
                    it.novel.status.toInt() == SManga.COMPLETED && it.unreadCount == 0L
                },
                totalReadDuration = getTotalNovelReadDuration.await(),
            )

            val titlesStatData = StatsData.NovelTitles(
                globalUpdateItemCount = getGlobalUpdateItemCount(libraryNovels),
                startedNovelCount = distinctLibraryNovels.count { it.hasStarted },
                // There is no dedicated local-novel source in the current backend.
                localNovelCount = 0,
            )

            val chaptersStatData = StatsData.Chapters(
                totalChapterCount = distinctLibraryNovels.sumOf { it.totalChapters }.toInt(),
                readChapterCount = distinctLibraryNovels.sumOf { it.readCount }.toInt(),
                downloadCount = downloadManager.getDownloadCount(),
            )

            val trackersStatData = StatsData.Trackers(
                trackedTitleCount = 0,
                meanScore = Double.NaN,
                trackerCount = 0,
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
}
