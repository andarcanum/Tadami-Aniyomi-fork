package eu.kanade.presentation.reader.novel

import android.app.Application
import android.graphics.Typeface
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextStyle
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NovelPageReaderSpannableTextTest {

    @Test
    fun `spannable builder preserves rich url annotations as clickable spans`() {
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(text = "Hello "),
                NovelRichTextSegment(text = "link", linkUrl = "https://example.org"),
            ),
        )

        val spannable = buildNovelPageReaderSpannableText(text = annotated)

        val clickableSpans = spannable.getSpans(0, spannable.length, ClickableSpan::class.java)
        assertEquals(1, clickableSpans.size)
    }

    @Test
    fun `spannable url click span dispatches callback`() {
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(text = "Hello "),
                NovelRichTextSegment(text = "link", linkUrl = "https://example.org"),
            ),
        )
        var clickedUrl: String? = null

        val spannable = buildNovelPageReaderSpannableText(
            text = annotated,
            onUrlClick = { clickedUrl = it },
        )

        val clickableSpan = spannable.getSpans(0, spannable.length, ClickableSpan::class.java).single()
        clickableSpan.onClick(TextView(ApplicationProvider.getApplicationContext()))

        assertTrue(clickedUrl == "https://example.org")
    }

    @Test
    fun `reader text view owns layout params before selection is enabled`() {
        val textView = NovelPageReaderTextView(
            context = ApplicationProvider.getApplicationContext(),
            selectionRenderer = NovelSelectedTextRenderer.PAGE_READER,
            selectionSessionIdProvider = { 0L },
            onSelectedTextSelectionChanged = {},
            onPlainTap = null,
            touchHandlingEnabled = true,
            selectionInteractionEnabled = true,
        )

        assertNotNull(textView.layoutParams)
    }

    @Test
    fun `reader text view ignores framework scroll requests`() {
        val textView = NovelPageReaderTextView(
            context = ApplicationProvider.getApplicationContext(),
            selectionRenderer = NovelSelectedTextRenderer.PAGE_READER,
            selectionSessionIdProvider = { 0L },
            onSelectedTextSelectionChanged = {},
            onPlainTap = null,
            touchHandlingEnabled = true,
            selectionInteractionEnabled = true,
        )

        textView.scrollTo(112, 117)
        assertEquals(0, textView.scrollX)
        assertEquals(0, textView.scrollY)

        textView.scrollBy(20, 30)
        assertEquals(0, textView.scrollX)
        assertEquals(0, textView.scrollY)
    }

    @Test
    fun `applyNovelPageReaderTextViewMetrics aligns break strategy and fallback spacing for page mode`() {
        val textView = TextView(ApplicationProvider.getApplicationContext())
        val metrics = resolveNovelPageReaderTextViewMetrics(
            textSizePx = 40f,
            lineSpacingMultiplier = 1.2f,
            typeface = null,
            textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.JUSTIFY,
            shadow = null,
            isPageMode = true,
        )

        applyNovelPageReaderTextViewMetrics(textView, metrics)

        assertEquals(android.text.Layout.BREAK_STRATEGY_SIMPLE, textView.breakStrategy)
        assertEquals(android.text.Layout.HYPHENATION_FREQUENCY_NONE, textView.hyphenationFrequency)
        assertEquals(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD, textView.justificationMode)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            assertEquals(false, textView.isFallbackLineSpacing)
        }
    }

    @Test
    fun `pagination layout breaks lines exactly like the rendering TextView`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(
                    text = "Bold lead ",
                    style = NovelRichTextStyle(bold = true),
                ),
                NovelRichTextSegment(
                    text = "and italic tail ".repeat(6),
                    style = NovelRichTextStyle(italic = true),
                ),
            ),
        )
        val textSizePx = 36f
        val lineHeightMultiplier = 1.2f
        val widthPx = 420
        val firstLineIndentPx = 36
        val spannable = buildNovelPageReaderSpannableText(
            text = annotated,
            firstLineIndentPx = firstLineIndentPx,
        )
        val paginationLayout = buildReaderStaticLayout(
            text = spannable,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.LEFT,
        )
        val textView = TextView(context).apply {
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setText(spannable)
            applyNovelPageReaderTextViewMetrics(
                this,
                resolveNovelPageReaderTextViewMetrics(
                    textSizePx = textSizePx,
                    lineSpacingMultiplier = lineHeightMultiplier,
                    typeface = Typeface.DEFAULT,
                    textAlign = TextAlign.LEFT,
                    shadow = null,
                    isPageMode = true,
                ),
            )
        }
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val displayLayout = requireNotNull(textView.layout)

        assertEquals(paginationLayout.lineCount, displayLayout.lineCount)
        for (line in 0 until paginationLayout.lineCount) {
            assertEquals(paginationLayout.getLineStart(line), displayLayout.getLineStart(line))
            assertEquals(paginationLayout.getLineEnd(line), displayLayout.getLineEnd(line))
        }
    }

    @Test
    fun `applyNovelPageReaderTextViewMetrics preserves high quality break and fallback spacing for scroll mode`() {
        val textView = TextView(ApplicationProvider.getApplicationContext())
        val metrics = resolveNovelPageReaderTextViewMetrics(
            textSizePx = 40f,
            lineSpacingMultiplier = 1.2f,
            typeface = null,
            textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.JUSTIFY,
            shadow = null,
            isPageMode = false,
        )

        applyNovelPageReaderTextViewMetrics(textView, metrics)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            assertEquals(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY, textView.breakStrategy)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            assertEquals(true, textView.isFallbackLineSpacing)
        }
    }
}
