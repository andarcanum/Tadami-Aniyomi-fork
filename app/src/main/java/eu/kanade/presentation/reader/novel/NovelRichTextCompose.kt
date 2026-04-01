package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextStyle
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
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

private fun resolveNativeReaderTextAlign(
    globalTextAlign: ReaderTextAlign,
    preserveSourceTextAlignInNative: Boolean,
    sourceTextAlign: eu.kanade.tachiyomi.ui.reader.novel.NovelRichBlockTextAlign? = null,
): ReaderTextAlign {
    return when (
        resolveNativeTextAlign(
            globalTextAlign = globalTextAlign,
            preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
            sourceTextAlign = sourceTextAlign,
        )
    ) {
        TextAlign.Center -> ReaderTextAlign.CENTER
        TextAlign.End -> ReaderTextAlign.RIGHT
        TextAlign.Justify -> ReaderTextAlign.JUSTIFY
        else -> ReaderTextAlign.LEFT
    }
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
    readerSettings: NovelReaderSettings,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    selectionSessionIdProvider: () -> Long,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit,
    onPlainTap: ((Float, Float) -> Unit)? = null,
) {
    val context = LocalContext.current
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
            val resolvedTextAlign = resolveNativeReaderTextAlign(
                globalTextAlign = readerSettings.textAlign,
                preserveSourceTextAlignInNative = readerSettings.preserveSourceTextAlignInNative,
                sourceTextAlign = block.textAlign,
            )
            val resolvedFirstLineIndentEm = resolveNativeFirstLineIndentEm(
                forceParagraphIndent = readerSettings.forceParagraphIndent && !isChapterTitle,
                sourceFirstLineIndentEm = block.firstLineIndentEm,
            )
            if (isChapterTitle) {
                Column(
                    modifier = Modifier.padding(
                        top = statusBarTopPadding + 10.dp,
                        bottom = if (index == lastIndex) 0.dp else 18.dp,
                    ),
                ) {
                    NovelPageReaderTextBlock(
                        text = text,
                        isChapterTitle = true,
                        firstLineIndentEm = null,
                        readerSettings = readerSettings,
                        textColor = textColor,
                        textBackground = backgroundColor,
                        textAlign = resolvedTextAlign,
                        textTypeface = textTypeface,
                        chapterTitleTypeface = chapterTitleTypeface,
                        chapterTitleTextColor = MaterialTheme.colorScheme.primary,
                        textShadowEnabled = readerSettings.textShadow,
                        textShadowColor = readerSettings.textShadowColor,
                        textShadowBlur = readerSettings.textShadowBlur,
                        textShadowX = readerSettings.textShadowX,
                        textShadowY = readerSettings.textShadowY,
                        selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                        selectionSessionIdProvider = selectionSessionIdProvider,
                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        onPlainTap = onPlainTap,
                        onUrlClick = onLinkClick,
                        modifier = Modifier.fillMaxWidth(),
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
                NovelPageReaderTextBlock(
                    text = text,
                    isChapterTitle = false,
                    firstLineIndentEm = resolvedFirstLineIndentEm,
                    readerSettings = readerSettings,
                    textColor = textColor,
                    textBackground = backgroundColor,
                    textAlign = resolvedTextAlign,
                    textTypeface = textTypeface,
                    chapterTitleTypeface = chapterTitleTypeface,
                    chapterTitleTextColor = MaterialTheme.colorScheme.primary,
                    textShadowEnabled = readerSettings.textShadow,
                    textShadowColor = readerSettings.textShadowColor,
                    textShadowBlur = readerSettings.textShadowBlur,
                    textShadowX = readerSettings.textShadowX,
                    textShadowY = readerSettings.textShadowY,
                    selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                    selectionSessionIdProvider = selectionSessionIdProvider,
                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                    onPlainTap = onPlainTap,
                    onUrlClick = onLinkClick,
                    modifier = Modifier.padding(
                        top = if (index == 0) statusBarTopPadding else 0.dp,
                        bottom = if (index == lastIndex) 0.dp else paragraphSpacing,
                    ),
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
            NovelPageReaderTextBlock(
                text = buildNovelRichAnnotatedString(block.segments),
                isChapterTitle = false,
                firstLineIndentEm = null,
                readerSettings = readerSettings,
                textColor = textColor,
                textBackground = backgroundColor,
                textAlign = resolveNativeReaderTextAlign(
                    globalTextAlign = readerSettings.textAlign,
                    preserveSourceTextAlignInNative = readerSettings.preserveSourceTextAlignInNative,
                    sourceTextAlign = block.textAlign,
                ),
                textTypeface = textTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                chapterTitleTextColor = MaterialTheme.colorScheme.primary,
                textShadowEnabled = readerSettings.textShadow,
                textShadowColor = readerSettings.textShadowColor,
                textShadowBlur = readerSettings.textShadowBlur,
                textShadowX = readerSettings.textShadowX,
                textShadowY = readerSettings.textShadowY,
                fontSizeMultiplier = headingScale,
                lineHeightMultiplier = 1.1f,
                baseTypefaceStyle = Typeface.BOLD,
                selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                onPlainTap = onPlainTap,
                onUrlClick = onLinkClick,
                modifier = Modifier.padding(
                    top = if (index == 0) statusBarTopPadding else 4.dp,
                    bottom = if (index == lastIndex) 0.dp else paragraphSpacing + 2.dp,
                ),
            )
        }
        is NovelRichContentBlock.BlockQuote -> {
            NovelPageReaderTextBlock(
                text = buildNovelRichAnnotatedString(block.segments),
                isChapterTitle = false,
                firstLineIndentEm = null,
                readerSettings = readerSettings,
                textColor = textColor,
                textBackground = backgroundColor,
                textAlign = resolveNativeReaderTextAlign(
                    globalTextAlign = readerSettings.textAlign,
                    preserveSourceTextAlignInNative = readerSettings.preserveSourceTextAlignInNative,
                    sourceTextAlign = block.textAlign,
                ),
                textTypeface = textTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                chapterTitleTextColor = MaterialTheme.colorScheme.primary,
                textShadowEnabled = readerSettings.textShadow,
                textShadowColor = readerSettings.textShadowColor,
                textShadowBlur = readerSettings.textShadowBlur,
                textShadowX = readerSettings.textShadowX,
                textShadowY = readerSettings.textShadowY,
                textColorOverride = textColor.copy(alpha = 0.92f),
                selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                onPlainTap = onPlainTap,
                onUrlClick = onLinkClick,
                modifier = Modifier
                    .padding(
                        top = if (index == 0) statusBarTopPadding else 0.dp,
                        bottom = if (index == lastIndex) 0.dp else paragraphSpacing,
                    )
                    .padding(start = 12.dp),
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
