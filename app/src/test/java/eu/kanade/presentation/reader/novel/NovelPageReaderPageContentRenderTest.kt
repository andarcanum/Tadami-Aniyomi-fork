package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NovelPageReaderPageContentRenderTest {

    @Test
    fun `render signature changes when highlight spans change but text stays the same`() {
        val plain = AnnotatedString("alpha beta gamma")
        val highlighted = buildAnnotatedString {
            append("alpha beta gamma")
            addStyle(
                style = SpanStyle(background = Color.Yellow),
                start = 6,
                end = 10,
            )
        }

        val plainSignature = resolveNovelPageReaderRenderedTextSignature(
            text = plain,
            firstLineIndentPx = null,
            forcedTypefaceStyle = Typeface.NORMAL,
        )
        val highlightedSignature = resolveNovelPageReaderRenderedTextSignature(
            text = highlighted,
            firstLineIndentPx = null,
            forcedTypefaceStyle = Typeface.NORMAL,
        )

        assertEquals(plain.text, highlighted.text)
        assertNotEquals(plainSignature, highlightedSignature)
    }

    @Test
    fun `buildNovelPageRenderItems merges contiguous plain and rich text blocks around rules and images`() {
        val blocks = listOf(
            NovelPageContentBlock.Rich(
                sourceBlockIndex = 0,
                text = AnnotatedString("Chapter Title"),
                spacingBeforePx = 0,
                firstLineIndentEm = null,
                isChapterTitle = true,
            ),
            NovelPageContentBlock.Plain(
                sourceBlockIndex = 1,
                text = "First paragraph.",
                spacingBeforePx = 12,
                firstLineIndentEm = 2f,
            ),
            NovelPageContentBlock.Plain(
                sourceBlockIndex = 2,
                text = "Second paragraph.",
                spacingBeforePx = 12,
                firstLineIndentEm = 2f,
            ),
            NovelPageContentBlock.Rich(
                sourceBlockIndex = 3,
                text = AnnotatedString("* * *"),
                spacingBeforePx = 16,
                firstLineIndentEm = null,
                isHorizontalRule = true,
            ),
            NovelPageContentBlock.Plain(
                sourceBlockIndex = 4,
                text = "After rule.",
                spacingBeforePx = 16,
                firstLineIndentEm = 2f,
            ),
            NovelPageContentBlock.Image(
                imageUrl = "https://example.com/art.jpg",
                contentDescription = null,
                spacingBeforePx = 20,
            ),
            NovelPageContentBlock.Plain(
                sourceBlockIndex = 5,
                text = "After image.",
                spacingBeforePx = 20,
                firstLineIndentEm = 2f,
            ),
        )

        val items = buildNovelPageRenderItems(blocks)

        assertEquals(5, items.size)
        val firstRun = items[0] as NovelPageRenderItem.TextRun
        assertEquals(
            listOf(0, 1, 2),
            firstRun.blocks.map {
                when (it) {
                    is NovelPageContentBlock.Plain -> it.sourceBlockIndex
                    is NovelPageContentBlock.Rich -> it.sourceBlockIndex
                    is NovelPageContentBlock.Image -> -1
                }
            },
        )
        assertEquals(NovelPageRenderItem.HorizontalRule::class.java, items[1]::class.java)
        assertEquals(NovelPageRenderItem.TextRun::class.java, items[2]::class.java)
        assertEquals(NovelPageRenderItem.Image::class.java, items[3]::class.java)
        assertEquals(NovelPageRenderItem.TextRun::class.java, items[4]::class.java)
    }
}
