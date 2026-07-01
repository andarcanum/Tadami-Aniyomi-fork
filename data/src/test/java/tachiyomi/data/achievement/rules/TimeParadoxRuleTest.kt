package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import java.time.LocalDateTime
import java.time.ZoneId

class TimeParadoxRuleTest {

    private lateinit var context: RuleContext

    @BeforeEach
    fun setup() {
        context = mockk()
    }

    private fun getTimestampForTime(hour: Int, minute: Int): Long {
        return LocalDateTime.of(2026, 6, 29, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    @Test
    fun `TimeParadoxRule triggers at exactly 11 11 AM`() = runTest {
        val rule = TimeParadoxRule()
        val timestamp = getTimestampForTime(11, 11)

        val event1 = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1, timestamp = timestamp)
        rule.evaluateDelta(event1, 0, context) shouldBe RuleResult.Update(1)

        val event2 = AchievementEvent.NovelChapterRead(novelId = 1L, chapterNumber = 1, timestamp = timestamp)
        rule.evaluateDelta(event2, 0, context) shouldBe RuleResult.Update(1)

        val event3 = AchievementEvent.EpisodeWatched(animeId = 1L, episodeNumber = 1, timestamp = timestamp)
        rule.evaluateDelta(event3, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `TimeParadoxRule triggers at exactly 11 11 PM`() = runTest {
        val rule = TimeParadoxRule()
        val timestamp = getTimestampForTime(23, 11)

        val event1 = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1, timestamp = timestamp)
        rule.evaluateDelta(event1, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `TimeParadoxRule does not trigger at other times`() = runTest {
        val rule = TimeParadoxRule()

        // 11:10 AM
        val t1 = getTimestampForTime(11, 10)
        val event1 = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1, timestamp = t1)
        rule.evaluateDelta(event1, 0, context) shouldBe RuleResult.NoChange

        // 11:12 AM
        val t2 = getTimestampForTime(11, 12)
        val event2 = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1, timestamp = t2)
        rule.evaluateDelta(event2, 0, context) shouldBe RuleResult.NoChange

        // 12:11 PM
        val t3 = getTimestampForTime(12, 11)
        val event3 = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1, timestamp = t3)
        rule.evaluateDelta(event3, 0, context) shouldBe RuleResult.NoChange

        // 10:11 AM
        val t4 = getTimestampForTime(10, 11)
        val event4 = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1, timestamp = t4)
        rule.evaluateDelta(event4, 0, context) shouldBe RuleResult.NoChange
    }

    @Test
    fun `TimeParadoxRule does not trigger for other event types`() = runTest {
        val rule = TimeParadoxRule()
        val timestamp = getTimestampForTime(11, 11)

        val event = AchievementEvent.AppStart(hourOfDay = 11, timestamp = timestamp)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.NoChange
    }

    @Test
    fun `TimeParadoxRule evaluateFull returns 0`() = runTest {
        val rule = TimeParadoxRule()
        rule.evaluateFull(context) shouldBe 0
    }
}
