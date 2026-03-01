package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelRichContentParserTest {

    @Test
    fun `rich paragraph model stores text and spans`() {
        val block = NovelRichContentBlock.Paragraph(
            segments = listOf(
                NovelRichTextSegment(
                    text = "Hello",
                    style = NovelRichTextStyle(bold = true),
                ),
            ),
        )

        (block as NovelRichContentBlock.Paragraph).segments.first().style.bold shouldBe true
    }

    @Test
    fun `parser extracts inline tags and links`() {
        val html = """
            <html><body>
            <p><strong>Bold</strong> <em>Italic</em> <a href="https://example.com">Link</a></p>
            </body></html>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        result.blocks shouldHaveSize 1
        assertFalse(result.unsupportedFeaturesDetected)
        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.segments.map { it.text.trim() }.filter { it.isNotEmpty() } shouldBe listOf("Bold", "Italic", "Link")
        paragraph.segments[0].style.bold shouldBe true
        paragraph.segments[1].style.italic shouldBe true
        paragraph.segments[2].linkUrl shouldBe "https://example.com"
    }

    @Test
    fun `parser extracts headings blockquotes and images`() {
        val html = """
            <h2>Chapter Header</h2>
            <blockquote>Quote text</blockquote>
            <img src="https://example.com/image.jpg" alt="preview" />
        """.trimIndent()

        val result = parseNovelRichContent(html)

        result.blocks shouldHaveSize 3
        assertTrue(result.blocks[0] is NovelRichContentBlock.Heading)
        assertTrue(result.blocks[1] is NovelRichContentBlock.BlockQuote)
        assertTrue(result.blocks[2] is NovelRichContentBlock.Image)
    }

    @Test
    fun `parser keeps plugin image inside paragraph container`() {
        val html = """
            <p><img src="heximg://hexnovels?ref=test-image" alt="hex" /></p>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        result.blocks shouldHaveSize 1
        val image = result.blocks.first() as NovelRichContentBlock.Image
        image.url shouldBe "heximg://hexnovels?ref=test-image"
        image.alt shouldBe "hex"
    }

    @Test
    fun `parser reads image url from data-src when src is empty`() {
        val html = """
            <p><img src="" data-src="/images/ch1.webp" alt="lazy" /></p>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        result.blocks shouldHaveSize 1
        val image = result.blocks.first() as NovelRichContentBlock.Image
        image.url shouldBe "/images/ch1.webp"
        image.alt shouldBe "lazy"
    }

    @Test
    fun `parser preserves block text alignment from inline style`() {
        val html = """
            <p style="text-align: center">Centered text</p>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.textAlign shouldBe NovelRichBlockTextAlign.CENTER
    }

    @Test
    fun `parser preserves first-line indent from inline style`() {
        val html = """
            <p style="text-indent: 2em">Indented text</p>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 2f
    }

    @Test
    fun `parser applies default paragraph indent from style tag`() {
        val html = """
            <html><head><style>p { text-indent: 1.5em; }</style></head><body><p>Indented by css</p></body></html>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 1.5f
    }

    @Test
    fun `parser applies paragraph class indent from style tag`() {
        val html = """
            <html><head><style>p.indent { text-indent: 24px; }</style></head><body><p class="indent">Indented by class css</p></body></html>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 1.5f
    }

    @Test
    fun `parser applies descendant paragraph class indent from style tag`() {
        val html = """
            <html><head><style>.entry p { text-indent: 2em; }</style></head><body><div class="entry"><p>Indented by descendant css</p></div></body></html>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 2f
    }

    @Test
    fun `parser applies text align from style tag`() {
        val html = """
            <html><head><style>p { text-align: justify; }</style></head><body><p>Aligned by css</p></body></html>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.textAlign shouldBe NovelRichBlockTextAlign.JUSTIFY
    }

    @Test
    fun `parser applies default div indent from style tag`() {
        val html = """
            <html><head><style>div { text-indent: 1.5em; }</style></head><body><div>Indented div paragraph</div></body></html>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 1.5f
    }

    @Test
    fun `parser prefers inline indent over style tag indent`() {
        val html = """
            <html><head><style>p { text-indent: 1em; }</style></head><body><p style="text-indent: 3em">Inline wins</p></body></html>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 3f
    }

    @Test
    fun `parser parses text indent in pt units`() {
        val html = """
            <p style="text-indent: 24pt">Indented by pt</p>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 2f
    }

    @Test
    fun `parser infers first-line indent from leading ideographic spaces`() {
        val html = """
            <p>　　Indented by leading spaces</p>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 2f
        paragraph.segments.joinToString(separator = "") { it.text } shouldBe "Indented by leading spaces"
    }

    @Test
    fun `parser infers first-line indent from leading em spaces and trims formatting newline`() {
        val html = """
            <p>
                &emsp;&emsp;Indented by em spaces
            </p>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 2f
        paragraph.segments.joinToString(separator = "") { it.text }.trim() shouldBe "Indented by em spaces"
    }

    @Test
    fun `parser strips trailing formatting newline after inferred leading indent`() {
        val html = "<p>\n\u3000\u3000Indented by ideographic spaces\n</p>"

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe 2f
        paragraph.segments.joinToString(separator = "") { it.text } shouldBe "Indented by ideographic spaces"
    }

    @Test
    fun `parser drops whitespace-only paragraph made of indent markers`() {
        val html = "<p>&emsp;&emsp;</p><p>Visible paragraph</p>"

        val result = parseNovelRichContent(html)

        result.blocks shouldHaveSize 1
        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.segments.joinToString(separator = "") { it.text } shouldBe "Visible paragraph"
    }

    @Test
    fun `parser does not treat single leading regular space as paragraph indent`() {
        val html = """
            <p> Single leading space</p>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        val paragraph = result.blocks.first() as NovelRichContentBlock.Paragraph
        paragraph.firstLineIndentEm shouldBe null
    }

    @Test
    fun `parser flags unsupported structures for webview fallback`() {
        val html = """
            <table><tr><td>Complex layout</td></tr></table>
        """.trimIndent()

        val result = parseNovelRichContent(html)

        assertTrue(result.unsupportedFeaturesDetected)
    }
}
