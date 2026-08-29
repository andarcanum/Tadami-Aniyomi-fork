package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.book.novel.model.NovelHighlight

class NovelHighlightResolverTest {

    private fun highlight(
        blockIndex: Int = 3,
        charStart: Int = 10,
        charEndExclusive: Int = 20,
        normalizedText: String = "stored snippet",
    ) = NovelHighlight(
        id = 1L,
        novelId = 1L,
        chapterId = 2L,
        blockIndex = blockIndex,
        charStart = charStart,
        charEndExclusive = charEndExclusive,
        normalizedText = normalizedText,
        colorArgb = 4294688813L,
        note = "",
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `exact path paints stored offsets`() {
        val blocks = listOf(
            1 to "before text",
            3 to "prefix stored snippet suffix",
        )

        val resolved = NovelHighlightResolver.resolve(highlight(charStart = 7, charEndExclusive = 21), blocks)

        resolved shouldBe DefaultResolvedNovelHighlight(
            highlight = highlight(charStart = 7, charEndExclusive = 21),
            blockIndex = 3,
            charStart = 7,
            charEndExclusive = 21,
            needsPersist = false,
        )
    }

    @Test
    fun `exact path rejects drifted content and falls back`() {
        // The stored offsets now cover different words, but the snippet still exists later.
        val blocks = listOf(
            3 to "drifted content replaced the old words",
            7 to "find the stored snippet here",
        )

        val resolved = NovelHighlightResolver.resolve(highlight(), blocks)

        resolved!!.blockIndex shouldBe 7
        resolved.charStart shouldBe 9
        resolved.charEndExclusive shouldBe 23
        resolved.needsPersist shouldBe true
    }

    @Test
    fun `fallback finds snippet in another block with messy whitespace`() {
        val blocks = listOf(
            3 to "nothing relevant",
            7 to "double  spaced   stored\nsnippet text",
        )

        val resolved = NovelHighlightResolver.resolve(highlight(), blocks)

        resolved!!.blockIndex shouldBe 7
        resolved.needsPersist shouldBe true
        val raw = blocks.first { it.first == 7 }.second
        raw.substring(resolved.charStart, resolved.charEndExclusive)
            .let { normalizeNovelSelectedText(it) } shouldBe "stored snippet"
    }

    @Test
    fun `fallback skips occupied ranges`() {
        val blocks = listOf(3 to "stored snippet or stored snippet")
        val occupied = setOf(3 to (0 until 14))

        val resolved = NovelHighlightResolver.resolve(highlight(), blocks, occupied)

        resolved!!.charStart shouldBe 18
        resolved.charEndExclusive shouldBe 32
        resolved.needsPersist shouldBe true
    }

    @Test
    fun `miss returns null`() {
        val blocks = listOf(3 to "completely different words")

        NovelHighlightResolver.resolve(highlight(), blocks).shouldBeNull()
    }

    @Test
    fun `parseNovelSelectionAnchor parses valid domId and rejects malformed`() {
        parseNovelSelectionAnchor("12:3", 4, 9) shouldBe
            NovelSelectionAnchor(chapterId = 12L, blockIndex = 3, charStart = 4, charEndExclusive = 9)
        parseNovelSelectionAnchor(null, 4, 9).shouldBeNull()
        parseNovelSelectionAnchor("x:y", 4, 9).shouldBeNull()
        parseNovelSelectionAnchor("12:-1", 4, 9).shouldBeNull()
    }

    @Test
    fun `mapNormalizedRangeToRaw projects through collapsed whitespace`() {
        val raw = "  Hello   world\n\nfoo  "

        mapNormalizedRangeToRaw(raw, 0, 5) shouldBe 2..6
        mapNormalizedRangeToRaw(raw, 6, 11) shouldBe 10..14
        mapNormalizedRangeToRaw(raw, 12, 15) shouldBe 17..19
        mapNormalizedRangeToRaw(raw, -1, 5).shouldBeNull()
        mapNormalizedRangeToRaw(raw, 5, 5).shouldBeNull()
        mapNormalizedRangeToRaw(raw, 0, 99).shouldBeNull()
    }
}
