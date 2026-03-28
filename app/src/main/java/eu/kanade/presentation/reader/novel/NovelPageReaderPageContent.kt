package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import kotlin.math.roundToInt
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
) : TextView(context) {

    init {
        isClickable = false
        isLongClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false
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
        NovelPageSurfaceBackground(
            backgroundTexture = backgroundTexture,
            nativeTextureStrengthPercent = nativeTextureStrengthPercent,
            surfaceColor = pageSurfaceColor,
        )
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelPageReaderTextBlock(
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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val blockFontSizeMultiplier = if (isChapterTitle) {
        PAGE_READER_CHAPTER_TITLE_FONT_SIZE_MULTIPLIER
    } else {
        1f
    }
    val blockLineHeightMultiplier = if (isChapterTitle) {
        PAGE_READER_CHAPTER_TITLE_LINE_HEIGHT_MULTIPLIER
    } else {
        1f
    }
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
    val blockTextColor = if (isChapterTitle) chapterTitleTextColor else textColor
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
            NovelPageReaderTextView(context).apply {
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
            textView.text = buildNovelPageReaderSpannableText(
                text = text,
                firstLineIndentPx = blockFirstLineIndentPx,
                forcedTypefaceStyle = resolveForcedReaderTypefaceStyle(
                    forceBoldText = readerSettings.forceBoldText,
                    forceItalicText = readerSettings.forceItalicText,
                ),
            )
        },
    )
}
