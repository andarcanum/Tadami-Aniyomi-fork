package tachiyomi.data.achievement.handler.checkers

/**
 * Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋРІР‚В°Р В РЎвЂР В РЎвЂќ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р РЋР С“Р В Р’ВµР РЋР вЂљР В РЎвЂР В РІвЂћвЂ“ (streak).
 *
 * Р В РЎвЂєР РЋРІР‚С™Р РЋР С“Р В Р’В»Р В Р’ВµР В Р’В¶Р В РЎвЂР В Р вЂ Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В Р’В»Р В Р’ВµР В РўвЂР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р В Р’ВµР В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В РўвЂР В Р вЂ¦Р В РЎвЂ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р В Р’ВµР В Р’В»Р РЋР РЏ Р В РўвЂР В Р’В»Р РЋР РЏ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂ”Р В Р’В° STREAK.
 * Р В Р Р‹Р В Р’ВµР РЋР вЂљР В РЎвЂР РЋР РЏ Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ, Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’ВµР РЋРІР‚В°Р В Р’Вµ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ Р РЋР С“Р В Р’ВµР В РЎвЂ“Р В РЎвЂўР В РўвЂР В Р вЂ¦Р РЋР РЏ - Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р вЂ Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’В°Р РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РўвЂР В Р’ВµР В Р вЂ¦Р РЋР Р‰.
 *
 * Р В РЎвЂ™Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰:
 * - Р В Р’В§Р РЋРІР‚С™Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ“Р В Р’В»Р В Р’В°Р В Р вЂ Р РЋРІР‚в„– Р В РЎВР В Р’В°Р В Р вЂ¦Р В РЎвЂ“Р В РЎвЂ
 * - Р В РЎСџР РЋР вЂљР В РЎвЂўР РЋР С“Р В РЎВР В РЎвЂўР РЋРІР‚С™Р РЋР вЂљ Р РЋР С“Р В Р’ВµР РЋР вЂљР В РЎвЂР В РЎвЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В РЎВР В Р’Вµ
 *
 * Р В РЎвЂ™Р В Р’В»Р В РЎвЂ“Р В РЎвЂўР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р В РЎВ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР РЋР С“Р РЋРІР‚РЋР В Р’ВµР РЋРІР‚С™Р В Р’В°:
 * 1. Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р РЋР С“Р В Р’ВµР В РЎвЂ“Р В РЎвЂўР В РўвЂР В Р вЂ¦Р РЋР РЏР РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РўвЂР В Р’ВµР В Р вЂ¦Р РЋР Р‰
 * 2. Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р РЋР С“Р В Р’ВµР В РЎвЂ“Р В РЎвЂўР В РўвЂР В Р вЂ¦Р РЋР РЏ - Р РЋР С“Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ Р В РЎвЂР В РўвЂР РЋРІР‚С™Р В РЎвЂ Р В РЎвЂќ Р В Р вЂ Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’В°Р РЋРІвЂљВ¬Р В Р вЂ¦Р В Р’ВµР В РЎВР РЋРЎвЂњ Р В РўвЂР В Р вЂ¦Р РЋР вЂ№
 * 3. Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ Р РЋР С“Р В Р’ВµР В РЎвЂ“Р В РЎвЂўР В РўвЂР В Р вЂ¦Р РЋР РЏ - Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’В°Р РЋРІвЂљВ¬Р В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РўвЂР В Р’ВµР В Р вЂ¦Р РЋР Р‰
 * 4. Р В РЎСџР РЋР вЂљР В РЎвЂўР В РўвЂР В РЎвЂўР В Р’В»Р В Р’В¶Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В° Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ  Р В РЎвЂќР В Р’В°Р В Р’В¶Р В РўвЂР РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РўвЂР В Р’ВµР В Р вЂ¦Р РЋР Р‰
 * 5. Р В РЎСџР РЋР вЂљР В Р’ВµР РЋР вЂљР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В РўвЂР В Р вЂ¦Р РЋР РЏ Р В Р’В±Р В Р’ВµР В Р’В· Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ
 *
 * @param database Р В РІР‚ВР В Р’В°Р В Р’В·Р В Р’В° Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В РўвЂР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В РўвЂР В Р’В»Р РЋР РЏ Р В Р’В»Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ
 *
 * @see AchievementType.STREAK
 */
import tachiyomi.data.achievement.Activity_log
import tachiyomi.data.achievement.database.AchievementsDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StreakAchievementChecker(
    private val database: AchievementsDatabase,
) {

    companion object {
        /** Р В РЎС™Р В Р’В°Р В РЎвЂќР РЋР С“Р В РЎвЂР В РЎВР В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р В Р’В°Р РЋР РЏ Р РЋР С“Р В Р’ВµР РЋР вЂљР В РЎвЂР РЋР РЏ Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В РЎвЂ (Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР В РЎвЂўР РЋРІР‚С™Р В Р вЂ Р РЋР вЂљР В Р’В°Р РЋРІР‚В°Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В Р’В±Р В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂўР В Р вЂ¦Р В Р’ВµР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р РЋРІР‚В Р В РЎвЂР В РЎвЂќР В Р’В»Р РЋРІР‚в„–) */
        private const val MAX_STREAK_DAYS = 365

        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /**
     * Calculate the current streak of consecutive days with activity.
     * Does not break streak if there's no activity yet today.
     */
    suspend fun getCurrentStreak(): Int {
        var streak = 0
        var checkDate = LocalDate.now()
        var checkedToday = false

        // Check up to MAX_STREAK_DAYS back
        repeat(MAX_STREAK_DAYS) {
            val activity = getActivityForDate(checkDate)

            when {
                // First iteration (today): no activity yet is OK, check yesterday
                !checkedToday && activity == null -> {
                    checkedToday = true
                    checkDate = checkDate.minusDays(1)
                    return@repeat
                }
                // First iteration (today): has activity, count it and continue
                !checkedToday && hasActivity(activity) -> {
                    checkedToday = true
                    streak++
                    checkDate = checkDate.minusDays(1)
                    return@repeat
                }
                // First iteration (today): no activity log at all, check yesterday
                !checkedToday -> {
                    checkedToday = true
                    checkDate = checkDate.minusDays(1)
                    return@repeat
                }
                // Subsequent iterations: need activity to continue streak
                hasActivity(activity) -> {
                    streak++
                    checkDate = checkDate.minusDays(1)
                    return@repeat
                }
                // No activity on this day, streak broken
                else -> return streak
            }
        }

        return streak
    }

    /**
     * Record that a chapter was read today.
     */
    suspend fun logChapterRead() {
        val today = LocalDate.now().format(DATE_FORMATTER)
        val now = System.currentTimeMillis()

        database.activityLogQueries.incrementChapters(
            date = today,
            level = 1, // Will be calculated by the query based on count
            count = 1,
            last_updated = now,
        )
    }

    /**
     * Record that an episode was watched today.
     */
    suspend fun logEpisodeWatched() {
        val today = LocalDate.now().format(DATE_FORMATTER)
        val now = System.currentTimeMillis()

        database.activityLogQueries.incrementEpisodes(
            date = today,
            level = 1, // Will be calculated by the query based on count
            count = 1,
            last_updated = now,
        )
    }

    /**
     * Get the activity record for a specific date.
     */
    private suspend fun getActivityForDate(date: LocalDate): ActivityLog? {
        val dateStr = date.format(DATE_FORMATTER)
        val record: Activity_log? = database.activityLogQueries
            .getActivityForDate(dateStr)
            .executeAsOneOrNull()

        return if (record != null) {
            ActivityLog(
                date = dateStr,
                chapterCount = record.chapters_read,
                episodeCount = record.episodes_watched,
                lastUpdated = record.last_updated,
            )
        } else {
            null
        }
    }

    /**
     * Check if an activity log contains any activity.
     */
    private fun hasActivity(activity: ActivityLog?): Boolean {
        return activity != null && (activity.chapterCount > 0 || activity.episodeCount > 0)
    }

    /**
     * Data class representing an activity log entry.
     */
    private data class ActivityLog(
        val date: String,
        val chapterCount: Long,
        val episodeCount: Long,
        val lastUpdated: Long,
    )
}
