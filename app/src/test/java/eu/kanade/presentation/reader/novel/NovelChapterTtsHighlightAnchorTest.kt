package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsUtterance
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Chapter mode renders rich blocks that carry `(chapterId, blockIndex)` anchors (written for
 * selection addressing), while the voice publishes an anchor only over a book. The highlight
 * addressing guard refuses to paint an anchored block from a state without an anchor, so chapter
 * rich-native scrolling never highlighted the spoken paragraph. The state builder now publishes the
 * anchor of the rendered block itself whenever the spoken index addresses it.
 */
class NovelChapterTtsHighlightAnchorTest {

    private fun paragraph(text: String, anchor: NovelBlockAnchor?) = NovelRichContentBlock.Paragraph(
        segments = listOf(NovelRichTextSegment(text = text)),
        anchor = anchor,
    )

    private fun utterance(text: String, sourceBlockIndex: Int) = NovelTtsUtterance(
        id = "utterance-0-0",
        segmentId = "segment-0",
        text = text,
        sourceBlockIndex = sourceBlockIndex,
        wordRanges = emptyList(),
    )

    private fun buildState(
        blocks: List<NovelRichContentBlock>,
        activeSourceBlockIndex: Int?,
        activeUtteranceText: String?,
        isBookMode: Boolean = false,
        bookTtsBlockAnchor: NovelBlockAnchor? = null,
    ) = buildNovelReaderTtsHighlightState(
        activeUtterance = activeSourceBlockIndex?.let { utterance(activeUtteranceText.orEmpty(), it) },
        activeSourceBlockIndex = activeSourceBlockIndex,
        activeUtteranceText = activeUtteranceText,
        activeWordRange = null,
        activeHighlightMode = NovelTtsHighlightMode.ESTIMATED,
        isBookMode = isBookMode,
        usePageReader = false,
        pageReaderProgressPageIndex = 0,
        activePageReaderTtsAnchors = emptyMap(),
        bookTtsBlockAnchor = bookTtsBlockAnchor,
        richScrollBlocks = blocks,
    )

    @Test
    fun `chapter mode publishes the anchor of the rendered spoken block`() {
        val blocks = listOf(
            paragraph("First paragraph.", NovelBlockAnchor(chapterId = 77L, blockIndex = 0)),
            paragraph("Hello world of the chapter.", NovelBlockAnchor(chapterId = 77L, blockIndex = 1)),
        )

        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 1,
            activeUtteranceText = "Hello world of the chapter.",
        )

        state.blockAnchor shouldBe NovelBlockAnchor(chapterId = 77L, blockIndex = 1)
    }

    @Test
    fun `the published chapter anchor makes the rich block highlight end to end`() {
        val blockText = "Hello world of the chapter."
        val blocks = listOf(
            paragraph("First paragraph.", NovelBlockAnchor(chapterId = 77L, blockIndex = 0)),
            paragraph(blockText, NovelBlockAnchor(chapterId = 77L, blockIndex = 1)),
        )
        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 1,
            activeUtteranceText = blockText,
        )

        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString(blockText),
            blockText = blockText,
            sourceBlockIndex = 1,
            highlightState = state,
            highlightColor = Color.Yellow,
            blockAnchor = NovelBlockAnchor(chapterId = 77L, blockIndex = 1),
        )

        rendered.spanStyles.any { it.item.background == Color.Yellow } shouldBe true
    }

    @Test
    fun `a different anchored block is still not painted`() {
        val blocks = listOf(
            paragraph("First paragraph.", NovelBlockAnchor(chapterId = 77L, blockIndex = 0)),
            paragraph("Hello world of the chapter.", NovelBlockAnchor(chapterId = 77L, blockIndex = 1)),
        )
        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 1,
            activeUtteranceText = "Hello world of the chapter.",
        )

        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString("First paragraph."),
            blockText = "First paragraph.",
            sourceBlockIndex = 0,
            highlightState = state,
            highlightColor = Color.Yellow,
            blockAnchor = NovelBlockAnchor(chapterId = 77L, blockIndex = 0),
        )

        rendered.spanStyles.any { it.item.background == Color.Yellow } shouldBe false
    }

    @Test
    fun `book mode keeps priority of the voice-published anchor`() {
        val bookAnchor = NovelBlockAnchor(chapterId = 12L, blockIndex = 5)
        val blocks = listOf(paragraph("Hello world.", NovelBlockAnchor(chapterId = 77L, blockIndex = 0)))

        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 0,
            activeUtteranceText = "Hello world.",
            isBookMode = true,
            bookTtsBlockAnchor = bookAnchor,
        )

        state.blockAnchor shouldBe bookAnchor
    }

    @Test
    fun `book mode without a published anchor never borrows the chapter logic`() {
        val blocks = listOf(paragraph("Hello world.", NovelBlockAnchor(chapterId = 12L, blockIndex = 0)))

        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 0,
            activeUtteranceText = "Hello world.",
            isBookMode = true,
            bookTtsBlockAnchor = null,
        )

        state.blockAnchor shouldBe null
    }

    @Test
    fun `an orphan block without anchor stays on index addressing`() {
        val blocks = listOf(paragraph("Hello world.", anchor = null))

        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 0,
            activeUtteranceText = "Hello world.",
        )

        state.blockAnchor shouldBe null
        state.sourceBlockIndex shouldBe 0
    }

    @Test
    fun `a translated model index pointing at the wrong block is rejected`() {
        // Translated TTS numbers plain content blocks, not rich blocks; the block at the spoken
        // index then holds different text and its anchor must not be adopted.
        val blocks = listOf(
            paragraph("Первый абзац.", NovelBlockAnchor(chapterId = 77L, blockIndex = 0)),
            paragraph("Второй абзац.", NovelBlockAnchor(chapterId = 77L, blockIndex = 1)),
        )

        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 1,
            activeUtteranceText = "Some translated sentence that is not in the block.",
        )

        state.blockAnchor shouldBe null
    }

    @Test
    fun `whitespace differences between utterance and block do not break the match`() {
        val blocks = listOf(
            paragraph("Hello\n   world of  the chapter.", NovelBlockAnchor(chapterId = 77L, blockIndex = 0)),
        )

        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 0,
            activeUtteranceText = "Hello world of the chapter.",
        )

        state.blockAnchor shouldBe NovelBlockAnchor(chapterId = 77L, blockIndex = 0)
    }

    @Test
    fun `the chapter title utterance at index -1 publishes nothing`() {
        val blocks = listOf(paragraph("Hello world.", NovelBlockAnchor(chapterId = 77L, blockIndex = 0)))

        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = -1,
            activeUtteranceText = "Chapter 1",
        )

        state.blockAnchor shouldBe null
    }

    @Test
    fun `an index outside the rendered list publishes nothing`() {
        val blocks = listOf(paragraph("Hello world.", NovelBlockAnchor(chapterId = 77L, blockIndex = 0)))

        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 9,
            activeUtteranceText = "Hello world.",
        )

        state.blockAnchor shouldBe null
    }

    @Test
    fun `long utterances match on their normalized prefix`() {
        val longText = buildString {
            repeat(30) { append("sentence$it. ") }
        }
        val blocks = listOf(
            paragraph(longText.trim(), NovelBlockAnchor(chapterId = 77L, blockIndex = 0)),
        )

        val state = buildState(
            blocks = blocks,
            activeSourceBlockIndex = 0,
            activeUtteranceText = longText.trim(),
        )

        state.blockAnchor shouldBe NovelBlockAnchor(chapterId = 77L, blockIndex = 0)
    }
}
