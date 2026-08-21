package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import eu.kanade.tachiyomi.ui.reader.novel.DefaultResolvedNovelHighlight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.book.novel.model.NovelHighlight

class NovelHighlightPaintTest {

    private fun text(value: String = "hello world"): AnnotatedString = buildAnnotatedString { append(value) }

    private fun resolved(
        blockIndex: Int = 1,
        charStart: Int = 0,
        charEndExclusive: Int = 5,
        colorArgb: Long = 4294688813L,
    ) = DefaultResolvedNovelHighlight(
        highlight = NovelHighlight(
            id = 1L, novelId = 1L, chapterId = 1L, blockIndex = blockIndex,
            charStart = charStart, charEndExclusive = charEndExclusive,
            normalizedText = "hello", colorArgb = colorArgb, note = "",
            createdAt = 0L, updatedAt = 0L,
        ),
        blockIndex = blockIndex,
        charStart = charStart,
        charEndExclusive = charEndExclusive,
        needsPersist = false,
    )

    @Test
    fun `single highlight paints background over its range`() {
        val painted = applyNovelHighlights(text(), blockIndex = 1, resolved = listOf(resolved()))

        val span = painted.spanStyles.single()
        span.start shouldBe 0
        span.end shouldBe 5
        span.item.background shouldBe Color(4294688813L).copy(alpha = HIGHLIGHT_PAINT_ALPHA)
    }

    @Test
    fun `two highlights with different colors produce two spans`() {
        val painted = applyNovelHighlights(
            text(),
            blockIndex = 1,
            resolved = listOf(
                resolved(charStart = 0, charEndExclusive = 5),
                resolved(charStart = 6, charEndExclusive = 11, colorArgb = 0xFF90CAF9),
            ),
        )

        painted.spanStyles.size shouldBe 2
        painted.spanStyles.map { it.start to it.end } shouldBe listOf(0 to 5, 6 to 11)
        painted.spanStyles[1].item.background shouldBe Color(0xFF90CAF9).copy(alpha = HIGHLIGHT_PAINT_ALPHA)
    }

    @Test
    fun `foreign block highlights are ignored`() {
        val source = text()
        val painted = applyNovelHighlights(text(), blockIndex = 2, resolved = listOf(resolved(blockIndex = 1)))

        painted shouldBe source
    }

    @Test
    fun `ranges outside the rendered text are clamped`() {
        val painted = applyNovelHighlights(
            text("hello"),
            blockIndex = 1,
            resolved = listOf(resolved(charStart = -5, charEndExclusive = 99)),
        )

        val span = painted.spanStyles.single()
        span.start shouldBe 0
        span.end shouldBe 5
    }

    @Test
    fun `empty list returns the original instance`() {
        val source = text()

        val painted = applyNovelHighlights(source, blockIndex = 1, resolved = emptyList())

        painted shouldBe source
    }
}
