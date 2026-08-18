package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
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
}
