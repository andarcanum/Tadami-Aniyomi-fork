package eu.kanade.domain.entries.novel.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelRatingJsonParserTest {

    @Test
    fun `parses ranobelib rating from stats distribution json`() {
        val json = """
            {
              "data": {
                "rating": {
                  "count": 7051,
                  "stats": [
                    {"label": 10, "value": 6490},
                    {"label": 9, "value": 147},
                    {"label": 8, "value": 145},
                    {"label": 7, "value": 64},
                    {"label": 6, "value": 67},
                    {"label": 5, "value": 27},
                    {"label": 4, "value": 28},
                    {"label": 3, "value": 15},
                    {"label": 2, "value": 6},
                    {"label": 1, "value": 62}
                  ]
                }
              }
            }
        """.trimIndent()

        NovelRatingJsonParser.parseRanobeLibStats(json) shouldBe 9.73f
    }
}
