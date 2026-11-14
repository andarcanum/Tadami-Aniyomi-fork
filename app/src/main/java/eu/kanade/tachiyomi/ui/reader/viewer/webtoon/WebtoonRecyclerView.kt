package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import kotlin.math.abs
import kotlin.math.max

/**
 * Implementation of a [RecyclerView] used by the webtoon reader.
 */
class WebtoonRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    private var isZooming = false
    private var atLastPosition = false
    private var atFirstPosition = false
    private var halfWidth = 0
    private var halfHeight = 0
    var originalHeight = 0
        private set
    private var heightSet = false
    private var firstVisibleItemPosition = 0
    private var lastVisibleItemPosition = 0
    private var currentScale = DEFAULT_RATE
    private var lastScrollDy = 0
    private var lastFlingVelocityY = 0
    private val inverseMatrix = Matrix()
    private val visibleRect = RectF()
    private val prefetchRect = RectF()
    private val retainRect = RectF()
    private val childPrefetchRect = RectF()
    private val childRetainRect = RectF()
    var zoomOutDisabled = false
        set(value) {
            field = value
            if (value && currentScale < DEFAULT_RATE) {
                zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
            }
        }
    private val minRate
        get() = if (zoomOutDisabled) DEFAULT_RATE else MIN_RATE

    private val listener = GestureListener()
    private val detector = Detector()

    var doubleTapZoom = true

    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        halfWidth = MeasureSpec.getSize(widthSpec) / 2
        halfHeight = MeasureSpec.getSize(heightSpec) / 2
        // Refresh the baseline height while not zoomed so multi-window/foldable resizes keep the
        // zoom clamps and tap zones correct. While zoomed the height spec reflects the inflated
        // layoutParams.height, which must not be captured as the baseline.
        if (!heightSet || currentScale == DEFAULT_RATE) {
            originalHeight = MeasureSpec.getSize(heightSpec)
            heightSet = true
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        return super.onTouchEvent(e)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        val layoutManager = layoutManager as? LinearLayoutManager ?: return
        lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        lastScrollDy = dy
        updateChildViewports()
    }

    /**
     * Invoked after the children have been laid out and before they are drawn. The viewer uses it
     * to apply a queued scroll compensation for a page whose height changed during this layout
     * pass: applying it here lands in the same frame (no visible jump) and, unlike a call from a
     * layout listener, does not re-enter the layout manager while it is filling children.
     */
    var onBeforeDrawChildren: (() -> Unit)? = null

    override fun dispatchDraw(canvas: Canvas) {
        onBeforeDrawChildren?.invoke()
        super.dispatchDraw(canvas)
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        val layoutManager = layoutManager
        val visibleItemCount = layoutManager?.childCount ?: 0
        val totalItemCount = layoutManager?.itemCount ?: 0
        atLastPosition = visibleItemCount > 0 && lastVisibleItemPosition == totalItemCount - 1
        atFirstPosition = firstVisibleItemPosition == 0
        when (state) {
            SCROLL_STATE_IDLE -> {
                lastScrollDy = 0
                lastFlingVelocityY = 0
                updateChildViewports()
            }
            SCROLL_STATE_DRAGGING -> {
                lastFlingVelocityY = 0
                updateChildViewports()
            }
        }
    }

    override fun fling(velocityX: Int, velocityY: Int): Boolean {
        lastFlingVelocityY = velocityY
        val flung = super.fling(velocityX, velocityY)
        updateChildViewports()
        return flung
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateChildViewports()
    }

    internal fun updateChildViewports() {
        val displayScale = computeViewportBounds()
        if (displayScale <= 0f) return
        for (i in 0 until childCount) {
            pushViewportToHolderChild(getChildAt(i), displayScale)
        }
    }

    internal fun updateViewportForChild(target: WebtoonSubsamplingImageView) {
        val displayScale = computeViewportBounds()
        if (displayScale <= 0f) return
        pushViewportToSubsamplingView(target, displayScale)
    }

    private fun pushViewportToHolderChild(child: View, displayScale: Float) {
        val target = when (child) {
            is WebtoonSubsamplingImageView -> child
            is ReaderPageImageView -> child.getImageView() as? WebtoonSubsamplingImageView
            else -> null
        } ?: return
        pushViewportToSubsamplingView(target, displayScale)
    }

    private fun pushViewportToSubsamplingView(target: WebtoonSubsamplingImageView, displayScale: Float) {
        val childW = target.width
        val childH = target.height
        if (childW <= 0 || childH <= 0) return

        var offsetX = 0f
        var offsetY = 0f
        var current: View? = target
        while (current != null && current !== this) {
            val parentView = current.parent as? View ?: return
            offsetX += current.left + current.translationX - parentView.scrollX
            offsetY += current.top + current.translationY - parentView.scrollY
            current = parentView
        }
        if (current !== this) return

        childPrefetchRect.set(prefetchRect)
        childPrefetchRect.offset(-offsetX, -offsetY)
        if (!childPrefetchRect.intersect(0f, 0f, childW.toFloat(), childH.toFloat())) {
            childPrefetchRect.setEmpty()
        }

        childRetainRect.set(retainRect)
        childRetainRect.offset(-offsetX, -offsetY)
        if (!childRetainRect.intersect(0f, 0f, childW.toFloat(), childH.toFloat())) {
            childRetainRect.setEmpty()
        }

        target.updateViewport(childPrefetchRect, childRetainRect, displayScale)
    }

    private fun computeViewportBounds(): Float {
        if (width <= 0 || height <= 0) return 0f

        var xScale = 1f
        var yScale = 1f
        var ancestor: View? = this
        while (ancestor != null) {
            xScale *= abs(ancestor.scaleX)
            yScale *= abs(ancestor.scaleY)
            ancestor = ancestor.parent as? View
        }
        val displayScale = max(xScale, yScale)
        if (!displayScale.isFinite() || displayScale <= 0f) return 0f

        val container = parent as? View
        val containerW = container?.width?.takeIf { it > 0 } ?: width
        val containerH = container?.height?.takeIf { it > 0 } ?: height
        visibleRect.set(0f, 0f, containerW.toFloat(), containerH.toFloat())
        if (container != null) {
            visibleRect.offset(-left.toFloat(), -top.toFloat())
        }
        val localMatrix = matrix
        if (!localMatrix.isIdentity) {
            if (!localMatrix.invert(inverseMatrix)) return 0f
            inverseMatrix.mapRect(visibleRect)
        }
        if (clipToPadding) {
            val padLeft = paddingLeft.toFloat()
            val padTop = paddingTop.toFloat()
            val padRight = (width - paddingRight).toFloat()
            val padBottom = (height - paddingBottom).toFloat()
            if (!visibleRect.intersect(padLeft, padTop, padRight, padBottom)) {
                visibleRect.setEmpty()
            }
        }

        if (visibleRect.isEmpty) {
            prefetchRect.setEmpty()
            retainRect.setEmpty()
            return displayScale
        }

        val viewportHeight = visibleRect.height()
        val retainMargin = viewportHeight * RETAIN_VIEWPORT_MARGIN
        retainRect.set(
            visibleRect.left,
            visibleRect.top - retainMargin,
            visibleRect.right,
            visibleRect.bottom + retainMargin,
        )
        prefetchRect.set(visibleRect)

        val direction = when {
            lastScrollDy != 0 -> if (lastScrollDy > 0) 1 else -1
            scrollState == SCROLL_STATE_SETTLING && lastFlingVelocityY != 0 -> if (lastFlingVelocityY > 0) 1 else -1
            else -> 0
        }
        if (scrollState != SCROLL_STATE_IDLE && direction != 0 && viewportHeight > 0f) {
            val lookaheadFactor = if (scrollState == SCROLL_STATE_SETTLING && lastFlingVelocityY != 0) {
                (abs(lastFlingVelocityY) / (viewportHeight * 2.5f)).coerceIn(
                    MIN_LOOKAHEAD_FACTOR,
                    MAX_LOOKAHEAD_FACTOR,
                )
            } else {
                (abs(lastScrollDy) / (viewportHeight * 0.08f).coerceAtLeast(1f)).coerceIn(
                    MIN_LOOKAHEAD_FACTOR,
                    MAX_LOOKAHEAD_FACTOR,
                )
            }
            val lookaheadPx = viewportHeight * lookaheadFactor
            if (direction > 0) {
                prefetchRect.bottom += lookaheadPx
            } else {
                prefetchRect.top -= lookaheadPx
            }
        }
        return displayScale
    }

    private fun getPositionX(positionX: Float): Float {
        if (currentScale < 1) {
            return 0f
        }
        val maxPositionX = halfWidth * (currentScale - 1)
        return positionX.coerceIn(-maxPositionX, maxPositionX)
    }

    private fun getPositionY(positionY: Float): Float {
        if (currentScale < 1) {
            return (originalHeight / 2 - halfHeight).toFloat()
        }
        val maxPositionY = halfHeight * (currentScale - 1)
        return positionY.coerceIn(-maxPositionY, maxPositionY)
    }

    private fun zoom(
        fromRate: Float,
        toRate: Float,
        fromX: Float,
        toX: Float,
        fromY: Float,
        toY: Float,
    ) {
        isZooming = true
        val animatorSet = AnimatorSet()
        val translationXAnimator = ValueAnimator.ofFloat(fromX, toX)
        translationXAnimator.addUpdateListener { animation ->
            x = animation.animatedValue as Float
            updateChildViewports()
        }

        val translationYAnimator = ValueAnimator.ofFloat(fromY, toY)
        translationYAnimator.addUpdateListener { animation ->
            y = animation.animatedValue as Float
            updateChildViewports()
        }

        val scaleAnimator = ValueAnimator.ofFloat(fromRate, toRate)
        scaleAnimator.addUpdateListener { animation ->
            currentScale = animation.animatedValue as Float
            setScaleRate(currentScale)
        }
        animatorSet.playTogether(translationXAnimator, translationYAnimator, scaleAnimator)
        animatorSet.duration = ANIMATOR_DURATION_TIME.toLong()
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
        animatorSet.doOnEnd {
            isZooming = false
            currentScale = toRate
            updateChildViewports()
        }
    }

    fun zoomFling(velocityX: Int, velocityY: Int): Boolean {
        if (currentScale <= 1f) return false

        val distanceTimeFactor = 0.4f
        val animatorSet = AnimatorSet()

        if (velocityX != 0) {
            val dx = distanceTimeFactor * velocityX / 2
            val newX = getPositionX(x + dx)
            val translationXAnimator = ValueAnimator.ofFloat(x, newX)
            translationXAnimator.addUpdateListener { animation ->
                x = getPositionX(
                    animation.animatedValue as Float,
                )
                updateChildViewports()
            }
            animatorSet.play(translationXAnimator)
        }
        if (velocityY != 0 && (atFirstPosition || atLastPosition)) {
            val dy = distanceTimeFactor * velocityY / 2
            val newY = getPositionY(y + dy)
            val translationYAnimator = ValueAnimator.ofFloat(y, newY)
            translationYAnimator.addUpdateListener { animation ->
                y = getPositionY(
                    animation.animatedValue as Float,
                )
                updateChildViewports()
            }
            animatorSet.play(translationYAnimator)
        }

        animatorSet.duration = 400
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()

        return true
    }

    private fun zoomScrollBy(dx: Int, dy: Int) {
        if (dx != 0) {
            x = getPositionX(x + dx)
        }
        if (dy != 0) {
            y = getPositionY(y + dy)
        }
        if (dx != 0 || dy != 0) {
            updateChildViewports()
        }
    }

    /**
     * A-M10: notified on every scale change (pinch frames and the double-tap zoom animator both
     * funnel through [setScaleRate]) so the viewer can resume auto-scroll once zoomed back out -
     * the zoom monitor pauses it and nothing ever resumed it in the webtoon branch.
     */
    var onZoomScaleChanged: ((scale: Float) -> Unit)? = null

    private fun setScaleRate(rate: Float, pushViewports: Boolean = true) {
        scaleX = rate
        scaleY = rate
        onZoomScaleChanged?.invoke(rate)
        if (pushViewports) {
            updateChildViewports()
        }
    }

    fun onScale(scaleFactor: Float) {
        currentScale *= scaleFactor
        currentScale = currentScale.coerceIn(
            minRate,
            MAX_SCALE_RATE,
        )

        setScaleRate(currentScale, pushViewports = false)

        layoutParams.height = if (currentScale < 1) {
            (originalHeight / currentScale).toInt()
        } else {
            originalHeight
        }
        halfHeight = layoutParams.height / 2

        if (currentScale != DEFAULT_RATE) {
            x = getPositionX(x)
            y = getPositionY(y)
        } else {
            x = 0f
            y = 0f
        }

        requestLayout()
        updateChildViewports()
    }

    fun onScaleBegin() {
        if (detector.isDoubleTapping) {
            detector.isQuickScaling = true
        }
    }

    fun onScaleEnd() {
        if (scaleX < minRate) {
            zoom(currentScale, minRate, x, 0f, y, 0f)
        }
    }

    inner class GestureListener : GestureDetectorWithLongTap.Listener() {

        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            tapListener?.invoke(ev)
            return false
        }

        override fun onDoubleTap(ev: MotionEvent): Boolean {
            detector.isDoubleTapping = true
            return false
        }

        fun onDoubleTapConfirmed(ev: MotionEvent) {
            if (!isZooming && doubleTapZoom) {
                if (scaleX != DEFAULT_RATE) {
                    zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
                    layoutParams.height = originalHeight
                    halfHeight = layoutParams.height / 2
                    requestLayout()
                } else {
                    val toScale = 2f
                    val toX = (halfWidth - ev.x) * (toScale - 1)
                    val toY = (halfHeight - ev.y) * (toScale - 1)
                    zoom(DEFAULT_RATE, toScale, 0f, toX, 0f, toY)
                }
            }
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            if (longTapListener?.invoke(ev) == true) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    inner class Detector : GestureDetectorWithLongTap(context, listener) {

        private var scrollPointerId = 0
        private var downX = 0
        private var downY = 0
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var isZoomDragging = false
        var isDoubleTapping = false
        var isQuickScaling = false

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val action = ev.actionMasked
            val actionIndex = ev.actionIndex

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    scrollPointerId = ev.getPointerId(0)
                    downX = (ev.x + 0.5f).toInt()
                    downY = (ev.y + 0.5f).toInt()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrollPointerId = ev.getPointerId(actionIndex)
                    downX = (ev.getX(actionIndex) + 0.5f).toInt()
                    downY = (ev.getY(actionIndex) + 0.5f).toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDoubleTapping && isQuickScaling) {
                        return true
                    }

                    val index = ev.findPointerIndex(scrollPointerId)
                    if (index < 0) {
                        return false
                    }

                    val x = (ev.getX(index) + 0.5f).toInt()
                    val y = (ev.getY(index) + 0.5f).toInt()
                    var dx = x - downX
                    var dy = if (atFirstPosition || atLastPosition) y - downY else 0

                    if (!isZoomDragging && currentScale > 1f) {
                        var startScroll = false

                        if (abs(dx) > touchSlop) {
                            if (dx < 0) {
                                dx += touchSlop
                            } else {
                                dx -= touchSlop
                            }
                            startScroll = true
                        }
                        if (abs(dy) > touchSlop) {
                            if (dy < 0) {
                                dy += touchSlop
                            } else {
                                dy -= touchSlop
                            }
                            startScroll = true
                        }

                        if (startScroll) {
                            isZoomDragging = true
                        }
                    }

                    if (isZoomDragging) {
                        zoomScrollBy(dx, dy)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDoubleTapping && !isQuickScaling) {
                        listener.onDoubleTapConfirmed(ev)
                    }
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}

private const val ANIMATOR_DURATION_TIME = 200
private const val MIN_RATE = 0.5f
private const val DEFAULT_RATE = 1f
private const val MAX_SCALE_RATE = 3f
private const val MIN_LOOKAHEAD_FACTOR = 0.5f
private const val MAX_LOOKAHEAD_FACTOR = 1.0f
private const val RETAIN_VIEWPORT_MARGIN = 0.5f
