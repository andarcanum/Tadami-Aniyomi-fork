package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelBlockAnchorTest {

    @Test
    fun `anchors are dense and follow the chapter block order`() {
        val html = "<p>One</p><p>Two</p><h2>Heading</h2><p>Three</p>"

        val annotated = annotateNovelBlockAnchors(rawHtml = html, chapterId = 42L)
        val blocks = parseNovelRichContent(annotated).blocks

        blocks.map { it.anchor } shouldBe listOf(
            NovelBlockAnchor(chapterId = 42L, blockIndex = 0),
            NovelBlockAnchor(chapterId = 42L, blockIndex = 1),
            NovelBlockAnchor(chapterId = 42L, blockIndex = 2),
            NovelBlockAnchor(chapterId = 42L, blockIndex = 3),
        )
    }

    @Test
    fun `an artifact chapter wrapper is unwrapped instead of being one anchor`() {
        val html = "<section class=\"nb-chapter\" data-cid=\"7\"><p>One</p><p>Two</p></section>"

        val annotated = annotateNovelBlockAnchors(rawHtml = html, chapterId = 7L)
        val blocks = parseNovelRichContent(annotated).blocks

        blocks.mapNotNull { it.anchor?.blockIndex } shouldBe listOf(0, 1)
    }

    @Test
    fun `anchor indices match the block stream the tts model numbers`() {
        // The TTS model numbers blocks with `richContentBlocks.mapIndexed`, so every parsed block
        // must carry the anchor of its own position: an image, a rule or a container that expands
        // into several blocks would otherwise shift the two numberings apart from that point on.
        val html = buildString {
            append("<p>One</p>")
            append("<img src=\"a.png\">")
            append("<hr>")
            append("<blockquote><p>Quoted one</p><p>Quoted two</p></blockquote>")
            append("<p>Last</p>")
        }

        val annotated = annotateNovelBlockAnchors(rawHtml = html, chapterId = 11L)
        val blocks = parseNovelRichContent(annotated).blocks

        blocks.mapIndexed { index, block -> block.anchor?.blockIndex to index }
            .forEach { (anchorIndex, index) -> anchorIndex shouldBe index }
        blocks.all { it.anchor?.chapterId == 11L } shouldBe true
    }

    @Test
    fun `a pre block expanded into paragraphs keeps dense anchors`() {
        val html = "<pre>First para\nwrapped line\n\nSecond para</pre>"

        val annotated = annotateNovelBlockAnchors(rawHtml = html, chapterId = 5L)
        val blocks = parseNovelRichContent(annotated).blocks

        blocks.map { it.anchor } shouldBe listOf(
            NovelBlockAnchor(chapterId = 5L, blockIndex = 0),
            NovelBlockAnchor(chapterId = 5L, blockIndex = 1),
        )
    }

    @Test
    fun `markup without anchors still parses into blocks that carry none`() {
        val blocks = parseNovelRichContent("<p>One</p><p>Two</p>").blocks

        blocks.size shouldBe 2
        blocks.all { it.anchor == null } shouldBe true
    }

    @Test
    fun `an unknown chapter is left untouched`() {
        val html = "<p>One</p>"

        annotateNovelBlockAnchors(rawHtml = html, chapterId = BookLocator.NO_CHAPTER_ID) shouldBe html
    }

    @Test
    fun `dom ids round trip`() {
        NovelBlockAnchor(chapterId = 9L, blockIndex = 3).domId shouldBe "9:3"
        NovelBlockAnchor.parse("9:3") shouldBe NovelBlockAnchor(chapterId = 9L, blockIndex = 3)
        NovelBlockAnchor.parse("nonsense") shouldBe null
        NovelBlockAnchor.parse(null) shouldBe null
    }
}
