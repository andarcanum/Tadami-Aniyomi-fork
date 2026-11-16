package eu.kanade.presentation.reader.novel

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelBookFlipAnimationSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnSpeed
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderInteractionTest {

    @Test
    fun `e ink mode forces instant page transition style`() {
        resolveActivePageTransitionStyle(
            requestedStyle = NovelPageTransitionStyle.BOOK_FLIP,
            pageTurnRendererSupported = true,
            isEInkMode = true,
        ) shouldBe NovelPageTransitionStyle.INSTANT
    }

    @Test
    fun `e ink instant transition keeps compose pager renderer route`() {
        val activeStyle = resolveActivePageTransitionStyle(
            requestedStyle = NovelPageTransitionStyle.CURL,
            pageTurnRendererSupported = false,
            isEInkMode = true,
        )

        resolvePageReaderRendererRoute(
            usePageReader = true,
            activeStyle = activeStyle,
        ) shouldBe NovelPageReaderRendererRoute.COMPOSE_PAGER
    }

    @Test
    fun `unsupported page turn renderer still falls back to slide when not e ink`() {
        resolveActivePageTransitionStyle(
            requestedStyle = NovelPageTransitionStyle.CURL,
            pageTurnRendererSupported = false,
        ) shouldBe NovelPageTransitionStyle.SLIDE
    }

    @Test
    fun `resolveComposePagerTransitionSpec supports BOOK transition style with 3D rotation`() {
        val spec = resolveComposePagerTransitionSpec(
            style = NovelPageTransitionStyle.BOOK,
            pageOffset = 0.5f,
        )
        spec.rotationY shouldBe -90f
        spec.pivotXFraction shouldBe 0f
        spec.cameraDistance shouldBe 15f
    }

    @Test
    fun `resolveComposePagerTransitionSpec supports CURL transition style with 3D curl rotation`() {
        val spec = resolveComposePagerTransitionSpec(
            style = NovelPageTransitionStyle.CURL,
            pageOffset = 0.5f,
        )
        spec.rotationY shouldBe -90f
        spec.pivotXFraction shouldBe 1f
        spec.cameraDistance shouldBe 15f
    }

    @Test
    fun `resolveReaderProgressToPersist allows page 0 when initial position is restored`() {
        val page2Progress = eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress(index = 2, totalItems = 10)
        val page0Progress = eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress(index = 0, totalItems = 10)

        resolveReaderProgressToPersist(
            shouldPersistRead = true,
            currentIndex = 0,
            resolvedPersistedProgress = page0Progress,
            previousProgress = page2Progress,
            isInitialPositionRestored = true,
        ) shouldBe page0Progress
    }

    @Test
    fun `resolveReaderProgressToPersist drops transient page 0 when initial position is not restored`() {
        val page2Progress = eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress(index = 2, totalItems = 10)
        val page0Progress = eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress(index = 0, totalItems = 10)

        resolveReaderProgressToPersist(
            shouldPersistRead = true,
            currentIndex = 0,
            resolvedPersistedProgress = page0Progress,
            previousProgress = page2Progress,
            isInitialPositionRestored = false,
        ) shouldBe null
    }

    @Test
    fun `resolveReaderProgressToPersist allows page 0 for START handoff or null previousProgress`() {
        val page0Progress = eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress(index = 0, totalItems = 10)
        val page5Progress = eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress(index = 5, totalItems = 10)

        resolveReaderProgressToPersist(
            shouldPersistRead = true,
            currentIndex = 0,
            resolvedPersistedProgress = page0Progress,
            previousProgress = null,
            isInitialPositionRestored = false,
        ) shouldBe page0Progress

        resolveReaderProgressToPersist(
            shouldPersistRead = true,
            currentIndex = 0,
            resolvedPersistedProgress = page0Progress,
            previousProgress = page5Progress,
            isInitialPositionRestored = false,
            chapterHandoffTarget = NovelReaderPageReaderHandoffTarget.START,
        ) shouldBe page0Progress
    }

    @Test
    fun `spread columns require landscape, enough width, and the preference on`() {
        resolveNovelSpreadColumns(
            twoPageLandscapeEnabled = true,
            viewportWidthPx = 2000,
            viewportHeightPx = 1000,
            minSpreadWidthPx = 1800,
        ) shouldBe 2

        resolveNovelSpreadColumns(
            twoPageLandscapeEnabled = false,
            viewportWidthPx = 2000,
            viewportHeightPx = 1000,
            minSpreadWidthPx = 1800,
        ) shouldBe 1

        resolveNovelSpreadColumns(
            twoPageLandscapeEnabled = true,
            viewportWidthPx = 1000,
            viewportHeightPx = 2000,
            minSpreadWidthPx = 1800,
        ) shouldBe 1

        resolveNovelSpreadColumns(
            twoPageLandscapeEnabled = true,
            viewportWidthPx = 1200,
            viewportHeightPx = 1000,
            minSpreadWidthPx = 1800,
        ) shouldBe 1
    }

    @Test
    fun `spread slot count collapses pages into pairs and keeps a trailing odd page`() {
        resolveSpreadSlotCount(contentPageCount = 10, columnsPerSpread = 2) shouldBe 5
        resolveSpreadSlotCount(contentPageCount = 9, columnsPerSpread = 2) shouldBe 5
        resolveSpreadSlotCount(contentPageCount = 9, columnsPerSpread = 1) shouldBe 9
        resolveSpreadSlotCount(contentPageCount = 0, columnsPerSpread = 2) shouldBe 1
    }

    @Test
    fun `spread slot first page index and page-to-slot mapping round trip`() {
        resolveSpreadSlotFirstPageIndex(spreadSlot = 0, columnsPerSpread = 2) shouldBe 0
        resolveSpreadSlotFirstPageIndex(spreadSlot = 3, columnsPerSpread = 2) shouldBe 6

        resolveSpreadSlotForPageIndex(pageIndex = 0, columnsPerSpread = 2) shouldBe 0
        resolveSpreadSlotForPageIndex(pageIndex = 1, columnsPerSpread = 2) shouldBe 0
        resolveSpreadSlotForPageIndex(pageIndex = 2, columnsPerSpread = 2) shouldBe 1
        resolveSpreadSlotForPageIndex(pageIndex = 7, columnsPerSpread = 2) shouldBe 3

        resolveSpreadSlotForPageIndex(pageIndex = 5, columnsPerSpread = 1) shouldBe 5
    }

    @Test
    fun `spread column text width is half the screen minus both margins`() {
        resolveNovelSpreadColumnTextWidth(screenWidthPx = 1200, horizontalPaddingPx = 32, columns = 2) shouldBe 568
        resolveNovelSpreadColumnTextWidth(screenWidthPx = 1200, horizontalPaddingPx = 32, columns = 1) shouldBe 1168
        resolveNovelSpreadColumnTextWidth(screenWidthPx = 10, horizontalPaddingPx = 32, columns = 2) shouldBe 1
        resolveNovelSpreadColumnTextWidth(screenWidthPx = 1000, horizontalPaddingPx = 0, columns = 2) shouldBe 500
    }

    @Test
    fun `compose pager seekbar in spread mode reports the first page of the slot`() {
        // pagerCurrentPage = slot index 2 -> real pages 4..5 -> seekbar shows page 4 of 10
        resolveReaderVerticalSeekbarValue(
            showWebView = false,
            webProgressPercent = 0,
            usePageReader = true,
            pageReaderRendererRoute = NovelPageReaderRendererRoute.COMPOSE_PAGER,
            pagerCurrentPage = 2,
            pageTurnCurrentPage = 0,
            composePagerContentPageCount = 5,
            composePagerHasPreviousChapter = false,
            seekbarItemsCount = 10,
            readingProgressPercent = 50,
            spreadColumns = 2,
        ) shouldBe 4f / 9f
    }

    @Test
    fun `page turn seekbar in spread mode reports the real page index`() {
        // pageTurnCurrentPage is already a real page; the spread must not shift it
        resolveReaderVerticalSeekbarValue(
            showWebView = false,
            webProgressPercent = 0,
            usePageReader = true,
            pageReaderRendererRoute = NovelPageReaderRendererRoute.PAGE_TURN_RENDERER,
            pagerCurrentPage = 0,
            pageTurnCurrentPage = 5,
            composePagerContentPageCount = 5,
            composePagerHasPreviousChapter = false,
            pageTurnContentPageCount = 10,
            seekbarItemsCount = 10,
            readingProgressPercent = 50,
            spreadColumns = 2,
        ) shouldBe 5f / 9f
    }

    @Test
    fun `spread column width reserves cutout room on the short edges`() {
        // Cutout-on: less text width (cutout px subtracted before the column split)
        resolveNovelSpreadColumnTextWidth(
            screenWidthPx = 1200,
            horizontalPaddingPx = 32,
            columns = 2,
            cutoutLeftPx = 48,
            cutoutRightPx = 48,
        ) shouldBe 520

        // Cutout-off: full width exactly as before
        resolveNovelSpreadColumnTextWidth(
            screenWidthPx = 1200,
            horizontalPaddingPx = 32,
            columns = 2,
        ) shouldBe 568

        // Single-page mode ignores cutout in the same gross-split formula
        resolveNovelSpreadColumnTextWidth(
            screenWidthPx = 1200,
            horizontalPaddingPx = 32,
            columns = 1,
            cutoutLeftPx = 48,
            cutoutRightPx = 48,
        ) shouldBe 1072
    }

    @Test
    fun `compact spread vertical padding keeps margin, anti-clip inset and book bottom`() {
        // 2x content padding (top+bottom) + pageFitSafety + bookBottomInset
        resolveNovelSpreadPageVerticalPadding(contentPaddingPx = 16, pageFitSafetyPx = 8) shouldBe 40
        resolveNovelSpreadPageVerticalPadding(
            contentPaddingPx = 16,
            pageFitSafetyPx = 8,
            bookBottomInsetPx = 32,
        ) shouldBe 72
        resolveNovelSpreadPageVerticalPadding(
            contentPaddingPx = 0,
            pageFitSafetyPx = 8,
            bookBottomInsetPx = 0,
        ) shouldBe 8
    }

    @Test
    fun `progress persistence never overwrites saved offset at index zero before restore even when read`() {
        resolveReaderProgressToPersist(
            shouldPersistRead = false,
            currentIndex = 0,
            resolvedPersistedProgress = 0L,
            previousProgress = 42L,
            isInitialPositionRestored = false,
            chapterHandoffTarget = NovelReaderPageReaderHandoffTarget.SAVED,
        ) shouldBe null

        resolveReaderProgressToPersist(
            shouldPersistRead = true,
            currentIndex = 0,
            resolvedPersistedProgress = 0L,
            previousProgress = 42L,
            isInitialPositionRestored = false,
            chapterHandoffTarget = NovelReaderPageReaderHandoffTarget.SAVED,
        ) shouldBe null

        resolveReaderProgressToPersist(
            shouldPersistRead = false,
            currentIndex = 0,
            resolvedPersistedProgress = 0L,
            previousProgress = 42L,
            isInitialPositionRestored = true,
            chapterHandoffTarget = NovelReaderPageReaderHandoffTarget.SAVED,
        ) shouldBe 0L

        val nativeStart = eu.kanade.tachiyomi.ui.reader.novel.encodeNativeScrollProgress(index = 0, offsetPx = 0)
        val nativeScrolledInZero = eu.kanade.tachiyomi.ui.reader.novel.encodeNativeScrollProgress(
            index = 0,
            offsetPx = 420,
        )
        resolveReaderProgressToPersist(
            shouldPersistRead = false,
            currentIndex = 0,
            resolvedPersistedProgress = nativeScrolledInZero,
            previousProgress = nativeStart,
            isInitialPositionRestored = false,
            chapterHandoffTarget = NovelReaderPageReaderHandoffTarget.SAVED,
        ) shouldBe nativeScrolledInZero

        resolveReaderProgressToPersist(
            shouldPersistRead = false,
            currentIndex = 0,
            resolvedPersistedProgress = nativeStart,
            previousProgress = nativeScrolledInZero,
            isInitialPositionRestored = false,
            chapterHandoffTarget = NovelReaderPageReaderHandoffTarget.SAVED,
        ) shouldBe null
    }

    @Test
    fun `page reader anchor restores exact block offset and image across reflow`() {
        val portraitPages = listOf(
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(
                        sourceBlockIndex = 0,
                        text = "First page ",
                        sourceTextStart = 0,
                        sourceTextEndExclusive = 11,
                        spacingBeforePx = 0,
                        firstLineIndentEm = null,
                    ),
                ),
                pageIndex = 0,
            ),
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(
                        sourceBlockIndex = 0,
                        text = "second slice",
                        sourceTextStart = 11,
                        sourceTextEndExclusive = 23,
                        spacingBeforePx = 0,
                        firstLineIndentEm = null,
                    ),
                ),
                pageIndex = 1,
            ),
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Image(
                        imageUrl = "https://example.org/illustration.jpg",
                        contentDescription = null,
                    ),
                ),
                pageIndex = 2,
            ),
        )

        val textAnchor = capturePageReaderAnchor(portraitPages, pageIndex = 1)
        val imageAnchor = capturePageReaderAnchor(portraitPages, pageIndex = 2)

        val landscapePages = listOf(
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(
                        sourceBlockIndex = 0,
                        text = "First page second slice",
                        sourceTextStart = 0,
                        sourceTextEndExclusive = 23,
                        spacingBeforePx = 0,
                        firstLineIndentEm = null,
                    ),
                ),
                pageIndex = 0,
            ),
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Image(
                        imageUrl = "https://example.org/illustration.jpg",
                        contentDescription = null,
                    ),
                ),
                pageIndex = 1,
            ),
        )

        resolveAnchoredPageReaderPageIndex(textAnchor, landscapePages) shouldBe 0
        resolveAnchoredPageReaderPageIndex(imageAnchor, landscapePages) shouldBe 1
    }

    @Test
    fun `native scroll viewport anchor scales item scroll offset across width changes`() {
        val fraction = resolveNativeScrollItemFraction(scrollOffsetPx = 150, itemHeightPx = 300)
        fraction shouldBe 0.5f

        resolveAnchoredNativeScrollOffsetPx(withinItemFraction = fraction, newItemHeightPx = 160) shouldBe 80
        resolveNativeScrollItemFraction(scrollOffsetPx = 50, itemHeightPx = 0) shouldBe 0f
        resolveAnchoredNativeScrollOffsetPx(withinItemFraction = 0.5f, newItemHeightPx = 0) shouldBe 0
    }

    @Test
    fun `horizontal safe insets take max of cutout and side nav bar in dp`() {
        val insets = resolveNovelHorizontalSafeInsetsDp(
            cutoutLeftPx = 90,
            cutoutRightPx = 0,
            navBarLeftPx = 0,
            navBarRightPx = 144,
            densityScale = 3f,
        )

        insets shouldBe NovelHorizontalSafeInsetsDp(left = 30.dp, right = 48.dp)
    }

    @Test
    fun `reflow anchor preservation keeps exact block offset across portrait to landscape round trip`() {
        val portraitPages = listOf(
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(
                        sourceBlockIndex = 0,
                        text = "Opening",
                        sourceTextStart = 0,
                        sourceTextEndExclusive = 7,
                        spacingBeforePx = 0,
                        firstLineIndentEm = null,
                    ),
                ),
                pageIndex = 0,
            ),
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(
                        sourceBlockIndex = 1,
                        text = "Middle",
                        sourceTextStart = 0,
                        sourceTextEndExclusive = 6,
                        spacingBeforePx = 0,
                        firstLineIndentEm = null,
                    ),
                ),
                pageIndex = 1,
            ),
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(
                        sourceBlockIndex = 2,
                        text = "Target paragraph",
                        sourceTextStart = 0,
                        sourceTextEndExclusive = 16,
                        spacingBeforePx = 0,
                        firstLineIndentEm = null,
                    ),
                ),
                pageIndex = 2,
            ),
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(
                        sourceBlockIndex = 3,
                        text = "Later paragraph",
                        sourceTextStart = 0,
                        sourceTextEndExclusive = 15,
                        spacingBeforePx = 0,
                        firstLineIndentEm = null,
                    ),
                ),
                pageIndex = 3,
            ),
        )
        val landscapePages = listOf(
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(0, "Opening", 0, 7, 0, null),
                    NovelPageContentBlock.Plain(1, "Middle", 0, 6, 0, null),
                    NovelPageContentBlock.Plain(2, "Target paragraph", 0, 16, 0, null),
                ),
                pageIndex = 0,
            ),
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(3, "Later paragraph", 0, 15, 0, null),
                ),
                pageIndex = 1,
            ),
        )

        var activeAnchor: NovelPageReaderAnchor? = capturePageReaderAnchor(portraitPages, pageIndex = 2)
        val landscapeTarget = resolveAnchoredPageReaderPageIndex(activeAnchor!!, landscapePages)
        landscapeTarget shouldBe 0

        // Reflowing into wider Landscape page 0 (which starts at block 0) must keep block 2's anchor.
        activeAnchor = resolvePageReaderAnchorAfterReflow(activeAnchor, landscapePages, landscapeTarget)
        activeAnchor = resolveNextPageReaderAnchor(
            existingAnchor = activeAnchor,
            contentPages = landscapePages,
            settledPageIndex = 0,
            spreadColumns = 2,
        )

        // Rotating back to Portrait resolves back to Portrait page 2 instead of rewinding to page 0.
        resolveAnchoredPageReaderPageIndex(activeAnchor!!, portraitPages) shouldBe 2

        // Turning a spread/page forward in Landscape updates the anchor to the new spread's block.
        activeAnchor = resolveNextPageReaderAnchor(
            existingAnchor = activeAnchor,
            contentPages = landscapePages,
            settledPageIndex = 1,
            spreadColumns = 1,
        )
        resolveAnchoredPageReaderPageIndex(activeAnchor!!, portraitPages) shouldBe 3
    }

    @Test
    fun `effective viewport size swaps stale measured bounds immediately on rotation`() {
        // Before first layout, fall back to configuration bounds.
        resolveEffectivePageReaderViewportSize(
            measuredWidthPx = 0,
            measuredHeightPx = 0,
            configWidthPx = 1260,
            configHeightPx = 2619,
        ) shouldBe IntSize(1260, 2619)

        // Same orientation keeps the exact measured full-screen viewport (with system bars).
        resolveEffectivePageReaderViewportSize(
            measuredWidthPx = 1260,
            measuredHeightPx = 2800,
            configWidthPx = 1260,
            configHeightPx = 2619,
        ) shouldBe IntSize(1260, 2800)

        // Rotation configuration arrives before `.onSizeChanged`: immediately swaps to landscape.
        resolveEffectivePageReaderViewportSize(
            measuredWidthPx = 1260,
            measuredHeightPx = 2800,
            configWidthPx = 2619,
            configHeightPx = 1260,
        ) shouldBe IntSize(2800, 1260)
    }

    @Test
    fun `page turn synced slot derives directly from synced content page index`() {
        resolvePageTurnRendererSyncedSlot(
            syncedContentPageIndex = 5,
            contentPageCount = 12,
            spreadColumns = 1,
        ) shouldBe 5

        resolvePageTurnRendererSyncedSlot(
            syncedContentPageIndex = 5,
            contentPageCount = 12,
            spreadColumns = 2,
        ) shouldBe 2

        resolvePageTurnRendererSyncedSlot(
            syncedContentPageIndex = null,
            contentPageCount = 12,
            spreadColumns = 2,
        ) shouldBe 0
    }

    @Test
    fun `compose pager boundary pages stay disabled for instant style and seamless mode`() {
        resolveComposePagerBoundaryPagesEnabled(
            seamlessChapterTransitionEnabled = false,
            activePageTransitionStyle = NovelPageTransitionStyle.SLIDE,
        ) shouldBe true
        resolveComposePagerBoundaryPagesEnabled(
            seamlessChapterTransitionEnabled = false,
            activePageTransitionStyle = NovelPageTransitionStyle.INSTANT,
        ) shouldBe false
        resolveComposePagerBoundaryPagesEnabled(
            seamlessChapterTransitionEnabled = true,
            activePageTransitionStyle = NovelPageTransitionStyle.SLIDE,
        ) shouldBe false
    }

    @Test
    fun `compose pager page turn animation duration respects style and speed`() {
        resolveComposePagerPageTurnAnimationDurationMillis(
            transitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
            bookFlipAnimationSpeed = NovelBookFlipAnimationSpeed.FAST,
            pageTurnSpeed = NovelPageTurnSpeed.NORMAL,
        ) shouldBe 500
        resolveComposePagerPageTurnAnimationDurationMillis(
            transitionStyle = NovelPageTransitionStyle.SLIDE,
            bookFlipAnimationSpeed = NovelBookFlipAnimationSpeed.SLOW,
            pageTurnSpeed = NovelPageTurnSpeed.FAST,
        ) shouldBe 300
        resolveComposePagerPageTurnAnimationDurationMillis(
            transitionStyle = NovelPageTransitionStyle.DEPTH,
            bookFlipAnimationSpeed = NovelBookFlipAnimationSpeed.SLOW,
            pageTurnSpeed = NovelPageTurnSpeed.SLOW,
        ) shouldBe 680
        resolveComposePagerPageTurnAnimationDurationMillis(
            transitionStyle = NovelPageTransitionStyle.INSTANT,
            bookFlipAnimationSpeed = NovelBookFlipAnimationSpeed.SLOW,
            pageTurnSpeed = NovelPageTurnSpeed.FAST,
        ) shouldBe null
    }
}
