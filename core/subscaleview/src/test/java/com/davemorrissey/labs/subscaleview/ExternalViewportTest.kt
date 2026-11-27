package com.davemorrissey.labs.subscaleview

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ExternalViewportTest {
    private data class Request(val region: Rect, val sample: Int) {
        val pixels: Long get() = ((region.width().toLong() + sample - 1) / sample) *
            ((region.height().toLong() + sample - 1) / sample)
    }
    private class Decoder : ImageRegionDecoder {
        val requests = mutableListOf<Request>()
        var recycled = false
        override fun init(context: Context, provider: InputProvider) = Point(4000, 40000)
        override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
            requests += Request(Rect(sRect), sampleSize)
            // Request-policy test, not a native/pixel-quality test; no full image allocation.
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        override fun isReady() = !recycled
        override fun recycle() {
            recycled = true
        }
    }
    private class TestView(
        val fake: Decoder = Decoder(),
    ) : SubsamplingScaleImageView(ApplicationProvider.getApplicationContext<Context>()) {
        override fun createRegionDecoder(): ImageRegionDecoder = fake
    }

    private fun TestView.render() {
        measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, measuredWidth, measuredHeight)
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            draw(Canvas(target))
        } finally {
            target.recycle()
        }
        ShadowLooper.idleMainLooper()
    }

    private fun loadedView(): TestView = TestView().apply {
        setExecutor(Executor { it.run() })
        setMaxTileSize(512)
        setMinimumTileDpi(0)
        setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
        setExternalViewport(RectF(0f, 0f, 400f, 400f), 1f)
        setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
        ShadowLooper.idleMainLooper()
        repeat(3) { render() }
    }

    @Test
    fun longStripKeepsBoundedPreviewAndOnlyVisibleDetail() {
        val view = loadedView()
        try {
            assertTrue(
                view.fake.requests.sumOf {
                    it.pixels
                } < 2_000_000,
                "Do not decode full-resolution detail for the entire 4000x40000 strip",
            )
        } finally {
            view.recycle()
        }
    }

    @Test
    fun parentZoomRequestsMoreDetailWithoutChangingGeometry() {
        val view = loadedView()
        try {
            val width = view.measuredWidth
            val height = view.measuredHeight
            view.fake.requests.clear()
            view.setExternalViewport(RectF(100f, 100f, 200f, 200f), 4f)
            repeat(2) { view.render() }
            assertTrue(view.fake.requests.any { it.sample <= 2 }, "4x parent zoom must decode finer source tiles")
            assertEquals(width, view.measuredWidth)
            assertEquals(height, view.measuredHeight)
        } finally {
            view.recycle()
        }
    }

    @Test
    fun skippedTileIsRetriedWhenItBecomesVisibleBeforeCompletion() {
        val view = loadedView()
        val pending = ArrayDeque<Runnable>()
        view.setExecutor(Executor { pending.addLast(it) })
        try {
            val viewport = RectF(10f, 900f, 20f, 910f)
            view.fake.requests.clear()
            view.setExternalViewport(viewport, 1f)
            assertEquals(1, pending.size)
            view.setExternalViewport(RectF(), 1f)
            pending.removeFirst().run() // Worker sees hidden; its completion is still queued on main.
            view.setExternalViewport(viewport, 1f)
            view.render() // Draw while loading=true, then dispatch the skipped completion.
            assertEquals(1, pending.size, "A current visible tile needs an explicit retry after a skipped worker")
            pending.removeFirst().run()
            ShadowLooper.idleMainLooper()
            assertTrue(view.fake.requests.any { it.region.top <= 9000 && it.region.bottom > 9000 })
        } finally {
            view.recycle()
            while (pending.isNotEmpty()) pending.removeFirst().run()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun obsoleteInitializationClosesItsNativeDecoder() {
        val view = TestView()
        view.setExecutor(Executor { it.run() })
        view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
        view.recycle()
        ShadowLooper.idleMainLooper()
        assertTrue(view.fake.recycled, "An initialization that completed after recycle still owns a decoder")
    }

    @Test
    fun retainViewportHysteresisPreservesTilesAcrossSmallOscillations() {
        val view = loadedView()
        try {
            view.setExternalViewport(RectF(0f, 400f, 400f, 800f), RectF(0f, 0f, 400f, 1200f), 2f)
            repeat(2) { view.render() }
            assertTrue(
                view.fake.requests.any { it.region.top in 4000 until 8000 && it.sample <= 4 },
                "Detail tiles in the initial band must decode",
            )

            // Scroll down so band y=[400, 800] leaves prefetch viewport but stays inside retainViewport.
            view.fake.requests.clear()
            view.setExternalViewport(RectF(0f, 900f, 400f, 1300f), RectF(0f, 300f, 400f, 1700f), 2f)
            repeat(2) { view.render() }

            // Scroll back to y=[400, 800]: retained tiles should reappear without re-decoding.
            view.fake.requests.clear()
            view.setExternalViewport(RectF(0f, 400f, 400f, 800f), RectF(0f, 0f, 400f, 1200f), 2f)
            repeat(2) { view.render() }
            assertEquals(
                0,
                view.fake.requests.count { it.region.top in 4000 until 8000 && it.sample <= 4 },
                "Tiles kept in retainViewport must not re-decode on oscillation back",
            )

            // Leave retainViewport completely so those tiles are evicted, then return and verify re-decode.
            view.setExternalViewport(RectF(0f, 2000f, 400f, 2400f), RectF(0f, 1800f, 400f, 2600f), 2f)
            repeat(2) { view.render() }
            view.fake.requests.clear()
            view.setExternalViewport(RectF(0f, 400f, 400f, 800f), RectF(0f, 0f, 400f, 1200f), 2f)
            repeat(2) { view.render() }
            assertTrue(
                view.fake.requests.any { it.region.top in 4000 until 8000 && it.sample <= 4 },
                "Tiles evicted after exiting retainViewport must decode again when visible",
            )
        } finally {
            view.recycle()
        }
    }
}
