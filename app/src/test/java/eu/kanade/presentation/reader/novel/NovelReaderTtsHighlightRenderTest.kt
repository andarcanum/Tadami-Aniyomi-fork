package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsWordRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelReaderTtsHighlightRenderTest {

    @Test
    fun `estimated highlight applies style to matching word`() {
        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString("alpha beta gamma"),
            blockText = "alpha beta gamma",
            sourceBlockIndex = 4,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = 4,
                utteranceText = "alpha beta gamma",
                wordRange = NovelTtsWordRange(
                    wordIndex = 1,
                    text = "beta",
                    startChar = 6,
                    endChar = 10,
                ),
                mode = NovelTtsHighlightMode.ESTIMATED,
            ),
            highlightColor = Color.Yellow,
        )

        assertEquals("alpha beta gamma", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.background == Color.Yellow })
    }

    @Test
    fun `highlight off keeps text plain`() {
        val original = AnnotatedString("alpha beta gamma")
        val rendered = applyNovelReaderTtsHighlight(
            text = original,
            blockText = original.text,
            sourceBlockIndex = 0,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = 0,
                utteranceText = original.text,
                wordRange = NovelTtsWordRange(
                    wordIndex = 0,
                    text = "alpha",
                    startChar = 0,
                    endChar = 5,
                ),
                mode = NovelTtsHighlightMode.OFF,
            ),
            highlightColor = Color.Yellow,
        )

        assertEquals(original, rendered)
    }

    @Test
    fun `page aware fallback highlights current page fragment without word offsets`() {
        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString("Delta epsilon zeta."),
            blockText = "Delta epsilon zeta.",
            sourceBlockIndex = 0,
            pageIndex = 1,
            pageBlockTextStart = 18,
            pageBlockTextEndExclusive = 37,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = 0,
                utteranceText = null,
                wordRange = null,
                pageIndex = 1,
                blockTextStart = 18,
                blockTextEndExclusive = 37,
                mode = NovelTtsHighlightMode.ESTIMATED,
            ),
            highlightColor = Color.Yellow,
        )

        assertEquals("Delta epsilon zeta.", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.background == Color.Yellow })
        assertEquals(0, rendered.spanStyles.single().start)
        assertEquals(rendered.text.length, rendered.spanStyles.single().end)
    }

    @Test
    fun `page aware highlight renders the active page fragment for the utterance`() {
        val blockText = "Alpha beta gamma delta epsilon zeta."
        val pageText = "delta epsilon zeta."
        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString(pageText),
            blockText = blockText,
            sourceBlockIndex = 0,
            pageIndex = 1,
            pageBlockTextStart = 17,
            pageBlockTextEndExclusive = blockText.length,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = 0,
                utteranceText = "gamma delta epsilon zeta.",
                wordRange = NovelTtsWordRange(
                    wordIndex = 1,
                    text = "delta",
                    startChar = 6,
                    endChar = 11,
                ),
                pageIndex = 1,
                blockTextStart = 11,
                blockTextEndExclusive = blockText.length,
                mode = NovelTtsHighlightMode.ESTIMATED,
            ),
            highlightColor = Color.Yellow,
        )

        assertEquals(pageText, rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.background == Color.Yellow })
        assertEquals(0, rendered.spanStyles.single().start)
        assertEquals(pageText.length, rendered.spanStyles.single().end)
    }

    @Test
    fun `block offsets highlight the active utterance in native mode without word ranges`() {
        val blockText = "Alpha beta. Gamma delta."
        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString(blockText),
            blockText = blockText,
            sourceBlockIndex = 0,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = 0,
                utteranceText = "Gamma delta.",
                wordRange = null,
                blockTextStart = 12,
                blockTextEndExclusive = 24,
                mode = NovelTtsHighlightMode.ESTIMATED,
            ),
            highlightColor = Color.Yellow,
        )

        assertEquals(blockText, rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.background == Color.Yellow })
        assertEquals(12, rendered.spanStyles.single().start)
        assertEquals(24, rendered.spanStyles.single().end)
    }

    @Test
    fun `word range with block coordinates narrows highlight to the spoken word`() {
        val blockText = "Alpha beta. Gamma delta."
        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString(blockText),
            blockText = blockText,
            sourceBlockIndex = 0,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = 0,
                utteranceText = "Gamma delta.",
                wordRange = NovelTtsWordRange(
                    wordIndex = 1,
                    text = "delta",
                    startChar = 6,
                    endChar = 11,
                    blockStartChar = 18,
                    blockEndCharExclusive = 23,
                ),
                blockTextStart = 12,
                blockTextEndExclusive = 24,
                mode = NovelTtsHighlightMode.EXACT,
            ),
            highlightColor = Color.Yellow,
        )

        assertEquals(blockText, rendered.text)
        // The word chip is the only span carrying an explicit text color; the paragraph backdrop
        // keeps the full highlight alpha while the chip moves over it (V4 palette).
        val wordSpan = rendered.spanStyles.single { it.item.color != Color.Unspecified }
        assertEquals(18, wordSpan.start)
        assertEquals(23, wordSpan.end)
        assertEquals(Color.Yellow, wordSpan.item.background)
        val backdropSpan = rendered.spanStyles.single { it.item.color == Color.Unspecified }
        assertEquals(12, backdropSpan.start)
        assertEquals(24, backdropSpan.end)
        assertEquals(Color.Yellow, backdropSpan.item.background)
    }

    @Test
    fun `word range without block coordinates narrows highlight via utterance position`() {
        val blockText = "alpha beta gamma"
        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString(blockText),
            blockText = blockText,
            sourceBlockIndex = 4,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = 4,
                utteranceText = blockText,
                wordRange = NovelTtsWordRange(
                    wordIndex = 1,
                    text = "beta",
                    startChar = 6,
                    endChar = 10,
                ),
                mode = NovelTtsHighlightMode.ESTIMATED,
            ),
            highlightColor = Color.Yellow,
        )

        val wordSpan = rendered.spanStyles.single { it.item.color != Color.Unspecified }
        assertEquals(6, wordSpan.start)
        assertEquals(10, wordSpan.end)
    }

    @Test
    fun `page aware word range maps into page local coordinates`() {
        val blockText = "Alpha beta gamma delta epsilon zeta."
        val pageText = "delta epsilon zeta."
        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString(pageText),
            blockText = blockText,
            sourceBlockIndex = 0,
            pageIndex = 1,
            pageBlockTextStart = 17,
            pageBlockTextEndExclusive = blockText.length,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = 0,
                utteranceText = "gamma delta epsilon zeta.",
                wordRange = NovelTtsWordRange(
                    wordIndex = 1,
                    text = "delta",
                    startChar = 6,
                    endChar = 11,
                    blockStartChar = 17,
                    blockEndCharExclusive = 22,
                ),
                pageIndex = 1,
                blockTextStart = 11,
                blockTextEndExclusive = blockText.length,
                mode = NovelTtsHighlightMode.EXACT,
            ),
            highlightColor = Color.Yellow,
        )

        val wordSpan = rendered.spanStyles.single { it.item.color != Color.Unspecified }
        assertEquals(0, wordSpan.start)
        assertEquals(5, wordSpan.end)
    }
}
