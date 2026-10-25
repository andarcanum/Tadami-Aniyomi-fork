package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelReaderChapterHtmlSanitizerTest {

    @Test
    fun `sanitizeChapterHtmlForReader removes active embeds and unsafe attrs`() {
        val rawHtml = """
            <div id="chapter-content">
                <style>#chapter-content{display:none}</style>
                <script>alert("x")</script>
                <p onclick="evil()" style="position: fixed; color: #fff; text-align: center">Visible text</p>
                <iframe src="https://ads.example/embed"></iframe>
            </div>
        """.trimIndent()

        val sanitized = sanitizeChapterHtmlForReader(rawHtml)

        assertFalse(sanitized.contains("<style", ignoreCase = true))
        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("<iframe", ignoreCase = true))
        assertFalse(sanitized.contains("onclick=", ignoreCase = true))
        assertFalse(sanitized.contains("position:", ignoreCase = true))
        assertTrue(sanitized.contains("Visible text"))
        assertTrue(sanitized.contains("color: #fff"))
        assertTrue(sanitized.contains("text-align: center"))
    }

    @Test
    fun `sanitizeReaderInlineStyle keeps only reader-safe declarations`() {
        val rawStyle = "position: fixed; color: #fff; text-align:center; display:flex; font-weight:700"

        val sanitized = sanitizeReaderInlineStyle(rawStyle)

        sanitized shouldBe "color: #fff; text-align: center; font-weight: 700"
    }

    @Test
    fun `sanitizeReaderInlineStyle returns null when nothing is allowed`() {
        sanitizeReaderInlineStyle("position: fixed; float: left; display: grid") shouldBe null
    }

    @Test
    fun `sanitizeChapterHtmlForReader restores word boundaries stripped by source plugins`() {
        // Multisrc plugins trim every text node separately, so these spaces never reach the app.
        val pluginHtml = "<p><strong>Victoria,</strong>nineteen, Ivy League venom in human form. " +
            "The twins, <strong>Danton</strong>and<strong>Delilah,</strong>eighteen, senior-year gods.</p>"

        val sanitized = sanitizeChapterHtmlForReader(pluginHtml)

        Jsoup.parseBodyFragment(sanitized).body().text() shouldBe
            "Victoria, nineteen, Ivy League venom in human form. " +
            "The twins, Danton and Delilah, eighteen, senior-year gods."
    }

    @Test
    fun `sanitizeChapterHtmlForReader keeps punctuation and excluded inline tags attached`() {
        val html = "<p><strong>well</strong>-known, <em>(aside)</em>. " +
            "H<sub>2</sub>O and 1990<strong>–</strong>2000.</p>"

        val sanitized = sanitizeChapterHtmlForReader(html)

        Jsoup.parseBodyFragment(sanitized).body().text() shouldBe
            "well-known, (aside). H2O and 1990–2000."
    }

    @Test
    fun `sanitizeChapterHtmlForReader does not space CJK inline boundaries`() {
        val html = "<p><strong>第一章</strong>开始，然后结束。</p>"

        val sanitized = sanitizeChapterHtmlForReader(html)

        Jsoup.parseBodyFragment(sanitized).body().text() shouldBe "第一章开始，然后结束。"
    }
}
