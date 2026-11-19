package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.ReaderActivity

/**
 * Implementation of a left to right PagerViewer.
 */
class L2RPagerViewer(activity: ReaderActivity) : PagerViewer(activity) {
    /**
     * Creates a new left to right pager.
     */
    override fun createPager(): Pager {
        return Pager(activity)
    }
}

/**
 * Implementation of a right to left PagerViewer.
 */
class R2LPagerViewer(activity: ReaderActivity) : PagerViewer(activity) {

    override val isRtlReading: Boolean = true

    /**
     * Creates a new right to left pager.
     */
    override fun createPager(): Pager {
        return Pager(activity)
    }

    /**
     * Moves to the next page. On a R2L pager the next page is the one at the left.
     */
    override fun moveToNext() {
        moveLeft()
    }

    /**
     * Moves to the previous page. On a R2L pager the previous page is the one at the right.
     */
    override fun moveToPrevious() {
        moveRight()
    }

    /**
     * The list is reversed for R2L: the last list item is the START of the series, so hitting it
     * while moving right (backwards) must not preload/meltdown.
     */
    override fun onReachedListEnd() = Unit

    /**
     * The first list item is the END of the reading direction for R2L: moving left into it is the
     * forward edge (preload the next chapter / meltdown when there is none).
     */
    override fun onReachedListStart() {
        handleReadingEndReached()
    }

    override fun isAtReadingEnd(position: Int): Boolean = position == 0
}

/**
 * Implementation of a vertical (top to bottom) PagerViewer.
 */
class VerticalPagerViewer(activity: ReaderActivity) : PagerViewer(activity) {
    /**
     * Creates a new vertical pager.
     */
    override fun createPager(): Pager {
        return Pager(activity, isHorizontal = false)
    }
}
