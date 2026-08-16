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
}
