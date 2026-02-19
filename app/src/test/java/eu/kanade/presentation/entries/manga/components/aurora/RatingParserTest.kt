package eu.kanade.presentation.entries.manga.components.aurora

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RatingParserTest {

    @Test
    fun `parseRating parses GroupLe modern star format`() {
        val description = "****+ 8.7 (votes: 456)\nDescription text"

        val parsed = RatingParser.parseRating(description)

        parsed.shouldNotBeNull()
        parsed.rating shouldBe 8.7f
        parsed.votes shouldBe 456
    }

    @Test
    fun `parseRating parses GroupLe legacy star format with bracket score`() {
        val description = "***** 9.8[9.4] (votes: 123)\nDescription text"

        val parsed = RatingParser.parseRating(description)

        parsed.shouldNotBeNull()
        parsed.rating shouldBe 9.8f
        parsed.votes shouldBe 123
    }

    @Test
    fun `parseRating keeps existing unicode star format support`() {
        val description = "★★★★★ 8.6 (1,234 votes)\nDescription text"

        val parsed = RatingParser.parseRating(description)

        parsed.shouldNotBeNull()
        parsed.rating shouldBe 8.6f
        parsed.votes shouldBe 1234
    }

    @Test
    fun `parseRating keeps existing rating slash ten format support`() {
        val description = "Reader score: 7.4/10\nDescription text"

        val parsed = RatingParser.parseRating(description)

        parsed.shouldNotBeNull()
        parsed.rating shouldBe 7.4f
    }
}
