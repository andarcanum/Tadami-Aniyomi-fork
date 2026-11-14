package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

/** Gestures belong to the webtoon container; pixel resolution follows pushed container transforms. */
open class WebtoonSubsamplingImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SubsamplingScaleImageView(context, attrs) {
    private val localViewport = RectF()
    private val retainViewport = RectF()
    private var lastDisplayScale = 1f

    init {
        // Enable bounded previews before any image starts loading (also while not attached).
        setExternalViewport(localViewport, retainViewport, lastDisplayScale)
    }

    fun updateViewport(prefetch: RectF, retain: RectF, displayScale: Float) {
        localViewport.set(prefetch)
        retainViewport.set(retain)
        lastDisplayScale = displayScale
        setExternalViewport(localViewport, retainViewport, displayScale)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if ((changed || localViewport.isEmpty) && width > 0 && height > 0) {
            findAncestorRecyclerView()?.updateViewportForChild(this)
        }
    }

    override fun onDetachedFromWindow() {
        // Clear prefetch so off-window mCachedViews holders stop requesting new loads while still
        // preserving already-decoded detail tiles inside retainViewport across scroll reversals.
        localViewport.setEmpty()
        setExternalViewport(localViewport, retainViewport, lastDisplayScale)
        super.onDetachedFromWindow()
    }

    private fun findAncestorRecyclerView(): WebtoonRecyclerView? {
        var current: View? = parent as? View
        while (current != null) {
            if (current is WebtoonRecyclerView) {
                return current
            }
            current = current.parent as? View
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
