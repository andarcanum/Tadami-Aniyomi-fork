package eu.kanade.presentation.reader.novel

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.icu.text.BreakIterator
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
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
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
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectionAnchor
import eu.kanade.tachiyomi.ui.reader.novel.SelectedTextAction
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.util.Locale
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
): Int {
    var result = text.hashCode()
    result = 31 * result + (firstLineIndentPx ?: 0)
    result = 31 * result + forcedTypefaceStyle
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
)

internal fun resolveNovelPageReaderTextViewMetrics(
    textSizePx: Float,
    lineSpacingMultiplier: Float,
    typeface: Typeface?,
    textAlign: ReaderTextAlign,
    shadow: Shadow?,
): NovelPageReaderTextViewMetrics {
    return NovelPageReaderTextViewMetrics(
        textSizePx = textSizePx,
        lineSpacingMultiplier = lineSpacingMultiplier,
        typeface = typeface,
        textAlign = textAlign,
        shadow = shadow,
    )
}

internal fun applyNovelPageReaderTextViewMetrics(
    textView: TextView,
    metrics: NovelPageReaderTextViewMetrics,
) {
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.textSizePx)
    textView.setLineSpacing(0f, metrics.lineSpacingMultiplier)
    textView.typeface = metrics.typeface
    textView.gravity = resolveNovelPageReaderTextGravity(metrics.textAlign)
    metrics.shadow?.let { resolvedShadow ->
        textView.setShadowLayer(
            resolvedShadow.blurRadius,
            resolvedShadow.offset.x,
            resolvedShadow.offset.y,
            resolvedShadow.color.toArgb(),
        )
    } ?: textView.setShadowLayer(0f, 0f, 0f, Color.Transparent.toArgb())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        textView.justificationMode = if (metrics.textAlign == ReaderTextAlign.JUSTIFY) {
            Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            Layout.JUSTIFICATION_MODE_NONE
        }
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
    private var onPlainTap: ((Float, Float, Float, Float) -> Unit)?,
    private val touchHandlingEnabled: Boolean,
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
    private var isDetaching = false

    override fun onDetachedFromWindow() {
        isDetaching = true
        finishSelectionActionMode()
        super.onDetachedFromWindow()
        isDetaching = false
    }

    private fun isDetachingFromCompose(): Boolean {
        if (isDetaching) return true
        val p = parent as? View ?: return true
        return p.parent == null
    }

    override fun clearFocus() {
        if (isDetachingFromCompose() || isLayoutRequested) {
            return
        }
        super.clearFocus()
    }

    override fun focusSearch(direction: Int): View? {
        if (isDetachingFromCompose() || isLayoutRequested) {
            return null
        }
        return super.focusSearch(direction)
    }

    init {
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
                // Mirror the framework behavior: collapse the selection when the toolbar
                // closes (back / tap outside / after an action). publishSelection() skips the
                // null event while isExecutingAction is set, so dictionary/translation flows
                // are not cancelled.
                val spannable = text as? Spannable ?: return
                val selectionEnd = Selection.getSelectionEnd(spannable)
                if (selectionEnd >= 0) {
                    Selection.setSelection(spannable, selectionEnd)
                }
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

    fun updateSelectionInteractionEnabled(enabled: Boolean) {
        selectionInteractionEnabled = enabled && touchHandlingEnabled
        setTextIsSelectable(selectionInteractionEnabled)
        // setTextIsSelectable() re-enables focusability, so this interop view can become the
        // focused Android view. Compose detaches interop views while composition changes are
        // still being applied, and ViewGroup.removeViewInLayout() then makes the framework search
        // the hierarchy for a new focus target. That search re-enters Compose layout and crashes
        // with "Cannot start a writer when another writer is pending". Selection here is driven by
        // Selection.setSelection(), so Android focus is not needed.
        // Focus matters for selection: the editor raises its drag handles only on a focused
        // view, and in touch mode a view must be focusable-IN-touch-mode to take focus at all
        // (programmatic requestFocus included). Detach-time focus searches stay neutralized by
        // the clearFocus/focusSearch guards above (the original "writer pending" crash).
        isFocusable = selectionInteractionEnabled
        isFocusableInTouchMode = selectionInteractionEnabled
        isLongClickable = selectionInteractionEnabled
        if (!selectionInteractionEnabled) {
            clearSelectionPromotion()
            selectionPromotedByLongPress = false
            clearSelection()
            finishSelectionActionMode()
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        if (!selectionInteractionEnabled) return
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
                // A down on the live selection (drag handles included) must fall through to the
                // TextView editor so the handles can extend the selection; wiping it here made
                // every long-press word selection impossible to grow into a phrase.
                val downOnLiveSelection = selectionInteractionEnabled &&
                    hasActiveSelection() &&
                    isTouchNearSelectionBounds(event.x, event.y)
                selectionPromotedByLongPress = false
                if (selectionInteractionEnabled && !downOnLiveSelection) {
                    clearSelection()
                    finishSelectionActionMode()
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
        if (!isFocused) {
            requestFocus()
        }
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
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    }

    private fun shareSelectedText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
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
            if (!isExecutingAction) {
                onSelectedTextSelectionChanged(null)
            }
            isExecutingAction = false
            return
        }

        val layout = layout ?: run {
            localSelection = null
            if (!isExecutingAction) {
                onSelectedTextSelectionChanged(null)
            }
            isExecutingAction = false
            return
        }
        val start = minOf(selStart, selEnd).coerceIn(0, currentText.length)
        val end = maxOf(selStart, selEnd).coerceIn(start, currentText.length)
        if (start >= end) {
            localSelection = null
            if (!isExecutingAction) {
                onSelectedTextSelectionChanged(null)
            }
            isExecutingAction = false
            return
        }

        val selectionPath = Path()
        layout.getSelectionPath(start, end, selectionPath)
        val bounds = RectF()
        selectionPath.computeBounds(bounds, true)
        if (bounds.isEmpty) {
            localSelection = null
            if (!isExecutingAction) {
                onSelectedTextSelectionChanged(null)
            }
            isExecutingAction = false
            return
        }
        selectionBoundsInView.set(bounds)

        val selectedText = currentText.substring(start, end)
        if (selectedText.isBlank()) {
            localSelection = null
            if (!isExecutingAction) {
                onSelectedTextSelectionChanged(null)
            }
            isExecutingAction = false
            return
        }

        val locationOnScreen = IntArray(2)
        getLocationOnScreen(locationOnScreen)
        val selectionAnchor = selectionAnchorChapterId?.let { chapterId ->
            NovelSelectionAnchor(
                chapterId = chapterId,
                blockIndex = selectionAnchorBlockIndex,
                charStart = selectionAnchorTextStartInBlock + start,
                charEndExclusive = selectionAnchorTextStartInBlock + end,
            )
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
            selectionAnchor = selectionAnchor,
        )
        isExecutingAction = false
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
        Selection.removeSelection(spannable)
        onSelectedTextSelectionChanged(null)
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
        if (!isFocused) {
            requestFocus()
        }
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
    ttsHighlightState: NovelReaderTtsHighlightState? = null,
    ttsHighlightColor: Color = Color.Transparent,
    selectionRenderer: NovelSelectedTextRenderer = NovelSelectedTextRenderer.PAGE_READER,
    selectionSessionIdProvider: () -> Long = { 0L },
    selectionAnchorChapterId: Long? = null,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onPlainTap: ((Float, Float, Float, Float) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
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
    val preserveSourceAlign = readerSettings.preserveSourceTextAlignInNative

    // Handle short taps on the empty page area (below the text, in margins, around images)
    // so they reach the same onPlainTap dispatch as taps on text/image blocks. Child
    // handlers (TextView, image block) consume their own events, so this never double-fires.
    val pageTapModifier = if (touchHandlingEnabled) {
        Modifier.pointerInput(onPlainTap) {
            detectTapGestures(
                onTap = { offset ->
                    onPlainTap?.invoke(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
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
                fullPage = true,
                onPlainTap = onPlainTap,
                onImageLongClick = onImageLongClick,
                touchHandlingEnabled = touchHandlingEnabled,
            )
            return@Box
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentLayout.textPadding)
                .padding(bottom = bookBottomInset),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                contentPage.blocks.forEachIndexed { index, block ->
                    key(index, block) {
                        if (block.spacingBeforePx > 0) {
                            Spacer(
                                modifier = Modifier.height(
                                    with(density) { block.spacingBeforePx.toDp() },
                                ),
                            )
                        }
                        when (block) {
                            is NovelPageContentBlock.Plain -> {
                                val baseText = if (readerSettings.bionicReading) {
                                    toBionicText(block.text)
                                } else {
                                    AnnotatedString(block.text)
                                }
                                NovelPageReaderTextBlock(
                                    text = applyNovelReaderTtsHighlight(
                                        text = baseText,
                                        blockText = block.text,
                                        sourceBlockIndex = block.sourceBlockIndex,
                                        pageIndex = contentPage.pageIndex,
                                        pageBlockTextStart = block.sourceTextStart,
                                        pageBlockTextEndExclusive = block.sourceTextEndExclusive,
                                        highlightState = ttsHighlightState,
                                        highlightColor = ttsHighlightColor,
                                    ),
                                    isChapterTitle = block.isChapterTitle,
                                    firstLineIndentEm = block.firstLineIndentEm,
                                    readerSettings = readerSettings,
                                    textColor = textColor,
                                    textBackground = textBackground,
                                    textAlign = resolveNativeReaderTextAlign(
                                        globalTextAlign = readerSettings.textAlign,
                                        preserveSourceTextAlignInNative = preserveSourceAlign,
                                        sourceTextAlign = null,
                                    ),
                                    textTypeface = textTypeface,
                                    chapterTitleTypeface = chapterTitleTypeface,
                                    chapterTitleTextColor = if (block.isChapterTitle) {
                                        Color.Black
                                    } else {
                                        chapterTitleTextColor
                                    },
                                    textShadowEnabled = readerSettings.textShadow,
                                    textShadowColor = readerSettings.textShadowColor,
                                    textShadowBlur = readerSettings.textShadowBlur,
                                    textShadowX = readerSettings.textShadowX,
                                    textShadowY = readerSettings.textShadowY,
                                    baseTypefaceStyle = if (block.isChapterTitle) {
                                        Typeface.BOLD
                                    } else {
                                        Typeface.NORMAL
                                    },
                                    selectionRenderer = selectionRenderer,
                                    selectionSessionIdProvider = selectionSessionIdProvider,
                                    selectionAnchorChapterId = selectionAnchorChapterId,
                                    selectionAnchorBlockIndex = block.sourceBlockIndex,
                                    selectionAnchorTextStartInBlock = block.sourceTextStart ?: 0,
                                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                    onPlainTap = onPlainTap,
                                    touchHandlingEnabled = touchHandlingEnabled,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            is NovelPageContentBlock.Rich -> {
                                NovelPageReaderTextBlock(
                                    text = applyNovelReaderTtsHighlight(
                                        text = block.text,
                                        blockText = block.text.text,
                                        sourceBlockIndex = block.sourceBlockIndex,
                                        pageIndex = contentPage.pageIndex,
                                        pageBlockTextStart = block.sourceTextStart,
                                        pageBlockTextEndExclusive = block.sourceTextEndExclusive,
                                        highlightState = ttsHighlightState,
                                        highlightColor = ttsHighlightColor,
                                    ),
                                    isChapterTitle = block.isChapterTitle,
                                    firstLineIndentEm = block.firstLineIndentEm,
                                    readerSettings = readerSettings,
                                    textColor = textColor,
                                    textBackground = textBackground,
                                    textAlign = resolveNativeReaderTextAlign(
                                        globalTextAlign = readerSettings.textAlign,
                                        preserveSourceTextAlignInNative = preserveSourceAlign,
                                        sourceTextAlign = block.sourceTextAlign,
                                    ),
                                    textTypeface = textTypeface,
                                    chapterTitleTypeface = chapterTitleTypeface,
                                    chapterTitleTextColor = if (block.isChapterTitle) {
                                        Color.Black
                                    } else {
                                        chapterTitleTextColor
                                    },
                                    textShadowEnabled = readerSettings.textShadow,
                                    textShadowColor = readerSettings.textShadowColor,
                                    textShadowBlur = readerSettings.textShadowBlur,
                                    textShadowX = readerSettings.textShadowX,
                                    textShadowY = readerSettings.textShadowY,
                                    baseTypefaceStyle = if (block.isChapterTitle) {
                                        Typeface.BOLD
                                    } else {
                                        Typeface.NORMAL
                                    },
                                    selectionRenderer = selectionRenderer,
                                    selectionSessionIdProvider = selectionSessionIdProvider,
                                    selectionAnchorChapterId = selectionAnchorChapterId,
                                    selectionAnchorBlockIndex = block.sourceBlockIndex,
                                    selectionAnchorTextStartInBlock = block.sourceTextStart ?: 0,
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
                                    fullPage = false,
                                    onPlainTap = onPlainTap,
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
}

@Composable
private fun NovelPageReaderImageBlock(
    imageUrl: String,
    contentDescription: String?,
    contentLayout: NovelPageReaderContentLayout,
    bookBottomInset: Dp,
    fullPage: Boolean,
    onPlainTap: ((Float, Float, Float, Float) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    touchHandlingEnabled: Boolean = true,
) {
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
        Modifier.pointerInput(imageUrl, onPlainTap, onImageLongClick) {
            detectTapGestures(
                onTap = { offset ->
                    onPlainTap?.invoke(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
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
                    .heightIn(max = 420.dp),
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
    selectionRenderer: NovelSelectedTextRenderer? = null,
    selectionSessionIdProvider: () -> Long = { 0L },
    selectionAnchorChapterId: Long? = null,
    selectionAnchorBlockIndex: Int = 0,
    selectionAnchorTextStartInBlock: Int = 0,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onPlainTap: ((Float, Float, Float, Float) -> Unit)? = null,
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
    val blockTextViewMetrics = resolveNovelPageReaderTextViewMetrics(
        textSizePx = blockTextSizePx,
        lineSpacingMultiplier = blockLineSpacingMultiplier,
        typeface = blockTypeface,
        textAlign = textAlign,
        shadow = blockTextShadow,
    )
    val selectionInteractionEnabled =
        touchHandlingEnabled &&
            (
                readerSettings.textSelectionEnabled ||
                    readerSettings.selectedTextTranslationEnabled ||
                    readerSettings.novelDictionaryEnabled
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
                selectionInteractionEnabled = selectionInteractionEnabled,
            ).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                val glyphPadBottom = resolvePageReaderGlyphOverflowPaddingPx(blockTextSizePx)
                setPadding(0, 0, 0, glyphPadBottom)
                includeFontPadding = false
                setTextColor(blockTextColor.toArgb())
                applyNovelPageReaderTextViewMetrics(textView = this, metrics = blockTextViewMetrics)
            }
        },
        update = { textView ->
            textView.isDictionaryEnabled = readerSettings.novelDictionaryEnabled
            textView.isTranslationEnabled = readerSettings.selectedTextTranslationEnabled
            textView.selectionAnchorChapterId = selectionAnchorChapterId
            textView.selectionAnchorBlockIndex = selectionAnchorBlockIndex
            textView.selectionAnchorTextStartInBlock = selectionAnchorTextStartInBlock
            textView.updatePlainTapHandler(onPlainTap)
            textView.updateSelectionInteractionEnabled(selectionInteractionEnabled)
            val glyphPadBottom = resolvePageReaderGlyphOverflowPaddingPx(blockTextSizePx)
            textView.setPadding(textView.paddingLeft, textView.paddingTop, textView.paddingRight, glyphPadBottom)
            applyNovelPageReaderTextViewMetrics(textView = textView, metrics = blockTextViewMetrics)
            val forcedTypefaceStyle = combineTypefaceStyles(
                first = baseTypefaceStyle,
                second = resolveForcedReaderTypefaceStyle(
                    forceBoldText = readerSettings.forceBoldText,
                    forceItalicText = readerSettings.forceItalicText,
                ),
            )
            val renderedTextSignature = resolveNovelPageReaderRenderedTextSignature(
                text = text,
                firstLineIndentPx = blockFirstLineIndentPx,
                forcedTypefaceStyle = forcedTypefaceStyle,
            )
            if (textView.renderedTextSignature != renderedTextSignature) {
                textView.text = buildNovelPageReaderSpannableText(
                    text = text,
                    firstLineIndentPx = blockFirstLineIndentPx,
                    forcedTypefaceStyle = forcedTypefaceStyle,
                    onUrlClick = onUrlClick,
                )
                textView.renderedTextSignature = renderedTextSignature
            }
            textView.setTextColor(blockTextColor.toArgb())
        },
        // Dropping a focused interop view makes the framework search the hierarchy for a new focus
        // target, which can re-enter Compose layout while changes are still being applied. Giving
        // up focus first keeps that removal side-effect free.
        onRelease = { textView ->
            textView.clearFocus()
        },
    )
}
