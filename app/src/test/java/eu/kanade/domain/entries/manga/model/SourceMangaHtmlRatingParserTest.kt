package eu.kanade.domain.entries.manga.model

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SourceMangaHtmlRatingParserTest {

    @Test
    fun `parse extracts legacy groupLe score`() {
        val html = """
            <html>
              <body>
                <span class="rating-block" data-score="4.65"></span>
              </body>
            </html>
        """.trimIndent()

        SourceMangaHtmlRatingParser.parse("ReadManga", "https://readmanga.io", html = html) shouldBe 9.3f
    }

    @Test
    fun `parse extracts modern groupLe score`() {
        val html = """
            <html>
              <body>
                <div class="cr-hero-rating__main">
                  <span class="cr-hero-rating__value">9.3</span>
                </div>
              </body>
            </html>
        """.trimIndent()

        SourceMangaHtmlRatingParser.parse("ReadManga", "https://readmanga.io", html = html) shouldBe 9.3f
    }

    @Test
    fun `parse extracts inkstory rating from json ld aggregate rating`() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                  {
                    "@context": "https://schema.org",
                    "@graph": [
                      {
                        "@type": ["WebPage", "CreativeWorkSeries"],
                        "aggregateRating": {
                          "@type": "AggregateRating",
                          "ratingValue": 9.71,
                          "ratingCount": 958,
                          "bestRating": 10,
                          "worstRating": 1
                        }
                      }
                    ]
                  }
                </script>
              </head>
            </html>
        """.trimIndent()

        SourceMangaHtmlRatingParser.parse("InkStory", "https://inkstory.net", html = html) shouldBe 9.71f
    }

    @Test
    fun `parse extracts inkstory rating from visible text`() {
        val html = """
            <html>
              <body>
                <div>Рейтинг</div>
                <div>9.71 958 оценок</div>
              </body>
            </html>
        """.trimIndent()

        SourceMangaHtmlRatingParser.parse("InkStory", "https://inkstory.net", html = html) shouldBe 9.71f
    }

    @Test
    fun `parse extracts madara rating from rating summary text`() {
        val html = """
            <html>
              <body>
                <h1>One Year Old Queen & The Eternity Empire</h1>
                <div>4.3</div>
                <div>Your Rating</div>
                <div>Rating</div>
                <div>One Year Old Queen & The Eternity Empire Average  4.3 / 5 out of 191</div>
              </body>
            </html>
        """.trimIndent()

        SourceMangaHtmlRatingParser.parse(
            "Manga Kiss",
            "https://mangakiss.org",
            sourceClassName = "Madara",
            html = html,
        ) shouldBe
            8.6f
    }

    @Test
    fun `parse ignores unrelated sources`() {
        val html = """
            <html>
              <body>
                <span class="rating-block" data-score="4.65"></span>
              </body>
            </html>
        """.trimIndent()

        SourceMangaHtmlRatingParser.parse("Shikimori", "https://shikimori.one", html = html).shouldBeNull()
    }
}
