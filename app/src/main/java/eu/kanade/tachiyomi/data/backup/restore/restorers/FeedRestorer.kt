package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.backupFeedMapper
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.source.SavedSearchMapper
import tachiyomi.domain.source.model.SourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FeedRestorer(
    private val handler: MangaDatabaseHandler = Injekt.get(),
) {
    suspend fun restoreFeeds(backupFeeds: List<BackupFeed>) {
        if (backupFeeds.isEmpty()) return

        val existing = handler.awaitList { db ->
            db.feed_saved_searchQueries.selectAllFeedWithSavedSearch(backupFeedMapper)
        }
        val newFeeds = backupFeeds.sortedWith(
            compareBy<BackupFeed> { it.sourceType }
                .thenBy { it.feedOrder },
        ).filter { backup ->
            existing.none { current ->
                current.source == backup.source &&
                    current.sourceType == backup.sourceType &&
                    current.global == backup.global &&
                    current.savedSearchName == backup.savedSearchName &&
                    current.savedSearchQuery == backup.savedSearchQuery &&
                    current.savedSearchFiltersJson == backup.savedSearchFiltersJson
            }
        }
        if (newFeeds.isEmpty()) return

        handler.await(inTransaction = true) { db ->
            for (feed in newFeeds) {
                val sourceType = SourceType.fromId(feed.sourceType)
                val savedSearchId = if (feed.savedSearchName != null) {
                    val existingSearch = db.saved_searchQueries
                        .selectBySource(feed.source, sourceType.id, SavedSearchMapper::map)
                        .executeAsList()
                        .firstOrNull {
                            it.name == feed.savedSearchName &&
                                it.query == feed.savedSearchQuery &&
                                it.filtersJson == feed.savedSearchFiltersJson
                        }
                    existingSearch?.id ?: run {
                        db.saved_searchQueries.insert(
                            source = feed.source,
                            sourceType = sourceType.id,
                            name = feed.savedSearchName,
                            query = feed.savedSearchQuery,
                            filtersJson = feed.savedSearchFiltersJson,
                        )
                        db.saved_searchQueries.selectLastInsertedRowId().executeAsOne()
                    }
                } else {
                    null
                }
                db.feed_saved_searchQueries.insert(feed.source, sourceType.id, savedSearchId, feed.global)
            }
        }
    }
}
