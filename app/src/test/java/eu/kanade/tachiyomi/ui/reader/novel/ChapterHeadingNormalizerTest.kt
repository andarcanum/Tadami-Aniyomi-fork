package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class ChapterHeadingNormalizerTest {

    @Test
    fun `does not prepend synthetic heading when chapter already has h4 heading`() {
        val html = "<div><h4>Chapter 1332: Dispute (I)</h4><p>Body</p></div>"

        val result = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = "Chapter 1332: Dispute (I)",
        )
        val body = Jsoup.parseBodyFragment(result).body()

        body.select("h1.an-reader-chapter-title").size shouldBe 0
        body.select("h4").size shouldBe 1
    }

    @Test
    fun `prepends synthetic heading when no heading exists`() {
        val html = "<div><p>Body</p></div>"

        val result = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = "Chapter 12",
        )
        val body = Jsoup.parseBodyFragment(result).body()

        body.select("h1.an-reader-chapter-title").size shouldBe 1
        body.selectFirst("h1.an-reader-chapter-title")?.text() shouldBe "Chapter 12"
    }
}
