package com.davemorrissey.labs.subscaleview

import android.app.Application
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
import com.davemorrissey.labs.subscaleview.decoder.HardwareTileConfigurableDecoder
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SubsamplingScaleImageViewRecoveryTest {

    private class RecordingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        val drawnBitmaps = mutableListOf<Bitmap>()

        override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
            drawnBitmaps += bitmap
            super.drawBitmap(bitmap, matrix, paint)
        }

        override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
            drawnBitmaps += bitmap
            super.drawBitmap(bitmap, src, dst, paint)
        }
    }

    private class ControllableDecoder(
        private val dimensions: Point = Point(4000, 40000),
        private val baseSampleThreshold: Int = 8,
    ) : ImageRegionDecoder, HardwareTileConfigurableDecoder {
        override var hardwareTilesEnabled: Boolean = true
        val baseBitmaps = mutableSetOf<Bitmap>()
        val detailBitmaps = mutableSetOf<Bitmap>()
        val decodedRegions = mutableListOf<Pair<Rect, Int>>()
        var failPredicate: ((Rect, Int, Boolean) -> Boolean)? = null
        var recycled = false

        override fun init(context: Context, provider: InputProvider): Point = Point(dimensions)

        override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
            decodedRegions += Rect(sRect) to sampleSize
            if (failPredicate?.invoke(sRect, sampleSize, hardwareTilesEnabled) == true) {
                throw IllegalStateException("Simulated decode failure (hw=$hardwareTilesEnabled)")
            }
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            if (sampleSize >= baseSampleThreshold) {
                baseBitmaps += bitmap
            } else {
                detailBitmaps += bitmap
            }
            return bitmap
        }

        override fun isReady(): Boolean = !recycled

        override fun recycle() {
            recycled = true
        }
    }

    private class RecoveryTestView(
        val fake: ControllableDecoder = ControllableDecoder(),
    ) : SubsamplingScaleImageView(ApplicationProvider.getApplicationContext<Context>()) {
        override fun createRegionDecoder(): ImageRegionDecoder = fake
    }

    private class QueueExecutor : Executor {
        val queue = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            queue.addLast(command)
        }

        fun runNext(): Boolean {
            val next = queue.removeFirstOrNull() ?: return false
            next.run()
            ShadowLooper.idleMainLooper()
            return true
        }

        fun drainAll() {
            while (queue.isNotEmpty()) {
                queue.removeFirst().run()
                ShadowLooper.idleMainLooper()
            }
        }
    }

    private fun RecoveryTestView.renderWithCanvas(
        target: Bitmap,
        clipRect: Rect? = null,
    ): RecordingCanvas {
        measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, measuredWidth, measuredHeight)
        val canvas = RecordingCanvas(target)
        if (clipRect != null) {
            canvas.clipRect(clipRect)
        }
        draw(canvas)
        ShadowLooper.idleMainLooper()
        return canvas
    }

    @Test
    fun emptyExternalViewportStillDrawsBaseLayer() {
        val view = RecoveryTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setExternalViewport(RectF(0f, 0f, 400f, 400f), 1f)
        }
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            repeat(2) { view.renderWithCanvas(target) }

            // Clear external viewport (as happens during detach/rebind races) and draw again.
            view.setExternalViewport(RectF(), 1f)
            val canvas = view.renderWithCanvas(target, Rect(0, 0, 400, 400))
            assertTrue(
                canvas.drawnBitmaps.any { it in view.fake.baseBitmaps },
                "Base layer must still draw when externalViewport is temporarily empty",
            )
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun partialExternalViewportLagDrawsBaseLayerUnderUncoveredClip() {
        val view = RecoveryTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            // Only the top half (0..200) is marked visible by externalViewport.
            setExternalViewport(RectF(0f, 0f, 400f, 200f), 2f)
        }
        val target = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            repeat(2) { view.renderWithCanvas(target, Rect(0, 0, 400, 200)) }

            // Now canvas clip extends to 0..400 before externalViewport catches up:
            // detail tiles cover only 0..200, so base layer must draw underneath 200..400.
            val canvas = view.renderWithCanvas(target, Rect(0, 0, 400, 400))
            assertTrue(
                canvas.drawnBitmaps.any { it in view.fake.baseBitmaps },
                "Base layer must render when canvas clip extends past loaded detail tiles",
            )
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun tileDecodeFailureFallsBackToSoftwareTilesAndRecovers() {
        val view = RecoveryTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setHardwareTilesEnabled(true)
            fake.failPredicate = { rect, sample, hw ->
                sample < 8 && rect.left == 0 && rect.top == 0 && hw
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
            repeat(2) { view.renderWithCanvas(target) }
            // Advance through retry #1 (50ms) and retry #2 (100ms, software fallback).
            ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)

            assertFalse(
                view.isHardwareTilesEnabled,
                "Repeated tile decode failures must fall back to software tile decoding",
            )
            assertFalse(
                view.fake.hardwareTilesEnabled,
                "Active decoder must be switched to software tiles on fallback",
            )
            assertEquals(0, tileErrors, "Software fallback recovery must not fire terminal onTileLoadError")
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun failedTileRecoversAfterLeavingViewportOrExplicitRetryFailedTiles() {
        var shouldFail = true
        val view = RecoveryTestView().apply {
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            fake.failPredicate = { rect, sample, _ ->
                shouldFail && sample < 8 && rect.left == 0 && rect.top == 0
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
            repeat(2) { view.renderWithCanvas(target) }
            ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
            assertEquals(1, tileErrors)

            // 1. Explicit retryFailedTiles() resets failed tiles and decodes them cleanly.
            shouldFail = false
            view.fake.decodedRegions.clear()
            assertTrue(view.retryFailedTiles(), "retryFailedTiles must report that failed tiles were queued")
            ShadowLooper.idleMainLooper()
            assertTrue(
                view.fake.decodedRegions.any { it.second < 8 && it.first.left == 0 && it.first.top == 0 },
                "retryFailedTiles must re-decode previously failed visible tiles",
            )

            // 2. Fail a different tile, scroll away so it exits viewport, then scroll back to recover.
            shouldFail = true
            view.fake.failPredicate = { rect, sample, _ ->
                shouldFail && sample < 8 && rect.top in 4000..8000
            }
            view.setExternalViewport(RectF(0f, 500f, 400f, 700f), 2f)
            repeat(2) { view.renderWithCanvas(target) }
            ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
            ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)

            // Scroll far away so the failed band exits the retain viewport and resets its failed flag.
            shouldFail = false
            view.setExternalViewport(RectF(0f, 2500f, 400f, 2700f), 2f)
            repeat(2) { view.renderWithCanvas(target) }

            view.fake.decodedRegions.clear()
            view.setExternalViewport(RectF(0f, 500f, 400f, 700f), 2f)
            repeat(2) { view.renderWithCanvas(target) }
            assertTrue(
                view.fake.decodedRegions.any { it.second < 8 && it.first.top in 4000..8000 },
                "Leaving and re-entering viewport must allow previously failed tiles to recover",
            )
        } finally {
            view.recycle()
            target.recycle()
        }
    }

    @Test
    fun onTilesInitedDimensionMismatchRetainsPreviewUntilBaseLayerReady() {
        val executor = QueueExecutor()
        // Actual decoded dimensions differ from pre-supplied dimensions (e.g. border crop).
        val view = RecoveryTestView(ControllableDecoder(Point(960, 2000), baseSampleThreshold = 1)).apply {
            setExecutor(executor)
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
        }
        var loadedCount = 0
        view.setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
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

            // Run only TilesInitTask (before base-layer TileLoadTasks finish).
            assertTrue(executor.runNext(), "Expected TilesInitTask to be queued")
            val canvasBeforeBase = view.renderWithCanvas(target)
            assertEquals(0, loadedCount, "Base layer tiles have not finished decoding yet")
            assertTrue(
                canvasBeforeBase.drawnBitmaps.contains(preview),
                "Preview bitmap must remain visible after dimension mismatch until base layer is ready",
            )

            // Now finish base-layer tiles: handoff completes without black gap.
            executor.drainAll()
            val canvasAfterBase = view.renderWithCanvas(target)
            assertEquals(1, loadedCount, "onImageLoaded must fire once base layer is ready")
            assertFalse(
                canvasAfterBase.drawnBitmaps.contains(preview),
                "Preview bitmap must be cleared once base layer tiles are ready",
            )
            assertTrue(
                canvasAfterBase.drawnBitmaps.any { it in view.fake.baseBitmaps },
                "Base layer tiles must render after handoff",
            )
        } finally {
            view.recycle()
            executor.drainAll()
            preview.recycle()
            target.recycle()
        }
    }
}
