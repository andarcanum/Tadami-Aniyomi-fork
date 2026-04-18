package tachiyomi.data.series.novel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.domain.series.novel.model.NovelSeriesEntry
import tachiyomi.domain.series.novel.repository.NovelSeriesRepository

class NovelSeriesRepositoryImpl(
    private val handler: NovelDatabaseHandler,
    private val novelRepository: NovelRepository,
) : NovelSeriesRepository {

    override fun getAllSeries(): Flow<List<NovelSeries>> {
        return handler.subscribeToList { db -> db.novel_seriesQueries.getAllSeries(novelSeriesMapper) }
    }

    override fun getSeriesById(id: Long): Flow<NovelSeries?> {
        return handler.subscribeToOneOrNull { db -> db.novel_seriesQueries.getSeriesById(id, novelSeriesMapper) }
    }

    override fun getSeriesByCategory(categoryId: Long): Flow<List<NovelSeries>> {
        return handler.subscribeToList { db ->
            db.novel_seriesQueries.getSeriesByCategory(categoryId, novelSeriesMapper)
        }
    }

    override suspend fun getSeriesForNovel(novelId: Long): NovelSeries? {
        val entry =
            handler.awaitOneOrNull { db ->
                db.novel_series_entriesQueries.getEntryByNovelId(novelId, novelSeriesEntryMapper)
            }
                ?: return null
        return handler.awaitOneOrNull { db -> db.novel_seriesQueries.getSeriesById(entry.seriesId, novelSeriesMapper) }
    }

    override fun getEntriesForSeries(seriesId: Long): Flow<List<NovelSeriesEntry>> {
        return handler.subscribeToList { db ->
            db.novel_series_entriesQueries.getEntriesBySeriesId(seriesId, novelSeriesEntryMapper)
        }
    }

    override fun getLibrarySeriesWithEntries(): Flow<List<LibraryNovelSeries>> {
        return combine(
            getAllSeries(),
            handler.subscribeToList { db -> db.novel_series_entriesQueries.getAllEntries(novelSeriesEntryMapper) },
            novelRepository.getLibraryNovelAsFlow(),
        ) { seriesList, allEntries, allLibraryNovels ->
            val libraryNovelsById = allLibraryNovels.associateBy { it.novel.id }
            val entriesBySeriesId = allEntries.groupBy { it.seriesId }

            seriesList.map { series ->
                val entriesForSeries = entriesBySeriesId[series.id]?.sortedBy { it.position } ?: emptyList()
                val libraryNovelsForSeries = entriesForSeries.mapNotNull { libraryNovelsById[it.novelId] }
                LibraryNovelSeries(series, libraryNovelsForSeries)
            }
        }
    }

    override fun getAllNovelIdsInAnySeries(): Flow<Set<Long>> {
        return handler.subscribeToList { db -> db.novel_series_entriesQueries.getAllNovelIdsInAnySeries() }
            .map { it.toSet() }
    }

    override suspend fun insertSeries(series: NovelSeries): Long {
        return handler.awaitOneExecutable(inTransaction = true) { db ->
            db.novel_seriesQueries.insert(
                title = series.title,
                description = series.description,
                categoryId = series.categoryId,
                sortOrder = series.sortOrder,
                dateAdded = series.dateAdded,
                coverLastModified = series.coverLastModified,
            )
            db.novel_seriesQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updateSeries(series: NovelSeries) {
        handler.await { db ->
            db.novel_seriesQueries.update(
                id = series.id,
                title = series.title,
                description = series.description,
                categoryId = series.categoryId,
                sortOrder = series.sortOrder,
                dateAdded = series.dateAdded,
                coverLastModified = series.coverLastModified,
            )
        }
    }

    override suspend fun deleteSeries(seriesId: Long) {
        handler.await { db ->
            db.novel_seriesQueries.delete(seriesId)
        }
    }

    override suspend fun insertEntry(entry: NovelSeriesEntry) {
        handler.await { db ->
            db.novel_series_entriesQueries.insert(
                seriesId = entry.seriesId,
                novelId = entry.novelId,
                position = entry.position.toLong(),
            )
        }
    }

    override suspend fun deleteEntry(novelId: Long) {
        handler.await { db ->
            db.novel_series_entriesQueries.deleteByNovelId(novelId)
        }
    }

    override suspend fun updateEntryPositions(entries: List<NovelSeriesEntry>) {
        handler.await(inTransaction = true) { db ->
            entries.forEach { entry ->
                db.novel_series_entriesQueries.updatePosition(
                    position = entry.position.toLong(),
                    id = entry.id,
                )
            }
        }
    }
}
