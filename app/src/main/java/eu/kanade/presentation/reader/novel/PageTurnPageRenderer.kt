package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings

internal fun resolvePageTurnRendererFallbackStyle(
    requestedStyle: NovelPageTransitionStyle,
): NovelPageTransitionStyle {
    return when (requestedStyle) {
        NovelPageTransitionStyle.BOOK,
        NovelPageTransitionStyle.CURL,
        -> NovelPageTransitionStyle.SLIDE
        NovelPageTransitionStyle.INSTANT,
        NovelPageTransitionStyle.SLIDE,
        NovelPageTransitionStyle.DEPTH,
        -> requestedStyle
    }
}

internal fun resolvePageTurnRendererProgressPageIndex(
    currentPage: Int,
): Int {
    return currentPage.coerceAtLeast(0)
}

@Composable
internal fun PageTurnPageRenderer(
    pagerState: androidx.compose.foundation.pager.PagerState,
    contentPages: List<NovelPageContentPage>,
    transitionStyle: NovelPageTransitionStyle,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    composeFontFamily: FontFamily?,
    chapterTitleFontFamily: FontFamily?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onToggleUi: () -> Unit,
    onMoveBackward: () -> Unit,
    onMoveForward: () -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
) {
    ComposePagerPageRenderer(
        pagerState = pagerState,
        contentPages = contentPages,
        transitionStyle = resolvePageTurnRendererFallbackStyle(transitionStyle),
        readerSettings = readerSettings,
        textColor = textColor,
        textBackground = textBackground,
        composeFontFamily = composeFontFamily,
        chapterTitleFontFamily = chapterTitleFontFamily,
        contentPadding = contentPadding,
        statusBarTopPadding = statusBarTopPadding,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
        onToggleUi = onToggleUi,
        onMoveBackward = onMoveBackward,
        onMoveForward = onMoveForward,
        onOpenPreviousChapter = onOpenPreviousChapter,
        onOpenNextChapter = onOpenNextChapter,
    )
}
