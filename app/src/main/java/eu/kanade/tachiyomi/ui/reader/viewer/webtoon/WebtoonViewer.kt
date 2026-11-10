package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.PointF
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.app.ActivityCompat
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ChapterScrollProgress
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPreloadManager
import eu.kanade.tachiyomi.ui.reader.WebtoonScrollProgress
import eu.kanade.tachiyomi.ui.reader.evaluateWebtoonRestoreSettle
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.resolveWebtoonRestoreOffsetPx
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import eu.kanade.tachiyomi.ui.reader.viewer.autoscroll.WebtoonAutoScrollManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Implementation of a [Viewer] to display pages with a [RecyclerView].
 */
class WebtoonViewer(val activity: ReaderActivity, val hasPageGaps: Boolean = false) : Viewer {

    val downloadManager: MangaDownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * Recycler view used by this viewer.
     */
    val recycler = WebtoonRecyclerView(activity)

    /**
     * Frame containing the recycler view.
     */
    private val frame = WebtoonFrame(activity)

    /**
     * Distance to scroll when the user taps on one side of the recycler view.
     */
    private val scrollDistance = activity.resources.displayMetrics.heightPixels * 3 / 4

    /**
     * Layout manager of the recycler view.
     */
    private val layoutManager = WebtoonLayoutManager(activity, scrollDistance)

    /**
     * Configuration used by this viewer, like allow taps, or crop image borders.
     */
    val config = WebtoonConfig(scope)

    /**
     * Adapter of the recycler view.
     */
    private val adapter = WebtoonAdapter(this)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null
    private var lastCompletionPage: ReaderPage? = null
    private var boundarySelectionRetry: OneShotPreDrawListener? = null

    /**
     * Until this uptime timestamp a programmatic chapter switch is still settling: relayouts caused
     * by decoding page heights can re-anchor the RecyclerView onto the retained neighbour-chapter
     * pages, and reports from there must re-anchor back instead of bouncing the switch (see
     * [onScrolled]). Cleared when the new chapter's first page is decoded, the user scrolls, or the
     * cap expires.
     */
    private var switchSettleUntilMs = 0L
    private var switchSettleReanchorMs = 0L

    /**
     * Scroll compensation queued by holders when the height of the page at the top of the viewport
     * changes; applied in [WebtoonRecyclerView.dispatchDraw], i.e. still inside the same frame.
     */
    private var pendingHeightCompensationPx = 0

    /**
     * True while that compensation moves the scroll: the scroll listener must not treat it as a
     * user scroll (no menu hiding, no progress report - the visible position did not change).
     */
    private var compensatingLayoutChange = false
    private var pendingRelativeRestore: PendingRelativeRestore? = null
    private val pendingRelativeRestoreRunnable = object : Runnable {
        override fun run() {
            val pending = pendingRelativeRestore ?: run {
                stopPendingRelativeRestoreLoop()
                return
            }
            if (SystemClock.uptimeMillis() - pending.startedAtUptimeMs >= MAX_PENDING_RELATIVE_RESTORE_DURATION_MS) {
                pendingRelativeRestore = null
                stopPendingRelativeRestoreLoop()
                return
            }
            applyPendingRelativeRestore(clearWhenApplied = false)
            if (pendingRelativeRestore == null) {
                stopPendingRelativeRestoreLoop()
                return
            }
            if (pendingRelativeRestore !== pending) {
                // Pending target changed while retrying, restart from scratch for the new target.
                // Reference check on purpose: PendingRelativeRestore has mutable fields, so a
                // structural comparison can miss a replacement target with equal field values.
                startPendingRelativeRestoreLoop()
                return
            }
            recycler.postOnAnimation(this)
        }
    }

    /**
     * Auto-scroll manager for automatic scrolling functionality.
     */
    val autoScrollManager: WebtoonAutoScrollManager by lazy {
        WebtoonAutoScrollManager(this)
    }

    private val threshold: Int =
        Injekt.get<ReaderPreferences>()
            .readerHideThreshold()
            .get()
            .threshold

    init {
        recycler.setItemViewCacheSize(RECYCLER_VIEW_CACHE_SIZE)
        recycler.isVisible = false // Don't let the recycler layout yet
        recycler.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.isFocusable = false
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.onBeforeDrawChildren = { applyPendingHeightCompensation() }
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                // Progress tracking is throttled: computing the scroll progress and
                // scheduling its persistence on every scrolled frame causes dropped
                // frames on high refresh rate displays (90/120Hz+). The exact final
                // position is always flushed once scrolling settles (see
                // onScrollStateChanged below), so throttling never loses precision.
                private var lastProgressUpdateMs = 0L

                // True when a drag starts with the "no more chapters" transition already
                // visible — the webtoon equivalent of swiping into the void.
                private var scrollStartedAtEndOfChapter = false

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    // A position-preserving compensation is not a user scroll: the visible content
                    // did not move, so the bookkeeping (and menu hiding) is skipped for it.
                    if (compensatingLayoutChange) return
                    onScrolled()
                    applyPendingRelativeRestore(clearWhenApplied = false)
                    val now = SystemClock.uptimeMillis()
                    if (now - lastProgressUpdateMs >= SCROLL_PROGRESS_UPDATE_INTERVAL_MS) {
                        lastProgressUpdateMs = now
                        getCurrentScrollProgress()?.let(activity.viewModel::onWebtoonScrollProgressChanged)
                    }

                    if ((dy > threshold || dy < -threshold) && activity.viewModel.state.value.menuVisible) {
                        // B-M1: navigator-slider jumps (moveToPage -> scrollToPositionWithOffset)
                        // produce a huge dy and used to hide the menu in the middle of the slider
                        // gesture. Consume the slider mark for this programmatic scroll burst,
                        // mirroring the pager's onPageSelected guard.
                        if (activity.isScrollingThroughPages) {
                            activity.consumeScrollingThroughPages()
                        } else {
                            activity.hideMenu()
                        }
                    }

                    if (dy < 0) {
                        val firstIndex = layoutManager.findFirstVisibleItemPosition()
                        val firstItem = adapter.items.getOrNull(firstIndex)
                        if (firstItem is ChapterTransition.Prev && firstItem.to != null) {
                            activity.requestPreloadChapter(firstItem.to)
                        }
                    }

                    if (dy > 0) {
                        val lastIndex = layoutManager.findLastEndVisibleItemPosition()
                        val lastItem = adapter.items.getOrNull(lastIndex)
                        if (lastItem is ChapterTransition.Next && lastItem.to == null) {
                            activity.showMenu()
                        }
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            // The user took over: a programmatic switch is no longer settling.
                            switchSettleUntilMs = 0L
                            // User took over the scroll: stop re-anchoring every frame against
                            // the drag ("rubber band") and let the pending restore go.
                            if (pendingRelativeRestore != null) {
                                pendingRelativeRestore = null
                                stopPendingRelativeRestoreLoop()
                            }
                            // Only a drag that starts with the end-of-manga transition already
                            // visible counts as a swipe into the void; landing on the page
                            // must not trigger the meltdown escalation.
                            val lastIndex = layoutManager.findLastEndVisibleItemPosition()
                            val lastItem = adapter.items.getOrNull(lastIndex)
                            scrollStartedAtEndOfChapter =
                                lastItem is ChapterTransition.Next && lastItem.to == null
                        }
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            if (scrollStartedAtEndOfChapter) {
                                val lastIndex = layoutManager.findLastEndVisibleItemPosition()
                                val lastItem = adapter.items.getOrNull(lastIndex)
                                if (lastItem is ChapterTransition.Next && lastItem.to == null) {
                                    activity.onMeltdownTransitionActivated()
                                }
                            }
                            scrollStartedAtEndOfChapter = false
                            // Persist the exact resting position once scrolling settles
                            lastProgressUpdateMs = SystemClock.uptimeMillis()
                            getCurrentScrollProgress()?.let(activity.viewModel::onWebtoonScrollProgressChanged)
                        }
                    }
                }
            },
        )
        recycler.tapListener = { event ->
            val viewPosition = IntArray(2)
            recycler.getLocationOnScreen(viewPosition)
            // rawX/rawY are screen coordinates: subtracting the on-screen position already gives
            // view-relative points. The old "+ locationInWindow" term double-counted the window
            // offset whenever the recycler was not flush with the window origin.
            val pos = PointF(
                (event.rawX - viewPosition[0]) / recycler.width,
                (event.rawY - viewPosition[1]) / recycler.originalHeight,
            )
            when (config.navigator.getAction(pos)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT, NavigationRegion.RIGHT -> scrollDown()
                NavigationRegion.PREV, NavigationRegion.LEFT -> scrollUp()
                NavigationRegion.NONE -> Unit
            }
        }
        recycler.longTapListener = f@{ event ->
            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val child = recycler.findChildViewUnder(event.x, event.y)
                if (child != null) {
                    val position = recycler.getChildAdapterPosition(child)
                    val item = adapter.items.getOrNull(position)
                    if (item is ReaderPage) {
                        activity.onPageLongTap(item)
                        return@f true
                    }
                }
            }
            false
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.transitionPropertyChangedListener = {
            activity.viewModel.state.value.viewerChapters?.let(::setChapters)
        }

        config.themeChangedListener = {
            ActivityCompat.recreate(activity)
        }

        frame.doubleTapZoom = config.doubleTapZoom
        frame.zoomOutDisabled = config.zoomOutDisabled
        frame.enablePinchToZoom = config.enablePinchToZoom

        config.doubleTapZoomChangedListener = {
            frame.doubleTapZoom = it
        }

        config.zoomPropertyChangedListener = {
            frame.zoomOutDisabled = it
            frame.enablePinchToZoom = config.enablePinchToZoom
        }

        config.navigationModeChangedListener = {
            activity.binding.navigationOverlay.setNavigation(config.navigator, config.navigationOverlayOnStart)
        }

        // Monitor zoom state and pause auto-scroll when zoomed in
        scope.launch {
            autoScrollManager.state.collectLatest { state ->
                if (state.isActive && !state.isPaused && isZoomedIn()) {
                    pauseAutoScroll()
                }
            }
        }

        // A-M10: zooming back OUT resumes the auto-scroll - the zoom monitor above paused it and
        // resumeAutoScroll() had zero call sites in the webtoon branch, so a zoom froze the
        // auto-scroll for the rest of the chapter. Covers pinch and double-tap zoom-out (both
        // funnel through WebtoonRecyclerView.setScaleRate). resume() is a no-op when not paused.
        recycler.onZoomScaleChanged = { scale ->
            if (scale <= 1f) resumeAutoScroll()
        }

        // A-M10: sync the VM state when the manager stops itself at the end of the content - the
        // FAB/menu kept showing "playing" over a stopped scroller, and the next FAB press went to
        // "pause" instead of start.
        autoScrollManager.onReachedEnd = {
            activity.viewModel.pauseAutoScroll()
        }

        frame.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        val current = currentPage
        if (current == null) {
            // Initial opening. Allow preload if the first page is already in the preload range.
            return true
        }

        val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
        val nextChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as? ReaderPage)?.chapter

        // Allow preload for
        // 1. Going between pages of same chapter
        // 2. Next chapter page
        return when (page.chapter) {
            (current as? ReaderPage)?.chapter -> true
            nextChapter -> true
            else -> false
        }
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return frame
    }

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    override fun destroy() {
        super.destroy()
        stopPendingRelativeRestoreLoop()
        boundarySelectionRetry?.removeListener()
        boundarySelectionRetry = null
        lastCompletionPage = null
        autoScrollManager.destroy()
        // Detaching the recycler from the window does NOT call onViewRecycled; without this the
        // bound holders of a swapped-out viewer keep collecting page status flows (duplicate
        // decodes) and hold their frames/bitmaps for the rest of the activity's life.
        recycler.adapter = null
        scope.cancel()
    }

    /**
     * Starts auto-scroll with the given [speed].
     *
     * @param speed The scroll speed (1-100). If null, uses the current speed.
     */
    fun startAutoScroll(speed: Int? = null) {
        autoScrollManager.start(speed)
    }

    /**
     * Stops auto-scroll completely.
     */
    fun stopAutoScroll() {
        autoScrollManager.stop()
    }

    /**
     * Pauses auto-scroll temporarily.
     */
    fun pauseAutoScroll() {
        autoScrollManager.pause()
    }

    /**
     * Resumes auto-scroll from a paused state.
     */
    fun resumeAutoScroll() {
        autoScrollManager.resume()
    }

    /**
     * Sets the auto-scroll speed.
     *
     * @param speed The new scroll speed (1-100).
     */
    fun setAutoScrollSpeed(speed: Int) {
        autoScrollManager.setSpeed(speed)
    }

    /**
     * Sets a cooldown period during which auto-scroll gradually slows down.
     * Used after touch events so the user can interact without scroll interference.
     *
     * @param delayMs Duration of the cooldown in milliseconds.
     */
    fun setAutoScrollCooldown(delayMs: Long) {
        autoScrollManager.setCooldown(delayMs)
    }

    /**
     * Checks if the webtoon is currently zoomed in.
     *
     * @return true if zoomed in (scale > 1), false otherwise.
     */
    fun isZoomedIn(): Boolean {
        return recycler.scaleX > 1f
    }

    private fun checkAndPreload(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        val inPreloadRange = pages.size - page.number < ReaderPreloadManager.nextChapterPreloadThreshold
        if (config.preloadNextChapter && inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
            val transitionChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as? ReaderPage)?.chapter
            if (transitionChapter != null) {
                logcat { "Requesting to preload chapter ${transitionChapter.chapter.chapter_number}" }
                activity.requestPreloadChapter(transitionChapter)
            }
        }
    }

    /**
     * Called from the RecyclerView listener when a [page] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onPageSelected(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onPageSelected: ${page.number}/${pages.size}" }
        lastCompletionPage = page.takeIf {
            it.status == Page.State.READY && it === pages.lastOrNull()
        }
        activity.onPageSelected(page)

        checkAndPreload(page, allowPreload)
    }

    /**
     * Called from the RecyclerView listener when a [transition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        } else if (transition is ChapterTransition.Next) {
            // End-of-manga transition became active: reveal the pending finale plate (if any).
            activity.viewModel.revealPendingFinale()
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        // Setting controls the info screen display. Do not force it based on previously being on a transition item.
        val forceTransition = config.alwaysShowChapterTransition
        // WEBTOON-ARROWS-2: a toolbar/jump switch must be detected BEFORE the adapter re-centers.
        // isLoadingAdjacentChapter is true exactly while ReaderViewModel.loadAdjacent runs, and
        // this method executes inline inside its state update - gesture-driven switches
        // (loadNewChapter) and preload refreshes arrive with the flag false.
        val isProgrammaticSwitch = adapter.currentChapter != chapters.currChapter &&
            activity.viewModel.state.value.isLoadingAdjacentChapter
        if (isProgrammaticSwitch) {
            switchSettleUntilMs = SystemClock.uptimeMillis() + PROGRAMMATIC_SWITCH_SETTLE_CAP_MS
            switchSettleReanchorMs = 0L
        }
        adapter.setChapters(chapters, forceTransition)

        if (recycler.isGone) {
            logcat { "Recycler first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            recycler.isVisible = true
        } else {
            if (isProgrammaticSwitch) {
                // WEBTOON-ARROWS-2: the toolbar switch used to rely on ReaderActivity's
                // moveToPageIndex(0) running AFTER this rebuild - but a layout frame can slip
                // between the two (vsync traversal outruns the queued coroutine resumption).
                // In that frame RecyclerView re-anchored to a RETAINED page of the OLD chapter
                // (the 2-page window the adapter keeps around curr), and the resulting - fully
                // layout-consistent - report bounced the VM straight back via loadNewChapter.
                // Logcat proof: "Loading adjacent X" -> onPageSelected(4/5 of OLD) ->
                // "Setting OLD as active". Anchor to the new chapter's first page in the SAME
                // main-thread block as the rebuild: scrollToPositionWithOffset registers the
                // pending position before the first post-rebuild layout, so that single layout
                // already sits on the new chapter and every scroll report is consistent.
                val pages = chapters.currChapter.pages
                if (pages != null) {
                    moveToPage(pages[0])
                }
            }
            // WEBTOON-ARROWS (H1): was `recycler.post { onScrolled() }` - the posted runnable
            // ran BEFORE the pending re-layout, so findLastEndVisibleItemPosition() returned a
            // position from the OLD layout which was then indexed into the NEW adapter items.
            // doOnLayout defers the report until after the layout pass that also applies the
            // pending scroll position, so the reported page matches what the user sees.
            recycler.doOnLayout { onScrolled() }
        }

        val page = currentPage as? ReaderPage
        if (page != null && page.chapter == adapter.currentChapter) {
            checkAndPreload(page, allowPreload = true)
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        val position = adapter.items.indexOf(page)
        if (position != -1) {
            val shouldRestore = page.chapter.requestedPage == page.index
            val ratioPpm = if (shouldRestore) page.chapter.requestedPageOffsetRatioPpm else null
            val restoreOffset = if (ratioPpm == null && shouldRestore) {
                page.chapter.requestedPageOffset.coerceAtLeast(0)
            } else {
                0
            }

            layoutManager.scrollToPositionWithOffset(position, -restoreOffset)
            if (ratioPpm != null) {
                pendingRelativeRestore = PendingRelativeRestore(
                    adapterPosition = position,
                    chapterId = page.chapter.chapter.id,
                    pageIndex = page.index,
                    ratioPpm = ratioPpm,
                )
                startPendingRelativeRestoreLoop()
            } else {
                pendingRelativeRestore = null
                stopPendingRelativeRestoreLoop()
                page.chapter.requestedPageOffset = 0
                page.chapter.requestedPageOffsetRatioPpm = null
            }
            if (layoutManager.findLastEndVisibleItemPosition() == -1) {
                onScrolled(pos = position)
            }
        } else {
            logcat { "Page $page not found in adapter" }
        }
    }

    private fun retryBoundarySelectionAfterLayout() {
        if (currentPage !is ChapterTransition.Next) return
        boundarySelectionRetry?.removeListener()
        // Image decode can resize the final page. Re-check the boundary only AFTER that layout,
        // never against the old placeholder geometry in a pre-traversal animation callback.
        boundarySelectionRetry = recycler.doOnPreDraw {
            boundarySelectionRetry = null
            onScrolled()
        }
        recycler.postInvalidateOnAnimation()
    }

    internal fun onPageImageReady(page: ReaderPage) {
        if (page === adapter.currentChapter?.pages?.lastOrNull()) {
            retryBoundarySelectionAfterLayout()
        }
        if (switchSettleUntilMs != 0L && page.index == 0 && page.chapter == adapter.currentChapter) {
            // The new chapter's first page has its final height: the switch is settled.
            switchSettleUntilMs = 0L
        }
        val pending = pendingRelativeRestore ?: return
        if (pending.pageIndex != page.index) return
        if (pending.chapterId != null && pending.chapterId != page.chapter.chapter.id) return
        pending.imageDecoded = true
        startPendingRelativeRestoreLoop()
    }

    private fun applyPendingRelativeRestore(clearWhenApplied: Boolean): Boolean {
        val pending = pendingRelativeRestore ?: return false
        val (adapterPosition, item) = resolvePendingRestoreTarget(pending) ?: return false

        // The target page failed: waiting for READY/decoded frames would pin the user to the
        // restore position until the 30s timeout with no way to scroll away. Give up instead.
        if (item.status == Page.State.ERROR) {
            pendingRelativeRestore = null
            stopPendingRelativeRestoreLoop()
            return true
        }

        val view = layoutManager.findViewByPosition(adapterPosition) ?: return false
        val pageHeightPx = view.height.takeIf { it > 0 } ?: return false

        val resolvedOffsetPx = resolveWebtoonRestoreOffsetPx(
            progress = ChapterScrollProgress(
                index = pending.pageIndex,
                offsetPx = 0,
                offsetRatioPpm = pending.ratioPpm,
            ),
            currentPageHeightPx = pageHeightPx,
        )
        val currentOffsetPx = (-view.top).coerceAtLeast(0)
        val settle = evaluateWebtoonRestoreSettle(
            currentOffsetPx = currentOffsetPx,
            targetOffsetPx = resolvedOffsetPx,
            currentPageHeightPx = pageHeightPx,
            previousPageHeightPx = pending.lastMeasuredPageHeightPx,
            previousStableHeightFrames = pending.stableHeightFrames,
            isPageReady = item.status == Page.State.READY,
            imageDecoded = pending.imageDecoded,
            previousReadyFrames = pending.readyFrames,
            minStableHeightFrames = MIN_STABLE_HEIGHT_FRAMES,
            offsetTolerancePx = RESTORE_SETTLE_TOLERANCE_PX,
            minReadyFramesFallback = MIN_READY_STATE_FRAMES_FALLBACK,
        )
        pending.stableHeightFrames = settle.stableHeightFrames
        pending.readyFrames = settle.readyFrames
        pending.lastMeasuredPageHeightPx = pageHeightPx

        if (!settle.settled) {
            layoutManager.scrollToPositionWithOffset(adapterPosition, -resolvedOffsetPx)
        }

        val shouldClear = clearWhenApplied || settle.canClearPending
        if (shouldClear) {
            pendingRelativeRestore = null
            stopPendingRelativeRestoreLoop()
            item.chapter.requestedPageOffset = resolvedOffsetPx
            // Keep the ratio on a settled restore: unlike resolvedOffsetPx (valid only for the
            // CURRENT page geometry), it is orientation-independent. Nulling it here made the
            // next rotation restore from px captured in the previous orientation - ~2x off with
            // fit-width scaling, which overshot the page (or the whole chapter) and read as
            // "progress reset". moveToPage prefers the ratio whenever it is set.
            item.chapter.requestedPageOffsetRatioPpm = pending.ratioPpm
            retryBoundarySelectionAfterLayout()
        }
        return true
    }

    private fun startPendingRelativeRestoreLoop() {
        if (pendingRelativeRestore == null) return
        stopPendingRelativeRestoreLoop()
        recycler.post(pendingRelativeRestoreRunnable)
    }

    private fun stopPendingRelativeRestoreLoop() {
        recycler.removeCallbacks(pendingRelativeRestoreRunnable)
    }

    private fun resolvePendingRestoreTarget(pending: PendingRelativeRestore): Pair<Int, ReaderPage>? {
        val currentAtPosition = adapter.items.getOrNull(pending.adapterPosition) as? ReaderPage
        if (currentAtPosition != null && matchesPendingRestoreTarget(currentAtPosition, pending)) {
            return pending.adapterPosition to currentAtPosition
        }

        val resolvedPosition = adapter.items.indexOfFirst { item ->
            val page = item as? ReaderPage ?: return@indexOfFirst false
            matchesPendingRestoreTarget(page, pending)
        }
        if (resolvedPosition == -1) return null

        pending.adapterPosition = resolvedPosition
        return resolvedPosition to (adapter.items[resolvedPosition] as ReaderPage)
    }

    private fun matchesPendingRestoreTarget(
        page: ReaderPage,
        pending: PendingRelativeRestore,
    ): Boolean {
        if (page.index != pending.pageIndex) return false
        val pendingChapterId = pending.chapterId
        return pendingChapterId == null || pendingChapterId == page.chapter.chapter.id
    }

    /**
     * Queues a scroll compensation for the page whose height just changed. RecyclerView pins the
     * top of the first visible item when it relayouts, so a height change of THAT item pushes
     * every page below it by the height difference - the content the reader is looking at jumps
     * away while a page is still being decoded. The visible fraction of the page is preserved
     * instead, the same way long page progress is stored.
     */
    internal fun schedulePositionPreservation(view: View, oldHeight: Int, newHeight: Int) {
        if (oldHeight <= 0 || newHeight <= 0 || oldHeight == newHeight) return
        val position = recycler.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        if (layoutManager.findFirstVisibleItemPosition() != position) return

        val offsetInPage = (-view.top).coerceAtLeast(0)
        if (offsetInPage == 0) return

        pendingHeightCompensationPx +=
            (offsetInPage * (newHeight.toFloat() / oldHeight)).roundToInt() - offsetInPage
    }

    private fun applyPendingHeightCompensation() {
        val compensation = pendingHeightCompensationPx
        pendingHeightCompensationPx = 0
        if (compensation == 0) return

        compensatingLayoutChange = true
        try {
            recycler.scrollBy(0, compensation)
        } finally {
            compensatingLayoutChange = false
        }
    }

    internal fun getCurrentScrollProgress(): WebtoonScrollProgress? {
        // WEBTOON-RESTORE-RACE: while a relative offset restore is in flight the reported
        // position is transitional - the anchor layout places the target page at the TOP
        // (the px offset depends on the page height, unknown before layout) and the settle
        // loop walks it in afterwards. A scroll report that fired in between persisted
        // (page, offset = 0) over the saved progress in both the DB and the long-page
        // cache, and the correction report was swallowed by the 150 ms throttle (no
        // further onScrolled arrives once the layout settles) - reopening then landed on
        // the TOP of the saved page after any viewer recreation (rotation, background
        // activity destroy, process death). Reports mean nothing until the restore
        // settles or the user's drag cancels it (the drag's own reports then fire with
        // the real position); report nothing instead of the transitional anchor.
        if (pendingRelativeRestore != null) return null
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return null

        val currentChapter = adapter.currentChapter
        val lastVisible = layoutManager.findLastVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: layoutManager.findLastEndVisibleItemPosition()
        if (lastVisible == RecyclerView.NO_POSITION) return null

        var fallback: WebtoonScrollProgress? = null
        for (position in firstVisible..min(lastVisible, adapter.items.lastIndex)) {
            val item = adapter.items.getOrNull(position) as? ReaderPage ?: continue
            val view = layoutManager.findViewByPosition(position) ?: continue
            val progress = WebtoonScrollProgress(
                index = item.index,
                offsetPx = (-view.top).coerceAtLeast(0),
                pageHeightPx = view.height.coerceAtLeast(0),
                chapterId = item.chapter.chapter.id,
            )
            if (item.chapter == currentChapter) return progress
            if (fallback == null) fallback = progress
        }
        return fallback
    }

    fun onScrolled(pos: Int? = null) {
        val position = pos ?: layoutManager.findLastEndVisibleItemPosition()
        val item = adapter.items.getOrNull(position)
        val allowPreload = checkAllowPreload(item as? ReaderPage)
        if (item is ReaderPage && isSwitchSettling() && item.chapter != adapter.currentChapter) {
            // A stale boundary report while a programmatic switch settles: the view was placed by
            // moveToPage, not by the user. Propagating it would make the VM bounce back to the
            // reported chapter ("the previous chapter stayed"); re-anchor instead.
            reanchorDuringSwitchSettle()
            return
        }
        if (item != null) {
            val selectionChanged = currentPage != item
            currentPage = item
            val readerState = activity.viewModel.state.value
            val viewerChapters = readerState.viewerChapters
            dispatchWebtoonSelection(
                item = item,
                currentChapter = adapter.currentChapter,
                nextChapter = viewerChapters?.nextChapter,
                // A restore or toolbar switch is not a forward scroll across the old chapter.
                // The adapter can briefly lag behind the VM while the activity collects state.
                allowChapterCompletion = pendingRelativeRestore == null &&
                    !isSwitchSettling() && !readerState.isLoadingAdjacentChapter &&
                    adapter.currentChapter === viewerChapters?.currChapter,
                lastCompletionPage = lastCompletionPage,
                selectionChanged = selectionChanged,
                onPageSelected = { onPageSelected(it, allowPreload) },
                onTransitionSelected = ::onTransitionSelected,
            )
        }
    }

    private fun isSwitchSettling(): Boolean =
        switchSettleUntilMs != 0L && SystemClock.uptimeMillis() < switchSettleUntilMs

    private fun reanchorDuringSwitchSettle() {
        val now = SystemClock.uptimeMillis()
        if (now - switchSettleReanchorMs < SWITCH_SETTLE_REANCHOR_INTERVAL_MS) return
        switchSettleReanchorMs = now
        adapter.currentChapter?.pages?.firstOrNull()?.let(::moveToPage)
    }

    /**
     * Scrolls up by [scrollDistance].
     */
    private fun scrollUp() {
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, -scrollDistance)
        } else {
            recycler.scrollBy(0, -scrollDistance)
        }
    }

    /**
     * Scrolls down by [scrollDistance].
     */
    private fun scrollDown() {
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, scrollDistance)
        } else {
            recycler.scrollBy(0, scrollDistance)
        }
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollDown() else scrollUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollUp() else scrollDown()
                }
            }
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> if (isUp) scrollUp()

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> if (isUp) scrollDown()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }

    /**
     * Notifies adapter of changes around the current page to trigger a relayout in the recycler.
     * Used when an image configuration is changed.
     */
    private fun refreshAdapter() {
        val position = layoutManager.findLastEndVisibleItemPosition()
        adapter.refresh()
        // notifyItemRangeChanged takes a COUNT, not an end position: the old call invalidated
        // [pos-3, pos-3+min(pos+3, last)] - the whole chapter tail for any later position.
        val start = max(0, position - 3)
        val endInclusive = min(position + 3, adapter.itemCount - 1)
        adapter.notifyItemRangeChanged(start, max(0, endInclusive - start + 1))
    }
}

// Double the cache size to reduce rebinds/recycles incurred by the extra layout space on scroll direction changes
private const val RECYCLER_VIEW_CACHE_SIZE = 4

private data class PendingRelativeRestore(
    var adapterPosition: Int,
    val chapterId: Long?,
    val pageIndex: Int,
    val ratioPpm: Int,
    val startedAtUptimeMs: Long = SystemClock.uptimeMillis(),
    var lastMeasuredPageHeightPx: Int = 0,
    var stableHeightFrames: Int = 0,
    var readyFrames: Int = 0,
    var imageDecoded: Boolean = false,
)

private const val MAX_PENDING_RELATIVE_RESTORE_DURATION_MS = 30_000L

// Minimum interval between webtoon scroll progress reports while actively scrolling.
private const val SCROLL_PROGRESS_UPDATE_INTERVAL_MS = 150L
private const val MIN_STABLE_HEIGHT_FRAMES = 2
private const val RESTORE_SETTLE_TOLERANCE_PX = 2
private const val MIN_READY_STATE_FRAMES_FALLBACK = 30

// How long a programmatic chapter switch suppresses stale boundary reports (capped; ends earlier
// when the new first page is decoded or the user scrolls).
private const val PROGRAMMATIC_SWITCH_SETTLE_CAP_MS = 3000L

// Minimum interval between re-anchors while a programmatic switch settles.
private const val SWITCH_SETTLE_REANCHOR_INTERVAL_MS = 400L
