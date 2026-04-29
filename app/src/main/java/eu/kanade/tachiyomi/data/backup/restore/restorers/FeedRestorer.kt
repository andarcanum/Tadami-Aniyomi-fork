package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.source.FeedSavedSearchMapper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FeedRestorer(
    private val handler: MangaDatabaseHandler = Injekt.get(),
) {
    suspend fun restoreFeeds(backupFeeds: List<BackupFeed>) {
        if (backupFeeds.isEmpty()) return

        val existing = handler.awaitList { db ->
            db.feed_saved_searchQueries.selectAllGlobal(FeedSavedSearchMapper::map)
        }
        val newFeeds = backupFeeds.filter { backup ->
            existing.none { it.source == backup.source && it.global }
        }
        if (newFeeds.isEmpty()) return

        handler.await(inTransaction = true) { db ->
            for (feed in newFeeds) {
                db.feed_saved_searchQueries.insert(feed.source, null, feed.global)
            }
        }
    }
}
