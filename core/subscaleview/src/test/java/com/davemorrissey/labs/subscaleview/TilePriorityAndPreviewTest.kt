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
import java.util.PriorityQueue
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class TilePriorityAndPreviewTest {

    private class PriorityDrainExecutor : Executor {
        private val queue = PriorityQueue<Runnable> { a, b ->
            @Suppress("UNCHECKED_CAST")
            when {
                a is Comparable<*> && b is Comparable<*> -> (a as Comparable<Any>).compareTo(b)
                a is Comparable<*> -> -1
                b is Comparable<*> -> 1
                else -> 0
            }
        }

        override fun execute(command: Runnable) {
            queue.offer(command)
        }

        fun drainAll() {
            while (queue.isNotEmpty()) {
                queue.poll()?.run()
                ShadowLooper.idleMainLooper()
            }
        }
    }

    private class RecordingDecoder(
        private val size: Point = Point(1000, 4000),
        private val globalOrder: MutableList<String>? = null,
        private val tag: String = "v",
    ) : ImageRegionDecoder {
        val decodedRegions = mutableListOf<Pair<Rect, Int>>()
        var initCount = 0
        var recycled = false

        override fun init(context: Context, provider: InputProvider): Point {
            initCount++
            globalOrder?.add("$tag:init")
            return size
        }

        override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
            decodedRegions += Rect(sRect) to sampleSize
            globalOrder?.add("$tag:${sRect.top}:$sampleSize")
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        override fun isReady(): Boolean = !recycled

        override fun recycle() {
            recycled = true
        }
    }

    private class TestView(
        val fake: RecordingDecoder = RecordingDecoder(),
    ) : SubsamplingScaleImageView(ApplicationProvider.getApplicationContext<Context>()) {
        override fun createRegionDecoder(): ImageRegionDecoder = fake
    }

    private fun TestView.render(target: Bitmap) {
        measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, measuredWidth, measuredHeight)
        draw(Canvas(target))
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun visibleTilesRunBeforeLookaheadPrefetchTiles() {
        val executor = PriorityDrainExecutor()
        val view = TestView(RecordingDecoder(Point(1000, 4000))).apply {
            setExecutor(executor)
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            // Only the bottom slice [1200..1600] is on screen while [0..1600] is in prefetch.
            setExternalViewport(
                RectF(0f, 1200f, 400f, 1600f),
                RectF(0f, 0f, 400f, 1600f),
                RectF(0f, 0f, 400f, 1600f),
                1f,
            )
        }
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            executor.drainAll()
            view.render(target)
            executor.drainAll()

            val detailRequests = view.fake.decodedRegions.filter { it.second < 4 }
            assertTrue(detailRequests.isNotEmpty(), "Expected detail tiles to decode")
            val firstVisibleIndex = detailRequests.indexOfFirst { it.first.bottom > 3000 }
            val firstPrefetchOnlyIndex = detailRequests.indexOfFirst { it.first.bottom <= 2000 }
            assertTrue(firstVisibleIndex >= 0, "Expected visible bottom detail tile to decode")
            assertTrue(firstPrefetchOnlyIndex >= 0, "Expected prefetch-only top detail tile to decode")
            assertTrue(
                firstVisibleIndex < firstPrefetchOnlyIndex,
                "Visible detail tile ($firstVisibleIndex) must run before prefetch-only tile ($firstPrefetchOnlyIndex): $detailRequests",
            )
        } finally {
            view.recycle()
            executor.drainAll()
            target.recycle()
        }
    }

    @Test
    fun queuedPrefetchTilePromotesWhenScrolledIntoVisibleViewport() {
        val executor = PriorityDrainExecutor()
        val view = TestView(RecordingDecoder(Point(1000, 4000))).apply {
            setExecutor(executor)
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setExternalViewport(
                RectF(),
                RectF(),
                RectF(0f, 0f, 400f, 1600f),
                1f,
            )
        }
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            executor.drainAll()
            // Initialise tile map and base layer first.
            view.render(target)
            executor.drainAll()
            view.fake.decodedRegions.clear()

            // Enqueue lower tile [1200..1600] as prefetch-only first (visible is empty).
            view.setExternalViewport(
                RectF(),
                RectF(0f, 1200f, 400f, 1600f),
                RectF(0f, 0f, 400f, 1600f),
                1f,
            )
            // Before draining, scroll so [1200..1600] becomes visible while [0..400] is prefetch-only.
            view.setExternalViewport(
                RectF(0f, 1200f, 400f, 1600f),
                RectF(0f, 0f, 400f, 1600f),
                RectF(0f, 0f, 400f, 1600f),
                1f,
            )
            executor.drainAll()

            val detailRequests = view.fake.decodedRegions.filter { it.second < 4 }
            val promotedTiles = detailRequests.filter { it.first.bottom > 3000 }
            val prefetchOnlyTiles = detailRequests.filter { it.first.bottom <= 1000 }
            assertTrue(promotedTiles.isNotEmpty(), "Expected promoted visible tiles to decode")
            assertTrue(prefetchOnlyTiles.isNotEmpty(), "Expected prefetch-only tiles to decode")
            assertEquals(
                promotedTiles.distinct(),
                promotedTiles,
                "Promoted queued tiles must not decode twice",
            )
            val firstPromoted = detailRequests.indexOfFirst { it.first.bottom > 3000 }
            val firstPrefetch = detailRequests.indexOfFirst { it.first.bottom <= 1000 }
            assertTrue(
                firstPromoted < firstPrefetch,
                "Promoted tile ($firstPromoted) must execute ahead of prefetch-only tile ($firstPrefetch)",
            )
        } finally {
            view.recycle()
            executor.drainAll()
            target.recycle()
        }
    }

    @Test
    fun boostedViewDecodesBeforeOffscreenNeighborView() {
        val executor = PriorityDrainExecutor()
        val order = mutableListOf<String>()
        val offscreen = TestView(RecordingDecoder(Point(1000, 1500), order, "offscreen")).apply {
            setExecutor(executor)
            setDecodePriorityBoost(false)
        }
        val active = TestView(RecordingDecoder(Point(1000, 1500), order, "active")).apply {
            setExecutor(executor)
            setDecodePriorityBoost(true)
        }
        try {
            // Offscreen neighbor enqueues first; active page enqueues second.
            offscreen.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            active.setImage(ImageSource.inputStream(byteArrayOf(2).inputStream()))
            executor.drainAll()

            assertEquals(
                listOf("active:init", "offscreen:init"),
                order,
                "Active foreground view must initialize ahead of queued offscreen neighbor",
            )
        } finally {
            offscreen.recycle()
            active.recycle()
            executor.drainAll()
        }
    }

    @Test
    fun previewBitmapRendersImmediatelyAndIsNotRecycledOnTileHandoff() {
        val executor = PriorityDrainExecutor()
        val view = TestView(RecordingDecoder(Point(1000, 2000))).apply {
            setExecutor(executor)
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
        }
        var readyCount = 0
        var loadedCount = 0
        view.setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    readyCount++
                }

                override fun onImageLoaded() {
                    loadedCount++
                }
            },
        )
        val preview = Bitmap.createBitmap(250, 500, Bitmap.Config.RGB_565)
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            val source = ImageSource.inputStream(byteArrayOf(1).inputStream())
                .dimensions(1000, 2000)
            view.setImage(source, preview, null)

            // Before any background decode runs, frame 0 renders using the cached preview.
            view.render(target)
            assertTrue(view.isReady, "Cached preview must make view ready on frame 0")
            assertEquals(1, readyCount)
            assertEquals(0, loadedCount, "onImageLoaded must wait for real base tiles")
            assertEquals(0, view.fake.initCount, "Background TilesInitTask has not run yet")

            // Complete full tiled decode and hand off from preview to tile pyramid.
            executor.drainAll()
            view.render(target)
            assertEquals(1, view.fake.initCount)
            assertEquals(1, loadedCount, "onImageLoaded must fire once base tiles finish")
            assertFalse(preview.isRecycled, "Non-owned preview bitmap must never be recycled on handoff")
        } finally {
            view.recycle()
            executor.drainAll()
            assertFalse(preview.isRecycled, "Non-owned preview bitmap must never be recycled on view.recycle()")
            preview.recycle()
            target.recycle()
        }
    }
}
