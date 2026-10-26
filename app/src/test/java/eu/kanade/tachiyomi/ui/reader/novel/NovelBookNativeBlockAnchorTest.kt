package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlock
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlockKind
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeSegment
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * The compiled book never went through `annotateNovelBlockAnchors`, so its blocks reached the native
 * renderer without any address at all: nothing could be highlighted and nothing could be scrolled to.
 */
class NovelBookNativeBlockAnchorTest {

    @Test
    fun `a compiled block knows where it sits in its chapter`() {
        val blocks = listOf(
            heading(chapterId = 7L, charStart = 0),
            paragraph(chapterId = 7L, charStart = 10, text = "first"),
            image(chapterId = 7L, charStart = 20),
            rule(chapterId = 7L, charStart = 21),
            paragraph(chapterId = 7L, charStart = 22, text = "after the picture"),
        )

        val rendered = blocks.toRichContentBlocks()

        rendered.mapIndexed { index, block -> block.anchor shouldBe NovelBlockAnchor(7L, index) }
        rendered.size shouldBe blocks.size
    }

    @Test
    fun `every chapter of a section is numbered from its own start`() {
        val blocks = listOf(
            heading(chapterId = 7L, charStart = 0),
            paragraph(chapterId = 7L, charStart = 10, text = "end of the chapter"),
            heading(chapterId = 8L, charStart = 30),
            paragraph(chapterId = 8L, charStart = 40, text = "start of the next one"),
        )

        val rendered = blocks.toRichContentBlocks()

        rendered.map { it.anchor } shouldBe listOf(
            NovelBlockAnchor(7L, 0),
            NovelBlockAnchor(7L, 1),
            NovelBlockAnchor(8L, 0),
            NovelBlockAnchor(8L, 1),
        )
    }

    @Test
    fun `hiding the chapter heading does not renumber the blocks the voice speaks`() {
        val blocks = listOf(
            heading(chapterId = 7L, charStart = 0),
            paragraph(chapterId = 7L, charStart = 10, text = "first spoken paragraph"),
        )

        val rendered = blocks.toRichContentBlocks(includeChapterHeadings = false)

        rendered.map { it.anchor } shouldBe listOf(NovelBlockAnchor(7L, 1))
    }

    @Test
    fun `a chapter that starts in an earlier section keeps counting from its beginning`() {
        val chapter = listOf(
            heading(chapterId = 7L, charStart = 0),
            paragraph(chapterId = 7L, charStart = 10, text = "still in the previous window"),
            paragraph(chapterId = 7L, charStart = 40, text = "first paragraph of this window"),
        )

        // What `anchoredNativeBlocksFor` does once it clipped the decoded chapter to the section.
        val clipped = chapter.withChapterBlockIndices().filter { it.block.charStart >= 40 }

        clipped.toAnchoredRichContentBlocks().map { it.anchor } shouldBe listOf(NovelBlockAnchor(7L, 2))
    }

    @Test
    fun `all native block kinds preserve source range and title flag`() {
        val blocks = listOf(
            heading(chapterId = 7L, charStart = 0),
            paragraph(chapterId = 7L, charStart = 10, text = "first"),
            image(chapterId = 7L, charStart = 20),
            rule(chapterId = 7L, charStart = 21),
            NovelBookNativeBlock(
                chapterId = 7L,
                charStart = 22,
                charLength = 15,
                kind = NovelBookNativeBlockKind.QUOTE,
                segments = listOf(NovelBookNativeSegment(t = "quoted speech")),
            ),
        )

        val rendered = blocks.toRichContentBlocks()

        rendered[0].bookRange shouldBe NovelBookSourceRange(0, 10)
        (rendered[0] as NovelRichContentBlock.Heading).isBookChapterHeading shouldBe true

        rendered[1].bookRange shouldBe NovelBookSourceRange(10, 5)
        rendered[2].bookRange shouldBe NovelBookSourceRange(20, 1)
        rendered[3].bookRange shouldBe NovelBookSourceRange(21, 1)
        rendered[4].bookRange shouldBe NovelBookSourceRange(22, 15)
    }

    @Test
    fun `blocks without valid source range remain legacy without bookRange`() {
        val block = NovelBookNativeBlock(
            chapterId = 7L,
            charStart = 0,
            charLength = 0,
            kind = NovelBookNativeBlockKind.PARAGRAPH,
            segments = listOf(NovelBookNativeSegment(t = "no range")),
        )
        val rendered = listOf(block).toRichContentBlocks().single()
        rendered.bookRange shouldBe null
    }

    private fun heading(chapterId: Long, charStart: Int) = NovelBookNativeBlock(
        chapterId = chapterId,
        charStart = charStart,
        charLength = 10,
        kind = NovelBookNativeBlockKind.HEADING,
        level = 1,
        isChapterHeading = true,
        segments = listOf(NovelBookNativeSegment(t = "Chapter")),
    )

    private fun paragraph(chapterId: Long, charStart: Int, text: String) = NovelBookNativeBlock(
        chapterId = chapterId,
        charStart = charStart,
        charLength = text.length,
        kind = NovelBookNativeBlockKind.PARAGRAPH,
        segments = listOf(NovelBookNativeSegment(t = text)),
    )

    private fun image(chapterId: Long, charStart: Int) = NovelBookNativeBlock(
        chapterId = chapterId,
        charStart = charStart,
        charLength = 1,
        kind = NovelBookNativeBlockKind.IMAGE,
        imageUrl = "https://example.invalid/picture.png",
    )

    private fun rule(chapterId: Long, charStart: Int) = NovelBookNativeBlock(
        chapterId = chapterId,
        charStart = charStart,
        charLength = 1,
        kind = NovelBookNativeBlockKind.RULE,
    )
}
