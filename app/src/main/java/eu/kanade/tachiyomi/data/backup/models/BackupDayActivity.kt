package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.achievement.model.ActivityType
import tachiyomi.domain.achievement.model.DayActivity
import java.time.LocalDate

/**
 * Backup model for daily activity data.
 *
 * Supports both legacy format (date/level/type only) and new format (detailed metrics).
 * Backward compatibility: old backups with only ProtoNumbers 1-3 will still restore correctly.
 */
@Serializable
data class BackupDayActivity(
    @ProtoNumber(1) var date: String = "", // ISO-8601 format (YYYY-MM-DD)
    @ProtoNumber(2) var level: Int = 0,
    @ProtoNumber(3) var type: Int = 0, // ActivityType ordinal

    // Detailed metrics (added in v5, optional for backward compatibility)
    @ProtoNumber(4) var chaptersRead: Int = 0,
    @ProtoNumber(5) var episodesWatched: Int = 0,
    @ProtoNumber(6) var appOpens: Int = 0,
    @ProtoNumber(7) var achievementsUnlocked: Int = 0,
    @ProtoNumber(8) var durationMs: Long = 0,
) {
    /**
     * Convert to domain model (DayActivity)
     * Used for displaying activity in UI
     */
    fun toDayActivity(): DayActivity {
        return DayActivity(
            date = LocalDate.parse(date),
            level = level,
            type = ActivityType.entries.getOrElse(type) { ActivityType.APP_OPEN },
        )
    }

    /**
     * Convert to database insert parameters
     * Returns tuple of (date, chaptersRead, episodesWatched, appOpens, achievementsUnlocked, durationMs)
     */
    fun toDatabaseParams(): ActivityLogParams {
        return ActivityLogParams(
            date = LocalDate.parse(date),
            chaptersRead = chaptersRead,
            episodesWatched = episodesWatched,
            appOpens = appOpens,
            achievementsUnlocked = achievementsUnlocked,
            durationMs = durationMs,
        )
    }

    companion object {
        /**
         * Create from DayActivity (legacy format)
         * Only includes date/level/type for UI display purposes
         */
        fun fromDayActivity(activity: DayActivity): BackupDayActivity {
            return BackupDayActivity(
                date = activity.date.toString(),
                level = activity.level,
                type = activity.type.ordinal,
            )
        }

        /**
         * Create from detailed database record (new format)
         * Includes all metrics for complete backup
         */
        fun fromDatabaseRecord(
            date: LocalDate,
            level: Int,
            type: ActivityType,
            chaptersRead: Int,
            episodesWatched: Int,
            appOpens: Int,
            achievementsUnlocked: Int,
            durationMs: Long,
        ): BackupDayActivity {
            return BackupDayActivity(
                date = date.toString(),
                level = level,
                type = type.ordinal,
                chaptersRead = chaptersRead,
                episodesWatched = episodesWatched,
                appOpens = appOpens,
                achievementsUnlocked = achievementsUnlocked,
                durationMs = durationMs,
            )
        }
    }
}

/**
 * Data class for activity log database parameters
 */
data class ActivityLogParams(
    val date: LocalDate,
    val chaptersRead: Int,
    val episodesWatched: Int,
    val appOpens: Int,
    val achievementsUnlocked: Int,
    val durationMs: Long,
)
