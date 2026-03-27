package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings

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

@Composable
internal fun NovelPageReaderPageContent(
    contentPage: NovelPageContentPage,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    pageSurfaceColor: Color? = null,
    composeFontFamily: FontFamily?,
    chapterTitleFontFamily: FontFamily?,
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
    val baseTextStyle = resolvePageReaderBaseTextStyle(
        baseStyle = MaterialTheme.typography.bodyLarge,
        color = textColor,
        backgroundColor = textBackground,
        fontSize = readerSettings.fontSize,
        lineHeight = readerSettings.lineHeight,
        fontFamily = composeFontFamily,
        textAlign = null,
        forceBoldText = readerSettings.forceBoldText,
        forceItalicText = readerSettings.forceItalicText,
        textShadow = readerSettings.textShadow,
        textShadowColor = readerSettings.textShadowColor,
        textShadowBlur = readerSettings.textShadowBlur,
        textShadowX = readerSettings.textShadowX,
        textShadowY = readerSettings.textShadowY,
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
                .padding(contentLayout.textPadding),
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
                            Text(
                                text = if (readerSettings.bionicReading) {
                                    toBionicText(block.text)
                                } else {
                                    AnnotatedString(block.text)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                style = resolvePageReaderBlockTextStyle(
                                    baseStyle = baseTextStyle,
                                    isChapterTitle = block.isChapterTitle,
                                    fontSize = readerSettings.fontSize,
                                    lineHeight = readerSettings.lineHeight,
                                    fontFamily = composeFontFamily,
                                    chapterTitleFontFamily = chapterTitleFontFamily,
                                )
                                    .withOptionalTextAlign(
                                        resolvePageReaderRenderTextAlign(
                                            globalTextAlign = readerSettings.textAlign,
                                        ),
                                    )
                                    .withOptionalFirstLineIndentEm(block.firstLineIndentEm),
                            )
                        }
                        is NovelPageContentBlock.Rich -> {
                            Text(
                                text = block.text,
                                modifier = Modifier.fillMaxWidth(),
                                style = resolvePageReaderBlockTextStyle(
                                    baseStyle = baseTextStyle,
                                    isChapterTitle = block.isChapterTitle,
                                    fontSize = readerSettings.fontSize,
                                    lineHeight = readerSettings.lineHeight,
                                    fontFamily = composeFontFamily,
                                    chapterTitleFontFamily = chapterTitleFontFamily,
                                )
                                    .withOptionalTextAlign(
                                        resolvePageReaderRenderTextAlign(
                                            globalTextAlign = readerSettings.textAlign,
                                        ),
                                    )
                                    .withOptionalFirstLineIndentEm(block.firstLineIndentEm),
                            )
                        }
                    }
                }
            }
        }
    }
}
