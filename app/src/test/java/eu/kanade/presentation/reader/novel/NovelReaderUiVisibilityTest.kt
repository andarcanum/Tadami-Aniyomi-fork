package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelReaderUiVisibilityTest {

    @Test
    fun `bottom overlay hidden when reader ui hidden`() {
        val visible = shouldShowBottomInfoOverlay(
            showReaderUi = false,
            showBatteryAndTime = true,
        )

        assertFalse(visible)
    }

    @Test
    fun `bottom overlay hidden when only percentage is enabled`() {
        val visible = shouldShowBottomInfoOverlay(
            showReaderUi = true,
            showBatteryAndTime = false,
        )

        assertFalse(visible)
    }

    @Test
    fun `bottom overlay stays visible for battery and time`() {
        val visible = shouldShowBottomInfoOverlay(
            showReaderUi = true,
            showBatteryAndTime = true,
        )

        assertTrue(visible)
    }

    @Test
    fun `seekbar shown only with visible ui and eligible state`() {
        assertTrue(
            shouldShowVerticalSeekbar(
                showReaderUi = true,
                verticalSeekbarEnabled = true,
                showWebView = false,
                textBlocksCount = 10,
            ),
        )

        assertFalse(
            shouldShowVerticalSeekbar(
                showReaderUi = false,
                verticalSeekbarEnabled = true,
                showWebView = false,
                textBlocksCount = 10,
            ),
        )

        assertFalse(
            shouldShowVerticalSeekbar(
                showReaderUi = true,
                verticalSeekbarEnabled = true,
                showWebView = true,
                textBlocksCount = 1,
            ),
        )
        assertTrue(
            shouldShowVerticalSeekbar(
                showReaderUi = true,
                verticalSeekbarEnabled = true,
                showWebView = true,
                textBlocksCount = 10,
            ),
        )
    }

    @Test
    fun `vertical seekbar labels show dynamic top and fixed bottom`() {
        val labels = verticalSeekbarLabels(
            readingProgressPercent = 42,
            showScrollPercentage = true,
        )

        assertTrue(labels.first == "42")
        assertTrue(labels.second == "100")
    }

    @Test
    fun `vertical seekbar labels hidden when percentage disabled`() {
        val labels = verticalSeekbarLabels(
            readingProgressPercent = 42,
            showScrollPercentage = false,
        )

        assertTrue(labels.first == null)
        assertTrue(labels.second == null)
    }

    @Test
    fun `content padding remains invariant when reader ui toggles`() {
        val hiddenPadding = resolveReaderContentPaddingPx(
            showReaderUi = false,
            basePaddingPx = 24,
        )
        val visiblePadding = resolveReaderContentPaddingPx(
            showReaderUi = true,
            basePaddingPx = 24,
        )

        assertTrue(hiddenPadding == visiblePadding)
        assertTrue(hiddenPadding == 24)
    }

    @Test
    fun `webview top padding remains stable when reader ui toggles`() {
        val hiddenPadding = resolveWebViewPaddingTopPx(
            statusBarHeightPx = 48,
            showReaderUi = false,
            appBarHeightPx = 120,
            basePaddingPx = 4,
            maxStatusBarInsetPx = 16,
        )
        val visiblePadding = resolveWebViewPaddingTopPx(
            statusBarHeightPx = 48,
            showReaderUi = true,
            appBarHeightPx = 120,
            basePaddingPx = 4,
            maxStatusBarInsetPx = 16,
        )

        assertTrue(hiddenPadding == 20)
        assertTrue(visiblePadding == 20)
    }

    @Test
    fun `webview top padding limits oversized status insets`() {
        val padded = resolveWebViewPaddingTopPx(
            statusBarHeightPx = 120,
            showReaderUi = false,
            appBarHeightPx = 120,
            basePaddingPx = 6,
            maxStatusBarInsetPx = 16,
        )

        assertTrue(padded == 22)
    }

    @Test
    fun `webview text align keeps site alignment for default left`() {
        val alignCss = resolveWebViewTextAlignCss(
            textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.LEFT,
        )

        assertTrue(alignCss == null)
    }

    @Test
    fun `webview text align applies explicit non-default alignments`() {
        assertTrue(
            resolveWebViewTextAlignCss(
                textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.CENTER,
            ) == "center",
        )
        assertTrue(
            resolveWebViewTextAlignCss(
                textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.JUSTIFY,
            ) == "justify",
        )
        assertTrue(
            resolveWebViewTextAlignCss(
                textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.RIGHT,
            ) == "right",
        )
    }

    @Test
    fun `webview bottom padding uses system navigation inset only`() {
        val hiddenPadding = resolveWebViewPaddingBottomPx(
            navigationBarHeightPx = 32,
            showReaderUi = false,
            bottomBarHeightPx = 128,
            basePaddingPx = 4,
        )
        val visiblePadding = resolveWebViewPaddingBottomPx(
            navigationBarHeightPx = 32,
            showReaderUi = true,
            bottomBarHeightPx = 128,
            basePaddingPx = 4,
        )

        assertTrue(hiddenPadding == 36)
        assertTrue(visiblePadding == 36)
    }

    @Test
    fun `webview progress update is blocked until restore completes`() {
        assertFalse(shouldTrackWebViewProgress(shouldRestoreWebScroll = true))
        assertFalse(
            shouldDispatchWebProgressUpdate(
                shouldRestoreWebScroll = true,
                newPercent = 0,
                currentPercent = 55,
            ),
        )

        assertTrue(shouldTrackWebViewProgress(shouldRestoreWebScroll = false))
        assertTrue(
            shouldDispatchWebProgressUpdate(
                shouldRestoreWebScroll = false,
                newPercent = 56,
                currentPercent = 55,
            ),
        )
    }

    @Test
    fun `webview progress for non-scrollable content stays at start`() {
        assertTrue(resolveWebViewTotalScrollablePx(contentHeightPx = 800, viewHeightPx = 900) == 0)
        assertTrue(resolveWebViewScrollProgressPercent(scrollY = 0, totalScrollable = 0) == 0)
        assertTrue(resolveWebViewScrollProgressPercent(scrollY = 50, totalScrollable = 100) == 50)
    }

    @Test
    fun `final webview progress keeps cached value when resolved progress drops to zero`() {
        assertTrue(resolveFinalWebViewProgressPercent(resolvedPercent = 0, cachedPercent = 57) == 57)
        assertTrue(resolveFinalWebViewProgressPercent(resolvedPercent = 43, cachedPercent = 57) == 43)
        assertTrue(resolveFinalWebViewProgressPercent(resolvedPercent = null, cachedPercent = 57) == 57)
    }

    @Test
    fun `multi block native tracking marks chapter complete at scroll end`() {
        val completed = resolveNativeScrollProgressForTracking(
            firstVisibleItemIndex = 3,
            textBlocksCount = 10,
            canScrollForward = false,
        )

        assertTrue(completed.first == 9 && completed.second == 10)
    }

    @Test
    fun `single block tracking progress marks as complete only at chapter end`() {
        val inProgress = resolveNativeScrollProgressForTracking(
            firstVisibleItemIndex = 0,
            textBlocksCount = 1,
            canScrollForward = true,
        )
        val completed = resolveNativeScrollProgressForTracking(
            firstVisibleItemIndex = 0,
            textBlocksCount = 1,
            canScrollForward = false,
        )

        assertTrue(inProgress.first == 0 && inProgress.second == 2)
        assertTrue(completed.first == 1 && completed.second == 2)
    }

    @Test
    fun `system bars are hidden only when fullscreen and reader ui hidden`() {
        assertTrue(shouldHideSystemBars(fullScreenMode = true, showReaderUi = false))
        assertFalse(shouldHideSystemBars(fullScreenMode = true, showReaderUi = true))
        assertFalse(shouldHideSystemBars(fullScreenMode = false, showReaderUi = false))
    }

    @Test
    fun `fullscreen reader restores system bars on dispose`() {
        assertTrue(shouldRestoreSystemBarsOnDispose(fullScreenMode = true))
        assertTrue(shouldRestoreSystemBarsOnDispose(fullScreenMode = false))
    }

    @Test
    fun `auto-scroll speed mapping keeps bounds and round-trips`() {
        assertTrue(intervalToAutoScrollSpeed(1) in 1..100)
        assertTrue(intervalToAutoScrollSpeed(60) in 1..100)
        assertTrue(autoScrollSpeedToInterval(1) in 1..60)
        assertTrue(autoScrollSpeedToInterval(100) in 1..60)

        val speed = intervalToAutoScrollSpeed(10)
        val interval = autoScrollSpeedToInterval(speed)
        assertTrue(interval in 8..12)
    }

    @Test
    fun `page pagination splits long text and keeps order`() {
        val text = buildString {
            repeat(120) { append("Paragraph $it line of text.\n\n") }
        }

        val pages = paginateTextIntoPages(
            text = text,
            widthPx = 720,
            heightPx = 480,
            textSizePx = 42f,
            lineHeightMultiplier = 1.6f,
            typeface = null,
            textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.LEFT,
        )

        assertTrue(pages.size > 1)
        assertTrue(pages.first().contains("Paragraph 0"))
        assertTrue(pages.last().contains("Paragraph 119"))
        assertNotEquals(pages.first(), pages.last())
    }

    @Test
    fun `page paginator is skipped when page reader mode is disabled`() {
        var invocationCount = 0

        val blocks = resolvePageReaderBlocks(
            shouldPaginate = false,
            textBlocks = listOf("First", "Second"),
        ) {
            invocationCount++
            listOf("paged")
        }

        assertTrue(blocks == listOf("First", "Second"))
        assertTrue(invocationCount == 0)
    }

    @Test
    fun `vertical swipe up near chapter end opens next chapter`() {
        val result = resolveVerticalChapterSwipeAction(
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 40f,
            deltaY = -320f,
            minSwipeDistancePx = 140f,
            horizontalTolerancePx = 24f,
            gestureDurationMillis = 240L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NEXT)
    }

    @Test
    fun `vertical swipe down near chapter start opens previous chapter`() {
        val result = resolveVerticalChapterSwipeAction(
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 20f,
            deltaY = 300f,
            minSwipeDistancePx = 140f,
            horizontalTolerancePx = 24f,
            gestureDurationMillis = 260L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = false,
            wasNearChapterStartAtDown = true,
            isNearChapterEnd = false,
            isNearChapterStart = true,
        )

        assertTrue(result == VerticalChapterSwipeAction.PREVIOUS)
    }

    @Test
    fun `horizontal dominant swipe does not trigger vertical chapter switch`() {
        val result = resolveVerticalChapterSwipeAction(
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 320f,
            deltaY = -200f,
            minSwipeDistancePx = 140f,
            horizontalTolerancePx = 24f,
            gestureDurationMillis = 260L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `webview vertical swipe up near chapter end opens next chapter`() {
        val result = resolveWebViewVerticalChapterSwipeAction(
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 8f,
            deltaY = -180f,
            minSwipeDistancePx = 72f,
            horizontalTolerancePx = 20f,
            gestureDurationMillis = 240L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NEXT)
    }

    @Test
    fun `webview vertical swipe requires minimum distance`() {
        val result = resolveWebViewVerticalChapterSwipeAction(
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 2f,
            deltaY = -45f,
            minSwipeDistancePx = 72f,
            horizontalTolerancePx = 20f,
            gestureDurationMillis = 240L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `webview vertical swipe ignores horizontal dominant gesture`() {
        val result = resolveWebViewVerticalChapterSwipeAction(
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 220f,
            deltaY = -140f,
            minSwipeDistancePx = 72f,
            horizontalTolerancePx = 20f,
            gestureDurationMillis = 240L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `vertical swipe requires deliberate hold duration`() {
        val result = resolveVerticalChapterSwipeAction(
            swipeToNextChapter = true,
            swipeToPrevChapter = false,
            deltaX = 4f,
            deltaY = -260f,
            minSwipeDistancePx = 140f,
            horizontalTolerancePx = 24f,
            gestureDurationMillis = 80L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `webview vertical swipe requires starting near chapter boundary`() {
        val result = resolveWebViewVerticalChapterSwipeAction(
            swipeToNextChapter = true,
            swipeToPrevChapter = false,
            deltaX = 2f,
            deltaY = -200f,
            minSwipeDistancePx = 72f,
            horizontalTolerancePx = 20f,
            gestureDurationMillis = 220L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = false,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `css color conversion keeps rgba order for alpha colors`() {
        val color = Color(red = 1f, green = 1f, blue = 1f, alpha = 0.5f)
        val cssColor = colorToCssHex(color)

        assertTrue(cssColor == "#FFFFFF80")
    }

    @Test
    fun `webview renderer starts enabled by preference`() {
        assertTrue(
            shouldStartInWebView(
                preferWebViewRenderer = true,
                contentBlocksCount = 10,
            ),
        )
    }

    @Test
    fun `webview renderer falls back only when no parsed content`() {
        assertFalse(
            shouldStartInWebView(
                preferWebViewRenderer = false,
                contentBlocksCount = 2,
            ),
        )
        assertTrue(
            shouldStartInWebView(
                preferWebViewRenderer = false,
                contentBlocksCount = 0,
            ),
        )
    }

    @Test
    fun `reader webview keeps javascript enabled even without plugin script`() {
        assertTrue(shouldEnableJavaScriptInReaderWebView(pluginRequestsJavaScript = false))
        assertTrue(shouldEnableJavaScriptInReaderWebView(pluginRequestsJavaScript = true))
    }
}
