package tachiyomi.domain.series.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.AnimeSeriesEntry
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries

interface AnimeSeriesRepository {
    fun getAllSeries(): Flow<List<AnimeSeries>>
    fun getSeriesById(id: Long): Flow<AnimeSeries?>
    fun getSeriesByCategory(categoryId: Long): Flow<List<AnimeSeries>>
    suspend fun getSeriesForAnime(animeId: Long): AnimeSeries?
    fun getEntriesForSeries(seriesId: Long): Flow<List<AnimeSeriesEntry>>
    fun getLibrarySeriesWithEntries(): Flow<List<LibraryAnimeSeries>>
    fun getAllAnimeIdsInAnySeries(): Flow<Set<Long>>
    suspend fun insertSeries(series: AnimeSeries): Long
    suspend fun updateSeries(series: AnimeSeries)
    suspend fun deleteSeries(seriesId: Long)

    /**
     * Moves all series assigned to [categoryId] back to the default category (0). Called when a
     * category is deleted: anime_series.category_id has no FK, so without this the series keep a
     * dangling id and become invisible on every library page with no way back.
     */
    suspend fun moveSeriesFromCategoryToDefault(categoryId: Long)

    suspend fun insertEntry(entry: AnimeSeriesEntry)
    suspend fun deleteEntry(animeId: Long)
    suspend fun updateEntryPositions(entries: List<AnimeSeriesEntry>)
}
