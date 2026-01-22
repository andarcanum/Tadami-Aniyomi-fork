package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class DefaultAnimeExtensionRepoMigration : Migration {
    override val version = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        return@withIOContext true
    }

    companion object {
        const val DEFAULT_ANIME_REPO_URL = ""
    }
}
