package eu.kanade.presentation.reader.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelFootnoteResolverTest {

    @Test
    fun `resolves local hash footnote and strips backref arrow`() {
        val html = """
            <p>A mystery <a href="#fn1"><sup>1</sup></a> appeared.</p>
            <ol>
              <li id="fn1">Guild of Night Lanterns <a href="#fnref1">↩</a></li>
            </ol>
        """.trimIndent()

        val result = resolveNovelFootnoteFromHtml(
            rawUrl = "#fn1",
            rawHtml = html,
        )

        assertNotNull(result)
        assertTrue(result!!.found)
        assertEquals("fn1", result.anchorId)
        assertEquals("[1]", result.titleNumber)
        assertEquals("Guild of Night Lanterns", result.bodyText)
    }

    @Test
    fun `resolves legacy empty anchor inside container paragraph`() {
        val html = """
            <p><a id="note_3"></a>Third decree of the northern province.</p>
        """.trimIndent()

        val result = resolveNovelFootnoteFromHtml(
            rawUrl = "#note_3",
            rawHtml = html,
        )

        assertNotNull(result)
        assertTrue(result!!.found)
        assertEquals("note_3", result.anchorId)
        assertEquals("[3]", result.titleNumber)
        assertEquals("Third decree of the northern province.", result.bodyText)
    }

    @Test
    fun `resolves same chapter URL with fragment`() {
        val html = """
            <aside id="fn-guild-2"><p>Established in year 412.</p><a class="footnote-backref" href="#ref2">back</a></aside>
        """.trimIndent()

        val result = resolveNovelFootnoteFromHtml(
            rawUrl = "https://example.com/novel/ch-12#fn-guild-2",
            rawHtml = html,
            chapterWebUrl = "https://example.com/novel/ch-12",
        )

        assertNotNull(result)
        assertTrue(result!!.found)
        assertEquals("fn-guild-2", result.anchorId)
        assertEquals("[2]", result.titleNumber)
        assertEquals("Established in year 412.", result.bodyText)
    }

    @Test
    fun `returns missing footnote fallback for unresolved local anchor`() {
        val html = "<p>Chapter body without footnotes.</p>"

        val result = resolveNovelFootnoteFromHtml(
            rawUrl = "#fn99",
            rawHtml = html,
        )

        assertNotNull(result)
        assertFalse(result!!.found)
        assertEquals("fn99", result.anchorId)
        assertEquals("[99]", result.titleNumber)
        assertNull(result.resolvedExternalUrl)
    }

    @Test
    fun `returns null for external URL without matching local anchor`() {
        val html = "<p>Chapter body.</p>"

        val result = resolveNovelFootnoteFromHtml(
            rawUrl = "https://en.wikipedia.org/wiki/Moon#Orbit",
            rawHtml = html,
            chapterWebUrl = "https://example.com/novel/ch-12",
        )

        assertNull(result)
    }
}
