package mihon.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.library.LibraryAutoUpdateSchedulerJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class CoalesceLibraryUpdateWorkersMigration : Migration {
    override val version = 133f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        LibraryAutoUpdateSchedulerJob.setupTask(context)
        return true
    }
}
