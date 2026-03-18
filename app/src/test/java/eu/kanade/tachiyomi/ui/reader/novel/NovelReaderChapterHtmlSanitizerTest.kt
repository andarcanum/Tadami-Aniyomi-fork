package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
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
}

