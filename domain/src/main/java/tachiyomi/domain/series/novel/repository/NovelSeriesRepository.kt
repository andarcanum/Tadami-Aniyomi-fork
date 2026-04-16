package tachiyomi.domain.series.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.domain.series.novel.model.NovelSeriesEntry

interface NovelSeriesRepository {
    fun getAllSeries(): Flow<List<NovelSeries>>
    fun getSeriesById(id: Long): Flow<NovelSeries?>
    fun getSeriesByCategory(categoryId: Long): Flow<List<NovelSeries>>
    suspend fun getSeriesForNovel(novelId: Long): NovelSeries?
    fun getEntriesForSeries(seriesId: Long): Flow<List<NovelSeriesEntry>>
    fun getLibrarySeriesWithEntries(): Flow<List<LibraryNovelSeries>>
    fun getAllNovelIdsInAnySeries(): Flow<Set<Long>>
    suspend fun insertSeries(series: NovelSeries): Long
    suspend fun updateSeries(series: NovelSeries)
    suspend fun deleteSeries(seriesId: Long)
    suspend fun insertEntry(entry: NovelSeriesEntry)
    suspend fun deleteEntry(novelId: Long)
    suspend fun updateEntryPositions(entries: List<NovelSeriesEntry>)
}
