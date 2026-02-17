package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.PointF
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.app.ActivityCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.ChapterScrollProgress
import eu.kanade.tachiyomi.ui.reader.WebtoonScrollProgress
import eu.kanade.tachiyomi.ui.reader.evaluateWebtoonRestoreSettle
import eu.kanade.tachiyomi.ui.reader.resolveWebtoonRestoreOffsetPx
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
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

/**
 * Implementation of a [Viewer] to display pages with a [RecyclerView].
 */
class WebtoonViewer(val activity: ReaderActivity, val isContinuous: Boolean = true) : Viewer {

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
            if (pendingRelativeRestore != pending) {
                // Pending target changed while retrying, restart from scratch for the new target.
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
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    onScrolled()
                    applyPendingRelativeRestore(clearWhenApplied = false)
                    getCurrentScrollProgress()?.let(activity.viewModel::onWebtoonScrollProgressChanged)

                    if ((dy > threshold || dy < -threshold) && activity.viewModel.state.value.menuVisible) {
                        activity.hideMenu()
                    }

                    if (dy < 0) {
                        val firstIndex = layoutManager.findFirstVisibleItemPosition()
                        val firstItem = adapter.items.getOrNull(firstIndex)
                        if (firstItem is ChapterTransition.Prev && firstItem.to != null) {
                            activity.requestPreloadChapter(firstItem.to)
                        }
                    }

                    val lastIndex = layoutManager.findLastEndVisibleItemPosition()
                    val lastItem = adapter.items.getOrNull(lastIndex)
                    if (lastItem is ChapterTransition.Next && lastItem.to == null) {
                        activity.showMenu()
                    }
                }
            },
        )
        recycler.tapListener = { event ->
            // Pause auto-scroll on user tap
            pauseAutoScroll()

            val viewPosition = IntArray(2)
            recycler.getLocationOnScreen(viewPosition)
            val viewPositionRelativeToWindow = IntArray(2)
            recycler.getLocationInWindow(viewPositionRelativeToWindow)
            val pos = PointF(
                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / recycler.width,
                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / recycler.originalHeight,
            )
            when (config.navigator.getAction(pos)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT, NavigationRegion.RIGHT -> scrollDown()
                NavigationRegion.PREV, NavigationRegion.LEFT -> scrollUp()
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

        config.themeChangedListener = {
            ActivityCompat.recreate(activity)
        }

        config.doubleTapZoomChangedListener = {
            frame.doubleTapZoom = it
        }

        config.zoomPropertyChangedListener = {
            frame.zoomOutDisabled = it
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }

        // Monitor zoom state and pause auto-scroll when zoomed in
        scope.launch {
            autoScrollManager.state.collectLatest { state ->
                if (state.isActive && !state.isPaused && isZoomedIn()) {
                    pauseAutoScroll()
                }
            }
        }

        frame.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
        val nextChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as? ReaderPage)?.chapter

        // Allow preload for
        // 1. Going between pages of same chapter
        // 2. Next chapter page
        return when (page.chapter) {
            (currentPage as? ReaderPage)?.chapter -> true
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
        autoScrollManager.destroy()
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
     * Checks if the webtoon is currently zoomed in.
     *
     * @return true if zoomed in (scale > 1), false otherwise.
     */
    fun isZoomedIn(): Boolean {
        return recycler.scaleX > 1f
    }

    /**
     * Called from the RecyclerView listener when a [page] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onPageSelected(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
            val transitionChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as?ReaderPage)?.chapter
            if (transitionChapter != null) {
                logcat { "Requesting to preload chapter ${transitionChapter.chapter.chapter_number}" }
                activity.requestPreloadChapter(transitionChapter)
            }
        }
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
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val forceTransition = config.alwaysShowChapterTransition || currentPage is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        if (recycler.isGone) {
            logcat { "Recycler first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            recycler.isVisible = true
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

    internal fun onPageImageReady(page: ReaderPage) {
        val pending = pendingRelativeRestore ?: return
        if (pending.pageIndex != page.index) return
        if (pending.chapterId != null && pending.chapterId != page.chapter.chapter.id) return
        pending.imageDecoded = true
        startPendingRelativeRestoreLoop()
    }

    private fun applyPendingRelativeRestore(clearWhenApplied: Boolean): Boolean {
        val pending = pendingRelativeRestore ?: return false
        val (adapterPosition, item) = resolvePendingRestoreTarget(pending) ?: return false

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
            item.chapter.requestedPageOffsetRatioPpm = null
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

    internal fun getCurrentScrollProgress(): WebtoonScrollProgress? {
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
        if (item != null && currentPage != item) {
            currentPage = item
            when (item) {
                is ReaderPage -> onPageSelected(item, allowPreload)
                is ChapterTransition -> onTransitionSelected(item)
            }
        }
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
        adapter.notifyItemRangeChanged(
            max(0, position - 3),
            min(position + 3, adapter.itemCount - 1),
        )
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
private const val MIN_STABLE_HEIGHT_FRAMES = 2
private const val RESTORE_SETTLE_TOLERANCE_PX = 2
private const val MIN_READY_STATE_FRAMES_FALLBACK = 30
