package tachiyomi.data.achievement

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.ActivityType
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Repository implementation for daily activity tracking.
 * Uses SQLite database to store activity data with ISO-8601 date format.
 *
 * Note: Content ID deduplication (preventing duplicate chapter/episode counts for the same ID)
 * is now the responsibility of the caller. The database increment operations are idempotent
 * at the date level but will accumulate counts if called multiple times.
 */
class ActivityDataRepositoryImpl(
    private val database: AchievementsDatabase,
) : ActivityDataRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun getActivityData(days: Int): Flow<List<DayActivity>> {
        val today = LocalDate.now()
        val startDate = today.minusDays((days - 1).toLong())
        val startDateStr = startDate.format(dateFormatter)
        val endDateStr = today.format(dateFormatter)

        return database.activityLogQueries
            .getActivityForDateRange(startDateStr, endDateStr)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { records ->
                // Create full date range with all days (including empty ones)
                val activities = mutableListOf<DayActivity>()
                var currentDate = startDate

                while (!currentDate.isAfter(today)) {
                    val dateStr = currentDate.format(dateFormatter)
                    val record = records.firstOrNull { it.date == dateStr }

                    val (type, level) = if (record != null) {
                        calculateActivityTypeAndLevel(
                            chaptersRead = record.chapters_read.toInt(),
                            episodesWatched = record.episodes_watched.toInt(),
                            appOpens = record.app_opens.toInt(),
                            achievementsUnlocked = record.achievements_unlocked.toInt(),
                        )
                    } else {
                        ActivityType.APP_OPEN to 0
                    }

                    activities.add(DayActivity(currentDate, level, type))
                    currentDate = currentDate.plusDays(1)
                }

                activities // Ascending order: Oldest -> Newest
            }
    }

    override suspend fun getMonthStats(year: Int, month: Int): MonthStats {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        val startDateStr = startDate.format(dateFormatter)
        val endDateStr = endDate.format(dateFormatter)

        val stats = database.activityLogQueries
            .getActivityStats(startDateStr, endDateStr)
            .executeAsOneOrNull()

        // Convert ms to minutes
        val timeInAppMinutes = ((stats?.total_duration ?: 0L) / 60000).toInt()

        return MonthStats(
            chaptersRead = stats?.total_chapters?.toInt() ?: 0,
            episodesWatched = stats?.total_episodes?.toInt() ?: 0,
            timeInAppMinutes = timeInAppMinutes,
            achievementsUnlocked = stats?.total_achievements?.toInt() ?: 0,
        )
    }

    override suspend fun getCurrentMonthStats(): MonthStats {
        val now = LocalDate.now()
        return getMonthStats(now.year, now.monthValue)
    }

    override suspend fun getPreviousMonthStats(): MonthStats {
        val now = LocalDate.now()
        val previousMonth = now.minusMonths(1)
        return getMonthStats(previousMonth.year, previousMonth.monthValue)
    }

    override suspend fun recordReading(id: Long, chaptersCount: Int, durationMs: Long) {
        val today = LocalDate.now().format(dateFormatter)
        val level = calculateActivityLevel(chaptersCount, ActivityType.READING)
        database.activityLogQueries.incrementChapters(
            date = today,
            level = level.toLong(),
            count = chaptersCount.toLong(),
            last_updated = System.currentTimeMillis(),
        )
        if (durationMs > 0) {
            database.activityLogQueries.addDuration(
                date = today,
                duration_ms = durationMs,
                last_updated = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun recordWatching(id: Long, episodesCount: Int, durationMs: Long) {
        val today = LocalDate.now().format(dateFormatter)
        val level = calculateActivityLevel(episodesCount, ActivityType.WATCHING)
        database.activityLogQueries.incrementEpisodes(
            date = today,
            level = level.toLong(),
            count = episodesCount.toLong(),
            last_updated = System.currentTimeMillis(),
        )
        if (durationMs > 0) {
            database.activityLogQueries.addDuration(
                date = today,
                duration_ms = durationMs,
                last_updated = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun recordAppSession(durationMs: Long) {
        val today = LocalDate.now().format(dateFormatter)
        database.activityLogQueries.addDuration(
            date = today,
            duration_ms = durationMs,
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun recordAppOpen() {
        val today = LocalDate.now().format(dateFormatter)
        database.activityLogQueries.incrementAppOpens(
            date = today,
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun recordAchievementUnlock() {
        val today = LocalDate.now().format(dateFormatter)
        database.activityLogQueries.incrementAchievements(
            date = today,
            last_updated = System.currentTimeMillis(),
        )
        logcat { "Recorded achievement unlock for $today" }
    }

    override suspend fun getLastTwelveMonthsStats(): List<Pair<YearMonth, MonthStats>> {
        val today = YearMonth.now()
        val stats = mutableListOf<Pair<YearMonth, MonthStats>>()

        // Last 12 months including current
        for (i in 11 downTo 0) {
            val month = today.minusMonths(i.toLong())
            stats.add(month to getMonthStats(month.year, month.monthValue))
        }
        return stats
    }

    // Backup/Restore support
    override suspend fun upsertActivityData(
        date: LocalDate,
        chaptersRead: Int,
        episodesWatched: Int,
        appOpens: Int,
        achievementsUnlocked: Int,
        durationMs: Long,
    ) {
        val dateStr = date.format(dateFormatter)
        val (type, level) = calculateActivityTypeAndLevel(
            chaptersRead = chaptersRead,
            episodesWatched = episodesWatched,
            appOpens = appOpens,
            achievementsUnlocked = achievementsUnlocked,
        )

        database.activityLogQueries.upsertActivity(
            date = dateStr,
            level = level.toLong(),
            type = type.ordinal.toLong(),
            chapters_read = chaptersRead.toLong(),
            episodes_watched = episodesWatched.toLong(),
            app_opens = appOpens.toLong(),
            achievements_unlocked = achievementsUnlocked.toLong(),
            duration_ms = durationMs,
            last_updated = System.currentTimeMillis(),
        )
    }

    override suspend fun deleteAllActivityData() {
        database.activityLogQueries.deleteAllActivityLog()
        logcat { "All activity data deleted" }
    }

    private fun calculateActivityTypeAndLevel(
        chaptersRead: Int,
        episodesWatched: Int,
        appOpens: Int,
        achievementsUnlocked: Int,
    ): Pair<ActivityType, Int> {
        return when {
            achievementsUnlocked > 0 -> ActivityType.READING to 4 // Highlighting achievements with max level
            episodesWatched > 0 -> ActivityType.WATCHING to calculateActivityLevel(episodesWatched, ActivityType.WATCHING)
            chaptersRead > 0 -> ActivityType.READING to calculateActivityLevel(chaptersRead, ActivityType.READING)
            appOpens > 0 -> ActivityType.APP_OPEN to 1
            else -> ActivityType.APP_OPEN to 0
        }
    }

    private fun calculateActivityLevel(count: Int, type: ActivityType): Int {
        return when (type) {
            ActivityType.READING -> when {
                count >= 20 -> 4
                count >= 10 -> 3
                count >= 5 -> 2
                else -> 1
            }
            ActivityType.WATCHING -> when {
                count >= 10 -> 4
                count >= 5 -> 3
                count >= 2 -> 2
                else -> 1
            }
            ActivityType.APP_OPEN -> 1
        }
    }
}
