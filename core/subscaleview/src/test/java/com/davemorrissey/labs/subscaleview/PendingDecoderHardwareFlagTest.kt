package com.davemorrissey.labs.subscaleview

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
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
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class PendingDecoderHardwareFlagTest {
    private class RecordingDecoder(initialHardware: Boolean) : ImageRegionDecoder, HardwareTileConfigurableDecoder {
        override var hardwareTilesEnabled: Boolean = initialHardware
        val hardwareFlagsUsedForTiles = mutableListOf<Boolean>()
        val outputs = mutableListOf<Bitmap>()
        var recycled = false
        private var initialized = false

        override fun init(context: Context, provider: InputProvider): Point {
            checkNotNull(provider.openStream()).use {}
            initialized = true
            return Point(400, 400)
        }

        override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
            check(isReady())
            hardwareFlagsUsedForTiles += hardwareTilesEnabled
            return Bitmap.createBitmap(
                (sRect.width() + sampleSize - 1) / sampleSize,
                (sRect.height() + sampleSize - 1) / sampleSize,
                Bitmap.Config.ARGB_8888,
            ).also { outputs += it }
        }

        override fun isReady(): Boolean = initialized && !recycled

        override fun recycle() {
            recycled = true
            initialized = false
        }
    }

    private class HardwareView : SubsamplingScaleImageView(ApplicationProvider.getApplicationContext<Context>()) {
        val createdDecoders = mutableListOf<RecordingDecoder>()

        override fun createRegionDecoder(): ImageRegionDecoder =
            RecordingDecoder(isHardwareTilesEnabled).also { createdDecoders += it }
    }

    private class Fixture(initialHardware: Boolean) : Closeable {
        private val queuedWorkers = ArrayDeque<Runnable>()
        val view = HardwareView().apply {
            setMaxTileSize(512)
            setMinimumTileDpi(0)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setExternalViewport(RectF(), 1f)
            setHardwareTilesEnabled(initialHardware)
            setExecutor(Executor { queuedWorkers.addLast(it) })
            measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 400)
        }

        fun bindImage(marker: Byte = 1): RecordingDecoder {
            view.setImage(ImageSource.inputStream(byteArrayOf(marker).inputStream()))
            return view.createdDecoders.last()
        }

        fun runNextWorkerWithoutAcceptingOnMain() {
            check(queuedWorkers.isNotEmpty())
            queuedWorkers.removeFirst().run()
        }

        fun drainWorkersAndMain() {
            ShadowLooper.idleMainLooper()
            while (queuedWorkers.isNotEmpty()) {
                queuedWorkers.removeFirst().run()
                ShadowLooper.idleMainLooper()
            }
        }

        override fun close() {
            view.setExecutor(Executor { it.run() })
            view.recycle()
            drainWorkersAndMain()
            assertTrue(view.createdDecoders.flatMap { it.outputs }.all { it.isRecycled })
        }
    }

    @Test
    fun firstTileUsesEnabledHardwareFlagChangedBeforeInitializationRuns() {
        Fixture(initialHardware = false).use { fixture ->
            val decoder = fixture.bindImage()
            fixture.view.setHardwareTilesEnabled(true)
            fixture.drainWorkersAndMain()

            assertEquals(
                listOf(true),
                decoder.hardwareFlagsUsedForTiles,
                "The first tile must consume the latest enabled hint",
            )
            assertTrue(fixture.view.isHardwareTilesEnabled)
            assertTrue(fixture.view.isReady)
            assertFalse(decoder.recycled)
        }
    }

    @Test
    fun firstTileUsesDisabledHardwareFlagChangedAfterInitializationBeforeAcceptance() {
        Fixture(initialHardware = true).use { fixture ->
            val decoder = fixture.bindImage()
            fixture.runNextWorkerWithoutAcceptingOnMain()
            fixture.view.setHardwareTilesEnabled(false)
            fixture.drainWorkersAndMain()

            assertEquals(
                listOf(false),
                decoder.hardwareFlagsUsedForTiles,
                "The first tile must consume the latest disabled hint",
            )
            assertFalse(fixture.view.isHardwareTilesEnabled)
            assertTrue(fixture.view.isReady)
            assertFalse(decoder.recycled)
        }
    }

    @Test
    fun staleInitializedDecoderIsClosedWithoutChangingReplacementTileHardwareFlag() {
        Fixture(initialHardware = false).use { fixture ->
            val obsolete = fixture.bindImage()
            fixture.runNextWorkerWithoutAcceptingOnMain()
            fixture.view.setHardwareTilesEnabled(true)
            val replacement = fixture.bindImage(marker = 2)
            fixture.drainWorkersAndMain()

            assertTrue(obsolete.recycled, "An obsolete initialized decoder must still close")
            assertTrue(obsolete.hardwareFlagsUsedForTiles.isEmpty(), "An obsolete decoder must never load tiles")
            assertEquals(listOf(true), replacement.hardwareFlagsUsedForTiles)
            assertFalse(replacement.recycled)
            assertTrue(fixture.view.isHardwareTilesEnabled)
            assertTrue(fixture.view.isReady)
        }
    }
}
