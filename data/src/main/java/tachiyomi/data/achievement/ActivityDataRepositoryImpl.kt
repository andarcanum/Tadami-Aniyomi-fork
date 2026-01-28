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

        for (i in 0 until days) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(dateFormatter)

            // Check for reading activity
            val chaptersRead = prefs.getInt(KEY_CHAPTERS_PREFIX + dateStr, 0)
            if (chaptersRead > 0) {
                activities.add(
                    DayActivity(
                        date = date,
                        level = calculateActivityLevel(chaptersRead, ActivityType.READING),
                        type = ActivityType.READING,
                    ),
                )
            }

            // Check for watching activity
            val episodesWatched = prefs.getInt(KEY_EPISODES_PREFIX + dateStr, 0)
            if (episodesWatched > 0) {
                activities.add(
                    DayActivity(
                        date = date,
                        level = calculateActivityLevel(episodesWatched, ActivityType.WATCHING),
                        type = ActivityType.WATCHING,
                    ),
                )
            }

            // Check for app opens
            val appOpens = prefs.getInt(KEY_APP_OPENS_PREFIX + dateStr, 0)
            if (appOpens > 0 && chaptersRead == 0 && episodesWatched == 0) {
                activities.add(
                    DayActivity(
                        date = date,
                        level = 1,
                        type = ActivityType.APP_OPEN,
                    ),
                )
            }
        }

        emit(activities.sortedByDescending { it.date })
    }.flowOn(ioContext)

    override suspend fun getMonthStats(year: Int, month: Int): MonthStats {
        val yearMonth = YearMonth.of(year, month)
        val daysInMonth = yearMonth.lengthOfMonth()

        var chaptersRead = 0
        var episodesWatched = 0
        var appOpens = 0
        var achievementsUnlocked = 0

        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)
            val dateStr = date.format(dateFormatter)

            chaptersRead += prefs.getInt(KEY_CHAPTERS_PREFIX + dateStr, 0)
            episodesWatched += prefs.getInt(KEY_EPISODES_PREFIX + dateStr, 0)
            appOpens += prefs.getInt(KEY_APP_OPENS_PREFIX + dateStr, 0)
            achievementsUnlocked += prefs.getInt(KEY_ACHIEVEMENTS_PREFIX + dateStr, 0)
        }

        // Estimate time in app (rough calculation: 5 min per chapter, 20 min per episode)
        val timeInAppMinutes = chaptersRead * 5 + episodesWatched * 20

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

    override suspend fun recordReading(chaptersCount: Int) {
        val today = LocalDate.now().format(dateFormatter)
        prefs.edit {
            val current = prefs.getInt(KEY_CHAPTERS_PREFIX + today, 0)
            putInt(KEY_CHAPTERS_PREFIX + today, current + chaptersCount)
        }
        logcat { "Recorded $chaptersCount chapters read" }
    }

    override suspend fun recordWatching(episodesCount: Int) {
        val today = LocalDate.now().format(dateFormatter)
        prefs.edit {
            val current = prefs.getInt(KEY_EPISODES_PREFIX + today, 0)
            putInt(KEY_EPISODES_PREFIX + today, current + episodesCount)
        }
        logcat { "Recorded $episodesCount episodes watched" }
    }

    override suspend fun recordAppOpen() {
        val today = LocalDate.now().format(dateFormatter)
        prefs.edit {
            val current = prefs.getInt(KEY_APP_OPENS_PREFIX + today, 0)
            putInt(KEY_APP_OPENS_PREFIX + today, current + 1)
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

    companion object {
        private const val PREFS_NAME = "activity_data"
        private const val KEY_CHAPTERS_PREFIX = "chapters_"
        private const val KEY_EPISODES_PREFIX = "episodes_"
        private const val KEY_APP_OPENS_PREFIX = "app_opens_"
        private const val KEY_ACHIEVEMENTS_PREFIX = "achievements_"
    }
}
