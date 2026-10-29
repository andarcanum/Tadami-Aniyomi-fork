package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSeries
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import kotlinx.coroutines.flow.first
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.AnimeSeriesEntry
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository
import tachiyomi.domain.series.model.SeriesCoverMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.io.inputStream

class AnimeSeriesRestorer(
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId = Injekt.get(),
    private val animeSeriesRepository: AnimeSeriesRepository = Injekt.get(),
    private val seriesCoverCache: SeriesCoverCache = Injekt.get(),
) {

    suspend fun restore(seriesList: List<BackupAnimeSeries>) {
        if (seriesList.isEmpty()) return

        val categoryByName = getAnimeCategories.await().associateBy { it.name }
        // Restores run repeatedly (cloud sync merges every cycle). Inserting unconditionally
        // created a brand new row per run and left the previous one as an empty zombie, so the
        // series list grew by one duplicate per sync. Reuse the row with the same title instead.
        val existingByTitle = animeSeriesRepository.getAllSeries().first().associateBy { it.title }

        seriesList.forEach { backupSeries ->
            val resolvedEntries = backupSeries.entries
                .sortedBy { it.position }
                .mapNotNull { ref ->
                    getAnimeByUrlAndSourceId.await(ref.url, ref.source)
                        ?.let { anime -> ref.position to anime.id }
                }
            if (resolvedEntries.isEmpty()) return@forEach

            val categoryId = backupSeries.categoryName
                ?.let { categoryByName[it]?.id }
                ?: 0L

            val coverEntryId = backupSeries.coverEntryUrl?.let { coverUrl ->
                val coverSource = backupSeries.coverEntrySource ?: return@let null
                getAnimeByUrlAndSourceId.await(coverUrl, coverSource)?.id
            }

            val insertedSeriesId = existingByTitle[backupSeries.title]?.id
                ?: animeSeriesRepository.insertSeries(
                    AnimeSeries(
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

            resolvedEntries.forEach { (position, animeId) ->
                animeSeriesRepository.deleteEntry(animeId)
                animeSeriesRepository.insertEntry(
                    AnimeSeriesEntry(
                        id = -1,
                        seriesId = insertedSeriesId,
                        animeId = animeId,
                        position = position,
                    ),
                )
            }

            if (backupSeries.customCover != null) {
                seriesCoverCache.setAnimeSeriesCoverToCache(
                    insertedSeriesId,
                    backupSeries.customCover.inputStream(),
                )
            }

            val effectiveCoverMode = when {
                backupSeries.customCover != null -> SeriesCoverMode.CUSTOM
                coverEntryId != null && backupSeries.coverMode == SeriesCoverMode.ENTRY.value -> SeriesCoverMode.ENTRY
                else -> SeriesCoverMode.AUTO
            }

            animeSeriesRepository.updateSeries(
                AnimeSeries(
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
