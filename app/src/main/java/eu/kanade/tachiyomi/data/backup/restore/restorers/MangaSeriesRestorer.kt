package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupMangaSeries
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.series.manga.model.MangaSeries
import tachiyomi.domain.series.manga.model.MangaSeriesEntry
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository
import tachiyomi.domain.series.model.SeriesCoverMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.io.inputStream

class MangaSeriesRestorer(
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val mangaSeriesRepository: MangaSeriesRepository = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) {

    suspend fun restore(seriesList: List<BackupMangaSeries>) {
        if (seriesList.isEmpty()) return

        val categoryByName = getMangaCategories.await().associateBy { it.name }

        seriesList.forEach { backupSeries ->
            val resolvedEntries = backupSeries.entries
                .sortedBy { it.position }
                .mapNotNull { ref ->
                    getMangaByUrlAndSourceId.await(ref.url, ref.source)
                        ?.let { manga -> ref.position to manga.id }
                }
            if (resolvedEntries.isEmpty()) return@forEach

            val categoryId = backupSeries.categoryName
                ?.let { categoryByName[it]?.id }
                ?: 0L

            val coverEntryId = backupSeries.coverEntryUrl?.let { coverUrl ->
                val coverSource = backupSeries.coverEntrySource ?: return@let null
                getMangaByUrlAndSourceId.await(coverUrl, coverSource)?.id
            }

            val insertedSeriesId = mangaSeriesRepository.insertSeries(
                MangaSeries(
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

            resolvedEntries.forEach { (position, mangaId) ->
                mangaSeriesRepository.deleteEntry(mangaId)
                mangaSeriesRepository.insertEntry(
                    MangaSeriesEntry(
                        id = -1,
                        seriesId = insertedSeriesId,
                        mangaId = mangaId,
                        position = position,
                    ),
                )
            }

            if (backupSeries.customCover != null) {
                seriesCoverCache.setMangaSeriesCoverToCache(
                    insertedSeriesId,
                    backupSeries.customCover.inputStream(),
                )
            }

            val effectiveCoverMode = when {
                backupSeries.customCover != null -> SeriesCoverMode.CUSTOM
                coverEntryId != null && backupSeries.coverMode == SeriesCoverMode.ENTRY.value -> SeriesCoverMode.ENTRY
                else -> SeriesCoverMode.AUTO
            }

            mangaSeriesRepository.updateSeries(
                MangaSeries(
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
