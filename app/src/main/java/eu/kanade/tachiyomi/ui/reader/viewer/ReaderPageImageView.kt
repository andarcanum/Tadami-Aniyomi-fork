package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.tachiyomi.data.coil.AnimatedNativeDrawable
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerSubsamplingImageView
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonBorderDetector
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import java.io.Closeable

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    private var pageView: View? = null

    private var config: Config? = null

    private var scope: CoroutineScope? = null
    private var smartFitJob: Job? = null
    private var pendingSmartFit: PendingSmartFit? = null
    private var pendingAnimatedImage: PendingSmartFit? = null
    private var landscapeZoomRunnable: Runnable? = null

    /**
     * In-flight Coil request for the current image. Tracked so a rebind (or recycle) cancels it:
     * an orphaned request's onError/onSuccess used to land on the NEXT page bound into this view,
     * marking a healthy page as failed or overwriting the new image.
     */
    private var coilDisposable: Disposable? = null

    private fun cancelCoilRequest() {
        coilDisposable?.dispose()
        coilDisposable = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        (pageView as? SubsamplingScaleImageView)?.setHardwareTilesEnabled(isHardwareAccelerated)
        val pending = pendingSmartFit
        if (pending != null && smartFitJob == null) {
            setNonAnimatedImage(pending.data, pending.config)
        }
        val pendingAnim = pendingAnimatedImage
        if (pendingAnim != null && coilDisposable == null) {
            setAnimatedImage(pendingAnim.data, pendingAnim.config)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
        scope = null
        smartFitJob?.cancel()
        smartFitJob = null
        cancelCoilRequest()
        cancelLandscapeZoom()
    }

    private fun cancelLandscapeZoom() {
        landscapeZoomRunnable?.let { handler?.removeCallbacks(it) }
        landscapeZoomRunnable = null
    }

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: (() -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null
    var onHdrChanged: ((Boolean) -> Unit)? = null

    val isHdr: Boolean
        get() = when (val view = pageView) {
            is SubsamplingScaleImageView -> view.isHdr
            is AppCompatImageView -> (view.drawable as? AnimatedNativeDrawable)?.isHdr == true
            else -> false
        }

    /**
     * For automatic background. Will be set as background color when [onImageLoaded] is called.
     */
    var pageBackground: Drawable? = null

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
    }

    @CallSuper
    open fun onImageLoadError() {
        onImageLoadError?.invoke()
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)
    }

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    open fun onPageSelected(forward: Boolean) {
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                (this as? PagerSubsamplingImageView)?.applySmartScaleIfNeeded()
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            (this@with as? PagerSubsamplingImageView)?.applySmartScaleIfNeeded()
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onHdrChanged?.invoke(isHdr)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError()
                        }
                    },
                )
            }
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        val isCenterInsideOrSmart = config != null && (
            config!!.minimumScaleType == SCALE_TYPE_CENTER_INSIDE ||
                config!!.minimumScaleType == SubsamplingScaleImageView.SCALE_TYPE_SMART_FIT
            )
        if (config != null &&
            config!!.landscapeZoom &&
            isCenterInsideOrSmart &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            // Track the delayed runnable: a stale callback firing after a fast page flip or a
            // detach used to zoom the wrong/recycled view.
            cancelLandscapeZoom()
            val runnable = Runnable {
                landscapeZoomRunnable = null
                val point = when (config!!.zoomStartPosition) {
                    ZoomStartPosition.LEFT -> if (forward) {
                        PointF(0F, 0F)
                    } else {
                        PointF(
                            sWidth.toFloat(),
                            0F,
                        )
                    }
                    ZoomStartPosition.RIGHT -> if (forward) {
                        PointF(sWidth.toFloat(), 0F)
                    } else {
                        PointF(
                            0F,
                            0F,
                        )
                    }
                    ZoomStartPosition.CENTER -> center
                }

                val targetScale = height.toFloat() / sHeight.toFloat()
                animateScaleAndCenter(targetScale, point)!!
                    .withDuration(500)
                    .withEasing(EASE_IN_OUT_QUAD)
                    .withInterruptible(true)
                    .start()
            }
            landscapeZoomRunnable = runnable
            handler?.postDelayed(runnable, 500)
        }
    }

    fun setImage(drawable: Drawable, config: Config) {
        this.config = config
        pendingSmartFit = null
        pendingAnimatedImage = null
        smartFitJob?.cancel()
        smartFitJob = null
        cancelCoilRequest()
        if (drawable is Animatable) {
            prepareAnimatedImageView()
            setAnimatedImage(drawable, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(drawable, config)
        }
    }

    fun setImage(source: BufferedSource, isAnimated: Boolean, config: Config) {
        this.config = config
        pendingSmartFit = null
        pendingAnimatedImage = null
        smartFitJob?.cancel()
        smartFitJob = null
        cancelCoilRequest()
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    open fun recycle() {
        pendingSmartFit = null
        pendingAnimatedImage = null
        smartFitJob?.cancel()
        smartFitJob = null
        cancelLandscapeZoom()
        cancelCoilRequest()
        onHdrChanged?.invoke(false)
        pageView?.let {
            when (it) {
                is SubsamplingScaleImageView -> it.recycle()
                is AppCompatImageView -> releaseAnimatedDrawable(it)
            }
            it.isVisible = false
        }
    }

    private fun releaseAnimatedDrawable(view: AppCompatImageView) {
        val current = view.drawable
        view.setImageDrawable(null)
        (current as? Animatable)?.stop()
        (current as? Closeable)?.close()
        view.dispose()
    }

    fun trimMemory(level: Int) {
        (pageView as? SubsamplingScaleImageView)?.trimMemory(level)
    }

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->

            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        (pageView as? AppCompatImageView)?.let(::releaseAnimatedDrawable)
        removeView(pageView)

        pageView = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            PagerSubsamplingImageView(context)
        }.apply {
            setMaxTileSize(minOf(ImageUtil.hardwareBitmapThreshold, 2048))
            setHardwareTilesEnabled(this@ReaderPageImageView.isHardwareAccelerated)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        // Not used
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        val baseScale = if (this is PagerSubsamplingImageView && isSmartFitEnabled) minScale else scale
        // 5x zoom
        maxScale = baseScale * MAX_ZOOM_SCALE
        setDoubleTapZoomScale(baseScale * 2)

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(baseScale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(baseScale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(
                baseScale,
                center ?: PointF(sWidth / 2F, sHeight / 2F),
            )
            null -> {}
        }
    }

    private fun setNonAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        val isSmartFit = config.minimumScaleType == SubsamplingScaleImageView.SCALE_TYPE_SMART_FIT
        (this as? PagerSubsamplingImageView)?.isSmartFitEnabled = isSmartFit

        setZoomEnabled(config.enablePinchToZoom)
        setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
        setHardwareTilesEnabled(this@ReaderPageImageView.isHardwareAccelerated)
        if (isSmartFit) {
            val initialType = if (width > 0 && height > 0 && isReady) {
                ReaderScaleUtil.resolveSmartScaleType(width, height, sWidth, sHeight)
            } else {
                SCALE_TYPE_CENTER_INSIDE
            }
            setMinimumScaleType(initialType)
        } else {
            setMinimumScaleType(config.minimumScaleType)
        }
        setMinimumDpi(1) // Just so that very small image will be fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    (this@apply as? PagerSubsamplingImageView)?.applySmartScaleIfNeeded()
                    setupZoom(config)
                    if (isVisibleOnScreen()) landscapeZoom(true)
                    this@ReaderPageImageView.onHdrChanged?.invoke(isHdr)
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError()
                }
            },
        )

        when (data) {
            is BitmapDrawable -> {
                val bitmap = data.bitmap
                val precomputed = config.precomputedSmartFitBounds
                if (precomputed != null) {
                    val image = ImageSource.bitmap(bitmap)
                    if (!precomputed.isEmpty) image.region(precomputed)
                    setImage(image)
                    isVisible = true
                } else if (config.webtoonSmartFit) {
                    pendingSmartFit = PendingSmartFit(data, config)
                    scope?.let { activeScope ->
                        smartFitJob = activeScope.launch {
                            val bounds = withContext(Dispatchers.Default) {
                                WebtoonBorderDetector.detectContentBounds(bitmap)
                            }
                            pendingSmartFit = null
                            smartFitJob = null
                            val image = ImageSource.bitmap(bitmap)
                            if (!bounds.isEmpty) image.region(bounds)
                            setImage(image)
                            isVisible = true
                        }
                    }
                } else {
                    setImage(ImageSource.bitmap(bitmap))
                    isVisible = true
                }
            }
            is BufferedSource -> {
                // Preserve original pixels for zoom, even for short webtoon pages. A fixed Coil
                // viewport bitmap cannot recover details when its RecyclerView parent is scaled.
                val precomputed = config.precomputedSmartFitBounds
                if (precomputed != null) {
                    val image = ImageSource.inputStream(data.inputStream())
                    if (!precomputed.isEmpty) image.region(precomputed)
                    setImage(image)
                    isVisible = true
                } else if (config.webtoonSmartFit) {
                    pendingSmartFit = PendingSmartFit(data, config)
                    scope?.let { activeScope ->
                        smartFitJob = activeScope.launch {
                            val bounds = withContext(Dispatchers.IO) {
                                WebtoonBorderDetector.detectContentBounds(
                                    data.peek().inputStream(),
                                    config.cropBorders,
                                )
                            }
                            pendingSmartFit = null
                            smartFitJob = null
                            val image = ImageSource.inputStream(data.inputStream())
                            if (!bounds.isEmpty) image.region(bounds)
                            setImage(image)
                            isVisible = true
                        }
                    }
                } else {
                    setImage(ImageSource.inputStream(data.inputStream()))
                    isVisible = true
                }
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        (pageView as? SubsamplingScaleImageView)?.recycle()
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force 2 scale levels on double tap
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged(scale)
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? AppCompatImageView)?.apply {
        if (this is PhotoView) {
            setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
            isZoomable = config.enablePinchToZoom
        }

        if (drawable !== data) {
            releaseAnimatedDrawable(this)
        }
        this@ReaderPageImageView.pendingAnimatedImage = PendingSmartFit(data, config)
        val requestData = if (data is BufferedSource) data.peek() else data
        val request = ImageRequest.Builder(context)
            .data(requestData)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    this@ReaderPageImageView.pendingAnimatedImage = null
                    this@ReaderPageImageView.coilDisposable = null
                    val newDrawable = result.asDrawable(context.resources)
                    if (drawable !== newDrawable) {
                        releaseAnimatedDrawable(this)
                    }
                    setImageDrawable(newDrawable)
                    (newDrawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onHdrChanged?.invoke(
                        (newDrawable as? AnimatedNativeDrawable)?.isHdr == true,
                    )
                    this@ReaderPageImageView.onImageLoaded()
                },
                onError = {
                    this@ReaderPageImageView.pendingAnimatedImage = null
                    this@ReaderPageImageView.coilDisposable = null
                    this@ReaderPageImageView.onHdrChanged?.invoke(false)
                    this@ReaderPageImageView.onImageLoadError()
                },
            )
            .crossfade(false)
            .build()
        val disposable = context.imageLoader.enqueue(request)
        if (this@ReaderPageImageView.pendingAnimatedImage != null) {
            coilDisposable = disposable
        }
    }

    private fun Int.getSystemScaledDuration(): Int {
        return (this * context.animatorDurationScale).toInt().coerceAtLeast(1)
    }

    /**
     * All of the config except [zoomDuration] will only be used for non-animated image.
     */
    data class Config(
        val zoomDuration: Int,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val webtoonSmartFit: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
        val enablePinchToZoom: Boolean = true,
        val precomputedSmartFitBounds: Rect? = null,
    )

    private data class PendingSmartFit(
        val data: Any,
        val config: Config,
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }

    fun getImageView(): View? = pageView
}

private const val MAX_ZOOM_SCALE = 5F

/**
 * Result of a holder's page-image preprocessing (dual-page split / rotate-to-fit).
 * [Decoded] lets those operations hand a bitmap directly to the image view, skipping the
 * historical JPEG q=100 re-encode plus second decode round trip; [Encoded] is the untouched
 * (or re-encoded fallback) byte stream.
 */
internal sealed interface ProcessedPageImage {
    data class Encoded(val source: BufferedSource) : ProcessedPageImage
    data class Decoded(val bitmap: android.graphics.Bitmap) : ProcessedPageImage
}
