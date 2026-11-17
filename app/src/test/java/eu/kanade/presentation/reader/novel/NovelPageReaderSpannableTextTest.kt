package eu.kanade.presentation.reader.novel

import android.app.Application
import android.graphics.Paint
import android.graphics.Typeface
import android.text.BoringLayout
import android.text.DynamicLayout
import android.text.Layout
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextStyle
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
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
        assertEquals(android.text.Layout.JUSTIFICATION_MODE_NONE, textView.justificationMode)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            assertEquals(false, textView.isFallbackLineSpacing)
        }

        val leftMetrics = metrics.copy(textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.LEFT)
        applyNovelPageReaderTextViewMetrics(textView, leftMetrics)
        assertEquals(android.text.Layout.BREAK_STRATEGY_SIMPLE, textView.breakStrategy)
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
            includeFontPadding = true
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
            assertEquals(android.text.Layout.BREAK_STRATEGY_SIMPLE, textView.breakStrategy)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            assertEquals(true, textView.isFallbackLineSpacing)
        }
    }

    @Test
    fun `resolveReaderMeasurementTextPaint enables subpixel flag and copies TextView paint`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sourcePaint = TextPaint().apply {
            set(TextView(context).paint)
            flags = flags or Paint.FILTER_BITMAP_FLAG
            linkColor = 0xFF123456.toInt()
            baselineShift = 7
        }

        val derived = resolveReaderMeasurementTextPaint(
            textSizePx = 49f,
            typeface = Typeface.SERIF,
            density = 3.5f,
            basePaint = sourcePaint,
        )
        assertTrue((derived.flags and Paint.SUBPIXEL_TEXT_FLAG) != 0)
        assertTrue((derived.flags and Paint.FILTER_BITMAP_FLAG) != 0)
        assertEquals(0xFF123456.toInt(), derived.linkColor)
        assertEquals(7, derived.baselineShift)
        assertEquals(49f, derived.textSize)

        val fallback = resolveReaderMeasurementTextPaint(
            textSizePx = 49f,
            typeface = Typeface.DEFAULT,
            density = 1f,
            basePaint = null,
        )
        assertTrue((fallback.flags and Paint.SUBPIXEL_TEXT_FLAG) != 0)
        assertTrue(fallback.isAntiAlias)
    }

    @Test
    fun `buildReaderTextLayout selects DynamicLayout only when selectable and matches non-selectable TextView`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val basePaint = TextPaint().apply {
            set(TextView(context).paint)
        }
        val text = "Single fitting line."

        val selectableLayout = buildReaderTextLayout(
            text = text,
            widthPx = 1148,
            textSizePx = 49f,
            lineHeightMultiplier = 1.6f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.JUSTIFY,
            measurementPaint = basePaint,
            preferDynamicLayout = true,
        )
        assertTrue(selectableLayout is DynamicLayout)

        val nonSelectableSingleLine = buildReaderTextLayout(
            text = text,
            widthPx = 1148,
            textSizePx = 49f,
            lineHeightMultiplier = 1.6f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.JUSTIFY,
            measurementPaint = basePaint,
            preferDynamicLayout = false,
        )
        assertTrue(nonSelectableSingleLine !is DynamicLayout)
        val boringPaint = resolveReaderMeasurementTextPaint(
            textSizePx = 49f,
            typeface = Typeface.DEFAULT,
            basePaint = basePaint,
        )
        if (BoringLayout.isBoring(text, boringPaint) != null) {
            assertTrue(nonSelectableSingleLine is BoringLayout)
        }

        val multiLineText = "First line.\nSecond line."
        val textView = TextView(context).apply {
            includeFontPadding = true
            setPadding(0, 0, 0, 0)
            setTextIsSelectable(false)
            setText(AnnotatedString(multiLineText).text)
            applyNovelPageReaderTextViewMetrics(
                this,
                resolveNovelPageReaderTextViewMetrics(
                    textSizePx = 49f,
                    lineSpacingMultiplier = 1.6f,
                    typeface = Typeface.DEFAULT,
                    textAlign = TextAlign.JUSTIFY,
                    shadow = null,
                    isPageMode = true,
                ),
            )
        }
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(1148, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val nonSelectableMultiLine = buildReaderTextLayout(
            text = multiLineText,
            widthPx = 1148,
            textSizePx = 49f,
            lineHeightMultiplier = 1.6f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.JUSTIFY,
            measurementPaint = basePaint,
            preferDynamicLayout = false,
        )
        assertEquals(textView.layout.lineCount, nonSelectableMultiLine.lineCount)
    }

    @Test
    fun `hyphenation enables high-quality break strategy, normal hyphenation, and script locale`() {
        val textView = TextView(ApplicationProvider.getApplicationContext())
        val latinText = "Soon enough five of the so-called barbarians come out of the forest."
        val cyrillicText = "Вскоре из леса вышли пятеро так называемых варваров."

        assertEquals(
            Locale.US,
            resolveNovelReaderTextLocale(latinText, fallback = Locale.forLanguageTag("ru-RU")),
        )
        assertEquals(
            Locale.forLanguageTag("ru"),
            resolveNovelReaderTextLocale(cyrillicText, fallback = Locale.US),
        )

        val hyphenLocale = resolveNovelReaderTextLocale(latinText, fallback = Locale.forLanguageTag("ru-RU"))
        val metrics = resolveNovelPageReaderTextViewMetrics(
            textSizePx = 36f,
            lineSpacingMultiplier = 1.4f,
            typeface = null,
            textAlign = TextAlign.JUSTIFY,
            shadow = null,
            isPageMode = true,
            hyphenationEnabled = true,
            textLocale = hyphenLocale,
        )
        applyNovelPageReaderTextViewMetrics(textView, metrics)

        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, textView.breakStrategy)
        assertEquals(Layout.HYPHENATION_FREQUENCY_NONE, textView.hyphenationFrequency)
        assertEquals(Layout.JUSTIFICATION_MODE_NONE, textView.justificationMode)
        assertEquals(Locale.US, textView.textLocale)

        val staticLayout = buildReaderStaticLayout(
            text = latinText,
            widthPx = 420,
            textSizePx = 36f,
            lineHeightMultiplier = 1.4f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.JUSTIFY,
            hyphenationEnabled = true,
            textLocale = hyphenLocale,
        )
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, textView.breakStrategy)
        assertTrue(staticLayout.height > 0)

        val leftMetrics = metrics.copy(textAlign = TextAlign.LEFT)
        applyNovelPageReaderTextViewMetrics(textView, leftMetrics)
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, textView.breakStrategy)
        assertEquals(Layout.HYPHENATION_FREQUENCY_NONE, textView.hyphenationFrequency)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [35])
    fun `applyJustifySpacing widens non-final lines to right edge in TextView without changing line breaks`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val paragraph =
            "They are made from wood, going by the color it's almost certainly the same kind of " +
                "wood I am currently standing on. The balconies are decorated with jade and gold, " +
                "while the rooftops appear to be tiled with solid jade."
        val widthPx = 1182
        val textSizePx = 56f
        val lineHeightMultiplier = 1.4f
        val spannable = buildNovelPageReaderSpannableText(
            text = AnnotatedString(paragraph),
            firstLineIndentPx = 112,
        )

        val leftResult = resolveWidthHyphenatedReaderTextAndLayout(
            source = spannable,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.LEFT,
            firstLineIndentPx = null,
            hyphenationEnabled = true,
            preferDynamicLayout = false,
            isPageMode = true,
            applyJustifySpacing = false,
        )
        val justifyResult = resolveWidthHyphenatedReaderTextAndLayout(
            source = spannable,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.JUSTIFY,
            firstLineIndentPx = null,
            hyphenationEnabled = true,
            preferDynamicLayout = false,
            isPageMode = true,
            applyJustifySpacing = true,
        )

        assertEquals(leftResult.layout.lineCount, justifyResult.layout.lineCount)
        for (line in 0 until leftResult.layout.lineCount) {
            assertEquals(leftResult.layout.getLineStart(line), justifyResult.layout.getLineStart(line))
        }

        val textView = TextView(context).apply {
            includeFontPadding = true
            setPadding(0, 0, 0, 0)
            applyNovelPageReaderTextViewMetrics(
                this,
                resolveNovelPageReaderTextViewMetrics(
                    textSizePx = textSizePx,
                    lineSpacingMultiplier = lineHeightMultiplier,
                    typeface = Typeface.DEFAULT,
                    textAlign = TextAlign.JUSTIFY,
                    shadow = null,
                    isPageMode = true,
                    hyphenationEnabled = true,
                ),
            )
            text = justifyResult.prepared.text
        }
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val displayLayout = requireNotNull(textView.layout)
        val spanned = justifyResult.prepared.text as android.text.Spanned
        val spaceSpans = spanned.getSpans(0, spanned.length, JustifySpaceSpan::class.java)
        assertTrue(spaceSpans.isNotEmpty(), "Expected JustifySpaceSpan instances on justified spaces")

        assertEquals(leftResult.layout.lineCount, displayLayout.lineCount)
        for (line in 0 until displayLayout.lineCount - 1) {
            val naturalMax = leftResult.layout.getLineMax(line)
            val justifiedMax = displayLayout.getLineMax(line)
            assertTrue(
                justifiedMax > naturalMax + 2f,
                "Line $line did not widen in TextView: natural=$naturalMax justified=$justifiedMax",
            )
            assertTrue(
                justifiedMax <= widthPx.toFloat(),
                "Line $line overflowed widthPx=$widthPx: justified=$justifiedMax",
            )
            assertTrue(
                justifiedMax >= widthPx - 5f,
                "Line $line stopped short of right margin: justified=$justifiedMax widthPx=$widthPx",
            )
        }
        assertEquals(leftResult.layout.height, displayLayout.height)

        // Verify hyphenated justify parity on SDK 35 (where TextView defaults useBoundsForWidth = true).
        val hyphenSample =
            "feeding the barely awake Lin we will continue. I stare at her pale hands. \"Are you a herbalist?\""
        val hyphenSpannable = buildNovelPageReaderSpannableText(
            text = AnnotatedString(hyphenSample),
            firstLineIndentPx = null,
        )
        val hyphenLeft = resolveWidthHyphenatedReaderTextAndLayout(
            source = hyphenSpannable,
            widthPx = 1176,
            textSizePx = 56f,
            lineHeightMultiplier = 1.4f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.LEFT,
            hyphenationEnabled = true,
            isPageMode = true,
            applyJustifySpacing = false,
        )
        val hyphenJust = resolveWidthHyphenatedReaderTextAndLayout(
            source = hyphenSpannable,
            widthPx = 1176,
            textSizePx = 56f,
            lineHeightMultiplier = 1.4f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.JUSTIFY,
            hyphenationEnabled = true,
            isPageMode = true,
            applyJustifySpacing = true,
        )
        val hyphenTv = TextView(context).apply {
            includeFontPadding = true
            setPadding(0, 0, 0, 0)
            applyNovelPageReaderTextViewMetrics(
                this,
                resolveNovelPageReaderTextViewMetrics(
                    textSizePx = 56f,
                    lineSpacingMultiplier = 1.4f,
                    typeface = Typeface.DEFAULT,
                    textAlign = TextAlign.JUSTIFY,
                    shadow = null,
                    isPageMode = true,
                    hyphenationEnabled = true,
                ),
            )
            text = hyphenJust.prepared.text
        }
        hyphenTv.measure(
            View.MeasureSpec.makeMeasureSpec(1176, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val tvLayout = requireNotNull(hyphenTv.layout)
        val tvLines = (0 until tvLayout.lineCount).joinToString(" | ") { l ->
            val sub = tvLayout.text.subSequence(tvLayout.getLineStart(l), tvLayout.getLineEnd(l)).toString()
            "L$l='$sub'(max=${tvLayout.getLineMax(l)})"
        }
        assertEquals(hyphenLeft.layout.lineCount, tvLayout.lineCount, "SDK35 lineCount mismatch: $tvLines")
        for (l in 0 until hyphenLeft.layout.lineCount) {
            assertEquals(
                hyphenLeft.layout.getLineStart(l),
                tvLayout.getLineStart(l),
                "SDK35 line $l start shifted! $tvLines",
            )
        }
        val tvLineEndSet = (0 until tvLayout.lineCount - 1).map { tvLayout.getLineEnd(it) }.toSet()
        hyphenJust.prepared.syntheticHyphenDisplayOffsets?.forEachIndexed { idx, isSynthetic ->
            if (isSynthetic && tvLayout.text[idx] == '-') {
                val atLineEnd = (idx + 1) in tvLineEndSet || (idx + 2) in tvLineEndSet
                assertTrue(atLineEnd, "Synthetic hyphen at $idx wrapped mid-line in TextView! $tvLines")
            }
        }

        // Also check DynamicLayout (selectable TextView) on SDK 35 keeps exact parity.
        val dynJust = resolveWidthHyphenatedReaderTextAndLayout(
            source = hyphenSpannable,
            widthPx = 1176,
            textSizePx = 56f,
            lineHeightMultiplier = 1.4f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.JUSTIFY,
            preferDynamicLayout = true,
            hyphenationEnabled = true,
            isPageMode = true,
            applyJustifySpacing = true,
        )
        assertEquals(hyphenLeft.layout.lineCount, dynJust.layout.lineCount)
        for (l in 0 until hyphenLeft.layout.lineCount) {
            assertEquals(hyphenLeft.layout.getLineStart(l), dynJust.layout.getLineStart(l))
        }
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `selectable TextView keeps SDK35 justify hyphen parity across font sizes`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val samples = listOf(
            null to "feeding the barely awake Lin we will continue. I stare at her pale hands. " +
                "\"Are you a herbalist?\"",
            40 to "She shakes her head. \"No, I am\u2026 was a merchant's daughter. I helped my father " +
                "with his accounting, herbalism and medicine is just something I like to read on.\"",
            40 to "Anyone who knows basic medicine knows how to make poison. Bao plucks a petal " +
                "from the flower. \"We are never going to see our homes again, are we?\"",
            40 to "I stay still for a second, before shaking my head. \"We don't know for sure. " +
                "If we manage to succeed there's nothing indicating we wouldn't be allowed to travel.\"",
        )
        val failures = mutableListOf<String>()
        for (sizeSteps in 96..136) {
            val textSizePx = sizeSteps * 0.5f
            for ((idx, pair) in samples.withIndex()) {
                val (indentPx, body) = pair
                val tv = TextView(context).apply {
                    includeFontPadding = true
                    setPadding(0, 0, 0, 0)
                    setTextIsSelectable(true)
                    applyNovelPageReaderTextViewMetrics(
                        this,
                        resolveNovelPageReaderTextViewMetrics(
                            textSizePx = textSizePx,
                            lineSpacingMultiplier = 1.4f,
                            typeface = Typeface.DEFAULT,
                            textAlign = TextAlign.JUSTIFY,
                            shadow = null,
                            isPageMode = true,
                            hyphenationEnabled = true,
                        ),
                    )
                }
                val baseSpannable = buildNovelPageReaderSpannableText(
                    text = AnnotatedString(body),
                    firstLineIndentPx = indentPx,
                )
                val result = resolveWidthHyphenatedReaderTextAndLayout(
                    source = baseSpannable,
                    widthPx = 1176,
                    textSizePx = textSizePx,
                    lineHeightMultiplier = 1.4f,
                    typeface = Typeface.DEFAULT,
                    textAlign = TextAlign.JUSTIFY,
                    firstLineIndentPx = null,
                    density = context.resources.displayMetrics.density,
                    measurementPaint = tv.paint,
                    preferDynamicLayout = true,
                    hyphenationEnabled = true,
                    isPageMode = true,
                    applyJustifySpacing = true,
                )
                tv.text = result.prepared.text
                tv.measure(
                    View.MeasureSpec.makeMeasureSpec(1176, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                val tvLayout = requireNotNull(tv.layout)
                assertTrue(tv.paint.isSubpixelText, "TextView paint must enable subpixel text to match measurement")
                assertTrue(tv.useBoundsForWidth, "SDK35 TextView must use bounds for width")
                if (hasMidLineSyntheticHyphen(tvLayout, result.prepared)) {
                    val linesDump = (0 until tvLayout.lineCount).joinToString(" | ") { l ->
                        val sub = tvLayout.text.subSequence(tvLayout.getLineStart(l), tvLayout.getLineEnd(l))
                        "L$l='$sub'"
                    }
                    failures += "sz=$textSizePx sample=$idx midHyphen: $linesDump"
                }
                if (!doLayoutsMatchLineStarts(result.layout, tvLayout)) {
                    val tvDump = (0 until tvLayout.lineCount).joinToString(" | ") { l ->
                        "L$l=${tvLayout.getLineStart(l)}..${tvLayout.getLineEnd(l)}"
                    }
                    val resDump = (0 until result.layout.lineCount).joinToString(" | ") { l ->
                        "L$l=${result.layout.getLineStart(l)}..${result.layout.getLineEnd(l)}"
                    }
                    failures += "sz=$textSizePx sample=$idx mismatch tv=[$tvDump] dyn=[$resDump]"
                }
            }
        }
        assertTrue(failures.isEmpty(), "Failures (${failures.size}):\n${failures.take(10).joinToString("\n")}")
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `NovelPageReaderTextView probe reconciles narrower TextView layout without mid-line hyphens`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val source = buildNovelPageReaderSpannableText(
            text = AnnotatedString(
                "feeding the barely awake Lin we will continue. I stare at her pale hands. " +
                    "\"Are you a herbalist?\"",
            ),
            firstLineIndentPx = null,
        )
        val metrics = resolveNovelPageReaderTextViewMetrics(
            textSizePx = 56f,
            lineSpacingMultiplier = 1.4f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.JUSTIFY,
            shadow = null,
            isPageMode = true,
            hyphenationEnabled = true,
        )
        val hostTv = NovelPageReaderTextView(
            context = context,
            selectionRenderer = eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer.PAGE_READER,
            selectionSessionIdProvider = { 0L },
            onSelectedTextSelectionChanged = {},
            onPlainTap = null,
            touchHandlingEnabled = false,
            selectionInteractionEnabled = true,
        ).apply {
            applyNovelPageReaderTextViewMetrics(this, metrics)
        }
        // Emulate an OEM ROM whose TextView renders ~8px narrower than standalone DynamicLayout.
        val reconciled = try {
            resolveWidthHyphenatedReaderTextAndLayout(
                source = source,
                widthPx = 1176,
                textSizePx = 56f,
                lineHeightMultiplier = 1.4f,
                typeface = Typeface.DEFAULT,
                textAlign = TextAlign.JUSTIFY,
                firstLineIndentPx = null,
                measurementPaint = hostTv.paint,
                preferDynamicLayout = true,
                hyphenationEnabled = true,
                isPageMode = true,
                applyJustifySpacing = true,
                layoutBuilderOverride = { candidate ->
                    hostTv.measureLayoutWithProbe(
                        candidateText = candidate,
                        widthPx = 1168,
                        metrics = metrics,
                        selectionEnabled = true,
                        bottomPadPx = 0,
                    )
                },
            )
        } finally {
            hostTv.clearLayoutProbe()
        }
        val verifyTv = TextView(context).apply {
            includeFontPadding = true
            setPadding(0, 0, 0, 0)
            setTextIsSelectable(true)
            applyNovelPageReaderTextViewMetrics(this, metrics)
            text = reconciled.prepared.text
        }
        verifyTv.measure(
            View.MeasureSpec.makeMeasureSpec(1168, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val verifyLayout = requireNotNull(verifyTv.layout)
        assertTrue(
            !hasMidLineSyntheticHyphen(verifyLayout, reconciled.prepared),
            "Reconciled layout must keep all synthetic hyphens at actual TextView line ends",
        )
        assertTrue(
            doLayoutsMatchLineStarts(reconciled.layout, verifyLayout),
            "Reconciled layout line starts must match probing TextView",
        )
    }

    @Test
    fun `renderedTextSignature updates when toggling align even with hyphenation disabled`() {
        val sample = AnnotatedString("Some paragraph text.")
        val leftSig = resolveNovelPageReaderRenderedTextSignature(
            text = sample,
            firstLineIndentPx = 40,
            forcedTypefaceStyle = Typeface.NORMAL,
            hyphenationEnabled = false,
            availableWidthPx = 1182,
            textSizePx = 56f,
            lineSpacingMultiplier = 1.4f,
            textAlign = TextAlign.LEFT,
        )
        val justifySig = resolveNovelPageReaderRenderedTextSignature(
            text = sample,
            firstLineIndentPx = 40,
            forcedTypefaceStyle = Typeface.NORMAL,
            hyphenationEnabled = false,
            availableWidthPx = 1182,
            textSizePx = 56f,
            lineSpacingMultiplier = 1.4f,
            textAlign = TextAlign.JUSTIFY,
        )
        assertTrue(leftSig != justifySig)
    }
}
