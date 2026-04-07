package eu.kanade.domain.entries.anime.model

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeRatingHtmlParserTest {

    @Test
    fun `parses animego style visible rating block`() {
        val html = """
            <html>
              <body>
                <div class="page__ratings">
                  <div class="page__rating-item page__rating-item--critics">
                    <span>Shikimori</span>
                    <span>8.1</span>
                  </div>
                  <div class="page__rating-item page__rating-item--imdb">
                    <span>IMDb</span>
                    <span>8.2</span>
                  </div>
                  <div class="page__rating-item page__rating-item--audience">
                    <span>AnimeGO</span>
                    <span>8.4</span>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        AnimeRatingHtmlParser.parse(html) shouldBe 8.1f
    }

    @Test
    fun `parses json ld aggregate rating`() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                  {
                    "@context": "https://schema.org",
                    "@type": "TVSeries",
                    "aggregateRating": {
                      "@type": "AggregateRating",
                      "ratingValue": 8.4,
                      "bestRating": 10,
                      "ratingCount": 1234
                    }
                  }
                </script>
              </head>
            </html>
        """.trimIndent()

        AnimeRatingHtmlParser.parse(html) shouldBe 8.4f
    }

    @Test
    fun `parses meta itemprop rating value`() {
        val html = """
            <html>
              <head>
                <meta itemprop="ratingValue" content="8.6" />
              </head>
            </html>
        """.trimIndent()

        AnimeRatingHtmlParser.parse(html) shouldBe 8.6f
    }

    @Test
    fun `parses explicit five point rating and normalizes to ten point scale`() {
        val html = """
            <html>
              <body>
                <div class="rating" title="rating">
                  4.65 / 5
                </div>
              </body>
            </html>
        """.trimIndent()

        AnimeRatingHtmlParser.parse(html) shouldBe 9.3f
    }

    @Test
    fun `parses textual rating fallback`() {
        val html = """
            <html>
              <body>
                <div class="info">
                  Рейтинг 8.7
                </div>
              </body>
            </html>
        """.trimIndent()

        AnimeRatingHtmlParser.parse(html) shouldBe 8.7f
    }

    @Test
    fun `rejects obvious non rating counters`() {
        val html = """
            <html>
              <body>
                <div class="page__stats">
                  Просмотров 11 510 162
                </div>
              </body>
            </html>
        """.trimIndent()

        AnimeRatingHtmlParser.parse(html).shouldBeNull()
    }

    @Test
    fun `rejects no rating sentinels`() {
        val html = """
            <html>
              <body>
                <div>Нет оценок</div>
              </body>
            </html>
        """.trimIndent()

        AnimeRatingHtmlParser.parse(html).shouldBeNull()
    }
}
