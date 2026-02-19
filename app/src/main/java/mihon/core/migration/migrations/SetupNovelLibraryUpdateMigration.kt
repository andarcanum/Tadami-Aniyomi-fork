package mihon.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class SetupNovelLibraryUpdateMigration : Migration {
    override val version = 132f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        NovelLibraryUpdateJob.setupTask(context)
        return true
    }
}
