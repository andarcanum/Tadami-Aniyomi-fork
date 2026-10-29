package eu.kanade.presentation.reader.novel

import android.app.Application
import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt
import kotlin.test.assertEquals
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
        // The production page-mode TextView is created with includeFontPadding = false in the
        // NovelPageReaderTextBlock factory, and the measurement mirrors it.
        textView.includeFontPadding = false
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
}
