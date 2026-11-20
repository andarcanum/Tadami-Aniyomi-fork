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
    private val visibleViewport = RectF()
    private val localViewport = RectF()
    private val retainViewport = RectF()
    private var lastDisplayScale = 1f

    init {
        // Enable bounded previews before any image starts loading (also while not attached).
        setExternalViewport(visibleViewport, localViewport, retainViewport, lastDisplayScale)
    }

    fun updateViewport(prefetch: RectF, retain: RectF, displayScale: Float) {
        updateViewport(prefetch, prefetch, retain, displayScale)
    }

    fun updateViewport(visible: RectF, prefetch: RectF, retain: RectF, displayScale: Float) {
        visibleViewport.set(visible)
        localViewport.set(prefetch)
        retainViewport.set(retain)
        lastDisplayScale = displayScale
        setExternalViewport(visibleViewport, localViewport, retainViewport, displayScale)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if ((changed || localViewport.isEmpty) && width > 0 && height > 0) {
            findAncestorRecyclerView()?.updateViewportForChild(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Re-sync viewport immediately when returning from RecyclerView.mCachedViews so holders
        // that skip onLayout(changed=true) do not stay stuck with the empty viewport set on detach.
        if (width > 0 && height > 0) {
            findAncestorRecyclerView()?.updateViewportForChild(this)
        } else {
            post {
                if (isAttachedToWindow && width > 0 && height > 0) {
                    findAncestorRecyclerView()?.updateViewportForChild(this)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        // Clear visible & prefetch so off-window mCachedViews holders stop requesting new loads
        // while preserving already-decoded detail tiles inside retainViewport across scroll reversals.
        visibleViewport.setEmpty()
        localViewport.setEmpty()
        setExternalViewport(visibleViewport, localViewport, retainViewport, lastDisplayScale)
        super.onDetachedFromWindow()
    }

    override fun recycle() {
        visibleViewport.setEmpty()
        localViewport.setEmpty()
        retainViewport.setEmpty()
        lastDisplayScale = 1f
        setExternalViewport(visibleViewport, localViewport, retainViewport, lastDisplayScale)
        super.recycle()
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
