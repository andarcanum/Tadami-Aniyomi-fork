package tachiyomi.domain.series.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.MangaSeries
import tachiyomi.domain.series.manga.model.MangaSeriesEntry

interface MangaSeriesRepository {
    fun getAllSeries(): Flow<List<MangaSeries>>
    fun getSeriesById(id: Long): Flow<MangaSeries?>
    fun getSeriesByCategory(categoryId: Long): Flow<List<MangaSeries>>
    suspend fun getSeriesForManga(mangaId: Long): MangaSeries?
    fun getEntriesForSeries(seriesId: Long): Flow<List<MangaSeriesEntry>>
    fun getLibrarySeriesWithEntries(): Flow<List<LibraryMangaSeries>>
    fun getAllMangaIdsInAnySeries(): Flow<Set<Long>>
    suspend fun insertSeries(series: MangaSeries): Long
    suspend fun updateSeries(series: MangaSeries)
    suspend fun deleteSeries(seriesId: Long)

    /**
     * Moves all series assigned to [categoryId] back to the default category (0). Called when a
     * category is deleted: manga_series.category_id has no FK, so without this the series keep a
     * dangling id and become invisible on every library page with no way back.
     */
    suspend fun moveSeriesFromCategoryToDefault(categoryId: Long)

    suspend fun insertEntry(entry: MangaSeriesEntry)
    suspend fun deleteEntry(mangaId: Long)
    suspend fun updateEntryPositions(entries: List<MangaSeriesEntry>)
}
