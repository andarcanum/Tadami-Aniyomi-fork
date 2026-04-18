package tachiyomi.data.series.manga

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.MangaSeries
import tachiyomi.domain.series.manga.model.MangaSeriesEntry
import tachiyomi.domain.series.manga.repository.MangaSeriesRepository

class MangaSeriesRepositoryImpl(
    private val handler: MangaDatabaseHandler,
    private val mangaRepository: MangaRepository,
) : MangaSeriesRepository {

    override fun getAllSeries(): Flow<List<MangaSeries>> {
        return handler.subscribeToList { db -> db.manga_seriesQueries.getAllSeries(mangaSeriesMapper) }
    }

    override fun getSeriesById(id: Long): Flow<MangaSeries?> {
        return handler.subscribeToOneOrNull { db -> db.manga_seriesQueries.getSeriesById(id, mangaSeriesMapper) }
    }

    override fun getSeriesByCategory(categoryId: Long): Flow<List<MangaSeries>> {
        return handler.subscribeToList { db ->
            db.manga_seriesQueries.getSeriesByCategory(categoryId, mangaSeriesMapper)
        }
    }

    override suspend fun getSeriesForManga(mangaId: Long): MangaSeries? {
        val entry = handler.awaitOneOrNull { db ->
            db.manga_series_entriesQueries.getEntryByMangaId(mangaId, mangaSeriesEntryMapper)
        } ?: return null
        return handler.awaitOneOrNull { db -> db.manga_seriesQueries.getSeriesById(entry.seriesId, mangaSeriesMapper) }
    }

    override fun getEntriesForSeries(seriesId: Long): Flow<List<MangaSeriesEntry>> {
        return handler.subscribeToList { db ->
            db.manga_series_entriesQueries.getEntriesBySeriesId(seriesId, mangaSeriesEntryMapper)
        }
    }

    override fun getLibrarySeriesWithEntries(): Flow<List<LibraryMangaSeries>> {
        return combine(
            getAllSeries(),
            handler.subscribeToList { db -> db.manga_series_entriesQueries.getAllEntries(mangaSeriesEntryMapper) },
            mangaRepository.getLibraryMangaAsFlow(),
        ) { seriesList, allEntries, allLibraryMangas ->
            val libraryMangasById = allLibraryMangas.associateBy { it.manga.id }
            val entriesBySeriesId = allEntries.groupBy { it.seriesId }

            seriesList.map { series ->
                val entriesForSeries = entriesBySeriesId[series.id]?.sortedBy { it.position } ?: emptyList()
                val libraryMangasForSeries = entriesForSeries.mapNotNull { libraryMangasById[it.mangaId] }
                LibraryMangaSeries(series, libraryMangasForSeries)
            }
        }
    }

    override fun getAllMangaIdsInAnySeries(): Flow<Set<Long>> {
        return handler.subscribeToList { db -> db.manga_series_entriesQueries.getAllMangaIdsInAnySeries() }
            .map { it.toSet() }
    }

    override suspend fun insertSeries(series: MangaSeries): Long {
        return handler.awaitOneExecutable(inTransaction = true) { db ->
            db.manga_seriesQueries.insert(
                title = series.title,
                description = series.description,
                categoryId = series.categoryId,
                sortOrder = series.sortOrder,
                dateAdded = series.dateAdded,
                coverLastModified = series.coverLastModified,
            )
            db.manga_seriesQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updateSeries(series: MangaSeries) {
        handler.await { db ->
            db.manga_seriesQueries.update(
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
            db.manga_seriesQueries.delete(seriesId)
        }
    }

    override suspend fun insertEntry(entry: MangaSeriesEntry) {
        handler.await { db ->
            db.manga_series_entriesQueries.insert(
                seriesId = entry.seriesId,
                mangaId = entry.mangaId,
                position = entry.position.toLong(),
            )
        }
    }

    override suspend fun deleteEntry(mangaId: Long) {
        handler.await { db ->
            db.manga_series_entriesQueries.deleteByMangaId(mangaId)
        }
    }

    override suspend fun updateEntryPositions(entries: List<MangaSeriesEntry>) {
        handler.await(inTransaction = true) { db ->
            entries.forEach { entry ->
                db.manga_series_entriesQueries.updatePosition(
                    position = entry.position.toLong(),
                    id = entry.id,
                )
            }
        }
    }
}

private val mangaSeriesMapper: (Long, String, String?, Long, Long, Long, Long) -> MangaSeries =
    { id, title, description, categoryId, sortOrder, dateAdded, coverLastModified ->
        MangaSeries(
            id = id,
            title = title,
            description = description,
            categoryId = categoryId,
            sortOrder = sortOrder,
            dateAdded = dateAdded,
            coverLastModified = coverLastModified,
        )
    }

private val mangaSeriesEntryMapper: (Long, Long, Long, Long) -> MangaSeriesEntry =
    { id, seriesId, mangaId, position ->
        MangaSeriesEntry(
            id = id,
            seriesId = seriesId,
            mangaId = mangaId,
            position = position.toInt(),
        )
    }
