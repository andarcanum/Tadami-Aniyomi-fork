package eu.kanade.domain.entries.novel.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelRatingHtmlParserTest {

    @Test
    fun `parses rating from ranobelib visible rating block`() {
        val html = """
            <html>
              <body>
                <div class="rating-info">
                  <span class="rating-info__value">9.27</span>
                  <span class="rating-info__votes">15</span>
                </div>
              </body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe 9.27f
    }

    @Test
    fun `parses rating from plain json ld aggregate rating`() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                  {"aggregateRating":{"ratingValue":"4.65"}}
                </script>
              </head>
              <body></body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe 4.65f
    }

    @Test
    fun `parses rating from hexnovels json ld and embedded average rating`() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                  {
                    "@context":"https://schema.org",
                    "@graph":[{
                      "@type":["WebPage","CreativeWorkSeries"],
                      "aggregateRating":{
                        "@type":"AggregateRating",
                        "worstRating":1,
                        "bestRating":10,
                        "ratingValue":9.85,
                        "ratingCount":7
                      }
                    }]
                  }
                </script>
                <script>
                  window.__ASTRO_STATE__ = {"averageRating":[0,9.857142]}
                </script>
              </head>
              <body></body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe 9.85f
    }

    @Test
    fun `parses rating from ranobes product aggregate rating and visible text`() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                  {
                    "@context":"https://schema.org",
                    "@type":"Product",
                    "aggregateRating":{
                      "@type":"AggregateRating",
                      "ratingValue":"4.2",
                      "bestRating":"5",
                      "worstRating":"1",
                      "ratingCount":"736"
                    }
                  }
                </script>
              </head>
              <body>
                <div class="novel-rating">4.2 / 5 (736 votes)</div>
              </body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe 8.4f
    }

    @Test
    fun `chooses the supported rulate meta candidate over distractors`() {
        val html = """
            <html>
              <head>
                <meta itemprop="ratingValue" content="5" />
                <meta itemprop="ratingValue" content="4.5" />
                <meta itemprop="ratingCount" content="736" />
                <meta itemprop="bestRating" content="5" />
                <meta itemprop="worstRating" content="1" />
                <meta itemprop="ratingValue" content="4.7" />
              </head>
              <body>
                <div class="book-rating" data-tippy-content="Рейтинг новеллы">4.5 / 5</div>
              </body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe 9.0f
    }

    @Test
    fun `parses rating from tooltip label and inline star value`() {
        val html = """
            <html>
              <body>
                <div class="ui label icon basic tiny __tooltip" data-tippy-content="Рейтинг">
                  <i class="icon star"></i> 8.74
                </div>
              </body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe 8.74f
    }

    @Test
    fun `parses rating from freewebnovel scale text`() {
        val html = """
            <html>
              <body>
                <div class="rating-widget">Your Rating? 4.3 / 5 (54 votes)</div>
              </body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe 8.6f
    }

    @Test
    fun `parses rating from five point text with scale words`() {
        val html = """
            <html>
              <body>
                <div class="rating">Оценка 4.65 из 5</div>
              </body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe 9.3f
    }

    @Test
    fun `returns null for large chapter-like counts`() {
        val html = """
            <html>
              <body>
                <div class="rating">Рейтинг 40000</div>
              </body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe null
    }

    @Test
    fun `returns null when there are no ratings`() {
        val html = """
            <html>
              <body>
                <div class="rating">Рейтинг -- Нет оценок</div>
              </body>
            </html>
        """.trimIndent()

        NovelRatingHtmlParser.parse(html) shouldBe null
    }
}
