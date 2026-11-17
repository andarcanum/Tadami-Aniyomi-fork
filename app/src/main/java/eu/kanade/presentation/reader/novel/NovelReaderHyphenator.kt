package eu.kanade.presentation.reader.novel

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import android.util.Log
import com.tadami.aurora.BuildConfig
import java.text.BreakIterator
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor

private const val HYPHENATOR_LOG_TAG = "NovelReaderHyphenator"
private const val SOFT_BREAK_ZWSP_CHAR = '\u200B'
private const val SYNTHETIC_HYPHEN_WITH_ZWSP = "-$SOFT_BREAK_ZWSP_CHAR"
private const val JUSTIFY_RIGHT_SAFETY_PX = 2.5f
private const val JUSTIFY_HYPHEN_LINE_SAFETY_PX = 4.5f
private const val JUSTIFY_MAX_SPACE_SCALE = 3.5f
private const val JUSTIFY_MAX_SINGLE_SPACE_SCALE = 2.2f
private const val JUSTIFY_MIN_DEFICIT_PX = 1.0f
private val debugHyphenBlockLogCount = AtomicInteger(0)
private val debugHyphenDisabledLogCount = AtomicInteger(0)
private val debugJustifyLogCount = AtomicInteger(0)

internal data class SyntheticHyphenBreak(
    val sourceOffset: Int,
    val token: String = "-",
)

private data class InsertedWorkMarker(
    val workOffset: Int,
    val length: Int,
)

private sealed interface HyphenBreakAttempt {
    data class Accepted(
        val rebuiltLayout: Layout,
        val workInsertOffset: Int,
    ) : HyphenBreakAttempt

    data class Rejected(
        val detail: String,
        val wrappedBeforeWord: Boolean,
    ) : HyphenBreakAttempt
}

internal const val SOFT_HYPHEN_CHAR = '\u00AD'
internal const val SYNTHETIC_HYPHEN_CHAR = '-'

internal data class PreparedHyphenatedText(
    val text: CharSequence,
    val displayToSourceOffsets: IntArray?,
    val syntheticHyphenDisplayOffsets: BooleanArray? = null,
)

internal data class WidthHyphenatedTextResult(
    val prepared: PreparedHyphenatedText,
    val layout: Layout,
)

internal fun stripSoftHyphens(text: String): String {
    return if (text.indexOf(SOFT_HYPHEN_CHAR) >= 0) {
        text.replace(SOFT_HYPHEN_CHAR.toString(), "")
    } else {
        text
    }
}

internal fun extractReaderSourceTextForDisplayRange(
    displayText: CharSequence,
    start: Int,
    endExclusive: Int,
    syntheticHyphenDisplayOffsets: BooleanArray? = null,
): String {
    if (displayText.isEmpty()) return ""
    val safeStart = start.coerceIn(0, displayText.length)
    val safeEnd = endExclusive.coerceIn(safeStart, displayText.length)
    if (safeStart >= safeEnd) return ""
    val raw = displayText.subSequence(safeStart, safeEnd).toString()
    if (syntheticHyphenDisplayOffsets == null) {
        return stripSoftHyphens(raw)
    }
    var hasSyntheticOrSoft = false
    for (i in safeStart until safeEnd) {
        if ((i < syntheticHyphenDisplayOffsets.size && syntheticHyphenDisplayOffsets[i]) ||
            displayText[i] == SOFT_HYPHEN_CHAR
        ) {
            hasSyntheticOrSoft = true
            break
        }
    }
    if (!hasSyntheticOrSoft) return raw
    val out = StringBuilder(safeEnd - safeStart)
    for (i in safeStart until safeEnd) {
        if ((i < syntheticHyphenDisplayOffsets.size && syntheticHyphenDisplayOffsets[i]) ||
            displayText[i] == SOFT_HYPHEN_CHAR
        ) {
            continue
        }
        out.append(displayText[i])
    }
    return out.toString()
}

internal fun mapDisplayOffsetToSourceOffset(
    displayOffset: Int,
    displayToSourceOffsets: IntArray?,
    fallbackLength: Int,
): Int {
    if (displayToSourceOffsets == null || displayToSourceOffsets.isEmpty()) {
        return displayOffset.coerceIn(0, fallbackLength)
    }
    return displayToSourceOffsets[displayOffset.coerceIn(0, displayToSourceOffsets.lastIndex)]
}

/**
 * Resolves the display character range for a word tap/long-press at [displayOffset], coalescing
 * halves of any word split across lines by synthetic hyphens so dictionary/translation lookup
 * receives the whole unsplit word.
 */
internal fun resolveReaderWordDisplayRangeForOffset(
    displayText: CharSequence,
    displayOffset: Int,
    syntheticHyphenDisplayOffsets: BooleanArray? = null,
    locale: Locale = Locale.getDefault(),
): IntRange? {
    if (displayText.isBlank()) return null
    val len = displayText.length
    if (syntheticHyphenDisplayOffsets == null) {
        val safeOffset = displayOffset.coerceIn(0, len - 1)
        val wordIterator = BreakIterator.getWordInstance(locale).apply {
            setText(displayText.toString())
        }
        val start = if (wordIterator.isBoundary(safeOffset)) {
            safeOffset
        } else {
            wordIterator.preceding(safeOffset)
        }
        val end = wordIterator.following(safeOffset)
        if (start < 0 || end < 0 || start >= end) return null
        return if (displayText.subSequence(start, end).isBlank()) null else start until end
    }

    val clean = StringBuilder(len)
    val cleanToDisplayStart = IntArray(len + 1)
    val cleanToDisplayEnd = IntArray(len + 1)
    val displayToClean = IntArray(len + 1)
    for (d in 0 until len) {
        displayToClean[d] = clean.length
        if ((d < syntheticHyphenDisplayOffsets.size && syntheticHyphenDisplayOffsets[d]) ||
            displayText[d] == SOFT_HYPHEN_CHAR
        ) {
            continue
        }
        cleanToDisplayStart[clean.length] = d
        clean.append(displayText[d])
        cleanToDisplayEnd[clean.length] = d + 1
    }
    displayToClean[len] = clean.length
    if (clean.isEmpty()) return null

    val cleanStr = clean.toString()
    val safeCleanOffset = displayToClean[displayOffset.coerceIn(0, len)].coerceIn(0, cleanStr.length - 1)
    val wordIterator = BreakIterator.getWordInstance(locale).apply {
        setText(cleanStr)
    }
    val cleanStart = if (wordIterator.isBoundary(safeCleanOffset)) {
        safeCleanOffset
    } else {
        wordIterator.preceding(safeCleanOffset)
    }
    val cleanEnd = wordIterator.following(safeCleanOffset)
    if (cleanStart < 0 || cleanEnd < 0 || cleanStart >= cleanEnd) return null
    if (cleanStr.substring(cleanStart, cleanEnd).isBlank()) return null
    val dispStart = cleanToDisplayStart[cleanStart]
    val dispEnd = cleanToDisplayEnd[cleanEnd].coerceAtLeast(dispStart + 1)
    return dispStart until dispEnd
}

/**
 * Normalizes inter-letter curly apostrophes (`’` -> `'`) 1:1 in character length so ICU/platform
 * line breakers do not split contractions like `I’ll` or `isn’t` across lines while preserving all
 * source character offsets and spans untouched.
 */
internal fun normalizeCurlyApostrophesForDisplay(source: CharSequence): CharSequence {
    if (source.isEmpty()) return source
    val firstCurly = source.indexOf('\u2019')
    if (firstCurly < 0) return source
    val length = source.length
    var hasInterLetterCurly = false
    for (idx in firstCurly until length) {
        if (source[idx] == '\u2019' &&
            idx > 0 &&
            idx + 1 < length &&
            source[idx - 1].isLetter() &&
            source[idx + 1].isLetter()
        ) {
            hasInterLetterCurly = true
            break
        }
    }
    if (!hasInterLetterCurly) return source

    val builder = SpannableStringBuilder(source)
    for (idx in firstCurly until length) {
        if (builder[idx] == '\u2019' &&
            idx > 0 &&
            idx + 1 < length &&
            builder[idx - 1].isLetter() &&
            builder[idx + 1].isLetter()
        ) {
            builder.replace(idx, idx + 1, "'")
        }
    }
    return builder
}

/**
 * Materializes visible ASCII line-break hyphens (`-`) strictly at [sortedSourceBreakOffsets] inside
 * [normalizedSource], preserving all spans and building [PreparedHyphenatedText.displayToSourceOffsets]
 * and [PreparedHyphenatedText.syntheticHyphenDisplayOffsets].
 */
internal fun buildPreparedHyphenatedTextFromSourceBreaks(
    normalizedSource: CharSequence,
    sortedSourceBreakOffsets: IntArray,
): PreparedHyphenatedText {
    if (normalizedSource.isEmpty() || sortedSourceBreakOffsets.isEmpty()) {
        return PreparedHyphenatedText(
            text = normalizedSource,
            displayToSourceOffsets = null,
            syntheticHyphenDisplayOffsets = null,
        )
    }
    val breaks = sortedSourceBreakOffsets.map { SyntheticHyphenBreak(sourceOffset = it) }
    return buildPreparedHyphenatedTextFromBreaks(
        normalizedSource = normalizedSource,
        breaks = breaks,
    )
}

internal fun buildPreparedHyphenatedTextFromBreaks(
    normalizedSource: CharSequence,
    breaks: List<SyntheticHyphenBreak>,
): PreparedHyphenatedText {
    if (normalizedSource.isEmpty() || breaks.isEmpty()) {
        return PreparedHyphenatedText(
            text = normalizedSource,
            displayToSourceOffsets = null,
            syntheticHyphenDisplayOffsets = null,
        )
    }

    val length = normalizedSource.length
    val breakTokenBySourceIndex = arrayOfNulls<String>(length)
    var extraDisplayChars = 0
    for (item in breaks) {
        val offset = item.sourceOffset
        if (offset in 1 until length && breakTokenBySourceIndex[offset] == null && item.token.isNotEmpty()) {
            breakTokenBySourceIndex[offset] = item.token
            extraDisplayChars += item.token.length
        }
    }
    if (extraDisplayChars == 0) {
        return PreparedHyphenatedText(
            text = normalizedSource,
            displayToSourceOffsets = null,
            syntheticHyphenDisplayOffsets = null,
        )
    }

    val displayLen = length + extraDisplayChars
    val out = StringBuilder(displayLen)
    val displayToSource = IntArray(displayLen + 1)
    val isSyntheticHyphen = BooleanArray(displayLen)
    val sourceToDisplayStart = IntArray(length + 1)
    val sourceToDisplayEnd = IntArray(length + 1)
    var displayCursor = 0

    for (idx in 0 until length) {
        val token = breakTokenBySourceIndex[idx]
        if (token != null) {
            for (k in 0 until token.length) {
                out.append(token[k])
                displayToSource[displayCursor] = idx
                isSyntheticHyphen[displayCursor] = true
                displayCursor++
            }
        }
        sourceToDisplayStart[idx] = out.length
        out.append(normalizedSource[idx])
        displayToSource[displayCursor] = idx
        displayCursor++
        sourceToDisplayEnd[idx + 1] = out.length
    }
    sourceToDisplayStart[length] = out.length
    sourceToDisplayEnd[0] = 0
    displayToSource[displayCursor] = length

    val resultText: CharSequence = if (normalizedSource is Spanned) {
        SpannableStringBuilder(out.toString()).apply {
            val spans = normalizedSource.getSpans(0, length, Any::class.java)
            for (span in spans) {
                val rawStart = normalizedSource.getSpanStart(span).coerceIn(0, length)
                val rawEnd = normalizedSource.getSpanEnd(span).coerceIn(rawStart, length)
                if (rawStart > rawEnd) continue
                val flags = normalizedSource.getSpanFlags(span)
                val mappedStart = sourceToDisplayStart[rawStart]
                val mappedEnd = if (rawStart == rawEnd) {
                    mappedStart
                } else {
                    sourceToDisplayEnd[rawEnd].coerceAtLeast(mappedStart)
                }
                if (mappedStart <= mappedEnd && mappedEnd <= this.length) {
                    setSpan(span, mappedStart, mappedEnd, flags)
                }
            }
        }
    } else {
        out.toString()
    }

    return PreparedHyphenatedText(
        text = resultText,
        displayToSourceOffsets = displayToSource,
        syntheticHyphenDisplayOffsets = isSyntheticHyphen,
    )
}

/**
 * Resolves visible line-end hyphens (`-`) against the real [widthPx] and font metrics using
 * greedy syllable fitting: a synthetic hyphen is kept ONLY when it immediately triggers a clean
 * line wrap right after `-` in [buildWholeLayout]. Words that fit without wrapping stay 100%
 * unaltered, eliminating vendor soft-hyphen rendering bugs and mid-line dashes.
 */
internal fun prepareWidthHyphenatedReaderText(
    source: CharSequence,
    widthPx: Int,
    paint: TextPaint,
    firstLineIndentPx: Int?,
    hyphenationEnabled: Boolean,
    buildWholeLayout: (CharSequence) -> Layout,
    buildSliceLayout: (CharSequence) -> Layout,
    justifySpacingEnabled: Boolean = false,
    measureLineNaturalWidth: ((Layout, CharSequence, Int, Int, Int) -> Float)? = null,
): WidthHyphenatedTextResult {
    if ((!hyphenationEnabled && !justifySpacingEnabled) || source.isEmpty() || widthPx <= 1) {
        if (BuildConfig.DEBUG && !hyphenationEnabled && !justifySpacingEnabled &&
            debugHyphenDisabledLogCount.getAndIncrement() < 12
        ) {
            runCatching {
                Log.d(HYPHENATOR_LOG_TAG, "disabled width=$widthPx len=${source.length} head=${source.take(24)}")
            }
        }
        return WidthHyphenatedTextResult(
            prepared = PreparedHyphenatedText(
                text = source,
                displayToSourceOffsets = null,
                syntheticHyphenDisplayOffsets = null,
            ),
            layout = buildWholeLayout(source),
        )
    }

    val normalizedSource = if (hyphenationEnabled) normalizeCurlyApostrophesForDisplay(source) else source
    var currentLayout = buildWholeLayout(normalizedSource)
    if (currentLayout.lineCount <= 1) {
        return WidthHyphenatedTextResult(
            prepared = PreparedHyphenatedText(
                text = normalizedSource,
                displayToSourceOffsets = null,
                syntheticHyphenDisplayOffsets = null,
            ),
            layout = currentLayout,
        )
    }

    val workBuilder = SpannableStringBuilder(normalizedSource)
    val acceptedBreaks = mutableListOf<SyntheticHyphenBreak>()
    val insertedMarkers = mutableListOf<InsertedWorkMarker>()
    var platformRequiresZwspBreak = false
    val hyphenWidthPx = paint.measureText(SYNTHETIC_HYPHEN_CHAR.toString())
    val line0EmbeddedIndentPx = if (normalizedSource is Spanned) {
        normalizedSource.getSpans(0, normalizedSource.length, LeadingMarginSpan::class.java)
            .maxOfOrNull { it.getLeadingMargin(true) } ?: 0
    } else {
        0
    }
    val effectiveLine0IndentPx = maxOf(firstLineIndentPx ?: 0, line0EmbeddedIndentPx)
    val maxIterations = (currentLayout.lineCount * 4).coerceAtLeast(48)
    val debugEvents = if (BuildConfig.DEBUG && hyphenationEnabled) mutableListOf<String>() else null

    fun areMarkersAnchored(rebuilt: Layout, upToWorkOffset: Int): Boolean {
        for (marker in insertedMarkers) {
            if (marker.workOffset >= upToWorkOffset) continue
            val expectedEnd = marker.workOffset + marker.length
            var anchored = false
            for (k in 0 until rebuilt.lineCount - 1) {
                if (rebuilt.getLineEnd(k) == expectedEnd && rebuilt.getLineStart(k + 1) == expectedEnd) {
                    anchored = true
                    break
                }
            }
            if (!anchored) return false
        }
        return true
    }

    if (hyphenationEnabled) {
        var line = 0
        var iterations = 0
        while (line < currentLayout.lineCount - 1 && iterations < maxIterations) {
            iterations++
            val lineStart = currentLayout.getLineStart(line)
            val nextLineStart = currentLayout.getLineStart(line + 1)
            if (nextLineStart <= lineStart || nextLineStart <= 0 || workBuilder[nextLineStart - 1] == '\n') {
                line++
                continue
            }

            // Only hyphenate genuine wrapped word tokens, never emergency mid-word breaks.
            if (!workBuilder[nextLineStart - 1].isWhitespace()) {
                debugEvents?.add("L$line:noWs(${workBuilder[nextLineStart - 1].code})")
                line++
                continue
            }

            var wordStart = nextLineStart
            var openingPunctSkipped = 0
            while (wordStart < workBuilder.length &&
                openingPunctSkipped < 2 &&
                isOpeningHyphenPunctuation(workBuilder[wordStart])
            ) {
                wordStart++
                openingPunctSkipped++
            }
            if (wordStart >= workBuilder.length || !workBuilder[wordStart].isLetter()) {
                debugEvents?.add("L$line:nonLet")
                line++
                continue
            }
            if (isHyphenSeparatorDisallowedNeighbor(workBuilder[wordStart - 1])) {
                debugEvents?.add("L$line:disPrev(${workBuilder[wordStart - 1].code})")
                line++
                continue
            }

            var wordEnd = wordStart
            while (wordEnd < workBuilder.length && workBuilder[wordEnd].isLetter()) {
                wordEnd++
            }
            if (wordEnd < workBuilder.length && isHyphenSeparatorDisallowedNeighbor(workBuilder[wordEnd])) {
                debugEvents?.add("L$line:disNext(${workBuilder[wordEnd].code})")
                line++
                continue
            }
            if (wordEnd - wordStart < MIN_HYPHEN_WORD_LENGTH) {
                line++
                continue
            }

            var tokenEnd = wordEnd
            while (tokenEnd < workBuilder.length && isTrailingHyphenPunctuation(workBuilder[tokenEnd])) {
                tokenEnd++
            }

            val word = workBuilder.subSequence(wordStart, wordEnd).toString()
            val breaks = findReaderWordHyphenBreaks(word)
            if (breaks.isEmpty()) {
                if (word.length >= 6) {
                    debugEvents?.add("L$line:noBr($word)")
                }
                line++
                continue
            }

            val lineIndentPx = if (line == 0) effectiveLine0IndentPx else 0
            val minLineWidthPx = lineIndentPx + paint.measureText(workBuilder, lineStart, nextLineStart)
            val minShortestPrefixWidthPx = paint.measureText(workBuilder, wordStart, wordStart + breaks[0]) +
                hyphenWidthPx
            if (minLineWidthPx + minShortestPrefixWidthPx > widthPx + paint.textSize) {
                debugEvents?.add(
                    "L$line:$word$breaks:pref(" +
                        "${minLineWidthPx.toInt()}+${minShortestPrefixWidthPx.toInt()}>" +
                        "${(widthPx + paint.textSize).toInt()})",
                )
                line++
                continue
            }

            var acceptedForLine = false
            var lastRejectDetail: String? = null
            val bareToken = SYNTHETIC_HYPHEN_CHAR.toString()
            val localWordStart = wordStart - lineStart
            for (breakIdx in breaks.lastIndex downTo 0) {
                val b = breaks[breakIdx]
                val candidateTokens = if (platformRequiresZwspBreak) {
                    listOf(SYNTHETIC_HYPHEN_WITH_ZWSP)
                } else {
                    listOf(bareToken)
                }
                var acceptedMatch: Pair<String, HyphenBreakAttempt.Accepted>? = null

                fun tryCandidate(token: String): Pair<String, HyphenBreakAttempt.Accepted>? {
                    return when (
                        val result = tryBuildHyphenBreakLayout(
                            workBuilder = workBuilder,
                            line = line,
                            lineStart = lineStart,
                            wordStart = wordStart,
                            tokenEnd = tokenEnd,
                            breakInWord = b,
                            token = token,
                            buildSliceLayout = buildSliceLayout,
                            buildWholeLayout = buildWholeLayout,
                            arePreviousMarkersAnchored = { rebuilt, upTo ->
                                areMarkersAnchored(rebuilt, upTo)
                            },
                        )
                    ) {
                        is HyphenBreakAttempt.Accepted -> token to result
                        is HyphenBreakAttempt.Rejected -> {
                            lastRejectDetail = result.detail
                            if (token == bareToken && result.wrappedBeforeWord) {
                                when (
                                    val zwspResult = tryBuildHyphenBreakLayout(
                                        workBuilder = workBuilder,
                                        line = line,
                                        lineStart = lineStart,
                                        wordStart = wordStart,
                                        tokenEnd = tokenEnd,
                                        breakInWord = b,
                                        token = SYNTHETIC_HYPHEN_WITH_ZWSP,
                                        buildSliceLayout = buildSliceLayout,
                                        buildWholeLayout = buildWholeLayout,
                                        arePreviousMarkersAnchored = { rebuilt, upTo ->
                                            areMarkersAnchored(rebuilt, upTo)
                                        },
                                    )
                                ) {
                                    is HyphenBreakAttempt.Accepted -> {
                                        platformRequiresZwspBreak = true
                                        SYNTHETIC_HYPHEN_WITH_ZWSP to zwspResult
                                    }
                                    is HyphenBreakAttempt.Rejected -> {
                                        lastRejectDetail = "${result.detail}|zw:${zwspResult.detail}"
                                        null
                                    }
                                }
                            } else {
                                null
                            }
                        }
                    }
                }

                for (token in candidateTokens) {
                    acceptedMatch = tryCandidate(token)
                    if (acceptedMatch != null) break
                }

                if (acceptedMatch != null) {
                    val (usedToken, acceptedOutcome) = acceptedMatch
                    val workInsert = acceptedOutcome.workInsertOffset
                    val priorSyntheticChars = insertedMarkers.sumOf { marker ->
                        if (marker.workOffset < workInsert) marker.length else 0
                    }
                    val sourceInsertOffset = workInsert - priorSyntheticChars
                    insertedMarkers.add(InsertedWorkMarker(workOffset = workInsert, length = usedToken.length))
                    acceptedBreaks.add(SyntheticHyphenBreak(sourceOffset = sourceInsertOffset, token = usedToken))
                    currentLayout = acceptedOutcome.rebuiltLayout
                    acceptedForLine = true
                    val suffixTag = if (usedToken.length > 1) "(zwsp)" else ""
                    debugEvents?.add("L$line:$word@$b:OK$suffixTag")
                    break
                }
                if (localWordStart < 0) break
            }
            if (!acceptedForLine && lastRejectDetail != null) {
                debugEvents?.add("L$line:$word$breaks:$lastRejectDetail")
            }
            line++
        }
        if (BuildConfig.DEBUG && !debugEvents.isNullOrEmpty() && debugHyphenBlockLogCount.getAndIncrement() < 180) {
            runCatching {
                val head = normalizedSource.take(24).toString().replace('\n', ' ')
                Log.d(
                    HYPHENATOR_LOG_TAG,
                    "w=$widthPx ind=$effectiveLine0IndentPx ts=${paint.textSize} lc=${currentLayout.lineCount} " +
                        "len=${source.length} ins=${acceptedBreaks.size} head='$head' | " +
                        debugEvents.joinToString("; "),
                )
            }
        }
    }

    val prepared = if (acceptedBreaks.isEmpty()) {
        PreparedHyphenatedText(
            text = normalizedSource,
            displayToSourceOffsets = null,
            syntheticHyphenDisplayOffsets = null,
        )
    } else {
        buildPreparedHyphenatedTextFromBreaks(
            normalizedSource = normalizedSource,
            breaks = acceptedBreaks,
        )
    }
    if (!justifySpacingEnabled) {
        return WidthHyphenatedTextResult(
            prepared = prepared,
            layout = currentLayout,
        )
    }
    return applyJustifySpacing(
        prepared = prepared,
        origLayout = currentLayout,
        widthPx = widthPx,
        paint = paint,
        effectiveLine0IndentPx = effectiveLine0IndentPx,
        measureLineNaturalWidth = measureLineNaturalWidth,
        buildWholeLayout = buildWholeLayout,
    )
}

internal class JustifySpaceSpan(
    internal val targetWidthPx: Int,
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = targetWidthPx

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val spanned = text as? Spanned ?: return
        val bgSpans = spanned.getSpans(start, end, BackgroundColorSpan::class.java)
        if (bgSpans.isEmpty()) return
        val prevColor = paint.color
        val prevStyle = paint.style
        paint.style = Paint.Style.FILL
        for (bgSpan in bgSpans) {
            paint.color = bgSpan.backgroundColor
            canvas.drawRect(x, top.toFloat(), x + targetWidthPx, bottom.toFloat(), paint)
        }
        paint.style = prevStyle
        paint.color = prevColor
    }
}

private fun applyJustifySpacing(
    prepared: PreparedHyphenatedText,
    origLayout: Layout,
    widthPx: Int,
    paint: TextPaint,
    effectiveLine0IndentPx: Int,
    measureLineNaturalWidth: ((Layout, CharSequence, Int, Int, Int) -> Float)?,
    buildWholeLayout: (CharSequence) -> Layout,
): WidthHyphenatedTextResult {
    val lineCount = origLayout.lineCount
    if (lineCount <= 1 || widthPx <= 1) {
        return WidthHyphenatedTextResult(prepared = prepared, layout = origLayout)
    }
    val origStarts = IntArray(lineCount) { origLayout.getLineStart(it) }
    val builder = (prepared.text as? SpannableStringBuilder)?.let { SpannableStringBuilder(it) }
        ?: SpannableStringBuilder(prepared.text)
    val targetRightPx = widthPx.toFloat() - JUSTIFY_RIGHT_SAFETY_PX
    val targetHyphenRightPx = widthPx.toFloat() - JUSTIFY_HYPHEN_LINE_SAFETY_PX
    val baseSpaceWidth = paint.measureText(" ")
    val floorBaseSpaceWidth = floor(baseSpaceWidth.toDouble()).toInt().coerceAtLeast(1)
    val lineSpans = arrayOfNulls<List<JustifySpaceSpan>>(lineCount)
    val lineBackoffs = IntArray(lineCount)
    var eligibleLines = 0
    var appliedLines = 0

    for (line in 0 until lineCount - 1) {
        val lineStart = origStarts[line]
        val nextLineStart = origStarts[line + 1]
        if (nextLineStart <= lineStart || nextLineStart <= 0 || builder[nextLineStart - 1] == '\n') {
            continue
        }
        var contentEnd = nextLineStart
        while (contentEnd > lineStart && isJustifyTrailingIgnored(builder[contentEnd - 1])) {
            contentEnd--
        }
        if (contentEnd <= lineStart) continue
        var firstNonSpace = lineStart
        while (firstNonSpace < contentEnd && builder[firstNonSpace] == ' ') {
            firstNonSpace++
        }
        if (firstNonSpace >= contentEnd) continue

        val spacePositions = mutableListOf<Int>()
        for (pos in (firstNonSpace + 1) until contentEnd) {
            if (builder[pos] == ' ' &&
                builder.getSpans(pos, pos + 1, UnderlineSpan::class.java).isEmpty() &&
                builder.getSpans(pos, pos + 1, StrikethroughSpan::class.java).isEmpty()
            ) {
                spacePositions += pos
            }
        }
        if (spacePositions.isEmpty()) continue
        eligibleLines++

        val endsWithSyntheticHyphen = contentEnd < nextLineStart &&
            builder[contentEnd - 1] == SYNTHETIC_HYPHEN_CHAR &&
            builder[contentEnd] == SOFT_BREAK_ZWSP_CHAR
        val lineTargetRightPx = if (endsWithSyntheticHyphen) targetHyphenRightPx else targetRightPx

        val naturalWidthPx = measureLineNaturalWidth?.invoke(
            origLayout,
            builder,
            line,
            lineStart,
            contentEnd,
        ) ?: run {
            val indent = if (line == 0 || (lineStart > 0 && builder[lineStart - 1] == '\n')) {
                effectiveLine0IndentPx
            } else {
                0
            }
            indent + paint.measureText(builder, lineStart, contentEnd)
        }
        val deficitPx = lineTargetRightPx - naturalWidthPx
        if (deficitPx < JUSTIFY_MIN_DEFICIT_PX) continue

        val isLtrLine = runCatching {
            origLayout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT
        }.getOrDefault(false)
        val maxReasonableSpaceWidth = (paint.textSize * 2f).coerceAtLeast(baseSpaceWidth * 2f)
        var totalSpaceWidthPx = 0f
        for (pos in spacePositions) {
            val layoutAdvance = if (isLtrLine) {
                runCatching {
                    if (!origLayout.isRtlCharAt(pos)) {
                        origLayout.getPrimaryHorizontal(pos + 1) - origLayout.getPrimaryHorizontal(pos)
                    } else {
                        -1f
                    }
                }.getOrDefault(-1f)
            } else {
                -1f
            }
            val measured = if (layoutAdvance in 1f..maxReasonableSpaceWidth) {
                layoutAdvance
            } else {
                paint.measureText(builder, pos, pos + 1)
            }
            totalSpaceWidthPx += if (measured > 0f) measured else baseSpaceWidth
        }
        if (totalSpaceWidthPx <= 0f) continue

        val maxScale = if (spacePositions.size == 1) {
            JUSTIFY_MAX_SINGLE_SPACE_SCALE
        } else {
            JUSTIFY_MAX_SPACE_SCALE
        }
        val maxExtraPx = totalSpaceWidthPx * (maxScale - 1f)
        val allowedExtraPx = minOf(deficitPx, maxExtraPx)
        if (allowedExtraPx < JUSTIFY_MIN_DEFICIT_PX) continue

        val spaceCount = spacePositions.size
        val targetTotalInt = floor((totalSpaceWidthPx + allowedExtraPx).toDouble()).toInt()
        if (targetTotalInt <= floor(totalSpaceWidthPx.toDouble()).toInt()) continue

        val baseWidthInt = (targetTotalInt / spaceCount).coerceAtLeast(1)
        val remainder = (targetTotalInt % spaceCount).coerceAtLeast(0)
        val spansForLine = ArrayList<JustifySpaceSpan>(spaceCount)
        var prevBucket = 0
        for (idx in 0 until spaceCount) {
            val pos = spacePositions[idx]
            val nextBucket = ((idx + 1) * remainder) / spaceCount
            val spaceWidthInt = baseWidthInt + if (nextBucket > prevBucket) 1 else 0
            prevBucket = nextBucket
            val span = JustifySpaceSpan(spaceWidthInt)
            builder.setSpan(span, pos, pos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spansForLine += span
        }
        lineSpans[line] = spansForLine
        appliedLines++
    }

    if (appliedLines == 0) {
        if (BuildConfig.DEBUG && debugJustifyLogCount.getAndIncrement() < 120) {
            runCatching {
                val head = prepared.text.take(24).toString().replace('\n', ' ')
                Log.i(HYPHENATOR_LOG_TAG, "justify w=$widthPx lc=$lineCount js=0/$eligibleLines head='$head'")
            }
        }
        return WidthHyphenatedTextResult(prepared = prepared, layout = origLayout)
    }

    var rebuilt = buildWholeLayout(builder)
    var rollbackCount = 0
    val maxRollbackSteps = lineCount * 3
    while (rollbackCount < maxRollbackSteps) {
        val firstShiftedLine = findFirstShiftedJustifyLine(origStarts, rebuilt) ?: break
        val dropLine = (firstShiftedLine - 1).coerceAtLeast(0)
        // Prefer backing off 1px per space (up to 2 passes) before abandoning justification on
        // the shifted line so marginal 1-2px overflows stay right-aligned instead of snapping left.
        if (tryBackoffJustifySpansForLine(builder, lineSpans, lineBackoffs, dropLine, floorBaseSpaceWidth)) {
            rollbackCount++
            rebuilt = buildWholeLayout(builder)
            continue
        }
        var dropped = dropJustifySpansForLine(builder, lineSpans, dropLine)
        if (!dropped && firstShiftedLine < lineCount) {
            dropped = dropJustifySpansForLine(builder, lineSpans, firstShiftedLine)
        }
        if (!dropped) {
            for (candidate in dropLine downTo 0) {
                if (dropJustifySpansForLine(builder, lineSpans, candidate)) {
                    dropped = true
                    break
                }
            }
        }
        if (!dropped) {
            appliedLines = 0
            rebuilt = origLayout
            break
        }
        appliedLines--
        rollbackCount++
        rebuilt = buildWholeLayout(builder)
    }

    if (BuildConfig.DEBUG && debugJustifyLogCount.getAndIncrement() < 120) {
        runCatching {
            val head = prepared.text.take(24).toString().replace('\n', ' ')
            Log.i(
                HYPHENATOR_LOG_TAG,
                "justify w=$widthPx lc=$lineCount js=$appliedLines/$eligibleLines rb=$rollbackCount head='$head'",
            )
        }
    }

    if (appliedLines <= 0) {
        return WidthHyphenatedTextResult(prepared = prepared, layout = origLayout)
    }
    return WidthHyphenatedTextResult(
        prepared = prepared.copy(text = builder),
        layout = rebuilt,
    )
}

internal fun doLayoutsMatchLineStarts(expected: Layout, actual: Layout): Boolean {
    if (actual.lineCount != expected.lineCount) return false
    for (line in 0 until expected.lineCount) {
        if (actual.getLineStart(line) != expected.getLineStart(line)) return false
    }
    return true
}

internal fun hasMidLineSyntheticHyphen(layout: Layout, prepared: PreparedHyphenatedText): Boolean {
    val syntheticOffsets = prepared.syntheticHyphenDisplayOffsets ?: return false
    val text = prepared.text
    val limit = minOf(text.length, syntheticOffsets.size)
    val lineCount = layout.lineCount
    if (lineCount <= 0) return false
    val lineEnds = HashSet<Int>(lineCount)
    for (line in 0 until lineCount - 1) {
        lineEnds += layout.getLineEnd(line)
    }
    for (idx in 0 until limit) {
        if (!syntheticOffsets[idx] || text[idx] != SYNTHETIC_HYPHEN_CHAR) continue
        val afterHyphen = idx + 1
        val afterZwsp = if (afterHyphen < text.length && text[afterHyphen] == SOFT_BREAK_ZWSP_CHAR) {
            afterHyphen + 1
        } else {
            afterHyphen
        }
        if (afterHyphen !in lineEnds && afterZwsp !in lineEnds) {
            return true
        }
    }
    return false
}

private fun isJustifyTrailingIgnored(ch: Char): Boolean {
    return ch == '\n' || ch == ' ' || ch == '\t' || ch == SOFT_BREAK_ZWSP_CHAR
}

private fun findFirstShiftedJustifyLine(origStarts: IntArray, rebuilt: Layout): Int? {
    val checkCount = minOf(origStarts.size, rebuilt.lineCount)
    for (line in 0 until checkCount) {
        if (rebuilt.getLineStart(line) != origStarts[line]) {
            return line
        }
    }
    if (rebuilt.lineCount != origStarts.size) {
        return (checkCount - 1).coerceAtLeast(0)
    }
    return null
}

private fun tryBackoffJustifySpansForLine(
    builder: SpannableStringBuilder,
    lineSpans: Array<List<JustifySpaceSpan>?>,
    lineBackoffs: IntArray,
    line: Int,
    floorBaseSpaceWidth: Int,
): Boolean {
    if (line !in lineBackoffs.indices || lineBackoffs[line] >= 2) return false
    val spans = lineSpans.getOrNull(line) ?: return false
    if (spans.isEmpty() || spans.any { it.targetWidthPx <= floorBaseSpaceWidth + 1 }) return false
    val updated = ArrayList<JustifySpaceSpan>(spans.size)
    for (span in spans) {
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        if (start < 0 || end <= start) return false
        val flags = builder.getSpanFlags(span)
        builder.removeSpan(span)
        val shrunk = JustifySpaceSpan(span.targetWidthPx - 1)
        builder.setSpan(shrunk, start, end, flags)
        updated += shrunk
    }
    lineSpans[line] = updated
    lineBackoffs[line]++
    return true
}

private fun dropJustifySpansForLine(
    builder: SpannableStringBuilder,
    lineSpans: Array<List<JustifySpaceSpan>?>,
    line: Int,
): Boolean {
    val spans = lineSpans.getOrNull(line) ?: return false
    for (span in spans) {
        builder.removeSpan(span)
    }
    lineSpans[line] = null
    return true
}

private fun tryBuildHyphenBreakLayout(
    workBuilder: SpannableStringBuilder,
    line: Int,
    lineStart: Int,
    wordStart: Int,
    tokenEnd: Int,
    breakInWord: Int,
    token: String,
    buildSliceLayout: (CharSequence) -> Layout,
    buildWholeLayout: (CharSequence) -> Layout,
    arePreviousMarkersAnchored: (Layout, Int) -> Boolean,
    requireSliceProbe: Boolean = true,
): HyphenBreakAttempt {
    if (requireSliceProbe) {
        val slice = SpannableStringBuilder(workBuilder, lineStart, tokenEnd)
        if (line > 0) {
            slice.getSpans(0, slice.length, LeadingMarginSpan::class.java).forEach {
                slice.removeSpan(it)
            }
        }
        val localWordStart = wordStart - lineStart
        val localInsert = localWordStart + breakInWord
        slice.insert(localInsert, token)
        val probeLayout = buildSliceLayout(slice)
        val expectedBreakEnd = localInsert + token.length
        if (probeLayout.lineCount < 2 || probeLayout.getLineEnd(0) != expectedBreakEnd) {
            val line0End = if (probeLayout.lineCount > 0) probeLayout.getLineEnd(0) else -1
            return HyphenBreakAttempt.Rejected(
                detail = "probe(${probeLayout.lineCount}:$line0End!=$expectedBreakEnd)",
                wrappedBeforeWord = probeLayout.lineCount >= 2 && line0End <= localWordStart,
            )
        }
    }
    val workInsert = wordStart + breakInWord
    workBuilder.insert(workInsert, token)
    val rebuilt = buildWholeLayout(workBuilder)
    val expectedNextStart = workInsert + token.length
    if (
        line + 1 < rebuilt.lineCount &&
        rebuilt.getLineEnd(line) == expectedNextStart &&
        rebuilt.getLineStart(line + 1) == expectedNextStart &&
        arePreviousMarkersAnchored(rebuilt, workInsert)
    ) {
        return HyphenBreakAttempt.Accepted(
            rebuiltLayout = rebuilt,
            workInsertOffset = workInsert,
        )
    }
    val nextStart = if (line + 1 < rebuilt.lineCount) rebuilt.getLineStart(line + 1) else -1
    val wrappedBeforeWord = !requireSliceProbe &&
        line + 1 < rebuilt.lineCount &&
        rebuilt.getLineEnd(line) <= wordStart
    val detail = if (line + 1 < rebuilt.lineCount &&
        rebuilt.getLineEnd(line) == expectedNextStart &&
        rebuilt.getLineStart(line + 1) == expectedNextStart
    ) {
        "shiftPrev"
    } else {
        "rebuilt(${rebuilt.lineCount}:${rebuilt.getLineEnd(line)}/$nextStart!=$expectedNextStart)"
    }
    workBuilder.delete(workInsert, workInsert + token.length)
    return HyphenBreakAttempt.Rejected(detail = detail, wrappedBeforeWord = wrappedBeforeWord)
}

/**
 * If [offset] splits the middle of a contiguous letter run (`a[offset]b`), returns the start of
 * that word run so page breaks move the whole word onto the next page cleanly. Returns null when
 * [offset] is already at a non-letter or clean word boundary.
 */
internal fun resolveWordStartBeforeOffset(
    text: CharSequence,
    offset: Int,
): Int? {
    if (offset <= 0 || offset >= text.length) return null
    if (!text[offset - 1].isLetter() || !text[offset].isLetter()) return null
    var start = offset - 1
    while (start > 0 && text[start - 1].isLetter()) {
        start--
    }
    return start
}

private fun isOpeningHyphenPunctuation(ch: Char): Boolean {
    return ch == '"' ||
        ch == '\'' ||
        ch == '\u201C' ||
        ch == '\u2018' ||
        ch == '(' ||
        ch == '[' ||
        ch == '\u00AB'
}

private fun isTrailingHyphenPunctuation(ch: Char): Boolean {
    return ch == '.' ||
        ch == ',' ||
        ch == ';' ||
        ch == ':' ||
        ch == '!' ||
        ch == '?' ||
        ch == '\'' ||
        ch == '\u2019' ||
        ch == '"' ||
        ch == '\u201D' ||
        ch == ')' ||
        ch == ']' ||
        ch == '\u00BB'
}

private fun isHyphenSeparatorDisallowedNeighbor(ch: Char): Boolean {
    return ch == SYNTHETIC_HYPHEN_CHAR ||
        ch == SOFT_HYPHEN_CHAR ||
        ch == '_' ||
        ch.isDigit()
}

internal fun findReaderWordHyphenBreaks(word: String): List<Int> {
    if (word.length < MIN_HYPHEN_WORD_LENGTH) return emptyList()
    // Skip all-uppercase acronyms (HTML, NASA).
    if (word.all { it.isUpperCase() }) return emptyList()

    var hasCyrillic = false
    var hasAsciiLatin = false
    for (ch in word) {
        when {
            ch in '\u0400'..'\u04FF' -> hasCyrillic = true
            (ch in 'A'..'Z') || (ch in 'a'..'z') -> hasAsciiLatin = true
            else -> return emptyList()
        }
    }
    if (hasCyrillic && hasAsciiLatin) return emptyList()
    return when {
        hasCyrillic -> findRussianWordHyphenBreaks(word.lowercase(Locale.forLanguageTag("ru")))
        hasAsciiLatin -> findEnglishWordHyphenBreaks(word.lowercase(Locale.ROOT))
        else -> emptyList()
    }
}

private const val MIN_HYPHEN_WORD_LENGTH = 4

private val RUSSIAN_VOWELS = setOf('а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я')
private val RUSSIAN_ATTACH_LEFT_SIGNS = setOf('ь', 'ъ', 'й')

private fun findRussianWordHyphenBreaks(lower: String): List<Int> {
    val len = lower.length
    if (len < 4) return emptyList()

    val vowelIndices = mutableListOf<Int>()
    for (i in 0 until len) {
        if (lower[i] in RUSSIAN_VOWELS) vowelIndices += i
    }
    if (vowelIndices.size < 2) return emptyList()

    val breaks = mutableListOf<Int>()
    for (k in 0 until vowelIndices.lastIndex) {
        val v1 = vowelIndices[k]
        val v2 = vowelIndices[k + 1]
        val clusterLen = v2 - v1 - 1
        val candidate = when {
            clusterLen == 0 -> v1 + 1
            clusterLen == 1 -> {
                val c = lower[v1 + 1]
                if (c in RUSSIAN_ATTACH_LEFT_SIGNS) v1 + 2 else v1 + 1
            }
            else -> {
                var pos = v1 + 2
                if (pos < v2 && lower[pos] in RUSSIAN_ATTACH_LEFT_SIGNS) {
                    pos++
                }
                pos
            }
        }
        if (candidate >= 2 &&
            len - candidate >= 2 &&
            candidate <= v2 &&
            lower[candidate] !in RUSSIAN_ATTACH_LEFT_SIGNS &&
            lower[candidate] != 'ы'
        ) {
            if (breaks.isEmpty() || candidate - breaks.last() >= 2) {
                breaks += candidate
            }
        }
    }
    return breaks
}

private val ENGLISH_LONG_PREFIXES = listOf(
    "counter",
    "under",
    "inter",
    "super",
    "trans",
    "after",
    "over",
    "non",
    "dis",
    "mis",
    "pre",
    "pro",
    "sub",
    "out",
    "for",
    "con",
    "com",
)

private val ENGLISH_SHORT_PREFIXES = listOf("un", "in", "im")

private val ENGLISH_SUFFIXES = listOf(
    "ously",
    "ation",
    "tion",
    "sion",
    "ment",
    "ness",
    "less",
    "able",
    "ible",
    "ance",
    "ence",
    "ious",
    "eous",
    "ful",
    "ing",
    "est",
    "ish",
    "ous",
)

private val ENGLISH_DIGRAPHS = setOf(
    "ch",
    "sh",
    "th",
    "ph",
    "wh",
    "qu",
    "ck",
    "gh",
    "kn",
    "wr",
    "gn",
)

private val ENGLISH_DIPHTHONGS = setOf(
    "ai",
    "au",
    "aw",
    "ay",
    "ea",
    "ee",
    "ei",
    "eu",
    "ew",
    "ey",
    "ie",
    "oa",
    "oe",
    "oi",
    "oo",
    "ou",
    "ow",
    "oy",
)

private fun findEnglishWordHyphenBreaks(lower: String): List<Int> {
    val len = lower.length
    if (len < 5) return emptyList()

    val candidates = mutableSetOf<Int>()

    // 1. Morphological prefix break.
    val matchedLongPrefix = ENGLISH_LONG_PREFIXES.firstOrNull { lower.startsWith(it) && len - it.length >= 4 }
    if (matchedLongPrefix != null) {
        candidates += matchedLongPrefix.length
    } else {
        val matchedShortPrefix = ENGLISH_SHORT_PREFIXES.firstOrNull { lower.startsWith(it) && len - it.length >= 4 }
        if (matchedShortPrefix != null && !isEnglishVowel(lower[matchedShortPrefix.length])) {
            candidates += matchedShortPrefix.length
        }
    }

    // 2. Morphological suffix break.
    var matchedSuffixCut: Int? = null
    for (suffix in ENGLISH_SUFFIXES) {
        if (lower.endsWith(suffix)) {
            val cut = len - suffix.length
            if (cut >= 3) {
                if (suffix == "ing" &&
                    cut >= 4 &&
                    lower[cut - 1] == lower[cut - 2] &&
                    !isEnglishVowel(lower[cut - 1])
                ) {
                    // Doubled consonant before -ing (car-ry-ing / stop-ping -> break between double).
                    candidates += cut - 1
                }
                candidates += cut
                matchedSuffixCut = cut
                break
            }
        }
    }

    // 3. Phonological VC-CV / V-CV syllable boundaries.
    val nuclei = findEnglishVowelNuclei(lower)
    for (k in 0 until nuclei.lastIndex) {
        val (n1Start, n1End) = nuclei[k]
        val (n2Start, _) = nuclei[k + 1]
        val consCount = n2Start - n1End
        val cut = when {
            consCount == 0 -> {
                val pair = lower.substring(n1End - 1, (n1End + 1).coerceAtMost(len))
                if (pair in ENGLISH_DIPHTHONGS) -1 else n1End
            }
            consCount == 1 -> {
                val c = lower[n1End]
                if (c == 'x') n1End + 1 else n1End
            }
            else -> {
                val c1c2 = lower.substring(n1End, n1End + 2)
                if (c1c2 in ENGLISH_DIGRAPHS) n1End else n1End + 1
            }
        }
        val collidesWithSuffix = matchedSuffixCut != null && kotlin.math.abs(cut - matchedSuffixCut) == 1
        if (cut in 2..(len - 3) && !collidesWithSuffix) {
            if (!breaksInsideDigraph(lower, cut)) {
                candidates += cut
            }
        }
    }

    val sorted = candidates
        .filter { pos -> isValidEnglishSplit(lower, pos) }
        .sorted()

    // Keep parts separated by >= 2 chars and prevent breaks directly overlapping suffixes like -ously.
    val filtered = mutableListOf<Int>()
    for (pos in sorted) {
        val prev = filtered.lastOrNull() ?: 0
        if (pos - prev >= 2 && len - pos >= 3) {
            filtered += pos
        }
    }
    return filtered
}

private fun isEnglishVowel(ch: Char): Boolean {
    return ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u' || ch == 'y'
}

private fun findEnglishVowelNuclei(lower: String): List<Pair<Int, Int>> {
    val len = lower.length
    val nuclei = mutableListOf<Pair<Int, Int>>()
    var i = 0
    while (i < len) {
        val ch = lower[i]
        val isVowelHere = when {
            ch == 'u' && i > 0 && lower[i - 1] == 'q' -> false
            ch == 'y' -> i > 0
            else -> ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u'
        }
        if (!isVowelHere) {
            i++
            continue
        }
        val start = i
        i++
        while (i < len) {
            val next = lower[i]
            val nextIsVowel = next == 'a' || next == 'e' || next == 'i' || next == 'o' || next == 'u' || next == 'y'
            if (!nextIsVowel) break
            i++
        }
        nuclei += start to i
    }
    // Drop final silent -e / -es / -ed nucleus unless it is a consonant + le syllable (re-sem-ble).
    if (nuclei.size >= 2) {
        val last = nuclei.last()
        val lastIsTrailingE = last.first == len - 1 && lower[last.first] == 'e'
        val lastIsTrailingEdEs = last.first == len - 2 && lower[last.first] == 'e' &&
            (lower[len - 1] == 'd' || lower[len - 1] == 's')
        val isConsonantLe = len >= 3 &&
            lower.endsWith("le") &&
            !isEnglishVowel(lower[len - 3])
        if ((lastIsTrailingE && !isConsonantLe) || lastIsTrailingEdEs) {
            nuclei.removeAt(nuclei.lastIndex)
        }
    }
    return nuclei
}

private fun breaksInsideDigraph(lower: String, pos: Int): Boolean {
    if (pos <= 0 || pos >= lower.length) return false
    if (pos >= 2 &&
        lower[pos - 1] == 'u' &&
        (lower[pos - 2] == 'q' || lower[pos - 2] == 'g') &&
        isEnglishVowel(lower[pos])
    ) {
        return true
    }
    val pair = lower.substring(pos - 1, pos + 1)
    return pair in ENGLISH_DIGRAPHS
}

private fun isValidEnglishSplit(lower: String, pos: Int): Boolean {
    if (pos < 2 || lower.length - pos < 3) return false
    if (breaksInsideDigraph(lower, pos)) return false
    val left = lower.substring(0, pos)
    val right = lower.substring(pos)
    if (!hasPronouncedEnglishVowel(left)) return false
    if (!hasPronouncedEnglishVowel(right)) return false
    return true
}

private fun hasPronouncedEnglishVowel(part: String): Boolean {
    if (part.isEmpty()) return false
    // Consonant + le (e.g. "-ble", "-tle") counts as a valid trailing syllable.
    if (part.length >= 3 && part.endsWith("le") && !isEnglishVowel(part[part.length - 3])) {
        return true
    }
    val effective = when {
        part.endsWith("e") && part.length >= 4 -> part.dropLast(1)
        (part.endsWith("ed") || part.endsWith("es")) && part.length >= 4 -> part.dropLast(2)
        else -> part
    }
    return effective.any { isEnglishVowel(it) }
}
