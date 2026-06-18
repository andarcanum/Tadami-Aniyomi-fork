package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubFilteredContentTest {

    @Test
    fun `resolveHomeHubFilteredContent returns original lists when query is empty`() {
        val hero = HomeHubHero(entryId = 1L, title = "Hero", progressNumber = 1.0, coverData = null)
        val history = listOf(history(2L, "History"))
        val recommendations = listOf(recommendation(3L, "Recommendation"))

        val result = resolveHomeHubFilteredContent(
            hero = hero,
            history = history,
            recommendations = recommendations,
            query = "",
        )

        result.hero shouldBe hero
        result.history shouldBe history
        result.recommendations shouldBe recommendations
        result.isFiltering shouldBe false
    }

    @Test
    fun `resolveHomeHubFilteredContent filters hero history and recommendations by title`() {
        val result = resolveHomeHubFilteredContent(
            hero = HomeHubHero(entryId = 1L, title = "Alpha", progressNumber = 1.0, coverData = null),
            history = listOf(
                history(2L, "Beta Match"),
                history(3L, "Gamma"),
            ),
            recommendations = listOf(
                recommendation(4L, "MATCH Delta"),
                recommendation(5L, "Epsilon"),
            ),
            query = "match",
        )

        result.hero shouldBe null
        result.history.map { it.entryId } shouldBe listOf(2L)
        result.recommendations.map { it.entryId } shouldBe listOf(4L)
        result.isFiltering shouldBe true
    }

    private fun history(entryId: Long, title: String): HomeHubHistory {
        return HomeHubHistory(
            entryId = entryId,
            title = title,
            progressNumber = 1.0,
            coverData = null,
            section = HomeHubSection.Anime,
        )
    }

    private fun recommendation(entryId: Long, title: String): HomeHubRecommendation {
        return HomeHubRecommendation(
            entryId = entryId,
            title = title,
            coverData = null,
        )
    }
}
