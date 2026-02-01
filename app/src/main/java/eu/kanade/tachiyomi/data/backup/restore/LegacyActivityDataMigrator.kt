package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.content.SharedPreferences
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Migrator for legacy activity data from SharedPreferences to database.
 *
 * Handles migration of data stored in old format:
 * - SharedPreferences key format: "chapters_YYYY-MM-DD", "episodes_YYYY-MM-DD", etc.
 * - Migrates to new activity_log database table
 *
 * This is a one-time migration that runs on app upgrade from v4 to v5.
 */
class LegacyActivityDataMigrator(
    private val context: Context,
    private val repository: ActivityDataRepository,
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    companion object {
        private const val PREFS_NAME = "activity_data"
        private const val MIGRATION_COMPLETE_KEY = "legacy_activity_migrated_v5"

        private const val KEY_PREFIX_CHAPTERS = "chapters_"
        private const val KEY_PREFIX_EPISODES = "episodes_"
        private const val KEY_PREFIX_APP_OPENS = "app_opens_"
        private const val KEY_PREFIX_ACHIEVEMENTS = "achievements_"
        private const val KEY_PREFIX_DURATION = "duration_"
    }

    /**
     * Check if migration has already been completed
     */
    suspend fun isMigrationNeeded(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyMigrated = prefs.getBoolean(MIGRATION_COMPLETE_KEY, false)

        if (alreadyMigrated) {
            logcat(LogPriority.INFO) { "[ActivityMigration] Migration already completed, skipping" }
            return false
        }

        // Check if there's any legacy data to migrate
        val hasLegacyData = prefs.all.keys.any { key ->
            key.startsWith(KEY_PREFIX_CHAPTERS) ||
                key.startsWith(KEY_PREFIX_EPISODES) ||
                key.startsWith(KEY_PREFIX_APP_OPENS) ||
                key.startsWith(KEY_PREFIX_ACHIEVEMENTS) ||
                key.startsWith(KEY_PREFIX_DURATION)
        }

        if (!hasLegacyData) {
            logcat(LogPriority.INFO) { "[ActivityMigration] No legacy data found, marking as migrated" }
            prefs.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
            return false
        }

        logcat(LogPriority.INFO) { "[ActivityMigration] Legacy data detected, migration needed" }
        return true
    }

    /**
     * Migrate all legacy activity data from SharedPreferences to database
     *
     * @return Migration result with statistics
     */
    suspend fun migrate(): MigrationResult {
        val startTime = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        try {
            logcat(LogPriority.INFO) { "[ActivityMigration] Starting migration..." }

            // Extract all unique dates from legacy data
            val dates = extractAllDates(prefs)
            logcat(LogPriority.INFO) { "[ActivityMigration] Found ${dates.size} unique dates" }

            var recordsMigrated = 0
            var recordsFailed = 0

            // Migrate each date's data
            dates.forEach { dateStr ->
                try {
                    val date = LocalDate.parse(dateStr, dateFormatter)

                    val chaptersRead = prefs.getInt(KEY_PREFIX_CHAPTERS + dateStr, 0)
                    val episodesWatched = prefs.getInt(KEY_PREFIX_EPISODES + dateStr, 0)
                    val appOpens = prefs.getInt(KEY_PREFIX_APP_OPENS + dateStr, 0)
                    val achievementsUnlocked = prefs.getInt(KEY_PREFIX_ACHIEVEMENTS + dateStr, 0)
                    val durationMs = prefs.getLong(KEY_PREFIX_DURATION + dateStr, 0L)

                    // Skip empty records (all zeros)
                    if (chaptersRead == 0 && episodesWatched == 0 && appOpens == 0 &&
                        achievementsUnlocked == 0 && durationMs == 0L
                    ) {
                        return@forEach
                    }

                    repository.upsertActivityData(
                        date = date,
                        chaptersRead = chaptersRead,
                        episodesWatched = episodesWatched,
                        appOpens = appOpens,
                        achievementsUnlocked = achievementsUnlocked,
                        durationMs = durationMs,
                    )

                    recordsMigrated++
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "[ActivityMigration] Failed to migrate date $dateStr: ${e.message}" }
                    recordsFailed++
                }
            }

            // Mark migration as complete
            prefs.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()

            val duration = System.currentTimeMillis() - startTime
            logcat(LogPriority.INFO) {
                "[ActivityMigration] Migration completed in ${duration}ms: $recordsMigrated migrated, $recordsFailed failed"
            }

            return MigrationResult(
                success = true,
                recordsMigrated = recordsMigrated,
                recordsFailed = recordsFailed,
                duration = duration,
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "[ActivityMigration] Migration failed: ${e.message}" }
            return MigrationResult(
                success = false,
                error = e.message ?: "Unknown error",
            )
        }
    }

    /**
     * Extract all unique dates from SharedPreferences keys
     */
    private fun extractAllDates(prefs: SharedPreferences): Set<String> {
        val dates = mutableSetOf<String>()

        prefs.all.keys.forEach { key ->
            when {
                key.startsWith(KEY_PREFIX_CHAPTERS) -> dates.add(key.removePrefix(KEY_PREFIX_CHAPTERS))
                key.startsWith(KEY_PREFIX_EPISODES) -> dates.add(key.removePrefix(KEY_PREFIX_EPISODES))
                key.startsWith(KEY_PREFIX_APP_OPENS) -> dates.add(key.removePrefix(KEY_PREFIX_APP_OPENS))
                key.startsWith(KEY_PREFIX_ACHIEVEMENTS) -> dates.add(key.removePrefix(KEY_PREFIX_ACHIEVEMENTS))
                key.startsWith(KEY_PREFIX_DURATION) -> dates.add(key.removePrefix(KEY_PREFIX_DURATION))
            }
        }

        return dates.filter { dateStr ->
            // Validate ISO-8601 format (YYYY-MM-DD)
            try {
                LocalDate.parse(dateStr, dateFormatter)
                true
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "[ActivityMigration] Invalid date format: $dateStr" }
                false
            }
        }.toSet()
    }

    /**
     * Clear legacy SharedPreferences data after successful migration
     * (optional cleanup step)
     */
    suspend fun clearLegacyData() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        var keysCleared = 0
        prefs.all.keys.forEach { key ->
            if (key.startsWith(KEY_PREFIX_CHAPTERS) ||
                key.startsWith(KEY_PREFIX_EPISODES) ||
                key.startsWith(KEY_PREFIX_APP_OPENS) ||
                key.startsWith(KEY_PREFIX_ACHIEVEMENTS) ||
                key.startsWith(KEY_PREFIX_DURATION)
            ) {
                editor.remove(key)
                keysCleared++
            }
        }

        editor.apply()
        logcat(LogPriority.INFO) { "[ActivityMigration] Cleared $keysCleared legacy keys from SharedPreferences" }
    }

    data class MigrationResult(
        val success: Boolean,
        val recordsMigrated: Int = 0,
        val recordsFailed: Int = 0,
        val duration: Long = 0,
        val error: String? = null,
    )
}
