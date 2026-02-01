package tachiyomi.data.achievement

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Execution(ExecutionMode.CONCURRENT)
class StreakAchievementCheckerTest : AchievementTestBase() {

    private lateinit var streakChecker: StreakAchievementChecker
    private val millisInDay = 24 * 60 * 60 * 1000L

    @org.junit.jupiter.api.BeforeEach
    override fun setup() {
        super.setup()
        streakChecker = StreakAchievementChecker(database)
    }

    @Test
    fun `initial streak is zero`() = runTest {
        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 0
    }

    @Test
    fun `streak is one after logging activity today`() = runTest {
        streakChecker.logChapterRead()

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 1
    }

    @Test
    fun `streak counts consecutive days`() = runTest {
        val today = LocalDate.now()

        // Log activity for today and past 2 days
        repeat(3) { dayOffset ->
            val date = today.minusDays(dayOffset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            database.activityLogQueries.incrementChapters(
                date = date,
                level = 1,
                count = 1,
                last_updated = System.currentTimeMillis(),
            )
        }

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 3
    }

    @Test
    fun `streak breaks on missing day`() = runTest {
        val today = LocalDate.now()

        // Log activity for today and 2 days ago (skipping yesterday)
        database.activityLogQueries.incrementChapters(
            date = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
            level = 1,
            count = 1,
            last_updated = System.currentTimeMillis(),
        )

        database.activityLogQueries.incrementChapters(
            date = today.minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
            level = 1,
            count = 1,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 1 // Only today counts
    }

    @Test
    fun `streak continues even without activity today yet`() = runTest {
        val today = LocalDate.now()

        // Log activity for yesterday and day before
        database.activityLogQueries.incrementChapters(
            date = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
            level = 1,
            count = 1,
            last_updated = System.currentTimeMillis(),
        )

        database.activityLogQueries.incrementChapters(
            date = today.minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
            level = 1,
            count = 1,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 2 // Yesterday and day before (today doesn't break streak)
    }

    @Test
    fun `logging chapter read increments count`() = runTest {
        streakChecker.logChapterRead()

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val activity = database.activityLogQueries.getActivityForDate(date = today).executeAsOneOrNull()

        activity shouldNotBe null
        activity!!.chapters_read shouldBe 1L
        activity.episodes_watched shouldBe 0L
    }

    @Test
    fun `logging episode watched increments count`() = runTest {
        streakChecker.logEpisodeWatched()

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val activity = database.activityLogQueries.getActivityForDate(date = today).executeAsOneOrNull()

        activity shouldNotBe null
        activity!!.chapters_read shouldBe 0L
        activity.episodes_watched shouldBe 1L
    }

    @Test
    fun `multiple chapter reads in same day update existing log`() = runTest {
        streakChecker.logChapterRead()
        streakChecker.logChapterRead()
        streakChecker.logChapterRead()

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val activity = database.activityLogQueries.getActivityForDate(date = today).executeAsOneOrNull()

        activity shouldNotBe null
        // The increment operation updates the existing record
        activity!!.chapters_read shouldBe 3L
    }

    @Test
    fun `mixed chapter and episode activity counts towards streak`() = runTest {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Log both chapter and episode activity
        database.activityLogQueries.incrementChapters(
            date = today,
            level = 1,
            count = 5,
            last_updated = System.currentTimeMillis(),
        )
        database.activityLogQueries.incrementEpisodes(
            date = today,
            level = 1,
            count = 3,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 1
    }

    @Test
    fun `streak resets after gap`() = runTest {
        val today = LocalDate.now()

        // Create a 5-day streak
        repeat(5) { dayOffset ->
            database.activityLogQueries.incrementChapters(
                date = today.minusDays(dayOffset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE),
                level = 1,
                count = 1,
                last_updated = System.currentTimeMillis(),
            )
        }

        val initialStreak = streakChecker.getCurrentStreak()
        initialStreak shouldBe 5

        // Now add a gap by removing activity from yesterday
        database.activityLogQueries.deleteActivityLog(
            today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
        )

        val newStreak = streakChecker.getCurrentStreak()
        newStreak shouldBe 1 // Only today counts now (yesterday was removed, streak broken)
    }

    @Test
    fun `long streak is calculated correctly`() = runTest {
        val today = LocalDate.now()

        // Create a 30-day streak
        repeat(30) { dayOffset ->
            database.activityLogQueries.incrementChapters(
                date = today.minusDays(dayOffset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE),
                level = 1,
                count = 1,
                last_updated = System.currentTimeMillis(),
            )
        }

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 30
    }
}
