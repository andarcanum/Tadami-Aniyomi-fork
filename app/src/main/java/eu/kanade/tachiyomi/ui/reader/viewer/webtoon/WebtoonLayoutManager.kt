@file:Suppress("PackageDirectoryMismatch")

package androidx.recyclerview.widget

import android.content.Context
import androidx.recyclerview.widget.RecyclerView.NO_POSITION

/**
 * Layout manager used by the webtoon viewer. Item prefetch is disabled because the extra layout
 * space feature is used which allows setting the image even if the holder is not visible,
 * avoiding (in most cases) black views when they are visible.
 *
 * This layout manager uses the same package name as the support library in order to use a package
 * protected method.
 */
class WebtoonLayoutManager(context: Context, var extraLayoutSpace: Int) : LinearLayoutManager(context) {

    init {
        isItemPrefetchEnabled = false
    }

    /**
     * Reserves the extra layout space on BOTH sides of the viewport. The deprecated
     * [getExtraLayoutSpace] compat path in LinearLayoutManager assigns the returned space only to
     * the current scroll direction, so on a direction change the opposite side had zero extra
     * space and its holders were recycled immediately - scrolling back then paid a full re-decode.
     */
    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        extraLayoutSpace[0] = this.extraLayoutSpace
        extraLayoutSpace[1] = this.extraLayoutSpace
    }

    /**
     * Returns the position of the last item whose end side is visible on screen.
     */
    fun findLastEndVisibleItemPosition(): Int {
        ensureLayoutState()
        val callback = if (mOrientation == HORIZONTAL) {
            mHorizontalBoundCheck
        } else {
            mVerticalBoundCheck
        }.mCallback
        val start = callback.parentStart
        val end = callback.parentEnd
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)!!
            val childStart = callback.getChildStart(child)
            val childEnd = callback.getChildEnd(child)
            if (childEnd <= end || childStart < start) {
                return getPosition(child)
            }
        }

        return NO_POSITION
    }
}
