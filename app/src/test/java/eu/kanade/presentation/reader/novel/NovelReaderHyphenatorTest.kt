package eu.kanade.presentation.reader.novel

import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NovelReaderHyphenatorTest {

    private fun hyphenateWord(word: String): String {
        val breaks = findReaderWordHyphenBreaks(word)
        if (breaks.isEmpty()) return word
        val out = StringBuilder(word.length + breaks.size)
        var last = 0
        for (pos in breaks) {
            out.append(word, last, pos).append('-')
            last = pos
        }
        out.append(word, last, word.length)
        return out.toString()
    }

    @Test
    fun `english hyphenator produces clean syllable breaks and avoids broken fragments`() {
        assertEquals("rest-ing", hyphenateWord("resting"))
        assertEquals("whis-per", hyphenateWord("whisper"))
        assertEquals("whis-pered", hyphenateWord("whispered"))
        assertEquals("car-ry-ing", hyphenateWord("carrying"))
        assertTrue(hyphenateWord("continuing").startsWith("con-"))
        assertFalse(hyphenateWord("continuing").endsWith("-g"))
        assertFalse(hyphenateWord("resting").startsWith("resti-"))
        assertFalse(hyphenateWord("whisper").startsWith("whi-"))
        assertEquals("dis-tin-guish", hyphenateWord("distinguish"))
        assertEquals("re-sem-ble", hyphenateWord("resemble"))
        assertEquals("nerv-ously", hyphenateWord("nervously"))
        assertEquals("un-bu-ried", hyphenateWord("unburied"))

        // Short words and uppercase acronyms remain intact.
        assertEquals("cave", hyphenateWord("cave"))
        assertEquals("HTML", hyphenateWord("HTML"))
    }

    @Test
    fun `russian hyphenator follows syllable and sign rules cleanly`() {
        assertEquals("мо-ло-ко", hyphenateWord("молоко"))
        assertEquals("кош-ка", hyphenateWord("кошка"))
        assertEquals("подъ-езд", hyphenateWord("подъезд"))
        assertEquals("боль-шой", hyphenateWord("большой"))
        assertEquals("ван-на", hyphenateWord("ванна"))
        assertEquals("со-юз", hyphenateWord("союз"))
        assertEquals("май-ор", hyphenateWord("майор"))
        assertEquals("ок-но", hyphenateWord("окно"))
        assertEquals("оса", hyphenateWord("оса")) // Never leaves 1-letter "о-са".
        assertEquals("ар-мия", hyphenateWord("армия")) // Never leaves "-я".
    }

    @Test
    fun `prepared break builder maps spans, preserves genuine hyphens, and coalesces word selection`() {
        val disabled = resolveWidthHyphenatedReaderTextAndLayout(
            source = "I’ll completely stay here today",
            widthPx = 120,
            textSizePx = 32f,
            lineHeightMultiplier = 1.4f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.LEFT,
            hyphenationEnabled = false,
        )
        assertNull(disabled.prepared.displayToSourceOffsets)
        assertNull(disabled.prepared.syntheticHyphenDisplayOffsets)
        assertEquals("I’ll completely stay here today", disabled.prepared.text.toString())

        val raw = "Xin is carrying, well-known, I’ll rest."
        val normalized = normalizeCurlyApostrophesForDisplay(raw)
        assertEquals("Xin is carrying, well-known, I'll rest.", normalized.toString())

        val carryStart = raw.indexOf("carrying")
        val carryEnd = carryStart + "carrying".length
        val spannable = SpannableStringBuilder(normalized).apply {
            setSpan(LeadingMarginSpan.Standard(32, 0), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), carryStart, carryStart + 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BackgroundColorSpan(Color.YELLOW), carryStart, carryEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Simulate wrapping "carrying" as "car-rying" at offset carryStart + 3.
        val prepared = buildPreparedHyphenatedTextFromSourceBreaks(
            normalizedSource = spannable,
            sortedSourceBreakOffsets = intArrayOf(carryStart + 3),
        )
        val display = prepared.text as Spanned
        val displayStr = display.toString()

        assertFalse(displayStr.contains(SOFT_HYPHEN_CHAR))
        assertTrue(displayStr.contains("car-rying"))
        assertTrue(displayStr.contains("well-known"))

        val extracted = extractReaderSourceTextForDisplayRange(
            displayText = displayStr,
            start = 0,
            endExclusive = displayStr.length,
            syntheticHyphenDisplayOffsets = prepared.syntheticHyphenDisplayOffsets,
        )
        // Synthetic '-' in "car-rying" is stripped; author's '-' in "well-known" stays intact.
        assertEquals("Xin is carrying, well-known, I'll rest.", extracted)

        // Spans survived on exact mapped substrings.
        val boldSpan = display.getSpans(0, display.length, StyleSpan::class.java).singleOrNull()
        assertNotNull(boldSpan)
        val boldClean = extractReaderSourceTextForDisplayRange(
            displayText = displayStr,
            start = display.getSpanStart(boldSpan),
            endExclusive = display.getSpanEnd(boldSpan),
            syntheticHyphenDisplayOffsets = prepared.syntheticHyphenDisplayOffsets,
        )
        assertEquals("carr", boldClean)

        val ttsSpan = display.getSpans(0, display.length, BackgroundColorSpan::class.java).singleOrNull()
        assertNotNull(ttsSpan)
        val ttsClean = extractReaderSourceTextForDisplayRange(
            displayText = displayStr,
            start = display.getSpanStart(ttsSpan),
            endExclusive = display.getSpanEnd(ttsSpan),
            syntheticHyphenDisplayOffsets = prepared.syntheticHyphenDisplayOffsets,
        )
        assertEquals("carrying", ttsClean)

        val marginSpan = display.getSpans(0, display.length, LeadingMarginSpan.Standard::class.java).singleOrNull()
        assertNotNull(marginSpan)
        assertEquals(0, display.getSpanStart(marginSpan))
        assertEquals(display.length, display.getSpanEnd(marginSpan))

        // Long-pressing either half ("car-" or "rying") selects the entire word "car-rying" -> "carrying".
        val rightHalfOffset = displayStr.indexOf("rying") + 1
        val wordRange = resolveReaderWordDisplayRangeForOffset(
            displayText = displayStr,
            displayOffset = rightHalfOffset,
            syntheticHyphenDisplayOffsets = prepared.syntheticHyphenDisplayOffsets,
        )
        assertNotNull(wordRange)
        assertEquals(
            "carrying",
            extractReaderSourceTextForDisplayRange(
                displayText = displayStr,
                start = wordRange.first,
                endExclusive = wordRange.last + 1,
                syntheticHyphenDisplayOffsets = prepared.syntheticHyphenDisplayOffsets,
            ),
        )

        // Reverse offset mapping from display range back to original raw indices.
        val offsets = requireNotNull(prepared.displayToSourceOffsets)
        val mappedStart = mapDisplayOffsetToSourceOffset(wordRange.first, offsets, display.length)
        val mappedEnd = mapDisplayOffsetToSourceOffset(wordRange.last + 1, offsets, display.length)
        assertEquals("carrying", raw.substring(mappedStart, mappedEnd))
    }

    /**
     * Minimal deterministic greedy Android-equivalent [Layout] using uniform glyph advance
     * [charWidthPx], breaking at whitespace or immediately after `-` so host tests can verify
     * exact width-aware hyphen placement without Robolectric's zero-width font limitation.
     */
    private class GreedyGridLayout(
        sourceText: CharSequence,
        private val widthPx: Int,
        private val charWidthPx: Float = 10f,
        private val paint: TextPaint = TextPaint(),
        private val requireZwspAfterHyphen: Boolean = false,
    ) : Layout(sourceText, paint, widthPx, Alignment.ALIGN_NORMAL, 1f, 0f) {

        private val lineStarts: IntArray
        private val lineEnds: IntArray

        init {
            val starts = mutableListOf<Int>()
            val ends = mutableListOf<Int>()
            val indentPx = if (sourceText is Spanned) {
                sourceText.getSpans(0, sourceText.length, LeadingMarginSpan::class.java)
                    .maxOfOrNull { it.getLeadingMargin(true) } ?: 0
            } else {
                0
            }

            var cursor = 0
            var lineIdx = 0
            while (cursor < sourceText.length) {
                val maxWidthPx = (widthPx - if (lineIdx == 0) indentPx else 0).coerceAtLeast(charWidthPx.toInt())
                val maxChars = (maxWidthPx / charWidthPx).toInt().coerceAtLeast(1)
                var visibleCount = 0
                var limit = cursor
                while (limit < sourceText.length) {
                    val ch = sourceText[limit]
                    if (ch == '\n') break
                    val nextVisible = if (ch == '\u200B') visibleCount else visibleCount + 1
                    if (nextVisible > maxChars) break
                    visibleCount = nextVisible
                    limit++
                }
                if (limit < sourceText.length && sourceText[limit] == '\n') {
                    starts += cursor
                    ends += limit + 1
                    cursor = limit + 1
                    lineIdx++
                    continue
                }
                if (limit >= sourceText.length) {
                    starts += cursor
                    ends += sourceText.length
                    break
                }
                var breakEnd = -1
                for (pos in limit downTo cursor + 1) {
                    val prev = sourceText[pos - 1]
                    val canBreak = prev == ' ' ||
                        prev == '\t' ||
                        prev == '\u200B' ||
                        (!requireZwspAfterHyphen && prev == '-')
                    if (canBreak) {
                        breakEnd = pos
                        break
                    }
                }
                if (breakEnd <= cursor) {
                    breakEnd = limit
                }
                starts += cursor
                ends += breakEnd
                cursor = breakEnd
                lineIdx++
            }
            if (starts.isEmpty()) {
                starts += 0
                ends += 0
            }
            starts += sourceText.length
            lineStarts = starts.toIntArray()
            lineEnds = ends.toIntArray()
        }

        private val emptyDirections: Directions =
            android.text.StaticLayout.Builder.obtain("", 0, 0, paint, 1).build().getLineDirections(0)

        override fun getLineCount(): Int = (lineStarts.size - 1).coerceAtLeast(1)
        override fun getLineTop(line: Int): Int = line * 20
        override fun getLineDescent(line: Int): Int = 4
        override fun getLineStart(line: Int): Int = lineStarts[line]
        override fun getEllipsisStart(line: Int): Int = 0
        override fun getEllipsisCount(line: Int): Int = 0
        override fun getParagraphDirection(line: Int): Int = DIR_LEFT_TO_RIGHT
        override fun getLineContainsTab(line: Int): Boolean = false
        override fun getLineDirections(line: Int): Directions = emptyDirections
        override fun getTopPadding(): Int = 0
        override fun getBottomPadding(): Int = 0
        override fun getLineMax(line: Int): Float = (lineEnds[line] - lineStarts[line]) * charWidthPx
    }

    private class FixedWidthTextPaint(
        private val charWidthPx: Float = 10f,
    ) : TextPaint() {
        init {
            textSize = 20f
        }

        override fun measureText(text: CharSequence, start: Int, end: Int): Float {
            var count = 0
            for (i in start.coerceAtLeast(0) until end.coerceAtMost(text.length)) {
                if (text[i] != '\u200B') count++
            }
            return count * charWidthPx
        }

        override fun measureText(text: String): Float {
            return text.count { it != '\u200B' } * charWidthPx
        }
    }

    @Test
    fun `width hyphenation places hyphens strictly at line breaks with zero mid-line or soft hyphens`() {
        val sample = "Her wound is still not completely closed. " +
            "Liang is carrying Lin while continuing gently forward through the forest."
        val paint = FixedWidthTextPaint(charWidthPx = 10f)

        var sawAtLeastOneLineBreakHyphen = false
        for (widthPx in listOf(220, 240, 260, 280, 300, 340)) {
            val result = prepareWidthHyphenatedReaderText(
                source = sample,
                widthPx = widthPx,
                paint = paint,
                firstLineIndentPx = null,
                hyphenationEnabled = true,
                buildWholeLayout = { candidate -> GreedyGridLayout(candidate, widthPx, 10f, paint) },
                buildSliceLayout = { slice -> GreedyGridLayout(slice, widthPx, 10f, paint) },
            )
            val displayStr = result.prepared.text.toString()
            assertFalse(displayStr.contains(SOFT_HYPHEN_CHAR))

            val lineEndSet = (0 until (result.layout.lineCount - 1))
                .map { result.layout.getLineEnd(it) }
                .toSet()

            val syntheticFlags = result.prepared.syntheticHyphenDisplayOffsets
            if (syntheticFlags != null) {
                for (idx in syntheticFlags.indices) {
                    if (syntheticFlags[idx]) {
                        assertEquals(SYNTHETIC_HYPHEN_CHAR, displayStr[idx])
                        assertTrue(
                            (idx + 1) in lineEndSet,
                            "Synthetic hyphen at $idx in '$displayStr' must end a layout line",
                        )
                        sawAtLeastOneLineBreakHyphen = true
                    }
                }
            }
            assertEquals(
                sample,
                extractReaderSourceTextForDisplayRange(
                    displayText = displayStr,
                    start = 0,
                    endExclusive = displayStr.length,
                    syntheticHyphenDisplayOffsets = syntheticFlags,
                ),
            )
        }
        assertTrue(sawAtLeastOneLineBreakHyphen, "Expected at least one line-end hyphen across tested widths")

        // Verify Android StaticLayout driver also runs cleanly across all TextAlign variants.
        for (align in listOf(TextAlign.LEFT, TextAlign.CENTER, TextAlign.RIGHT, TextAlign.JUSTIFY)) {
            val nativeResult = resolveWidthHyphenatedReaderTextAndLayout(
                source = sample,
                widthPx = 300,
                textSizePx = 32f,
                lineHeightMultiplier = 1.4f,
                typeface = Typeface.DEFAULT,
                textAlign = align,
                hyphenationEnabled = true,
            )
            assertFalse(nativeResult.prepared.text.toString().contains(SOFT_HYPHEN_CHAR))
        }
    }

    @Test
    fun `width hyphenation respects first line indent on line zero only`() {
        // Indent = 40px (4 chars) on a 200px column leaves 16 chars for line 0 ("Her wound is in-"),
        // while line 1+ keeps full 20 chars ("jured completely ...").
        val sample = "Her wound is injured completely while resting nearby."
        val paint = FixedWidthTextPaint(charWidthPx = 10f)
        val spannable = SpannableStringBuilder(sample).apply {
            setSpan(LeadingMarginSpan.Standard(40, 0), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val result = prepareWidthHyphenatedReaderText(
            source = spannable,
            widthPx = 200,
            paint = paint,
            firstLineIndentPx = 40,
            hyphenationEnabled = true,
            buildWholeLayout = { candidate -> GreedyGridLayout(candidate, 200, 10f, paint) },
            buildSliceLayout = { slice -> GreedyGridLayout(slice, 200, 10f, paint) },
        )
        val displayStr = result.prepared.text.toString()
        assertFalse(displayStr.contains(SOFT_HYPHEN_CHAR))
        val line0 = displayStr.substring(result.layout.getLineStart(0), result.layout.getLineEnd(0))
        assertEquals("Her wound is in-", line0)
        val lineEndSet = (0 until (result.layout.lineCount - 1))
            .map { result.layout.getLineEnd(it) }
            .toSet()
        result.prepared.syntheticHyphenDisplayOffsets?.forEachIndexed { idx, isSynthetic ->
            if (isSynthetic) {
                assertTrue((idx + 1) in lineEndSet)
            }
        }
        assertEquals(
            sample,
            extractReaderSourceTextForDisplayRange(
                displayText = displayStr,
                start = 0,
                endExclusive = displayStr.length,
                syntheticHyphenDisplayOffsets = result.prepared.syntheticHyphenDisplayOffsets,
            ),
        )
    }

    @Test
    fun `hyphenated plain pagination keeps source slice ranges contiguous and skips mid-word page breaks`() {
        val blocks = listOf(
            PlainPageReaderTextBlock(0, "Her wound is still not completely closed. Xun’s resting place."),
            PlainPageReaderTextBlock(1, "We each stuff our pockets as full of meat as we can before continuing."),
            PlainPageReaderTextBlock(2, "The journey through the silent dark forest is unexpectedly quiet tonight."),
            PlainPageReaderTextBlock(3, "Even the tiniest whisper can be clearly remembered until tomorrow morning."),
        )
        val pages = paginatePlainPageBlocks(
            textBlocks = blocks,
            paragraphSpacingPx = 12,
            widthPx = 28,
            heightPx = 190,
            textSizePx = 34f,
            lineHeightMultiplier = 1.4f,
            typeface = Typeface.DEFAULT,
            textAlign = TextAlign.JUSTIFY,
            hyphenationEnabled = true,
        )

        assertTrue(pages.size >= 2)
        blocks.forEach { block ->
            val slices = pages.flatten().filterIsInstance<PlainPageSlice.Text>().filter {
                it.blockIndex == block.sourceBlockIndex
            }
            assertTrue(slices.isNotEmpty())
            assertEquals(0, slices.first().range.start)
            assertEquals(block.text.length, slices.last().range.endExclusive)
            for (i in 0 until slices.lastIndex) {
                assertTrue(slices[i].range.endExclusive <= slices[i + 1].range.start)
                val endOffset = slices[i].range.endExclusive
                if (endOffset > 0 && endOffset < block.text.length) {
                    assertFalse(
                        block.text[endOffset - 1].isLetter() && block.text[endOffset].isLetter(),
                        "Page break split word mid-letters at $endOffset in '${block.text}'",
                    )
                }
            }
        }
    }

    @Test
    fun `width hyphenator falls back to hyphen plus zwsp when layout refuses bare hyphen`() {
        val sample = "that they have left is the sound of their footsteps."
        val paint = FixedWidthTextPaint(charWidthPx = 10f)
        // Line 0 fits 47 visible characters ("that they have left is the sound of their foot-"),
        // whereas bare '-' fails to break so it falls back to "-\u200B".
        val result = prepareWidthHyphenatedReaderText(
            source = sample,
            widthPx = 470,
            paint = paint,
            firstLineIndentPx = null,
            hyphenationEnabled = true,
            buildWholeLayout = { candidate ->
                GreedyGridLayout(
                    sourceText = candidate,
                    widthPx = 470,
                    charWidthPx = 10f,
                    paint = paint,
                    requireZwspAfterHyphen = true,
                )
            },
            buildSliceLayout = { slice ->
                GreedyGridLayout(
                    sourceText = slice,
                    widthPx = 470,
                    charWidthPx = 10f,
                    paint = paint,
                    requireZwspAfterHyphen = true,
                )
            },
        )

        val displayStr = result.prepared.text.toString()
        assertEquals("that they have left is the sound of their foot-\u200Bsteps.", displayStr)
        assertEquals(2, result.layout.lineCount)
        assertEquals(
            "that they have left is the sound of their foot-\u200B",
            displayStr.substring(result.layout.getLineStart(0), result.layout.getLineEnd(0)),
        )
        assertEquals(
            sample,
            extractReaderSourceTextForDisplayRange(
                displayText = displayStr,
                start = 0,
                endExclusive = displayStr.length,
                syntheticHyphenDisplayOffsets = result.prepared.syntheticHyphenDisplayOffsets,
            ),
        )
        val tappedSecondHalfOffset = displayStr.indexOf("steps") + 1
        val coalescedRange = resolveReaderWordDisplayRangeForOffset(
            displayText = displayStr,
            displayOffset = tappedSecondHalfOffset,
            syntheticHyphenDisplayOffsets = result.prepared.syntheticHyphenDisplayOffsets,
        )
        assertNotNull(coalescedRange)
        assertEquals(
            "footsteps",
            extractReaderSourceTextForDisplayRange(
                displayText = displayStr,
                start = coalescedRange.first,
                endExclusive = coalescedRange.last + 1,
                syntheticHyphenDisplayOffsets = result.prepared.syntheticHyphenDisplayOffsets,
            ),
        )
        val sourceWordStart = sample.indexOf("footsteps")
        assertEquals(
            sourceWordStart,
            mapDisplayOffsetToSourceOffset(
                displayOffset = coalescedRange.first,
                displayToSourceOffsets = result.prepared.displayToSourceOffsets,
                fallbackLength = displayStr.length,
            ),
        )
        assertEquals(
            sourceWordStart + "footsteps".length,
            mapDisplayOffsetToSourceOffset(
                displayOffset = coalescedRange.last + 1,
                displayToSourceOffsets = result.prepared.displayToSourceOffsets,
                fallbackLength = displayStr.length,
            ),
        )
    }

    @Test
    fun `english hyphenator enables two letter prefixes and prepares without breaking monosyllables`() {
        assertTrue(findReaderWordHyphenBreaks("resolved").contains(2))
        assertTrue(findReaderWordHyphenBreaks("remained").contains(2))
        assertTrue(findReaderWordHyphenBreaks("expense").contains(2))
        assertTrue(findReaderWordHyphenBreaks("escape").contains(2))
        assertTrue(findReaderWordHyphenBreaks("potential").contains(2))
        assertTrue(findReaderWordHyphenBreaks("beforehand").contains(2))
        assertTrue(findReaderWordHyphenBreaks("prepares").contains(3))

        assertEquals(emptyList<Int>(), findReaderWordHyphenBreaks("stared"))
        assertEquals(emptyList<Int>(), findReaderWordHyphenBreaks("closed"))
        assertEquals(emptyList<Int>(), findReaderWordHyphenBreaks("moved"))
        assertEquals(emptyList<Int>(), findReaderWordHyphenBreaks("dressed"))
        assertEquals(emptyList<Int>(), findReaderWordHyphenBreaks("slammed"))
        assertEquals(emptyList<Int>(), findReaderWordHyphenBreaks("ripped"))
    }

    private class RangeTestLayout(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        ranges: List<IntRange>,
    ) : Layout(text, paint, widthPx, Alignment.ALIGN_NORMAL, 1f, 0f) {
        private val lineStarts: IntArray = (ranges.map { it.first } + listOf(ranges.last().last + 1)).toIntArray()
        private val emptyDir: Directions =
            android.text.StaticLayout.Builder.obtain("", 0, 0, paint, 1).build().getLineDirections(0)

        override fun getLineCount(): Int = lineStarts.size - 1
        override fun getLineTop(line: Int): Int = line * 40
        override fun getLineDescent(line: Int): Int = 8
        override fun getLineStart(line: Int): Int = lineStarts[line]
        override fun getEllipsisStart(line: Int): Int = 0
        override fun getEllipsisCount(line: Int): Int = 0
        override fun getParagraphDirection(line: Int): Int = DIR_LEFT_TO_RIGHT
        override fun getLineContainsTab(line: Int): Boolean = false
        override fun getLineDirections(line: Int): Directions = emptyDir
        override fun getTopPadding(): Int = 0
        override fun getBottomPadding(): Int = 0
    }

    @Test
    fun `rebalancing layout rejects hyphenation that pulls an earlier synthetic hyphen mid-line`() {
        val sample = "First line carries resting word then second line carries brother down below."
        val paint = FixedWidthTextPaint(charWidthPx = 10f)

        // Simulate OptimalLineBreaker: line 0 wraps cleanly after "resting" -> "res-"; once line 1
        // inserts "bro-" earlier-accepted hyphen on line 0 would be pulled mid-line unless rejected.
        fun buildRebalanced(text: CharSequence): Layout {
            val str = text.toString()
            val broIdx = str.indexOf("bro-")
            val resIdx = str.indexOf("res-")
            val ranges = when {
                resIdx >= 0 && broIdx >= 0 -> {
                    val secondEnd = str.indexOf("second ") + "second ".length
                    val broEnd = broIdx + "bro-".length
                    listOf(0 until secondEnd, secondEnd until broEnd, broEnd until str.length)
                }
                resIdx >= 0 -> {
                    val resEnd = resIdx + "res-".length
                    val broStart = str.indexOf("brother ")
                    listOf(0 until resEnd, resEnd until broStart, broStart until str.length)
                }
                else -> {
                    val b0 = str.indexOf("resting ")
                    val b1 = str.indexOf("brother ")
                    listOf(0 until b0, b0 until b1, b1 until str.length)
                }
            }
            return RangeTestLayout(text, paint, 240, ranges)
        }

        val result = prepareWidthHyphenatedReaderText(
            source = sample,
            widthPx = 240,
            paint = paint,
            firstLineIndentPx = null,
            hyphenationEnabled = true,
            buildWholeLayout = { candidate -> buildRebalanced(candidate) },
            buildSliceLayout = { slice ->
                val s = slice.toString()
                val firstHyphen = s.indexOf('-')
                if (firstHyphen > 0 && firstHyphen + 1 < s.length) {
                    RangeTestLayout(
                        slice,
                        paint,
                        240,
                        listOf(
                            0 until (firstHyphen + 1),
                            (firstHyphen + 1) until s.length,
                        ),
                    )
                } else {
                    buildRebalanced(slice)
                }
            },
        )
        val displayStr = result.prepared.text.toString()
        val lineEndSet = (0 until result.layout.lineCount - 1)
            .map { result.layout.getLineEnd(it) }
            .toSet()
        result.prepared.syntheticHyphenDisplayOffsets?.forEachIndexed { idx, isSynthetic ->
            if (isSynthetic) {
                assertTrue((idx + 1) in lineEndSet, "Synthetic hyphen at $idx ended up mid-line in '$displayStr'")
            }
        }
    }

    @Test
    fun `justify spacing widens interior ascii spaces via JustifySpaceSpan while skipping paragraph ends and nbsp`() {
        val sample = "Alpha beta\u00A0gamma\ndelta epsilon zeta\nOne two three four five six seven."
        val paint = FixedWidthTextPaint(charWidthPx = 10f)
        val widthPx = 220

        val result = prepareWidthHyphenatedReaderText(
            source = sample,
            widthPx = widthPx,
            paint = paint,
            firstLineIndentPx = null,
            hyphenationEnabled = false,
            buildWholeLayout = { candidate -> GreedyGridLayout(candidate, widthPx, 10f, paint) },
            buildSliceLayout = { slice -> GreedyGridLayout(slice, widthPx, 10f, paint) },
            justifySpacingEnabled = true,
        )

        assertEquals(sample, result.prepared.text.toString())
        assertEquals(null, result.prepared.displayToSourceOffsets)
        val spanned = result.prepared.text as Spanned
        val spaceSpans = spanned.getSpans(0, spanned.length, JustifySpaceSpan::class.java)
        assertTrue(spaceSpans.isNotEmpty())

        // Sub-paragraph lines ending in '\n' and the paragraph-final line receive zero spans.
        val layout = result.layout
        val nlLine0End = layout.getLineEnd(0)
        val nlLine1End = layout.getLineEnd(1)
        val lastLineStart = layout.getLineStart(layout.lineCount - 1)
        for (span in spaceSpans) {
            val s = spanned.getSpanStart(span)
            val e = spanned.getSpanEnd(span)
            assertEquals(1, e - s)
            assertEquals(' ', spanned[s])
            assertTrue(s >= nlLine1End, "Line ending in '\\n' must not be stretched (span@$s, nl1=$nlLine1End)")
            assertTrue(s < lastLineStart, "Final line must not be stretched (span@$s, lastStart=$lastLineStart)")
            assertTrue(span.targetWidthPx in 11..35)
        }
        assertTrue(nlLine0End > 0)
        val nbspIdx = sample.indexOf('\u00A0')
        assertEquals(0, spanned.getSpans(nbspIdx, nbspIdx + 1, JustifySpaceSpan::class.java).size)
    }

    @Test
    fun `justify spacing preserves hyphenated breaks, spans, and rolls back on line shift`() {
        val raw = "We are continuing steadily forward through the silent valley."
        val paint = FixedWidthTextPaint(charWidthPx = 10f)
        val rich = android.text.SpannableStringBuilder(raw).apply {
            setSpan(LeadingMarginSpan.Standard(30, 0), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0,
                2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

        val withHyphens = prepareWidthHyphenatedReaderText(
            source = rich,
            widthPx = 240,
            paint = paint,
            firstLineIndentPx = null,
            hyphenationEnabled = true,
            buildWholeLayout = { candidate -> GreedyGridLayout(candidate, 240, 10f, paint) },
            buildSliceLayout = { slice -> GreedyGridLayout(slice, 240, 10f, paint) },
            justifySpacingEnabled = true,
        )
        val spanned = withHyphens.prepared.text as Spanned
        assertEquals(1, spanned.getSpans(0, spanned.length, LeadingMarginSpan.Standard::class.java).size)
        assertEquals(1, spanned.getSpans(0, 2, android.text.style.StyleSpan::class.java).size)
        val spaceSpans = spanned.getSpans(0, spanned.length, JustifySpaceSpan::class.java)
        assertTrue(spaceSpans.isNotEmpty())
        val lineEndSet = (0 until withHyphens.layout.lineCount - 1)
            .map { withHyphens.layout.getLineEnd(it) }
            .toSet()
        withHyphens.prepared.syntheticHyphenDisplayOffsets?.forEachIndexed { idx, isSynthetic ->
            if (isSynthetic) {
                assertTrue((idx + 1) in lineEndSet)
            }
        }

        // Verify rollback drops only the offending line when JustifySpaceSpan causes a break shift.
        val sample = "One two three four\nfive six seven."
        val line0End = "One two three ".length
        val rolledBack = prepareWidthHyphenatedReaderText(
            source = sample,
            widthPx = 220,
            paint = paint,
            firstLineIndentPx = null,
            hyphenationEnabled = false,
            buildWholeLayout = { candidate ->
                val s = candidate as? Spanned
                val hasLine0Span =
                    s?.getSpans(0, line0End, JustifySpaceSpan::class.java)?.isNotEmpty() == true
                val ranges = if (hasLine0Span) {
                    listOf(
                        0 until "One two ".length,
                        "One two ".length until "One two three four\n".length,
                        "One two three four\n".length until candidate.length,
                    )
                } else {
                    listOf(
                        0 until line0End,
                        line0End until "One two three four\n".length,
                        "One two three four\n".length until candidate.length,
                    )
                }
                RangeTestLayout(candidate, paint, 220, ranges)
            },
            buildSliceLayout = { slice -> GreedyGridLayout(slice, 220, 10f, paint) },
            justifySpacingEnabled = true,
        )
        assertEquals(3, rolledBack.layout.lineCount)
        assertEquals(line0End, rolledBack.layout.getLineEnd(0))
        val remainingSpans = (rolledBack.prepared.text as? Spanned)
            ?.getSpans(0, rolledBack.prepared.text.length, JustifySpaceSpan::class.java)
            .orEmpty()
        assertEquals(0, remainingSpans.size)
    }
}
