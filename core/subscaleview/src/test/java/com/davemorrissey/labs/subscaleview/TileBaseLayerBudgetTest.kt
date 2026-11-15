package com.davemorrissey.labs.subscaleview

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.davemorrissey.labs.subscaleview.decoder.HdrAwareRegionDecoder
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class TileBaseLayerBudgetTest {
    private data class BudgetCase(
        val label: String,
        val width: Int,
        val height: Int,
        val maxTile: Int = 2048,
        val config: Bitmap.Config = Bitmap.Config.ARGB_8888,
        val byteCap: Long = 1_048_576L,
    )

    private data class Request(val rect: Rect, val sample: Int)

    private class SizedDecoder(private val fixture: BudgetCase) : HdrAwareRegionDecoder {
        val requests = mutableListOf<Request>()
        val outputs = mutableListOf<Bitmap>()
        var recycled = false
        private var initialized = false

        override val isHdr: Boolean
            get() = fixture.config == Bitmap.Config.RGBA_F16

        override fun init(context: Context, provider: InputProvider): Point {
            checkNotNull(provider.openStream()).use {}
            initialized = true
            return Point(fixture.width, fixture.height)
        }

        override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
            check(isReady())
            requests += Request(Rect(sRect), sampleSize)
            return Bitmap.createBitmap(
                (sRect.width() + sampleSize - 1) / sampleSize,
                (sRect.height() + sampleSize - 1) / sampleSize,
                fixture.config,
            ).also { outputs += it }
        }

        override fun isReady(): Boolean = initialized && !recycled

        override fun recycle() {
            recycled = true
            initialized = false
        }
    }

    private class BudgetView(val sizedDecoder: SizedDecoder) :
        SubsamplingScaleImageView(ApplicationProvider.getApplicationContext<Context>()) {
        override fun createRegionDecoder(): ImageRegionDecoder = sizedDecoder

        fun detachIntoCacheForTest() {
            super.onDetachedFromWindow()
        }
    }

    private data class BaseSnapshot(
        val sample: Int,
        val tiles: Int,
        val pixels: Long,
        val allocationBytes: Long,
    )

    private fun baseSnapshot(view: BudgetView): BaseSnapshot {
        val sampleField = SubsamplingScaleImageView::class.java.getDeclaredField("fullImageSampleSize").apply {
            isAccessible = true
        }
        val mapField = SubsamplingScaleImageView::class.java.getDeclaredField("tileMap").apply {
            isAccessible = true
        }
        val sample = sampleField.getInt(view)
        val map = mapField.get(view) as Map<*, *>
        val tiles = map[sample] as List<*>
        val bitmaps = tiles.map { entry ->
            val tile = checkNotNull(entry)
            val bitmapField = tile.javaClass.getDeclaredField("bitmap").apply { isAccessible = true }
            checkNotNull(bitmapField.get(tile) as Bitmap?)
        }
        return BaseSnapshot(
            sample,
            bitmaps.size,
            bitmaps.sumOf { it.width.toLong() * it.height },
            bitmaps.sumOf { it.allocationByteCount.toLong() },
        )
    }

    private fun layoutImage(view: BudgetView) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        ShadowLooper.idleMainLooper()
    }

    private fun <T> withLoadedBase(fixture: BudgetCase, body: (BudgetView) -> T): T {
        val decoder = SizedDecoder(fixture)
        val view = BudgetView(decoder).apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(fixture.maxTile)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setHardwareTilesEnabled(false)
            setExternalViewport(RectF(), 1f)
        }
        try {
            // Known initial layout lets onTilesInited allocate the actual base grid without drawing F16.
            view.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 400, 400)
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            layoutImage(view)
            assertTrue(view.isReady, "Base allocation must be accepted by the real SSIV engine")
            assertEquals(fixture.width, view.sWidth)
            assertEquals(fixture.height, view.sHeight)
            return body(view)
        } finally {
            view.recycle()
            ShadowLooper.idleMainLooper()
            assertTrue(decoder.recycled)
            assertTrue(decoder.outputs.all { it.isRecycled }, "The view owns and must release all output bitmaps")
        }
    }

    @Test
    fun permanentBaseAllocationsRespectCapsAcrossSingleTwoAndRoundedTileGrids() {
        val fixtures = listOf(
            BudgetCase("two-tile SDR", 2046, 4090),
            BudgetCase("two-to-three edge SDR", 2046, 4092),
            BudgetCase("long-strip ceil edge SDR", 1024, 65536),
            BudgetCase("small-limit split ceil edge SDR", 2048, 2048, maxTile = 256),
            BudgetCase("two-tile F16", 2046, 4090, config = Bitmap.Config.RGBA_F16, byteCap = 2_097_152L),
        )
        // Capture every old/fixed fixture before asserting, so an early failure cannot hide edge measurements.
        val measurements = fixtures.map { fixture ->
            val snapshot = withLoadedBase(fixture) { view ->
                val before = baseSnapshot(view)
                view.detachIntoCacheForTest()
                view.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                val retained = baseSnapshot(view)
                assertEquals(before, retained, "Detach/trim must retain the bounded fallback, not reopen the source")
                assertFalse(view.sizedDecoder.recycled)
                assertTrue(
                    view.sizedDecoder.requests.all {
                        it.sample == retained.sample
                    },
                    "Empty prefetch must not decode detail",
                )
                retained
            }
            println(
                "M10 JVM Bitmap API allocation " + fixture.label + ": " + snapshot +
                    "; not RSS or physical-device latency",
            )
            fixture to snapshot
        }
        val overBudget = measurements.filter { (fixture, snapshot) ->
            snapshot.pixels > 262_144L || snapshot.allocationBytes > fixture.byteCap
        }
        assertTrue(overBudget.isEmpty(), "Permanent base-grid allocations exceed the fallback budget: " + overBudget)
    }

    @Test
    fun boundedFallbackStillLoadsFullResolutionViewportDetailWithoutChangingGeometry() {
        withLoadedBase(BudgetCase("detail quality SDR", 2046, 4090)) { view ->
            val initialBase = baseSnapshot(view)
            assertTrue(
                initialBase.allocationBytes <= 1_048_576L,
                "Fallback must be bounded before loading optional detail",
            )
            val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(target))
                ShadowLooper.idleMainLooper()
                val originalScale = view.scale
                view.sizedDecoder.requests.clear()
                view.setExternalViewport(RectF(0f, 0f, 200f, 100f), 4f)
                repeat(3) {
                    layoutImage(view)
                    view.draw(Canvas(target))
                    ShadowLooper.idleMainLooper()
                }
                assertTrue(
                    view.sizedDecoder.requests.any {
                        it.sample == 1
                    },
                    "4x parent scale must still request full-resolution detail",
                )
                assertEquals(400, view.width)
                assertEquals(799, view.height)
                assertEquals(2046, view.sWidth)
                assertEquals(4090, view.sHeight)
                assertEquals(originalScale, view.scale, "Parent resolution must not alter gesture or layout scale")

                view.setExternalViewport(RectF(), 4f)
                view.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                assertEquals(initialBase, baseSnapshot(view), "Detail eviction must preserve the original bounded base")
            } finally {
                target.recycle()
            }
        }
    }

    @Test
    fun tinySingleTileKeepsFullResolutionInsideTheFallbackBudget() {
        withLoadedBase(BudgetCase("tiny SDR", 400, 400)) { view ->
            val snapshot = baseSnapshot(view)
            assertEquals(1, snapshot.sample)
            assertEquals(1, snapshot.tiles)
            assertEquals(160_000L, snapshot.pixels)
            assertEquals(640_000L, snapshot.allocationBytes)
        }
    }
}
