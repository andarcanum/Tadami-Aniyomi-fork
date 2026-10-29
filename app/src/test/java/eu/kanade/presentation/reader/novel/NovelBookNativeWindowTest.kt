package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.BookLocator
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekReason
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSourceRange
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookWindowState
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelBookNativeWindowTest {

    private fun sectionOf(index: Int, revision: Long = 0L) = NovelBookNativeSection(
        sectionIndex = index,
        blocks = listOf("section-$index"),
        revision = revision,
    )

    private fun windowOf(
        resident: List<Int>,
        revisions: Map<Int, Long> = emptyMap(),
    ) = NovelBookWindowState(
        sectionCount = 20,
        residentSections = resident,
        sectionRevisions = revisions,
    )

    @Test
    fun `sections that left the window are dropped`() {
        val mounted = listOf(sectionOf(2), sectionOf(3), sectionOf(4))

        val pruned = pruneNovelBookNativeSections(mounted, windowOf(listOf(3, 4, 5)))

        pruned.map { it.sectionIndex } shouldBe listOf(3, 4)
    }

    @Test
    fun `a window that holds everything is returned untouched`() {
        val mounted = listOf(sectionOf(3), sectionOf(4))

        val pruned = pruneNovelBookNativeSections(mounted, windowOf(listOf(3, 4, 5)))

        // Same instance: an unchanged window must not make the reader recompose its list.
        (pruned === mounted) shouldBe true
    }

    @Test
    fun `missing sections are pulled nearest to the reading position first`() {
        val mounted = listOf(sectionOf(4))

        val missing = missingNovelBookNativeSections(mounted, windowOf(listOf(2, 3, 4, 5, 6)))

        missing shouldBe listOf(3, 5, 2, 6)
    }

    @Test
    fun `a section whose revision moved on is pulled again in place`() {
        val mounted = listOf(sectionOf(3), sectionOf(4, revision = 1L), sectionOf(5))

        val missing = missingNovelBookNativeSections(
            sections = mounted,
            window = windowOf(resident = listOf(3, 4, 5), revisions = mapOf(4 to 2L)),
        )

        missing shouldBe listOf(4)
    }

    @Test
    fun `nothing is pulled while the renderer already holds the window`() {
        val mounted = listOf(sectionOf(3), sectionOf(4), sectionOf(5))

        missingNovelBookNativeSections(mounted, windowOf(listOf(3, 4, 5))) shouldBe emptyList()
    }

    @Test
    fun `a pulled section is inserted in spine order`() {
        val mounted = listOf(sectionOf(3), sectionOf(5))

        val next = withNovelBookNativeSection(mounted, sectionOf(4))

        next.map { it.sectionIndex } shouldBe listOf(3, 4, 5)
    }

    @Test
    fun `re-pulling a section replaces it instead of duplicating it`() {
        val mounted = listOf(sectionOf(3), sectionOf(4), sectionOf(5))

        val next = withNovelBookNativeSection(mounted, sectionOf(4, revision = 2L))

        next.map { it.sectionIndex } shouldBe listOf(3, 4, 5)
        next.first { it.sectionIndex == 4 }.revision shouldBe 2L
    }

    private fun blockOf(
        sectionIndex: Int,
        blockIndex: Int,
        block: NovelRichContentBlock,
        charOffsetBefore: Int,
        sectionCharCount: Int,
        sectionBlockCount: Int,
    ) = NovelBookNativeEntry.Block(
        sectionIndex = sectionIndex,
        blockIndex = blockIndex,
        block = block,
        charOffsetBefore = charOffsetBefore,
        blockCharCount = novelBookBlockTextLength(block),
        sectionCharCount = sectionCharCount,
        sectionBlockCount = sectionBlockCount,
    )

    /** Text length used by the entry builder; mirrors the production char-weight rule. */
    private fun novelBookBlockTextLength(block: NovelRichContentBlock): Int = when (block) {
        is NovelRichContentBlock.Paragraph -> block.segments.sumOf { it.text.length }.coerceAtLeast(1)
        is NovelRichContentBlock.Heading -> block.segments.sumOf { it.text.length }.coerceAtLeast(1)
        is NovelRichContentBlock.BlockQuote -> block.segments.sumOf { it.text.length }.coerceAtLeast(1)
        else -> 1
    }

    private fun paragraphOf(text: String) = NovelRichContentBlock.Paragraph(
        segments = listOf(NovelRichTextSegment(text)),
    )

    private fun entriesOf(vararg sectionIndices: Int): List<NovelBookNativeEntry> =
        sectionIndices.map { index ->
            val text = "section-$index"
            blockOf(
                sectionIndex = index,
                blockIndex = 0,
                block = paragraphOf(text),
                charOffsetBefore = 0,
                sectionCharCount = text.length,
                sectionBlockCount = 1,
            )
        }

    private fun seekRequestOf(id: Long, sectionIndex: Int, charOffset: Int = 0) = BookSeekRequest(
        id = id,
        locator = BookLocator(chapterId = sectionIndex.toLong(), blockIndex = 0, charOffset = 0),
        location = NovelBookLocation(sectionIndex = sectionIndex, charOffset = charOffset),
        reason = BookSeekReason.Resume,
    )

    @Test
    fun `a seek resolves to the list item holding that section`() {
        val target = resolveNovelBookNativeSeekTarget(
            entries = entriesOf(3, 4, 5),
            request = seekRequestOf(id = 7L, sectionIndex = 5, charOffset = 3),
            lastAppliedSeekId = 0L,
            sectionFraction = 0f,
        )

        target shouldBe NovelBookNativeSeekTarget(seekRequestId = 7L, itemIndex = 2, fraction = 3f / 9f)
    }

    @Test
    fun `a seek lands inside the block that owns the requested char offset`() {
        // Section 5 holds two blocks: "aaaa" (4 chars) then "bbbbbb" (6 chars).
        val entries = listOf(
            blockOf(
                sectionIndex = 5,
                blockIndex = 0,
                block = paragraphOf("aaaa"),
                charOffsetBefore = 0,
                sectionCharCount = 10,
                sectionBlockCount = 2,
            ),
            blockOf(
                sectionIndex = 5,
                blockIndex = 1,
                block = paragraphOf("bbbbbb"),
                charOffsetBefore = 4,
                sectionCharCount = 10,
                sectionBlockCount = 2,
            ),
        )

        val target = resolveNovelBookNativeSeekTarget(
            entries = entries,
            request = seekRequestOf(id = 7L, sectionIndex = 5, charOffset = 7),
            lastAppliedSeekId = 0L,
            sectionFraction = 0f,
        )

        // Char 7 lives in the second block (range 4..10), halfway through it.
        target shouldBe NovelBookNativeSeekTarget(seekRequestId = 7L, itemIndex = 1, fraction = 0.5f)
    }

    @Test
    fun `a seek with zero char offset targets the section start regardless of fraction`() {
        val target = resolveNovelBookNativeSeekTarget(
            entries = entriesOf(3, 4, 5),
            request = seekRequestOf(id = 7L, sectionIndex = 5),
            lastAppliedSeekId = 0L,
            sectionFraction = 0.5f,
        )

        target shouldBe NovelBookNativeSeekTarget(seekRequestId = 7L, itemIndex = 2, fraction = 0f)
    }

    @Test
    fun `a seek is applied at most once`() {
        val target = resolveNovelBookNativeSeekTarget(
            entries = entriesOf(3, 4, 5),
            request = seekRequestOf(id = 7L, sectionIndex = 5),
            lastAppliedSeekId = 7L,
            sectionFraction = 0.25f,
        )

        target shouldBe null
    }

    @Test
    fun `a seek waits until its section is mounted`() {
        val target = resolveNovelBookNativeSeekTarget(
            entries = entriesOf(3, 4, 5),
            request = seekRequestOf(id = 7L, sectionIndex = 9),
            lastAppliedSeekId = 0L,
            sectionFraction = 0f,
        )

        target shouldBe null
    }

    @Test
    fun `a failed section keeps its place in the book`() {
        val entries = buildNovelBookNativeEntries(
            sections = listOf(
                NovelBookNativeSection(
                    sectionIndex = 3,
                    blocks = listOf(paragraphOf("section-3")),
                ),
                NovelBookNativeSection(
                    sectionIndex = 5,
                    blocks = listOf(paragraphOf("section-5")),
                ),
            ),
            failedSectionIndices = listOf(4),
        )

        entries.map { it.sectionIndex } shouldBe listOf(3, 4, 5)
        (entries[1] is NovelBookNativeEntry.Failed) shouldBe true
    }

    @Test
    fun `every block of a section becomes its own row`() {
        val entries = buildNovelBookNativeEntries(
            sections = listOf(
                NovelBookNativeSection(
                    sectionIndex = 3,
                    blocks = listOf(paragraphOf("aaaa"), paragraphOf("bbbb"), paragraphOf("cc")),
                ),
            ),
            failedSectionIndices = emptyList(),
        )

        entries.size shouldBe 3
        entries.map { (it as NovelBookNativeEntry.Block).blockIndex } shouldBe listOf(0, 1, 2)
        entries.map { (it as NovelBookNativeEntry.Block).charOffsetBefore } shouldBe listOf(0, 4, 8)
        entries.map { (it as NovelBookNativeEntry.Block).sectionCharCount } shouldBe listOf(10, 10, 10)
    }

    @Test
    fun `the reading position is weighted by the block's share of the section text`() {
        val items = listOf(
            NovelBookNativeViewportItem(
                sectionIndex = 5,
                charOffsetBefore = 4,
                blockCharCount = 6,
                sectionCharCount = 10,
                offsetPx = -50,
                heightPx = 100,
            ),
        )

        val location = resolveNovelBookNativeRelocate(items)

        // Halfway through the 6-char second block = char 7 of 10.
        location shouldBe NovelBookViewLocation(sectionIndex = 5, sectionFraction = 0.7f)
    }

    @Test
    fun `tracer RED fixture - source range from data attributes`() {
        val html = """<p data-an-b="5:1" data-o="10" data-l="10">ABCDE</p>"""
        val blocks = parseNovelBookNativeSection(html)
        val entry = buildNovelBookNativeEntries(
            listOf(NovelBookNativeSection(sectionIndex = 0, blocks = blocks)),
            emptyList(),
        ).filterIsInstance<NovelBookNativeEntry.Block>().single()
        entry.charOffsetBefore shouldBe 10
        entry.blockCharCount shouldBe 10
        entry.sectionCharCount shouldBe 20
    }

    @Test
    fun `N-output fixture - canonical PRE source span shared across displayed rows`() {
        val html = """<pre data-an-b="5:1" data-o="0" data-l="6">AA

BB</pre>"""
        val blocks = parseNovelBookNativeSection(html)
        val entries = buildNovelBookNativeEntries(
            listOf(NovelBookNativeSection(sectionIndex = 0, blocks = blocks)),
            emptyList(),
        ).filterIsInstance<NovelBookNativeEntry.Block>()

        entries.size shouldBe 2
        entries[0].charOffsetBefore shouldBe 0
        entries[0].blockCharCount shouldBe 3
        entries[1].charOffsetBefore shouldBe 3
        entries[1].blockCharCount shouldBe 3
        entries[0].sectionCharCount shouldBe 6
        entries[1].sectionCharCount shouldBe 6
        (entries[0].blockCharCount + entries[1].blockCharCount) shouldBe 6
    }

    @Test
    fun `mixed text and image group has zero span for image without altering total`() {
        val html = """<p data-an-b="5:1" data-o="0" data-l="10">Hello<img src="https://example.com/a.png" />World</p>"""
        val blocks = parseNovelBookNativeSection(html)
        val entries = buildNovelBookNativeEntries(
            listOf(NovelBookNativeSection(sectionIndex = 0, blocks = blocks)),
            emptyList(),
        ).filterIsInstance<NovelBookNativeEntry.Block>()

        entries.size shouldBe 3
        entries[0].blockCharCount shouldBe 5
        entries[1].blockCharCount shouldBe 0
        entries[2].blockCharCount shouldBe 5
        entries.sumOf { it.blockCharCount } shouldBe 10
        entries.first().sectionCharCount shouldBe 10
    }

    @Test
    fun `GAP fixture - seek into hidden title gap targets following paragraph and preserves anchor blockIndex`() {
        val html = """
            <h2 class="nb-title" data-an-b="5:0" data-o="100" data-l="10">Title B</h2>
            <p data-an-b="5:1" data-o="110" data-l="40">Paragraph B</p>
        """.trimIndent()
        val blocks = parseNovelBookNativeSection(html, showChapterHeadings = false)
        blocks.size shouldBe 1
        blocks.single().anchor?.blockIndex shouldBe 1

        val sectionA = NovelBookNativeSection<NovelRichContentBlock>(
            sectionIndex = 0,
            blocks = listOf(
                NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment("A".repeat(100))),
                    bookRange = NovelBookSourceRange(0, 100),
                ),
            ),
            canonicalCharStart = 0,
            canonicalCharCount = 100,
        )
        val sectionB = NovelBookNativeSection(
            sectionIndex = 1,
            blocks = blocks,
            canonicalCharStart = 0,
            canonicalCharCount = 150,
        )
        val entries = buildNovelBookNativeEntries(listOf(sectionA, sectionB), emptyList())

        val target = resolveNovelBookNativeSeekTarget(
            entries = entries,
            request = seekRequestOf(id = 10L, sectionIndex = 1, charOffset = 100),
            lastAppliedSeekId = 0L,
            sectionFraction = 0f,
        )
        target shouldBe NovelBookNativeSeekTarget(seekRequestId = 10L, itemIndex = 1, fraction = 0f)

        val relocateItem = NovelBookNativeViewportItem(
            sectionIndex = 1,
            charOffsetBefore = 110,
            blockCharCount = 40,
            sectionCharCount = 150,
            offsetPx = -50,
            heightPx = 100,
        )
        val location = resolveNovelBookNativeRelocate(listOf(relocateItem))
        location shouldBe NovelBookViewLocation(sectionIndex = 1, sectionFraction = 130f / 150f)
    }

    @Test
    fun `nonzero section base 90 maps source block start 100 to local 10`() {
        val block = NovelRichContentBlock.Paragraph(
            segments = listOf(NovelRichTextSegment("Text")),
            bookRange = NovelBookSourceRange(100, 20),
        )
        val section = NovelBookNativeSection(
            sectionIndex = 1,
            blocks = listOf(block),
            canonicalCharStart = 90,
            canonicalCharCount = 50,
        )
        val entries = buildNovelBookNativeEntries(listOf(section), emptyList())
        val entry = entries.filterIsInstance<NovelBookNativeEntry.Block>().single()
        entry.charOffsetBefore shouldBe 10
        entry.blockCharCount shouldBe 20
        entry.sectionCharCount shouldBe 50
    }
}
