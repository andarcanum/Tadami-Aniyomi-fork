package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

/**
 * Force-runs a batch of migrations that were ported from Mihon with low version numbers
 * (131f–139f) but were silently skipped in Tadami.
 *
 * Tadami's BuildConfig.VERSION_CODE is significantly higher than Mihon's (currently 187+).
 * When a user upgrades (e.g. last_version_code=182 → 187), the VersionRangeMigrationStrategy
 * only considers migrations whose version.toInt() falls into (old+1)..new.
 *
 * All these low-numbered migrations fell outside the range for users who were already
 * past ~146, causing data, preferences, and job setups to be lost or left in legacy state.
 *
 * This wrapper is registered at 187f. It is safe to call the individual migrations again
 * because they are defensive (they check whether the work has already been done).
 *
 * Includes ExtensionRepoToStoreMigration for completeness (even though it is now ALWAYS
 * and has additional resilience inside the store repositories).
 */
class ForceMissedMigrations187 : Migration {
    override val version: Float = 187f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        // Run in historical order (lowest to highest original version)
        AuroraSectionMigration().invoke(migrationContext)
        SetupNovelLibraryUpdateMigration().invoke(migrationContext)
        CoalesceLibraryUpdateWorkersMigration().invoke(migrationContext)
        NavigationTransitionModeMigration().invoke(migrationContext)
        DefaultChapterEpisodeSortNumberMigration().invoke(migrationContext)
        DefaultChapterSortAscendingMigration().invoke(migrationContext)
        EInkProfileMigration().invoke(migrationContext)
        MoveLatestToFeedMigration().invoke(migrationContext)
        ExtensionRepoToStoreMigration().invoke(migrationContext)

        return true
    }
}
