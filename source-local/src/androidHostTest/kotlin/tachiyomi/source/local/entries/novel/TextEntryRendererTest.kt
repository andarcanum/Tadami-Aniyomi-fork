package tachiyomi.source.local.entries.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class TextEntryRendererTest {

    @Test
    fun `html passes through untouched`() {
        renderTextFileBody("ch.xhtml", "<p>x</p>") shouldBe "<p>x</p>"
    }

    @Test
    fun `markdown converts to html`() {
        renderTextFileBody("ch.md", "# T") shouldContain "<h1>T</h1>"
    }

    @Test
    fun `markdown second alias converts`() {
        renderTextFileBody("ch.markdown", "_i_") shouldContain "<em>i</em>"
    }

    @Test
    fun `txt escapes to preformatted block`() {
        val html = renderTextFileBody("ch.txt", "a<b")
        html shouldContain "&lt;b"
        html shouldContain "<pre"
    }

    @Test
    fun `extension-less name falls back to plain`() {
        renderTextFileBody("README", "x") shouldContain "<pre"
    }

    @Test
    fun `archive md entry renders markdown`() {
        renderArchiveEntryText("text/intro.md", "# Head") shouldContain "<h1>Head</h1>"
    }

    @Test
    fun `archive html entry passes through`() {
        renderArchiveEntryText("page.htm", "<p>raw</p>") shouldBe "<p>raw</p>"
    }

    @Test
    fun `archive txt entry escapes`() {
        renderArchiveEntryText("ch.txt", "a&b") shouldContain "a&amp;b"
    }

    @Test
    fun `archive fb2 entry parses body`() {
        val fb2 = "<FictionBook><body><p>hello</p></body></FictionBook>"
        renderArchiveEntryText("book.fb2", fb2) shouldContain "hello"
    }
}
