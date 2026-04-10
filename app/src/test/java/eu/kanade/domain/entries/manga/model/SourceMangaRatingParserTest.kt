package eu.kanade.domain.entries.manga.model

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SourceMangaRatingParserTest {

    @Test
    fun `parse extracts english keyword rating`() {
        SourceMangaRatingParser.parse("Rating: 8.7 (1,024 votes)") shouldBe 8.7f
    }

    @Test
    fun `parse extracts russian keyword rating`() {
        SourceMangaRatingParser.parse("Рейтинг: 8.3 (голосов: 1 024)") shouldBe 8.3f
    }

    @Test
    fun `parse extracts chinese keyword rating`() {
        SourceMangaRatingParser.parse("评分：8.7") shouldBe 8.7f
    }

    @Test
    fun `parse extracts portuguese keyword rating`() {
        SourceMangaRatingParser.parse("Avaliação: 8,3") shouldBe 8.3f
    }

    @Test
    fun `parse extracts out-of-10 format`() {
        SourceMangaRatingParser.parse("Score 7.9/10") shouldBe 7.9f
    }

    @Test
    fun `parse extracts kotatsu style star summary`() {
        SourceMangaRatingParser.parse("***** 8.5[8.7] (votes: 123)") shouldBe 8.5f
    }

    @Test
    fun `parse extracts kotatsu style modern star summary`() {
        SourceMangaRatingParser.parse("★★★★ 9.2 (votes: 34)") shouldBe 9.2f
    }

    @Test
    fun `parse ignores chapter fractions`() {
        SourceMangaRatingParser.parse("Progress: 12/120 chapters").shouldBeNull()
    }

    @Test
    fun `parse ignores year and unrelated numbers`() {
        SourceMangaRatingParser.parse("Published in 2024 with 125 chapters").shouldBeNull()
    }

    @Test
    fun `resolveIncomingSourceRating uses parser when source rating is unknown`() {
        resolveIncomingSourceRating(
            rawRating = -1f,
            description = "Rating: 8.6",
        ) shouldBe 8.6f
    }

    @Test
    fun `resolveIncomingSourceRating treats zero source rating as unknown`() {
        resolveIncomingSourceRating(
            rawRating = 0f,
            description = "Рейтинг: 8.4",
        ) shouldBe 8.4f
    }

    @Test
    fun `resolveIncomingSourceRating keeps explicit source rating when present`() {
        resolveIncomingSourceRating(
            rawRating = 7.2f,
            description = "Rating: 8.9",
        ) shouldBe 7.2f
    }
}
