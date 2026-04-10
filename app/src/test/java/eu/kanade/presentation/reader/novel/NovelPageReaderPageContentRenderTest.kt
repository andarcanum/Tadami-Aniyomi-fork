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
}
