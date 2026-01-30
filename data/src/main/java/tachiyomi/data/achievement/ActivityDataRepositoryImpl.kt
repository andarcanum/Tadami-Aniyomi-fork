package tachiyomi.data.achievement

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.achievement.model.ActivityType
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.coroutines.CoroutineContext

class ActivityDataRepositoryImpl(
    private val context: Context,
    private val ioContext: CoroutineContext,
) : ActivityDataRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun getActivityData(days: Int): Flow<List<DayActivity>> = flow {
        val activities = mutableListOf<DayActivity>()
        val today = LocalDate.now()
        // Start from 'days' ago to include today
        val startDate = today.minusDays((days - 1).toLong())

        var currentDate = startDate
        while (!currentDate.isAfter(today)) {
            val dateStr = currentDate.format(dateFormatter)

            val chaptersRead = prefs.getInt(KEY_CHAPTERS_PREFIX + dateStr, 0)
            val episodesWatched = prefs.getInt(KEY_EPISODES_PREFIX + dateStr, 0)
            val appOpens = prefs.getInt(KEY_APP_OPENS_PREFIX + dateStr, 0)
            val achievementsUnlocked = prefs.getInt(KEY_ACHIEVEMENTS_PREFIX + dateStr, 0)

            // Determine activity type and level
            val (type, level) = when {
                achievementsUnlocked > 0 -> ActivityType.READING to 4 // Highlighting achievements with max level
                episodesWatched > 0 -> ActivityType.WATCHING to calculateActivityLevel(episodesWatched, ActivityType.WATCHING)
                chaptersRead > 0 -> ActivityType.READING to calculateActivityLevel(chaptersRead, ActivityType.READING)
                appOpens > 0 -> ActivityType.APP_OPEN to 1
                else -> ActivityType.APP_OPEN to 0
            }

            activities.add(DayActivity(currentDate, level, type))
            currentDate = currentDate.plusDays(1)
        }

        emit(activities) // Ascending order: Oldest -> Newest
    }.flowOn(ioContext)

    override suspend fun getMonthStats(year: Int, month: Int): MonthStats {
        val yearMonth = YearMonth.of(year, month)
        val daysInMonth = yearMonth.lengthOfMonth()

        var chaptersRead = 0
        var episodesWatched = 0
        var achievementsUnlocked = 0
        var totalDurationMs = 0L

        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)
            val dateStr = date.format(dateFormatter)

            chaptersRead += prefs.getInt(KEY_CHAPTERS_PREFIX + dateStr, 0)
            episodesWatched += prefs.getInt(KEY_EPISODES_PREFIX + dateStr, 0)
            achievementsUnlocked += prefs.getInt(KEY_ACHIEVEMENTS_PREFIX + dateStr, 0)
            totalDurationMs += prefs.getLong(KEY_DURATION_PREFIX + dateStr, 0L)
        }

        // Convert ms to minutes
        val timeInAppMinutes = (totalDurationMs / 60000).toInt()

        return MonthStats(
            chaptersRead = chaptersRead,
            episodesWatched = episodesWatched,
            timeInAppMinutes = timeInAppMinutes,
            achievementsUnlocked = achievementsUnlocked,
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
        val keySet = "read_ids_$today"
        val existingIds = prefs.getStringSet(keySet, emptySet()) ?: emptySet()
        
        prefs.edit {
            if (!existingIds.contains(id.toString())) {
                val currentCount = prefs.getInt(KEY_CHAPTERS_PREFIX + today, 0)
                putInt(KEY_CHAPTERS_PREFIX + today, currentCount + chaptersCount)
                putStringSet(keySet, existingIds + id.toString())
            }
            if (durationMs > 0) {
                val currentDuration = prefs.getLong(KEY_DURATION_PREFIX + today, 0L)
                putLong(KEY_DURATION_PREFIX + today, currentDuration + durationMs)
            }
        }
    }

    override suspend fun recordWatching(id: Long, episodesCount: Int, durationMs: Long) {
        val today = LocalDate.now().format(dateFormatter)
        val keySet = "watch_ids_$today"
        val existingIds = prefs.getStringSet(keySet, emptySet()) ?: emptySet()

        prefs.edit {
            if (!existingIds.contains(id.toString())) {
                val currentCount = prefs.getInt(KEY_EPISODES_PREFIX + today, 0)
                putInt(KEY_EPISODES_PREFIX + today, currentCount + episodesCount)
                putStringSet(keySet, existingIds + id.toString())
            }
            if (durationMs > 0) {
                val currentDuration = prefs.getLong(KEY_DURATION_PREFIX + today, 0L)
                putLong(KEY_DURATION_PREFIX + today, currentDuration + durationMs)
            }
        }
    }

    override suspend fun recordAppOpen() {
        val today = LocalDate.now().format(dateFormatter)
        prefs.edit {
            val current = prefs.getInt(KEY_APP_OPENS_PREFIX + today, 0)
            putInt(KEY_APP_OPENS_PREFIX + today, current + 1)
        }
    }

    override suspend fun recordAchievementUnlock() {
        val today = LocalDate.now().format(dateFormatter)
        prefs.edit {
            val current = prefs.getInt(KEY_ACHIEVEMENTS_PREFIX + today, 0)
            putInt(KEY_ACHIEVEMENTS_PREFIX + today, current + 1)
        }
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

    companion object {
        private const val PREFS_NAME = "activity_data"
        private const val KEY_CHAPTERS_PREFIX = "chapters_"
        private const val KEY_EPISODES_PREFIX = "episodes_"
        private const val KEY_APP_OPENS_PREFIX = "app_opens_"
        private const val KEY_ACHIEVEMENTS_PREFIX = "achievements_"
        private const val KEY_DURATION_PREFIX = "duration_"
    }
}