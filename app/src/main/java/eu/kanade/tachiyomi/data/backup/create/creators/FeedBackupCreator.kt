package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.backupFeedMapper
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FeedBackupCreator(
    private val handler: MangaDatabaseHandler = Injekt.get(),
) {
    suspend operator fun invoke(): List<BackupFeed> {
        return handler.awaitList { db ->
            db.feed_saved_searchQueries.selectAllFeedWithSavedSearch(backupFeedMapper)
        }
    }
}
