package mihon.core.migration.migrations

import eu.kanade.domain.source.service.SourcePreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch

class MoveLatestToFeedMigration : Migration {
    override val version: Float = 138f
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return false
        val insertFeedSavedSearch = migrationContext.get<InsertFeedSavedSearch>() ?: return false

        val feedSources = sourcePreferences.mangaFeedSources().get()
        if (feedSources.isEmpty()) return true

        val entries = feedSources.map {
            FeedSavedSearch(id = -1, source = it.toLong(), savedSearch = null, global = true, feedOrder = 0)
        }
        insertFeedSavedSearch.awaitAll(entries)

        sourcePreferences.mangaFeedSources().set(emptySet())
        return true
    }
}
