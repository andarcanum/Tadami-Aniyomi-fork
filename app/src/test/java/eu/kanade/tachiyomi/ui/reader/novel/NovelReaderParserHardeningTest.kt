package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.test.assertIcuSafeRegexPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelReaderParserHardeningTest {
    private fun paragraphs(html: String): List<NovelRichContentBlock.Paragraph> =
        parseNovelRichContent(html).blocks.filterIsInstance<NovelRichContentBlock.Paragraph>()

    private fun texts(html: String): List<String> = paragraphs(html).map { paragraph ->
        paragraph.segments.joinToString("") { it.text }.trim()
    }

    @Test
    fun `mixed body nodes stay in DOM order`() {
        assertEquals(listOf("A", "B", "C"), texts("A<p>B</p>C"))
    }

    @Test
    fun `text-only body is parsed once`() {
        assertEquals(listOf("Only once"), texts("Only once"))
    }

    @Test
    fun `top level inline runs do not become reordered paragraphs`() {
        assertEquals(listOf("A B C"), texts("A <b>B</b> C"))
    }

    @Test
    fun `inline boundaries preserve word separators and links`() {
        val paragraph = paragraphs("<p><b>A</b> <a href='https://example.org'>B</a> <i>C</i></p>").single()
        assertEquals("A B C", paragraph.segments.joinToString("") { it.text })
        assertEquals("https://example.org", paragraph.segments.single { it.text == "B" }.linkUrl)
    }

    @Test
    fun `whitespace does not create phantom paragraphs`() {
        assertEquals(listOf("A", "B"), texts("  <p>A</p> \n <p>B</p>  "))
    }

    @Test
    fun `rem is not consumed by em suffix`() {
        assertEquals(2f, paragraphs("<p style='text-indent:2rem'>A</p>").single().firstLineIndentEm)
    }

    @Test
    fun `descendant selector does not leak outside its ancestor`() {
        val blocks = paragraphs("<style>.entry p { text-indent:2em; }</style><div class='entry'><p>A</p></div><p>B</p>")
        assertEquals(2f, blocks[0].firstLineIndentEm)
        assertEquals(null, blocks[1].firstLineIndentEm)
    }

    @Test
    fun `CSS class case is preserved during matching`() {
        val blocks = paragraphs("<style>.Entry p { text-indent:2em; }</style><div class='Entry'><p>A</p></div><p>B</p>")
        assertEquals(2f, blocks[0].firstLineIndentEm)
        assertEquals(null, blocks[1].firstLineIndentEm)
    }

    @Test
    fun `flex and grid declarations are recognized independent of spaces`() {
        listOf("display:flex", "display: flex", "display : GRID", "display:inline-flex !important").forEach { css ->
            assertTrue(parseNovelRichContent("<div style='$css'><p>A</p></div>").unsupportedFeaturesDetected, css)
        }
    }

    @Test
    fun `unsupported layout in a style block requests fallback`() {
        assertTrue(
            parseNovelRichContent(
                "<style>.x { display: flex; }</style><div class='x'>A</div>",
            ).unsupportedFeaturesDetected,
        )
    }

    @Test
    fun `style rule regex keeps its braces escaped for the Android ICU engine`() {
        // The desktop JVM used by unit tests accepts a bare '}' as a literal; Android's ICU engine
        // throws PatternSyntaxException while compiling it, which broke every chapter load on device.
        assertIcuSafeRegexPattern(richCssRuleBodyRegex.pattern)
    }

    @Test
    fun `styled inline words keep the source spaces around them`() {
        val html = "<p> <strong>Victoria,</strong> nineteen, Ivy League venom in human form. " +
            "The twins, <strong>Danton</strong> and <strong>Delilah,</strong> eighteen, senior-year gods.</p>"
        assertEquals(
            "Victoria, nineteen, Ivy League venom in human form. " +
                "The twins, Danton and Delilah, eighteen, senior-year gods.",
            texts(html).single(),
        )
    }

    @Test
    fun `ordinary inline formatting stays supported`() {
        assertFalse(parseNovelRichContent("<p><b>A</b> <i>B</i></p>").unsupportedFeaturesDetected)
    }
}
