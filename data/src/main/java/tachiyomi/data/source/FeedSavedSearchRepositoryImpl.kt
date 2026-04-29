package tachiyomi.data.source

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.SourceType
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class FeedSavedSearchRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : FeedSavedSearchRepository {

    override suspend fun getGlobal(sourceType: SourceType): List<FeedSavedSearch> {
        return handler.awaitList { db ->
            db.feed_saved_searchQueries.selectAllGlobal(sourceType.id, FeedSavedSearchMapper::map)
        }
    }

    override fun getGlobalAsFlow(sourceType: SourceType): Flow<List<FeedSavedSearch>> {
        return handler.subscribeToList { db ->
            db.feed_saved_searchQueries.selectAllGlobal(sourceType.id, FeedSavedSearchMapper::map)
        }
    }

    override suspend fun countGlobal(sourceType: SourceType): Long {
        return handler.awaitOne { db ->
            db.feed_saved_searchQueries.countGlobal(sourceType.id)
        }
    }

    override suspend fun delete(feedSavedSearchId: Long) {
        handler.await { db ->
            db.feed_saved_searchQueries.deleteById(feedSavedSearchId)
        }
    }

    override suspend fun insert(feedSavedSearch: FeedSavedSearch): Long? {
        return handler.await(inTransaction = true) {
            val existing = handler.awaitList { db ->
                db.feed_saved_searchQueries.selectAllGlobal(feedSavedSearch.sourceType.id, FeedSavedSearchMapper::map)
            }
            val duplicate = existing.find { current ->
                current.source == feedSavedSearch.source &&
                    current.sourceType == feedSavedSearch.sourceType &&
                    current.savedSearch == feedSavedSearch.savedSearch &&
                    current.global == feedSavedSearch.global
            }
            duplicate?.id ?: handler.awaitOneExecutable { db ->
                db.feed_saved_searchQueries.insert(
                    feedSavedSearch.source,
                    feedSavedSearch.sourceType.id,
                    feedSavedSearch.savedSearch,
                    feedSavedSearch.global,
                )
                db.feed_saved_searchQueries.selectLastInsertedRowId()
            }
        }
    }

    override suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>) {
        handler.await(inTransaction = true) { db ->
            feedSavedSearch.forEach {
                db.feed_saved_searchQueries.insert(it.source, it.sourceType.id, it.savedSearch, it.global)
            }
        }
    }

    override suspend fun updatePartial(update: FeedSavedSearchUpdate) {
        handler.await { db ->
            db.feed_saved_searchQueries.update(
                source = update.source,
                media_type = update.sourceType?.id,
                saved_search = update.savedSearch,
                global = update.global,
                feed_order = update.feedOrder,
                id = update.id,
            )
        }
    }

    override suspend fun updatePartial(updates: List<FeedSavedSearchUpdate>) {
        handler.await(inTransaction = true) { db ->
            for (update in updates) {
                db.feed_saved_searchQueries.update(
                    source = update.source,
                    media_type = update.sourceType?.id,
                    saved_search = update.savedSearch,
                    global = update.global,
                    feed_order = update.feedOrder,
                    id = update.id,
                )
            }
        }
    }
}
