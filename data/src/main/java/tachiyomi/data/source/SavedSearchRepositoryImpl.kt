package tachiyomi.data.source

import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

class SavedSearchRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : SavedSearchRepository {

    override suspend fun getById(savedSearchId: Long): SavedSearch? {
        return handler.awaitOneOrNull { db ->
            db.saved_searchQueries.selectById(savedSearchId, SavedSearchMapper::map)
        }
    }

    override suspend fun getBySourceId(sourceId: Long): List<SavedSearch> {
        return handler.awaitList { db ->
            db.saved_searchQueries.selectBySource(sourceId, SavedSearchMapper::map)
        }
    }

    override suspend fun delete(savedSearchId: Long) {
        handler.await { db -> db.saved_searchQueries.deleteById(savedSearchId) }
    }

    override suspend fun insert(savedSearch: SavedSearch): Long? {
        return handler.await(inTransaction = true) {
            val existing = handler.awaitList { db ->
                db.saved_searchQueries.selectBySource(savedSearch.source, SavedSearchMapper::map)
            }
            val duplicate = existing.find {
                it.source == savedSearch.source &&
                    it.name == savedSearch.name &&
                    it.query == savedSearch.query &&
                    it.filtersJson == savedSearch.filtersJson
            }
            duplicate?.id ?: handler.awaitOneExecutable { db ->
                db.saved_searchQueries.insert(
                    savedSearch.source,
                    savedSearch.name,
                    savedSearch.query,
                    savedSearch.filtersJson,
                )
                db.saved_searchQueries.selectLastInsertedRowId()
            }
        }
    }
}
