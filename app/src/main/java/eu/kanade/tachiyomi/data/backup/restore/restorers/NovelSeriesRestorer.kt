package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupNovelSeries
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.series.model.SeriesCoverMode
import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.domain.series.novel.model.NovelSeriesEntry
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.io.inputStream

class NovelSeriesRestorer(
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val getNovelByUrlAndSourceId: GetNovelByUrlAndSourceId = Injekt.get(),
    private val novelSeriesRepository: NovelSeriesRepository = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) {

    suspend fun restore(seriesList: List<BackupNovelSeries>) {
        if (seriesList.isEmpty()) return

        val categoryByName = getNovelCategories.await().associateBy { it.name }

        seriesList.forEach { backupSeries ->
            val resolvedEntries = backupSeries.entries
                .sortedBy { it.position }
                .mapNotNull { ref ->
                    getNovelByUrlAndSourceId.await(ref.url, ref.source)
                        ?.let { novel -> ref.position to novel.id }
                }
            if (resolvedEntries.isEmpty()) return@forEach

            val categoryId = backupSeries.categoryName
                ?.let { categoryByName[it]?.id }
                ?: 0L

            val coverEntryId = backupSeries.coverEntryUrl?.let { coverUrl ->
                val coverSource = backupSeries.coverEntrySource ?: return@let null
                getNovelByUrlAndSourceId.await(coverUrl, coverSource)?.id
            }

            val insertedSeriesId = novelSeriesRepository.insertSeries(
                NovelSeries(
                    id = -1,
                    title = backupSeries.title,
                    description = backupSeries.description,
                    categoryId = categoryId,
                    sortOrder = backupSeries.sortOrder,
                    dateAdded = backupSeries.dateAdded,
                    coverLastModified = backupSeries.coverLastModified,
                    pinned = backupSeries.pinned,
                    coverMode = SeriesCoverMode.from(backupSeries.coverMode),
                    coverEntryId = coverEntryId,
                ),
            )

            resolvedEntries.forEach { (position, novelId) ->
                novelSeriesRepository.deleteEntry(novelId)
                novelSeriesRepository.insertEntry(
                    NovelSeriesEntry(
                        id = -1,
                        seriesId = insertedSeriesId,
                        novelId = novelId,
                        position = position,
                    ),
                )
            }

            if (backupSeries.customCover != null) {
                seriesCoverCache.setNovelSeriesCoverToCache(
                    insertedSeriesId,
                    backupSeries.customCover.inputStream(),
                )
            }

            val effectiveCoverMode = when {
                backupSeries.customCover != null -> SeriesCoverMode.CUSTOM
                coverEntryId != null && backupSeries.coverMode == SeriesCoverMode.ENTRY.value -> SeriesCoverMode.ENTRY
                else -> SeriesCoverMode.AUTO
            }

            novelSeriesRepository.updateSeries(
                NovelSeries(
                    id = insertedSeriesId,
                    title = backupSeries.title,
                    description = backupSeries.description,
                    categoryId = categoryId,
                    sortOrder = backupSeries.sortOrder,
                    dateAdded = backupSeries.dateAdded,
                    coverLastModified = backupSeries.coverLastModified,
                    pinned = backupSeries.pinned,
                    coverMode = effectiveCoverMode,
                    coverEntryId = coverEntryId?.takeIf { effectiveCoverMode == SeriesCoverMode.ENTRY },
                ),
            )
        }
    }
}
