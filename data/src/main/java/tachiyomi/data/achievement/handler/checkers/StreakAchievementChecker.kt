package tachiyomi.data.achievement.handler.checkers

/**
 * Проверщик достижений серий (streak).
 *
 * Отслеживает последовательные дни активности пользователя для достижений типа STREAK.
 * Серия не прерывается, если еще нет активности сегодня - проверяется вчерашний день.
 *
 * Активность:
 * - Чтение главы манги
 * - Просмотр серии аниме
 *
 * Алгоритм подсчета:
 * 1. Проверить сегодняшний день
 * 2. Если есть активность сегодня - считать и идти к вчерашнему дню
 * 3. Если нет активности сегодня - проверить вчерашний день
 * 4. Продолжать пока есть активность в каждый день
 * 5. Прервать если дня без активности
 *
 * @param database База данных достижений для логирования активности
 *
 * @see AchievementType.STREAK
 */
import tachiyomi.data.achievement.database.AchievementsDatabase

class StreakAchievementChecker(
    private val database: AchievementsDatabase,
) {

    companion object {
        /** Количество миллисекунд в дне */
        private const val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L

        /** Максимальная серия для проверки (предотвращает бесконечные циклы) */
        private const val MAX_STREAK_DAYS = 365
    }

    /**
     * Calculate the current streak of consecutive days with activity.
     * Does not break streak if there's no activity yet today.
     */
    suspend fun getCurrentStreak(): Int {
        var streak = 0
        var checkDate = getTodayDate()
        var checkedToday = false

        // Check up to MAX_STREAK_DAYS back
        repeat(MAX_STREAK_DAYS) {
            val activity = getActivityForDate(checkDate)

            when {
                // First iteration (today): no activity yet is OK, check yesterday
                !checkedToday && activity == null -> {
                    checkedToday = true
                    checkDate = getPreviousDayDate(checkDate)
                    return@repeat
                }
                // First iteration (today): has activity, count it and continue
                !checkedToday && hasActivity(activity) -> {
                    checkedToday = true
                    streak++
                    checkDate = getPreviousDayDate(checkDate)
                    return@repeat
                }
                // First iteration (today): no activity log at all, check yesterday
                !checkedToday -> {
                    checkedToday = true
                    checkDate = getPreviousDayDate(checkDate)
                    return@repeat
                }
                // Subsequent iterations: need activity to continue streak
                hasActivity(activity) -> {
                    streak++
                    checkDate = getPreviousDayDate(checkDate)
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
        val today = getTodayDate()
        database.achievementActivityLogQueries.upsertActivityLog(
            date = today,
            chapter_count = 1,
            episode_count = 0,
            last_updated = System.currentTimeMillis(),
        )
    }

    /**
     * Record that an episode was watched today.
     */
    suspend fun logEpisodeWatched() {
        val today = getTodayDate()
        database.achievementActivityLogQueries.upsertActivityLog(
            date = today,
            chapter_count = 0,
            episode_count = 1,
            last_updated = System.currentTimeMillis(),
        )
    }

    /**
     * Get the activity record for a specific date.
     */
    private suspend fun getActivityForDate(date: Long): ActivityLog? {
        return database.achievementActivityLogQueries.getActivityForDate(
            date,
            ::mapActivityLog,
        ).executeAsOneOrNull()
    }

    /**
     * Check if an activity log contains any activity.
     */
    private fun hasActivity(activity: ActivityLog?): Boolean {
        return activity != null && (activity.chapterCount > 0 || activity.episodeCount > 0)
    }

    /**
     * Get today's date as a day-aligned timestamp (milliseconds since epoch, at midnight).
     * Uses UTC to ensure consistent date boundaries across timezones.
     */
    private fun getTodayDate(): Long {
        val currentTime = System.currentTimeMillis()
        return (currentTime / MILLIS_IN_DAY) * MILLIS_IN_DAY
    }

    /**
     * Get the previous day's date.
     */
    private fun getPreviousDayDate(date: Long): Long {
        return date - MILLIS_IN_DAY
    }

    /**
     * Data class representing an activity log entry.
     */
    private data class ActivityLog(
        val date: Long,
        val chapterCount: Long,
        val episodeCount: Long,
        val lastUpdated: Long,
    )

    /**
     * Mapper function for SQL query results.
     */
    private fun mapActivityLog(
        date: Long,
        chapter_count: Long,
        episode_count: Long,
        last_updated: Long,
    ): ActivityLog {
        return ActivityLog(
            date = date,
            chapterCount = chapter_count,
            episodeCount = episode_count,
            lastUpdated = last_updated,
        )
    }
}
