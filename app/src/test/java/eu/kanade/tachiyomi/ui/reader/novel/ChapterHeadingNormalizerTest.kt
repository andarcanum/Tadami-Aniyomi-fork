package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.presentation.reader.novel.isNativeChapterTitleText
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

    @Test
    fun `promotes matching first paragraph instead of duplicating chapter title`() {
        val html = "<div><p>Глава 12: Начало пути</p><p>Первый абзац текста.</p></div>"

        val result = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = "Глава 12 — Начало пути",
        )
        val body = Jsoup.parseBodyFragment(result).body()

        body.select("h1.an-reader-chapter-title").size shouldBe 1
        body.select("p").map { it.text() } shouldBe listOf("Первый абзац текста.")
    }

    @Test
    fun `deduplicates volume-prefixed chapter name against body paragraph title`() {
        val html = "<p><strong>Глава 5. Пробуждение</strong></p><p>Герой открыл глаза.</p>"

        val result = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = "Том 1. Глава 5: Пробуждение",
        )
        val body = Jsoup.parseBodyFragment(result).body()

        body.select("h1.an-reader-chapter-title").size shouldBe 1
        body.selectFirst("h1.an-reader-chapter-title")?.text() shouldBe "Том 1. Глава 5: Пробуждение"
        body.select("p").map { it.text() } shouldBe listOf("Герой открыл глаза.")
    }

    @Test
    fun `deduplicates paragraph repeating an existing heading tag and preserves richer title`() {
        val html = "<h2>Глава 12</h2><p>Глава 12: Пробуждение древнего дракона</p><p>Текст главы.</p>"

        val result = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = "Глава 12",
        )
        val body = Jsoup.parseBodyFragment(result).body()

        body.select("h1.an-reader-chapter-title").size shouldBe 0
        body.select("h2").map { it.text() } shouldBe listOf("Глава 12: Пробуждение древнего дракона")
        body.select("p").map { it.text() } shouldBe listOf("Текст главы.")
    }

    @Test
    fun `deduplicates subtitle-only first paragraph when chapter name has chapter prefix`() {
        val html = "<p>Тень прошлого</p><p>Ветер завывал за окном.</p>"

        val result = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = "Глава 7: Тень прошлого",
        )
        val body = Jsoup.parseBodyFragment(result).body()

        body.select("h1.an-reader-chapter-title").map { it.text() } shouldBe listOf("Глава 7: Тень прошлого")
        body.select("p").map { it.text() } shouldBe listOf("Ветер завывал за окном.")
    }

    @Test
    fun `does not treat long prose paragraph as duplicate chapter title`() {
        val prose = "Глава 12 была самой трудной за всё время его путешествия, " +
            "но он не собирался сдаваться и пошёл вперёд сквозь бурю."
        val html = "<p>$prose</p><p>Второй абзац.</p>"

        val result = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = "Глава 12",
        )
        val body = Jsoup.parseBodyFragment(result).body()

        body.select("h1.an-reader-chapter-title").map { it.text() } shouldBe listOf("Глава 12")
        body.select("p").map { it.text() } shouldBe listOf(prose, "Второй абзац.")
    }

    @Test
    fun `merges split number-only heading and subtitle paragraph without dropping chapter number`() {
        val html = "<h1>Глава 5</h1><p>Пробуждение древнего дракона</p><p>Текст главы.</p>"

        val result = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = "Глава 5: Пробуждение древнего дракона",
        )
        val body = Jsoup.parseBodyFragment(result).body()

        body.select("h1").map { it.text() } shouldBe listOf("Глава 5: Пробуждение древнего дракона")
        body.select("p").map { it.text() } shouldBe listOf("Текст главы.")
    }

    @Test
    fun `does not delete short opening paragraph when heading already has full title`() {
        val html = "<h1>Глава 5: Тишина</h1><p>Тишина.</p><p>Ни звука вокруг.</p>"

        val result = prependChapterHeadingIfMissing(
            rawHtml = html,
            chapterName = "Глава 5: Тишина",
        )
        val body = Jsoup.parseBodyFragment(result).body()

        body.select("h1").map { it.text() } shouldBe listOf("Глава 5: Тишина")
        body.select("p").map { it.text() } shouldBe listOf("Тишина.", "Ни звука вокруг.")
    }

    @Test
    fun `injectIntoHtml preserves dollar signs and backslashes in CSS and JS`() {
        val html = "<html><head></head><body><p>Body</p></body></html>"
        val injection = """<style>p::before { content: "\2014"; }</style><script>const x = ${'$'}1;</script>"""

        val result = injectIntoHtml(html, injection)

        result.contains("""content: "\2014"""") shouldBe true
        result.contains("const x = \$1;") shouldBe true
    }

    @Test
    fun `native chapter title matcher handles normalized punctuation and volume prefix`() {
        isNativeChapterTitleText("Глава 12: Начало", "Том 1 Гл. 12 — Начало") shouldBe true
        isNativeChapterTitleText("12. Начало", "Глава 12: Начало") shouldBe true
        isNativeChapterTitleText("Он открыл глаза и посмотрел на небо.", "Глава 12") shouldBe false
    }
}
