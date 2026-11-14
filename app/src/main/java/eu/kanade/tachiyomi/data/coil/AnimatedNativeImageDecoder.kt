package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Half
import androidx.core.graphics.withClip
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import ca.mpreg.imagedecoder.ImageDecoder
import coil3.DrawableImage
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.gif.AnimatedTransformation
import coil3.gif.PixelOpacity
import coil3.gif.animatedTransformation
import coil3.gif.animationEndCallback
import coil3.gif.animationStartCallback
import coil3.gif.repeatCount
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okio.BufferedSource
import tachiyomi.core.common.util.image.ImageHeader
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * A [Decoder] using `ca.mpreg.imagedecoder.ImageDecoder` for animated images that Android's built-in
 * `AnimatedImageDecoder` cannot decode everywhere (animated AVIF, HEIF, JXL, and pre-API 28 WebP).
 */
class AnimatedNativeImageDecoder(
    private val resources: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val decoder = runInterruptible {
            resources.use { imageSource ->
                imageSource.source().inputStream().use { stream ->
                    ImageDecoder.open(stream)
                }
            }
        }

        try {
            if (!decoder.isAnimated) {
                var sampleSize = 1
                val bitmap = try {
                    runInterruptible {
                        val (dstWidth, dstHeight) = options.computeDstDimensions(decoder.width, decoder.height)
                        sampleSize = DecodeUtils.calculateInSampleSize(
                            srcWidth = decoder.width,
                            srcHeight = decoder.height,
                            dstWidth = dstWidth,
                            dstHeight = dstHeight,
                            scale = options.scale,
                        )
                        decoder.decodeToBitmap(sampleSize = sampleSize)
                    }
                } finally {
                    decoder.close()
                }
                return DecodeResult(
                    image = bitmap.asImage(),
                    isSampled = sampleSize > 1,
                )
            }

            val srcWidth = decoder.width
            val srcHeight = decoder.height
            val (dstWidth, dstHeight) = options.computeDstDimensions(srcWidth, srcHeight)
            val multiplier = DecodeUtils.computeSizeMultiplier(
                srcWidth = srcWidth.toDouble(),
                srcHeight = srcHeight.toDouble(),
                dstWidth = dstWidth.toDouble(),
                dstHeight = dstHeight.toDouble(),
                scale = options.scale,
                maxSize = options.maxBitmapSize,
            )
            val effectiveMultiplier = if (options.precision == Precision.EXACT) {
                multiplier
            } else {
                multiplier.coerceAtMost(1.0)
            }
            val targetWidth = (srcWidth * effectiveMultiplier).roundToInt().coerceAtLeast(1)
            val targetHeight = (srcHeight * effectiveMultiplier).roundToInt().coerceAtLeast(1)
            val isSampled = targetWidth < srcWidth || targetHeight < srcHeight

            val source = ImageDecoderAnimationSource(decoder)
            val firstFrameMeta = runInterruptible {
                val pageCount = runCatching { decoder.pages }.getOrDefault(1)
                val frame = source.decodeNext()
                try {
                    val isHdr = decoder.isHdr
                    val config = if (isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
                    val displayBitmap = Bitmap.createBitmap(targetWidth, targetHeight, config)
                    try {
                        val intScratch = if (isHdr) null else IntArray(targetWidth * targetHeight)
                        val hdrScratch = if (isHdr) {
                            ByteBuffer.allocateDirect(targetWidth * targetHeight * 8).order(ByteOrder.nativeOrder())
                        } else {
                            null
                        }
                        unpackFrame(frame, isHdr, targetWidth, targetHeight, intScratch, hdrScratch)
                        uploadFrameToBitmap(
                            displayBitmap = displayBitmap,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                            isHdr = isHdr,
                            intScratch = intScratch,
                            hdrScratch = hdrScratch,
                        )
                        val transformation = options.animatedTransformation
                        val pixelOpacity = transformation?.transform(Canvas(displayBitmap)) ?: PixelOpacity.UNCHANGED
                        FirstFrameInit(
                            bitmap = displayBitmap,
                            intScratch = intScratch,
                            hdrScratch = hdrScratch,
                            pageCount = pageCount,
                            pixelOpacity = pixelOpacity,
                            index = frame.index,
                            last = isLastFrame(frame.index, pageCount),
                            duration = frame.duration,
                        )
                    } catch (error: Throwable) {
                        displayBitmap.recycle()
                        throw error
                    }
                } finally {
                    frame.close()
                }
            }

            val drawable = AnimatedNativeDrawable(
                source = source,
                displayBitmap = firstFrameMeta.bitmap,
                intScratch = firstFrameMeta.intScratch,
                hdrScratch = firstFrameMeta.hdrScratch,
                isHdr = decoder.isHdr,
                pageCount = firstFrameMeta.pageCount,
                initialIndex = firstFrameMeta.index,
                initialLast = firstFrameMeta.last,
                initialDurationMs = firstFrameMeta.duration,
                scale = options.scale,
                animatedTransformation = options.animatedTransformation,
                pixelOpacity = firstFrameMeta.pixelOpacity,
            )

            // Encoded loop count is not exposed by ca.mpreg:imagedecoder; fall back to infinite repeat.
            if (options.repeatCount != AnimatedImageDecoder.ENCODED_LOOP_COUNT) {
                drawable.setRepeatCount(options.repeatCount)
            }

            val onStart = options.animationStartCallback
            val onEnd = options.animationEndCallback
            if (onStart != null || onEnd != null) {
                withContext(Dispatchers.Main.immediate) {
                    drawable.registerAnimationCallback(
                        object : Animatable2Compat.AnimationCallback() {
                            override fun onAnimationStart(d: Drawable) {
                                onStart?.invoke()
                            }

                            override fun onAnimationEnd(d: Drawable) {
                                onEnd?.invoke()
                            }
                        },
                    )
                }
            }

            return DecodeResult(
                image = drawable.asImage(),
                isSampled = isSampled,
            )
        } catch (error: Throwable) {
            decoder.close()
            throw error
        }
    }

    private class FirstFrameInit(
        val bitmap: Bitmap,
        val intScratch: IntArray?,
        val hdrScratch: ByteBuffer?,
        val pageCount: Int,
        val pixelOpacity: PixelOpacity,
        val index: Int,
        val last: Boolean,
        val duration: Int,
    )

    private class ImageDecoderAnimationSource(
        private val decoder: ImageDecoder,
    ) : NativeAnimationSource {
        override fun decodeNext(): NativeFrameData {
            val frame = decoder.decodeNext()
            return NativeFrameData(
                width = frame.width,
                height = frame.height,
                image = frame.image,
                index = frame.index,
                duration = frame.duration,
                closeable = frame,
            )
        }

        override fun close() = decoder.close()
    }

    class Factory : Decoder.Factory {

        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            if (!isApplicable(result.source.source())) return null
            return AnimatedNativeImageDecoder(result.source, options)
        }

        private fun isApplicable(source: BufferedSource): Boolean {
            val info = ImageHeader.read(source.peek().inputStream()) ?: return false
            return when (info.format) {
                ImageHeader.Format.AVIF,
                ImageHeader.Format.HEIF,
                -> info.animated
                ImageHeader.Format.WEBP -> info.animated && Build.VERSION.SDK_INT < Build.VERSION_CODES.P
                ImageHeader.Format.JXL -> {
                    try {
                        source.peek().inputStream().use { stream ->
                            ImageDecoder.open(stream).use { it.isAnimated }
                        }
                    } catch (_: Exception) {
                        false
                    } catch (_: LinkageError) {
                        false
                    }
                }
                else -> false
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}

internal class NativeFrameData(
    val width: Int,
    val height: Int,
    val image: ByteBuffer,
    val index: Int,
    val duration: Int,
    private val closeable: Closeable? = null,
) : Closeable {
    override fun close() {
        closeable?.close()
    }
}

internal interface NativeAnimationSource : Closeable {
    fun decodeNext(): NativeFrameData
}

class AnimatedNativeDrawable internal constructor(
    private val source: NativeAnimationSource,
    private val displayBitmap: Bitmap,
    private var intScratch: IntArray?,
    private var hdrScratch: ByteBuffer?,
    val isHdr: Boolean,
    private val pageCount: Int,
    initialIndex: Int,
    initialLast: Boolean,
    initialDurationMs: Int,
    private val scale: Scale,
    private val animatedTransformation: AnimatedTransformation?,
    private val pixelOpacity: PixelOpacity,
) : Drawable(), Animatable2Compat, DrawableImage.SizeProvider, Closeable {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val callbacks = CopyOnWriteArrayList<Animatable2Compat.AnimationCallback>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val decoderLock = Any()
    private val bitmapLock = Any()
    private val currentBounds = Rect()
    private val destRect = RectF()
    private val displayCanvas = Canvas(displayBitmap)
    private val intrinsicW = displayBitmap.width
    private val intrinsicH = displayBitmap.height
    private val closed = AtomicBoolean(false)

    private var playJob: Job? = null

    @Volatile
    private var isRunning = false
    private var shouldResumeOnVisible = false
    private var startDispatched = false
    private var repeatCount = REPEAT_INFINITE
    private var completedPasses = 0
    private var currentFrameIndex = initialIndex
    private var currentFrameWasLast = initialLast
    private var currentDurationMs = initialDurationMs

    override val size: Long
        get() = if (closed.get() || displayBitmap.isRecycled) 0L else displayBitmap.allocationByteCount.toLong()

    fun setRepeatCount(repeatCount: Int) {
        require(repeatCount >= REPEAT_INFINITE) { "Invalid repeatCount: $repeatCount" }
        this.repeatCount = repeatCount
    }

    override fun draw(canvas: Canvas) {
        if (closed.get()) return
        synchronized(bitmapLock) {
            if (closed.get() || displayBitmap.isRecycled) return
            updateBounds(bounds)
            if (destRect.isEmpty) return
            canvas.withClip(bounds) {
                drawBitmap(displayBitmap, null, destRect, paint)
            }
        }
    }

    private fun updateBounds(bounds: Rect) {
        if (currentBounds == bounds) return
        currentBounds.set(bounds)
        val boundsW = bounds.width()
        val boundsH = bounds.height()
        if (boundsW <= 0 || boundsH <= 0 || intrinsicW <= 0 || intrinsicH <= 0) {
            destRect.setEmpty()
            return
        }
        val factor = DecodeUtils.computeSizeMultiplier(
            srcWidth = intrinsicW,
            srcHeight = intrinsicH,
            dstWidth = boundsW,
            dstHeight = boundsH,
            scale = scale,
            maxSize = Size.ORIGINAL,
        ).toFloat()
        val drawW = factor * intrinsicW
        val drawH = factor * intrinsicH
        val left = bounds.left + (boundsW - drawW) / 2f
        val top = bounds.top + (boundsH - drawH) / 2f
        destRect.set(left, top, left + drawW, top + drawH)
    }

    override fun getIntrinsicWidth(): Int = intrinsicW

    override fun getIntrinsicHeight(): Int = intrinsicH

    override fun setAlpha(alpha: Int) {
        require(alpha in 0..255) { "Invalid alpha: $alpha" }
        paint.alpha = alpha
    }

    override fun getAlpha(): Int = paint.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getColorFilter(): ColorFilter? = paint.colorFilter

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return if (paint.alpha == 255 && pixelOpacity == PixelOpacity.OPAQUE) {
            PixelFormat.OPAQUE
        } else {
            PixelFormat.TRANSLUCENT
        }
    }

    override fun isRunning(): Boolean = isRunning

    override fun start() {
        if (closed.get()) return
        if (!isVisible) {
            shouldResumeOnVisible = true
            return
        }
        if (isRunning) return
        shouldResumeOnVisible = false
        completedPasses = 0
        isRunning = true
        if (!startDispatched) {
            startDispatched = true
            callbacks.forEach { it.onAnimationStart(this) }
        }
        launchPlayback()
    }

    override fun stop() {
        shouldResumeOnVisible = false
        startDispatched = false
        if (!isRunning) return
        pausePlayback()
        callbacks.forEach { it.onAnimationEnd(this) }
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (closed.get()) return changed
        if (!visible) {
            if (isRunning) {
                shouldResumeOnVisible = true
                pausePlayback()
            }
        } else if (shouldResumeOnVisible || (restart && isRunning)) {
            if (restart && isRunning) {
                pausePlayback()
                completedPasses = 0
            }
            if (!startDispatched) {
                start()
            } else {
                shouldResumeOnVisible = false
                if (!isRunning) {
                    isRunning = true
                    launchPlayback()
                }
            }
        }
        return changed
    }

    private fun pausePlayback() {
        isRunning = false
        playJob?.cancel()
        playJob = null
    }

    private fun launchPlayback() {
        playJob?.cancel()
        playJob = scope.launch {
            while (isActive && isRunning && !closed.get()) {
                val waitUntil = SystemClock.uptimeMillis() + normalizeDuration(currentDurationMs)
                if (currentFrameWasLast) {
                    completedPasses++
                    if (repeatCount != REPEAT_INFINITE && completedPasses > repeatCount) {
                        val remaining = waitUntil - SystemClock.uptimeMillis()
                        if (remaining > 0L) delay(remaining)
                        stop()
                        break
                    }
                }

                val nextFrame = try {
                    withContext(Dispatchers.IO) {
                        synchronized(decoderLock) {
                            val scratchInts = intScratch
                            val scratchHdr = hdrScratch
                            if (closed.get() || !isRunning) return@withContext null
                            val frame = source.decodeNext()
                            try {
                                unpackFrame(
                                    frame = frame,
                                    isHdr = isHdr,
                                    targetWidth = intrinsicW,
                                    targetHeight = intrinsicH,
                                    intScratch = scratchInts,
                                    hdrScratch = scratchHdr,
                                )
                                FrameStep(frame.index, isLastFrame(frame.index, pageCount), frame.duration)
                            } finally {
                                frame.close()
                            }
                        }
                    }
                } catch (_: Exception) {
                    stop()
                    break
                } ?: break

                val remaining = waitUntil - SystemClock.uptimeMillis()
                if (remaining > 0L) delay(remaining)
                if (!isActive || !isRunning || closed.get()) break

                if (!currentFrameWasLast && nextFrame.index <= currentFrameIndex) {
                    completedPasses++
                    if (repeatCount != REPEAT_INFINITE && completedPasses > repeatCount) {
                        stop()
                        break
                    }
                }

                synchronized(bitmapLock) {
                    if (closed.get() || displayBitmap.isRecycled) return@launch
                    uploadFrameToBitmap(
                        displayBitmap = displayBitmap,
                        targetWidth = intrinsicW,
                        targetHeight = intrinsicH,
                        isHdr = isHdr,
                        intScratch = intScratch,
                        hdrScratch = hdrScratch,
                    )
                    animatedTransformation?.transform(displayCanvas)
                }
                currentFrameIndex = nextFrame.index
                currentFrameWasLast = nextFrame.last
                currentDurationMs = nextFrame.duration
                invalidateSelf()
            }
        }
    }

    override fun registerAnimationCallback(callback: Animatable2Compat.AnimationCallback) {
        callbacks.add(callback)
    }

    override fun unregisterAnimationCallback(callback: Animatable2Compat.AnimationCallback): Boolean {
        return callbacks.remove(callback)
    }

    override fun clearAnimationCallbacks() = callbacks.clear()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        shouldResumeOnVisible = false
        startDispatched = false
        isRunning = false
        playJob?.cancel()
        playJob = null
        scope.cancel()
        callbacks.clear()
        synchronized(bitmapLock) {
            if (!displayBitmap.isRecycled) {
                displayBitmap.recycle()
            }
        }
        synchronized(decoderLock) {
            intScratch = null
            hdrScratch = null
            source.close()
        }
    }

    private class FrameStep(val index: Int, val last: Boolean, val duration: Int)

    companion object {
        const val REPEAT_INFINITE = -1
    }
}

private const val MIN_FRAME_DURATION_MS = 16
private const val DEFAULT_FRAME_DURATION_MS = 100

private fun isLastFrame(index: Int, pageCount: Int): Boolean {
    return pageCount > 1 && index >= pageCount - 1
}

private fun normalizeDuration(durationMs: Int): Long {
    return if (durationMs < MIN_FRAME_DURATION_MS) {
        DEFAULT_FRAME_DURATION_MS.toLong()
    } else {
        durationMs.toLong()
    }
}

private fun unpackFrame(
    frame: NativeFrameData,
    isHdr: Boolean,
    targetWidth: Int,
    targetHeight: Int,
    intScratch: IntArray?,
    hdrScratch: ByteBuffer?,
) {
    val src = frame.image
    val srcW = frame.width
    val srcH = frame.height
    if (!isHdr) {
        val out = checkNotNull(intScratch)
        if (targetWidth == srcW && targetHeight == srcH) {
            var offset = 0
            val count = srcW * srcH
            for (i in 0 until count) {
                val r = src.get(offset).toInt() and 0xFF
                val g = src.get(offset + 1).toInt() and 0xFF
                val b = src.get(offset + 2).toInt() and 0xFF
                val a = src.get(offset + 3).toInt() and 0xFF
                out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                offset += 4
            }
        } else {
            var dstIdx = 0
            for (y in 0 until targetHeight) {
                val sy = (y.toLong() * srcH / targetHeight).toInt()
                val rowBase = sy * srcW
                for (x in 0 until targetWidth) {
                    val sx = (x.toLong() * srcW / targetWidth).toInt()
                    val offset = (rowBase + sx) * 4
                    val r = src.get(offset).toInt() and 0xFF
                    val g = src.get(offset + 1).toInt() and 0xFF
                    val b = src.get(offset + 2).toInt() and 0xFF
                    val a = src.get(offset + 3).toInt() and 0xFF
                    out[dstIdx++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
    } else {
        val out = checkNotNull(hdrScratch)
        out.clear()
        for (y in 0 until targetHeight) {
            val sy = if (targetHeight == srcH) y else (y.toLong() * srcH / targetHeight).toInt()
            val rowBase = sy * srcW
            for (x in 0 until targetWidth) {
                val sx = if (targetWidth == srcW) x else (x.toLong() * srcW / targetWidth).toInt()
                val offset = (rowBase + sx) * 8
                val hr = src.getShort(offset)
                val hg = src.getShort(offset + 2)
                val hb = src.getShort(offset + 4)
                val ha = src.getShort(offset + 6)
                val alpha = Half.toFloat(ha)
                when {
                    alpha == 1f -> {
                        out.putShort(hr)
                        out.putShort(hg)
                        out.putShort(hb)
                        out.putShort(ha)
                    }
                    alpha <= 0f -> {
                        out.putShort(0)
                        out.putShort(0)
                        out.putShort(0)
                        out.putShort(0)
                    }
                    else -> {
                        out.putShort(Half.toHalf(Half.toFloat(hr) * alpha))
                        out.putShort(Half.toHalf(Half.toFloat(hg) * alpha))
                        out.putShort(Half.toHalf(Half.toFloat(hb) * alpha))
                        out.putShort(ha)
                    }
                }
            }
        }
        out.flip()
    }
}

private fun uploadFrameToBitmap(
    displayBitmap: Bitmap,
    targetWidth: Int,
    targetHeight: Int,
    isHdr: Boolean,
    intScratch: IntArray?,
    hdrScratch: ByteBuffer?,
) {
    if (!isHdr) {
        displayBitmap.setPixels(
            checkNotNull(intScratch),
            0,
            targetWidth,
            0,
            0,
            targetWidth,
            targetHeight,
        )
    } else {
        val buf = checkNotNull(hdrScratch)
        buf.rewind()
        displayBitmap.copyPixelsFromBuffer(buf)
    }
}
