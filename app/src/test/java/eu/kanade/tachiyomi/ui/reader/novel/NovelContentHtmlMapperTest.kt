package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class NovelContentHtmlMapperTest {

    // Canonical collect space: a blockquote is ONE atomic block (its nested <p> is not counted
    // again), and the loose text node between block elements is a block of its own. The old
    // select-based walk saw [p A, blockquote Q, nested p Q, p B] here - a duplicated "Q" and no
    // "loose" - so every translation after index 1 landed on the wrong paragraph.
    private val fixture = "<div><p>A</p><blockquote><p>Q</p></blockquote>loose<p>B</p></div>"

    @Test
    fun `extractTextBlocks uses the canonical collect order`() {
        extractTextBlocks(fixture) shouldBe listOf("A", "Q", "loose", "B")
    }

    @Test
    fun `blockquote with nested paragraph yields a single text block`() {
        extractTextBlocks("<blockquote><p>Quoted</p></blockquote>") shouldBe listOf("Quoted")
    }

    @Test
    fun `list items keep the bullet prefix in the canonical extraction`() {
        extractTextBlocks("<ul><li>one</li><li>two</li></ul>") shouldBe listOf("• one", "• two")
    }

    @Test
    fun `translated template overlay follows the canonical collect order`() {
        val translated = NovelContentHtmlMapper.buildTranslatedHtmlFromTemplate(
            templateHtml = fixture,
            translatedByIndex = mapOf(0 to "TA", 1 to "TQ", 2 to "TL", 3 to "TB"),
        )
        translated.shouldNotBeNull()
        translated shouldContain "<p>TA</p>"
        translated shouldContain "TQ"
        translated shouldContain "TL"
        translated shouldContain "<p>TB</p>"
        // The loose text node is a canonical block and must be replaced, not skipped.
        translated shouldNotContain "loose"
        // The nested <p> is not a separate block: TQ lands inside the blockquote exactly once.
        translated shouldContain "<blockquote><p>TQ</p></blockquote>"
    }

    @Test
    fun `overlay keeps original markup for missing indices`() {
        val translated = NovelContentHtmlMapper.buildTranslatedHtmlFromTemplate(
            templateHtml = fixture,
            translatedByIndex = mapOf(0 to "TA", 3 to "TB"),
        )
        translated.shouldNotBeNull()
        translated shouldContain "<p>TA</p>"
        translated shouldContain "Q"
        translated shouldContain "loose"
        translated shouldContain "<p>TB</p>"
    }
}
