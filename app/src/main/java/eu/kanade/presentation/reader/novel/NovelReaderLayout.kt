package eu.kanade.presentation.reader.novel

import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichBlockTextAlign
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
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

internal fun autoScrollScrollStepPx(speed: Int): Float {
    val clamped = speed.coerceIn(1, 100)
    return 1.5f + (clamped - 1) * (8.5f / 99f)
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
): Boolean {
    return false
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
): List<TextPageRange> {
    if (text.isBlank()) return emptyList()

    val safeWidth = widthPx.coerceAtLeast(1)
    val safeHeight = heightPx.coerceAtLeast(1)

    val layout = buildReaderStaticLayout(
        text = text,
        widthPx = safeWidth,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
        firstLineIndentPx = firstLineIndentPx,
    ) ?: run {
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
): List<AnnotatedString> {
    if (text.text.isBlank()) return emptyList()
    val ranges = paginateTextIntoPageRanges(
        text = text.text,
        widthPx = widthPx,
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
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
): List<String> {
    return paginateTextIntoPageRanges(
        text = text,
        widthPx = widthPx,
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
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

private data class PageReaderBlockLayoutMetrics(
    val textSizePx: Float,
    val lineHeightMultiplier: Float,
)

internal fun buildReaderStaticLayout(
    text: CharSequence,
    widthPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    firstLineIndentPx: Int? = null,
): StaticLayout? {
    return runCatching {
        val layoutText = firstLineIndentPx
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
        val paint = TextPaint().apply {
            isAntiAlias = true
            this.textSize = textSizePx.coerceAtLeast(1f)
            this.typeface = typeface
        }
        StaticLayout.Builder.obtain(layoutText, 0, layoutText.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(textAlign.toLayoutAlignment())
            .setIncludePad(false)
            .setLineSpacing(0f, lineHeightMultiplier.coerceAtLeast(1f))
            .build()
    }.getOrNull()
}

internal fun resolveStaticLayoutHeight(
    layout: StaticLayout,
): Int {
    if (layout.lineCount <= 0) return 0
    return layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(0)
}

internal fun resolveStaticLayoutSliceForHeight(
    text: CharSequence,
    layout: StaticLayout,
    startLine: Int,
    availableHeight: Int,
): StaticLayoutSlice? {
    if (startLine >= layout.lineCount || availableHeight <= 0) return null

    val startOffset = layout.getLineStart(startLine)
    var endLineExclusive = startLine
    while (
        endLineExclusive < layout.lineCount &&
        layout.getLineBottom(endLineExclusive) - layout.getLineTop(startLine) <= availableHeight
    ) {
        endLineExclusive++
    }
    val endLine = if (endLineExclusive > startLine) endLineExclusive - 1 else startLine
    val endOffset = layout.getLineEnd(endLine).coerceIn(startOffset, text.length)
    val range = trimTextRange(text, startOffset, endOffset) ?: return null
    return StaticLayoutSlice(
        range = range,
        nextStartLine = endLine + 1,
        heightPx = layout.getLineBottom(endLine) - layout.getLineTop(startLine),
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
): PageReaderBlockLayoutMetrics {
    return if (isChapterTitle) {
        PageReaderBlockLayoutMetrics(
            textSizePx = textSizePx * PAGE_READER_CHAPTER_TITLE_FONT_SIZE_MULTIPLIER,
            lineHeightMultiplier = lineHeightMultiplier *
                PAGE_READER_CHAPTER_TITLE_LINE_HEIGHT_MULTIPLIER,
        )
    } else {
        PageReaderBlockLayoutMetrics(
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
        )
    }
}

private fun ReaderTextAlign.toLayoutAlignment(): Layout.Alignment {
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

internal data class PlainPageSlice(
    val blockIndex: Int,
    val range: TextPageRange,
)

internal data class RichPageSlice(
    val blockIndex: Int,
    val range: TextPageRange,
)

internal data class PlainPageRenderBlock(
    val text: String,
    val spacingBeforePx: Int,
    val firstLineIndentEm: Float?,
    val isChapterTitle: Boolean = false,
)

internal data class RichPageBlockText(
    val text: AnnotatedString,
    val firstLineIndentEm: Float?,
    val sourceTextAlign: NovelRichBlockTextAlign? = null,
)

internal data class RichPageRenderBlock(
    val text: AnnotatedString,
    val spacingBeforePx: Int,
    val firstLineIndentEm: Float?,
    val sourceTextAlign: NovelRichBlockTextAlign? = null,
    val isChapterTitle: Boolean = false,
)

internal sealed interface NovelPageContentBlock {
    val spacingBeforePx: Int
    val firstLineIndentEm: Float?
    val isChapterTitle: Boolean

    data class Plain(
        val text: String,
        override val spacingBeforePx: Int,
        override val firstLineIndentEm: Float?,
        override val isChapterTitle: Boolean = false,
    ) : NovelPageContentBlock

    data class Rich(
        val text: AnnotatedString,
        override val spacingBeforePx: Int,
        override val firstLineIndentEm: Float?,
        val sourceTextAlign: NovelRichBlockTextAlign? = null,
        override val isChapterTitle: Boolean = false,
    ) : NovelPageContentBlock
}

internal data class NovelPageContentPage(
    val blocks: List<NovelPageContentBlock>,
)

internal fun paginatePlainPageBlocks(
    textBlocks: List<String>,
    paragraphSpacingPx: Int,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    forceParagraphIndent: Boolean = false,
    chapterTitle: String? = null,
): List<List<PlainPageSlice>> {
    val safeHeight = heightPx.coerceAtLeast(1)
    val pages = mutableListOf<MutableList<PlainPageSlice>>()
    var currentPage = mutableListOf<PlainPageSlice>()
    var remainingHeight = safeHeight

    fun flushPage() {
        if (currentPage.isNotEmpty()) {
            pages += currentPage
            currentPage = mutableListOf()
        }
        remainingHeight = safeHeight
    }

    textBlocks.forEachIndexed { blockIndex, text ->
        if (text.isBlank()) return@forEachIndexed
        val isChapterTitle = blockIndex == 0 &&
            chapterTitle != null &&
            isNativeChapterTitleText(text, chapterTitle)
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
        val approximateMetrics = ApproximateTextMetrics(
            charsPerLine = ((widthPx - (firstLineIndentPx ?: 0)).coerceAtLeast(1) /
                (blockMetrics.textSizePx.coerceAtLeast(1f) * 0.55f))
                .toInt()
                .coerceAtLeast(15),
            lineHeightPx = (blockMetrics.textSizePx.coerceAtLeast(1f) *
                blockMetrics.lineHeightMultiplier.coerceAtLeast(1f))
                .toInt()
                .coerceAtLeast(1),
        )
        val layout = buildReaderStaticLayout(
            text = text,
            widthPx = widthPx,
            textSizePx = blockMetrics.textSizePx,
            lineHeightMultiplier = blockMetrics.lineHeightMultiplier,
            typeface = typeface,
            textAlign = textAlign,
            firstLineIndentPx = firstLineIndentPx,
        )
        if (layout == null) {
            val blockPages = paginateTextIntoPageRanges(
                text = text,
                widthPx = widthPx,
                heightPx = safeHeight,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = typeface,
                textAlign = textAlign,
                firstLineIndentPx = firstLineIndentPx,
            )
            val blockHeight = measureApproximateTextHeight(
                text = text,
                metrics = approximateMetrics,
            )
            blockPages.forEachIndexed { sliceIndex, range ->
                val spacingBefore = if (currentPage.isNotEmpty() && sliceIndex == 0) {
                    paragraphSpacingPx.coerceAtLeast(0)
                } else {
                    0
                }
                if (remainingHeight <= spacingBefore) {
                    flushPage()
                }
                if (
                    currentPage.isNotEmpty() &&
                    sliceIndex == 0 &&
                    blockHeight <= safeHeight &&
                    blockHeight + spacingBefore > remainingHeight
                ) {
                    flushPage()
                }
                val sliceHeight = measureApproximateTextHeight(
                    text = text.substring(range.start, range.endExclusive),
                    metrics = approximateMetrics,
                ).coerceAtMost(safeHeight)
                currentPage += PlainPageSlice(blockIndex = blockIndex, range = range)
                remainingHeight -= spacingBefore + sliceHeight
                if (sliceIndex < blockPages.lastIndex || remainingHeight <= 0) {
                    flushPage()
                }
            }
            return@forEachIndexed
        }

        val blockHeight = resolveStaticLayoutHeight(layout)
        var startLine = 0
        var isFirstSliceOfBlock = true

        while (startLine < layout.lineCount) {
            val spacingBefore = if (currentPage.isNotEmpty() && isFirstSliceOfBlock) {
                paragraphSpacingPx.coerceAtLeast(0)
            } else {
                0
            }

            if (remainingHeight <= spacingBefore) {
                flushPage()
                continue
            }

            if (
                currentPage.isNotEmpty() &&
                isFirstSliceOfBlock &&
                blockHeight <= safeHeight &&
                blockHeight + spacingBefore > remainingHeight
            ) {
                flushPage()
                continue
            }

            val slice = resolveStaticLayoutSliceForHeight(
                text = text,
                layout = layout,
                startLine = startLine,
                availableHeight = remainingHeight - spacingBefore,
            )

            if (slice == null) {
                flushPage()
                continue
            }

            currentPage += PlainPageSlice(blockIndex = blockIndex, range = slice.range)
            remainingHeight -= spacingBefore + slice.heightPx
            startLine = slice.nextStartLine
            isFirstSliceOfBlock = false

            if (remainingHeight <= 0) {
                flushPage()
            }
        }
    }

    flushPage()
    return pages
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
    forcedParagraphFirstLineIndentEm: Float? = null,
): RichPageBlockText {
    val blockText: AnnotatedString = when (block) {
        is NovelRichContentBlock.Paragraph -> buildNovelRichAnnotatedString(block.segments)
        is NovelRichContentBlock.Heading -> buildNovelRichAnnotatedString(block.segments)
        is NovelRichContentBlock.BlockQuote -> buildNovelRichAnnotatedString(block.segments)
        NovelRichContentBlock.HorizontalRule -> AnnotatedString("* * *")
        is NovelRichContentBlock.Image -> AnnotatedString("")
    }
    if (blockText.text.isBlank()) {
        return RichPageBlockText(
            text = AnnotatedString(""),
            firstLineIndentEm = null,
            sourceTextAlign = null,
        )
    }
    if (block !is NovelRichContentBlock.Paragraph) {
        val sourceTextAlign = when (block) {
            is NovelRichContentBlock.Heading -> block.textAlign
            is NovelRichContentBlock.BlockQuote -> block.textAlign
            else -> null
        }
        return RichPageBlockText(
            text = blockText,
            firstLineIndentEm = null,
            sourceTextAlign = sourceTextAlign,
        )
    }

    val firstLineIndentEm = forcedParagraphFirstLineIndentEm ?: block.firstLineIndentEm
    return RichPageBlockText(
        text = blockText,
        firstLineIndentEm = firstLineIndentEm,
        sourceTextAlign = block.textAlign,
    )
}

internal fun paginateRichPageBlocks(
    blockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
    chapterTitle: String? = null,
): List<List<RichPageSlice>> {
    val safeHeight = heightPx.coerceAtLeast(1)
    val pages = mutableListOf<MutableList<RichPageSlice>>()
    var currentPage = mutableListOf<RichPageSlice>()
    var remainingHeight = safeHeight

    fun flushPage() {
        if (currentPage.isNotEmpty()) {
            pages += currentPage
            currentPage = mutableListOf()
        }
        remainingHeight = safeHeight
    }

    blockTexts.forEachIndexed { blockIndex, blockText ->
        if (blockText.text.text.isBlank()) return@forEachIndexed
        val isChapterTitle = blockIndex == 0 &&
            chapterTitle != null &&
            isNativeChapterTitleText(blockText.text.text, chapterTitle)
        val firstLineIndentEm = if (isChapterTitle) null else blockText.firstLineIndentEm
        val blockMetrics = resolvePageReaderBlockLayoutMetrics(
            isChapterTitle = isChapterTitle,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
        )
        val firstLineIndentPx = resolvePageReaderFirstLineIndentPx(
            firstLineIndentEm = firstLineIndentEm,
            textSizePx = blockMetrics.textSizePx,
        )
        val approximateMetrics = ApproximateTextMetrics(
            charsPerLine = ((widthPx - (firstLineIndentPx ?: 0)).coerceAtLeast(1) /
                (blockMetrics.textSizePx.coerceAtLeast(1f) * 0.55f))
                .toInt()
                .coerceAtLeast(15),
            lineHeightPx = (blockMetrics.textSizePx.coerceAtLeast(1f) *
                blockMetrics.lineHeightMultiplier.coerceAtLeast(1f))
                .toInt()
                .coerceAtLeast(1),
        )
        val layout = buildReaderStaticLayout(
            text = blockText.text.text,
            widthPx = widthPx,
            textSizePx = blockMetrics.textSizePx,
            lineHeightMultiplier = blockMetrics.lineHeightMultiplier,
            typeface = typeface,
            textAlign = textAlign,
            firstLineIndentPx = firstLineIndentPx,
        )
        if (layout == null) {
            val blockPages = paginateTextIntoPageRanges(
                text = blockText.text.text,
                widthPx = widthPx,
                heightPx = safeHeight,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = typeface,
                textAlign = textAlign,
                firstLineIndentPx = firstLineIndentPx,
            )
            val blockHeight = measureApproximateTextHeight(
                text = blockText.text.text,
                metrics = approximateMetrics,
            )
            blockPages.forEachIndexed { sliceIndex, range ->
                val spacingBefore = if (currentPage.isNotEmpty() && sliceIndex == 0) {
                    paragraphSpacingPx.coerceAtLeast(0)
                } else {
                    0
                }
                if (remainingHeight <= spacingBefore) {
                    flushPage()
                }
                if (
                    currentPage.isNotEmpty() &&
                    sliceIndex == 0 &&
                    blockHeight <= safeHeight &&
                    blockHeight + spacingBefore > remainingHeight
                ) {
                    flushPage()
                }
                val sliceHeight = measureApproximateTextHeight(
                    text = blockText.text.text.substring(range.start, range.endExclusive),
                    metrics = approximateMetrics,
                ).coerceAtMost(safeHeight)
                currentPage += RichPageSlice(blockIndex = blockIndex, range = range)
                remainingHeight -= spacingBefore + sliceHeight
                if (sliceIndex < blockPages.lastIndex || remainingHeight <= 0) {
                    flushPage()
                }
            }
            return@forEachIndexed
        }

        val blockHeight = resolveStaticLayoutHeight(layout)
        var startLine = 0
        var isFirstSliceOfBlock = true

        while (startLine < layout.lineCount) {
            val spacingBefore = if (currentPage.isNotEmpty() && isFirstSliceOfBlock) {
                paragraphSpacingPx.coerceAtLeast(0)
            } else {
                0
            }

            if (remainingHeight <= spacingBefore) {
                flushPage()
                continue
            }

            if (
                currentPage.isNotEmpty() &&
                isFirstSliceOfBlock &&
                blockHeight <= safeHeight &&
                blockHeight + spacingBefore > remainingHeight
            ) {
                flushPage()
                continue
            }

            val slice = resolveStaticLayoutSliceForHeight(
                text = blockText.text.text,
                layout = layout,
                startLine = startLine,
                availableHeight = remainingHeight - spacingBefore,
            )

            if (slice == null) {
                flushPage()
                continue
            }

            currentPage += RichPageSlice(blockIndex = blockIndex, range = slice.range)
            remainingHeight -= spacingBefore + slice.heightPx
            startLine = slice.nextStartLine
            isFirstSliceOfBlock = false

            if (remainingHeight <= 0) {
                flushPage()
            }
        }
    }

    flushPage()
    return pages
}

internal fun buildPlainPageRenderBlocks(
    page: List<PlainPageSlice>,
    textBlocks: List<String>,
    paragraphSpacingPx: Int,
    forceParagraphIndent: Boolean,
    chapterTitle: String? = null,
): List<PlainPageRenderBlock> {
    if (page.isEmpty()) return emptyList()
    return page.mapIndexed { index, slice ->
        val previousBlockIndex = page.getOrNull(index - 1)?.blockIndex
        val startsNewBlock = index > 0 && previousBlockIndex != slice.blockIndex
        val fullBlockText = textBlocks[slice.blockIndex]
        PlainPageRenderBlock(
            text = fullBlockText.substring(slice.range.start, slice.range.endExclusive),
            spacingBeforePx = if (startsNewBlock) paragraphSpacingPx.coerceAtLeast(0) else 0,
            firstLineIndentEm = if (
                forceParagraphIndent &&
                slice.range.start == 0 &&
                !(chapterTitle != null &&
                    slice.blockIndex == 0 &&
                    isNativeChapterTitleText(fullBlockText, chapterTitle))
            ) {
                FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
            } else {
                null
            },
            isChapterTitle = chapterTitle != null &&
                slice.blockIndex == 0 &&
                slice.range.start == 0 &&
                isNativeChapterTitleText(fullBlockText, chapterTitle),
        )
    }
}

internal fun buildRichPageRenderBlocks(
    page: List<RichPageSlice>,
    blockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    chapterTitle: String? = null,
): List<RichPageRenderBlock> {
    if (page.isEmpty()) return emptyList()
    return page.mapIndexed { index, slice ->
        val previousBlockIndex = page.getOrNull(index - 1)?.blockIndex
        val startsNewBlock = index > 0 && previousBlockIndex != slice.blockIndex
        val blockText = blockTexts[slice.blockIndex]
        val isChapterTitle = chapterTitle != null &&
            slice.blockIndex == 0 &&
            slice.range.start == 0 &&
            isNativeChapterTitleText(blockText.text.text, chapterTitle)
        RichPageRenderBlock(
            text = blockText.text.subSequence(TextRange(slice.range.start, slice.range.endExclusive)),
            spacingBeforePx = if (startsNewBlock) paragraphSpacingPx.coerceAtLeast(0) else 0,
            firstLineIndentEm = if (slice.range.start == 0 && !isChapterTitle) blockText.firstLineIndentEm else null,
            sourceTextAlign = blockText.sourceTextAlign,
            isChapterTitle = isChapterTitle,
        )
    }
}

internal fun normalizePageReaderContentPages(
    useRichPageReader: Boolean,
    plainPages: List<List<PlainPageSlice>>,
    richPages: List<List<RichPageSlice>>,
    plainTextBlocks: List<String>,
    richBlockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    forceParagraphIndent: Boolean,
    chapterTitle: String? = null,
): List<NovelPageContentPage> {
    return if (useRichPageReader) {
        richPages.map { page ->
            NovelPageContentPage(
                blocks = buildRichPageRenderBlocks(
                    page = page,
                    blockTexts = richBlockTexts,
                    paragraphSpacingPx = paragraphSpacingPx,
                    chapterTitle = chapterTitle,
                ).map { block ->
                    NovelPageContentBlock.Rich(
                        text = block.text,
                        spacingBeforePx = block.spacingBeforePx,
                        firstLineIndentEm = block.firstLineIndentEm,
                        sourceTextAlign = block.sourceTextAlign,
                        isChapterTitle = block.isChapterTitle,
                    )
                },
            )
        }
    } else {
        plainPages.map { page ->
            NovelPageContentPage(
                blocks = buildPlainPageRenderBlocks(
                    page = page,
                    textBlocks = plainTextBlocks,
                    paragraphSpacingPx = paragraphSpacingPx,
                    forceParagraphIndent = forceParagraphIndent,
                    chapterTitle = chapterTitle,
                ).map { block ->
                    NovelPageContentBlock.Plain(
                        text = block.text,
                        spacingBeforePx = block.spacingBeforePx,
                        firstLineIndentEm = block.firstLineIndentEm,
                        isChapterTitle = block.isChapterTitle,
                    )
                },
            )
        }
    }
}

internal fun resolvePageReaderParagraphSeparator(
    paragraphSpacingDp: Int,
): String {
    return if (paragraphSpacingDp.coerceIn(0, 32) >= 20) "\n\n" else "\n"
}
