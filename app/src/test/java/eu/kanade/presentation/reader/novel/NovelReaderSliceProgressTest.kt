package eu.kanade.presentation.reader.novel

import android.app.Application
import android.graphics.Typeface
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NovelReaderSliceProgressTest {
    @Test
    fun `whitespace-only layout line advances the cursor`() {
        val text = "\n\nTail"
        val layout = buildReaderStaticLayout(text, 400, 24f, 1.2f, Typeface.DEFAULT, TextAlign.LEFT)
        val lineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
        val slice = resolveStaticLayoutSliceForHeight(text, layout, 0, lineHeight)
        assertNotNull(slice)
        assertEquals(1, requireNotNull(slice).nextStartLine)
    }

    @Test
    fun `leading blank lines do not discard the remainder`() {
        val text = "\n\nTail"
        val ranges = paginateTextIntoPageRanges(text, 400, 1, 24f, 1.2f, Typeface.DEFAULT, TextAlign.LEFT)
        val restored = ranges.joinToString("") { text.substring(it.start, it.endExclusive) }
        assertTrue(restored.contains("Tail"))
        assertTrue(ranges.size <= text.length + 1)
    }
}
