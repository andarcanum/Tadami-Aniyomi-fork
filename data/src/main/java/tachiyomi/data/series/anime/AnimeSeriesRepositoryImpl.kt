package tachiyomi.data.series.anime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.AnimeSeriesEntry
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import tachiyomi.domain.series.anime.repository.AnimeSeriesRepository

class AnimeSeriesRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
    private val animeRepository: AnimeRepository,
) : AnimeSeriesRepository {

    override fun getAllSeries(): Flow<List<AnimeSeries>> {
        return handler.subscribeToList { db -> db.anime_seriesQueries.getAllSeries(animeSeriesMapper) }
    }

    override fun getSeriesById(id: Long): Flow<AnimeSeries?> {
        return handler.subscribeToOneOrNull { db -> db.anime_seriesQueries.getSeriesById(id, animeSeriesMapper) }
    }

    override fun getSeriesByCategory(categoryId: Long): Flow<List<AnimeSeries>> {
        return handler.subscribeToList { db ->
            db.anime_seriesQueries.getSeriesByCategory(categoryId, animeSeriesMapper)
        }
    }

    override suspend fun getSeriesForAnime(animeId: Long): AnimeSeries? {
        val entry =
            handler.awaitOneOrNull { db ->
                db.anime_series_entriesQueries.getEntryByAnimeId(animeId, animeSeriesEntryMapper)
            }
                ?: return null
        return handler.awaitOneOrNull { db -> db.anime_seriesQueries.getSeriesById(entry.seriesId, animeSeriesMapper) }
    }

    override fun getEntriesForSeries(seriesId: Long): Flow<List<AnimeSeriesEntry>> {
        return handler.subscribeToList { db ->
            db.anime_series_entriesQueries.getEntriesBySeriesId(seriesId, animeSeriesEntryMapper)
        }
    }

    override fun getLibrarySeriesWithEntries(): Flow<List<LibraryAnimeSeries>> {
        return combine(
            getAllSeries(),
            handler.subscribeToList { db -> db.anime_series_entriesQueries.getAllEntries(animeSeriesEntryMapper) },
            animeRepository.getLibraryAnimeAsFlow(),
        ) { seriesList, allEntries, allLibraryAnimes ->
            val libraryAnimesById = allLibraryAnimes.associateBy { it.anime.id }
            val entriesBySeriesId = allEntries.groupBy { it.seriesId }

            seriesList.map { series ->
                val entriesForSeries = entriesBySeriesId[series.id]?.sortedBy { it.position } ?: emptyList()
                val libraryAnimesForSeries = entriesForSeries.mapNotNull { libraryAnimesById[it.animeId] }
                LibraryAnimeSeries(series, libraryAnimesForSeries)
            }
        }
    }

    override fun getAllAnimeIdsInAnySeries(): Flow<Set<Long>> {
        return handler.subscribeToList { db -> db.anime_series_entriesQueries.getAllAnimeIdsInAnySeries() }
            .map { it.toSet() }
    }

    override suspend fun insertSeries(series: AnimeSeries): Long {
        return handler.awaitOneExecutable(inTransaction = true) { db ->
            db.anime_seriesQueries.insert(
                title = series.title,
                description = series.description,
                categoryId = series.categoryId,
                sortOrder = series.sortOrder,
                dateAdded = series.dateAdded,
                coverLastModified = series.coverLastModified,
                pinned = series.pinned,
                coverMode = series.coverMode.value,
                coverEntryId = series.coverEntryId,
            )
            db.anime_seriesQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updateSeries(series: AnimeSeries) {
        handler.await { db ->
            db.anime_seriesQueries.update(
                id = series.id,
                title = series.title,
                description = series.description,
                categoryId = series.categoryId,
                sortOrder = series.sortOrder,
                dateAdded = series.dateAdded,
                coverLastModified = series.coverLastModified,
                pinned = series.pinned,
                coverMode = series.coverMode.value,
                coverEntryId = series.coverEntryId,
            )
        }
    }

    override suspend fun deleteSeries(seriesId: Long) {
        handler.await { db ->
            db.anime_seriesQueries.delete(seriesId)
        }
    }

    override suspend fun moveSeriesFromCategoryToDefault(categoryId: Long) {
        handler.await { db ->
            db.anime_seriesQueries.resetCategory(categoryId)
        }
    }

    override suspend fun insertEntry(entry: AnimeSeriesEntry) {
        handler.await { db ->
            db.anime_series_entriesQueries.insert(
                seriesId = entry.seriesId,
                animeId = entry.animeId,
                position = entry.position.toLong(),
            )
        }
    }

    override suspend fun deleteEntry(animeId: Long) {
        handler.await { db ->
            db.anime_series_entriesQueries.deleteByAnimeId(animeId)
        }
    }

    override suspend fun updateEntryPositions(entries: List<AnimeSeriesEntry>) {
        handler.await(inTransaction = true) { db ->
            entries.forEach { entry ->
                db.anime_series_entriesQueries.updatePosition(
                    position = entry.position.toLong(),
                    id = entry.id,
                )
            }
        }
    }
}
