package eu.kanade.presentation.reader.novel

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.tadami.aurora.BuildConfig
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectionAnchor
import eu.kanade.tachiyomi.ui.reader.novel.SelectedTextAction
import eu.kanade.tachiyomi.ui.reader.novel.resolveNovelHighlightsForRange
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.hypot
import kotlin.math.roundToInt
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

internal const val MENU_ID_DICTIONARY = 0x9001
internal const val MENU_ID_TRANSLATION = 0x9002
internal const val MENU_ID_COPY = 0x9003
internal const val MENU_ID_SELECT_ALL = 0x9004
internal const val MENU_ID_SHARE = 0x9005
internal const val MENU_ID_HIGHLIGHT = 0x9006

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

internal fun resolveNovelPageReaderPageFitSafetyInset(
    density: Density,
    fontSize: Int,
    lineHeight: Float,
): Dp {
    return with(density) {
        val scaledFontInset = fontSize.sp.toDp() * 0.5f
        val lineHeightInset = fontSize.sp.toDp() * (lineHeight.coerceAtLeast(1f) - 1f) * 1.0f
        val resolvedInset = maxOf(8.dp, scaledFontInset, lineHeightInset)
        resolvedInset
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

internal fun resolveNovelPageReaderRenderedTextSignature(
    text: AnnotatedString,
    firstLineIndentPx: Int?,
    forcedTypefaceStyle: Int,
    hyphenationEnabled: Boolean = false,
    availableWidthPx: Int? = null,
    textSizePx: Float = 0f,
    lineSpacingMultiplier: Float = 1f,
    textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    typefaceHash: Int = 0,
    textLocaleTag: String? = null,
): Int {
    var result = text.hashCode()
    result = 31 * result + (firstLineIndentPx ?: 0)
    result = 31 * result + forcedTypefaceStyle
    result = 31 * result + if (hyphenationEnabled) 1 else 0
    result = 31 * result + textAlign.ordinal
    if (hyphenationEnabled || textAlign == ReaderTextAlign.JUSTIFY) {
        result = 31 * result + (availableWidthPx ?: 0)
        result = 31 * result + textSizePx.toBits()
        result = 31 * result + lineSpacingMultiplier.toBits()
        result = 31 * result + typefaceHash
        result = 31 * result + (textLocaleTag?.hashCode() ?: 0)
    }
    return result
}

private class NovelPageReaderUrlClickableSpan(
    private val url: String,
    private val onUrlClick: ((String) -> Unit)?,
) : ClickableSpan() {
    override fun onClick(widget: android.view.View) {
        onUrlClick?.invoke(url)
    }

    override fun updateDrawState(ds: TextPaint) {
    }
}

internal data class NovelPageReaderTextViewMetrics(
    val textSizePx: Float,
    val lineSpacingMultiplier: Float,
    val typeface: Typeface?,
    val textAlign: ReaderTextAlign,
    val shadow: Shadow?,
    val isPageMode: Boolean = false,
    val hyphenationEnabled: Boolean = false,
    val textLocale: Locale? = null,
)

/**
 * How a native text block will be laid out. Kept explicit instead of inferred from the selection
 * renderer: selection support and layout metrics are independent and must not drift apart.
 */
internal enum class NovelPageReaderLayoutMode {
    PAGE,
    SCROLL,
}

internal fun resolveNovelPageReaderTextViewMetrics(
    textSizePx: Float,
    lineSpacingMultiplier: Float,
    typeface: Typeface?,
    textAlign: ReaderTextAlign,
    shadow: Shadow?,
    isPageMode: Boolean = false,
    hyphenationEnabled: Boolean = false,
    textLocale: Locale? = null,
): NovelPageReaderTextViewMetrics {
    return NovelPageReaderTextViewMetrics(
        textSizePx = textSizePx,
        lineSpacingMultiplier = lineSpacingMultiplier,
        typeface = typeface,
        textAlign = textAlign,
        shadow = shadow,
        isPageMode = isPageMode,
        hyphenationEnabled = hyphenationEnabled,
        textLocale = textLocale,
    )
}

internal fun applyNovelPageReaderTextViewMetrics(
    textView: TextView,
    metrics: NovelPageReaderTextViewMetrics,
) {
    if (textView is NovelPageReaderTextView && textView.appliedMetrics == metrics) {
        return
    }
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.textSizePx)
    textView.setLineSpacing(0f, metrics.lineSpacingMultiplier)
    textView.typeface = metrics.typeface
    if (metrics.textLocale != null) {
        textView.textLocale = metrics.textLocale
    }
    textView.gravity = resolveNovelPageReaderTextGravity(metrics.textAlign)
    val resolvedBreakStrategy = resolveNovelPageReaderBreakStrategy(
        isPageMode = metrics.isPageMode,
        hyphenationEnabled = metrics.hyphenationEnabled,
        textAlign = metrics.textAlign,
    )
    val resolvedHyphenationFrequency = resolveNovelPageReaderHyphenationFrequency(
        hyphenationEnabled = metrics.hyphenationEnabled,
    )
    if (metrics.isPageMode) {
        textView.breakStrategy = resolvedBreakStrategy
        textView.hyphenationFrequency = resolvedHyphenationFrequency
        // Page mode must match buildReaderStaticLayout: fallback spacing there is disabled since
        // API 28 (the TextView API's own introduction level), not only since Q, or the paginator
        // and the rendered TextView would measure different line heights on Android 9.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            textView.isFallbackLineSpacing = false
        }
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.breakStrategy = resolvedBreakStrategy
            textView.hyphenationFrequency = resolvedHyphenationFrequency
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            textView.isFallbackLineSpacing = true
        }
    }
    metrics.shadow?.let { resolvedShadow ->
        textView.setShadowLayer(
            resolvedShadow.blurRadius,
            resolvedShadow.offset.x,
            resolvedShadow.offset.y,
            resolvedShadow.color.toArgb(),
        )
    } ?: textView.setShadowLayer(0f, 0f, 0f, Color.Transparent.toArgb())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        textView.justificationMode = Layout.JUSTIFICATION_MODE_NONE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        textView.lineBreakStyle = LineBreakConfig.LINE_BREAK_STYLE_NONE
        textView.lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        textView.useBoundsForWidth = true
    }
    // TextView initializes its TextPaint without SUBPIXEL_TEXT_FLAG while
    // resolveReaderMeasurementTextPaint enables it; sync both paints so marginal lines and
    // justified spacing agree between measurement and TextView rendering.
    textView.paint.flags = textView.paint.flags or Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
    textView.paint.isAntiAlias = true
    textView.paint.isSubpixelText = true
    if (textView is NovelPageReaderTextView) {
        textView.appliedMetrics = metrics
    }
}

internal fun combineTypefaceStyles(
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

private const val NOVEL_PAGE_READER_TEXT_VIEW_LOG_TAG = "NovelPageReaderTextView"
private val loggedTextViewStates = WeakHashMap<View, String>()

/**
 * Debug-only render-state snapshot used to chase the "text is invisible until a long press"
 * symptom on device. Deliberately excludes the text itself and any selection substring: only a
 * content hash, span fingerprint, geometry, color, layout and attachment flags are logged.
 * Identical consecutive states of the same view are dropped so a draw loop cannot flood logcat.
 */
private fun logNovelPageReaderTextViewState(
    textView: NovelPageReaderTextView,
    event: String,
    canvas: Canvas? = null,
) {
    if (!BuildConfig.DEBUG) return
    val text = textView.text
    val spanned = text as? Spanned
    val spans: Array<Any> = if (spanned != null) {
        spanned.getSpans(0, spanned.length, Any::class.java)
    } else {
        emptyArray()
    }
    var spanFingerprint = spans.size
    if (spanned != null) {
        spans.forEach { span ->
            spanFingerprint = 31 * spanFingerprint + span.javaClass.name.hashCode()
            spanFingerprint = 31 * spanFingerprint + spanned.getSpanStart(span)
            spanFingerprint = 31 * spanFingerprint + spanned.getSpanEnd(span)
        }
    }
    val layout = textView.layout
    val state = buildString {
        append("event=").append(event)
        append(" view=").append(textView.javaClass.simpleName)
        append(" chapter=").append(textView.selectionAnchorChapterId)
        append(" block=").append(textView.selectionAnchorBlockIndex)
        append(" sliceStart=").append(textView.selectionAnchorTextStartInBlock)
        append(" len=").append(text?.length ?: 0)
        append(" textHash=").append(text?.hashCode() ?: 0)
        append(" spans=").append(spanFingerprint)
        append(" layout=").append(layout?.javaClass?.simpleName ?: "null")
            .append('(').append(layout?.width ?: -1).append('x').append(layout?.height ?: -1)
            .append(',').append(layout?.lineCount ?: -1).append(" lines)")
        append(" predicted=").append(textView.predictedContentHeightPx)
        append(" delta=").append((layout?.height ?: -1) - textView.predictedContentHeightPx)
        append(" size=").append(textView.width).append('x').append(textView.height)
        append(" pad=")
            .append(textView.totalPaddingLeft).append(',')
            .append(textView.totalPaddingTop).append(',')
            .append(textView.totalPaddingRight).append(',')
            .append(textView.totalPaddingBottom)
        append(" scroll=").append(textView.scrollX).append(',').append(textView.scrollY)
        append(" alpha=").append(textView.alpha)
        append(" visibility=").append(textView.visibility)
        append(" attached=").append(textView.isAttachedToWindow)
        append(" layoutRequested=").append(textView.isLayoutRequested)
        append(" focused=").append(textView.hasFocus())
        append(" selection=").append(textView.selectionStart).append(',').append(textView.selectionEnd)
        append(" color=").append(Integer.toHexString(textView.currentTextColor))
        append(" highlight=").append(Integer.toHexString(textView.highlightColor))
        append(" clip=").append(textView.clipBounds?.toShortString() ?: "null")
        append(" viewHardware=").append(textView.isHardwareAccelerated)
        append(" canvasHardware=").append(canvas?.isHardwareAccelerated ?: "n/a")
        append(" gravity=").append(textView.gravity)
        append(" bs=").append(textView.breakStrategy)
        append(" hf=").append(textView.hyphenationFrequency)
        append(" ta=").append(textView.textAlignment)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            append(" just=").append(textView.justificationMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            append(" lJust=").append(runCatching { layout?.justificationMode }.getOrDefault(-1) ?: -1)
        }
        append(" lAlign=").append(layout?.alignment)
        append(" parent=").append(textView.parent?.javaClass?.simpleName ?: "null")
    }
    if (loggedTextViewStates.put(textView, state) == state) return
    Log.d(NOVEL_PAGE_READER_TEXT_VIEW_LOG_TAG, state)
}

internal class NovelPageReaderTextView constructor(
    context: android.content.Context,
    private val selectionRenderer: NovelSelectedTextRenderer,
    private val selectionSessionIdProvider: () -> Long,
    private val onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit,
    private var onPlainTap: ((Float, Float, Float, Float) -> Unit)?,
    private var touchHandlingEnabled: Boolean,
    selectionInteractionEnabled: Boolean,
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
    private var selectionInteractionEnabled = selectionInteractionEnabled
    var renderedTextSignature: Int? = null
    var appliedMetrics: NovelPageReaderTextViewMetrics? = null
    var appliedTextColorArgb: Int? = null

    fun applyTextColorIfChanged(colorArgb: Int) {
        if (appliedTextColorArgb != colorArgb) {
            appliedTextColorArgb = colorArgb
            setTextColor(colorArgb)
        }
    }

    /**
     * Standalone StaticLayout height (px) the paginator predicted for this view's text, or 0 when
     * unknown (scroll mode, rich blocks). Debug diagnostics compare it with the produced layout
     * height to tell pagination/render divergence from a squeezed container.
     */
    var predictedContentHeightPx: Int = 0
    private val selectionPromotionRunnable = Runnable {
        selectionPromotionScheduled = false
        if (
            selectionInteractionEnabled &&
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

    private var localSelection: NovelSelectedTextSelection? = null
    internal fun currentSelectionForTesting(): NovelSelectedTextSelection? = localSelection
    private var selectionActionMode: ActionMode? = null
    private val selectionBoundsInView = RectF()
    private var isExecutingAction = false
    var isDictionaryEnabled = false
    var isTranslationEnabled = false

    /**
     * Persistent address of the text rendered by this view: the chapter id, the source block
     * index and the offset of this view's text inside that block (slices of one block carry a
     * non-zero start). When the chapter id is null the renderer cannot anchor selections.
     */
    var selectionAnchorChapterId: Long? = null
    var selectionAnchorBlockIndex: Int = 0
    var selectionAnchorTextStartInBlock: Int = 0
    var displayToSourceOffsets: IntArray? = null
    var syntheticHyphenDisplayOffsets: BooleanArray? = null
    var mergedSegments: List<MergedTextBlockSegment> = emptyList()
    private var isDetaching = false
    private var layoutProbeTextView: TextView? = null
    private var focusReleaseScheduled = false
    private val focusReleaseRunnable = Runnable {
        focusReleaseScheduled = false
        if (!isAttachedToWindow || isDetaching) return@Runnable
        if (!selectionInteractionEnabled || !hasActiveSelection()) {
            isFocusable = false
            isFocusableInTouchMode = false
            if (hasFocus() && !isInLayout) {
                super.clearFocus()
            }
        }
    }

    internal fun measureLayoutWithProbe(
        candidateText: CharSequence,
        widthPx: Int,
        metrics: NovelPageReaderTextViewMetrics,
        selectionEnabled: Boolean,
        bottomPadPx: Int,
    ): Layout? {
        val probe = layoutProbeTextView ?: TextView(context).also { created ->
            created.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            created.includeFontPadding = true
            layoutProbeTextView = created
        }
        if (probe.isTextSelectable != selectionEnabled) {
            probe.setTextIsSelectable(selectionEnabled)
        }
        probe.setPadding(0, 0, 0, bottomPadPx)
        applyNovelPageReaderTextViewMetrics(probe, metrics)
        probe.text = candidateText
        probe.measure(
            MeasureSpec.makeMeasureSpec(widthPx.coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        return probe.layout
    }

    internal fun clearLayoutProbe() {
        layoutProbeTextView?.text = ""
    }

    override fun onDetachedFromWindow() {
        isDetaching = true
        focusReleaseScheduled = false
        removeCallbacks(focusReleaseRunnable)
        finishSelectionActionMode()
        layoutProbeTextView = null
        super.onDetachedFromWindow()
        isDetaching = false
    }

    private fun isDetachingFromCompose(): Boolean {
        if (isDetaching) return true
        val p = parent as? View ?: return true
        return p.parent == null
    }

    override fun clearFocus() {
        if (isDetachingFromCompose()) {
            return
        }
        super.clearFocus()
    }

    override fun focusSearch(direction: Int): View? {
        // Returning a child while Compose is mid-layout re-enters requestLayout and used to
        // crash with "Cannot start a writer when another writer is pending".
        if (isDetachingFromCompose() || isLayoutRequested) {
            return null
        }
        return super.focusSearch(direction)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        logNovelPageReaderTextViewState(this, "draw", canvas)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        logNovelPageReaderTextViewState(this, "focus=$gainFocus")
        if (!gainFocus && !hasActiveSelection() && !hasFocus() && !isDetachingFromCompose() && !isInLayout) {
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        // A selectable TextView registers for pre-draw on every measure, and the framework then
        // calls bringPointIntoView(), which scrolls this view to its cursor. Inside a LazyColumn
        // item that scroll shifts the text inside the view bounds (clipped line starts and an
        // uneven top gap) even though the list is the only vertical scroller. This view is
        // wrap-content and must never scroll on its own; View.scrollBy() delegates here too.
        super.scrollTo(0, 0)
    }

    init {
        // Compose assigns LayoutParams only after construction, but setTextIsSelectable() reapplies
        // the text and TextView.checkForRelayout() then dereferences them: on Android 16 that NPE
        // kills the app before the reader renders its first block.
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        updateSelectionInteractionEnabled(selectionInteractionEnabled)
        isClickable = false
        setupCustomSelectionActionModeCallback()
        // Hand our menu to the editor: it builds its OWN floating action mode from this
        // callback, which keeps its two-sided drag handles alive. Starting a competing mode
        // manually (the previous approach) finished the editor's mode right after it appeared
        // and collapsed the selection to a single end-of-word handle without highlight.
        setCustomSelectionActionModeCallback(
            requireNotNull(customSelectionActionModeCallback),
        )
    }

    private fun setupCustomSelectionActionModeCallback() {
        customSelectionActionModeCallback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                if (menu == null) return true
                // Standard system-style actions first, then the reader-specific ones.
                menu.add(Menu.NONE, MENU_ID_COPY, 10, context.getString(MR.strings.copy.resourceId))
                menu.add(Menu.NONE, MENU_ID_SELECT_ALL, 20, context.getString(MR.strings.action_select_all.resourceId))
                menu.add(Menu.NONE, MENU_ID_SHARE, 30, context.getString(MR.strings.action_share.resourceId))
                val menuOrder = 100
                if (isDictionaryEnabled) {
                    menu.add(
                        Menu.NONE,
                        MENU_ID_DICTIONARY,
                        menuOrder,
                        context.getString(AYMR.strings.novel_reader_text_selection_action_dictionary.resourceId),
                    )
                }
                if (isTranslationEnabled) {
                    menu.add(
                        Menu.NONE,
                        MENU_ID_TRANSLATION,
                        menuOrder + 1,
                        context.getString(AYMR.strings.novel_reader_text_selection_action_translate.resourceId),
                    )
                }
                // The view either can anchor selections at all or not; the anchor itself is
                // computed from the live selection at click time (the system may open this menu
                // before our selection listener has published localSelection).
                if (selectionAnchorChapterId != null) {
                    menu.add(
                        Menu.NONE,
                        MENU_ID_HIGHLIGHT,
                        menuOrder + 2,
                        context.getString(AYMR.strings.novel_highlight_action_save.resourceId),
                    )
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (item == null) return false
                val selection = localSelection
                when (item.itemId) {
                    MENU_ID_COPY -> {
                        val selectedText = selection?.text ?: return false
                        copyToClipboard(selectedText)
                        mode?.finish()
                        return true
                    }
                    MENU_ID_SELECT_ALL -> {
                        selectAllText()
                        return true
                    }
                    MENU_ID_SHARE -> {
                        val selectedText = selection?.text ?: return false
                        shareSelectedText(selectedText)
                        mode?.finish()
                        return true
                    }
                    MENU_ID_DICTIONARY -> {
                        if (selection == null) return false
                        isExecutingAction = true
                        val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.DICTIONARY)
                        onSelectedTextSelectionChanged(selectionWithAction)
                        mode?.finish()
                        return true
                    }
                    MENU_ID_TRANSLATION -> {
                        if (selection == null) return false
                        isExecutingAction = true
                        val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.TRANSLATION)
                        onSelectedTextSelectionChanged(selectionWithAction)
                        mode?.finish()
                        return true
                    }
                    MENU_ID_HIGHLIGHT -> {
                        if (selection == null) return false
                        isExecutingAction = true
                        val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.HIGHLIGHT)
                        onSelectedTextSelectionChanged(selectionWithAction)
                        android.widget.Toast.makeText(
                            context,
                            context.getString(AYMR.strings.novel_highlight_saved.resourceId),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        mode?.finish()
                        return true
                    }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                selectionActionMode = null
                // Mirror the framework behavior: collapse the selection when the toolbar
                // closes (back / tap outside / after an action). publishSelection() skips the
                // null event while isExecutingAction is set, so dictionary/translation flows
                // are not cancelled.
                val spannable = text as? Spannable
                if (spannable != null) {
                    val selectionEnd = Selection.getSelectionEnd(spannable)
                    if (selectionEnd >= 0) {
                        Selection.setSelection(spannable, selectionEnd)
                    }
                }
                releaseSelectionFocusIfSafe()
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                if (view !== this@NovelPageReaderTextView || layout == null || selectionBoundsInView.isEmpty) {
                    super.onGetContentRect(mode, view, outRect)
                    return
                }
                outRect.set(
                    selectionBoundsInView.left.roundToInt(),
                    selectionBoundsInView.top.roundToInt(),
                    selectionBoundsInView.right.roundToInt(),
                    selectionBoundsInView.bottom.roundToInt(),
                )
            }
        }
    }

    fun updateTouchHandlingEnabled(enabled: Boolean) {
        touchHandlingEnabled = enabled
        if (!enabled) {
            updateSelectionInteractionEnabled(
                enabled = false,
                selectableLayoutEnabled = isTextSelectable,
            )
        }
    }

    private fun acquireSelectionFocus() {
        if (!selectionInteractionEnabled || isDetachingFromCompose()) return
        focusReleaseScheduled = false
        removeCallbacks(focusReleaseRunnable)
        if (!isFocusable) isFocusable = true
        if (!isFocusableInTouchMode) isFocusableInTouchMode = true
        if (!isFocused) {
            requestFocus()
        }
    }

    private fun scheduleFocusReleaseAfterApply() {
        if (focusReleaseScheduled || isDetachingFromCompose()) return
        focusReleaseScheduled = true
        post(focusReleaseRunnable)
    }

    private fun releaseSelectionFocusIfSafe() {
        if (isDetachingFromCompose()) return
        focusReleaseScheduled = false
        removeCallbacks(focusReleaseRunnable)
        if (!hasFocus()) {
            isFocusable = false
            isFocusableInTouchMode = false
        } else if (!isInLayout) {
            isFocusable = false
            isFocusableInTouchMode = false
        } else {
            scheduleFocusReleaseAfterApply()
        }
    }

    fun clearActiveSelectionAndFocus() {
        clearSelectionPromotion()
        selectionPromotedByLongPress = false
        clearSelection()
        finishSelectionActionMode()
        releaseSelectionFocusIfSafe()
    }

    fun updateSelectionInteractionEnabled(
        enabled: Boolean,
        selectableLayoutEnabled: Boolean = enabled,
    ) {
        selectionInteractionEnabled = enabled && touchHandlingEnabled
        // Never tear down TextView's selectable DynamicLayout mid-gesture when only
        // touchHandlingEnabled changes (swipe/curl); otherwise DynamicLayout <-> StaticLayout
        // switches shift line 2+ by a few pixels on touch and snap back on release.
        if (isTextSelectable != selectableLayoutEnabled) {
            setTextIsSelectable(selectableLayoutEnabled)
        }
        // Keep the interop view unfocusable during normal reading, taps, and paging so
        // HorizontalPager / LazyColumn eviction (Operation$RemoveNode -> removeViewInLayout)
        // never sees a focused View and never triggers rootViewRequestFocus() -> Compose
        // subcompose re-entry inside applyChanges. Focusability is enabled only while an active
        // text selection needs Editor drag handles.
        isClickable = false
        isLongClickable = selectionInteractionEnabled
        val keepActiveSelectionFocus = selectionInteractionEnabled && hasActiveSelection()
        if (!keepActiveSelectionFocus) {
            clearSelectionPromotion()
            selectionPromotedByLongPress = false
            clearSelection()
            finishSelectionActionMode()
            // Never strip focusability synchronously during AndroidView.update if this view
            // currently holds focus: setFocusable(false) calls clearFocus() ->
            // rootViewRequestFocus() inside Compose commit/layout passes.
            if (!hasFocus()) {
                focusReleaseScheduled = false
                removeCallbacks(focusReleaseRunnable)
                isFocusable = false
                isFocusableInTouchMode = false
            } else {
                scheduleFocusReleaseAfterApply()
            }
        } else {
            focusReleaseScheduled = false
            removeCallbacks(focusReleaseRunnable)
            isFocusable = true
            isFocusableInTouchMode = true
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        logNovelPageReaderTextViewState(this, "selection")
        if (!selectionInteractionEnabled) return
        super.onSelectionChanged(selStart, selEnd)
        publishSelection(selStart, selEnd)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchHandlingEnabled) {
            return false
        }
        var downOnLiveSelection = false
        var shouldSchedulePromotion = false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            logNovelPageReaderTextViewState(this, "touchDown")
            gestureStartUptimeMillis = event.eventTime
            gestureStartX = event.x
            gestureStartY = event.y
            latestX = event.x
            latestY = event.y
            // A down on the live selection (drag handles included) must fall through to the
            // TextView editor so the handles can extend the selection; wiping it here made
            // every long-press word selection impossible to grow into a phrase.
            downOnLiveSelection = selectionInteractionEnabled &&
                hasActiveSelection() &&
                isTouchNearSelectionBounds(event.x, event.y)
            if (downOnLiveSelection) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            selectionPromotedByLongPress = false
            if (selectionInteractionEnabled && !downOnLiveSelection) {
                clearSelection()
                finishSelectionActionMode()
                releaseSelectionFocusIfSafe()
                shouldSchedulePromotion = true
            }
        }
        val handledBySuper = super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (shouldSchedulePromotion) {
                    scheduleSelectionPromotion()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                latestX = event.x
                latestY = event.y
                if (selectionInteractionEnabled && currentGestureDistancePx() > touchSlopPx) {
                    clearSelectionPromotion()
                }
            }
            MotionEvent.ACTION_UP -> {
                latestX = event.x
                latestY = event.y
                if (selectionInteractionEnabled) {
                    clearSelectionPromotion()
                }
                val plainTapEligible = NovelReaderSelectionGestureArbiter.shouldHandlePlainTap(
                    elapsedMillis = event.eventTime - gestureStartUptimeMillis,
                    movedDistancePx = currentGestureDistancePx(),
                    touchSlopPx = touchSlopPx,
                    longPressTimeoutMillis = longPressTimeoutMillis,
                )
                val hasActiveSelection = hasActiveSelection()
                if (!hasActiveSelection) {
                    releaseSelectionFocusIfSafe()
                }
                if (plainTapEligible && !hasActiveSelection) {
                    val clickableSpan = resolveClickableSpanAt(event)
                    if (clickableSpan != null) {
                        clickableSpan.onClick(this)
                        return true
                    }
                    val windowOffset = IntArray(2)
                    getLocationInWindow(windowOffset)
                    onPlainTap?.invoke(
                        windowOffset[0] + event.x,
                        windowOffset[1] + event.y,
                        rootView.width.toFloat().coerceAtLeast(1f),
                        rootView.height.toFloat().coerceAtLeast(1f),
                    )
                    return true
                }
                return handledBySuper
            }
            MotionEvent.ACTION_CANCEL -> {
                if (selectionInteractionEnabled) {
                    clearSelection()
                    clearSelectionPromotion()
                    releaseSelectionFocusIfSafe()
                }
                selectionPromotedByLongPress = false
                return handledBySuper
            }
        }
        return handledBySuper
    }

    fun selectWordAt(x: Float, y: Float): Boolean {
        if (!selectionInteractionEnabled) return false
        val spannable = text as? Spannable ?: return false
        val selectionRange = resolveWordSelectionRangeAt(x, y) ?: return false
        val selectionStart = selectionRange.first
        val selectionEnd = selectionRange.last + 1
        Selection.setSelection(spannable, selectionStart, selectionEnd)
        publishSelection(selectionStart, selectionEnd)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        acquireSelectionFocus()
        return true
    }

    /**
     * Programmatic Selection.setSelection() does not make the framework start the
     * floating selection action mode on its own, and this view is intentionally
     * non-focusable, so the ActionMode has to be started explicitly to surface the
     * system copy/share/select-all menu plus the custom dictionary/translation items.
     */
    private fun startSelectionActionMode() {
        if (!isAttachedToWindow) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            selectionActionMode = startActionMode(customSelectionActionModeCallback, ActionMode.TYPE_FLOATING)
        } else {
            selectionActionMode = startActionMode(customSelectionActionModeCallback)
        }
    }

    private fun finishSelectionActionMode() {
        selectionActionMode?.finish()
        selectionActionMode = null
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(null, stripSoftHyphens(text)))
    }

    private fun shareSelectedText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, stripSoftHyphens(text))
        }
        val chooser = Intent.createChooser(shareIntent, null)
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun selectAllText() {
        val spannable = text as? Spannable ?: return
        if (spannable.length == 0) return
        Selection.setSelection(spannable, 0, spannable.length)
        // Re-anchor the floating toolbar to the full selection.
        selectionActionMode?.invalidateContentRect()
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
        selectionBoundsInView.setEmpty()
        val currentText = text?.toString().orEmpty()
        if (selStart < 0 || selEnd < 0 || selStart == selEnd || currentText.isBlank()) {
            localSelection = null
            publishSelectionCleared()
            isExecutingAction = false
            return
        }

        val layout = layout ?: run {
            localSelection = null
            publishSelectionCleared()
            isExecutingAction = false
            return
        }
        val start = minOf(selStart, selEnd).coerceIn(0, currentText.length)
        val end = maxOf(selStart, selEnd).coerceIn(start, currentText.length)
        if (start >= end) {
            localSelection = null
            publishSelectionCleared()
            isExecutingAction = false
            return
        }

        val selectionPath = Path()
        layout.getSelectionPath(start, end, selectionPath)
        val bounds = RectF()
        selectionPath.computeBounds(bounds, true)
        if (bounds.isEmpty) {
            localSelection = null
            publishSelectionCleared()
            isExecutingAction = false
            return
        }
        val maxSelectionRight = layout.width.toFloat().coerceAtLeast(1f)
        bounds.left = bounds.left.coerceIn(0f, maxSelectionRight)
        bounds.right = bounds.right.coerceIn(bounds.left, maxSelectionRight)
        selectionBoundsInView.set(bounds)

        val selectedText = extractReaderSourceTextForDisplayRange(
            displayText = currentText,
            start = start,
            endExclusive = end,
            syntheticHyphenDisplayOffsets = syntheticHyphenDisplayOffsets,
        )
        if (selectedText.isBlank()) {
            localSelection = null
            publishSelectionCleared()
            isExecutingAction = false
            return
        }

        val locationOnScreen = IntArray(2)
        getLocationOnScreen(locationOnScreen)
        val chapterId = selectionAnchorChapterId
        val overlappingAnchors = if (chapterId != null && mergedSegments.isNotEmpty()) {
            mergedSegments.mapNotNull { seg ->
                val segStart = maxOf(start, seg.displayStart)
                val segEnd = minOf(end, seg.displayEndExclusive)
                if (segStart >= segEnd) return@mapNotNull null
                val localStart = segStart - seg.displayStart
                val localEnd = segEnd - seg.displayStart
                val segLen = (seg.displayEndExclusive - seg.displayStart).coerceAtLeast(0)
                val mappedStart = mapDisplayOffsetToSourceOffset(localStart, seg.displayToSourceOffsets, segLen)
                val mappedEnd = mapDisplayOffsetToSourceOffset(localEnd, seg.displayToSourceOffsets, segLen)
                val snippet = extractReaderSourceTextForDisplayRange(
                    displayText = currentText,
                    start = segStart,
                    endExclusive = segEnd,
                    syntheticHyphenDisplayOffsets = syntheticHyphenDisplayOffsets,
                )
                NovelSelectionAnchor(
                    chapterId = chapterId,
                    blockIndex = seg.sourceBlockIndex,
                    charStart = seg.sourceTextStartInBlock + mappedStart,
                    charEndExclusive = seg.sourceTextStartInBlock + mappedEnd.coerceAtLeast(mappedStart),
                    selectedText = snippet.takeIf { it.isNotBlank() },
                )
            }
        } else if (chapterId != null) {
            val mappedStart = mapDisplayOffsetToSourceOffset(start, displayToSourceOffsets, currentText.length)
            val mappedEnd = mapDisplayOffsetToSourceOffset(end, displayToSourceOffsets, currentText.length)
            listOf(
                NovelSelectionAnchor(
                    chapterId = chapterId,
                    blockIndex = selectionAnchorBlockIndex,
                    charStart = selectionAnchorTextStartInBlock + mappedStart,
                    charEndExclusive = selectionAnchorTextStartInBlock + mappedEnd.coerceAtLeast(mappedStart),
                ),
            )
        } else {
            emptyList()
        }
        localSelection = NovelSelectedTextSelection(
            sessionId = selectionSessionIdProvider(),
            renderer = selectionRenderer,
            text = selectedText,
            anchor = NovelSelectedTextAnchor(
                leftPx = (locationOnScreen[0] + bounds.left).roundToInt(),
                topPx = (locationOnScreen[1] + bounds.top).roundToInt(),
                rightPx = (locationOnScreen[0] + bounds.right).roundToInt(),
                bottomPx = (locationOnScreen[1] + bounds.bottom).roundToInt(),
            ),
            selectionAnchor = overlappingAnchors.firstOrNull(),
            selectionAnchors = overlappingAnchors,
        )
        isExecutingAction = false
    }

    /**
     * Publishes "selection ended" to the shared state. TextView lifecycle (text reset on update,
     * detach, focus churn) fires [onSelectionChanged] outside composition — including during
     * layout and draw passes — and the screenModel write is collected on Main.immediate, so a
     * synchronous call re-enters composition mid-placement and crashes with "LayoutNode should
     * be attached to an owner" (measured on Android 16 when toggling fullscreen). Deferred to
     * the next main-loop turn; the main looper is used instead of this view's handler because a
     * detached view must still clear the shared selection state. The active-selection guard
     * keeps a deferred clear from wiping a selection made in the meantime.
     */
    private fun publishSelectionCleared() {
        if (isExecutingAction) return
        Handler(Looper.getMainLooper()).post {
            if (localSelection == null && !isExecutingAction) {
                onSelectedTextSelectionChanged(null)
            }
        }
    }

    private fun hasActiveSelection(): Boolean {
        if (!selectionInteractionEnabled) return false
        val spannable = text as? Spannable ?: return false
        val start = Selection.getSelectionStart(spannable)
        val end = Selection.getSelectionEnd(spannable)
        return start >= 0 && end >= 0 && start != end
    }

    /**
     * True when [x, y] lands on or near the painted selection bounds. Selection drag handles
     * render just outside the rect, so the finger gets extra slack around it.
     */
    private fun isTouchNearSelectionBounds(x: Float, y: Float): Boolean {
        if (selectionBoundsInView.isEmpty) return false
        val grabSlopPx = touchSlopPx * 4f
        return x >= selectionBoundsInView.left - grabSlopPx &&
            x <= selectionBoundsInView.right + grabSlopPx &&
            y >= selectionBoundsInView.top - grabSlopPx &&
            y <= selectionBoundsInView.bottom + grabSlopPx
    }

    private fun clearSelection() {
        selectionBoundsInView.setEmpty()
        val spannable = text as? Spannable ?: return
        val hadSelectionRange = Selection.getSelectionStart(spannable) != Selection.getSelectionEnd(spannable)
        val hadLocalSelection = localSelection != null
        if (!hadSelectionRange && !hadLocalSelection) return
        Selection.removeSelection(spannable)
        localSelection = null
        // Defer via publishSelectionCleared() so AndroidView.update never writes Compose state
        // synchronously mid-layout (Android 16 crash) and never kills an in-flight Dictionary /
        // Translate action that already collapsed the TextView selection.
        publishSelectionCleared()
    }

    private fun currentGestureDistancePx(): Float {
        return hypot((latestX - gestureStartX).toDouble(), (latestY - gestureStartY).toDouble()).toFloat()
    }

    private fun promoteSelectionFromGesture(): Boolean {
        if (!selectionInteractionEnabled) return false
        val spannable = text as? Spannable ?: return false
        val selectionRange = resolveWordSelectionRangeAt(latestX, latestY) ?: return false
        val selectionStart = selectionRange.first
        val selectionEnd = selectionRange.last + 1
        Selection.setSelection(spannable, selectionStart, selectionEnd)
        publishSelection(selectionStart, selectionEnd)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // The editor attaches its drag handles only once the view holds focus. Its own action
        // mode surfaces from the registered custom callback - starting ours here would kill it.
        acquireSelectionFocus()
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

        return resolveReaderWordDisplayRangeForOffset(
            displayText = currentText,
            displayOffset = offset,
            syntheticHyphenDisplayOffsets = syntheticHyphenDisplayOffsets,
        )
    }

    fun updatePlainTapHandler(handler: ((Float, Float, Float, Float) -> Unit)?) {
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

internal fun clearFocusedNovelReaderTextView(hostView: View?) {
    val focused = hostView?.findFocus() as? NovelPageReaderTextView ?: return
    focused.clearActiveSelectionAndFocus()
}

@Composable
internal fun NovelPageReaderPageContent(
    contentPage: NovelPageContentPage,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    pageSurfaceColor: Color? = Color.Transparent,
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
    ttsHighlightState: NovelReaderTtsHighlightState? = null,
    ttsHighlightColor: Color = Color.Transparent,
    selectionRenderer: NovelSelectedTextRenderer = NovelSelectedTextRenderer.PAGE_READER,
    selectionSessionIdProvider: () -> Long = { 0L },
    selectionAnchorChapterId: Long? = null,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onPlainTap: ((Float, Float, Float, Float) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null,
    touchHandlingEnabled: Boolean = true,
    renderAsStaticCanvas: Boolean = false,
    pageNumber: Int? = null,
    totalPages: Int = 0,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rootView = LocalView.current
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
    val preserveSourceAlign = readerSettings.preserveSourceTextAlignInNative
    var pageRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Handle short taps on the empty page area (below the text, in margins, around images)
    // so they reach the same onPlainTap dispatch as taps on text/image blocks. Child
    // handlers (TextView, image block) consume their own events, so this never double-fires.
    val pageTapModifier = if (touchHandlingEnabled) {
        Modifier
            .onGloballyPositioned { pageRootCoords = it }
            .pointerInput(onPlainTap, rootView) {
                detectTapGestures(
                    onTap = { offset ->
                        val coords = pageRootCoords
                        val rootOffset = if (coords != null && coords.isAttached) {
                            coords.localToRoot(offset)
                        } else {
                            offset
                        }
                        onPlainTap?.invoke(
                            rootOffset.x,
                            rootOffset.y,
                            rootView.width.toFloat().coerceAtLeast(size.width.toFloat()),
                            rootView.height.toFloat().coerceAtLeast(size.height.toFloat()),
                        )
                    },
                )
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier.fillMaxSize().then(pageTapModifier),
        contentAlignment = Alignment.TopStart,
    ) {
        if (pageSurfaceColor != null && pageSurfaceColor != Color.Transparent) {
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
                fullPage = true,
                onPlainTap = onPlainTap,
                onImageClick = onImageClick,
                onImageLongClick = onImageLongClick,
                touchHandlingEnabled = touchHandlingEnabled,
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentLayout.textPadding)
                    .padding(bottom = bookBottomInset),
            ) {
                val pageColumnWidthPx = constraints.maxWidth.coerceAtLeast(1)
                val renderItems = remember(contentPage.blocks) {
                    buildNovelPageRenderItems(contentPage.blocks)
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    renderItems.forEachIndexed { index, item ->
                        key(index, item) {
                            when (item) {
                                is NovelPageRenderItem.TextRun -> {
                                    val leadingSpacingPx = item.blocks.first().spacingBeforePx
                                    if (leadingSpacingPx > 0) {
                                        Spacer(
                                            modifier = Modifier.height(
                                                with(density) { leadingSpacingPx.toDp() },
                                            ),
                                        )
                                    }
                                    NovelPageReaderMergedTextBlock(
                                        blocks = item.blocks,
                                        pageIndex = contentPage.pageIndex,
                                        readerSettings = readerSettings,
                                        preserveSourceAlign = preserveSourceAlign,
                                        textColor = textColor,
                                        textBackground = textBackground,
                                        textTypeface = textTypeface,
                                        chapterTitleTypeface = chapterTitleTypeface,
                                        chapterTitleTextColor = chapterTitleTextColor,
                                        textShadowEnabled = textShadowEnabled,
                                        textShadowColor = textShadowColor,
                                        textShadowBlur = textShadowBlur,
                                        textShadowX = textShadowX,
                                        textShadowY = textShadowY,
                                        availableWidthPx = pageColumnWidthPx,
                                        ttsHighlightState = ttsHighlightState,
                                        ttsHighlightColor = ttsHighlightColor,
                                        selectionRenderer = selectionRenderer,
                                        selectionSessionIdProvider = selectionSessionIdProvider,
                                        selectionAnchorChapterId = selectionAnchorChapterId,
                                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                        onPlainTap = onPlainTap,
                                        onUrlClick = onUrlClick,
                                        touchHandlingEnabled = touchHandlingEnabled,
                                        renderAsStaticCanvas = renderAsStaticCanvas,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                is NovelPageRenderItem.HorizontalRule -> {
                                    if (item.spacingBeforePx > 0) {
                                        Spacer(
                                            modifier = Modifier.height(
                                                with(density) { item.spacingBeforePx.toDp() },
                                            ),
                                        )
                                    }
                                    val ruleContainerHeight = item.measuredHeightPx
                                        .takeIf { it > 0 }
                                        ?.let { px -> with(density) { px.toDp() } }
                                        ?: 20.dp
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ruleContainerHeight),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.42f)
                                                .height(1.dp)
                                                .background(textColor.copy(alpha = 0.25f)),
                                        )
                                    }
                                }
                                is NovelPageRenderItem.Image -> {
                                    val block = item.block
                                    if (block.spacingBeforePx > 0) {
                                        Spacer(
                                            modifier = Modifier.height(
                                                with(density) { block.spacingBeforePx.toDp() },
                                            ),
                                        )
                                    }
                                    NovelPageReaderImageBlock(
                                        imageUrl = block.imageUrl,
                                        contentDescription = block.contentDescription,
                                        contentLayout = contentLayout,
                                        bookBottomInset = bookBottomInset,
                                        fullPage = false,
                                        maxHeightPx = block.maxHeightPx,
                                        onPlainTap = onPlainTap,
                                        onImageClick = onImageClick,
                                        onImageLongClick = onImageLongClick,
                                        touchHandlingEnabled = touchHandlingEnabled,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (
            readerSettings.showPageFooter &&
            pageNumber != null &&
            totalPages > 0 &&
            contentPage.blocks.isNotEmpty()
        ) {
            NovelPageReaderFooter(
                pageNumber = pageNumber,
                totalPages = totalPages,
                textColor = textColor,
                horizontalPadding = readerSettings.margin.dp,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun NovelPageReaderFooter(
    pageNumber: Int,
    totalPages: Int,
    textColor: Color,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val safeTotal = totalPages.coerceAtLeast(1)
    val safePage = pageNumber.coerceIn(1, safeTotal)
    val percent = ((safePage.toFloat() / safeTotal.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$safePage / $safeTotal · $percent%",
            color = textColor.copy(alpha = 0.56f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun NovelPageReaderImageBlock(
    imageUrl: String,
    contentDescription: String?,
    contentLayout: NovelPageReaderContentLayout,
    bookBottomInset: Dp,
    fullPage: Boolean,
    maxHeightPx: Int? = null,
    onPlainTap: ((Float, Float, Float, Float) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    touchHandlingEnabled: Boolean = true,
) {
    val density = LocalDensity.current
    val referer = LocalNovelReaderReferer.current
    val imageModel = if (NovelPluginImage.isSupported(imageUrl)) {
        NovelPluginImage(imageUrl)
    } else if (referer != null) {
        NovelReaderRefererImage(
            url = imageUrl,
            referer = referer,
        )
    } else {
        imageUrl
    }

    val gestureModifier = if (touchHandlingEnabled) {
        Modifier.pointerInput(imageUrl, onPlainTap, onImageClick, onImageLongClick) {
            detectTapGestures(
                onTap = { offset ->
                    if (onImageClick != null) {
                        onImageClick(imageUrl)
                    } else {
                        onPlainTap?.invoke(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                    }
                },
                onLongPress = {
                    onImageLongClick?.invoke(imageUrl)
                },
            )
        }
    } else {
        Modifier
    }

    if (fullPage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentLayout.textPadding)
                .padding(bottom = bookBottomInset)
                .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                contentScale = ContentScale.Inside,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        val defaultMaxHeight = 420.dp
        val constrainedMaxHeight = maxHeightPx
            ?.takeIf { it > 0 }
            ?.let { px -> with(density) { px.toDp() }.coerceAtMost(defaultMaxHeight) }
            ?: defaultMaxHeight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = constrainedMaxHeight),
            )
        }
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
    predictedContentHeightPx: Int = 0,
    availableWidthPx: Int? = null,
    layoutMode: NovelPageReaderLayoutMode,
    selectionRenderer: NovelSelectedTextRenderer? = null,
    selectionSessionIdProvider: () -> Long = { 0L },
    selectionAnchorChapterId: Long? = null,
    selectionAnchorBlockIndex: Int = 0,
    selectionAnchorTextStartInBlock: Int = 0,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onPlainTap: ((Float, Float, Float, Float) -> Unit)? = null,
    touchHandlingEnabled: Boolean = true,
    renderAsStaticCanvas: Boolean = false,
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

    // Paint saved highlights over the rendered slice before anything consumes the text.
    val text = paintNovelSliceHighlights(
        text = text,
        chapterId = selectionAnchorChapterId,
        blockIndex = selectionAnchorBlockIndex,
        sliceStart = selectionAnchorTextStartInBlock,
        sliceEndExclusive = selectionAnchorTextStartInBlock + text.length,
    )
    val blockTextShadow = resolveReaderTextShadow(
        textShadowEnabled = textShadowEnabled,
        textShadowColor = textShadowColor,
        textShadowBlur = textShadowBlur,
        textShadowX = textShadowX,
        textShadowY = textShadowY,
        textColor = blockTextColor,
        backgroundColor = textBackground,
    )
    val isPageMode = layoutMode == NovelPageReaderLayoutMode.PAGE
    val hyphenationEnabled = readerSettings.hyphenationEnabled
    val blockTextLocale = if (hyphenationEnabled) resolveNovelReaderTextLocale(text.text) else null
    val blockTextViewMetrics = resolveNovelPageReaderTextViewMetrics(
        textSizePx = blockTextSizePx,
        lineSpacingMultiplier = blockLineSpacingMultiplier,
        typeface = blockTypeface,
        textAlign = textAlign,
        shadow = blockTextShadow,
        isPageMode = isPageMode,
        hyphenationEnabled = hyphenationEnabled,
        textLocale = blockTextLocale,
    )
    val selectionInteractionEnabled =
        touchHandlingEnabled &&
            (
                readerSettings.textSelectionEnabled ||
                    readerSettings.selectedTextTranslationEnabled ||
                    readerSettings.novelDictionaryEnabled
                )
    val forcedTypefaceStyle = combineTypefaceStyles(
        first = baseTypefaceStyle,
        second = resolveForcedReaderTypefaceStyle(
            forceBoldText = readerSettings.forceBoldText,
            forceItalicText = readerSettings.forceItalicText,
        ),
    )
    if (renderAsStaticCanvas && availableWidthPx != null && availableWidthPx > 0) {
        val safeWidthPx = availableWidthPx.coerceAtLeast(1)
        val glyphPadBottom = resolvePageReaderGlyphOverflowPaddingPx(blockTextSizePx)
        val staticLayout = remember(
            text,
            blockFirstLineIndentPx,
            forcedTypefaceStyle,
            hyphenationEnabled,
            safeWidthPx,
            blockTextSizePx,
            blockLineSpacingMultiplier,
            textAlign,
            blockTypeface,
            blockTextLocale,
            isPageMode,
            density.density,
            blockTextColor,
            blockTextShadow,
        ) {
            val baseSpannable = buildNovelPageReaderSpannableText(
                text = text,
                firstLineIndentPx = blockFirstLineIndentPx,
                forcedTypefaceStyle = forcedTypefaceStyle,
                onUrlClick = null,
            )
            val paint = resolveReaderMeasurementTextPaint(
                textSizePx = blockTextSizePx,
                typeface = blockTypeface,
                density = density.density,
                basePaint = null,
                textLocale = blockTextLocale,
            ).apply {
                color = blockTextColor.toArgb()
                if (blockTextShadow != null) {
                    setShadowLayer(
                        blockTextShadow.blurRadius,
                        blockTextShadow.offset.x,
                        blockTextShadow.offset.y,
                        blockTextShadow.color.toArgb(),
                    )
                } else {
                    clearShadowLayer()
                }
            }
            if (hyphenationEnabled || textAlign == ReaderTextAlign.JUSTIFY) {
                resolveWidthHyphenatedReaderTextAndLayout(
                    source = baseSpannable,
                    widthPx = safeWidthPx,
                    textSizePx = blockTextSizePx,
                    lineHeightMultiplier = blockLineSpacingMultiplier,
                    typeface = blockTypeface,
                    textAlign = textAlign,
                    firstLineIndentPx = null,
                    density = density.density,
                    measurementPaint = paint,
                    preferDynamicLayout = false,
                    hyphenationEnabled = hyphenationEnabled,
                    textLocale = blockTextLocale,
                    isPageMode = isPageMode,
                    applyJustifySpacing = textAlign == ReaderTextAlign.JUSTIFY,
                ).layout
            } else {
                buildReaderStaticLayout(
                    text = baseSpannable,
                    widthPx = safeWidthPx,
                    textSizePx = blockTextSizePx,
                    lineHeightMultiplier = blockLineSpacingMultiplier,
                    typeface = blockTypeface,
                    textAlign = textAlign,
                    firstLineIndentPx = null,
                    density = density.density,
                    measurementPaint = paint,
                    hyphenationEnabled = false,
                    textLocale = blockTextLocale,
                )
            }
        }
        val canvasHeightDp = with(density) { (staticLayout.height + glyphPadBottom).coerceAtLeast(1).toDp() }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(canvasHeightDp)
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        staticLayout.draw(canvas.nativeCanvas)
                    }
                },
        )
        return
    }
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
                selectionInteractionEnabled = selectionInteractionEnabled,
            ).apply {
                val glyphPadBottom = resolvePageReaderGlyphOverflowPaddingPx(blockTextSizePx)
                setPadding(0, 0, 0, glyphPadBottom)
                // Keep bottom font padding enabled so descenders (y, g, p) at small font sizes
                // have full fontMetrics.bottom clearance and are not clipped by TextView.onDraw.
                includeFontPadding = true
                applyTextColorIfChanged(blockTextColor.toArgb())
                applyNovelPageReaderTextViewMetrics(textView = this, metrics = blockTextViewMetrics)
            }
        },
        update = { textView ->
            textView.isDictionaryEnabled = readerSettings.novelDictionaryEnabled
            textView.isTranslationEnabled = readerSettings.selectedTextTranslationEnabled
            textView.selectionAnchorChapterId = selectionAnchorChapterId
            textView.selectionAnchorBlockIndex = selectionAnchorBlockIndex
            textView.selectionAnchorTextStartInBlock = selectionAnchorTextStartInBlock
            textView.predictedContentHeightPx = predictedContentHeightPx
            textView.updatePlainTapHandler(onPlainTap)
            textView.updateTouchHandlingEnabled(touchHandlingEnabled)
            val selectionConfigured = readerSettings.textSelectionEnabled ||
                readerSettings.selectedTextTranslationEnabled ||
                readerSettings.novelDictionaryEnabled
            textView.updateSelectionInteractionEnabled(
                enabled = selectionInteractionEnabled,
                selectableLayoutEnabled = selectionConfigured,
            )
            val glyphPadBottom = resolvePageReaderGlyphOverflowPaddingPx(blockTextSizePx)
            if (textView.paddingBottom != glyphPadBottom) {
                textView.setPadding(textView.paddingLeft, textView.paddingTop, textView.paddingRight, glyphPadBottom)
            }
            applyNovelPageReaderTextViewMetrics(textView = textView, metrics = blockTextViewMetrics)
            val renderedTextSignature = resolveNovelPageReaderRenderedTextSignature(
                text = text,
                firstLineIndentPx = blockFirstLineIndentPx,
                forcedTypefaceStyle = forcedTypefaceStyle,
                hyphenationEnabled = hyphenationEnabled,
                availableWidthPx = availableWidthPx,
                textSizePx = blockTextSizePx,
                lineSpacingMultiplier = blockLineSpacingMultiplier,
                textAlign = textAlign,
                typefaceHash = blockTypeface?.hashCode() ?: 0,
                textLocaleTag = blockTextLocale?.toLanguageTag(),
            )
            if (textView.renderedTextSignature != renderedTextSignature) {
                val baseSpannable = buildNovelPageReaderSpannableText(
                    text = text,
                    firstLineIndentPx = blockFirstLineIndentPx,
                    forcedTypefaceStyle = forcedTypefaceStyle,
                    onUrlClick = onUrlClick,
                )
                val resolvedAvailableWidthPx = availableWidthPx
                    ?.takeIf { it > 0 }
                    ?.let { width ->
                        (width - textView.compoundPaddingLeft - textView.compoundPaddingRight).coerceAtLeast(1)
                    }
                if ((hyphenationEnabled || textAlign == ReaderTextAlign.JUSTIFY) && resolvedAvailableWidthPx != null) {
                    fun solveWidthHyphenated(
                        layoutOverride: ((CharSequence) -> Layout?)? = null,
                    ) = resolveWidthHyphenatedReaderTextAndLayout(
                        source = baseSpannable,
                        widthPx = resolvedAvailableWidthPx,
                        textSizePx = blockTextSizePx,
                        lineHeightMultiplier = blockLineSpacingMultiplier,
                        typeface = blockTypeface,
                        textAlign = textAlign,
                        firstLineIndentPx = null,
                        density = density.density,
                        measurementPaint = textView.paint,
                        preferDynamicLayout = selectionConfigured,
                        hyphenationEnabled = hyphenationEnabled,
                        textLocale = blockTextLocale,
                        isPageMode = isPageMode,
                        applyJustifySpacing = textAlign == ReaderTextAlign.JUSTIFY,
                        layoutBuilderOverride = layoutOverride,
                    )
                    val initialHyphenated = solveWidthHyphenated()
                    val hyphenated = try {
                        val probeLayout = textView.measureLayoutWithProbe(
                            candidateText = initialHyphenated.prepared.text,
                            widthPx = resolvedAvailableWidthPx,
                            metrics = blockTextViewMetrics,
                            selectionEnabled = selectionConfigured,
                            bottomPadPx = glyphPadBottom,
                        )
                        val needsTextViewReconcile = probeLayout != null && (
                            !doLayoutsMatchLineStarts(initialHyphenated.layout, probeLayout) ||
                                hasMidLineSyntheticHyphen(probeLayout, initialHyphenated.prepared)
                            )
                        if (needsTextViewReconcile) {
                            solveWidthHyphenated { candidate ->
                                textView.measureLayoutWithProbe(
                                    candidateText = candidate,
                                    widthPx = resolvedAvailableWidthPx,
                                    metrics = blockTextViewMetrics,
                                    selectionEnabled = selectionConfigured,
                                    bottomPadPx = glyphPadBottom,
                                )
                            }
                        } else {
                            initialHyphenated
                        }
                    } finally {
                        textView.clearLayoutProbe()
                    }
                    textView.displayToSourceOffsets = hyphenated.prepared.displayToSourceOffsets
                    textView.syntheticHyphenDisplayOffsets = hyphenated.prepared.syntheticHyphenDisplayOffsets
                    textView.text = hyphenated.prepared.text
                } else {
                    textView.displayToSourceOffsets = null
                    textView.syntheticHyphenDisplayOffsets = null
                    textView.text = if (hyphenationEnabled) {
                        normalizeCurlyApostrophesForDisplay(baseSpannable)
                    } else {
                        baseSpannable
                    }
                }
                textView.renderedTextSignature = renderedTextSignature
            }
            textView.applyTextColorIfChanged(blockTextColor.toArgb())
        },
        // Dropping a focused interop view makes the framework search the hierarchy for a new focus
        // target, which can re-enter Compose layout while changes are still being applied. Giving
        // up focus first keeps that removal side-effect free.
        onRelease = { textView ->
            textView.clearFocus()
        },
    )
}

internal data class MergedTextBlockSegment(
    val displayStart: Int,
    val displayEndExclusive: Int,
    val sourceBlockIndex: Int,
    val sourceTextStartInBlock: Int,
    val displayToSourceOffsets: IntArray?,
)

internal sealed interface NovelPageRenderItem {
    data class TextRun(
        val blocks: List<NovelPageContentBlock>,
    ) : NovelPageRenderItem

    data class HorizontalRule(
        val spacingBeforePx: Int,
        val measuredHeightPx: Int,
    ) : NovelPageRenderItem

    data class Image(
        val block: NovelPageContentBlock.Image,
    ) : NovelPageRenderItem
}

internal fun buildNovelPageRenderItems(blocks: List<NovelPageContentBlock>): List<NovelPageRenderItem> {
    if (blocks.isEmpty()) return emptyList()
    val items = mutableListOf<NovelPageRenderItem>()
    val pendingTextBlocks = mutableListOf<NovelPageContentBlock>()

    fun flushTextRun() {
        if (pendingTextBlocks.isNotEmpty()) {
            items += NovelPageRenderItem.TextRun(pendingTextBlocks.toList())
            pendingTextBlocks.clear()
        }
    }

    for (block in blocks) {
        when (block) {
            is NovelPageContentBlock.Plain -> pendingTextBlocks += block
            is NovelPageContentBlock.Rich -> {
                if (block.isHorizontalRule) {
                    flushTextRun()
                    items += NovelPageRenderItem.HorizontalRule(
                        spacingBeforePx = block.spacingBeforePx,
                        measuredHeightPx = block.measuredHeightPx,
                    )
                } else {
                    pendingTextBlocks += block
                }
            }
            is NovelPageContentBlock.Image -> {
                flushTextRun()
                items += NovelPageRenderItem.Image(block)
            }
        }
    }
    flushTextRun()
    return items
}

private class ParagraphSpacerLineHeightSpan(
    private val targetHeightPx: Int,
) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence?,
        start: Int,
        end: Int,
        spanstartv: Int,
        v: Int,
        fm: Paint.FontMetricsInt?,
    ) {
        if (fm == null) return
        fm.top = -targetHeightPx
        fm.ascent = -targetHeightPx
        fm.descent = 0
        fm.bottom = 0
    }
}

private class NovelBlockQuoteSpan(
    private val stripeWidthPx: Float,
    private val stripeOffsetPx: Float,
    private val stripeColor: Int,
    private val leadMarginPx: Int,
) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = leadMarginPx

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence?,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) {
        val oldStyle = p.style
        val oldColor = p.color
        p.style = Paint.Style.FILL
        p.color = stripeColor
        val left = (x + dir * stripeOffsetPx).coerceAtLeast(0f)
        c.drawRect(left, top.toFloat(), left + stripeWidthPx, bottom.toFloat(), p)
        p.style = oldStyle
        p.color = oldColor
    }
}

private class NovelTypefaceSpan(
    private val customTypeface: Typeface,
) : MetricAffectingSpan() {
    override fun updateDrawState(ds: TextPaint) {
        ds.typeface = customTypeface
    }

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = customTypeface
    }
}

internal data class PreparedMergedBlockInput(
    val sourceBlockIndex: Int,
    val sourceTextStartInBlock: Int,
    val highlightedText: AnnotatedString,
    val spacingBeforePx: Int,
    val firstLineIndentPx: Int?,
    val forcedTypefaceStyle: Int,
    val textAlign: ReaderTextAlign,
    val fontSizeMultiplier: Float,
    val lineHeightMultiplier: Float,
    val typeface: Typeface?,
    val textColor: Color,
    val isBlockQuote: Boolean,
    val startIndentPx: Int,
    val textLocale: Locale?,
)

internal class MergedPageTextContent(
    val spannable: SpannableStringBuilder,
    val segments: List<MergedTextBlockSegment>,
    val syntheticHyphenDisplayOffsets: BooleanArray?,
    val predictedTotalHeightPx: Int,
)

internal fun buildMergedPageTextContent(
    inputs: List<PreparedMergedBlockInput>,
    pageColumnWidthPx: Int,
    bodyTextSizePx: Float,
    bodyLineSpacingMultiplier: Float,
    bodyTypeface: Typeface?,
    quoteStripeWidthPx: Float,
    quoteStripeOffsetPx: Float,
    quoteStripeColor: Int,
    density: Float,
    hyphenationEnabled: Boolean,
    onUrlClick: ((String) -> Unit)?,
    measurementPaint: TextPaint?,
    probeBuilder: ((CharSequence, PreparedMergedBlockInput) -> Layout?)? = null,
): MergedPageTextContent {
    val mergedBuilder = SpannableStringBuilder()
    val segments = mutableListOf<MergedTextBlockSegment>()
    val blockSyntheticFlags = arrayOfNulls<BooleanArray>(inputs.size)
    var predictedTotalHeightPx = 0

    for (index in inputs.indices) {
        val input = inputs[index]
        if (index > 0) {
            val prev = inputs[index - 1]
            val prevBlockSizePx = bodyTextSizePx * prev.fontSizeMultiplier
            val prevBlockLineMult = bodyLineSpacingMultiplier * prev.lineHeightMultiplier
            val prevGlyphPadPx = resolvePageReaderGlyphOverflowPaddingPx(prevBlockSizePx)
            val prevLeadingGapPx = (prevBlockSizePx * (prevBlockLineMult - 1f)).roundToInt().coerceAtLeast(0)
            val desiredTotalGapPx = prevGlyphPadPx + input.spacingBeforePx
            val extraSpacerPx = (desiredTotalGapPx - prevLeadingGapPx).coerceAtLeast(0)
            mergedBuilder.append('\n')
            if (extraSpacerPx > 0) {
                val spacerStart = mergedBuilder.length
                mergedBuilder.append('\n')
                val adjustedHeightPx = (extraSpacerPx / bodyLineSpacingMultiplier.coerceAtLeast(1f))
                    .roundToInt()
                    .coerceAtLeast(1)
                mergedBuilder.setSpan(
                    ParagraphSpacerLineHeightSpan(adjustedHeightPx),
                    spacerStart,
                    spacerStart + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            predictedTotalHeightPx += desiredTotalGapPx
        }

        val chunkBase = buildNovelPageReaderSpannableText(
            text = input.highlightedText,
            firstLineIndentPx = if (input.isBlockQuote) null else input.firstLineIndentPx,
            forcedTypefaceStyle = input.forcedTypefaceStyle,
            onUrlClick = onUrlClick,
        )
        val chunkSpannable = SpannableStringBuilder(chunkBase)
        val rawLen = chunkSpannable.length
        if (rawLen > 0) {
            if (input.isBlockQuote && input.startIndentPx > 0) {
                chunkSpannable.setSpan(
                    NovelBlockQuoteSpan(
                        stripeWidthPx = quoteStripeWidthPx,
                        stripeOffsetPx = quoteStripeOffsetPx,
                        stripeColor = quoteStripeColor,
                        leadMarginPx = input.startIndentPx,
                    ),
                    0,
                    rawLen,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (input.fontSizeMultiplier != 1f) {
                chunkSpannable.setSpan(
                    RelativeSizeSpan(input.fontSizeMultiplier),
                    0,
                    rawLen,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (input.typeface != null && input.typeface != bodyTypeface) {
                chunkSpannable.setSpan(
                    NovelTypefaceSpan(input.typeface),
                    0,
                    rawLen,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            chunkSpannable.setSpan(
                ForegroundColorSpan(input.textColor.toArgb()),
                0,
                rawLen,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (input.textAlign != ReaderTextAlign.LEFT && input.textAlign != ReaderTextAlign.JUSTIFY) {
                chunkSpannable.setSpan(
                    AlignmentSpan.Standard(input.textAlign.toLayoutAlignment()),
                    0,
                    rawLen,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

        val blockSizePx = bodyTextSizePx * input.fontSizeMultiplier
        val blockLineMult = bodyLineSpacingMultiplier * input.lineHeightMultiplier
        val resolvedWidthPx = pageColumnWidthPx.coerceAtLeast(1)
        var segmentDisplayToSource: IntArray? = null
        val displayChunk: CharSequence = if (
            (hyphenationEnabled || input.textAlign == ReaderTextAlign.JUSTIFY) &&
            rawLen > 0
        ) {
            fun solveChunk(layoutOverride: ((CharSequence) -> Layout?)? = null) =
                resolveWidthHyphenatedReaderTextAndLayout(
                    source = chunkSpannable,
                    widthPx = resolvedWidthPx,
                    textSizePx = bodyTextSizePx,
                    lineHeightMultiplier = bodyLineSpacingMultiplier,
                    typeface = bodyTypeface,
                    textAlign = input.textAlign,
                    firstLineIndentPx = null,
                    density = density,
                    measurementPaint = measurementPaint,
                    preferDynamicLayout = probeBuilder != null,
                    hyphenationEnabled = hyphenationEnabled,
                    textLocale = input.textLocale,
                    isPageMode = true,
                    applyJustifySpacing = input.textAlign == ReaderTextAlign.JUSTIFY,
                    layoutBuilderOverride = layoutOverride,
                )

            val initialHyphenated = solveChunk()
            val finalHyphenated = if (probeBuilder != null) {
                val probeLayout = probeBuilder(initialHyphenated.prepared.text, input)
                val needsReconcile = probeLayout != null && (
                    !doLayoutsMatchLineStarts(initialHyphenated.layout, probeLayout) ||
                        hasMidLineSyntheticHyphen(probeLayout, initialHyphenated.prepared)
                    )
                if (needsReconcile) {
                    solveChunk { candidate -> probeBuilder(candidate, input) }
                } else {
                    initialHyphenated
                }
            } else {
                initialHyphenated
            }
            segmentDisplayToSource = finalHyphenated.prepared.displayToSourceOffsets
            blockSyntheticFlags[index] = finalHyphenated.prepared.syntheticHyphenDisplayOffsets
            finalHyphenated.prepared.text
        } else if (hyphenationEnabled) {
            normalizeCurlyApostrophesForDisplay(chunkSpannable)
        } else {
            chunkSpannable
        }

        val displayStart = mergedBuilder.length
        mergedBuilder.append(displayChunk)
        val displayEnd = mergedBuilder.length
        segments += MergedTextBlockSegment(
            displayStart = displayStart,
            displayEndExclusive = displayEnd,
            sourceBlockIndex = input.sourceBlockIndex,
            sourceTextStartInBlock = input.sourceTextStartInBlock,
            displayToSourceOffsets = segmentDisplayToSource,
        )
        predictedTotalHeightPx += (blockSizePx * blockLineMult).roundToInt()
    }

    val totalLength = mergedBuilder.length
    var mergedSynthetic: BooleanArray? = null
    for (index in segments.indices) {
        val segFlags = blockSyntheticFlags[index] ?: continue
        val seg = segments[index]
        val arr = mergedSynthetic ?: BooleanArray(totalLength).also { mergedSynthetic = it }
        val copyLen = minOf(segFlags.size, seg.displayEndExclusive - seg.displayStart)
        for (i in 0 until copyLen) {
            if (segFlags[i]) {
                arr[seg.displayStart + i] = true
            }
        }
    }

    return MergedPageTextContent(
        spannable = mergedBuilder,
        segments = segments,
        syntheticHyphenDisplayOffsets = mergedSynthetic,
        predictedTotalHeightPx = predictedTotalHeightPx,
    )
}

@Composable
private fun NovelPageReaderMergedTextBlock(
    blocks: List<NovelPageContentBlock>,
    pageIndex: Int,
    readerSettings: NovelReaderSettings,
    preserveSourceAlign: Boolean,
    textColor: Color,
    textBackground: Color,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    chapterTitleTextColor: Color,
    textShadowEnabled: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    availableWidthPx: Int,
    ttsHighlightState: NovelReaderTtsHighlightState?,
    ttsHighlightColor: Color,
    selectionRenderer: NovelSelectedTextRenderer,
    selectionSessionIdProvider: () -> Long,
    selectionAnchorChapterId: Long?,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit,
    onPlainTap: ((Float, Float, Float, Float) -> Unit)?,
    onUrlClick: ((String) -> Unit)?,
    touchHandlingEnabled: Boolean,
    renderAsStaticCanvas: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bodyTextSizePx = with(density) { readerSettings.fontSize.sp.toPx() }
    val bodyLineSpacingMultiplier = readerSettings.lineHeight
    val hyphenationEnabled = readerSettings.hyphenationEnabled
    val forcedSettingsStyle = resolveForcedReaderTypefaceStyle(
        forceBoldText = readerSettings.forceBoldText,
        forceItalicText = readerSettings.forceItalicText,
    )
    val baseBodyTextAlign = resolveNativeReaderTextAlign(
        globalTextAlign = readerSettings.textAlign,
        preserveSourceTextAlignInNative = preserveSourceAlign,
        sourceTextAlign = null,
    )
    val quoteStripeWidthPx = with(density) { 2.dp.toPx() }
    val quoteStripeOffsetPx = with(density) { 3.dp.toPx() }
    val quoteStripeColor = textColor.copy(alpha = 0.28f).toArgb()
    val chapterHighlights = LocalNovelChapterHighlights.current
    val preparedInputs = remember(
        blocks,
        pageIndex,
        readerSettings.bionicReading,
        readerSettings.textAlign,
        preserveSourceAlign,
        bodyTextSizePx,
        forcedSettingsStyle,
        textTypeface,
        chapterTitleTypeface,
        textColor,
        chapterTitleTextColor,
        hyphenationEnabled,
        ttsHighlightState,
        ttsHighlightColor,
        selectionAnchorChapterId,
        chapterHighlights,
        density.density,
    ) {
        blocks.mapNotNull { block ->
            when (block) {
                is NovelPageContentBlock.Plain -> {
                    val baseText = if (readerSettings.bionicReading) {
                        toBionicText(
                            block.text,
                        )
                    } else {
                        AnnotatedString(block.text)
                    }
                    val ttsText = applyNovelReaderTtsHighlight(
                        text = baseText,
                        blockText = block.text,
                        sourceBlockIndex = block.sourceBlockIndex,
                        pageIndex = pageIndex,
                        pageBlockTextStart = block.sourceTextStart,
                        pageBlockTextEndExclusive = block.sourceTextEndExclusive,
                        highlightState = ttsHighlightState,
                        highlightColor = ttsHighlightColor,
                    )
                    val sliceStart = block.sourceTextStart ?: 0
                    val highlightedText = if (selectionAnchorChapterId == null || chapterHighlights.isEmpty()) {
                        ttsText
                    } else {
                        applyNovelHighlights(
                            text = ttsText,
                            blockIndex = block.sourceBlockIndex,
                            resolved = resolveNovelHighlightsForRange(
                                highlights = chapterHighlights,
                                chapterId = selectionAnchorChapterId,
                                blockIndex = block.sourceBlockIndex,
                                rangeStart = sliceStart,
                                rangeEndExclusive = sliceStart + ttsText.length,
                                rangeText = ttsText.text,
                            ),
                        )
                    }
                    val fontSizeMultiplier = if (block.isChapterTitle) {
                        PAGE_READER_CHAPTER_TITLE_FONT_SIZE_MULTIPLIER
                    } else {
                        1f
                    }
                    val lineHeightMultiplier = if (block.isChapterTitle) {
                        PAGE_READER_CHAPTER_TITLE_LINE_HEIGHT_MULTIPLIER
                    } else {
                        1f
                    }
                    val blockSizePx = bodyTextSizePx * fontSizeMultiplier
                    val blockTypeface = if (block.isChapterTitle) chapterTitleTypeface ?: textTypeface else textTypeface
                    PreparedMergedBlockInput(
                        sourceBlockIndex = block.sourceBlockIndex,
                        sourceTextStartInBlock = sliceStart,
                        highlightedText = highlightedText,
                        spacingBeforePx = block.spacingBeforePx,
                        firstLineIndentPx = resolveNovelPageReaderFirstLineIndentPx(
                            block.firstLineIndentEm,
                            blockSizePx,
                        ),
                        forcedTypefaceStyle = combineTypefaceStyles(
                            first = if (block.isChapterTitle) Typeface.BOLD else Typeface.NORMAL,
                            second = forcedSettingsStyle,
                        ),
                        textAlign = baseBodyTextAlign,
                        fontSizeMultiplier = fontSizeMultiplier,
                        lineHeightMultiplier = lineHeightMultiplier,
                        typeface = blockTypeface,
                        textColor = if (block.isChapterTitle) chapterTitleTextColor else textColor,
                        isBlockQuote = false,
                        startIndentPx = 0,
                        textLocale = if (hyphenationEnabled) {
                            resolveNovelReaderTextLocale(
                                highlightedText.text,
                            )
                        } else {
                            null
                        },
                    )
                }
                is NovelPageContentBlock.Rich -> {
                    val ttsText = applyNovelReaderTtsHighlight(
                        text = block.text,
                        blockText = block.text.text,
                        sourceBlockIndex = block.sourceBlockIndex,
                        pageIndex = pageIndex,
                        pageBlockTextStart = block.sourceTextStart,
                        pageBlockTextEndExclusive = block.sourceTextEndExclusive,
                        highlightState = ttsHighlightState,
                        highlightColor = ttsHighlightColor,
                    )
                    val sliceStart = block.sourceTextStart ?: 0
                    val highlightedText = if (selectionAnchorChapterId == null || chapterHighlights.isEmpty()) {
                        ttsText
                    } else {
                        applyNovelHighlights(
                            text = ttsText,
                            blockIndex = block.sourceBlockIndex,
                            resolved = resolveNovelHighlightsForRange(
                                highlights = chapterHighlights,
                                chapterId = selectionAnchorChapterId,
                                blockIndex = block.sourceBlockIndex,
                                rangeStart = sliceStart,
                                rangeEndExclusive = sliceStart + ttsText.length,
                                rangeText = ttsText.text,
                            ),
                        )
                    }
                    val fontSizeMultiplier = if (block.isChapterTitle) {
                        PAGE_READER_CHAPTER_TITLE_FONT_SIZE_MULTIPLIER
                    } else {
                        block.fontSizeMultiplier
                    }
                    val lineHeightMultiplier = if (block.isChapterTitle) {
                        PAGE_READER_CHAPTER_TITLE_LINE_HEIGHT_MULTIPLIER
                    } else {
                        block.lineHeightMultiplier
                    }
                    val blockSizePx = bodyTextSizePx * fontSizeMultiplier
                    val blockTypeface = if (block.isChapterTitle) chapterTitleTypeface ?: textTypeface else textTypeface
                    val isQuote = block.isBlockQuote && !block.isChapterTitle
                    val startIndentPx = if (isQuote && block.startIndentDp > 0) {
                        with(density) { block.startIndentDp.dp.roundToPx() }
                    } else {
                        0
                    }
                    val blockColor = when {
                        isQuote -> textColor.copy(alpha = 0.90f)
                        block.isChapterTitle -> chapterTitleTextColor
                        else -> textColor
                    }
                    PreparedMergedBlockInput(
                        sourceBlockIndex = block.sourceBlockIndex,
                        sourceTextStartInBlock = sliceStart,
                        highlightedText = highlightedText,
                        spacingBeforePx = block.spacingBeforePx,
                        firstLineIndentPx = resolveNovelPageReaderFirstLineIndentPx(
                            block.firstLineIndentEm,
                            blockSizePx,
                        ),
                        forcedTypefaceStyle = combineTypefaceStyles(
                            first = if (block.isChapterTitle) Typeface.BOLD else block.baseTypefaceStyle,
                            second = forcedSettingsStyle,
                        ),
                        textAlign = resolveNativeReaderTextAlign(
                            globalTextAlign = readerSettings.textAlign,
                            preserveSourceTextAlignInNative = preserveSourceAlign,
                            sourceTextAlign = block.sourceTextAlign,
                        ),
                        fontSizeMultiplier = fontSizeMultiplier,
                        lineHeightMultiplier = lineHeightMultiplier,
                        typeface = blockTypeface,
                        textColor = blockColor,
                        isBlockQuote = isQuote,
                        startIndentPx = startIndentPx,
                        textLocale = if (hyphenationEnabled) {
                            resolveNovelReaderTextLocale(
                                highlightedText.text,
                            )
                        } else {
                            null
                        },
                    )
                }
                is NovelPageContentBlock.Image -> null
            }
        }
    }
    if (preparedInputs.isEmpty()) return

    val lastInput = preparedInputs.last()
    val glyphPadBottom = resolvePageReaderGlyphOverflowPaddingPx(bodyTextSizePx * lastInput.fontSizeMultiplier)
    val bodyTextShadow = resolveReaderTextShadow(
        textShadowEnabled = textShadowEnabled,
        textShadowColor = textShadowColor,
        textShadowBlur = textShadowBlur,
        textShadowX = textShadowX,
        textShadowY = textShadowY,
        textColor = textColor,
        backgroundColor = textBackground,
    )
    val bodyLocale = preparedInputs.firstNotNullOfOrNull { it.textLocale }
    val bodyMetrics = resolveNovelPageReaderTextViewMetrics(
        textSizePx = bodyTextSizePx,
        lineSpacingMultiplier = bodyLineSpacingMultiplier,
        typeface = textTypeface,
        textAlign = ReaderTextAlign.LEFT,
        shadow = bodyTextShadow,
        isPageMode = true,
        hyphenationEnabled = hyphenationEnabled,
        textLocale = bodyLocale,
    )
    val selectionConfigured = readerSettings.textSelectionEnabled ||
        readerSettings.selectedTextTranslationEnabled ||
        readerSettings.novelDictionaryEnabled
    val selectionInteractionEnabled = touchHandlingEnabled && selectionConfigured

    if (renderAsStaticCanvas && availableWidthPx > 0) {
        val safeWidthPx = availableWidthPx.coerceAtLeast(1)
        val staticLayout = remember(
            preparedInputs,
            safeWidthPx,
            bodyTextSizePx,
            bodyLineSpacingMultiplier,
            textTypeface,
            textColor,
            bodyTextShadow,
            quoteStripeColor,
            hyphenationEnabled,
            selectionConfigured,
            density.density,
        ) {
            val paint = resolveReaderMeasurementTextPaint(
                textSizePx = bodyTextSizePx,
                typeface = textTypeface,
                density = density.density,
                basePaint = null,
                textLocale = bodyLocale,
            ).apply {
                color = textColor.toArgb()
                if (bodyTextShadow != null) {
                    setShadowLayer(
                        bodyTextShadow.blurRadius,
                        bodyTextShadow.offset.x,
                        bodyTextShadow.offset.y,
                        bodyTextShadow.color.toArgb(),
                    )
                } else {
                    clearShadowLayer()
                }
            }
            val mergedContent = buildMergedPageTextContent(
                inputs = preparedInputs,
                pageColumnWidthPx = safeWidthPx,
                bodyTextSizePx = bodyTextSizePx,
                bodyLineSpacingMultiplier = bodyLineSpacingMultiplier,
                bodyTypeface = textTypeface,
                quoteStripeWidthPx = quoteStripeWidthPx,
                quoteStripeOffsetPx = quoteStripeOffsetPx,
                quoteStripeColor = quoteStripeColor,
                density = density.density,
                hyphenationEnabled = hyphenationEnabled,
                onUrlClick = null,
                measurementPaint = paint,
            )
            (
                if (selectionConfigured) {
                    buildReaderDynamicLayout(
                        text = mergedContent.spannable,
                        widthPx = safeWidthPx,
                        textSizePx = bodyTextSizePx,
                        lineHeightMultiplier = bodyLineSpacingMultiplier,
                        typeface = textTypeface,
                        textAlign = ReaderTextAlign.LEFT,
                        firstLineIndentPx = null,
                        density = density.density,
                        measurementPaint = paint,
                        hyphenationEnabled = false,
                        textLocale = bodyLocale,
                    )
                } else {
                    null
                }
                ) ?: buildReaderStaticLayout(
                text = mergedContent.spannable,
                widthPx = safeWidthPx,
                textSizePx = bodyTextSizePx,
                lineHeightMultiplier = bodyLineSpacingMultiplier,
                typeface = textTypeface,
                textAlign = ReaderTextAlign.LEFT,
                firstLineIndentPx = null,
                density = density.density,
                measurementPaint = paint,
                hyphenationEnabled = false,
                textLocale = bodyLocale,
            )
        }
        val canvasHeightDp = with(density) { (staticLayout.height + glyphPadBottom).coerceAtLeast(1).toDp() }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(canvasHeightDp)
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        staticLayout.draw(canvas.nativeCanvas)
                    }
                },
        )
        return
    }

    val signatureSeed = run {
        var seed = availableWidthPx
        seed = 31 * seed + bodyTextSizePx.toBits()
        seed = 31 * seed + bodyLineSpacingMultiplier.toBits()
        seed = 31 * seed + (textTypeface?.hashCode() ?: 0)
        seed = 31 * seed + quoteStripeColor
        seed = 31 * seed + if (hyphenationEnabled) 1 else 0
        31 * seed + if (onUrlClick != null) 1 else 0
    }
    val mergedSignature = preparedInputs.fold(signatureSeed) { acc, input ->
        var h = 31 * acc + input.sourceBlockIndex
        h = 31 * h + input.sourceTextStartInBlock
        h = 31 * h + input.highlightedText.hashCode()
        h = 31 * h + input.spacingBeforePx
        h = 31 * h + (input.firstLineIndentPx ?: 0)
        h = 31 * h + input.forcedTypefaceStyle
        h = 31 * h + input.textAlign.ordinal
        h = 31 * h + input.fontSizeMultiplier.toBits()
        h = 31 * h + input.lineHeightMultiplier.toBits()
        h = 31 * h + (input.typeface?.hashCode() ?: 0)
        h = 31 * h + input.textColor.toArgb()
        h = 31 * h + input.startIndentPx
        31 * h + (input.textLocale?.toLanguageTag()?.hashCode() ?: 0)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            NovelPageReaderTextView(
                context = context,
                selectionRenderer = selectionRenderer,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                onPlainTap = onPlainTap,
                touchHandlingEnabled = touchHandlingEnabled,
                selectionInteractionEnabled = selectionInteractionEnabled,
            ).apply {
                setPadding(0, 0, 0, glyphPadBottom)
                includeFontPadding = true
                applyTextColorIfChanged(textColor.toArgb())
                applyNovelPageReaderTextViewMetrics(textView = this, metrics = bodyMetrics)
            }
        },
        update = { textView ->
            textView.isDictionaryEnabled = readerSettings.novelDictionaryEnabled
            textView.isTranslationEnabled = readerSettings.selectedTextTranslationEnabled
            textView.selectionAnchorChapterId = selectionAnchorChapterId
            textView.selectionAnchorBlockIndex = preparedInputs.first().sourceBlockIndex
            textView.selectionAnchorTextStartInBlock = preparedInputs.first().sourceTextStartInBlock
            textView.updatePlainTapHandler(onPlainTap)
            textView.updateTouchHandlingEnabled(touchHandlingEnabled)
            textView.updateSelectionInteractionEnabled(
                enabled = selectionInteractionEnabled,
                selectableLayoutEnabled = selectionConfigured,
            )
            if (textView.paddingBottom != glyphPadBottom) {
                textView.setPadding(textView.paddingLeft, textView.paddingTop, textView.paddingRight, glyphPadBottom)
            }
            applyNovelPageReaderTextViewMetrics(textView = textView, metrics = bodyMetrics)
            if (textView.renderedTextSignature != mergedSignature) {
                val resolvedWidth = (availableWidthPx - textView.compoundPaddingLeft - textView.compoundPaddingRight)
                    .coerceAtLeast(1)
                val mergedContent = try {
                    buildMergedPageTextContent(
                        inputs = preparedInputs,
                        pageColumnWidthPx = resolvedWidth,
                        bodyTextSizePx = bodyTextSizePx,
                        bodyLineSpacingMultiplier = bodyLineSpacingMultiplier,
                        bodyTypeface = textTypeface,
                        quoteStripeWidthPx = quoteStripeWidthPx,
                        quoteStripeOffsetPx = quoteStripeOffsetPx,
                        quoteStripeColor = quoteStripeColor,
                        density = density.density,
                        hyphenationEnabled = hyphenationEnabled,
                        onUrlClick = onUrlClick,
                        measurementPaint = textView.paint,
                        probeBuilder = { candidate, _ ->
                            textView.measureLayoutWithProbe(
                                candidateText = candidate,
                                widthPx = resolvedWidth,
                                metrics = bodyMetrics,
                                selectionEnabled = selectionConfigured,
                                bottomPadPx = glyphPadBottom,
                            )
                        },
                    )
                } finally {
                    textView.clearLayoutProbe()
                }
                textView.predictedContentHeightPx = mergedContent.predictedTotalHeightPx
                textView.mergedSegments = mergedContent.segments
                textView.displayToSourceOffsets = null
                textView.syntheticHyphenDisplayOffsets = mergedContent.syntheticHyphenDisplayOffsets
                textView.text = mergedContent.spannable
                textView.renderedTextSignature = mergedSignature
            }
            textView.applyTextColorIfChanged(textColor.toArgb())
        },
        onRelease = { textView ->
            textView.clearFocus()
        },
    )
}
