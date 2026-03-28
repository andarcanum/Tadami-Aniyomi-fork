package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextStyle
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import android.graphics.Color as AndroidColor
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

internal fun buildNovelRichAnnotatedString(
    segments: List<NovelRichTextSegment>,
): AnnotatedString {
    if (segments.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        var cursor = 0
        segments.forEach { segment ->
            if (segment.text.isEmpty()) return@forEach
            val start = cursor
            append(segment.text)
            cursor += segment.text.length
            val end = cursor

            buildNovelRichSpanStyle(segment.style)?.let { addStyle(it, start, end) }
            segment.linkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                addStringAnnotation(tag = "URL", annotation = url, start = start, end = end)
            }
        }
    }
}

private fun buildNovelRichSpanStyle(style: NovelRichTextStyle): SpanStyle? {
    val decorations = buildList {
        if (style.underline) add(TextDecoration.Underline)
        if (style.strikeThrough) add(TextDecoration.LineThrough)
    }
    val textDecoration = when (decorations.size) {
        0 -> null
        1 -> decorations.first()
        else -> TextDecoration.combine(decorations)
    }
    val color = parseNovelRichCssColor(style.colorCss)
    val background = parseNovelRichCssColor(style.backgroundColorCss)

    val spanStyle = SpanStyle(
        fontWeight = if (style.bold) FontWeight.Bold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        textDecoration = textDecoration,
        color = color ?: Color.Unspecified,
        background = background ?: Color.Unspecified,
    )

    return spanStyle.takeUnless { it == SpanStyle() }
}

private fun parseNovelRichCssColor(value: String?): Color? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return null
    val hex = normalized.removePrefix("#")
    return when (hex.length) {
        6 -> runCatching {
            val rgb = hex.toLong(16).toInt()
            val argb = (0xFF shl 24) or rgb
            Color(argb)
        }.getOrNull()
        8 -> runCatching {
            val rgba = hex.toLong(16).toInt()
            val rr = (rgba shr 24) and 0xFF
            val gg = (rgba shr 16) and 0xFF
            val bb = (rgba shr 8) and 0xFF
            val aa = rgba and 0xFF
            val argb = (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
            Color(argb)
        }.getOrNull() ?: runCatching {
            Color(AndroidColor.parseColor(normalized))
        }.getOrNull()
        else -> runCatching { Color(AndroidColor.parseColor(normalized)) }.getOrNull()
    }
}

@Composable
private fun NovelRichAnnotatedText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
) {
    val hasLinkAnnotations = remember(text) {
        text.length > 0 && text.getStringAnnotations(tag = "URL", start = 0, end = text.length).isNotEmpty()
    }
    if (!hasLinkAnnotations) {
        Text(
            text = text,
            style = style,
            modifier = modifier,
        )
        return
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = text,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            resolveNovelRichLinkAtCharOffset(text, offset)?.let(onLinkClick)
        },
    )
}

@Composable
internal fun NovelRichNativeScrollItem(
    block: NovelRichContentBlock,
    index: Int,
    lastIndex: Int,
    chapterTitle: String,
    novelTitle: String,
    sourceId: Long,
    chapterWebUrl: String?,
    novelUrl: String?,
    statusBarTopPadding: androidx.compose.ui.unit.Dp,
    textColor: Color,
    backgroundColor: Color,
    fontSize: Int,
    lineHeight: Float,
    composeFontFamily: FontFamily?,
    chapterTitleFontFamily: FontFamily?,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    textAlign: ReaderTextAlign,
    forceParagraphIndent: Boolean,
    preserveSourceTextAlignInNative: Boolean,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
    textShadow: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
) {
    val context = LocalContext.current
    val textShadowImpl = remember(
        textShadow,
        textShadowColor,
        textShadowBlur,
        textShadowX,
        textShadowY,
        textColor,
        backgroundColor,
    ) {
        if (textShadow) {
            val customColor = parseReaderColor(textShadowColor)
            val shadowColor = resolveAutoReaderShadowColor(
                customShadowColor = customColor,
                textColor = textColor,
                backgroundColor = backgroundColor,
            )
            Shadow(
                color = shadowColor,
                offset = Offset(textShadowX, textShadowY),
                blurRadius = textShadowBlur,
            )
        } else {
            null
        }
    }
    val onLinkClick: (String) -> Unit = { rawUrl ->
        val resolvedUrl = resolveNovelReaderLinkUrl(
            rawUrl = rawUrl,
            chapterWebUrl = chapterWebUrl,
            novelUrl = novelUrl,
        )
        if (!resolvedUrl.isNullOrBlank()) {
            context.startActivity(
                WebViewActivity.newIntent(
                    context = context,
                    url = resolvedUrl,
                    sourceId = sourceId,
                    title = novelTitle,
                ),
            )
        }
    }
    when (block) {
        is NovelRichContentBlock.Paragraph -> {
            val text = buildNovelRichAnnotatedString(block.segments)
            val isChapterTitle = index == 0 && isNativeChapterTitleText(text.text, chapterTitle)
            val paragraphStyle = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                fontSize = if (isChapterTitle) (fontSize * 1.12f).sp else fontSize.sp,
                lineHeight = if (isChapterTitle) (lineHeight * 1.08f).em else lineHeight.em,
                fontFamily = if (isChapterTitle) chapterTitleFontFamily ?: composeFontFamily else composeFontFamily,
                fontWeight = if (isChapterTitle) {
                    FontWeight.SemiBold
                } else if (forceBoldText) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                fontStyle = if (forceItalicText) FontStyle.Italic else FontStyle.Normal,
                shadow = textShadowImpl,
            ).withOptionalTextAlign(
                resolveNativeTextAlign(
                    globalTextAlign = textAlign,
                    preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                    sourceTextAlign = block.textAlign,
                ),
            ).withOptionalFirstLineIndentEm(
                resolveNativeFirstLineIndentEm(
                    forceParagraphIndent = forceParagraphIndent && !isChapterTitle,
                    sourceFirstLineIndentEm = block.firstLineIndentEm,
                ),
            )
            if (isChapterTitle) {
                Column(
                    modifier = Modifier.padding(
                        top = statusBarTopPadding + 10.dp,
                        bottom = if (index == lastIndex) 0.dp else 18.dp,
                    ),
                ) {
                    NovelRichAnnotatedText(
                        text = text,
                        style = paragraphStyle.copy(color = MaterialTheme.colorScheme.primary),
                        onLinkClick = onLinkClick,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(0.72f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    )
                }
            } else {
                NovelRichAnnotatedText(
                    text = text,
                    style = paragraphStyle,
                    modifier = Modifier.padding(
                        top = if (index == 0) statusBarTopPadding else 0.dp,
                        bottom = if (index == lastIndex) 0.dp else paragraphSpacing,
                    ),
                    onLinkClick = onLinkClick,
                )
            }
        }
        is NovelRichContentBlock.Heading -> {
            val headingScale = when (block.level) {
                1 -> 1.24f
                2 -> 1.18f
                3 -> 1.13f
                else -> 1.08f
            }
            NovelRichAnnotatedText(
                text = buildNovelRichAnnotatedString(block.segments),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    fontSize = (fontSize * headingScale).sp,
                    lineHeight = (lineHeight * 1.1f).em,
                    fontFamily = composeFontFamily,
                    fontWeight = if (forceBoldText) FontWeight.Bold else FontWeight.SemiBold,
                    fontStyle = if (forceItalicText) FontStyle.Italic else FontStyle.Normal,
                    shadow = textShadowImpl,
                ).withOptionalTextAlign(
                    resolveNativeTextAlign(
                        globalTextAlign = textAlign,
                        preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                        sourceTextAlign = block.textAlign,
                    ),
                ),
                modifier = Modifier.padding(
                    top = if (index == 0) statusBarTopPadding else 4.dp,
                    bottom = if (index == lastIndex) 0.dp else paragraphSpacing + 2.dp,
                ),
                onLinkClick = onLinkClick,
            )
        }
        is NovelRichContentBlock.BlockQuote -> {
            NovelRichAnnotatedText(
                text = buildNovelRichAnnotatedString(block.segments),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor.copy(alpha = 0.92f),
                    fontSize = fontSize.sp,
                    lineHeight = lineHeight.em,
                    fontFamily = composeFontFamily,
                    fontWeight = if (forceBoldText) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (forceItalicText) FontStyle.Italic else FontStyle.Normal,
                    shadow = textShadowImpl,
                ).withOptionalTextAlign(
                    resolveNativeTextAlign(
                        globalTextAlign = textAlign,
                        preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                        sourceTextAlign = block.textAlign,
                    ),
                ),
                modifier = Modifier
                    .padding(
                        top = if (index == 0) statusBarTopPadding else 0.dp,
                        bottom = if (index == lastIndex) 0.dp else paragraphSpacing,
                    )
                    .padding(start = 12.dp),
                onLinkClick = onLinkClick,
            )
        }
        NovelRichContentBlock.HorizontalRule -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (index == 0) statusBarTopPadding else 4.dp,
                        bottom = if (index == lastIndex) 4.dp else paragraphSpacing + 4.dp,
                    )
                    .height(1.dp)
                    .background(textColor.copy(alpha = 0.22f)),
            )
        }
        is NovelRichContentBlock.Image -> {
            val imageModel = if (NovelPluginImage.isSupported(block.url)) {
                NovelPluginImage(block.url)
            } else {
                block.url
            }
            AsyncImage(
                model = imageModel,
                contentDescription = block.alt,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (index == 0) statusBarTopPadding else 0.dp,
                        bottom = if (index == lastIndex) 0.dp else paragraphSpacing,
                    ),
            )
        }
    }
}
