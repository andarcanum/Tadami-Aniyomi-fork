package eu.kanade.presentation.reader.novel

import android.graphics.Paint
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.BoringLayout
import android.text.DynamicLayout
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tadami.aurora.BuildConfig
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichBlockTextAlign
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

internal fun colorToCssHex(color: Color): String {
    fun channelToHex(channel: Float): String {
        return (channel.coerceIn(0f, 1f) * 255f)
            .roundToInt()
            .coerceIn(0, 255)
            .toString(16)
            .padStart(2, '0')
            .uppercase()
    }

    val red = channelToHex(color.red)
    val green = channelToHex(color.green)
    val blue = channelToHex(color.blue)
    val alpha = channelToHex(color.alpha)
    return if (alpha == "FF") {
        "#$red$green$blue"
    } else {
        "#$red$green$blue$alpha"
    }
}

internal fun resolvePageReaderBlocks(
    shouldPaginate: Boolean,
    textBlocks: List<String>,
    paragraphSpacingDp: Int,
    paginate: (List<String>, Int) -> List<String>,
): List<String> {
    val fallbackBlocks = textBlocks.takeIf { it.isNotEmpty() } ?: listOf("")
    if (!shouldPaginate) return fallbackBlocks

    val nonBlankBlocks = textBlocks.filter { it.isNotBlank() }
    if (nonBlankBlocks.isEmpty()) return fallbackBlocks

    return paginate(nonBlankBlocks, paragraphSpacingDp.coerceIn(0, 32)).ifEmpty { fallbackBlocks }
}

internal fun stripPageReaderChapterTitleBlocks(
    textBlocks: List<PlainPageReaderTextBlock>,
    chapterTitle: String?,
): List<PlainPageReaderTextBlock> {
    if (chapterTitle.isNullOrBlank() || textBlocks.isEmpty()) return textBlocks
    val firstBlock = textBlocks.first().text
    return if (isNativeChapterTitleText(firstBlock, chapterTitle)) {
        textBlocks.drop(1)
    } else {
        textBlocks
    }
}

internal fun resolvePageReaderChapterTitleForFiltering(
    showPageChapterTitle: Boolean,
    chapterTitle: String?,
): String? {
    return if (showPageChapterTitle) null else chapterTitle
}

internal fun stripPageReaderChapterTitleRichBlocks(
    richBlocks: List<IndexedValue<NovelRichContentBlock>>,
    chapterTitle: String?,
): List<IndexedValue<NovelRichContentBlock>> {
    if (chapterTitle.isNullOrBlank() || richBlocks.isEmpty()) return richBlocks
    val firstBlock = richBlocks.first().value
    val firstBlockText = buildRichPageReaderBlockAnnotatedText(firstBlock).text
    return if (isNativeChapterTitleText(firstBlockText, chapterTitle)) {
        richBlocks.drop(1)
    } else {
        richBlocks
    }
}

internal fun resolveReaderContentPaddingPx(
    showReaderUi: Boolean,
    basePaddingPx: Int,
): Int {
    return basePaddingPx
}

internal fun resolveWebViewPaddingTopPx(
    statusBarHeightPx: Int,
    @Suppress("UNUSED_PARAMETER") showReaderUi: Boolean,
    @Suppress("UNUSED_PARAMETER") appBarHeightPx: Int,
    basePaddingPx: Int,
    maxStatusBarInsetPx: Int = Int.MAX_VALUE,
): Int {
    val safeStatusInset = statusBarHeightPx
        .coerceAtLeast(0)
        .coerceAtMost(maxStatusBarInsetPx.coerceAtLeast(0))
    return safeStatusInset + basePaddingPx.coerceAtLeast(0)
}

internal fun resolveWebViewPaddingBottomPx(
    navigationBarHeightPx: Int,
    @Suppress("UNUSED_PARAMETER") showReaderUi: Boolean,
    @Suppress("UNUSED_PARAMETER") bottomBarHeightPx: Int,
    basePaddingPx: Int,
): Int {
    return navigationBarHeightPx.coerceAtLeast(0) + basePaddingPx.coerceAtLeast(0)
}

internal data class NovelHorizontalSafeInsetsDp(
    val left: Dp,
    val right: Dp,
)

internal fun resolveNovelHorizontalSafeInsetsDp(
    cutoutLeftPx: Int,
    cutoutRightPx: Int,
    navBarLeftPx: Int,
    navBarRightPx: Int,
    densityScale: Float,
): NovelHorizontalSafeInsetsDp {
    val safeScale = densityScale.takeIf { it > 0f } ?: 1f
    val leftPx = maxOf(cutoutLeftPx.coerceAtLeast(0), navBarLeftPx.coerceAtLeast(0))
    val rightPx = maxOf(cutoutRightPx.coerceAtLeast(0), navBarRightPx.coerceAtLeast(0))
    return NovelHorizontalSafeInsetsDp(
        left = (leftPx / safeScale).dp,
        right = (rightPx / safeScale).dp,
    )
}

@Composable
internal fun rememberNovelHorizontalSafeInsets(
    includeDisplayCutout: Boolean = true,
): NovelHorizontalSafeInsetsDp {
    val density = LocalDensity.current
    val cutout = WindowInsets.displayCutout
    val navigation = WindowInsets.navigationBarsIgnoringVisibility
    // Compose insets remain observable when the platform applies the new side insets after
    // configuration/layout. Physical edges are mapped by each foreground consumer, not the paper.
    return resolveNovelHorizontalSafeInsetsDp(
        cutoutLeftPx = if (includeDisplayCutout) cutout.getLeft(density, LayoutDirection.Ltr) else 0,
        cutoutRightPx = if (includeDisplayCutout) cutout.getRight(density, LayoutDirection.Ltr) else 0,
        navBarLeftPx = navigation.getLeft(density, LayoutDirection.Ltr),
        navBarRightPx = navigation.getRight(density, LayoutDirection.Ltr),
        densityScale = density.density,
    )
}

internal const val AUTO_SCROLL_COOLDOWN_MS = 2000L
internal const val AUTO_SCROLL_SPEED_FACTOR_DELTA = 0.02f
private const val MAX_INLINE_IMAGE_PRECEDING_TEXT_CHARS = 180

internal fun intervalToAutoScrollSpeed(intervalSeconds: Int): Int {
    val clamped = intervalSeconds.coerceIn(1, 60)
    val normalized = (60 - clamped).toFloat() / 59f
    return (1f + normalized * 99f).roundToInt().coerceIn(1, 100)
}

internal fun autoScrollSpeedToInterval(speed: Int): Int {
    val clamped = speed.coerceIn(1, 100)
    val normalized = (clamped - 1).toFloat() / 99f
    return (60f - normalized * 59f).roundToInt().coerceIn(1, 60)
}

internal fun autoScrollPageDelayMs(speed: Int): Long {
    val clamped = speed.coerceIn(1, 100)
    return (10_000 - (clamped - 1) * 80).toLong().coerceIn(2_000L, 10_000L)
}

internal fun autoScrollPageDelayMsForCharacterCount(
    intervalSeconds: Int,
    characterCount: Int,
    adaptiveEnabled: Boolean = true,
): Long {
    val baseDelayMs = intervalSeconds * 1000L
    if (!adaptiveEnabled || characterCount <= 0) return baseDelayMs.coerceIn(2_000L, 60_000L)
    val normalizedFactor = characterCount.toFloat() / 900f
    return (baseDelayMs * normalizedFactor)
        .roundToInt()
        .toLong()
        .coerceIn(2_000L, 60_000L)
}

internal fun plainPageReaderCharacterCount(
    page: List<PlainPageSlice>,
    textBlocks: List<PlainPageReaderTextBlock>,
): Int {
    return page.sumOf { slice ->
        when (slice) {
            is PlainPageSlice.Text -> {
                val text = textBlocks.getOrNull(slice.blockIndex)?.text.orEmpty()
                (slice.range.endExclusive.coerceAtMost(text.length) - slice.range.start.coerceAtLeast(0))
                    .coerceAtLeast(0)
            }
            is PlainPageSlice.Image -> 0
        }
    }
}

internal fun richPageReaderCharacterCount(
    page: List<RichPageSlice>,
    blockTexts: List<RichPageBlockText>,
): Int {
    return page.sumOf { slice ->
        when (slice) {
            is RichPageSlice.Text -> {
                val text = blockTexts.getOrNull(slice.blockIndex)?.text?.text.orEmpty()
                (slice.range.endExclusive.coerceAtMost(text.length) - slice.range.start.coerceAtLeast(0))
                    .coerceAtLeast(0)
            }
            is RichPageSlice.Image -> 0
        }
    }
}

internal fun autoScrollScrollStepPx(speed: Int): Float {
    val clamped = speed.coerceIn(1, 100)
    return 0.5f + (clamped - 1) * (4.5f / 99f)
}

internal fun autoScrollPixelsPerSecond(speed: Int): Float {
    return autoScrollScrollStepPx(speed) * (1_000_000_000f / 16_000_000f)
}

internal fun autoScrollFrameStepPx(
    speed: Int,
    frameDeltaNanos: Long,
): Float {
    val baseStepPx = autoScrollScrollStepPx(speed)
    val normalizedDelta = frameDeltaNanos.coerceIn(1L, 250_000_000L).toFloat() / 16_000_000f
    return (baseStepPx * normalizedDelta).coerceAtLeast(0.05f)
}

internal data class AutoScrollStepResult(
    val stepPx: Int,
    val remainderPx: Float,
)

internal data class AutoScrollUiState(
    val autoScrollEnabled: Boolean,
    val showReaderUi: Boolean,
    val autoScrollExpanded: Boolean,
)

internal fun resolveAutoScrollUiStateOnToggle(
    currentEnabled: Boolean,
    showReaderUi: Boolean,
    autoScrollExpanded: Boolean,
): AutoScrollUiState {
    val toggledEnabled = !currentEnabled
    return if (toggledEnabled) {
        AutoScrollUiState(
            autoScrollEnabled = true,
            showReaderUi = false,
            autoScrollExpanded = false,
        )
    } else {
        AutoScrollUiState(
            autoScrollEnabled = false,
            showReaderUi = showReaderUi,
            autoScrollExpanded = autoScrollExpanded,
        )
    }
}

internal fun resolveInitialAutoScrollEnabled(
    @Suppress("UNUSED_PARAMETER")
    savedPreferenceEnabled: Boolean,
    handoff: NovelAutoScrollHandoffState? = null,
): Boolean {
    return handoff != null
}

internal fun resolveAutoScrollStep(
    frameStepPx: Float,
    previousRemainderPx: Float,
): AutoScrollStepResult {
    val totalStep = frameStepPx.coerceAtLeast(0f) + previousRemainderPx.coerceAtLeast(0f)
    val stepPx = totalStep.toInt().coerceAtLeast(0)
    return AutoScrollStepResult(
        stepPx = stepPx,
        remainderPx = totalStep - stepPx,
    )
}

internal data class TextPageRange(
    val start: Int,
    val endExclusive: Int,
)

internal fun paginateTextIntoPageRanges(
    text: CharSequence,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    firstLineIndentPx: Int? = null,
    density: Float = 1.0f,
    measurement: ReaderTextMeasurement = ReaderTextMeasurement.STATIC_LAYOUT,
    measurementPaint: TextPaint? = null,
    preferDynamicLayout: Boolean = false,
    hyphenationEnabled: Boolean = false,
): List<TextPageRange> {
    if (text.isBlank()) return emptyList()

    val safeWidth = widthPx.coerceAtLeast(1)
    val safeHeight = resolvePageReaderSafeHeightPx(
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
    )
    val resolvedLocale = if (hyphenationEnabled) resolveNovelReaderTextLocale(text) else null
    val hyphenatedResult = if (measurement == ReaderTextMeasurement.APPROXIMATE) {
        null
    } else {
        resolveWidthHyphenatedReaderTextAndLayout(
            source = text,
            widthPx = safeWidth,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = typeface,
            textAlign = textAlign,
            firstLineIndentPx = firstLineIndentPx,
            density = density,
            measurementPaint = measurementPaint,
            preferDynamicLayout = preferDynamicLayout,
            hyphenationEnabled = hyphenationEnabled,
            textLocale = resolvedLocale,
            isPageMode = true,
        )
    }
    val layout = hyphenatedResult?.layout
    val preparedRangeOffsets = hyphenatedResult?.prepared?.displayToSourceOffsets
    if (layout == null) {
        val safeTextSize = textSizePx.coerceAtLeast(1f)
        val approxCharsPerLine = (safeWidth / (safeTextSize * 0.55f)).toInt().coerceAtLeast(15)
        val approxLinesPerPage = (safeHeight / (safeTextSize * lineHeightMultiplier.coerceAtLeast(1f)))
            .toInt()
            .coerceAtLeast(8)
        val chunkSize = (approxCharsPerLine * approxLinesPerPage).coerceAtLeast(120)
        val ranges = mutableListOf<TextPageRange>()
        var start = 0
        while (start < text.length) {
            val end = (start + chunkSize).coerceAtMost(text.length)
            val trimmed = trimTextRange(text, start, end)
            if (trimmed != null) ranges += trimmed
            start = end
        }
        return ranges
    }

    if (layout.lineCount <= 0) return listOf(TextPageRange(0, text.length))

    val ranges = mutableListOf<TextPageRange>()
    var startLine = 0
    while (startLine < layout.lineCount) {
        val slice = resolveStaticLayoutSliceForHeight(
            text = text,
            layout = layout,
            startLine = startLine,
            availableHeight = safeHeight,
            displayToSourceOffsets = preparedRangeOffsets,
        ) ?: break
        ranges += slice.range
        startLine = slice.nextStartLine
    }

    return if (ranges.isNotEmpty()) ranges else listOf(TextPageRange(0, text.length))
}

internal fun paginateAnnotatedTextIntoPages(
    text: AnnotatedString,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    measurement: ReaderTextMeasurement = ReaderTextMeasurement.STATIC_LAYOUT,
): List<AnnotatedString> {
    if (text.text.isBlank()) return emptyList()
    val measuredText: CharSequence = if (measurement == ReaderTextMeasurement.APPROXIMATE) {
        text.text
    } else {
        buildNovelPageReaderSpannableText(text)
    }
    val ranges = paginateTextIntoPageRanges(
        text = measuredText,
        widthPx = widthPx,
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
        measurement = measurement,
    )
    return ranges.map { range ->
        text.subSequence(TextRange(range.start, range.endExclusive))
    }
}

internal fun paginateTextIntoPages(
    text: String,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    measurement: ReaderTextMeasurement = ReaderTextMeasurement.STATIC_LAYOUT,
): List<String> {
    return paginateTextIntoPageRanges(
        text = text,
        widthPx = widthPx,
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
        measurement = measurement,
    ).map { range ->
        text.substring(range.start, range.endExclusive)
    }
}

private fun trimTextRange(
    text: CharSequence,
    startInclusive: Int,
    endExclusive: Int,
): TextPageRange? {
    var start = startInclusive.coerceIn(0, text.length)
    var end = endExclusive.coerceIn(start, text.length)
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    return if (start < end) TextPageRange(start, end) else null
}

internal data class StaticLayoutSlice(
    val range: TextPageRange,
    val nextStartLine: Int,
    val heightPx: Int,
)

internal data class ApproximateTextMetrics(
    val charsPerLine: Int,
    val lineHeightPx: Int,
)

/**
 * How pagination measures text height.
 *
 * [STATIC_LAYOUT] is the production path: the same layout engine the render TextView uses
 * (DynamicLayout on P+ with a StaticLayout fallback), real font metrics, and the same spans the
 * renderer draws. A failure to build both layouts is a bug and must surface instead of silently
 * paginating with different metrics than the TextView renders with.
 *
 * [APPROXIMATE] is a character-count estimate used by plain-JVM tests, where android.text.Layout
 * is unavailable. Callers must opt into it explicitly so production cannot degrade silently.
 */
internal enum class ReaderTextMeasurement {
    STATIC_LAYOUT,
    APPROXIMATE,
}

private data class PageReaderBlockLayoutMetrics(
    val textSizePx: Float,
    val lineHeightMultiplier: Float,
)

internal fun resolveNovelPageReaderBreakStrategy(
    isPageMode: Boolean,
    hyphenationEnabled: Boolean = false,
    textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
): Int {
    return if (isPageMode || hyphenationEnabled || textAlign == ReaderTextAlign.JUSTIFY) {
        // Greedy line breaking stays strictly within widthPx without OptimalLineBreaker's
        // space-shrink overshoot, keeps app-inserted line-end hyphens anchored at line breaks,
        // and guarantees identical line breaks/pagination when toggling SOURCE/LEFT <-> JUSTIFY.
        Layout.BREAK_STRATEGY_SIMPLE
    } else {
        Layout.BREAK_STRATEGY_HIGH_QUALITY
    }
}

internal fun resolveNovelPageReaderHyphenationFrequency(
    hyphenationEnabled: Boolean,
): Int {
    // Keep platform auto-hyphenation disabled: on vendor ROMs (e.g. OriginOS Android 16)
    // HYPHENATION_FREQUENCY_FULL/NORMAL splits words mid-syllable without inserting a hyphen
    // (such as "resti / ng"), while U+00AD renders inline as visible dashes. App-managed ASCII
    // hyphens inserted strictly at measured line breaks avoid both platform bugs.
    return Layout.HYPHENATION_FREQUENCY_NONE
}

/**
 * Picks a script-matching [Locale] so Android's hyphenator still hyphenates Latin (English) or
 * Cyrillic (Russian) chapters even when the device UI locale uses a different script.
 */
internal fun resolveNovelReaderTextLocale(
    sampleText: CharSequence,
    fallback: Locale = Locale.getDefault(),
): Locale {
    if (sampleText.isEmpty()) return fallback
    var latinLetters = 0
    var cyrillicLetters = 0
    var checkedLetters = 0
    val limit = minOf(sampleText.length, 512)
    for (i in 0 until limit) {
        val ch = sampleText[i]
        if (!ch.isLetter()) continue
        checkedLetters++
        when {
            ch in '\u0400'..'\u04FF' -> cyrillicLetters++
            (ch in 'A'..'Z') || (ch in 'a'..'z') || (ch in '\u00C0'..'\u024F') -> latinLetters++
        }
        if (checkedLetters >= 96) break
    }
    if (checkedLetters == 0) return fallback
    val fallbackLang = fallback.language.lowercase(Locale.ROOT)
    return when {
        cyrillicLetters * 2 >= checkedLetters -> {
            if (fallbackLang == "ru" || fallbackLang == "uk" || fallbackLang == "be") {
                fallback
            } else {
                Locale.forLanguageTag("ru")
            }
        }
        latinLetters * 2 >= checkedLetters -> {
            if (fallbackLang in setOf("ru", "uk", "be", "ja", "zh", "ko", "ar")) {
                Locale.US
            } else {
                fallback
            }
        }
        else -> fallback
    }
}

internal fun resolveReaderMeasurementTextPaint(
    textSizePx: Float,
    typeface: android.graphics.Typeface?,
    density: Float = 1.0f,
    basePaint: TextPaint? = null,
    textLocale: Locale? = null,
): TextPaint {
    return TextPaint().apply {
        if (basePaint != null) {
            set(basePaint)
        }
        // Android TextView initializes its TextPaint with Paint.SUBPIXEL_TEXT_FLAG; omitting it
        // in standalone measurement causes marginal lines to wrap earlier than on render.
        flags = flags or Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        isAntiAlias = true
        isSubpixelText = true
        this.textSize = textSizePx.coerceAtLeast(1f)
        this.typeface = typeface
        this.density = density
        if (textLocale != null) {
            this.textLocale = textLocale
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildNoneLineBreakConfig(): LineBreakConfig {
    return LineBreakConfig.Builder()
        .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
        .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
        .build()
}

private fun buildRawReaderStaticLayout(
    layoutText: CharSequence,
    widthPx: Int,
    lineHeightMultiplier: Float,
    textAlign: ReaderTextAlign,
    paint: TextPaint,
    hyphenationEnabled: Boolean,
    isPageMode: Boolean = true,
): StaticLayout {
    val builder = StaticLayout.Builder.obtain(
        layoutText,
        0,
        layoutText.length,
        paint,
        widthPx.coerceAtLeast(1),
    )
        .setAlignment(textAlign.toLayoutAlignment())
        .setIncludePad(true)
        .setLineSpacing(0f, lineHeightMultiplier.coerceAtLeast(1f))
        .setBreakStrategy(
            resolveNovelPageReaderBreakStrategy(
                isPageMode = isPageMode,
                hyphenationEnabled = hyphenationEnabled,
                textAlign = textAlign,
            ),
        )
        .setHyphenationFrequency(resolveNovelPageReaderHyphenationFrequency(hyphenationEnabled))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        builder.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE)
    }
    // Page mode renders every slice through a TextView configured by
    // applyNovelPageReaderTextViewMetrics; the two must set the same break strategy,
    // hyphenation and fallback spacing policy (both fallback APIs exist since API 28,
    // where the TextView side defaults to enabled and this builder to disabled) or the
    // pagination and the rendered lines diverge.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        builder.setUseLineSpacingFromFallbacks(!isPageMode)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        builder.setLineBreakConfig(buildNoneLineBreakConfig())
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        // TextView enables USE_BOUNDS_FOR_WIDTH by default when targetSdk >= 35; match it so
        // hyphenated line endings and justified right edges never re-wrap inside TextView.
        builder.setUseBoundsForWidth(true)
    }
    return builder.build()
}

private fun buildRawReaderDynamicLayout(
    layoutText: CharSequence,
    widthPx: Int,
    lineHeightMultiplier: Float,
    textAlign: ReaderTextAlign,
    paint: TextPaint,
    hyphenationEnabled: Boolean,
    isPageMode: Boolean = true,
): DynamicLayout? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return runCatching {
        val builder = DynamicLayout.Builder.obtain(layoutText, paint, widthPx.coerceAtLeast(1))
            .setAlignment(textAlign.toLayoutAlignment())
            .setLineSpacing(0f, lineHeightMultiplier.coerceAtLeast(1f))
            .setBreakStrategy(
                resolveNovelPageReaderBreakStrategy(
                    isPageMode = isPageMode,
                    hyphenationEnabled = hyphenationEnabled,
                    textAlign = textAlign,
                ),
            )
            .setHyphenationFrequency(resolveNovelPageReaderHyphenationFrequency(hyphenationEnabled))
            // Mirrors NovelPageReaderTextBlock's includeFontPadding = true so descenders (y, g, p)
            // have full bottom clearance without being clipped by TextView.onDraw at small sizes.
            .setIncludePad(true)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setJustificationMode(Layout.JUSTIFICATION_MODE_NONE)
            // Mirrors applyNovelPageReaderTextViewMetrics page vs scroll fallback policy.
            .setUseLineSpacingFromFallbacks(!isPageMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            builder.setLineBreakConfig(buildNoneLineBreakConfig())
            builder.setUseBoundsForWidth(true)
        }
        builder.build()
    }.getOrNull()
}

private fun buildRawReaderBoringLayout(
    layoutText: CharSequence,
    widthPx: Int,
    lineHeightMultiplier: Float,
    textAlign: ReaderTextAlign,
    paint: TextPaint,
): BoringLayout? {
    val safeWidth = widthPx.coerceAtLeast(1)
    return runCatching {
        val boring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            BoringLayout.isBoring(
                layoutText,
                paint,
                TextDirectionHeuristics.FIRSTSTRONG_LTR,
                false,
                null,
            )
        } else {
            BoringLayout.isBoring(layoutText, paint)
        } ?: return@runCatching null
        if (boring.width > safeWidth) return@runCatching null
        BoringLayout.make(
            layoutText,
            paint,
            safeWidth,
            textAlign.toLayoutAlignment(),
            lineHeightMultiplier.coerceAtLeast(1f),
            0f,
            boring,
            true,
        )
    }.getOrNull()
}

private fun wrapReaderTextWithFirstLineIndent(
    text: CharSequence,
    firstLineIndentPx: Int?,
): CharSequence {
    return firstLineIndentPx
        ?.takeIf { it > 0 }
        ?.let { indentPx ->
            SpannableStringBuilder(text).apply {
                setSpan(
                    LeadingMarginSpan.Standard(indentPx, 0),
                    0,
                    length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        ?: text
}

/**
 * Resolves width-aware line-end hyphens and builds the matching Android [Layout] so pagination
 * and [NovelPageReaderTextView] share identical line breaks and offset mappings.
 */
internal fun resolveWidthHyphenatedReaderTextAndLayout(
    source: CharSequence,
    widthPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    firstLineIndentPx: Int? = null,
    density: Float = 1.0f,
    measurementPaint: TextPaint? = null,
    preferDynamicLayout: Boolean = false,
    hyphenationEnabled: Boolean = false,
    textLocale: Locale? = null,
    isPageMode: Boolean = true,
    applyJustifySpacing: Boolean = false,
    layoutBuilderOverride: ((CharSequence) -> Layout?)? = null,
): WidthHyphenatedTextResult {
    val safeWidth = widthPx.coerceAtLeast(1)
    val baseSource = wrapReaderTextWithFirstLineIndent(source, firstLineIndentPx)
    val resolvedLocale = textLocale ?: if (hyphenationEnabled) resolveNovelReaderTextLocale(baseSource) else null
    val paint = resolveReaderMeasurementTextPaint(
        textSizePx = textSizePx,
        typeface = typeface,
        density = density,
        basePaint = measurementPaint,
        textLocale = resolvedLocale,
    )

    fun buildLayoutFor(candidateText: CharSequence, allowBoring: Boolean): Layout {
        layoutBuilderOverride?.invoke(candidateText)?.let { return it }
        if (preferDynamicLayout) {
            buildRawReaderDynamicLayout(
                layoutText = candidateText,
                widthPx = safeWidth,
                lineHeightMultiplier = lineHeightMultiplier,
                textAlign = textAlign,
                paint = paint,
                hyphenationEnabled = hyphenationEnabled,
                isPageMode = isPageMode,
            )?.let { return it }
        } else if (allowBoring) {
            buildRawReaderBoringLayout(
                layoutText = candidateText,
                widthPx = safeWidth,
                lineHeightMultiplier = lineHeightMultiplier,
                textAlign = textAlign,
                paint = paint,
            )?.let { return it }
        }
        return buildRawReaderStaticLayout(
            layoutText = candidateText,
            widthPx = safeWidth,
            lineHeightMultiplier = lineHeightMultiplier,
            textAlign = textAlign,
            paint = paint,
            hyphenationEnabled = hyphenationEnabled,
            isPageMode = isPageMode,
        )
    }

    val justifySpacingEnabled = applyJustifySpacing && textAlign == ReaderTextAlign.JUSTIFY
    return prepareWidthHyphenatedReaderText(
        source = baseSource,
        widthPx = safeWidth,
        paint = paint,
        firstLineIndentPx = firstLineIndentPx,
        hyphenationEnabled = hyphenationEnabled,
        buildWholeLayout = { candidate -> buildLayoutFor(candidate, allowBoring = true) },
        buildSliceLayout = { slice -> buildLayoutFor(slice, allowBoring = false) },
        justifySpacingEnabled = justifySpacingEnabled,
        measureLineNaturalWidth = if (justifySpacingEnabled) {
            { layout, _, lineIdx, _, _ -> layout.getLineMax(lineIdx) }
        } else {
            null
        },
    )
}

internal fun buildReaderStaticLayout(
    text: CharSequence,
    widthPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    firstLineIndentPx: Int? = null,
    density: Float = 1.0f,
    measurementPaint: TextPaint? = null,
    hyphenationEnabled: Boolean = false,
    textLocale: Locale? = null,
): StaticLayout {
    val safeWidth = widthPx.coerceAtLeast(1)
    val baseSource = wrapReaderTextWithFirstLineIndent(text, firstLineIndentPx)
    val resolvedLocale = textLocale ?: if (hyphenationEnabled) resolveNovelReaderTextLocale(baseSource) else null
    val paint = resolveReaderMeasurementTextPaint(
        textSizePx = textSizePx,
        typeface = typeface,
        density = density,
        basePaint = measurementPaint,
        textLocale = resolvedLocale,
    )
    return prepareWidthHyphenatedReaderText(
        source = baseSource,
        widthPx = safeWidth,
        paint = paint,
        firstLineIndentPx = firstLineIndentPx,
        hyphenationEnabled = hyphenationEnabled,
        buildWholeLayout = { candidate ->
            buildRawReaderStaticLayout(
                layoutText = candidate,
                widthPx = safeWidth,
                lineHeightMultiplier = lineHeightMultiplier,
                textAlign = textAlign,
                paint = paint,
                hyphenationEnabled = hyphenationEnabled,
                isPageMode = true,
            )
        },
        buildSliceLayout = { slice ->
            buildRawReaderStaticLayout(
                layoutText = slice,
                widthPx = safeWidth,
                lineHeightMultiplier = lineHeightMultiplier,
                textAlign = textAlign,
                paint = paint,
                hyphenationEnabled = hyphenationEnabled,
                isPageMode = true,
            )
        },
    ).layout as StaticLayout
}

/**
 * Builds the layout with the same engine and parameters the page-mode TextView builds its own
 * with. TextView lays spannable text out via DynamicLayout, and on some vendor builds (measured
 * on vivo V2338A / Android 16) its line breaking diverges from an identically configured
 * StaticLayout by one line on marginal blocks — a divergence the page budget cannot absorb once
 * justify-fill spends the leftover. Pagination must measure with the render engine, not a
 * look-alike. Returns null where DynamicLayout is unavailable (API < P) or fails to build
 * (plain-JVM unit tests); callers fall back to [buildReaderStaticLayout].
 */
internal fun buildReaderDynamicLayout(
    text: CharSequence,
    widthPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    firstLineIndentPx: Int? = null,
    density: Float = 1.0f,
    measurementPaint: TextPaint? = null,
    hyphenationEnabled: Boolean = false,
    textLocale: Locale? = null,
): Layout? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val safeWidth = widthPx.coerceAtLeast(1)
    val baseSource = wrapReaderTextWithFirstLineIndent(text, firstLineIndentPx)
    val resolvedLocale = textLocale ?: if (hyphenationEnabled) resolveNovelReaderTextLocale(baseSource) else null
    val paint = resolveReaderMeasurementTextPaint(
        textSizePx = textSizePx,
        typeface = typeface,
        density = density,
        basePaint = measurementPaint,
        textLocale = resolvedLocale,
    )
    return runCatching {
        prepareWidthHyphenatedReaderText(
            source = baseSource,
            widthPx = safeWidth,
            paint = paint,
            firstLineIndentPx = firstLineIndentPx,
            hyphenationEnabled = hyphenationEnabled,
            buildWholeLayout = { candidate ->
                buildRawReaderDynamicLayout(
                    layoutText = candidate,
                    widthPx = safeWidth,
                    lineHeightMultiplier = lineHeightMultiplier,
                    textAlign = textAlign,
                    paint = paint,
                    hyphenationEnabled = hyphenationEnabled,
                    isPageMode = true,
                ) ?: throw IllegalStateException("DynamicLayout unavailable")
            },
            buildSliceLayout = { slice ->
                buildRawReaderDynamicLayout(
                    layoutText = slice,
                    widthPx = safeWidth,
                    lineHeightMultiplier = lineHeightMultiplier,
                    textAlign = textAlign,
                    paint = paint,
                    hyphenationEnabled = hyphenationEnabled,
                    isPageMode = true,
                ) ?: throw IllegalStateException("DynamicLayout unavailable")
            },
        ).layout
    }.getOrNull()
}

/**
 * Mirrors TextView's fast single-line BoringLayout path when text selection (DynamicLayout) is off
 * and the whole text fits on a single line without ParagraphStyle spans.
 */
internal fun buildReaderBoringLayout(
    text: CharSequence,
    widthPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    firstLineIndentPx: Int? = null,
    density: Float = 1.0f,
    measurementPaint: TextPaint? = null,
    hyphenationEnabled: Boolean = false,
    textLocale: Locale? = null,
): BoringLayout? {
    val safeWidth = widthPx.coerceAtLeast(1)
    val rawText = if (hyphenationEnabled) normalizeCurlyApostrophesForDisplay(text) else text
    val layoutText = wrapReaderTextWithFirstLineIndent(rawText, firstLineIndentPx)
    val paint = resolveReaderMeasurementTextPaint(
        textSizePx = textSizePx,
        typeface = typeface,
        density = density,
        basePaint = measurementPaint,
        textLocale = textLocale,
    )
    return buildRawReaderBoringLayout(
        layoutText = layoutText,
        widthPx = safeWidth,
        lineHeightMultiplier = lineHeightMultiplier,
        textAlign = textAlign,
        paint = paint,
    )
}

/**
 * Selects the layout engine matching [NovelPageReaderTextView]: DynamicLayout when text is
 * selectable ([preferDynamicLayout]), or BoringLayout (single-line) / StaticLayout (multi-line)
 * when non-selectable.
 */
internal fun buildReaderTextLayout(
    text: CharSequence,
    widthPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    firstLineIndentPx: Int? = null,
    density: Float = 1.0f,
    measurementPaint: TextPaint? = null,
    preferDynamicLayout: Boolean = false,
    hyphenationEnabled: Boolean = false,
    textLocale: Locale? = null,
): Layout {
    return resolveWidthHyphenatedReaderTextAndLayout(
        source = text,
        widthPx = widthPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
        firstLineIndentPx = firstLineIndentPx,
        density = density,
        measurementPaint = measurementPaint,
        preferDynamicLayout = preferDynamicLayout,
        hyphenationEnabled = hyphenationEnabled,
        textLocale = textLocale,
        isPageMode = true,
    ).layout
}

internal fun resolveStaticLayoutHeight(
    layout: Layout,
): Int {
    if (layout.lineCount <= 0) return 0
    return layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(0)
}

/**
 * Measures a page slice as its own layout built the way the page-mode TextView builds its own
 * (DynamicLayout when selectable, otherwise BoringLayout / StaticLayout), instead of reading line
 * boxes off the whole-block layout the candidate was cut from. Styled slices must be passed
 * pre-styled in [sliceText]: the spannable the render path builds (leading margin, bionic bold,
 * forced typeface) is the source of truth for metrics.
 */
internal fun measurePlainPageSliceStandaloneHeight(
    sliceText: CharSequence,
    widthPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    density: Float = 1.0f,
    measurementPaint: TextPaint? = null,
    preferDynamicLayout: Boolean = false,
    hyphenationEnabled: Boolean = false,
    textLocale: Locale? = null,
): Int {
    if (sliceText.isEmpty()) return 0
    val layout = resolveWidthHyphenatedReaderTextAndLayout(
        source = sliceText,
        widthPx = widthPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
        density = density,
        measurementPaint = measurementPaint,
        preferDynamicLayout = preferDynamicLayout,
        hyphenationEnabled = hyphenationEnabled,
        textLocale = textLocale,
        isPageMode = true,
    ).layout
    return resolveStaticLayoutHeight(layout)
}

/**
 * Returns null when [allowOversizedFirstLine] is false and the next line does not fully fit.
 * Block pagination uses this on non-empty pages so a leftover bottom sliver does not pull
 * one more line into a separate TextView and visually collapse the final inter-line gap.
 */
internal fun resolveStaticLayoutSliceForHeight(
    text: CharSequence,
    layout: Layout,
    startLine: Int,
    availableHeight: Int,
    allowOversizedFirstLine: Boolean = true,
    displayToSourceOffsets: IntArray? = null,
): StaticLayoutSlice? {
    if (startLine >= layout.lineCount || availableHeight <= 0) return null

    val startOffset = mapDisplayOffsetToSourceOffset(
        displayOffset = layout.getLineStart(startLine),
        displayToSourceOffsets = displayToSourceOffsets,
        fallbackLength = text.length,
    )
    var endLineExclusive = startLine
    while (
        endLineExclusive < layout.lineCount &&
        layout.getLineBottom(endLineExclusive) - layout.getLineTop(startLine) <= availableHeight
    ) {
        endLineExclusive++
    }
    if (endLineExclusive == startLine && !allowOversizedFirstLine) {
        return null
    }
    val endLine = if (endLineExclusive > startLine) endLineExclusive - 1 else startLine
    val endOffset = mapDisplayOffsetToSourceOffset(
        displayOffset = layout.getLineEnd(endLine),
        displayToSourceOffsets = displayToSourceOffsets,
        fallbackLength = text.length,
    ).coerceIn(startOffset, text.length)
    // Empty/whitespace-only lines still consume layout lines. Returning null confuses them
    // with "needs a new page": callers either stop early or retry the same line forever.
    val range = trimTextRange(text, startOffset, endOffset)
        ?: TextPageRange(startOffset, endOffset)
    return StaticLayoutSlice(
        range = range,
        nextStartLine = endLine + 1,
        heightPx = layout.getLineBottom(endLine) - layout.getLineTop(startLine),
    )
}

private data class StandalonePageSliceFit(
    val range: TextPageRange,
    val nextStartLine: Int,
    val heightPx: Int,
    val rewoundMidWord: Boolean = false,
    val nextResumeSourceOffset: Int = 0,
)

private inline fun resolveStandalonePageSliceFit(
    plainText: String,
    layout: Layout,
    startLine: Int,
    availableHeight: Int,
    allowOversizedFirstLine: Boolean,
    displayToSourceOffsets: IntArray? = null,
    avoidMidWordPageBreak: Boolean = false,
    measureStandaloneHeightPx: (TextPageRange) -> Int,
): StandalonePageSliceFit? {
    val candidate = resolveStaticLayoutSliceForHeight(
        text = plainText,
        layout = layout,
        startLine = startLine,
        availableHeight = availableHeight,
        allowOversizedFirstLine = allowOversizedFirstLine,
        displayToSourceOffsets = displayToSourceOffsets,
    ) ?: return null

    // The candidate height is line geometry of the whole-block layout, which includes trailing
    // lineSpacingMultiplier on every non-final line, whereas a rendered slice omits trailing
    // line spacing on its own final line. Probing one line past candidate.nextStartLine and walking
    // backward ensures both exact standalone fit and avoids leaving one fitting line stranded.
    val startOffset = mapDisplayOffsetToSourceOffset(
        displayOffset = layout.getLineStart(startLine),
        displayToSourceOffsets = displayToSourceOffsets,
        fallbackLength = plainText.length,
    )
    val startLineTop = layout.getLineTop(startLine)
    var lastRange: TextPageRange? = null
    var lastHeightPx = 0
    var lastRewound = false
    var lastResumeOffset = 0
    var endLine = candidate.nextStartLine.coerceAtMost(layout.lineCount - 1)
    while (endLine >= startLine) {
        val rawEnd = mapDisplayOffsetToSourceOffset(
            displayOffset = layout.getLineEnd(endLine),
            displayToSourceOffsets = displayToSourceOffsets,
            fallbackLength = plainText.length,
        ).coerceIn(startOffset, plainText.length)
        var effectiveEnd = rawEnd
        var rewoundMidWord = false
        if (avoidMidWordPageBreak && rawEnd < plainText.length) {
            val midWordStart = resolveWordStartBeforeOffset(plainText, rawEnd)
            if (midWordStart != null) {
                val endLineStart = mapDisplayOffsetToSourceOffset(
                    displayOffset = layout.getLineStart(endLine),
                    displayToSourceOffsets = displayToSourceOffsets,
                    fallbackLength = plainText.length,
                ).coerceIn(startOffset, plainText.length)
                if (midWordStart > startOffset && midWordStart > endLineStart) {
                    effectiveEnd = midWordStart
                    rewoundMidWord = true
                }
            }
        }
        // O(1) line-metric lower bound: skip rebuilding a standalone DynamicLayout/StaticLayout
        // when even subtracting an entire endLine's height still exceeds availableHeight.
        if (!rewoundMidWord && endLine > startLine && endLine < layout.lineCount - 1) {
            val wholeSpanHeight = layout.getLineBottom(endLine) - startLineTop
            val wholeEndLineHeight = (layout.getLineBottom(endLine) - layout.getLineTop(endLine))
                .coerceAtLeast(0)
            if (wholeSpanHeight - wholeEndLineHeight > availableHeight) {
                endLine--
                continue
            }
        }
        val range = trimTextRange(plainText, startOffset, effectiveEnd)
            ?: TextPageRange(startOffset, effectiveEnd)
        val measured = measureStandaloneHeightPx(range)
        lastRange = range
        lastHeightPx = measured
        lastRewound = rewoundMidWord
        lastResumeOffset = effectiveEnd
        if (measured <= availableHeight) {
            return StandalonePageSliceFit(
                range = range,
                nextStartLine = endLine + 1,
                heightPx = measured,
                rewoundMidWord = rewoundMidWord,
                nextResumeSourceOffset = effectiveEnd,
            )
        }
        endLine--
    }

    if (!allowOversizedFirstLine) return null
    // Nothing fits an empty page: accept the smallest (single-line) candidate so the algorithm
    // keeps moving — the rendered TextView clips the oversized line instead of losing the text.
    return StandalonePageSliceFit(
        range = lastRange ?: candidate.range,
        nextStartLine = startLine + 1,
        heightPx = lastHeightPx,
        rewoundMidWord = lastRewound,
        nextResumeSourceOffset = lastResumeOffset,
    )
}

internal fun measureApproximateTextHeight(
    text: String,
    metrics: ApproximateTextMetrics,
): Int {
    if (text.isBlank()) return metrics.lineHeightPx
    val lineCount = text.split('\n').sumOf { line ->
        ceil(line.length.toDouble() / metrics.charsPerLine.toDouble()).toInt().coerceAtLeast(1)
    }.coerceAtLeast(1)
    return lineCount * metrics.lineHeightPx
}

private fun resolveApproximateTextRangeForHeight(
    text: String,
    start: Int,
    availableHeight: Int,
    metrics: ApproximateTextMetrics,
    allowOversizedFirstLine: Boolean = true,
): TextPageRange? {
    if (start >= text.length || availableHeight <= 0) return null
    val lineHeightPx = metrics.lineHeightPx.coerceAtLeast(1)
    if (!allowOversizedFirstLine && availableHeight < lineHeightPx) return null
    val linesPerPage = (availableHeight / lineHeightPx).coerceAtLeast(1)
    val charsPerLine = metrics.charsPerLine.coerceAtLeast(1)
    var end = start
    var usedLines = 1
    var charsOnLine = 0
    while (end < text.length) {
        val char = text[end]
        if (char == '\n') {
            if (usedLines >= linesPerPage) break
            usedLines++
            charsOnLine = 0
            end++
            continue
        }
        if (charsOnLine >= charsPerLine) {
            if (usedLines >= linesPerPage) break
            usedLines++
            charsOnLine = 0
        }
        charsOnLine++
        end++
    }
    if (end == start) {
        if (!allowOversizedFirstLine) return null
        end = (start + charsPerLine).coerceAtMost(text.length)
    }
    return trimTextRange(
        text = text,
        startInclusive = start,
        endExclusive = end,
    )
}

private fun resolvePageReaderFirstLineIndentPx(
    firstLineIndentEm: Float?,
    textSizePx: Float,
): Int? {
    return firstLineIndentEm
        ?.takeIf { it > 0f }
        ?.let { (textSizePx.coerceAtLeast(1f) * it).roundToInt().coerceAtLeast(1) }
}

private fun resolvePageReaderBlockLayoutMetrics(
    isChapterTitle: Boolean,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    fontSizeMultiplier: Float = 1f,
    blockLineHeightMultiplier: Float = 1f,
): PageReaderBlockLayoutMetrics {
    return if (isChapterTitle) {
        PageReaderBlockLayoutMetrics(
            textSizePx = textSizePx * PAGE_READER_CHAPTER_TITLE_FONT_SIZE_MULTIPLIER,
            lineHeightMultiplier = lineHeightMultiplier *
                PAGE_READER_CHAPTER_TITLE_LINE_HEIGHT_MULTIPLIER,
        )
    } else {
        PageReaderBlockLayoutMetrics(
            textSizePx = textSizePx * fontSizeMultiplier,
            lineHeightMultiplier = lineHeightMultiplier * blockLineHeightMultiplier,
        )
    }
}

internal fun resolvePageReaderInterBlockSpacingPx(
    paragraphSpacingPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
): Int {
    // TextView slices already carry glyphPadPx at the bottom; subtract it from leadingGapPx so
    // paragraphSpacing = 0dp keeps the same baseline-to-baseline cadence as lines inside a
    // paragraph, and small slider steps stay strictly monotonic instead of hitting a flat floor.
    val leadingGapPx = (
        textSizePx.coerceAtLeast(1f) *
            (lineHeightMultiplier.coerceAtLeast(1f) - 1f)
        )
        .roundToInt()
        .coerceAtLeast(0)
    val glyphPadPx = resolvePageReaderGlyphOverflowPaddingPx(textSizePx)
    val baselineCompensationPx = (leadingGapPx - glyphPadPx).coerceAtLeast(0)
    return paragraphSpacingPx.coerceAtLeast(0) + baselineCompensationPx
}

internal fun ReaderTextAlign.toLayoutAlignment(): Layout.Alignment {
    return when (this) {
        ReaderTextAlign.SOURCE -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        ReaderTextAlign.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }
}

internal fun toBionicText(text: String): AnnotatedString {
    if (text.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        Regex("\\S+|\\s+").findAll(text).forEach { match ->
            val token = match.value
            if (token.firstOrNull()?.isWhitespace() == true) {
                append(token)
            } else {
                val emphasizeCount = ceil(token.length * 0.5f).toInt().coerceAtLeast(1)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(token.take(emphasizeCount))
                }
                append(token.drop(emphasizeCount))
            }
        }
    }
}

internal sealed interface PlainPageSlice {
    /** Index of the source block this slice belongs to, in the chapter's block stream. */
    val blockIndex: Int
    val spacingBeforePx: Int

    data class Text(
        override val blockIndex: Int,
        val range: TextPageRange,
        override val spacingBeforePx: Int = 0,
        /**
         * Standalone StaticLayout height (px) predicted for the exact CharSequence this slice
         * renders — the number the page budget was spent against. Debug logs compare it with the
         * DynamicLayout height the TextView actually produces; 0 means "not measured" (legacy
         * callers / oversized-fallback with no standalone pass).
         */
        val measuredHeightPx: Int = 0,
    ) : PlainPageSlice

    /**
     * An illustration of the chapter. The plain page reader used to be text-only, so books whose
     * illustrations only exist as image blocks (every .epub) lost them in page mode; an image slice
     * rides its own page, or joins the previous one when that page ends in a short paragraph,
     * exactly like the rich page reader does.
     */
    data class Image(
        override val blockIndex: Int,
        val imageUrl: String,
        val contentDescription: String?,
        override val spacingBeforePx: Int = 0,
        val maxHeightPx: Int? = null,
    ) : PlainPageSlice
}

internal data class PlainPageReaderTextBlock(
    val sourceBlockIndex: Int,
    val text: String,
)

internal sealed interface RichPageSlice {
    data class Text(
        val blockIndex: Int,
        val range: TextPageRange,
        val spacingBeforePx: Int = 0,
        /** Standalone StaticLayout height (px) predicted for this slice; see PlainPageSlice.Text. */
        val measuredHeightPx: Int = 0,
    ) : RichPageSlice

    data class Image(
        val sourceBlockIndex: Int,
        val imageUrl: String,
        val contentDescription: String?,
        val spacingBeforePx: Int = 0,
        val maxHeightPx: Int? = null,
    ) : RichPageSlice
}

internal data class PlainPageRenderBlock(
    val sourceBlockIndex: Int,
    val text: String,
    val sourceTextStart: Int,
    val sourceTextEndExclusive: Int,
    val spacingBeforePx: Int,
    val firstLineIndentEm: Float?,
    val isChapterTitle: Boolean = false,
    val measuredHeightPx: Int = 0,
)

internal data class RichPageBlockText(
    val sourceBlockIndex: Int,
    val text: AnnotatedString,
    val firstLineIndentEm: Float?,
    val sourceTextAlign: NovelRichBlockTextAlign? = null,
    val fontSizeMultiplier: Float = 1f,
    val lineHeightMultiplier: Float = 1f,
    val baseTypefaceStyle: Int = android.graphics.Typeface.NORMAL,
    val startIndentDp: Int = 0,
    val isBlockQuote: Boolean = false,
    val isHorizontalRule: Boolean = false,
)

internal data class RichPageRenderBlock(
    val sourceBlockIndex: Int,
    val text: AnnotatedString,
    val sourceTextStart: Int,
    val sourceTextEndExclusive: Int,
    val spacingBeforePx: Int,
    val firstLineIndentEm: Float?,
    val sourceTextAlign: NovelRichBlockTextAlign? = null,
    val isChapterTitle: Boolean = false,
    val measuredHeightPx: Int = 0,
    val fontSizeMultiplier: Float = 1f,
    val lineHeightMultiplier: Float = 1f,
    val baseTypefaceStyle: Int = android.graphics.Typeface.NORMAL,
    val startIndentDp: Int = 0,
    val isBlockQuote: Boolean = false,
    val isHorizontalRule: Boolean = false,
)

private fun resolveRichPageBlockIndex(
    sourceBlockIndex: Int,
    fallbackIndex: Int,
): Int {
    return if (sourceBlockIndex >= 0) sourceBlockIndex else fallbackIndex
}

internal sealed interface NovelPageContentBlock {
    val spacingBeforePx: Int
    val firstLineIndentEm: Float?
    val isChapterTitle: Boolean

    data class Plain(
        val sourceBlockIndex: Int,
        val text: String,
        val sourceTextStart: Int? = null,
        val sourceTextEndExclusive: Int? = null,
        override val spacingBeforePx: Int,
        override val firstLineIndentEm: Float?,
        override val isChapterTitle: Boolean = false,
        val measuredHeightPx: Int = 0,
    ) : NovelPageContentBlock

    data class Rich(
        val sourceBlockIndex: Int,
        val text: AnnotatedString,
        val sourceTextStart: Int? = null,
        val sourceTextEndExclusive: Int? = null,
        override val spacingBeforePx: Int,
        override val firstLineIndentEm: Float?,
        val sourceTextAlign: NovelRichBlockTextAlign? = null,
        override val isChapterTitle: Boolean = false,
        val measuredHeightPx: Int = 0,
        val fontSizeMultiplier: Float = 1f,
        val lineHeightMultiplier: Float = 1f,
        val baseTypefaceStyle: Int = android.graphics.Typeface.NORMAL,
        val startIndentDp: Int = 0,
        val isBlockQuote: Boolean = false,
        val isHorizontalRule: Boolean = false,
    ) : NovelPageContentBlock

    data class Image(
        val imageUrl: String,
        val contentDescription: String?,
        override val spacingBeforePx: Int = 0,
        val maxHeightPx: Int? = null,
    ) : NovelPageContentBlock {
        override val firstLineIndentEm: Float? = null
        override val isChapterTitle: Boolean = false
    }
}

internal data class NovelPageContentPage(
    val blocks: List<NovelPageContentBlock>,
    val pageIndex: Int = -1,
)

internal data class SeamlessAdjacentPagesSlice(
    val pages: List<NovelPageContentPage> = emptyList(),
    val firstPageNumber: Int? = null,
    val totalPages: Int = 0,
)

internal fun createSeamlessFallbackBoundaryPage(chapterName: String?): NovelPageContentPage {
    val cleanTitle = chapterName?.trim().orEmpty()
    if (cleanTitle.isEmpty()) return NovelPageContentPage(emptyList())
    return NovelPageContentPage(
        blocks = listOf(
            NovelPageContentBlock.Plain(
                sourceBlockIndex = -1,
                text = cleanTitle,
                spacingBeforePx = 0,
                firstLineIndentEm = null,
                isChapterTitle = true,
            ),
        ),
        pageIndex = 0,
    )
}

internal fun resolveSeamlessBoundarySpreadPages(
    boundaryTarget: HorizontalChapterSwipeAction,
    spreadColumns: Int,
    previousChapterName: String?,
    nextChapterName: String?,
    previousChapterPreview: SeamlessAdjacentPagesSlice,
    nextChapterPreview: SeamlessAdjacentPagesSlice,
): Pair<List<NovelPageContentPage>, SeamlessAdjacentPagesSlice> {
    val safeColumns = spreadColumns.coerceAtLeast(1)
    val slice = when (boundaryTarget) {
        HorizontalChapterSwipeAction.PREVIOUS -> previousChapterPreview
        HorizontalChapterSwipeAction.NEXT -> nextChapterPreview
        HorizontalChapterSwipeAction.NONE -> SeamlessAdjacentPagesSlice()
    }
    if (slice.pages.isNotEmpty()) {
        val pages = (0 until safeColumns).map { index ->
            slice.pages.getOrElse(index) { NovelPageContentPage(emptyList()) }
        }
        return pages to slice
    }
    val fallbackTitle = when (boundaryTarget) {
        HorizontalChapterSwipeAction.PREVIOUS -> previousChapterName
        HorizontalChapterSwipeAction.NEXT -> nextChapterName
        HorizontalChapterSwipeAction.NONE -> null
    }
    val fallbackPages = (0 until safeColumns).map { index ->
        if (index == 0) {
            createSeamlessFallbackBoundaryPage(fallbackTitle)
        } else {
            NovelPageContentPage(emptyList())
        }
    }
    return fallbackPages to SeamlessAdjacentPagesSlice()
}

internal fun resolvePageReaderGlyphOverflowPaddingPx(textSizePx: Float): Int {
    return (textSizePx.coerceAtLeast(1f) * 0.25f)
        .roundToInt()
        .coerceAtLeast(6)
}

/**
 * Vertical padding subtracted from the viewport height when paginating a single-column page.
 *
 * The rendered page already reserves contentPadding + bookBottomInset below the text column
 * (NovelPageReaderPageContent), which normally already covers the navigation bar zone. Only the
 * part of the navigation bar NOT covered by that rendered reservation may claim extra page
 * height; reserving the full bar on top of the rendered inset double-books the same zone and
 * leaves every page visibly short at the bottom.
 */
internal fun resolveNovelPageReaderVerticalPaddingPx(
    topPaddingPx: Int,
    bottomPaddingPx: Int,
    bookBottomInsetPx: Int,
    pageFitSafetyPx: Int,
    navigationBarHeightPx: Int,
): Int {
    // The rendered page reserves contentPadding + bookBottomInset below the text, which usually
    // already covers the navigation bar zone. Only the uncovered part of the bar may claim extra
    // page height; adding the full bar on top of the rendered inset double-books the same zone.
    val renderedBottomReservationPx = (bottomPaddingPx + bookBottomInsetPx).coerceAtLeast(0)
    val uncoveredNavigationBarPx = (navigationBarHeightPx - renderedBottomReservationPx).coerceIn(
        0,
        navigationBarHeightPx.coerceAtLeast(0),
    )
    return topPaddingPx +
        bottomPaddingPx +
        bookBottomInsetPx +
        pageFitSafetyPx +
        uncoveredNavigationBarPx
}

internal data class NovelPageReaderPageBudget(
    val widthPx: Int,
    val heightPx: Int,
    val topPaddingPx: Int,
    val bottomPaddingPx: Int,
    val bookBottomInsetPx: Int,
)

internal fun resolveNovelPageReaderPageBudget(
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Density,
    fontSize: Int,
    lineHeight: Float,
    marginDp: Int,
    contentPaddingDp: Dp,
    spreadContentPaddingPx: Int,
    statusBarTopPaddingDp: Dp,
    navigationBarHeightPx: Int,
    spreadColumns: Int,
    cutoutLeftPx: Int,
    cutoutRightPx: Int,
): NovelPageReaderPageBudget {
    val horizontalPaddingPx = with(density) { marginDp.dp.roundToPx() * 2 }
    val topPaddingPx = with(density) { (contentPaddingDp + statusBarTopPaddingDp).roundToPx() }
    val bottomPaddingPx = with(density) { contentPaddingDp.roundToPx() }
    val bookBottomInsetPx = with(density) {
        resolveNovelPageReaderBookBottomInset(
            density = this,
            fontSize = fontSize,
            lineHeight = lineHeight,
        ).roundToPx()
    }
    // Both plain and rich paginators already reserve one full body line (`measureParityReservePx`)
    // internally for TextView vs StaticLayout parity, and NovelPageReaderPageContent already adds
    // `bookBottomInset` below the text column; subtracting `pageFitSafetyPx` again double-booked the
    // bottom safety zone and pushed text upward unnecessarily.
    val verticalPaddingPx = if (spreadColumns > 1) {
        resolveNovelSpreadPageVerticalPadding(
            contentPaddingPx = spreadContentPaddingPx,
            pageFitSafetyPx = 0,
            bookBottomInsetPx = bookBottomInsetPx,
        )
    } else {
        resolveNovelPageReaderVerticalPaddingPx(
            topPaddingPx = topPaddingPx,
            bottomPaddingPx = bottomPaddingPx,
            bookBottomInsetPx = bookBottomInsetPx,
            pageFitSafetyPx = 0,
            navigationBarHeightPx = navigationBarHeightPx,
        )
    }
    val spreadColumnWidthPx = resolveNovelSpreadColumnTextWidth(
        screenWidthPx = screenWidthPx,
        horizontalPaddingPx = horizontalPaddingPx,
        columns = spreadColumns,
        cutoutLeftPx = cutoutLeftPx,
        cutoutRightPx = cutoutRightPx,
    )
    return NovelPageReaderPageBudget(
        widthPx = spreadColumnWidthPx,
        heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
        topPaddingPx = topPaddingPx,
        bottomPaddingPx = bottomPaddingPx,
        bookBottomInsetPx = bookBottomInsetPx,
    )
}

/**
 * Resolves the top padding used for page reader pagination and rendering.
 *
 * In fullscreen mode, reading takes place with system bars hidden; temporary chrome overlays
 * must not alter the content's vertical budget or trigger full-chapter repagination.
 */
internal fun resolvePageReaderStableTopPadding(
    isFullScreen: Boolean,
    cutoutTopDp: Dp,
    statusBarTopPaddingDp: Dp,
): Dp {
    return if (isFullScreen) {
        cutoutTopDp
    } else {
        statusBarTopPaddingDp
    }
}

/**
 * Resolves the navigation bar bottom height used for single-page vertical padding budget.
 *
 * In fullscreen mode, the bottom navigation bar is hidden during reading; temporary chrome
 * must not introduce extra bottom insets into the pagination calculation.
 */
internal fun resolvePageReaderStableNavigationBarHeightPx(
    isFullScreen: Boolean,
    cutoutBottomPx: Int,
    navigationBarHeightPx: Int,
): Int {
    return if (isFullScreen) {
        cutoutBottomPx.coerceAtLeast(0)
    } else {
        navigationBarHeightPx.coerceAtLeast(0)
    }
}

/**
 * Extra spacing (px) added to each paragraph gap so a full page's leftover height is spread
 * across inter-paragraph breaks instead of collecting at the bottom. [leftoverPx] is exactly
 * the unclaimed remainder of the page budget at flushPage time.
 *
 * Fill goes only to real paragraph gaps (spacingBefore > 0): stretching inside a paragraph or
 * between continuation slices would look like a rendering bug. Each gap gains at most half a
 * line, the visual step books use between paragraphs; when the leftover is larger, the excess
 * A small [safetyBufferPx] is kept unallocated so multi-view Compose rendering never clips the
 * final line due to integer rounding or subpixel glyph advances.
 */
internal fun resolveNovelPageReaderJustifyFillPx(
    sliceSpacingBeforePx: List<Int>,
    leftoverPx: Int,
    sliceLineHeightPx: Int,
    safetyBufferPx: Int = 6,
): List<Int> {
    if (sliceSpacingBeforePx.isEmpty() || leftoverPx <= 0) {
        return List(sliceSpacingBeforePx.size) { 0 }
    }
    val gapIndices = sliceSpacingBeforePx.indices.filter { sliceSpacingBeforePx[it] > 0 }
    if (gapIndices.isEmpty()) return List(sliceSpacingBeforePx.size) { 0 }
    val usableLeftoverPx = (leftoverPx - safetyBufferPx).coerceAtLeast(0)
    if (usableLeftoverPx <= 0) return List(sliceSpacingBeforePx.size) { 0 }
    val perGapCapPx = (sliceLineHeightPx.coerceAtLeast(1) / 2)
    val fillPerGapPx = (usableLeftoverPx / gapIndices.size).coerceAtMost(perGapCapPx)
    return List(sliceSpacingBeforePx.size) { index ->
        if (index in gapIndices) fillPerGapPx else 0
    }
}

internal const val PLAIN_PAGE_PAGINATION_LOG_TAG = "NovelPageReaderPagination"

internal fun resolvePageReaderSafeHeightPx(
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
): Int {
    // Render-side measurement can disagree with pagination by one line on marginal blocks:
    // measured live on vivo V2338A, the same slice measured 3 lines in one pagination run and
    // 2 in the next, and a rendered TextView wrapped a whole line taller than the identical
    // DynamicLayout measurement. Reserving one body line keeps such a flip from clipping the
    // page's last block.
    val measureParityReservePx = (
        textSizePx.coerceAtLeast(1f) * lineHeightMultiplier.coerceAtLeast(1f)
        )
        .toInt()
        .coerceAtLeast(1)
    return (heightPx - measureParityReservePx).coerceAtLeast(1)
}

private fun resolvePageJustifyFillPx(
    applyJustifyFill: Boolean,
    paragraphSpacingPx: Int,
    sliceSpacingBeforePx: List<Int>,
    leftoverPx: Int,
    sliceLineHeightPx: Int,
): List<Int> {
    if (!applyJustifyFill || paragraphSpacingPx <= 0) {
        return List(sliceSpacingBeforePx.size) { 0 }
    }
    return resolveNovelPageReaderJustifyFillPx(
        sliceSpacingBeforePx = sliceSpacingBeforePx,
        leftoverPx = leftoverPx,
        sliceLineHeightPx = sliceLineHeightPx,
    )
}

private fun resolveAttachableInlineImageMaxHeightPx(
    precedingTextLength: Int,
    usedHeightPx: Int,
    imageSpacingPx: Int,
    safeHeightPx: Int,
    pageHeightPx: Int,
    density: Float,
): Int? {
    if (precedingTextLength !in 1..MAX_INLINE_IMAGE_PRECEDING_TEXT_CHARS) return null
    val availableHeightPx = safeHeightPx - usedHeightPx - imageSpacingPx
    val safeDensity = density.coerceAtLeast(1f)
    val minInlineImageHeightPx = minOf(
        (180f * safeDensity).roundToInt(),
        (pageHeightPx * 0.4f).roundToInt(),
    ).coerceAtLeast((96f * safeDensity).roundToInt())
    return availableHeightPx.takeIf { it >= minInlineImageHeightPx }
}

private data class PaginatableTextBlock(
    val blockIndex: Int,
    val annotatedText: AnnotatedString,
    val blockWidthPx: Int,
    val isChapterTitle: Boolean,
    val metrics: PageReaderBlockLayoutMetrics,
    val firstLineIndentPx: Int?,
    val blockTypeface: android.graphics.Typeface?,
    val blockTextAlign: ReaderTextAlign,
    val blockForcedStyle: Int,
)

private data class PaginatedTextSlice(
    val blockIndex: Int,
    val range: TextPageRange,
    val spacingBeforePx: Int,
    val measuredHeightPx: Int,
)

private fun paginateCharSequencePageBlocks(
    blocks: List<PaginatableTextBlock>,
    paragraphSpacingPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    density: Float,
    measurement: ReaderTextMeasurement,
    measurementPaint: TextPaint?,
    preferDynamicLayout: Boolean,
    hyphenationEnabled: Boolean,
    logPrefix: String,
    maxPages: Int? = null,
): List<List<PaginatedTextSlice>> {
    if (maxPages != null && maxPages <= 0) return emptyList()
    val safeHeight = resolvePageReaderSafeHeightPx(
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
    )
    val pages = mutableListOf<List<PaginatedTextSlice>>()
    var currentPage = mutableListOf<PaginatedTextSlice>()
    var remainingHeight = safeHeight
    // Line height of body text: caps how far one paragraph gap may be stretched.
    val sliceLineHeightPx = (textSizePx.coerceAtLeast(1f) * lineHeightMultiplier.coerceAtLeast(1f)).toInt()

    fun flushPage(applyJustifyFill: Boolean = true) {
        if (currentPage.isNotEmpty()) {
            // remainingHeight IS the unclaimed leftover of the page budget at flush time; the
            // fill may never exceed it or the rendered page overflows and clips its last line.
            val justifyFillPx = resolvePageJustifyFillPx(
                applyJustifyFill = applyJustifyFill,
                paragraphSpacingPx = paragraphSpacingPx,
                sliceSpacingBeforePx = currentPage.map { it.spacingBeforePx },
                leftoverPx = remainingHeight.coerceAtLeast(0),
                sliceLineHeightPx = sliceLineHeightPx,
            )
            pages += currentPage.mapIndexed { index, slice ->
                val fillPx = justifyFillPx[index]
                if (fillPx > 0) slice.copy(spacingBeforePx = slice.spacingBeforePx + fillPx) else slice
            }
            // Debug-only budget snapshot: safe/leftover plus each slice's spacing and predicted
            // height. Correlates with the NovelPageReaderTextView view-state log on device.
            // runCatching: android.util.Log is unmocked in plain JVM unit tests and must not
            // break them — diagnostics are best-effort by design.
            if (BuildConfig.DEBUG) {
                runCatching {
                    val flushedPage = pages.last()
                    Log.d(
                        PLAIN_PAGE_PAGINATION_LOG_TAG,
                        buildString {
                            append(logPrefix).append('=').append(pages.size - 1)
                            append(" safe=").append(safeHeight)
                            append(" leftover=").append(remainingHeight)
                            flushedPage.forEach { slice ->
                                append(" [b=").append(slice.blockIndex)
                                append(",sp=").append(slice.spacingBeforePx)
                                append(",s=").append(slice.range.start)
                                append(",l=").append(slice.range.endExclusive - slice.range.start)
                                append(",h=").append(slice.measuredHeightPx)
                                append(']')
                            }
                        },
                    )
                }
            }
            currentPage = mutableListOf()
        }
        remainingHeight = safeHeight
    }

    fun hasReachedMaxPages(): Boolean = maxPages != null && pages.size >= maxPages

    blocks.forEach { block ->
        if (hasReachedMaxPages()) return pages
        val plainText = block.annotatedText.text
        if (plainText.isBlank()) return@forEach
        val glyphPadPx = resolvePageReaderGlyphOverflowPaddingPx(block.metrics.textSizePx)
        val approximateMetrics = ApproximateTextMetrics(
            charsPerLine = (
                (block.blockWidthPx - (block.firstLineIndentPx ?: 0)).coerceAtLeast(1) /
                    (block.metrics.textSizePx.coerceAtLeast(1f) * 0.55f)
                )
                .toInt()
                .coerceAtLeast(15),
            lineHeightPx = (
                block.metrics.textSizePx.coerceAtLeast(1f) *
                    block.metrics.lineHeightMultiplier.coerceAtLeast(1f)
                )
                .toInt()
                .coerceAtLeast(1),
        )
        val blockTextLocale = if (hyphenationEnabled) resolveNovelReaderTextLocale(plainText) else null
        // The render path lays out every slice through buildNovelPageReaderSpannableText, which
        // applies bionic SemiBold and forced-typeface StyleSpans; measuring raw text would wrap
        // narrower lines than the TextView actually draws.
        val initialResult = if (measurement == ReaderTextMeasurement.APPROXIMATE) {
            null
        } else {
            val initialMeasuredText = buildNovelPageReaderSpannableText(
                text = block.annotatedText,
                firstLineIndentPx = block.firstLineIndentPx,
                forcedTypefaceStyle = block.blockForcedStyle,
            )
            resolveWidthHyphenatedReaderTextAndLayout(
                source = initialMeasuredText,
                widthPx = block.blockWidthPx,
                textSizePx = block.metrics.textSizePx,
                lineHeightMultiplier = block.metrics.lineHeightMultiplier,
                typeface = block.blockTypeface,
                textAlign = block.blockTextAlign,
                firstLineIndentPx = null,
                density = density,
                measurementPaint = measurementPaint,
                preferDynamicLayout = preferDynamicLayout,
                hyphenationEnabled = hyphenationEnabled,
                textLocale = blockTextLocale,
                isPageMode = true,
            )
        }
        if (initialResult == null) {
            var start = 0
            var isFirstSliceOfBlock = true
            while (start < plainText.length) {
                val spacingBefore = if (currentPage.isNotEmpty() && isFirstSliceOfBlock) {
                    resolvePageReaderInterBlockSpacingPx(
                        paragraphSpacingPx = paragraphSpacingPx,
                        textSizePx = block.metrics.textSizePx,
                        lineHeightMultiplier = block.metrics.lineHeightMultiplier,
                    )
                } else {
                    0
                }
                if (remainingHeight <= spacingBefore) {
                    flushPage()
                    if (hasReachedMaxPages()) return pages
                    continue
                }
                val range = resolveApproximateTextRangeForHeight(
                    text = plainText,
                    start = start,
                    availableHeight = (remainingHeight - spacingBefore - glyphPadPx).coerceAtLeast(0),
                    metrics = approximateMetrics,
                    allowOversizedFirstLine = currentPage.isEmpty(),
                ) ?: run {
                    flushPage()
                    if (hasReachedMaxPages()) return pages
                    continue
                }
                val sliceHeight = measureApproximateTextHeight(
                    text = plainText.substring(range.start, range.endExclusive),
                    metrics = approximateMetrics,
                ).coerceAtMost(safeHeight)
                currentPage += PaginatedTextSlice(
                    blockIndex = block.blockIndex,
                    range = range,
                    spacingBeforePx = spacingBefore,
                    measuredHeightPx = sliceHeight,
                )
                remainingHeight -= spacingBefore + sliceHeight + glyphPadPx
                start = range.endExclusive
                isFirstSliceOfBlock = false
                if (remainingHeight <= 0 || start < plainText.length) {
                    flushPage()
                    if (hasReachedMaxPages()) return pages
                }
            }
            return@forEach
        }

        var baseSourceOffset = 0
        var activePlainText = plainText
        var activeAnnotatedText = block.annotatedText
        var activePrepared = initialResult.prepared
        var activeLayout = initialResult.layout
        var startLine = 0
        var isFirstSliceOfBlock = true

        while (startLine < activeLayout.lineCount) {
            val spacingBefore = if (currentPage.isNotEmpty() && isFirstSliceOfBlock) {
                resolvePageReaderInterBlockSpacingPx(
                    paragraphSpacingPx = paragraphSpacingPx,
                    textSizePx = block.metrics.textSizePx,
                    lineHeightMultiplier = block.metrics.lineHeightMultiplier,
                )
            } else {
                0
            }

            if (remainingHeight <= spacingBefore) {
                flushPage()
                if (hasReachedMaxPages()) return pages
                continue
            }

            val availableHeight = (remainingHeight - spacingBefore - glyphPadPx).coerceAtLeast(0)
            val sliceIndentPx = if (baseSourceOffset == 0 && activeLayout.getLineStart(startLine) == 0 &&
                !block.isChapterTitle
            ) {
                block.firstLineIndentPx
            } else {
                null
            }
            val wholeBlockTrimmed = trimTextRange(activePlainText, 0, activePlainText.length)
            val fit = resolveStandalonePageSliceFit(
                plainText = activePlainText,
                layout = activeLayout,
                startLine = startLine,
                availableHeight = availableHeight,
                allowOversizedFirstLine = currentPage.isEmpty(),
                displayToSourceOffsets = activePrepared.displayToSourceOffsets,
                avoidMidWordPageBreak = hyphenationEnabled,
            ) { range ->
                if (startLine == 0 && wholeBlockTrimmed == range) {
                    resolveStaticLayoutHeight(activeLayout)
                } else {
                    measurePlainPageSliceStandaloneHeight(
                        sliceText = buildNovelPageReaderSpannableText(
                            text = activeAnnotatedText.subSequence(range.start, range.endExclusive),
                            firstLineIndentPx = sliceIndentPx,
                            forcedTypefaceStyle = block.blockForcedStyle,
                        ),
                        widthPx = block.blockWidthPx,
                        textSizePx = block.metrics.textSizePx,
                        lineHeightMultiplier = block.metrics.lineHeightMultiplier,
                        typeface = block.blockTypeface,
                        textAlign = block.blockTextAlign,
                        density = density,
                        measurementPaint = measurementPaint,
                        preferDynamicLayout = preferDynamicLayout,
                        hyphenationEnabled = hyphenationEnabled,
                        textLocale = blockTextLocale,
                    )
                }
            } ?: run {
                flushPage()
                if (hasReachedMaxPages()) return pages
                continue
            }

            currentPage += PaginatedTextSlice(
                blockIndex = block.blockIndex,
                range = TextPageRange(
                    start = baseSourceOffset + fit.range.start,
                    endExclusive = baseSourceOffset + fit.range.endExclusive,
                ),
                spacingBeforePx = spacingBefore,
                measuredHeightPx = fit.heightPx,
            )
            remainingHeight -= spacingBefore + fit.heightPx + glyphPadPx
            isFirstSliceOfBlock = false

            if (fit.rewoundMidWord) {
                val resumeInActive = fit.nextResumeSourceOffset.coerceIn(fit.range.endExclusive, activePlainText.length)
                val nextGlobalStart = (baseSourceOffset + resumeInActive).coerceAtMost(plainText.length)
                if (nextGlobalStart >= plainText.length) {
                    startLine = activeLayout.lineCount
                } else {
                    baseSourceOffset = nextGlobalStart
                    activePlainText = plainText.substring(nextGlobalStart)
                    activeAnnotatedText = block.annotatedText.subSequence(nextGlobalStart, block.annotatedText.length)
                    val remainingMeasured = buildNovelPageReaderSpannableText(
                        text = activeAnnotatedText,
                        firstLineIndentPx = null,
                        forcedTypefaceStyle = block.blockForcedStyle,
                    )
                    val remainingResult = resolveWidthHyphenatedReaderTextAndLayout(
                        source = remainingMeasured,
                        widthPx = block.blockWidthPx,
                        textSizePx = block.metrics.textSizePx,
                        lineHeightMultiplier = block.metrics.lineHeightMultiplier,
                        typeface = block.blockTypeface,
                        textAlign = block.blockTextAlign,
                        firstLineIndentPx = null,
                        density = density,
                        measurementPaint = measurementPaint,
                        preferDynamicLayout = preferDynamicLayout,
                        hyphenationEnabled = hyphenationEnabled,
                        textLocale = blockTextLocale,
                        isPageMode = true,
                    )
                    activePrepared = remainingResult.prepared
                    activeLayout = remainingResult.layout
                    startLine = 0
                }
            } else {
                startLine = fit.nextStartLine
            }

            // Never emit two slices from the same block onto one page (prevents stacked
            // TextViews with missing inter-line spacing between penultimate and final lines).
            if (remainingHeight <= 0 || startLine < activeLayout.lineCount) {
                flushPage()
                if (hasReachedMaxPages()) return pages
            }
        }
    }

    flushPage(applyJustifyFill = false)
    return pages
}

internal fun paginatePlainPageBlocks(
    textBlocks: List<PlainPageReaderTextBlock>,
    paragraphSpacingPx: Int,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    chapterTitleTypeface: android.graphics.Typeface? = null,
    textAlign: ReaderTextAlign,
    forceParagraphIndent: Boolean = false,
    chapterTitle: String? = null,
    bionicReadingEnabled: Boolean = false,
    forcedTypefaceStyle: Int = android.graphics.Typeface.NORMAL,
    density: Float = 1.0f,
    measurement: ReaderTextMeasurement = ReaderTextMeasurement.STATIC_LAYOUT,
    measurementPaint: TextPaint? = null,
    preferDynamicLayout: Boolean = false,
    hyphenationEnabled: Boolean = false,
    maxPages: Int? = null,
): List<List<PlainPageSlice>> {
    if (maxPages != null && maxPages <= 0) return emptyList()
    val safeWidthPx = widthPx.coerceAtLeast(1)
    val preparedBlocks = textBlocks.mapNotNull { block ->
        if (block.text.isBlank()) return@mapNotNull null
        val isChapterTitle = block.sourceBlockIndex == 0 &&
            chapterTitle != null &&
            isNativeChapterTitleText(block.text, chapterTitle)
        val blockMetrics = resolvePageReaderBlockLayoutMetrics(
            isChapterTitle = isChapterTitle,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
        )
        val firstLineIndentPx = if (forceParagraphIndent && !isChapterTitle) {
            resolvePageReaderFirstLineIndentPx(
                firstLineIndentEm = FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM,
                textSizePx = blockMetrics.textSizePx,
            )
        } else {
            null
        }
        val blockTypeface = if (isChapterTitle) {
            android.graphics.Typeface.create(chapterTitleTypeface ?: typeface, android.graphics.Typeface.BOLD)
        } else {
            typeface
        }
        val blockForcedStyle = if (isChapterTitle) {
            combineTypefaceStyles(android.graphics.Typeface.BOLD, forcedTypefaceStyle)
        } else {
            forcedTypefaceStyle
        }
        PaginatableTextBlock(
            blockIndex = block.sourceBlockIndex,
            annotatedText = if (bionicReadingEnabled) toBionicText(block.text) else AnnotatedString(block.text),
            blockWidthPx = safeWidthPx,
            isChapterTitle = isChapterTitle,
            metrics = blockMetrics,
            firstLineIndentPx = firstLineIndentPx,
            blockTypeface = blockTypeface,
            blockTextAlign = textAlign,
            blockForcedStyle = blockForcedStyle,
        )
    }
    return paginateCharSequencePageBlocks(
        blocks = preparedBlocks,
        paragraphSpacingPx = paragraphSpacingPx,
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        density = density,
        measurement = measurement,
        measurementPaint = measurementPaint,
        preferDynamicLayout = preferDynamicLayout,
        hyphenationEnabled = hyphenationEnabled,
        logPrefix = "page",
        maxPages = maxPages,
    ).map { page ->
        page.map { slice ->
            PlainPageSlice.Text(
                blockIndex = slice.blockIndex,
                range = slice.range,
                spacingBeforePx = slice.spacingBeforePx,
                measuredHeightPx = slice.measuredHeightPx,
            )
        }
    }
}

/** An illustration of the chapter, addressed by the index of its block in the chapter stream. */
internal data class PlainPageImageBlock(
    val sourceBlockIndex: Int,
    val imageUrl: String,
    val contentDescription: String?,
)

/**
 * Paginates the plain page reader including illustrations.
 *
 * Text runs between two illustrations are paginated on their own, so an image always starts where
 * the source placed it instead of vanishing (the plain reader used to consume text blocks only).
 * An image whose preceding page ends in a short paragraph and has enough vertical room joins that
 * page, mirroring the rich page reader, so a lone illustration after a short caption does not
 * waste a whole page while oversized combinations move to the next page cleanly.
 */
internal fun paginatePlainPageBlocksWithImages(
    textBlocks: List<PlainPageReaderTextBlock>,
    imageBlocks: List<PlainPageImageBlock>,
    paragraphSpacingPx: Int,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    chapterTitleTypeface: android.graphics.Typeface? = null,
    textAlign: ReaderTextAlign,
    forceParagraphIndent: Boolean = false,
    chapterTitle: String? = null,
    bionicReadingEnabled: Boolean = false,
    forcedTypefaceStyle: Int = android.graphics.Typeface.NORMAL,
    density: Float = 1.0f,
    measurement: ReaderTextMeasurement = ReaderTextMeasurement.STATIC_LAYOUT,
    measurementPaint: TextPaint? = null,
    preferDynamicLayout: Boolean = false,
    hyphenationEnabled: Boolean = false,
    maxPages: Int? = null,
): List<List<PlainPageSlice>> {
    if (maxPages != null && maxPages <= 0) return emptyList()
    if (imageBlocks.isEmpty()) {
        return paginatePlainPageBlocks(
            textBlocks = textBlocks,
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = heightPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = typeface,
            chapterTitleTypeface = chapterTitleTypeface,
            textAlign = textAlign,
            forceParagraphIndent = forceParagraphIndent,
            chapterTitle = chapterTitle,
            bionicReadingEnabled = bionicReadingEnabled,
            forcedTypefaceStyle = forcedTypefaceStyle,
            density = density,
            measurement = measurement,
            measurementPaint = measurementPaint,
            preferDynamicLayout = preferDynamicLayout,
            hyphenationEnabled = hyphenationEnabled,
            maxPages = maxPages,
        )
    }

    val safeHeightPx = resolvePageReaderSafeHeightPx(
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
    )
    val textBySourceBlockIndex = textBlocks.associateBy({ it.sourceBlockIndex }, { it.text })
    val pages = mutableListOf<List<PlainPageSlice>>()
    val pendingText = mutableListOf<PlainPageReaderTextBlock>()
    val emittedImages = mutableSetOf<Int>()

    fun flushText() {
        if (pendingText.isEmpty()) return
        val remainingPages = maxPages?.let { (it - pages.size).coerceAtLeast(0) }
        if (remainingPages == 0) {
            pendingText.clear()
            return
        }
        pages += paginatePlainPageBlocks(
            textBlocks = pendingText.toList(),
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = heightPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = typeface,
            chapterTitleTypeface = chapterTitleTypeface,
            textAlign = textAlign,
            forceParagraphIndent = forceParagraphIndent,
            chapterTitle = chapterTitle,
            bionicReadingEnabled = bionicReadingEnabled,
            forcedTypefaceStyle = forcedTypefaceStyle,
            density = density,
            measurement = measurement,
            measurementPaint = measurementPaint,
            preferDynamicLayout = preferDynamicLayout,
            hyphenationEnabled = hyphenationEnabled,
            maxPages = remainingPages,
        )
        pendingText.clear()
    }

    fun appendImage(image: PlainPageImageBlock) {
        val slice = PlainPageSlice.Image(
            blockIndex = image.sourceBlockIndex,
            imageUrl = image.imageUrl,
            contentDescription = image.contentDescription,
        )
        val lastPage = pages.lastOrNull()
        val imageSpacingPx = resolvePageReaderInterBlockSpacingPx(
            paragraphSpacingPx = paragraphSpacingPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
        )
        val availableImageHeightPx = lastPage?.let { page ->
            resolvePlainInlineImageMaxHeightPx(
                page = page,
                textBySourceBlockIndex = textBySourceBlockIndex,
                chapterTitle = chapterTitle,
                imageSpacingPx = imageSpacingPx,
                safeHeightPx = safeHeightPx,
                pageHeightPx = heightPx,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                density = density,
            )
        }
        if (lastPage != null && availableImageHeightPx != null) {
            pages[pages.lastIndex] = lastPage + slice.copy(
                spacingBeforePx = imageSpacingPx,
                maxHeightPx = availableImageHeightPx,
            )
        } else {
            pages += listOf(slice)
        }
    }

    textBlocks.forEach { block ->
        imageBlocks.forEach { image ->
            if (image.sourceBlockIndex < block.sourceBlockIndex && emittedImages.add(image.sourceBlockIndex)) {
                flushText()
                if (maxPages != null && pages.size >= maxPages) {
                    val lastPageAttachable = pages.lastOrNull()?.let { page ->
                        val imageSpacingPx = resolvePageReaderInterBlockSpacingPx(
                            paragraphSpacingPx = paragraphSpacingPx,
                            textSizePx = textSizePx,
                            lineHeightMultiplier = lineHeightMultiplier,
                        )
                        resolvePlainInlineImageMaxHeightPx(
                            page = page,
                            textBySourceBlockIndex = textBySourceBlockIndex,
                            chapterTitle = chapterTitle,
                            imageSpacingPx = imageSpacingPx,
                            safeHeightPx = safeHeightPx,
                            pageHeightPx = heightPx,
                            textSizePx = textSizePx,
                            lineHeightMultiplier = lineHeightMultiplier,
                            density = density,
                        ) != null
                    } ?: false
                    if (!lastPageAttachable) return pages.take(maxPages)
                }
                appendImage(image)
                if (maxPages != null && pages.size > maxPages) return pages.take(maxPages)
            }
        }
        if (maxPages != null && pages.size >= maxPages) return pages.take(maxPages)
        pendingText += block
    }
    imageBlocks.forEach { image ->
        if (emittedImages.add(image.sourceBlockIndex)) {
            flushText()
            if (maxPages != null && pages.size >= maxPages) {
                val lastPageAttachable = pages.lastOrNull()?.let { page ->
                    val imageSpacingPx = resolvePageReaderInterBlockSpacingPx(
                        paragraphSpacingPx = paragraphSpacingPx,
                        textSizePx = textSizePx,
                        lineHeightMultiplier = lineHeightMultiplier,
                    )
                    resolvePlainInlineImageMaxHeightPx(
                        page = page,
                        textBySourceBlockIndex = textBySourceBlockIndex,
                        chapterTitle = chapterTitle,
                        imageSpacingPx = imageSpacingPx,
                        safeHeightPx = safeHeightPx,
                        pageHeightPx = heightPx,
                        textSizePx = textSizePx,
                        lineHeightMultiplier = lineHeightMultiplier,
                        density = density,
                    ) != null
                } ?: false
                if (!lastPageAttachable) return pages.take(maxPages)
            }
            appendImage(image)
            if (maxPages != null && pages.size > maxPages) return pages.take(maxPages)
        }
    }
    flushText()
    return if (maxPages != null && pages.size > maxPages) pages.take(maxPages) else pages
}

private fun resolvePlainInlineImageMaxHeightPx(
    page: List<PlainPageSlice>,
    textBySourceBlockIndex: Map<Int, String>,
    chapterTitle: String?,
    imageSpacingPx: Int,
    safeHeightPx: Int,
    pageHeightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    density: Float,
): Int? {
    if (page.isEmpty() || page.any { it is PlainPageSlice.Image }) return null
    val slices = page.filterIsInstance<PlainPageSlice.Text>().map { slice ->
        PaginatedTextSlice(slice.blockIndex, slice.range, slice.spacingBeforePx, slice.measuredHeightPx)
    }
    return resolveTextSlicesInlineImageMaxHeightPx(
        textSlices = slices,
        resolveFontScale = { slice ->
            val isTitle = slice.blockIndex == 0 &&
                chapterTitle != null &&
                slice.range.start == 0 &&
                isNativeChapterTitleText(textBySourceBlockIndex[slice.blockIndex].orEmpty(), chapterTitle)
            if (isTitle) PAGE_READER_CHAPTER_TITLE_FONT_SIZE_MULTIPLIER else 1f
        },
        imageSpacingPx = imageSpacingPx,
        safeHeightPx = safeHeightPx,
        pageHeightPx = pageHeightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        density = density,
    )
}

private fun resolveTextSlicesInlineImageMaxHeightPx(
    textSlices: List<PaginatedTextSlice>,
    resolveFontScale: (PaginatedTextSlice) -> Float,
    imageSpacingPx: Int,
    safeHeightPx: Int,
    pageHeightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    density: Float,
): Int? {
    val textLength = textSlices.sumOf { slice -> slice.range.endExclusive - slice.range.start }
    val usedHeightPx = textSlices.sumOf { slice ->
        val fontScale = resolveFontScale(slice)
        val sliceGlyphPadPx = resolvePageReaderGlyphOverflowPaddingPx(textSizePx * fontScale)
        val sliceHeight = slice.measuredHeightPx.takeIf { it > 0 }
            ?: (textSizePx * fontScale * lineHeightMultiplier).roundToInt()
        slice.spacingBeforePx + sliceHeight + sliceGlyphPadPx
    }
    return resolveAttachableInlineImageMaxHeightPx(
        precedingTextLength = textLength,
        usedHeightPx = usedHeightPx,
        imageSpacingPx = imageSpacingPx,
        safeHeightPx = safeHeightPx,
        pageHeightPx = pageHeightPx,
        density = density,
    )
}

internal fun buildRichPageReaderChapterAnnotatedText(
    richBlocks: List<NovelRichContentBlock>,
    paragraphSpacingDp: Int,
    forcedParagraphFirstLineIndentEm: Float? = null,
): AnnotatedString {
    if (richBlocks.isEmpty()) return AnnotatedString("")

    val paragraphSeparator = resolvePageReaderParagraphSeparator(paragraphSpacingDp)

    return buildAnnotatedString {
        var appendedAny = false
        richBlocks.forEach { block ->
            val blockText = buildRichPageReaderBlockAnnotatedText(
                block = block,
                forcedParagraphFirstLineIndentEm = forcedParagraphFirstLineIndentEm,
            )
            if (blockText.text.isBlank()) return@forEach
            if (appendedAny) append(paragraphSeparator)
            append(blockText)
            appendedAny = true
        }
    }
}

internal fun buildRichPageReaderBlockAnnotatedText(
    block: NovelRichContentBlock,
    forcedParagraphFirstLineIndentEm: Float? = null,
): AnnotatedString {
    return buildRichPageReaderBlockText(
        block = block,
        forcedParagraphFirstLineIndentEm = forcedParagraphFirstLineIndentEm,
    ).text
}

internal fun buildRichPageReaderBlockText(
    block: NovelRichContentBlock,
    sourceBlockIndex: Int = -1,
    forcedParagraphFirstLineIndentEm: Float? = null,
): RichPageBlockText {
    val blockText: AnnotatedString = when (block) {
        is NovelRichContentBlock.Paragraph -> buildNovelRichAnnotatedString(block.segments)
        is NovelRichContentBlock.Heading -> buildNovelRichAnnotatedString(block.segments)
        is NovelRichContentBlock.BlockQuote -> buildNovelRichAnnotatedString(block.segments)
        is NovelRichContentBlock.HorizontalRule -> AnnotatedString("* * *")
        is NovelRichContentBlock.Image -> AnnotatedString("")
    }
    if (blockText.text.isBlank()) {
        return RichPageBlockText(
            sourceBlockIndex = sourceBlockIndex,
            text = AnnotatedString(""),
            firstLineIndentEm = null,
            sourceTextAlign = null,
        )
    }
    return when (block) {
        is NovelRichContentBlock.Heading -> {
            val headingScale = when (block.level) {
                1 -> 1.24f
                2 -> 1.18f
                3 -> 1.13f
                else -> 1.08f
            }
            RichPageBlockText(
                sourceBlockIndex = sourceBlockIndex,
                text = blockText,
                firstLineIndentEm = null,
                sourceTextAlign = block.textAlign,
                fontSizeMultiplier = headingScale,
                lineHeightMultiplier = 1.1f,
                baseTypefaceStyle = android.graphics.Typeface.BOLD,
            )
        }
        is NovelRichContentBlock.BlockQuote -> RichPageBlockText(
            sourceBlockIndex = sourceBlockIndex,
            text = blockText,
            firstLineIndentEm = null,
            sourceTextAlign = block.textAlign,
            startIndentDp = 12,
            isBlockQuote = true,
        )
        is NovelRichContentBlock.HorizontalRule -> RichPageBlockText(
            sourceBlockIndex = sourceBlockIndex,
            text = blockText,
            firstLineIndentEm = null,
            sourceTextAlign = NovelRichBlockTextAlign.CENTER,
            isHorizontalRule = true,
        )
        is NovelRichContentBlock.Paragraph -> RichPageBlockText(
            sourceBlockIndex = sourceBlockIndex,
            text = blockText,
            firstLineIndentEm = forcedParagraphFirstLineIndentEm ?: block.firstLineIndentEm,
            sourceTextAlign = block.textAlign,
        )
        is NovelRichContentBlock.Image -> RichPageBlockText(
            sourceBlockIndex = sourceBlockIndex,
            text = AnnotatedString(""),
            firstLineIndentEm = null,
            sourceTextAlign = null,
        )
    }
}

internal fun paginateRichPageBlocks(
    blockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    chapterTitleTypeface: android.graphics.Typeface? = null,
    textAlign: ReaderTextAlign,
    preserveSourceTextAlignInNative: Boolean = false,
    chapterTitle: String? = null,
    forcedTypefaceStyle: Int = android.graphics.Typeface.NORMAL,
    allowChapterTitleBlock: Boolean = true,
    density: Float = 1.0f,
    measurement: ReaderTextMeasurement = ReaderTextMeasurement.STATIC_LAYOUT,
    measurementPaint: TextPaint? = null,
    preferDynamicLayout: Boolean = false,
    hyphenationEnabled: Boolean = false,
    maxPages: Int? = null,
): List<List<RichPageSlice.Text>> {
    if (maxPages != null && maxPages <= 0) return emptyList()
    val preparedBlocks = blockTexts.mapIndexedNotNull { fallbackIndex, blockText ->
        val blockIndex = resolveRichPageBlockIndex(blockText.sourceBlockIndex, fallbackIndex)
        if (blockText.text.text.isBlank()) return@mapIndexedNotNull null
        val isChapterTitle = allowChapterTitleBlock &&
            blockIndex == 0 &&
            chapterTitle != null &&
            isNativeChapterTitleText(blockText.text.text, chapterTitle)
        val firstLineIndentEm = if (isChapterTitle) null else blockText.firstLineIndentEm
        val blockMetrics = resolvePageReaderBlockLayoutMetrics(
            isChapterTitle = isChapterTitle,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            fontSizeMultiplier = blockText.fontSizeMultiplier,
            blockLineHeightMultiplier = blockText.lineHeightMultiplier,
        )
        val blockStartIndentPx = if (isChapterTitle) {
            0
        } else {
            (blockText.startIndentDp.coerceAtLeast(0) * density).roundToInt().coerceAtLeast(0)
        }
        val blockWidthPx = (widthPx - blockStartIndentPx).coerceAtLeast(1)
        val firstLineIndentPx = resolvePageReaderFirstLineIndentPx(
            firstLineIndentEm = firstLineIndentEm,
            textSizePx = blockMetrics.textSizePx,
        )
        val blockTextAlign = if (blockText.isHorizontalRule) {
            ReaderTextAlign.CENTER
        } else {
            resolveNativeReaderTextAlign(
                globalTextAlign = textAlign,
                preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                sourceTextAlign = blockText.sourceTextAlign,
            )
        }
        val blockForcedStyle = if (isChapterTitle) {
            combineTypefaceStyles(android.graphics.Typeface.BOLD, forcedTypefaceStyle)
        } else {
            combineTypefaceStyles(blockText.baseTypefaceStyle, forcedTypefaceStyle)
        }
        val blockTypeface = if (isChapterTitle) {
            android.graphics.Typeface.create(chapterTitleTypeface ?: typeface, android.graphics.Typeface.BOLD)
        } else {
            typeface
        }
        PaginatableTextBlock(
            blockIndex = blockIndex,
            annotatedText = blockText.text,
            blockWidthPx = blockWidthPx,
            isChapterTitle = isChapterTitle,
            metrics = blockMetrics,
            firstLineIndentPx = firstLineIndentPx,
            blockTypeface = blockTypeface,
            blockTextAlign = blockTextAlign,
            blockForcedStyle = blockForcedStyle,
        )
    }
    return paginateCharSequencePageBlocks(
        blocks = preparedBlocks,
        paragraphSpacingPx = paragraphSpacingPx,
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        density = density,
        measurement = measurement,
        measurementPaint = measurementPaint,
        preferDynamicLayout = preferDynamicLayout,
        hyphenationEnabled = hyphenationEnabled,
        logPrefix = "richPage",
        maxPages = maxPages,
    ).map { page ->
        page.map { slice ->
            RichPageSlice.Text(
                blockIndex = slice.blockIndex,
                range = slice.range,
                spacingBeforePx = slice.spacingBeforePx,
                measuredHeightPx = slice.measuredHeightPx,
            )
        }
    }
}

internal data class MixedRichPagePagination(
    val blockTexts: List<RichPageBlockText>,
    val pages: List<List<RichPageSlice>>,
)

internal fun paginateMixedRichPageBlocks(
    richBlocks: List<IndexedValue<NovelRichContentBlock>>,
    paragraphSpacingPx: Int,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    chapterTitleTypeface: android.graphics.Typeface? = null,
    textAlign: ReaderTextAlign,
    preserveSourceTextAlignInNative: Boolean = false,
    forceParagraphIndent: Boolean,
    chapterTitle: String? = null,
    forcedTypefaceStyle: Int = android.graphics.Typeface.NORMAL,
    density: Float = 1.0f,
    measurement: ReaderTextMeasurement = ReaderTextMeasurement.STATIC_LAYOUT,
    measurementPaint: TextPaint? = null,
    preferDynamicLayout: Boolean = false,
    hyphenationEnabled: Boolean = false,
    maxPages: Int? = null,
): MixedRichPagePagination {
    if (maxPages != null && maxPages <= 0) {
        return MixedRichPagePagination(blockTexts = emptyList(), pages = emptyList())
    }
    val safeHeightPx = resolvePageReaderSafeHeightPx(
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
    )
    val allBlockTexts = mutableListOf<RichPageBlockText>()
    val currentChunkTexts = mutableListOf<RichPageBlockText>()
    val pages = mutableListOf<List<RichPageSlice>>()
    var currentChunkCanUseChapterTitle = true
    var lastChunkUsedChapterTitle = false
    var sawRenderableSourceBlock = false

    fun flushCurrentChunk() {
        if (currentChunkTexts.isEmpty()) return
        val remainingPages = maxPages?.let { (it - pages.size).coerceAtLeast(0) }
        if (remainingPages == 0) {
            currentChunkTexts.clear()
            currentChunkCanUseChapterTitle = false
            return
        }
        lastChunkUsedChapterTitle = currentChunkCanUseChapterTitle
        pages += paginateRichPageBlocks(
            blockTexts = currentChunkTexts.toList(),
            paragraphSpacingPx = paragraphSpacingPx,
            widthPx = widthPx,
            heightPx = heightPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = typeface,
            chapterTitleTypeface = chapterTitleTypeface,
            textAlign = textAlign,
            preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
            chapterTitle = chapterTitle,
            forcedTypefaceStyle = forcedTypefaceStyle,
            allowChapterTitleBlock = currentChunkCanUseChapterTitle,
            density = density,
            measurement = measurement,
            measurementPaint = measurementPaint,
            preferDynamicLayout = preferDynamicLayout,
            hyphenationEnabled = hyphenationEnabled,
            maxPages = remainingPages,
        )
        currentChunkTexts.clear()
        currentChunkCanUseChapterTitle = false
    }

    fun appendImagePage(image: RichPageSlice.Image) {
        val lastPage = pages.lastOrNull()
        val imageSpacingPx = resolvePageReaderInterBlockSpacingPx(
            paragraphSpacingPx = paragraphSpacingPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
        )
        val availableImageHeightPx = lastPage?.let { page ->
            resolveRichInlineImageMaxHeightPx(
                page = page,
                blockTexts = allBlockTexts,
                chapterTitle = chapterTitle,
                lastChunkUsedChapterTitle = lastChunkUsedChapterTitle,
                imageSpacingPx = imageSpacingPx,
                safeHeightPx = safeHeightPx,
                pageHeightPx = heightPx,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                density = density,
            )
        }
        if (lastPage != null && availableImageHeightPx != null) {
            pages[pages.lastIndex] = lastPage + image.copy(
                spacingBeforePx = imageSpacingPx,
                maxHeightPx = availableImageHeightPx,
            )
        } else {
            pages += listOf(image)
        }
    }

    richBlocks.forEach { indexedBlock ->
        val sourceBlockIndex = indexedBlock.index
        val block = indexedBlock.value
        when (block) {
            is NovelRichContentBlock.Image -> {
                flushCurrentChunk()
                if (maxPages != null && pages.size >= maxPages) {
                    val lastPageAttachable = pages.lastOrNull()?.let { page ->
                        val imageSpacingPx = resolvePageReaderInterBlockSpacingPx(
                            paragraphSpacingPx = paragraphSpacingPx,
                            textSizePx = textSizePx,
                            lineHeightMultiplier = lineHeightMultiplier,
                        )
                        resolveRichInlineImageMaxHeightPx(
                            page = page,
                            blockTexts = allBlockTexts,
                            chapterTitle = chapterTitle,
                            lastChunkUsedChapterTitle = lastChunkUsedChapterTitle,
                            imageSpacingPx = imageSpacingPx,
                            safeHeightPx = safeHeightPx,
                            pageHeightPx = heightPx,
                            textSizePx = textSizePx,
                            lineHeightMultiplier = lineHeightMultiplier,
                            density = density,
                        ) != null
                    } ?: false
                    if (!lastPageAttachable) {
                        return MixedRichPagePagination(
                            blockTexts = allBlockTexts,
                            pages = pages.take(maxPages),
                        )
                    }
                }
                appendImagePage(
                    RichPageSlice.Image(
                        sourceBlockIndex = sourceBlockIndex,
                        imageUrl = block.url,
                        contentDescription = block.alt,
                    ),
                )
                if (maxPages != null && pages.size > maxPages) {
                    return MixedRichPagePagination(
                        blockTexts = allBlockTexts,
                        pages = pages.take(maxPages),
                    )
                }
                sawRenderableSourceBlock = true
            }
            else -> {
                if (maxPages != null && pages.size >= maxPages) {
                    return MixedRichPagePagination(
                        blockTexts = allBlockTexts,
                        pages = pages.take(maxPages),
                    )
                }
                val blockText = buildRichPageReaderBlockText(
                    block = block,
                    sourceBlockIndex = sourceBlockIndex,
                    forcedParagraphFirstLineIndentEm = if (forceParagraphIndent) {
                        FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
                    } else {
                        null
                    },
                )
                if (blockText.text.text.isBlank()) return@forEach
                if (currentChunkTexts.isEmpty()) currentChunkCanUseChapterTitle = !sawRenderableSourceBlock
                allBlockTexts += blockText
                currentChunkTexts += blockText
                sawRenderableSourceBlock = true
            }
        }
    }

    flushCurrentChunk()
    return MixedRichPagePagination(
        blockTexts = allBlockTexts,
        pages = if (maxPages != null && pages.size > maxPages) pages.take(maxPages) else pages,
    )
}

private fun resolveRichInlineImageMaxHeightPx(
    page: List<RichPageSlice>,
    blockTexts: List<RichPageBlockText>,
    chapterTitle: String?,
    lastChunkUsedChapterTitle: Boolean,
    imageSpacingPx: Int,
    safeHeightPx: Int,
    pageHeightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    density: Float,
): Int? {
    if (page.isEmpty() || page.any { it is RichPageSlice.Image }) return null
    val blockTextBySourceBlockIndex = blockTexts.withIndex().associateBy(
        keySelector = { resolveRichPageBlockIndex(it.value.sourceBlockIndex, it.index) },
        valueTransform = { it.value },
    )
    val slices = page.filterIsInstance<RichPageSlice.Text>().map { slice ->
        PaginatedTextSlice(slice.blockIndex, slice.range, slice.spacingBeforePx, slice.measuredHeightPx)
    }
    return resolveTextSlicesInlineImageMaxHeightPx(
        textSlices = slices,
        resolveFontScale = { slice ->
            val blockText = blockTextBySourceBlockIndex[slice.blockIndex]
            val isTitle = lastChunkUsedChapterTitle &&
                slice.blockIndex == 0 &&
                chapterTitle != null &&
                blockText != null &&
                slice.range.start == 0 &&
                isNativeChapterTitleText(blockText.text.text, chapterTitle)
            if (isTitle) {
                PAGE_READER_CHAPTER_TITLE_FONT_SIZE_MULTIPLIER
            } else {
                blockText?.fontSizeMultiplier ?: 1f
            }
        },
        imageSpacingPx = imageSpacingPx,
        safeHeightPx = safeHeightPx,
        pageHeightPx = pageHeightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        density = density,
    )
}

internal fun buildPlainPageRenderBlocks(
    page: List<PlainPageSlice>,
    textBlocks: List<PlainPageReaderTextBlock>,
    paragraphSpacingPx: Int,
    forceParagraphIndent: Boolean,
    chapterTitle: String? = null,
): List<PlainPageRenderBlock> {
    if (page.isEmpty()) return emptyList()
    val textBySourceBlockIndex = textBlocks.associateBy({ it.sourceBlockIndex }, { it.text })
    val textSlices = page.filterIsInstance<PlainPageSlice.Text>()
    return textSlices.mapIndexed { index, slice ->
        val previousBlockIndex = textSlices.getOrNull(index - 1)?.blockIndex
        val startsNewBlock = index > 0 && previousBlockIndex != slice.blockIndex
        val fullBlockText = textBySourceBlockIndex[slice.blockIndex].orEmpty()
        PlainPageRenderBlock(
            sourceBlockIndex = slice.blockIndex,
            text = fullBlockText.substring(slice.range.start, slice.range.endExclusive),
            sourceTextStart = slice.range.start,
            sourceTextEndExclusive = slice.range.endExclusive,
            spacingBeforePx = if (startsNewBlock) slice.spacingBeforePx.coerceAtLeast(0) else 0,
            firstLineIndentEm = if (
                forceParagraphIndent &&
                slice.range.start == 0 &&
                !(
                    chapterTitle != null &&
                        slice.blockIndex == 0 &&
                        isNativeChapterTitleText(fullBlockText, chapterTitle)
                    )
            ) {
                FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
            } else {
                null
            },
            isChapterTitle = chapterTitle != null &&
                slice.blockIndex == 0 &&
                slice.range.start == 0 &&
                isNativeChapterTitleText(fullBlockText, chapterTitle),
            measuredHeightPx = slice.measuredHeightPx,
        )
    }
}

internal fun buildRichPageRenderBlocks(
    page: List<RichPageSlice.Text>,
    blockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    chapterTitle: String? = null,
    allowChapterTitleBlock: Boolean = true,
): List<RichPageRenderBlock> {
    if (page.isEmpty()) return emptyList()
    val blockTextBySourceBlockIndex = blockTexts.withIndex().associateBy(
        keySelector = { resolveRichPageBlockIndex(it.value.sourceBlockIndex, it.index) },
        valueTransform = { it.value },
    )
    return page.mapIndexed { index, slice ->
        val previousBlockIndex = page.getOrNull(index - 1)?.blockIndex
        val startsNewBlock = index > 0 && previousBlockIndex != slice.blockIndex
        val blockText = blockTextBySourceBlockIndex.getValue(slice.blockIndex)
        val isChapterTitle = allowChapterTitleBlock &&
            chapterTitle != null &&
            slice.blockIndex == 0 &&
            slice.range.start == 0 &&
            isNativeChapterTitleText(blockText.text.text, chapterTitle)
        RichPageRenderBlock(
            sourceBlockIndex = slice.blockIndex,
            text = blockText.text.subSequence(TextRange(slice.range.start, slice.range.endExclusive)),
            sourceTextStart = slice.range.start,
            sourceTextEndExclusive = slice.range.endExclusive,
            spacingBeforePx = if (startsNewBlock) slice.spacingBeforePx.coerceAtLeast(0) else 0,
            firstLineIndentEm = if (slice.range.start == 0 && !isChapterTitle) blockText.firstLineIndentEm else null,
            sourceTextAlign = blockText.sourceTextAlign,
            isChapterTitle = isChapterTitle,
            measuredHeightPx = slice.measuredHeightPx,
            fontSizeMultiplier = blockText.fontSizeMultiplier,
            lineHeightMultiplier = blockText.lineHeightMultiplier,
            baseTypefaceStyle = blockText.baseTypefaceStyle,
            startIndentDp = if (isChapterTitle) 0 else blockText.startIndentDp,
            isBlockQuote = !isChapterTitle && blockText.isBlockQuote,
            isHorizontalRule = !isChapterTitle && blockText.isHorizontalRule,
        )
    }
}

private fun buildMixedRichPageContentBlocks(
    page: List<RichPageSlice>,
    blockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    chapterTitle: String? = null,
): List<NovelPageContentBlock> {
    val textBlocks = buildRichPageRenderBlocks(
        page = page.filterIsInstance<RichPageSlice.Text>(),
        blockTexts = blockTexts,
        paragraphSpacingPx = paragraphSpacingPx,
        chapterTitle = chapterTitle,
    ).map { block ->
        NovelPageContentBlock.Rich(
            sourceBlockIndex = block.sourceBlockIndex,
            text = block.text,
            sourceTextStart = block.sourceTextStart,
            sourceTextEndExclusive = block.sourceTextEndExclusive,
            spacingBeforePx = block.spacingBeforePx,
            firstLineIndentEm = block.firstLineIndentEm,
            sourceTextAlign = block.sourceTextAlign,
            isChapterTitle = block.isChapterTitle,
            measuredHeightPx = block.measuredHeightPx,
            fontSizeMultiplier = block.fontSizeMultiplier,
            lineHeightMultiplier = block.lineHeightMultiplier,
            baseTypefaceStyle = block.baseTypefaceStyle,
            startIndentDp = block.startIndentDp,
            isBlockQuote = block.isBlockQuote,
            isHorizontalRule = block.isHorizontalRule,
        )
    }.iterator()

    return page.mapNotNull { slice ->
        when (slice) {
            is RichPageSlice.Text -> if (textBlocks.hasNext()) textBlocks.next() else null
            is RichPageSlice.Image -> NovelPageContentBlock.Image(
                imageUrl = slice.imageUrl,
                contentDescription = slice.contentDescription,
                spacingBeforePx = slice.spacingBeforePx,
                maxHeightPx = slice.maxHeightPx,
            )
        }
    }
}

internal fun normalizePageReaderContentPages(
    useRichPageReader: Boolean,
    plainPages: List<List<PlainPageSlice>>,
    richPages: List<List<RichPageSlice>>,
    plainTextBlocks: List<PlainPageReaderTextBlock>,
    richBlockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    forceParagraphIndent: Boolean,
    chapterTitle: String? = null,
): List<NovelPageContentPage> {
    return if (useRichPageReader) {
        richPages.mapIndexed { pageIndex, page ->
            val imagePage = page.singleOrNull() as? RichPageSlice.Image
            if (imagePage != null) {
                NovelPageContentPage(
                    blocks = listOf(
                        NovelPageContentBlock.Image(
                            imageUrl = imagePage.imageUrl,
                            contentDescription = imagePage.contentDescription,
                        ),
                    ),
                    pageIndex = pageIndex,
                )
            } else {
                NovelPageContentPage(
                    blocks = buildMixedRichPageContentBlocks(
                        page = page,
                        blockTexts = richBlockTexts,
                        paragraphSpacingPx = paragraphSpacingPx,
                        chapterTitle = chapterTitle,
                    ),
                    pageIndex = pageIndex,
                )
            }
        }
    } else {
        plainPages.mapIndexed { pageIndex, page ->
            NovelPageContentPage(
                blocks = buildPlainPageContentBlocks(
                    page = page,
                    textBlocks = plainTextBlocks,
                    paragraphSpacingPx = paragraphSpacingPx,
                    forceParagraphIndent = forceParagraphIndent,
                    chapterTitle = chapterTitle,
                ),
                pageIndex = pageIndex,
            )
        }
    }
}

/**
 * Turns one plain page into renderable content blocks, keeping illustrations in place.
 *
 * Text slices map onto the measured render blocks in order; image slices become image blocks at
 * the very position the pagination gave them, so a page that ends with an illustration renders it
 * after the last line of text instead of dropping it.
 */
private fun buildPlainPageContentBlocks(
    page: List<PlainPageSlice>,
    textBlocks: List<PlainPageReaderTextBlock>,
    paragraphSpacingPx: Int,
    forceParagraphIndent: Boolean,
    chapterTitle: String? = null,
): List<NovelPageContentBlock> {
    val renderBlocks = buildPlainPageRenderBlocks(
        page = page,
        textBlocks = textBlocks,
        paragraphSpacingPx = paragraphSpacingPx,
        forceParagraphIndent = forceParagraphIndent,
        chapterTitle = chapterTitle,
    ).iterator()
    return page.mapNotNull { slice ->
        when (slice) {
            is PlainPageSlice.Text -> if (renderBlocks.hasNext()) {
                val block = renderBlocks.next()
                NovelPageContentBlock.Plain(
                    sourceBlockIndex = block.sourceBlockIndex,
                    text = block.text,
                    sourceTextStart = block.sourceTextStart,
                    sourceTextEndExclusive = block.sourceTextEndExclusive,
                    spacingBeforePx = block.spacingBeforePx,
                    firstLineIndentEm = block.firstLineIndentEm,
                    isChapterTitle = block.isChapterTitle,
                    measuredHeightPx = block.measuredHeightPx,
                )
            } else {
                null
            }
            is PlainPageSlice.Image -> NovelPageContentBlock.Image(
                imageUrl = slice.imageUrl,
                contentDescription = slice.contentDescription,
                spacingBeforePx = slice.spacingBeforePx,
                maxHeightPx = slice.maxHeightPx,
            )
        }
    }
}

internal fun resolvePageReaderParagraphSeparator(
    paragraphSpacingDp: Int,
): String {
    return if (paragraphSpacingDp.coerceIn(0, 32) >= 20) "\n\n" else "\n"
}
