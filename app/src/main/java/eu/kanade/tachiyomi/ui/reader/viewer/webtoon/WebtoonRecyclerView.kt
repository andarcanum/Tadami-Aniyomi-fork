package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

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
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ViewMotionFrameRateBooster
import eu.kanade.tachiyomi.ui.reader.viewer.WebtoonViewportPushCoalescer
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
    private var activeZoomAnimator: ValueAnimator? = null
    private var activeFlingAnimator: ValueAnimator? = null
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
    private val childVisibleRect = RectF()
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

    private val frameRateBooster = ViewMotionFrameRateBooster(this)
    private val viewportCoalescer = WebtoonViewportPushCoalescer()

    internal val isZoomedIn: Boolean
        get() = currentScale > DEFAULT_RATE

    internal fun notifyExternalScrollMotion(active: Boolean) {
        if (active) {
            frameRateBooster.onMotionStart()
        } else if (scrollState == SCROLL_STATE_IDLE && activeZoomAnimator == null && activeFlingAnimator == null) {
            frameRateBooster.onMotionIdle()
        }
    }

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
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> frameRateBooster.onMotionStart()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (scrollState == SCROLL_STATE_IDLE && activeZoomAnimator == null && activeFlingAnimator == null) {
                    frameRateBooster.onMotionIdle()
                }
            }
        }
        detector.onTouchEvent(e)
        return super.onTouchEvent(e)
    }

    override fun onDetachedFromWindow() {
        cancelActiveAnimators()
        frameRateBooster.releaseImmediately()
        super.onDetachedFromWindow()
    }

    private fun cancelActiveAnimators() {
        activeZoomAnimator?.cancel()
        activeZoomAnimator = null
        activeFlingAnimator?.cancel()
        activeFlingAnimator = null
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        if (currentScale > DEFAULT_RATE) {
            val layoutManager = layoutManager as? LinearLayoutManager
            if (layoutManager != null) {
                lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            }
        }
        if (dy != 0 || dx != 0) {
            frameRateBooster.onMotionStart()
        }
        lastScrollDy = dy
        val shouldPush = viewportCoalescer.onScrollDelta(
            dy = dy,
            viewportHeightPx = height.toFloat(),
            density = resources.displayMetrics.density,
            scale = currentScale,
        )
        if (shouldPush) {
            updateChildViewports()
        }
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
                viewportCoalescer.resetVelocity()
                viewportCoalescer.markForcedPush(currentScale)
                updateChildViewports()
                if (activeZoomAnimator == null && activeFlingAnimator == null) {
                    frameRateBooster.onMotionIdle()
                }
            }
            SCROLL_STATE_DRAGGING -> {
                lastFlingVelocityY = 0
                frameRateBooster.onMotionStart()
                viewportCoalescer.markForcedPush(currentScale)
                updateChildViewports()
            }
            SCROLL_STATE_SETTLING -> {
                frameRateBooster.onMotionStart()
            }
        }
    }

    override fun fling(velocityX: Int, velocityY: Int): Boolean {
        lastFlingVelocityY = velocityY
        frameRateBooster.onMotionStart()
        val flung = super.fling(velocityX, velocityY)
        viewportCoalescer.markForcedPush(currentScale)
        updateChildViewports()
        return flung
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateChildViewports()
    }

    override fun onChildAttachedToWindow(child: View) {
        super.onChildAttachedToWindow(child)
        val displayScale = computeViewportBounds()
        if (displayScale > 0f) {
            pushViewportToHolderChild(child, displayScale)
        }
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

        childVisibleRect.set(visibleRect)
        childVisibleRect.offset(-offsetX, -offsetY)
        if (!childVisibleRect.intersect(0f, 0f, childW.toFloat(), childH.toFloat())) {
            childVisibleRect.setEmpty()
        }

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

        target.updateViewport(childVisibleRect, childPrefetchRect, childRetainRect, displayScale)
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
            val lookaheadFactor = viewportCoalescer.computeSmoothedLookaheadFactor(
                isSettling = scrollState == SCROLL_STATE_SETTLING,
                flingVelocityY = lastFlingVelocityY,
                viewportHeightPx = viewportHeight,
                minFactor = MIN_LOOKAHEAD_FACTOR,
                maxFactor = MAX_LOOKAHEAD_FACTOR,
            )
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
        cancelActiveAnimators()
        frameRateBooster.onMotionStart()
        isZooming = true
        var cancelled = false
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATOR_DURATION_TIME.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                x = fromX + (toX - fromX) * fraction
                y = fromY + (toY - fromY) * fraction
                currentScale = fromRate + (toRate - fromRate) * fraction
                setScaleRate(currentScale, pushViewports = false)
                updateChildViewports()
            }
            doOnCancel {
                cancelled = true
            }
            doOnEnd {
                isZooming = false
                if (activeZoomAnimator === this) {
                    activeZoomAnimator = null
                }
                if (!cancelled) {
                    x = toX
                    y = toY
                    currentScale = toRate
                    setScaleRate(currentScale, pushViewports = false)
                    viewportCoalescer.markForcedPush(currentScale)
                    updateChildViewports()
                }
                if (scrollState == SCROLL_STATE_IDLE && activeFlingAnimator == null) {
                    frameRateBooster.onMotionIdle()
                }
            }
        }
        activeZoomAnimator = animator
        animator.start()
    }

    fun zoomFling(velocityX: Int, velocityY: Int): Boolean {
        if (currentScale <= 1f) return false

        val animateX = velocityX != 0
        val animateY = velocityY != 0 && (atFirstPosition || atLastPosition)
        if (!animateX && !animateY) return false

        cancelActiveAnimators()
        frameRateBooster.onMotionStart()

        val distanceTimeFactor = 0.4f
        val startX = x
        val startY = y
        val targetX = if (animateX) getPositionX(startX + distanceTimeFactor * velocityX / 2) else startX
        val targetY = if (animateY) getPositionY(startY + distanceTimeFactor * velocityY / 2) else startY

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                if (animateX) {
                    x = getPositionX(startX + (targetX - startX) * fraction)
                }
                if (animateY) {
                    y = getPositionY(startY + (targetY - startY) * fraction)
                }
                updateChildViewports()
            }
            doOnEnd {
                if (activeFlingAnimator === this) {
                    activeFlingAnimator = null
                }
                updateChildViewports()
                if (scrollState == SCROLL_STATE_IDLE && activeZoomAnimator == null) {
                    frameRateBooster.onMotionIdle()
                }
            }
        }
        activeFlingAnimator = animator
        animator.start()

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

        val newHeight = if (currentScale < 1) {
            (originalHeight / currentScale).toInt()
        } else {
            originalHeight
        }
        val heightChanged = layoutParams.height != newHeight
        if (heightChanged) {
            layoutParams.height = newHeight
            halfHeight = newHeight / 2
        }

        if (currentScale != DEFAULT_RATE) {
            x = getPositionX(x)
            y = getPositionY(y)
        } else {
            x = 0f
            y = 0f
        }

        if (heightChanged) {
            requestLayout()
            post {
                if (isAttachedToWindow) {
                    updateChildViewports()
                }
            }
        }
        updateChildViewports()
    }

    fun onScaleBegin() {
        cancelActiveAnimators()
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
                    if (layoutParams.height != originalHeight) {
                        layoutParams.height = originalHeight
                        halfHeight = layoutParams.height / 2
                        requestLayout()
                    }
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
                    cancelActiveAnimators()
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
