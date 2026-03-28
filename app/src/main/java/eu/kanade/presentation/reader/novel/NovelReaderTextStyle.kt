package eu.kanade.presentation.reader.novel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichBlockTextAlign
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

private fun novelReaderTextAlign(textAlign: ReaderTextAlign): TextAlign? {
    return when (textAlign) {
        ReaderTextAlign.SOURCE -> null
        ReaderTextAlign.LEFT -> TextAlign.Start
        ReaderTextAlign.CENTER -> TextAlign.Center
        ReaderTextAlign.JUSTIFY -> TextAlign.Justify
        ReaderTextAlign.RIGHT -> TextAlign.End
    }
}

internal fun TextStyle.withOptionalTextAlign(textAlign: TextAlign?): TextStyle {
    return if (textAlign == null) this else copy(textAlign = textAlign)
}

internal fun TextStyle.withOptionalFirstLineIndentEm(firstLineIndentEm: Float?): TextStyle {
    return if (firstLineIndentEm == null) {
        this
    } else {
        copy(textIndent = TextIndent(firstLine = firstLineIndentEm.em))
    }
}

internal fun resolveReaderTextShadow(
    textShadowEnabled: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    textColor: Color,
    backgroundColor: Color,
): Shadow? {
    if (!textShadowEnabled) return null
    val customColor = parseReaderColor(textShadowColor)
    val shadowColor = resolveAutoReaderShadowColor(
        customShadowColor = customColor,
        textColor = textColor,
        backgroundColor = backgroundColor,
    )
    return Shadow(
        color = shadowColor,
        offset = Offset(textShadowX, textShadowY),
        blurRadius = textShadowBlur,
    )
}

internal fun resolvePageReaderBaseTextStyle(
    baseStyle: TextStyle,
    color: Color,
    backgroundColor: Color,
    fontSize: Int,
    lineHeight: Float,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    textAlign: TextAlign?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
    textShadow: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
): TextStyle {
    return baseStyle.copy(
        color = color,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.em,
        fontFamily = fontFamily,
        fontWeight = if (forceBoldText) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (forceItalicText) FontStyle.Italic else FontStyle.Normal,
        shadow = resolveReaderTextShadow(
            textShadowEnabled = textShadow,
            textShadowColor = textShadowColor,
            textShadowBlur = textShadowBlur,
            textShadowX = textShadowX,
            textShadowY = textShadowY,
            textColor = color,
            backgroundColor = backgroundColor,
        ),
    ).withOptionalTextAlign(textAlign)
}

internal fun resolvePageReaderBlockTextStyle(
    baseStyle: TextStyle,
    isChapterTitle: Boolean,
    fontSize: Int,
    lineHeight: Float,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    chapterTitleFontFamily: androidx.compose.ui.text.font.FontFamily?,
): TextStyle {
    if (!isChapterTitle) return baseStyle
    return baseStyle.copy(
        fontSize = (fontSize * 1.12f).sp,
        lineHeight = (lineHeight * 1.08f).em,
        fontFamily = chapterTitleFontFamily ?: fontFamily,
        fontWeight = FontWeight.SemiBold,
    )
}

internal fun resolvePageReaderLayoutTextAlign(
    globalTextAlign: ReaderTextAlign,
    @Suppress("UNUSED_PARAMETER") preserveSourceTextAlignInNative: Boolean,
): ReaderTextAlign {
    return if (globalTextAlign == ReaderTextAlign.SOURCE) {
        // Page mode always needs a deterministic alignment for layout and pagination.
        ReaderTextAlign.LEFT
    } else {
        globalTextAlign
    }
}

internal fun resolvePageReaderRenderTextAlign(
    globalTextAlign: ReaderTextAlign,
): TextAlign {
    return novelReaderTextAlign(textAlign = globalTextAlign) ?: TextAlign.Start
}

internal fun resolveNativeTextAlign(
    globalTextAlign: ReaderTextAlign,
    preserveSourceTextAlignInNative: Boolean,
    sourceTextAlign: NovelRichBlockTextAlign? = null,
): TextAlign? {
    if (!preserveSourceTextAlignInNative) {
        return novelReaderTextAlign(textAlign = globalTextAlign)
    }
    return when (sourceTextAlign) {
        NovelRichBlockTextAlign.LEFT -> TextAlign.Start
        NovelRichBlockTextAlign.CENTER -> TextAlign.Center
        NovelRichBlockTextAlign.JUSTIFY -> TextAlign.Justify
        NovelRichBlockTextAlign.RIGHT -> TextAlign.End
        null -> novelReaderTextAlign(textAlign = globalTextAlign)
    }
}

internal fun resolveNativeFirstLineIndentEm(
    forceParagraphIndent: Boolean,
    sourceFirstLineIndentEm: Float?,
): Float? {
    return if (forceParagraphIndent) {
        FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
    } else {
        sourceFirstLineIndentEm
    }
}

internal fun resolveNovelRichLinkAtCharOffset(
    text: AnnotatedString,
    offset: Int,
): String? {
    if (text.isEmpty()) return null
    val clamped = offset.coerceIn(0, text.length - 1)
    return text.getStringAnnotations(
        tag = "URL",
        start = clamped,
        end = (clamped + 1).coerceAtMost(text.length),
    ).lastOrNull()?.item
}

internal fun resolveNovelReaderLinkUrl(
    rawUrl: String,
    chapterWebUrl: String?,
    novelUrl: String?,
): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    trimmed.toHttpUrlOrNull()?.let { return it.toString() }
    chapterWebUrl?.toHttpUrlOrNull()?.resolve(trimmed)?.let { return it.toString() }
    novelUrl?.toHttpUrlOrNull()?.resolve(trimmed)?.let { return it.toString() }
    return null
}

internal fun isNativeChapterTitleText(
    blockText: String,
    chapterName: String,
): Boolean {
    val normalizedBlock = blockText
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    val normalizedChapter = chapterName
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    return normalizedBlock.isNotBlank() && normalizedBlock == normalizedChapter
}
