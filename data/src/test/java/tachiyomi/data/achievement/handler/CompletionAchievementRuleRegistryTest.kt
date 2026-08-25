package tachiyomi.data.achievement.handler

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.rules.CompletionCountRule
import tachiyomi.data.achievement.rules.QuantityRule
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

/**
 * Guards the wiring of completion-style achievement ids to
 * [CompletionCountRule] so they count completed titles rather than
 * chapters or episodes.
 */
class CompletionAchievementRuleRegistryTest {

    private val registry = AchievementRuleRegistry(
        mangaRepository = mockk<MangaRepository>(relaxed = true),
        animeRepository = mockk<AnimeRepository>(relaxed = true),
        novelRepository = mockk<NovelRepository>(relaxed = true),
        novelHighlightRepository = mockk<tachiyomi.domain.book.novel.repository.NovelHighlightRepository>(
            relaxed = true,
        ),
    )

    @Test
    fun `complete 10 manga uses completion count rule`() {
        val rule = registry.getRule("complete_10_manga")
        rule.shouldBeInstanceOf<CompletionCountRule>()
        rule!!.achievementId shouldBe "complete_10_manga"
    }

    @Test
    fun `complete 50 manga uses completion count rule`() {
        val rule = registry.getRule("complete_50_manga")
        rule.shouldBeInstanceOf<CompletionCountRule>()
        rule!!.achievementId shouldBe "complete_50_manga"
    }

    @Test
    fun `complete 10 novel uses completion count rule`() {
        val rule = registry.getRule("complete_10_novel")
        rule.shouldBeInstanceOf<CompletionCountRule>()
        rule!!.achievementId shouldBe "complete_10_novel"
    }

    @Test
    fun `complete 50 novel uses completion count rule`() {
        val rule = registry.getRule("complete_50_novel")
        rule.shouldBeInstanceOf<CompletionCountRule>()
        rule!!.achievementId shouldBe "complete_50_novel"
    }

    @Test
    fun `complete 10 anime uses completion count rule`() {
        val rule = registry.getRule("complete_10_anime")
        rule.shouldBeInstanceOf<CompletionCountRule>()
        rule!!.achievementId shouldBe "complete_10_anime"
    }

    @Test
    fun `complete 50 anime uses completion count rule`() {
        val rule = registry.getRule("complete_50_anime")
        rule.shouldBeInstanceOf<CompletionCountRule>()
        rule!!.achievementId shouldBe "complete_50_anime"
    }

    @Test
    fun `read 10 chapters stays on quantity rule`() {
        val rule = registry.getRule("read_10_chapters")
        rule.shouldBeInstanceOf<QuantityRule>()
        rule!!.achievementId shouldBe "read_10_chapters"
    }
}
