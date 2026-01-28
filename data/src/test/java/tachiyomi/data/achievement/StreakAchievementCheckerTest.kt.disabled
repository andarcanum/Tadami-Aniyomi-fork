package tachiyomi.data.achievement

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker

@Execution(ExecutionMode.CONCURRENT)
class StreakAchievementCheckerTest : AchievementTestBase() {

    private lateinit var streakChecker: StreakAchievementChecker
    private val millisInDay = 24 * 60 * 60 * 1000L

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
        val today = (System.currentTimeMillis() / millisInDay) * millisInDay

        // Log activity for today and past 2 days
        repeat(3) { dayOffset ->
            val date = today - (dayOffset * millisInDay)
            database.achievementActivityLogQueries.upsertActivityLog(
                date = date,
                chapter_count = 1,
                episode_count = 0,
                last_updated = System.currentTimeMillis(),
            )
        }

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 3
    }

    @Test
    fun `streak breaks on missing day`() = runTest {
        val today = (System.currentTimeMillis() / millisInDay) * millisInDay

        // Log activity for today and 2 days ago (skipping yesterday)
        database.achievementActivityLogQueries.upsertActivityLog(
            date = today,
            chapter_count = 1,
            episode_count = 0,
            last_updated = System.currentTimeMillis(),
        )

        database.achievementActivityLogQueries.upsertActivityLog(
            date = today - (2 * millisInDay),
            chapter_count = 1,
            episode_count = 0,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 1 // Only today counts
    }

    @Test
    fun `streak continues even without activity today yet`() = runTest {
        val today = (System.currentTimeMillis() / millisInDay) * millisInDay

        // Log activity for yesterday and day before
        database.achievementActivityLogQueries.upsertActivityLog(
            date = today - millisInDay,
            chapter_count = 1,
            episode_count = 0,
            last_updated = System.currentTimeMillis(),
        )

        database.achievementActivityLogQueries.upsertActivityLog(
            date = today - (2 * millisInDay),
            chapter_count = 1,
            episode_count = 0,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 2 // Yesterday and day before (today doesn't break streak)
    }

    @Test
    fun `logging chapter read increments count`() = runTest {
        val today = (System.currentTimeMillis() / millisInDay) * millisInDay

        streakChecker.logChapterRead()

        val activity = database.achievementActivityLogQueries.getActivityForDate(
            date = today,
            mapper = { date, chapterCount, episodeCount, lastUpdated ->
                Triple(date, chapterCount, episodeCount)
            },
        ).executeAsOneOrNull()

        activity shouldNotBe null
        activity!!.second shouldBe 1L // chapter_count
        activity.third shouldBe 0L // episode_count
    }

    @Test
    fun `logging episode watched increments count`() = runTest {
        val today = (System.currentTimeMillis() / millisInDay) * millisInDay

        streakChecker.logEpisodeWatched()

        val activity = database.achievementActivityLogQueries.getActivityForDate(
            date = today,
            mapper = { date, chapterCount, episodeCount, lastUpdated ->
                Triple(date, chapterCount, episodeCount)
            },
        ).executeAsOneOrNull()

        activity shouldNotBe null
        activity!!.second shouldBe 0L // chapter_count
        activity.third shouldBe 1L // episode_count
    }

    @Test
    fun `multiple chapter reads in same day update existing log`() = runTest {
        streakChecker.logChapterRead()
        streakChecker.logChapterRead()
        streakChecker.logChapterRead()

        val today = (System.currentTimeMillis() / millisInDay) * millisInDay

        val activity = database.achievementActivityLogQueries.getActivityForDate(
            date = today,
            mapper = { date, chapterCount, episodeCount, lastUpdated ->
                Triple(date, chapterCount, episodeCount)
            },
        ).executeAsOneOrNull()

        activity shouldNotBe null
        // The upsert operation updates the existing record
        activity!!.second shouldBe 1L
    }

    @Test
    fun `mixed chapter and episode activity counts towards streak`() = runTest {
        val today = (System.currentTimeMillis() / millisInDay) * millisInDay

        // Log both chapter and episode activity
        database.achievementActivityLogQueries.upsertActivityLog(
            date = today,
            chapter_count = 5,
            episode_count = 3,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 1
    }

    @Test
    fun `streak resets after gap`() = runTest {
        val today = (System.currentTimeMillis() / millisInDay) * millisInDay

        // Create a 5-day streak
        repeat(5) { dayOffset ->
            database.achievementActivityLogQueries.upsertActivityLog(
                date = today - (dayOffset * millisInDay),
                chapter_count = 1,
                episode_count = 0,
                last_updated = System.currentTimeMillis(),
            )
        }

        val initialStreak = streakChecker.getCurrentStreak()
        initialStreak shouldBe 5

        // Now add a gap by removing activity from yesterday
        database.achievementActivityLogQueries.deleteActivityLog(today - millisInDay)

        val newStreak = streakChecker.getCurrentStreak()
        newStreak shouldBe 1 // Only today counts now (yesterday was removed, streak broken)
    }

    @Test
    fun `long streak is calculated correctly`() = runTest {
        val today = (System.currentTimeMillis() / millisInDay) * millisInDay

        // Create a 30-day streak
        repeat(30) { dayOffset ->
            database.achievementActivityLogQueries.upsertActivityLog(
                date = today - (dayOffset * millisInDay),
                chapter_count = 1,
                episode_count = 0,
                last_updated = System.currentTimeMillis(),
            )
        }

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 30
    }
}
