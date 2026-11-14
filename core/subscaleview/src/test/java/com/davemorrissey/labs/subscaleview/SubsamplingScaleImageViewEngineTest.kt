package com.davemorrissey.labs.subscaleview

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
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
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SubsamplingScaleImageViewEngineTest {
    private class ConfigurableDecoder : ImageRegionDecoder {
        var recycled = false
        var failDetailPredicate: ((Rect, Int) -> Boolean)? = null
        val decodedRegions = mutableListOf<Pair<Rect, Int>>()
        var returnHardwareBitmap = false

        override fun init(context: Context, provider: InputProvider): Point {
            provider.openStream()?.close()
            return Point(4000, 40000)
        }

        override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
            decodedRegions += Rect(sRect) to sampleSize
            if (failDetailPredicate?.invoke(sRect, sampleSize) == true) {
                throw IllegalStateException("Transient tile decode failure")
            }
            val software = if (sampleSize >= 8) {
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            } else {
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            }
            if (returnHardwareBitmap) {
                val hw = runCatching { software.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
                if (hw != null) {
                    software.recycle()
                    return hw
                }
            }
            return software
        }

        override fun isReady(): Boolean = !recycled

        override fun recycle() {
            recycled = true
        }
    }

    private class EngineTestView(
        val fake: ConfigurableDecoder = ConfigurableDecoder(),
    ) : SubsamplingScaleImageView(ApplicationProvider.getApplicationContext<Context>()) {
        override fun createRegionDecoder(): ImageRegionDecoder = fake
    }

    private class RecordingCanvas(target: Bitmap) : Canvas(target) {
        val draws = mutableListOf<Pair<Int, Int>>()

        override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
            draws += bitmap.width to (paint?.alpha ?: 255)
            super.drawBitmap(bitmap, matrix, paint)
        }
    }

    private fun EngineTestView.render(
        canvasBitmap: Bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888),
    ): List<Pair<Int, Int>> {
        measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, measuredWidth, measuredHeight)
        val draws = drawIntoRecording(canvasBitmap)
        ShadowLooper.idleMainLooper()
        return draws
    }

    private fun EngineTestView.drawIntoRecording(canvasBitmap: Bitmap): List<Pair<Int, Int>> {
        val recording = RecordingCanvas(canvasBitmap)
        draw(recording)
        return recording.draws
    }

    @Test
    fun trimMemoryEvictsOffscreenRetainedTilesWhileKeepingVisibleOnes() {
        val view = EngineTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setExternalViewport(RectF(0f, 400f, 400f, 800f), RectF(0f, 0f, 400f, 1200f), 2f)
            setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
        }
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            repeat(3) { view.render(target) }
            // Pan down so band y=[400, 800] is only retained, while y=[900, 1300] is visible.
            view.setExternalViewport(RectF(0f, 900f, 400f, 1300f), RectF(0f, 300f, 400f, 1700f), 2f)
            repeat(2) { view.render(target) }

            view.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

            view.fake.decodedRegions.clear()
            // Scroll back to y=[400, 800]: evicted retained tile must re-decode, while y=[900..1300] wouldn't.
            view.setExternalViewport(RectF(0f, 400f, 400f, 800f), RectF(0f, 0f, 400f, 1200f), 2f)
            repeat(2) { view.render(target) }
            assertTrue(
                view.fake.decodedRegions.any { it.first.top in 4000 until 8000 && it.second <= 4 },
                "trimMemory must evict non-visible detail tiles even inside retainViewport",
            )
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun tileDecodeFailureRetriesUpToLimitAndFiresTileErrorOnce() {
        val view = EngineTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            fake.failDetailPredicate = { rect, sample -> sample < 8 && rect.left == 0 && rect.top == 0 }
            setExternalViewport(RectF(0f, 0f, 400f, 200f), 2f)
        }
        var tileErrors = 0
        var imageErrors = 0
        view.setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onTileLoadError(e: Exception) {
                    tileErrors++
                }

                override fun onImageLoadError(e: Exception) {
                    imageErrors++
                }
            },
        )
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            repeat(2) { view.render(target) }
            // Initial attempt ran synchronously; advance for retry #1 (50ms) and retry #2 (100ms).
            ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(120, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)

            val topTileAttempts = view.fake.decodedRegions.count {
                it.second < 8 && it.first.left == 0 && it.first.top == 0
            }
            assertEquals(3, topTileAttempts, "Expected 1 initial try plus 2 bounded retries")
            assertEquals(1, tileErrors, "Expected exactly one terminal onTileLoadError after exhausting retries")
            assertEquals(0, imageErrors, "Detail tile failure must never escalate to onImageLoadError")

            // Subsequent draws must not restart retries for failed tiles.
            repeat(2) { view.render(target) }
            assertEquals(
                3,
                view.fake.decodedRegions.count { it.second < 8 && it.first.left == 0 && it.first.top == 0 },
            )
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun transientTileFailureRecoversOnRetryWithoutFiringTileError() {
        var remainingFailures = 1
        val view = EngineTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            fake.failDetailPredicate = { rect, sample ->
                if (sample < 8 && rect.left == 0 && rect.top == 0 && remainingFailures > 0) {
                    remainingFailures--
                    true
                } else {
                    false
                }
            }
            setExternalViewport(RectF(0f, 0f, 400f, 200f), 2f)
        }
        var tileErrors = 0
        view.setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onTileLoadError(e: Exception) {
                    tileErrors++
                }
            },
        )
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            repeat(2) { view.render(target) }
            ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
            assertEquals(0, tileErrors)
            assertEquals(
                2,
                view.fake.decodedRegions.count { it.second < 8 && it.first.left == 0 && it.first.top == 0 },
            )
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun baseLayerTileFailureInvokesOnImageLoadErrorAfterExhaustingRetries() {
        val view = EngineTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            fake.failDetailPredicate = { _, _ -> true }
            setExternalViewport(RectF(0f, 0f, 400f, 200f), 1f)
        }
        var tileErrors = 0
        var imageErrors = 0
        view.setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onTileLoadError(e: Exception) {
                    tileErrors++
                }

                override fun onImageLoadError(e: Exception) {
                    imageErrors++
                }
            },
        )
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            repeat(2) { view.render(target) }
            ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(120, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
            assertEquals(1, imageErrors, "Base layer failure after retries must fail the page load")
            assertEquals(0, tileErrors)
            assertFalse(view.isReady)
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun detailTileFadesInOverDurationAndCompletesToFullAlpha() {
        val view = EngineTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setTileFadeDurationMs(100L)
            setExternalViewport(RectF(0f, 0f, 400f, 200f), 2f)
        }
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            // Measure/layout and idle until detail tiles have been decoded and delivered on UI thread.
            repeat(5) {
                if (view.fake.decodedRegions.none { it.second < 8 }) {
                    view.render(target)
                }
            }
            assertTrue(view.fake.decodedRegions.any { it.second < 8 }, "Detail tiles must decode")

            // Draw immediately before advancing looper clock: alpha starts below 255 and base stays drawn.
            val midFadeDraws = view.drawIntoRecording(target)
            val detailFadeDraws = midFadeDraws.filter { it.first == 4 }
            assertTrue(detailFadeDraws.isNotEmpty(), "Expected fine tile draws")
            assertTrue(detailFadeDraws.any { it.second < 255 }, "Expected detail tile to start below full alpha")
            assertTrue(midFadeDraws.any { it.first == 2 }, "Expected base layer to remain visible underneath")

            // Advance past 100ms fade duration; detail tiles must settle to alpha 255.
            ShadowLooper.idleMainLooper(120, TimeUnit.MILLISECONDS)
            val settledDraws = view.drawIntoRecording(target)
            val settledDetailDraws = settledDraws.filter { it.first == 4 }
            assertTrue(settledDetailDraws.isNotEmpty())
            assertTrue(settledDetailDraws.all { it.second == 255 }, "Expected fade completion to alpha 255")
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun hardwareTileBitmapDrawsSafelyOntoSoftwareCanvas() {
        val view = EngineTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setHardwareTilesEnabled(true)
            fake.returnHardwareBitmap = true
            setExternalViewport(RectF(0f, 0f, 400f, 200f), 1f)
        }
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            repeat(2) { view.render(target) }
            val draws = view.render(target)
            assertTrue(draws.isNotEmpty())
            assertTrue(view.isHardwareTilesEnabled)
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun openStreamProviderOpensOnceAndDetachClearsPendingRetriesWithoutCrash() {
        var openCount = 0
        val countingProvider = object : InputProvider {
            override fun openStream(): InputStream {
                openCount++
                return ByteArrayInputStream(byteArrayOf(1))
            }
        }
        val view = EngineTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            fake.failDetailPredicate = { _, sample -> sample < 8 }
            setExternalViewport(RectF(0f, 0f, 400f, 200f), 2f)
        }
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.provider(countingProvider))
            assertTrue(view.hasImage())
            ShadowLooper.idleMainLooper()
            assertEquals(1, openCount)
            repeat(2) { view.render(target) }
            assertEquals(1, openCount, "OpenStreamProvider/InputProvider must not reopen after init")
            // Detach while retry runnables are queued on the main handler.
            view.dispatchDetachedFromWindowForTest()
            ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
            assertFalse(view.fake.recycled, "Detach into view cache must not recycle decoder")
            assertTrue(view.hasImage())
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun externalViewportDetachedBeforeBaseTilesFinishReenqueuesAndReadiesOnReattach() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val activity = controller.get()
        val root = android.widget.FrameLayout(activity)
        activity.setContentView(root)

        val queuedTasks = java.util.ArrayDeque<Runnable>()
        val view = EngineTestView().apply {
            setExecutor(Executor { queuedTasks.add(it) })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setExternalViewport(RectF(0f, 0f, 400f, 200f), 1f)
        }
        var readyCount = 0
        var errorCount = 0
        view.setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    readyCount++
                }

                override fun onImageLoadError(e: Exception) {
                    errorCount++
                }
            },
        )
        root.addView(view, android.widget.FrameLayout.LayoutParams(400, 400))

        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 400, 400)
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))

            // Run only TilesInitTask so base-layer TileLoadTasks are queued, not yet executed.
            assertEquals(1, queuedTasks.size)
            queuedTasks.removeFirst().run()
            ShadowLooper.idleMainLooper()
            assertTrue(queuedTasks.isNotEmpty(), "Base-layer TileLoadTasks should be queued")
            assertEquals(0, readyCount)

            // Detach into RecyclerView cache before base tiles complete, then drain the cancelled tasks.
            root.removeView(view)
            while (queuedTasks.isNotEmpty()) {
                queuedTasks.removeFirst().run()
            }
            ShadowLooper.idleMainLooper()
            assertEquals(0, readyCount, "Detached view must not mark ready with cancelled base tiles")

            // Re-attach from view cache (no onBindViewHolder / setImage) and push external viewport.
            root.addView(view, android.widget.FrameLayout.LayoutParams(400, 400))
            view.setExternalViewport(RectF(0f, 0f, 400f, 200f), 1f)
            assertTrue(queuedTasks.isNotEmpty(), "Re-attach must re-enqueue missing base-layer tiles")

            while (queuedTasks.isNotEmpty()) {
                queuedTasks.removeFirst().run()
                ShadowLooper.idleMainLooper()
            }
            val draws = view.render(target)
            ShadowLooper.idleMainLooper()

            assertEquals(0, errorCount)
            assertEquals(1, readyCount, "Re-attached externalViewport view must fire onReady()")
            assertTrue(draws.isNotEmpty(), "Re-attached view must draw base tiles")
        } finally {
            view.recycle()
            target.recycle()
            controller.pause().stop().destroy()
        }
    }
}

private fun View.dispatchDetachedFromWindowForTest() {
    val parent = android.widget.FrameLayout(context)
    parent.addView(this)
    parent.removeView(this)
}
