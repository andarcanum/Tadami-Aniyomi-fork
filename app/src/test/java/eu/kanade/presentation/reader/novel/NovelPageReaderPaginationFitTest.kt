package eu.kanade.presentation.reader.novel

import android.app.Application
import android.graphics.Typeface
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.view.View
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Page-packing contract of the plain paginator: every accepted slice, measured standalone through
 * the same spannable + StaticLayout pipeline the page-mode TextView uses, must fit the page budget
 * together with its spacing and glyph padding, and the accepted ranges must tile every source
 * block without loss or duplication.
 *
 * Harness limit (see project memory): this Robolectric environment never wraps StaticLayout text
 * (glyph advances measure ~1px/char), so multi-page coverage is built from many one-line blocks
 * instead of one long paragraph. Line-break parity, walk-back truncation and maximality of page
 * fill therefore need on-device verification and are intentionally not asserted here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NovelPageReaderPaginationFitTest {

    private val widthPx = 420
    private val pageHeightPx = 600
    private val textSizePx = 36f
    private val lineHeightMultiplier = 1.2f
    private val paragraphSpacingPx = 12
    private val glyphPadPx = resolvePageReaderGlyphOverflowPaddingPx(textSizePx)
    private val firstLineIndentPx = (
        textSizePx.coerceAtLeast(1f) * FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
        )
        .roundToInt()
        .coerceAtLeast(1)

    private fun paginate(
        blocks: List<PlainPageReaderTextBlock>,
        bionic: Boolean = false,
        forcedStyle: Int = Typeface.NORMAL,
        indent: Boolean = false,
    ): List<List<PlainPageSlice>> {
        return paginatePlainPageBlocks(
            textBlocks = blocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = pageHeightPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = null,
            textAlign = TextAlign.LEFT,
            forceParagraphIndent = indent,
            bionicReadingEnabled = bionic,
            forcedTypefaceStyle = forcedStyle,
        )
    }

    /** Measures a slice through the same spannable + StaticLayout pipeline the render path uses. */
    private fun measureSliceHeight(
        sliceText: String,
        sliceStart: Int,
        forcedStyle: Int,
        bionic: Boolean,
        indent: Boolean,
    ): Int {
        val annotated = if (bionic) toBionicText(sliceText) else AnnotatedString(sliceText)
        val spannable = buildNovelPageReaderSpannableText(
            text = annotated,
            firstLineIndentPx = if (indent && sliceStart == 0) firstLineIndentPx else null,
            forcedTypefaceStyle = forcedStyle,
        )
        assertEquals(sliceText.length, spannable.length, "styled spannable must keep slice offsets")
        return measurePlainPageSliceStandaloneHeight(
            sliceText = spannable,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
    }

    private fun measurePageUsage(
        page: List<PlainPageSlice>,
        blocks: List<PlainPageReaderTextBlock>,
        forcedStyle: Int,
        bionic: Boolean,
        indent: Boolean,
    ): Int {
        return page.sumOf { slice ->
            when (slice) {
                is PlainPageSlice.Text -> {
                    val block = blocks.first { it.sourceBlockIndex == slice.blockIndex }
                    val sliceText = block.text.substring(slice.range.start, slice.range.endExclusive)
                    slice.spacingBeforePx + measureSliceHeight(
                        sliceText = sliceText,
                        sliceStart = slice.range.start,
                        forcedStyle = forcedStyle,
                        bionic = bionic,
                        indent = indent,
                    ) + glyphPadPx
                }
                is PlainPageSlice.Image -> slice.spacingBeforePx
            }
        }
    }

    private fun textSlices(
        pages: List<List<PlainPageSlice>>,
        blockIndex: Int,
    ): List<PlainPageSlice.Text> {
        return pages.asSequence()
            .flatten()
            .filterIsInstance<PlainPageSlice.Text>()
            .filter { it.blockIndex == blockIndex }
            .toList()
    }

    private fun shortBlocks(count: Int): List<PlainPageReaderTextBlock> {
        return (0 until count).map { index ->
            PlainPageReaderTextBlock(index, "Paragraph number $index keeps one short line.")
        }
    }

    @Test
    fun `every page fits its budget with render-equivalent slice measurement`() {
        val blocks = shortBlocks(40) + listOf(
            PlainPageReaderTextBlock(40, "The paginator must never trust a single measurement. ".repeat(24)),
        )
        val pages = paginate(
            blocks = blocks,
            bionic = true,
            forcedStyle = Typeface.BOLD,
            indent = true,
        )

        assertTrue(pages.size > 1, "expected a multi-page chapter, got ${pages.size} page(s)")
        pages.forEachIndexed { pageIndex, page ->
            assertTrue(page.isNotEmpty(), "page $pageIndex is empty")
            val used = measurePageUsage(
                page = page,
                blocks = blocks,
                forcedStyle = Typeface.BOLD,
                bionic = true,
                indent = true,
            )
            assertTrue(
                used <= pageHeightPx,
                "page $pageIndex overflows: used=$used budget=$pageHeightPx",
            )
        }
    }

    @Test
    fun `page ranges tile every source block without loss or duplication`() {
        val blocks = shortBlocks(40) + listOf(
            PlainPageReaderTextBlock(40, "Ranges must cover the whole block text. ".repeat(30)),
            PlainPageReaderTextBlock(41, "No character may appear twice or vanish. ".repeat(22)),
        )
        val pages = paginate(
            blocks = blocks,
            bionic = true,
            forcedStyle = Typeface.BOLD,
            indent = true,
        )

        blocks.forEach { block ->
            val slices = textSlices(pages, block.sourceBlockIndex)
            assertTrue(slices.isNotEmpty(), "block ${block.sourceBlockIndex} lost every slice")
            var cursor = 0
            slices.forEachIndexed { index, slice ->
                val start = slice.range.start
                val end = slice.range.endExclusive
                assertTrue(
                    start >= cursor,
                    "block ${block.sourceBlockIndex} slice $index starts before the previous end",
                )
                if (start > cursor) {
                    assertTrue(
                        block.text.substring(cursor, start).isBlank(),
                        "block ${block.sourceBlockIndex} slice $index skips non-whitespace text",
                    )
                }
                assertTrue(end > start, "block ${block.sourceBlockIndex} slice $index is empty")
                cursor = end
            }
            assertTrue(
                cursor == block.text.length || block.text.substring(cursor).isBlank(),
                "block ${block.sourceBlockIndex} loses its tail after offset $cursor",
            )
        }
    }

    @Test
    fun `long block stays covered when it does not fit the page budget`() {
        val longText = "A long block must survive pagination without losing its tail. ".repeat(20)
        val blocks = listOf(PlainPageReaderTextBlock(0, longText))
        val pages = paginate(blocks = blocks)

        val slices = textSlices(pages, 0)
        assertTrue(slices.isNotEmpty(), "long block lost every slice")
        val covered = slices.joinToString("") { slice ->
            longText.substring(slice.range.start, slice.range.endExclusive)
        }
        assertTrue(
            covered.filterNot { it.isWhitespace() } == longText.filterNot { it.isWhitespace() },
            "covered text diverges from the source block",
        )
    }

    @Test
    fun `standalone slice height matches the rendering TextView height`() {
        val sliceText = "The TextView is the ground truth the paginator must agree with."
        val spannable = buildNovelPageReaderSpannableText(
            text = AnnotatedString(sliceText),
            firstLineIndentPx = firstLineIndentPx,
        )
        val standaloneHeight = measurePlainPageSliceStandaloneHeight(
            sliceText = spannable,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )

        val textView = TextView(ApplicationProvider.getApplicationContext())
        // The production page-mode TextView is created with includeFontPadding = true so
        // descenders (y, g, p) at small font sizes are not clipped at fontMetrics.descent.
        textView.includeFontPadding = true
        textView.setPadding(0, 0, 0, glyphPadPx)
        applyNovelPageReaderTextViewMetrics(
            textView = textView,
            metrics = resolveNovelPageReaderTextViewMetrics(
                textSizePx = textSizePx,
                lineSpacingMultiplier = lineHeightMultiplier,
                typeface = null,
                textAlign = TextAlign.LEFT,
                shadow = null,
                isPageMode = true,
            ),
        )
        textView.text = spannable
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        assertEquals(
            standaloneHeight,
            textView.measuredHeight - textView.paddingBottom,
            "paginator measurement and TextView layout disagree",
        )
    }

    @Test
    fun `block split across pages never emits two slices of the same block on one page`() {
        val lineHeightMult = 1.6f
        val blocks = listOf(
            PlainPageReaderTextBlock(0, "Opening paragraph."),
            PlainPageReaderTextBlock(1, "Alpha line\nBeta line\nGamma line\nDelta line"),
        )
        val spacing = resolvePageReaderInterBlockSpacingPx(
            paragraphSpacingPx = paragraphSpacingPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
        )
        val topHeight = measurePlainPageSliceStandaloneHeight(
            sliceText = blocks[0].text,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
        val threeLineStandalone = measurePlainPageSliceStandaloneHeight(
            sliceText = "Alpha line\nBeta line\nGamma line",
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
        // Budget intentionally sits just below 3 standalone lines while still leaving enough
        // remainingHeight after 2 lines + glyphPad for another single line, matching the device
        // scenario that previously stacked two slices of block 108 onto one page.
        val desiredSafeHeight = topHeight + glyphPadPx + spacing + threeLineStandalone - 4 + glyphPadPx
        val reservePx = 1000 - resolvePageReaderSafeHeightPx(1000, textSizePx, lineHeightMult)

        val plainPages = paginatePlainPageBlocks(
            textBlocks = blocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = desiredSafeHeight + reservePx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
        plainPages.forEachIndexed { pageIndex, page ->
            val blockIds = page.filterIsInstance<PlainPageSlice.Text>().map { it.blockIndex }
            assertEquals(
                blockIds.distinct().size,
                blockIds.size,
                "plain page $pageIndex emitted multiple slices of the same block: $blockIds",
            )
        }

        val richBlocks = blocks.map { block ->
            RichPageBlockText(
                sourceBlockIndex = block.sourceBlockIndex,
                text = AnnotatedString(block.text),
                firstLineIndentEm = null,
                sourceTextAlign = null,
            )
        }
        val richPages = paginateRichPageBlocks(
            blockTexts = richBlocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = desiredSafeHeight + reservePx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
        richPages.forEachIndexed { pageIndex, page ->
            val blockIds = page.map { it.blockIndex }
            assertEquals(
                blockIds.distinct().size,
                blockIds.size,
                "rich page $pageIndex emitted multiple slices of the same block: $blockIds",
            )
        }
    }

    @Test
    fun `standalone slice fit keeps last fitting line together when whole block spacing overflows`() {
        val lineHeightMult = 1.6f
        val blocks = listOf(
            PlainPageReaderTextBlock(0, "Opening paragraph."),
            PlainPageReaderTextBlock(1, "Alpha line\nBeta line\nGamma line\nDelta line"),
        )
        val wholeLayout = buildReaderTextLayout(
            text = blocks[1].text,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
        val whole3LineBottom = wholeLayout.getLineBottom(2) - wholeLayout.getLineTop(0)
        val standalone3LineHeight = measurePlainPageSliceStandaloneHeight(
            sliceText = "Alpha line\nBeta line\nGamma line",
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
        assertTrue(
            whole3LineBottom > standalone3LineHeight,
            "expected whole-block trailing line spacing ($whole3LineBottom) > standalone ($standalone3LineHeight)",
        )
        val spacing = resolvePageReaderInterBlockSpacingPx(
            paragraphSpacingPx = paragraphSpacingPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
        )
        val topHeight = measurePlainPageSliceStandaloneHeight(
            sliceText = blocks[0].text,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
        val midAvailableHeight = (standalone3LineHeight + whole3LineBottom) / 2
        val desiredSafeHeight = topHeight + glyphPadPx + spacing + midAvailableHeight + glyphPadPx
        val reservePx = 1000 - resolvePageReaderSafeHeightPx(1000, textSizePx, lineHeightMult)

        val pages = paginatePlainPageBlocks(
            textBlocks = blocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = desiredSafeHeight + reservePx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMult,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
        val block1SlicesPage0 = pages.first()
            .filterIsInstance<PlainPageSlice.Text>()
            .filter { it.blockIndex == 1 }
        assertEquals(1, block1SlicesPage0.size)
        val page0SliceText = blocks[1].text.substring(
            block1SlicesPage0.first().range.start,
            block1SlicesPage0.first().range.endExclusive,
        )
        assertEquals("Alpha line\nBeta line\nGamma line", page0SliceText)
    }

    @Test
    fun `merged text block enables cross paragraph selection with multi block anchors`() {
        val spacing = resolvePageReaderInterBlockSpacingPx(
            paragraphSpacingPx = paragraphSpacingPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
        )
        val inputs = listOf(
            PreparedMergedBlockInput(
                sourceBlockIndex = 0,
                sourceTextStartInBlock = 0,
                highlightedText = AnnotatedString("First paragraph end."),
                spacingBeforePx = 0,
                firstLineIndentPx = firstLineIndentPx,
                forcedTypefaceStyle = Typeface.NORMAL,
                textAlign = TextAlign.LEFT,
                fontSizeMultiplier = 1f,
                lineHeightMultiplier = 1f,
                typeface = null,
                textColor = Color.Black,
                isBlockQuote = false,
                startIndentPx = 0,
                textLocale = null,
            ),
            PreparedMergedBlockInput(
                sourceBlockIndex = 1,
                sourceTextStartInBlock = 4,
                highlightedText = AnnotatedString("Second paragraph start."),
                spacingBeforePx = spacing,
                firstLineIndentPx = null,
                forcedTypefaceStyle = Typeface.NORMAL,
                textAlign = TextAlign.LEFT,
                fontSizeMultiplier = 1f,
                lineHeightMultiplier = 1f,
                typeface = null,
                textColor = Color.Black,
                isBlockQuote = false,
                startIndentPx = 0,
                textLocale = null,
            ),
        )
        val mergedContent = buildMergedPageTextContent(
            inputs = inputs,
            pageColumnWidthPx = widthPx,
            bodyTextSizePx = textSizePx,
            bodyLineSpacingMultiplier = lineHeightMultiplier,
            bodyTypeface = null,
            quoteStripeWidthPx = 4f,
            quoteStripeOffsetPx = 6f,
            quoteStripeColor = android.graphics.Color.GRAY,
            density = 1f,
            hyphenationEnabled = false,
            onUrlClick = null,
            measurementPaint = null,
        )
        assertEquals(2, mergedContent.segments.size)

        val textView = NovelPageReaderTextView(
            context = ApplicationProvider.getApplicationContext(),
            selectionRenderer = NovelSelectedTextRenderer.PAGE_READER,
            selectionSessionIdProvider = { 1L },
            onSelectedTextSelectionChanged = {},
            onPlainTap = null,
            touchHandlingEnabled = true,
            selectionInteractionEnabled = true,
        ).apply {
            selectionAnchorChapterId = 42L
            includeFontPadding = true
            setPadding(0, 0, 0, glyphPadPx)
            applyNovelPageReaderTextViewMetrics(
                textView = this,
                metrics = resolveNovelPageReaderTextViewMetrics(
                    textSizePx = textSizePx,
                    lineSpacingMultiplier = lineHeightMultiplier,
                    typeface = null,
                    textAlign = TextAlign.LEFT,
                    shadow = null,
                    isPageMode = true,
                ),
            )
            mergedSegments = mergedContent.segments
            syntheticHyphenDisplayOffsets = mergedContent.syntheticHyphenDisplayOffsets
            text = mergedContent.spannable
            measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }

        val fullText = textView.text.toString()
        val selectStart = fullText.indexOf("end.")
        val selectEnd = fullText.indexOf("Second") + "Second".length
        Selection.setSelection(textView.text as Spannable, selectStart, selectEnd)
        shadowOf(Looper.getMainLooper()).idle()

        val selection = requireNotNull(textView.currentSelectionForTesting())
        assertTrue(selection.text.contains("end."))
        assertTrue(selection.text.contains("Second"))
        assertEquals(2, selection.selectionAnchors.size)
        assertEquals(0, selection.selectionAnchors[0].blockIndex)
        assertEquals(1, selection.selectionAnchors[1].blockIndex)
        assertEquals(4, selection.selectionAnchors[1].charStart)
    }

    @Test
    fun `paginatePlainPageBlocksWithImages with maxPages matches full prefix`() {
        val textBlocks = (0 until 20).map { index ->
            PlainPageReaderTextBlock(
                sourceBlockIndex = index * 2,
                text = "Paragraph $index line one",
            )
        }
        val imageBlocks = listOf(
            PlainPageImageBlock(
                sourceBlockIndex = 1,
                imageUrl = "https://example.com/1.jpg",
                contentDescription = null,
            ),
            PlainPageImageBlock(
                sourceBlockIndex = 15,
                imageUrl = "https://example.com/2.jpg",
                contentDescription = null,
            ),
        )
        val fullPages = paginatePlainPageBlocksWithImages(
            textBlocks = textBlocks,
            imageBlocks = imageBlocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = 220,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = null,
            textAlign = TextAlign.LEFT,
        )
        assertTrue(fullPages.size > 2, "expected multiple pages, got ${fullPages.size}")

        val limitedOne = paginatePlainPageBlocksWithImages(
            textBlocks = textBlocks,
            imageBlocks = imageBlocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = 220,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = null,
            textAlign = TextAlign.LEFT,
            maxPages = 1,
        )
        assertEquals(fullPages.take(1), limitedOne)

        val limitedTwo = paginatePlainPageBlocksWithImages(
            textBlocks = textBlocks,
            imageBlocks = imageBlocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = 220,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = null,
            textAlign = TextAlign.LEFT,
            maxPages = 2,
        )
        assertEquals(fullPages.take(2), limitedTwo)
    }

    @Test
    fun `paginateMixedRichPageBlocks with maxPages matches full prefix`() {
        val richBlocks = (0 until 18).map { index ->
            IndexedValue(
                index = index,
                value = if (index == 1) {
                    NovelRichContentBlock.Image(url = "https://example.com/rich.png", alt = null)
                } else {
                    NovelRichContentBlock.Paragraph(
                        segments = listOf(NovelRichTextSegment("Rich paragraph $index content")),
                    )
                },
            )
        }
        val fullPagination = paginateMixedRichPageBlocks(
            richBlocks = richBlocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = 220,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = null,
            textAlign = TextAlign.LEFT,
            forceParagraphIndent = false,
        )
        assertTrue(fullPagination.pages.size > 2, "expected multiple rich pages, got ${fullPagination.pages.size}")

        val limitedTwo = paginateMixedRichPageBlocks(
            richBlocks = richBlocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = 220,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = null,
            textAlign = TextAlign.LEFT,
            forceParagraphIndent = false,
            maxPages = 2,
        )
        assertEquals(fullPagination.pages.take(2), limitedTwo.pages)
    }

    @Test
    fun `shouldTrackNativeScrollProgress blocks unmeasured layout and chapter mismatch`() {
        assertFalse(
            shouldTrackNativeScrollProgress(
                totalItemsCount = 0,
                hasVisibleItems = false,
                restoredChapterId = 10L,
                currentChapterId = 10L,
            ),
        )
        assertFalse(
            shouldTrackNativeScrollProgress(
                totalItemsCount = 5,
                hasVisibleItems = false,
                restoredChapterId = 10L,
                currentChapterId = 10L,
            ),
        )
        assertFalse(
            shouldTrackNativeScrollProgress(
                totalItemsCount = 5,
                hasVisibleItems = true,
                restoredChapterId = 9L,
                currentChapterId = 10L,
            ),
        )
        assertTrue(
            shouldTrackNativeScrollProgress(
                totalItemsCount = 5,
                hasVisibleItems = true,
                restoredChapterId = 10L,
                currentChapterId = 10L,
            ),
        )
    }

    @Test
    fun `NovelPageReaderTextView caches appliedMetrics and textColor`() {
        val textView = NovelPageReaderTextView(
            context = ApplicationProvider.getApplicationContext(),
            selectionRenderer = NovelSelectedTextRenderer.PAGE_READER,
            selectionSessionIdProvider = { 1L },
            onSelectedTextSelectionChanged = {},
            onPlainTap = null,
            touchHandlingEnabled = true,
            selectionInteractionEnabled = false,
        )
        val metrics = resolveNovelPageReaderTextViewMetrics(
            textSizePx = textSizePx,
            lineSpacingMultiplier = lineHeightMultiplier,
            typeface = null,
            textAlign = TextAlign.LEFT,
            shadow = null,
            isPageMode = true,
        )
        applyNovelPageReaderTextViewMetrics(textView, metrics)
        assertEquals(metrics, textView.appliedMetrics)
        textView.applyTextColorIfChanged(android.graphics.Color.BLACK)
        assertEquals(android.graphics.Color.BLACK, textView.appliedTextColorArgb)
    }
}
