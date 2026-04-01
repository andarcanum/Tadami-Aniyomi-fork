package eu.kanade.presentation.reader.novel

import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.icu.text.BreakIterator
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import kotlin.math.hypot
import kotlin.math.roundToInt
import java.util.Locale
import kotlinx.coroutines.delay
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

internal data class NovelPageReaderContentLayout(
    val textPadding: PaddingValues,
)

internal fun resolveNovelPageReaderContentLayout(
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    horizontalMargin: Int,
): NovelPageReaderContentLayout {
    return NovelPageReaderContentLayout(
        textPadding = PaddingValues(
            top = contentPadding + statusBarTopPadding,
            bottom = contentPadding,
            start = horizontalMargin.dp,
            end = horizontalMargin.dp,
        ),
    )
}

internal fun resolveNovelPageReaderBookBottomInset(
    density: Density,
    fontSize: Int,
    lineHeight: Float,
): Dp {
    return with(density) {
        val oneLineInset = fontSize.sp.toDp() * lineHeight.coerceAtLeast(1f)
        val conservativeInset = oneLineInset * 1.25f
        if (conservativeInset.value > 24.dp.value) conservativeInset else 24.dp
    }
}

internal data class NovelPageReaderSpanSpec(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeThrough: Boolean = false,
    val foregroundColor: Color? = null,
    val backgroundColor: Color? = null,
    val leadingMarginPx: Int? = null,
)

internal fun buildNovelPageReaderSpanSpecs(
    text: AnnotatedString,
    firstLineIndentPx: Int? = null,
): List<NovelPageReaderSpanSpec> {
    val specs = mutableListOf<NovelPageReaderSpanSpec>()
    text.spanStyles.forEach { range ->
        val start = range.start.coerceIn(0, text.length)
        val end = range.end.coerceIn(start, text.length)
        if (start >= end) return@forEach

        val style = range.item
        specs += NovelPageReaderSpanSpec(
            start = start,
            end = end,
            bold = style.fontWeight?.weight?.let { it >= FontWeight.SemiBold.weight } == true,
            italic = style.fontStyle == FontStyle.Italic,
            underline = style.textDecoration?.contains(
                androidx.compose.ui.text.style.TextDecoration.Underline,
            ) == true,
            strikeThrough = style.textDecoration?.contains(
                androidx.compose.ui.text.style.TextDecoration.LineThrough,
            ) == true,
            foregroundColor = style.color.takeIf { it != Color.Unspecified },
            backgroundColor = style.background.takeIf { it != Color.Unspecified },
        )
    }

    val indentPx = firstLineIndentPx?.takeIf { it > 0 }
    if (indentPx != null && text.isNotEmpty()) {
        specs += NovelPageReaderSpanSpec(
            start = 0,
            end = text.length,
            leadingMarginPx = indentPx,
        )
    }

    return specs
}

internal fun buildNovelPageReaderSpannableText(
    text: AnnotatedString,
    firstLineIndentPx: Int? = null,
    forcedTypefaceStyle: Int = Typeface.NORMAL,
    onUrlClick: ((String) -> Unit)? = null,
): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(text.text)
    buildNovelPageReaderSpanSpecs(
        text = text,
        firstLineIndentPx = firstLineIndentPx,
    ).forEach { spec ->
        if (spec.leadingMarginPx != null) {
            spannable.setSpan(
                LeadingMarginSpan.Standard(spec.leadingMarginPx, 0),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            return@forEach
        }

        val fontStyle = when {
            spec.bold && spec.italic -> Typeface.BOLD_ITALIC
            spec.bold -> Typeface.BOLD
            spec.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (fontStyle != Typeface.NORMAL) {
            spannable.setSpan(
                StyleSpan(fontStyle),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        if (spec.underline) {
            spannable.setSpan(
                UnderlineSpan(),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        if (spec.strikeThrough) {
            spannable.setSpan(
                StrikethroughSpan(),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        spec.foregroundColor?.let { color ->
            spannable.setSpan(
                ForegroundColorSpan(color.toArgb()),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        spec.backgroundColor?.let { color ->
            spannable.setSpan(
                BackgroundColorSpan(color.toArgb()),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    text.getStringAnnotations(
        tag = "URL",
        start = 0,
        end = text.length,
    ).forEach { annotation ->
        val start = annotation.start.coerceIn(0, text.length)
        val end = annotation.end.coerceIn(start, text.length)
        if (start >= end) return@forEach
        spannable.setSpan(
            NovelPageReaderUrlClickableSpan(
                url = annotation.item,
                onUrlClick = onUrlClick,
            ),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    if (forcedTypefaceStyle != Typeface.NORMAL && text.text.isNotEmpty()) {
        spannable.setSpan(
            StyleSpan(forcedTypefaceStyle),
            0,
            text.text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return spannable
}

private class NovelPageReaderUrlClickableSpan(
    private val url: String,
    private val onUrlClick: ((String) -> Unit)?,
) : ClickableSpan() {
    override fun onClick(widget: android.view.View) {
        onUrlClick?.invoke(url)
    }

    override fun updateDrawState(ds: TextPaint) {
        Unit
    }
}

private fun combineTypefaceStyles(
    first: Int,
    second: Int,
): Int {
    val bold = first == Typeface.BOLD ||
        first == Typeface.BOLD_ITALIC ||
        second == Typeface.BOLD ||
        second == Typeface.BOLD_ITALIC
    val italic = first == Typeface.ITALIC ||
        first == Typeface.BOLD_ITALIC ||
        second == Typeface.ITALIC ||
        second == Typeface.BOLD_ITALIC
    return when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
}

private fun resolveNovelPageReaderFirstLineIndentPx(
    firstLineIndentEm: Float?,
    textSizePx: Float,
): Int? {
    return firstLineIndentEm
        ?.takeIf { it > 0f }
        ?.let { (textSizePx.coerceAtLeast(1f) * it).roundToInt().coerceAtLeast(1) }
}

private fun resolveNovelPageReaderTextGravity(
    textAlign: ReaderTextAlign,
): Int {
    return when (textAlign) {
        ReaderTextAlign.CENTER -> Gravity.CENTER_HORIZONTAL
        ReaderTextAlign.RIGHT -> Gravity.END
        ReaderTextAlign.JUSTIFY,
        ReaderTextAlign.LEFT,
        ReaderTextAlign.SOURCE,
        -> Gravity.START
    }
}

private class NovelPageReaderTextView constructor(
    context: android.content.Context,
    private val selectionRenderer: NovelSelectedTextRenderer,
    private val selectionSessionIdProvider: () -> Long,
    private val onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit,
    private var onPlainTap: ((Float, Float) -> Unit)?,
    private val touchHandlingEnabled: Boolean,
) : TextView(context) {
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMillis = ViewConfiguration.getLongPressTimeout().toLong()
    private var gestureStartUptimeMillis = 0L
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var latestX = 0f
    private var latestY = 0f
    private var selectionPromotionScheduled = false
    private var selectionPromotedByLongPress = false
    private var selectionHighlightSpan: BackgroundColorSpan? = null
    private var selectionHighlightStart: Int = -1
    private var selectionHighlightEnd: Int = -1
    private val selectionPromotionRunnable = Runnable {
        selectionPromotionScheduled = false
        if (
            NovelReaderSelectionGestureArbiter.shouldPromoteSelectionCandidate(
                elapsedMillis = SystemClock.uptimeMillis() - gestureStartUptimeMillis,
                movedDistancePx = currentGestureDistancePx(),
                touchSlopPx = touchSlopPx,
                longPressTimeoutMillis = longPressTimeoutMillis,
            )
        ) {
            selectionPromotedByLongPress = promoteSelectionFromGesture()
            if (selectionPromotedByLongPress) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
    }

    init {
        setTextIsSelectable(touchHandlingEnabled)
        isClickable = false
        isLongClickable = touchHandlingEnabled
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        publishSelection(selStart, selEnd)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchHandlingEnabled) {
            return false
        }
        val handledBySuper = super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartUptimeMillis = event.eventTime
                gestureStartX = event.x
                gestureStartY = event.y
                latestX = event.x
                latestY = event.y
                selectionPromotedByLongPress = false
            }
            MotionEvent.ACTION_MOVE -> {
                latestX = event.x
                latestY = event.y
            }
            MotionEvent.ACTION_UP -> {
                latestX = event.x
                latestY = event.y
                val plainTapEligible = NovelReaderSelectionGestureArbiter.shouldHandlePlainTap(
                    elapsedMillis = event.eventTime - gestureStartUptimeMillis,
                    movedDistancePx = currentGestureDistancePx(),
                    touchSlopPx = touchSlopPx,
                    longPressTimeoutMillis = longPressTimeoutMillis,
                )
                val hasActiveSelection = hasActiveSelection()
                if (plainTapEligible && !hasActiveSelection) {
                    val clickableSpan = resolveClickableSpanAt(event)
                    if (clickableSpan != null) {
                        clickableSpan.onClick(this)
                        return true
                    }
                    onPlainTap?.invoke(
                        event.x,
                        width.toFloat(),
                    )
                    return true
                }
                return handledBySuper
            }
        }
        return handledBySuper
    }

    fun selectWordAt(x: Float, y: Float): Boolean {
        val spannable = text as? Spannable ?: return false
        val selectionRange = resolveWordSelectionRangeAt(x, y) ?: return false
        val selectionStart = selectionRange.first
        val selectionEnd = selectionRange.last + 1
        Selection.setSelection(spannable, selectionStart, selectionEnd)
        applySelectionHighlight(selectionStart, selectionEnd)
        publishSelection(selectionStart, selectionEnd)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        return true
    }

    private fun scheduleSelectionPromotion() {
        clearSelectionPromotion()
        selectionPromotionScheduled = true
        postDelayed(selectionPromotionRunnable, longPressTimeoutMillis)
    }

    private fun clearSelectionPromotion() {
        if (!selectionPromotionScheduled) return
        removeCallbacks(selectionPromotionRunnable)
        selectionPromotionScheduled = false
    }

    private fun publishSelection(selStart: Int, selEnd: Int) {
        val currentText = text?.toString().orEmpty()
        if (selStart < 0 || selEnd < 0 || selStart == selEnd || currentText.isBlank()) {
            onSelectedTextSelectionChanged(null)
            return
        }

        val layout = layout ?: run {
            onSelectedTextSelectionChanged(null)
            return
        }
        val start = minOf(selStart, selEnd).coerceIn(0, currentText.length)
        val end = maxOf(selStart, selEnd).coerceIn(start, currentText.length)
        if (start >= end) {
            onSelectedTextSelectionChanged(null)
            return
        }

        val selectionPath = Path()
        layout.getSelectionPath(start, end, selectionPath)
        val bounds = RectF()
        selectionPath.computeBounds(bounds, true)
        if (bounds.isEmpty) {
            onSelectedTextSelectionChanged(null)
            return
        }

        val selectedText = currentText.substring(start, end)
        if (selectedText.isBlank()) {
            onSelectedTextSelectionChanged(null)
            return
        }

        val locationOnScreen = IntArray(2)
        getLocationOnScreen(locationOnScreen)
        onSelectedTextSelectionChanged(
            NovelSelectedTextSelection(
                sessionId = selectionSessionIdProvider(),
                renderer = selectionRenderer,
                text = selectedText,
                anchor = NovelSelectedTextAnchor(
                    leftPx = (locationOnScreen[0] + bounds.left).roundToInt(),
                    topPx = (locationOnScreen[1] + bounds.top).roundToInt(),
                    rightPx = (locationOnScreen[0] + bounds.right).roundToInt(),
                    bottomPx = (locationOnScreen[1] + bounds.bottom).roundToInt(),
                ),
            ),
        )
    }

    private fun hasActiveSelection(): Boolean {
        val spannable = text as? Spannable ?: return false
        val start = Selection.getSelectionStart(spannable)
        val end = Selection.getSelectionEnd(spannable)
        return start >= 0 && end >= 0 && start != end
    }

    private fun applySelectionHighlight(selStart: Int, selEnd: Int) {
        clearSelectionHighlight()
        val spannable = text as? Spannable ?: return
        val color = android.graphics.Color.argb(60, 66, 133, 244)
        val highlightSpan = BackgroundColorSpan(color)
        spannable.setSpan(highlightSpan, selStart, selEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        selectionHighlightSpan = highlightSpan
        selectionHighlightStart = selStart
        selectionHighlightEnd = selEnd
    }

    private fun clearSelectionHighlight() {
        val spannable = text as? Spannable ?: return
        selectionHighlightSpan?.let {
            spannable.removeSpan(it)
        }
        selectionHighlightSpan = null
        selectionHighlightStart = -1
        selectionHighlightEnd = -1
    }

    private fun clearSelection() {
        val spannable = text as? Spannable ?: return
        Selection.removeSelection(spannable)
        onSelectedTextSelectionChanged(null)
    }

    private fun currentGestureDistancePx(): Float {
        return hypot((latestX - gestureStartX).toDouble(), (latestY - gestureStartY).toDouble()).toFloat()
    }

    private fun promoteSelectionFromGesture(): Boolean {
        val spannable = text as? Spannable ?: return false
        val selectionRange = resolveWordSelectionRangeAt(latestX, latestY) ?: return false
        val selectionStart = selectionRange.first
        val selectionEnd = selectionRange.last + 1
        Selection.setSelection(spannable, selectionStart, selectionEnd)
        applySelectionHighlight(selectionStart, selectionEnd)
        publishSelection(selectionStart, selectionEnd)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        return true
    }

    private fun resolveWordSelectionRangeAt(
        x: Float,
        y: Float,
    ): IntRange? {
        val currentText = text?.toString().orEmpty()
        if (currentText.isBlank()) return null
        val currentLayout = layout ?: return null
        val contentX = x - totalPaddingLeft + scrollX
        val contentY = y - totalPaddingTop + scrollY
        if (
            contentX < 0f ||
            contentY < 0f ||
            contentX > currentLayout.width ||
            contentY > currentLayout.height
        ) {
            return null
        }

        val line = currentLayout.getLineForVertical(contentY.roundToInt())
        val lineStart = currentLayout.getLineStart(line)
        val lineEnd = currentLayout.getLineEnd(line)
        if (lineStart >= lineEnd) return null

        val offset = currentLayout.getOffsetForHorizontal(line, contentX).coerceIn(lineStart, lineEnd)
            .coerceIn(0, currentText.length)
        if (offset >= currentText.length) return null

        val wordIterator = BreakIterator.getWordInstance(Locale.getDefault()).apply {
            setText(currentText)
        }
        val safeOffset = offset.coerceIn(0, currentText.length)
        val start = wordIterator.preceding(safeOffset)
        val end = wordIterator.following(safeOffset)
        if (start < 0 || end < 0 || start >= end) return null
        val selectionText = currentText.substring(start, end)
        if (selectionText.isBlank()) return null
        return start until end
    }

    fun updatePlainTapHandler(handler: ((Float, Float) -> Unit)?) {
        onPlainTap = handler
    }

    private fun resolveClickableSpanAt(event: MotionEvent): ClickableSpan? {
        val currentLayout = layout ?: return null
        val spannable = text as? Spanned ?: return null
        if (spannable.length == 0) return null
        val x = event.x - totalPaddingLeft + scrollX
        val y = event.y - totalPaddingTop + scrollY
        if (x < 0 || y < 0 || x > currentLayout.width || y > currentLayout.height) {
            return null
        }
        val line = currentLayout.getLineForVertical(y.roundToInt())
        val offset = currentLayout.getOffsetForHorizontal(line, x).coerceIn(0, spannable.length - 1)
        return spannable.getSpans(offset, offset + 1, ClickableSpan::class.java).lastOrNull()
    }

}

@Composable
internal fun NovelPageReaderPageContent(
    contentPage: NovelPageContentPage,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    pageSurfaceColor: Color? = null,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    chapterTitleTextColor: Color,
    textShadowEnabled: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    selectionRenderer: NovelSelectedTextRenderer = NovelSelectedTextRenderer.PAGE_READER,
    selectionSessionIdProvider: () -> Long = { 0L },
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onPlainTap: ((Float, Float) -> Unit)? = null,
    touchHandlingEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val contentLayout = resolveNovelPageReaderContentLayout(
        contentPadding = contentPadding,
        statusBarTopPadding = statusBarTopPadding,
        horizontalMargin = readerSettings.margin,
    )
    val bookBottomInset = resolveNovelPageReaderBookBottomInset(
        density = density,
        fontSize = readerSettings.fontSize,
        lineHeight = readerSettings.lineHeight,
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        if (pageSurfaceColor != Color.Transparent) {
            NovelPageSurfaceBackground(
                backgroundTexture = backgroundTexture,
                nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                surfaceColor = pageSurfaceColor,
            )
        }
        val imageBlock = contentPage.blocks.singleOrNull() as? NovelPageContentBlock.Image
        if (imageBlock != null) {
            NovelPageReaderImageBlock(
                imageUrl = imageBlock.imageUrl,
                contentDescription = imageBlock.contentDescription,
                contentLayout = contentLayout,
                bookBottomInset = bookBottomInset,
            )
            return@Box
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentLayout.textPadding)
                .padding(bottom = bookBottomInset),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                contentPage.blocks.forEach { block ->
                    if (block.spacingBeforePx > 0) {
                        Spacer(
                            modifier = Modifier.height(
                                with(density) { block.spacingBeforePx.toDp() },
                            ),
                        )
                    }
                    when (block) {
                        is NovelPageContentBlock.Plain -> {
                            NovelPageReaderTextBlock(
                                text = if (readerSettings.bionicReading) {
                                    toBionicText(block.text)
                                } else {
                                    AnnotatedString(block.text)
                                },
                                isChapterTitle = block.isChapterTitle,
                                firstLineIndentEm = block.firstLineIndentEm,
                                readerSettings = readerSettings,
                                textColor = textColor,
                                textBackground = textBackground,
                                textAlign = readerSettings.textAlign,
                                textTypeface = textTypeface,
                                chapterTitleTypeface = chapterTitleTypeface,
                                chapterTitleTextColor = chapterTitleTextColor,
                                textShadowEnabled = readerSettings.textShadow,
                                textShadowColor = readerSettings.textShadowColor,
                                textShadowBlur = readerSettings.textShadowBlur,
                                textShadowX = readerSettings.textShadowX,
                                textShadowY = readerSettings.textShadowY,
                                selectionRenderer = selectionRenderer,
                                selectionSessionIdProvider = selectionSessionIdProvider,
                                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                onPlainTap = onPlainTap,
                                touchHandlingEnabled = touchHandlingEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is NovelPageContentBlock.Rich -> {
                            NovelPageReaderTextBlock(
                                text = block.text,
                                isChapterTitle = block.isChapterTitle,
                                firstLineIndentEm = block.firstLineIndentEm,
                                readerSettings = readerSettings,
                                textColor = textColor,
                                textBackground = textBackground,
                                textAlign = readerSettings.textAlign,
                                textTypeface = textTypeface,
                                chapterTitleTypeface = chapterTitleTypeface,
                                chapterTitleTextColor = chapterTitleTextColor,
                                textShadowEnabled = readerSettings.textShadow,
                                textShadowColor = readerSettings.textShadowColor,
                                textShadowBlur = readerSettings.textShadowBlur,
                                textShadowX = readerSettings.textShadowX,
                                textShadowY = readerSettings.textShadowY,
                                selectionRenderer = selectionRenderer,
                                selectionSessionIdProvider = selectionSessionIdProvider,
                                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                onPlainTap = onPlainTap,
                                touchHandlingEnabled = touchHandlingEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is NovelPageContentBlock.Image -> {
                            NovelPageReaderImageBlock(
                                imageUrl = block.imageUrl,
                                contentDescription = block.contentDescription,
                                contentLayout = contentLayout,
                                bookBottomInset = bookBottomInset,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelPageReaderImageBlock(
    imageUrl: String,
    contentDescription: String?,
    contentLayout: NovelPageReaderContentLayout,
    bookBottomInset: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentLayout.textPadding)
            .padding(bottom = bookBottomInset),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = if (NovelPluginImage.isSupported(imageUrl)) {
                NovelPluginImage(imageUrl)
            } else {
                imageUrl
            },
            contentDescription = contentDescription,
            contentScale = ContentScale.Inside,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun NovelPageReaderTextBlock(
    text: AnnotatedString,
    isChapterTitle: Boolean,
    firstLineIndentEm: Float?,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    textAlign: ReaderTextAlign,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    chapterTitleTextColor: Color,
    textShadowEnabled: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    fontSizeMultiplier: Float = if (isChapterTitle) PAGE_READER_CHAPTER_TITLE_FONT_SIZE_MULTIPLIER else 1f,
    lineHeightMultiplier: Float = if (isChapterTitle) PAGE_READER_CHAPTER_TITLE_LINE_HEIGHT_MULTIPLIER else 1f,
    textColorOverride: Color? = null,
    baseTypefaceStyle: Int = Typeface.NORMAL,
    selectionRenderer: NovelSelectedTextRenderer? = null,
    selectionSessionIdProvider: () -> Long = { 0L },
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onPlainTap: ((Float, Float) -> Unit)? = null,
    touchHandlingEnabled: Boolean = true,
    onUrlClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val blockFontSizeMultiplier = fontSizeMultiplier
    val blockLineHeightMultiplier = lineHeightMultiplier
    val blockTextSizePx = with(density) {
        (readerSettings.fontSize * blockFontSizeMultiplier).sp.toPx()
    }
    val blockLineSpacingMultiplier = readerSettings.lineHeight * blockLineHeightMultiplier
    val blockFirstLineIndentPx = resolveNovelPageReaderFirstLineIndentPx(
        firstLineIndentEm = firstLineIndentEm,
        textSizePx = blockTextSizePx,
    )
    val blockTypeface = if (isChapterTitle) {
        chapterTitleTypeface ?: textTypeface
    } else {
        textTypeface
    }
    val blockTextColor = textColorOverride ?: if (isChapterTitle) chapterTitleTextColor else textColor
    val blockTextShadow = resolveReaderTextShadow(
        textShadowEnabled = textShadowEnabled,
        textShadowColor = textShadowColor,
        textShadowBlur = textShadowBlur,
        textShadowX = textShadowX,
        textShadowY = textShadowY,
        textColor = blockTextColor,
        backgroundColor = textBackground,
    )
    AndroidView(
        modifier = modifier,
        factory = { context ->
            NovelPageReaderTextView(
                context = context,
                selectionRenderer = selectionRenderer ?: NovelSelectedTextRenderer.PAGE_READER,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                onPlainTap = onPlainTap,
                touchHandlingEnabled = touchHandlingEnabled,
            ).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                setTextColor(blockTextColor.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_PX, blockTextSizePx)
                setLineSpacing(0f, blockLineSpacingMultiplier)
                typeface = blockTypeface
                gravity = resolveNovelPageReaderTextGravity(textAlign)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    justificationMode = if (textAlign == ReaderTextAlign.JUSTIFY) {
                        Layout.JUSTIFICATION_MODE_INTER_WORD
                    } else {
                        Layout.JUSTIFICATION_MODE_NONE
                    }
                }
            }
        },
        update = { textView ->
            textView.updatePlainTapHandler(onPlainTap)
            val renderedText = text.text
            if (textView.text?.toString() != renderedText) {
                textView.text = buildNovelPageReaderSpannableText(
                    text = text,
                    firstLineIndentPx = blockFirstLineIndentPx,
                    forcedTypefaceStyle = combineTypefaceStyles(
                        first = baseTypefaceStyle,
                        second = resolveForcedReaderTypefaceStyle(
                            forceBoldText = readerSettings.forceBoldText,
                            forceItalicText = readerSettings.forceItalicText,
                        ),
                    ),
                    onUrlClick = onUrlClick,
                )
            }
            textView.setTextColor(blockTextColor.toArgb())
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, blockTextSizePx)
            textView.setLineSpacing(0f, blockLineSpacingMultiplier)
            textView.typeface = blockTypeface
            textView.gravity = resolveNovelPageReaderTextGravity(textAlign)
            blockTextShadow?.let { shadow ->
                textView.setShadowLayer(
                    shadow.blurRadius,
                    shadow.offset.x,
                    shadow.offset.y,
                    shadow.color.toArgb(),
                )
            } ?: textView.setShadowLayer(0f, 0f, 0f, Color.Transparent.toArgb())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                textView.justificationMode = if (textAlign == ReaderTextAlign.JUSTIFY) {
                    Layout.JUSTIFICATION_MODE_INTER_WORD
                } else {
                    Layout.JUSTIFICATION_MODE_NONE
                }
            }
        },
    )
}
