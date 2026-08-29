package tachiyomi.source.local.io.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class MarkdownToHtmlConverterTest {

    @Test
    fun `heading converts to h1`() {
        markdownToHtml("# Title") shouldContain "<h1>Title</h1>"
    }

    @Test
    fun `emphasis and strong convert`() {
        val html = markdownToHtml("*a* **b**")
        html shouldContain "<em>a</em>"
        html shouldContain "<strong>b</strong>"
    }

    @Test
    fun `list converts to ul li`() {
        val html = markdownToHtml("- a\n- b")
        html shouldContain "<ul>"
        html shouldContain "<li>a</li>"
    }

    @Test
    fun `plain paragraph wrapped in p`() {
        markdownToHtml("hello") shouldContain "<p>hello</p>"
    }

    @Test
    fun `output wrapped in html body`() {
        val html = markdownToHtml("hello")
        html.startsWith("<html><body>") shouldBe true
        html.endsWith("</body></html>") shouldBe true
    }

    @Test
    fun `blank input yields empty body`() {
        markdownToHtml("") shouldBe "<html><body></body></html>"
        markdownToHtml("   \n  ") shouldBe "<html><body></body></html>"
    }
}
