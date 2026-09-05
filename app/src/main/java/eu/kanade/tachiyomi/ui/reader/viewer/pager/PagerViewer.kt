package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewpager.widget.ViewPager
import com.tadami.aurora.R
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPreloadManager
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.JoinedReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import eu.kanade.tachiyomi.ui.reader.viewer.autoscroll.PagerAutoScrollManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

/**
 * Implementation of a [Viewer] to display pages with a [ViewPager].
 */
@Suppress("LeakingThis")
abstract class PagerViewer(val activity: ReaderActivity) : Viewer {

    val downloadManager: MangaDownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * View pager used by this viewer. It's abstract to implement L2R, R2L and vertical pagers on
     * top of this class.
     */
    val pager = createPager()

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = PagerConfig(this, scope)

    /**
     * Auto-scroll manager for automatic page advancement.
     */
    val autoScrollManager by lazy { PagerAutoScrollManager(this) }

    /**
     * Adapter of the pager.
     */
    private val adapter = PagerViewerAdapter(this)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    /**
     * Viewer chapters to set when the pager enters idle mode. Otherwise, if the view was settling
     * or dragging, there'd be a noticeable and annoying jump.
     */
    private var awaitingIdleViewerChapters: ViewerChapters? = null

    /**
     * Whether the view pager is currently in idle mode. It sets the awaiting chapters if setting
     * this field to true.
     */
    private var isIdle = true
        set(value) {
            field = value
            if (value) {
                awaitingIdleViewerChapters?.let { viewerChapters ->
                    setChaptersInternal(viewerChapters)
                    awaitingIdleViewerChapters = null
                }
            }
        }

    init {
        pager.isVisible = false // Don't layout the pager yet
        pager.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        pager.isFocusable = false
        pager.offscreenPageLimit = 1
        pager.id = R.id.reader_pager
        pager.adapter = adapter
        pager.addOnPageChangeListener(
            object : ViewPager.SimpleOnPageChangeListener() {
                // Track swipes into void at the last page
                private var dragStartedAtLastPage = false

                override fun onPageSelected(position: Int) {
                    if (!activity.isScrollingThroughPages) {
                        activity.hideMenu()
                    }
                    onPageChange(position)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    when (state) {
                        ViewPager.SCROLL_STATE_DRAGGING -> {
                            // Record if user starts dragging when already on the last page
                            dragStartedAtLastPage = pager.currentItem == adapter.count - 1
                            pauseAutoScroll()
                        }
                        ViewPager.SCROLL_STATE_IDLE -> {
                            if (dragStartedAtLastPage && pager.currentItem == adapter.count - 1) {
                                // Drag started and ended on last page — user swiped into void
                                activity.onMeltdownTransitionActivated()
                            }
                            dragStartedAtLastPage = false
                            resumeAutoScroll()
                        }
                        else -> { /* SETTLING — ignore */ }
                    }
                    isIdle = state == ViewPager.SCROLL_STATE_IDLE
                }
            },
        )
        pager.tapListener = { event ->
            val viewPosition = IntArray(2)
            pager.getLocationOnScreen(viewPosition)
            val viewPositionRelativeToWindow = IntArray(2)
            pager.getLocationInWindow(viewPositionRelativeToWindow)
            val pos = PointF(
                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / pager.width,
                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / pager.height,
            )
            when (config.navigator.getAction(pos)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT -> {
                    moveToNext()
                }
                NavigationRegion.PREV -> {
                    moveToPrevious()
                }
                NavigationRegion.RIGHT -> {
                    moveRight()
                }
                NavigationRegion.LEFT -> {
                    moveLeft()
                }
                NavigationRegion.NONE -> Unit
            }
        }
        pager.longTapListener = f@{
            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val item = adapter.items.getOrNull(pager.currentItem)
                if (item is ReaderPage) {
                    activity.onPageLongTap(item)
                    return@f true
                }
            }
            false
        }

        config.dualPageSplitChangedListener = { enabled ->
            if (!enabled) {
                cleanupPageSplit()
            }
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.transitionPropertyChangedListener = {
            activity.viewModel.state.value.viewerChapters?.let(::setChapters)
        }

        config.spreadPropertyChangedListener = {
            // Live regroup on join/shift toggles: grouping happens only in setChapters, while
            // refreshAdapter recreated views over the OLD groups - the spread toggle took effect
            // only on the next chapter. User-initiated change, so the position re-anchor is
            // expected. Wide-page auto-detection deliberately keeps refreshAdapter: re-pairing
            // mid-chapter on an automatic detection would make the visible page jump.
            activity.viewModel.state.value.viewerChapters?.let(::setChapters)
        }

        config.navigationModeChangedListener = {
            activity.binding.navigationOverlay.setNavigation(config.navigator, config.navigationOverlayOnStart)
        }
    }

    override fun destroy() {
        super.destroy()
        autoScrollManager.destroy()
        scope.cancel()
    }

    /**
     * Starts auto-scroll with the specified speed.
     *
     * @param speed The speed value (1-100), where higher is faster.
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
     * Pauses auto-scroll. Can be resumed later.
     */
    fun pauseAutoScroll() {
        autoScrollManager.pause()
    }

    /**
     * Resumes auto-scroll after a temporary pause.
     */
    fun resumeAutoScroll() {
        autoScrollManager.resume()
    }

    /**
     * Sets a cooldown period during which auto-scroll pauses.
     * Used after touch events so the user can interact without scroll interference.
     *
     * @param delayMs Duration of the cooldown in milliseconds.
     */
    fun setAutoScrollCooldown(delayMs: Long) {
        autoScrollManager.setCooldown(delayMs)
    }

    /**
     * Creates a new ViewPager.
     */
    abstract fun createPager(): Pager

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return pager
    }

    /**
     * Returns the PagerPageHolder for the provided page
     */
    private fun getPageHolder(page: ReaderPage): PagerPageHolder? =
        pager.children
            .filterIsInstance(PagerPageHolder::class.java)
            .firstOrNull { it.item == page }

    /**
     * Called when a new page (either a [ReaderPage] or [ChapterTransition]) is marked as active
     */
    private fun onPageChange(position: Int) {
        val page = adapter.items.getOrNull(position)
        if (page != null && currentPage != page) {
            val allowPreload = checkAllowPreload(page as? ReaderPage)
            val forward = when {
                currentPage is ReaderPage && page is ReaderPage -> {
                    // if both pages have the same number, it's a split page with an InsertPage
                    if (page.number == (currentPage as ReaderPage).number) {
                        // the InsertPage is always the second in the reading direction
                        page is InsertPage
                    } else {
                        page.number > (currentPage as ReaderPage).number
                    }
                }
                currentPage is ChapterTransition.Prev && page is ReaderPage ->
                    false
                else -> true
            }
            currentPage = page
            when (page) {
                is ReaderPage -> onReaderPageSelected(page, allowPreload, forward)
                is ChapterTransition -> onTransitionSelected(page)
            }
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        val current = currentPage
        if (current == null) {
            // Initial opening. Allow preload if the first page is already in the preload range.
            return true
        }

        // Allow preload for
        // 1. Going to next chapter from chapter transition
        // 2. Going between pages of same chapter
        // 3. Next chapter page
        return when (page.chapter) {
            (current as? ChapterTransition.Next)?.to -> true
            (current as? ReaderPage)?.chapter -> true
            adapter.nextTransition?.to -> true
            else -> false
        }
    }

    private fun checkAndPreload(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        val inPreloadRange = pages.size - page.number < ReaderPreloadManager.nextChapterPreloadThreshold
        if (config.preloadNextChapter && inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            adapter.nextTransition?.to?.let(activity::requestPreloadChapter)
        }
    }

    /**
     * Called when a [ReaderPage] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onReaderPageSelected(page: ReaderPage, allowPreload: Boolean, forward: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onReaderPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Notify holder of page change
        getPageHolder(page)?.onPageSelected(forward)

        // Skip preload on inserts it causes unwanted page jumping
        if (page is InsertPage) {
            return
        }

        checkAndPreload(page, allowPreload)
    }

    /**
     * Called when a [ChapterTransition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        } else if (transition is ChapterTransition.Next) {
            // No more chapters: reveal the pending finale plate (if any) and show the menu
            // because the user is probably going to close the reader.
            // The meltdown easter egg starts only on an explicit swipe into the void (see
            // onPageScrollStateChanged), not when merely landing on this page.
            activity.viewModel.revealPendingFinale()
            activity.showMenu()
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        if (isIdle) {
            setChaptersInternal(chapters)
        } else {
            awaitingIdleViewerChapters = chapters
        }
    }

    /**
     * Sets the active [chapters] on this pager.
     */
    private fun setChaptersInternal(chapters: ViewerChapters) {
        // The setting controls whether to show the info screen. We do not force the info screen just because
        // the previous current item was a transition (this was causing it to show for 1-page chapters etc even when disabled).
        val forceTransition = config.alwaysShowChapterTransition
        adapter.setChapters(chapters, forceTransition)

        // Layout the pager once a chapter is being set
        if (pager.isGone) {
            logcat { "Pager first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            pager.isVisible = true
        } else {
            pager.post {
                onPageChange(pager.currentItem)
            }
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
        // A-M2: a page paired into a JoinedReaderPage (landscape spread) is not identity-equal to
        // any adapter item; indexOf returned -1, silently breaking progress restore on chapter
        // open and the navigator slider for every paired page. Find the item CONTAINING it.
        val position = adapter.items.indexOfFirst { item ->
            when (item) {
                is JoinedReaderPage -> item.firstPage === page || item.secondPage === page
                else -> item === page
            }
        }
        if (position != -1) {
            val currentPosition = pager.currentItem
            pager.setCurrentItem(position, true)
            // manually call onPageChange since ViewPager listener is not triggered in this case
            if (currentPosition == position) {
                onPageChange(position)
            }
        } else {
            logcat { "Page $page not found in adapter" }
        }
    }

    /**
     * Moves to the next page.
     */
    open fun moveToNext() {
        moveRight()
    }

    /**
     * Moves to the previous page.
     */
    open fun moveToPrevious() {
        moveLeft()
    }

    /**
     * Moves to the page at the right.
     */
    protected open fun moveRight() {
        if (pager.currentItem != adapter.count - 1) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && config.navigateToPan && holder.canPanRight()) {
                holder.panRight()
            } else {
                pager.setCurrentItem(pager.currentItem + 1, config.usePageTransitions)
            }
        } else {
            // At the end of the current list.
            // If there is a logical next chapter, request its preload (so switching can happen when it loads).
            // Only fire meltdown if there is truly no next (the "no more chapters" case).
            val next = adapter.nextTransition?.to
            if (next != null) {
                activity.requestPreloadChapter(next)
            } else {
                activity.onMeltdownTransitionActivated()
            }
        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
        if (pager.currentItem != 0) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && config.navigateToPan && holder.canPanLeft()) {
                holder.panLeft()
            } else {
                pager.setCurrentItem(pager.currentItem - 1, config.usePageTransitions)
            }
        } else {
            // Already at the first item — for R2L viewers this is the "end".
            // Fire meltdown only if this is the logical forward direction (R2L).
            // We rely on R2LPagerViewer.moveToNext() -> moveLeft(), so the activity
            // call is safe here. PagerViewer will silently no-op for L2R.
        }
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected open fun moveUp() {
        moveToPrevious()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected open fun moveDown() {
        moveToNext()
    }

    /**
     * Resets the adapter in order to recreate all the views. Used when a image configuration is
     * changed.
     */
    internal fun refreshAdapter() {
        val currentItem = pager.currentItem
        adapter.refresh()
        pager.adapter = adapter
        pager.setCurrentItem(currentItem, false)
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState.and(KeyEvent.META_CTRL_ON) > 0

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveDown() else moveUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveUp() else moveDown()
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isUp) {
                    if (ctrlPressed) moveToNext() else moveRight()
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isUp) {
                    if (ctrlPressed) moveToPrevious() else moveLeft()
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                        moveDown()
                    } else {
                        moveUp()
                    }
                    return true
                }
            }
        }
        return false
    }

    fun onPageSplit(currentPage: ReaderPage, newPage: InsertPage) {
        activity.runOnUiThread {
            // Need to insert on UI thread else images will go blank
            adapter.onPageSplit(currentPage, newPage)
        }
    }

    private fun cleanupPageSplit() {
        adapter.cleanupPageSplit()
    }
}
