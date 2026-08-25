package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import tachiyomi.domain.book.novel.repository.NovelHighlightRepository

class QuoteRuleTest {

    private lateinit var context: RuleContext
    private lateinit var repository: NovelHighlightRepository

    @BeforeEach
    fun setup() {
        context = mockk()
        repository = mockk()
    }

    @Test
    fun `evaluateFull returns current highlight count`() = runTest {
        coEvery { repository.countAll() } returns 4

        QuoteRule(repository).evaluateFull(context) shouldBe 4
    }

    @Test
    fun `evaluateDelta updates on quote saved and ignores other events`() = runTest {
        coEvery { repository.countAll() } returns 5
        val rule = QuoteRule(repository)

        val saved = AchievementEvent.FeatureUsed(AchievementEvent.Feature.QUOTE_SAVED)
        rule.evaluateDelta(saved, 0, context) shouldBe RuleResult.Update(5)

        val other = AchievementEvent.FeatureUsed(AchievementEvent.Feature.SEARCH)
        rule.evaluateDelta(other, 0, context) shouldBe RuleResult.NoChange

        val unrelated = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)
        rule.evaluateDelta(unrelated, 0, context) shouldBe RuleResult.NoChange
    }
}
