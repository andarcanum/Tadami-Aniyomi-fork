package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlin.math.abs

/** Gestures belong to the webtoon container; pixel resolution follows its actual transform. */
open class WebtoonSubsamplingImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SubsamplingScaleImageView(context, attrs) {
    private val visibleRect = Rect()
    private val globalOrigin = Point()
    private val localViewport = RectF()
    private var observedTree: ViewTreeObserver? = null
    private val viewportListener = ViewTreeObserver.OnPreDrawListener {
        refreshViewport()
        true
    }

    init {
        // Enable bounded previews before any image starts loading (also while not attached).
        setExternalViewport(localViewport, 1f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observedTree = viewTreeObserver.also { it.addOnPreDrawListener(viewportListener) }
    }

    override fun onDetachedFromWindow() {
        observedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(viewportListener)
        observedTree = null
        localViewport.setEmpty()
        setExternalViewport(localViewport, 1f)
        super.onDetachedFromWindow()
    }

    private fun refreshViewport() {
        var xScale = 1f
        var yScale = 1f
        var ancestor: View? = this
        while (ancestor != null) {
            xScale *= ancestor.scaleX
            yScale *= ancestor.scaleY
            ancestor = ancestor.parent as? View
        }
        xScale = abs(xScale)
        yScale = abs(yScale)
        if (xScale <= 0f || yScale <= 0f || !xScale.isFinite() || !yScale.isFinite() ||
            !getGlobalVisibleRect(visibleRect, globalOrigin)
        ) {
            localViewport.setEmpty()
            setExternalViewport(localViewport, 1f)
            return
        }
        // getGlobalVisibleRect transforms/clips both the rect and the local origin through
        // ancestors. Undo the parent scale; subtracting the origin alone would over-decode.
        localViewport.set(
            ((visibleRect.left - globalOrigin.x) / xScale).coerceIn(0f, width.toFloat()),
            ((visibleRect.top - globalOrigin.y) / yScale).coerceIn(0f, height.toFloat()),
            ((visibleRect.right - globalOrigin.x) / xScale).coerceIn(0f, width.toFloat()),
            ((visibleRect.bottom - globalOrigin.y) / yScale).coerceIn(0f, height.toFloat()),
        )
        setExternalViewport(localViewport, maxOf(xScale, yScale))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
