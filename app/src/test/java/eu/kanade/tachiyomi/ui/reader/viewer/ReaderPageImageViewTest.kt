package eu.kanade.tachiyomi.ui.reader.viewer

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import coil3.gif.PixelOpacity
import coil3.size.Scale
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.data.coil.AnimatedNativeDrawable
import eu.kanade.tachiyomi.data.coil.NativeAnimationSource
import eu.kanade.tachiyomi.data.coil.NativeFrameData
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerSubsamplingImageView
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import okio.Buffer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import tachiyomi.core.common.util.image.NativeImageDecoder
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ReaderPageImageViewTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun layoutAndDraw(root: View, width: Int, height: Int, canvasBitmap: Bitmap) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        root.draw(Canvas(canvasBitmap))
    }

    @Test
    fun smartFitJobCancelledOnDetachResumesWhenReattached() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val pageView = ReaderPageImageView(activity, isWebtoon = true)
        root.addView(pageView, FrameLayout.LayoutParams(400, 400))
        activity.setContentView(root)

        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 0 until 100) {
                for (x in 25 until 75) setPixel(x, y, Color.BLUE)
            }
        }
        val canvasBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        var loaded = false
        pageView.onImageLoaded = { loaded = true }

        try {
            layoutAndDraw(root, 400, 400, canvasBitmap)
            pageView.setImage(
                BitmapDrawable(activity.resources, bitmap),
                ReaderPageImageView.Config(
                    zoomDuration = 100,
                    minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                    webtoonSmartFit = true,
                ),
            )
            // Detach immediately while smartFitJob is still pending/running in background.
            root.removeView(pageView)
            Thread.sleep(60)
            ShadowLooper.idleMainLooper()
            assertFalse(loaded, "Cancelled smartFitJob must not deliver while detached")

            // Re-attach must resume pendingSmartFit and deliver the cropped image.
            root.addView(pageView, FrameLayout.LayoutParams(400, 400))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!loaded && System.nanoTime() < deadline) {
                layoutAndDraw(root, 400, 400, canvasBitmap)
                ShadowLooper.idleMainLooper()
                Thread.sleep(10)
            }
            assertTrue(loaded, "Pending smartFit should complete after reattach")
            val ssiv = assertIs<SubsamplingScaleImageView>(pageView.getImageView())
            assertEquals(50, ssiv.sWidth)
        } finally {
            pageView.recycle()
            bitmap.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun singleTileLoadErrorDoesNotCallOnImageLoadError() {
        mockkObject(NativeImageDecoder.Companion)
        val detailFailures = AtomicInteger(0)
        val native = mockk<NativeImageDecoder>(relaxed = true)
        every { native.width } returns 4096
        every { native.height } returns 4096
        every { native.isHdr } returns false
        every { native.decode(any(), any()) } answers {
            val sample = secondArg<Int>()
            if (sample < 4) {
                detailFailures.incrementAndGet()
                throw IllegalStateException("Corrupt detail tile")
            }
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        }
        every { NativeImageDecoder.open(any(), any()) } returns native

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val pageView = ReaderPageImageView(activity, isWebtoon = false)
        root.addView(pageView, FrameLayout.LayoutParams(400, 400))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)

        var loaded = false
        var imageErrors = 0
        pageView.onImageLoaded = { loaded = true }
        pageView.onImageLoadError = { imageErrors++ }

        try {
            layoutAndDraw(root, 400, 400, canvasBitmap)
            pageView.setImage(
                Buffer().writeByte(1),
                isAnimated = false,
                ReaderPageImageView.Config(
                    zoomDuration = 100,
                    minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE,
                ),
            )
            val initDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!loaded && System.nanoTime() < initDeadline) {
                layoutAndDraw(root, 400, 400, canvasBitmap)
                ShadowLooper.idleMainLooper()
                Thread.sleep(10)
            }
            assertTrue(loaded, "Base image must initialize before testing tile error handling")

            // Zoom to maxScale so sampleSize < 4 detail tiles are decoded and hit tile failures.
            val ssiv = assertIs<SubsamplingScaleImageView>(pageView.getImageView())
            ssiv.setScaleAndCenter(ssiv.maxScale, PointF(2048f, 2048f))
            val retryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (detailFailures.get() < 3 && System.nanoTime() < retryDeadline) {
                layoutAndDraw(root, 400, 400, canvasBitmap)
                ShadowLooper.idleMainLooper(120, TimeUnit.MILLISECONDS)
                Thread.sleep(10)
            }
            ShadowLooper.idleMainLooper(120, TimeUnit.MILLISECONDS)

            assertTrue(detailFailures.get() >= 3, "Detail tile failure must exhaust retries and fire onTileLoadError")
            assertEquals(0, imageErrors, "Single tile failure must not fail the whole page")
        } finally {
            pageView.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun pagerSubsamplingSetupZoomUsesPostSmartFitMinScale() {
        mockkObject(NativeImageDecoder.Companion)
        val native = mockk<NativeImageDecoder>(relaxed = true)
        every { native.width } returns 1000
        every { native.height } returns 3000
        every { native.isHdr } returns false
        every { native.decode(any(), any()) } answers {
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        }
        every { NativeImageDecoder.open(any(), any()) } returns native

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val pageView = ReaderPageImageView(activity, isWebtoon = false)
        root.addView(pageView, FrameLayout.LayoutParams(400, 800))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)

        var loaded = false
        pageView.onImageLoaded = { loaded = true }

        try {
            layoutAndDraw(root, 400, 800, canvasBitmap)
            pageView.setImage(
                Buffer().writeByte(1),
                isAnimated = false,
                ReaderPageImageView.Config(
                    zoomDuration = 100,
                    minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_SMART_FIT,
                    zoomStartPosition = ReaderPageImageView.ZoomStartPosition.CENTER,
                ),
            )
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!loaded && System.nanoTime() < deadline) {
                layoutAndDraw(root, 400, 800, canvasBitmap)
                ShadowLooper.idleMainLooper()
                Thread.sleep(10)
            }
            assertTrue(loaded, "Expected pager smart-fit image to become ready")
            val ssiv = assertIs<PagerSubsamplingImageView>(pageView.getImageView())
            val expectedFitWidthScale = 400f / 1000f
            assertTrue(
                abs(ssiv.minScale - expectedFitWidthScale) < 0.001f,
                "Expected portrait smart-fit minScale=$expectedFitWidthScale, got ${ssiv.minScale}",
            )
            assertTrue(
                abs(ssiv.scale - ssiv.minScale) < 0.001f,
                "Expected initial scale=${ssiv.scale} to match post-smart-fit minScale=${ssiv.minScale}",
            )
            assertTrue(
                abs(ssiv.maxScale - ssiv.minScale * 5f) < 0.001f,
                "Expected maxScale=${ssiv.maxScale} to be 5x post-smart-fit minScale=${ssiv.minScale}",
            )
        } finally {
            pageView.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun streamBackedNonAnimatedImageLoadsViaReusableStreamProvider() {
        mockkObject(NativeImageDecoder.Companion)
        val native = mockk<NativeImageDecoder>(relaxed = true)
        every { native.width } returns 800
        every { native.height } returns 1200
        every { native.isHdr } returns false
        every { native.decode(any(), any()) } answers {
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        }
        every { NativeImageDecoder.open(any(), any()) } returns native

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val pageView = ReaderPageImageView(activity, isWebtoon = true)
        root.addView(pageView, FrameLayout.LayoutParams(400, 800))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)

        var openCount = 0
        var loaded = false
        pageView.onImageLoaded = { loaded = true }

        try {
            layoutAndDraw(root, 400, 800, canvasBitmap)
            pageView.setImage(
                streamFn = {
                    openCount++
                    byteArrayOf(1).inputStream()
                },
                isAnimated = false,
                config = ReaderPageImageView.Config(
                    zoomDuration = 100,
                    minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                ),
            )
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!loaded && System.nanoTime() < deadline) {
                layoutAndDraw(root, 400, 800, canvasBitmap)
                ShadowLooper.idleMainLooper()
                Thread.sleep(10)
            }
            assertTrue(loaded, "Expected stream-backed image to load")
            assertTrue(openCount >= 1, "Expected StreamInputProvider to open stream")
        } finally {
            pageView.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun splitMergeRegionDecoderMapsHalvesAndSeamCrossingTiles() {
        mockkObject(NativeImageDecoder.Companion)
        val native = mockk<NativeImageDecoder>(relaxed = true)
        every { native.width } returns 200
        every { native.height } returns 100
        every { native.isHdr } returns false
        val decodedRects = mutableListOf<android.graphics.Rect>()
        every { native.decode(any(), any()) } answers {
            val rect = firstArg<android.graphics.Rect>()
            decodedRects += android.graphics.Rect(rect)
            Bitmap.createBitmap(rect.width().coerceAtLeast(1), rect.height().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }
        every { NativeImageDecoder.open(any(), any()) } returns native

        val decoder = com.davemorrissey.labs.subscaleview.decoder.SplitMergeRegionDecoder(
            upperSide = tachiyomi.core.common.util.system.ImageUtil.Side.RIGHT,
            hardwareTilesEnabled = false,
        )
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val size = decoder.init(
            app,
            com.davemorrissey.labs.subscaleview.provider.StreamInputProvider { byteArrayOf(1).inputStream() },
        )
        assertEquals(100, size.x)
        assertEquals(200, size.y)

        decoder.decodeRegion(android.graphics.Rect(10, 10, 50, 40), 1).recycle()
        assertEquals(android.graphics.Rect(110, 10, 150, 40), decodedRects.last())

        decoder.decodeRegion(android.graphics.Rect(10, 110, 50, 140), 1).recycle()
        assertEquals(android.graphics.Rect(10, 10, 50, 40), decodedRects.last())

        val beforeSeam = decodedRects.size
        val seamBitmap = decoder.decodeRegion(android.graphics.Rect(0, 80, 100, 120), 1)
        try {
            assertEquals(100, seamBitmap.width)
            assertEquals(40, seamBitmap.height)
            assertEquals(beforeSeam + 2, decodedRects.size)
            assertEquals(android.graphics.Rect(100, 80, 200, 100), decodedRects[beforeSeam])
            assertEquals(android.graphics.Rect(0, 0, 100, 20), decodedRects[beforeSeam + 1])
        } finally {
            seamBitmap.recycle()
            decoder.recycle()
        }
    }

    @Test
    fun splitMergeRegionDecoderPreservesWiderHalfWhenSmartGutterShiftsCenter() {
        mockkObject(NativeImageDecoder.Companion)
        val native = mockk<NativeImageDecoder>(relaxed = true)
        every { native.width } returns 1000
        every { native.height } returns 400
        every { native.isHdr } returns false
        val decodedRects = mutableListOf<android.graphics.Rect>()
        every { native.decode(any(), any()) } answers {
            val rect = firstArg<android.graphics.Rect>()
            decodedRects += android.graphics.Rect(rect)
            // When findSmartSplitX probes 450..550, put a uniform white gutter at col 30 (x = 480)
            val bmp = Bitmap.createBitmap(
                rect.width().coerceAtLeast(1),
                rect.height().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            if (rect.left == 450 && rect.right == 550) {
                for (x in 0 until bmp.width) {
                    for (y in 0 until bmp.height) {
                        bmp.setPixel(
                            x,
                            y,
                            if (x ==
                                30
                            ) {
                                android.graphics.Color.WHITE
                            } else if (y % 2 ==
                                0
                            ) {
                                android.graphics.Color.BLACK
                            } else {
                                android.graphics.Color.WHITE
                            },
                        )
                    }
                }
            }
            bmp
        }
        every { NativeImageDecoder.open(any(), any()) } returns native

        val decoder = com.davemorrissey.labs.subscaleview.decoder.SplitMergeRegionDecoder(
            upperSide = tachiyomi.core.common.util.system.ImageUtil.Side.RIGHT,
            hardwareTilesEnabled = false,
            smartGutter = true,
        )
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val size = decoder.init(
            app,
            com.davemorrissey.labs.subscaleview.provider.StreamInputProvider { byteArrayOf(1).inputStream() },
        )
        // Right half is 480..1000 (width 520), left half is 0..480 (width 480) -> max halfWidth = 520
        assertEquals(520, size.x)
        assertEquals(800, size.y)

        // Decoding rightmost tile of the narrower lower (left) half [440..520] must clamp to [440..480] without OOB
        val narrowEdge = decoder.decodeRegion(android.graphics.Rect(440, 410, 520, 450), 1)
        try {
            assertEquals(80, narrowEdge.width)
            assertEquals(40, narrowEdge.height)
            assertEquals(android.graphics.Rect(440, 10, 480, 50), decodedRects.last())
        } finally {
            narrowEdge.recycle()
            decoder.recycle()
        }
    }

    @Test
    fun lockedZoomRatioPreservesScaleOnInitialSetup() {
        mockkObject(NativeImageDecoder.Companion)
        val native = mockk<NativeImageDecoder>(relaxed = true)
        every { native.width } returns 1000
        every { native.height } returns 1500
        every { native.isHdr } returns false
        every { native.decode(any(), any()) } answers {
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        }
        every { NativeImageDecoder.open(any(), any()) } returns native

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val pageView = ReaderPageImageView(activity, isWebtoon = false)
        root.addView(pageView, FrameLayout.LayoutParams(400, 800))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)

        var loaded = false
        pageView.onImageLoaded = { loaded = true }

        try {
            layoutAndDraw(root, 400, 800, canvasBitmap)
            pageView.setImage(
                streamFn = { byteArrayOf(1).inputStream() },
                isAnimated = false,
                config = ReaderPageImageView.Config(
                    zoomDuration = 100,
                    minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE,
                    zoomStartPosition = ReaderPageImageView.ZoomStartPosition.CENTER,
                    lockedZoomRatio = 2.0f,
                ),
            )
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!loaded && System.nanoTime() < deadline) {
                layoutAndDraw(root, 400, 800, canvasBitmap)
                ShadowLooper.idleMainLooper()
                Thread.sleep(10)
            }
            assertTrue(loaded, "Expected image with lockedZoomRatio to load")
            val ssiv = assertIs<PagerSubsamplingImageView>(pageView.getImageView())
            assertTrue(
                abs(ssiv.scale - ssiv.minScale * 2.0f) < 0.01f,
                "Expected locked scale=${ssiv.scale} to be 2x minScale=${ssiv.minScale}",
            )
        } finally {
            pageView.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun animatedDrawableStoppedClosedAndClearedOnRecycleSwapAndRebind() {
        class CloseableAnimatableDrawable : Drawable(), Animatable, Closeable {
            var running = false
            var stopCalls = 0
            var closeCalls = 0

            override fun start() {
                running = true
            }

            override fun stop() {
                running = false
                stopCalls++
            }

            override fun isRunning(): Boolean = running

            override fun close() {
                closeCalls++
            }

            override fun getIntrinsicWidth(): Int = 16

            override fun getIntrinsicHeight(): Int = 16

            override fun draw(canvas: Canvas) {}

            override fun setAlpha(alpha: Int) {}

            override fun setColorFilter(colorFilter: ColorFilter?) {}

            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val pageView = ReaderPageImageView(activity, isWebtoon = true)
        root.addView(pageView, FrameLayout.LayoutParams(400, 400))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val staticBitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val config = ReaderPageImageView.Config(
            zoomDuration = 100,
            minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
        )

        var loaded = false
        pageView.onImageLoaded = { loaded = true }

        fun awaitLoad() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!loaded && System.nanoTime() < deadline) {
                layoutAndDraw(root, 400, 400, canvasBitmap)
                ShadowLooper.idleMainLooper()
                Thread.sleep(10)
            }
            assertTrue(loaded, "Expected image to finish loading")
        }

        try {
            layoutAndDraw(root, 400, 400, canvasBitmap)

            val first = CloseableAnimatableDrawable()
            loaded = false
            pageView.setImage(first, config)
            awaitLoad()
            assertTrue(first.running)

            pageView.recycle()
            assertFalse(first.running)
            assertEquals(1, first.stopCalls)
            assertEquals(1, first.closeCalls)
            val imageView = assertIs<AppCompatImageView>(pageView.getImageView())
            assertNull(imageView.drawable)

            val second = CloseableAnimatableDrawable()
            loaded = false
            pageView.setImage(second, config)
            awaitLoad()

            // Rebinding a new animated image immediately stops and closes the previous one.
            val third = CloseableAnimatableDrawable()
            loaded = false
            pageView.setImage(third, config)
            assertEquals(1, second.stopCalls)
            assertEquals(1, second.closeCalls)
            awaitLoad()

            // Swapping to non-animated releases the active animated drawable before removing AppCompatImageView.
            loaded = false
            pageView.setImage(BitmapDrawable(activity.resources, staticBitmap), config)
            assertEquals(1, third.stopCalls)
            assertEquals(1, third.closeCalls)
            assertIs<SubsamplingScaleImageView>(pageView.getImageView())
        } finally {
            pageView.recycle()
            staticBitmap.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun animatedNativeDrawableForwardsHdrAndClosesOnRecycle() {
        var sourceClosed = false
        val hdrSource = object : NativeAnimationSource {
            override fun decodeNext(): NativeFrameData = NativeFrameData(
                width = 1,
                height = 1,
                image = ByteBuffer.allocateDirect(8),
                index = 1,
                duration = 500,
            )

            override fun close() {
                sourceClosed = true
            }
        }
        val hdrBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGBA_F16)
        val hdrDrawable = AnimatedNativeDrawable(
            source = hdrSource,
            displayBitmap = hdrBitmap,
            intScratch = null,
            hdrScratch = ByteBuffer.allocateDirect(8),
            isHdr = true,
            pageCount = 2,
            initialIndex = 0,
            initialLast = false,
            initialDurationMs = 500,
            scale = Scale.FIT,
            animatedTransformation = null,
            pixelOpacity = PixelOpacity.UNCHANGED,
        )

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val pageView = ReaderPageImageView(activity, isWebtoon = true)
        root.addView(pageView, FrameLayout.LayoutParams(400, 400))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val hdrEvents = mutableListOf<Boolean>()
        var loaded = false
        pageView.onHdrChanged = { hdrEvents.add(it) }
        pageView.onImageLoaded = { loaded = true }

        try {
            layoutAndDraw(root, 400, 400, canvasBitmap)
            pageView.setImage(
                hdrDrawable,
                ReaderPageImageView.Config(
                    zoomDuration = 100,
                    minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                ),
            )
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!loaded && System.nanoTime() < deadline) {
                layoutAndDraw(root, 400, 400, canvasBitmap)
                ShadowLooper.idleMainLooper()
                Thread.sleep(10)
            }
            assertTrue(loaded)
            assertTrue(pageView.isHdr)
            assertEquals(listOf(true), hdrEvents)

            pageView.recycle()
            assertFalse(pageView.isHdr)
            assertEquals(listOf(true, false), hdrEvents)
            assertTrue(hdrBitmap.isRecycled)
            assertTrue(sourceClosed)
        } finally {
            pageView.recycle()
            hdrDrawable.close()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun animatedRequestCancelledOnDetachResumesWhenReattached() {
        class TestAnimatableDrawable : Drawable(), Animatable {
            var running = false

            override fun start() {
                running = true
            }

            override fun stop() {
                running = false
            }

            override fun isRunning(): Boolean = running

            override fun getIntrinsicWidth(): Int = 16

            override fun getIntrinsicHeight(): Int = 16

            override fun draw(canvas: Canvas) {}

            override fun setAlpha(alpha: Int) {}

            override fun setColorFilter(colorFilter: ColorFilter?) {}

            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val pageView = ReaderPageImageView(activity, isWebtoon = true)
        root.addView(pageView, FrameLayout.LayoutParams(400, 400))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val config = ReaderPageImageView.Config(
            zoomDuration = 100,
            minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
        )
        val drawable = TestAnimatableDrawable()
        var loadedCount = 0
        var errorCount = 0
        pageView.onImageLoaded = { loadedCount++ }
        pageView.onImageLoadError = { errorCount++ }

        try {
            layoutAndDraw(root, 400, 400, canvasBitmap)
            pageView.setImage(drawable, config)
            // Detach immediately into view cache, cancelling the in-flight Coil request.
            root.removeView(pageView)
            Thread.sleep(60)
            ShadowLooper.idleMainLooper()
            assertEquals(0, loadedCount, "Detached view must not deliver while cached off-screen")

            // Re-attach from view cache must re-enqueue the pending animated request.
            root.addView(pageView, FrameLayout.LayoutParams(400, 400))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (loadedCount == 0 && System.nanoTime() < deadline) {
                layoutAndDraw(root, 400, 400, canvasBitmap)
                ShadowLooper.idleMainLooper()
                Thread.sleep(10)
            }
            assertEquals(0, errorCount)
            assertEquals(1, loadedCount, "Pending animated request should complete after reattach")
            assertTrue(drawable.running)
        } finally {
            pageView.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }
}
