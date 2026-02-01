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
import tachiyomi.data.achievement.Activity_log
import tachiyomi.data.achievement.database.AchievementsDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StreakAchievementChecker(
    private val database: AchievementsDatabase,
) {

    companion object {
        /** Максимальная серия для проверки (предотвращает бесконечные циклы) */
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
