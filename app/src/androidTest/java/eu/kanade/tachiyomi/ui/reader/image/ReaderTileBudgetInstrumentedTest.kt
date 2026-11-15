package eu.kanade.tachiyomi.ui.reader.image

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Debug
import android.util.Log
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.HdrAwareRegionDecoder
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor

/**
 * Android bitmap-allocation acceptance for SSIV's permanent external-viewport base layer.
 * Only encoded/native region decoding is replaced. SSIV selects samples, constructs its grids,
 * accepts outputs on Main, and owns/recycles actual correctly-sized Android software bitmaps.
 * An empty external viewport excludes detail allocations. Four retained views are a controlled
 * holder dataset, not the whole RecyclerView cache. These bytes are not RSS, native decoder
 * buffers, GPU allocations, HDR codec coverage, or measured old/low-RAM-device performance.
 * Process-wide memory diagnostics include unrelated work and allocator/GC effects; they are not
 * attributed to these bitmaps and never form acceptance assertions.
 */
@RunWith(AndroidJUnit4::class)
class ReaderTileBudgetInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun sdr_two_tile_page_keeps_permanent_base_within_one_mib() {
        val fixture = readyFixture(2046, 4090, 2048, Bitmap.Config.ARGB_8888)
        try {
            val snapshot = snapshot(fixture)
            logAllocation("sdr-2046x4090", snapshot)
            assertBaseOnly(fixture, snapshot)
            assertTrue(
                "Permanent SDR base must be <= 1048576 B, actual=${snapshot.baseBytes} B",
                snapshot.baseBytes <= SDR_BASE_BYTES,
            )
        } finally {
            recycleFixture(fixture)
        }
    }

    @Test
    fun four_retained_sdr_pages_keep_base_allocations_within_four_mib() {
        val fixtures = mutableListOf<Fixture>()
        try {
            repeat(4) {
                fixtures += readyFixture(2046, 4090, 2048, Bitmap.Config.ARGB_8888)
            }
            val snapshots = fixtures.mapIndexed { index, fixture ->
                snapshot(fixture).also { logAllocation("retained-sdr-holder-$index", it) }
            }
            val totalBytes = snapshots.sumOf { it.baseBytes }
            Log.i(TAG, "dataset=4-independent-retained-sdr-holders baseBytes=$totalBytes unit=B")
            fixtures.zip(snapshots).forEach { (fixture, snapshot) ->
                assertBaseOnly(fixture, snapshot)
            }
            assertTrue(
                "Four permanent SDR bases must be <= 4194304 B, actual=$totalBytes B",
                totalBytes <= FOUR_SDR_BASE_BYTES,
            )
            snapshots.forEach {
                assertTrue("Each retained SDR base must be <= 1048576 B", it.baseBytes <= SDR_BASE_BYTES)
            }
        } finally {
            var cleanupError: Throwable? = null
            for (fixture in fixtures) {
                try {
                    recycleFixture(fixture)
                } catch (error: Throwable) {
                    val previous = cleanupError
                    if (previous == null) cleanupError = error else previous.addSuppressed(error)
                }
            }
            cleanupError?.let { throw it }
        }
    }

    @Test
    fun sdr_split_edge_pages_keep_aggregate_base_within_one_mib() {
        // Literal cases exercise actual tile splitting and ceil-rounded edge outputs, not
        // an expected value computed by TileQuality or an unsplit-image pixel formula.
        val cases = listOf(
            Triple(2046, 4092, 2048),
            Triple(1024, 65536, 2048),
            Triple(2048, 2048, 256),
        )
        val snapshots = cases.map { (width, height, maxTile) ->
            val fixture = readyFixture(width, height, maxTile, Bitmap.Config.ARGB_8888)
            try {
                snapshot(fixture).also {
                    logAllocation("sdr-${width}x$height-max$maxTile", it)
                    assertBaseOnly(fixture, it)
                }
            } finally {
                recycleFixture(fixture)
            }
        }
        snapshots.forEach {
            assertTrue(
                "Split SDR base ${it.sourceWidth}x${it.sourceHeight} must be <= 1048576 B, " +
                    "actual=${it.baseBytes} B",
                it.baseBytes <= SDR_BASE_BYTES,
            )
        }
    }

    @Test
    fun half_float_two_tile_page_keeps_permanent_base_within_two_mib() {
        val fixture = readyFixture(2046, 4090, 2048, Bitmap.Config.RGBA_F16)
        try {
            val snapshot = snapshot(fixture)
            logAllocation("half-float-2046x4090", snapshot)
            assertBaseOnly(fixture, snapshot)
            assertTrue(
                "Permanent F16 base must be <= 2097152 B, actual=${snapshot.baseBytes} B",
                snapshot.baseBytes <= F16_BASE_BYTES,
            )
        } finally {
            recycleFixture(fixture)
        }
    }

    private fun readyFixture(width: Int, height: Int, maxTile: Int, config: Bitmap.Config): Fixture {
        logProcessMemory("before-${width}x$height-max$maxTile-$config")
        val fixture = onMain {
            val decoder = SizedRegionDecoder(width, height, config)
            val view = BudgetView(ApplicationProvider.getApplicationContext(), decoder).apply {
                setExecutor(Executor { it.run() })
                setMaxTileSize(maxTile)
                setHardwareTilesEnabled(false)
                setMinimumTileDpi(0)
                setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
                setExternalViewport(RectF(), 1f)
                val spec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
                measure(spec, spec)
                layout(0, 0, 400, 400)
                setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            }
            Fixture(view, decoder, width, height, config)
        }
        try {
            // Inline decode tasks still post init/base acceptance to the real Main Handler.
            // Waiting for queue idle drains those callbacks without sleeps or a new worker.
            instrumentation.waitForIdleSync()
            onMain {
                @Suppress("DEPRECATION")
                fixture.view.trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            }
            instrumentation.waitForIdleSync()
            return fixture
        } catch (error: Throwable) {
            try {
                recycleFixture(fixture)
            } catch (cleanupError: Throwable) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    private fun snapshot(fixture: Fixture): Snapshot = onMain {
        val view = fixture.view
        // Observe accepted ownership; no production accessors or test-only cleanup seams.
        val sampleField = SubsamplingScaleImageView::class.java.getDeclaredField("fullImageSampleSize").apply {
            isAccessible = true
        }
        val mapField = SubsamplingScaleImageView::class.java.getDeclaredField("tileMap").apply {
            isAccessible = true
        }
        val sample = sampleField.getInt(view)
        val tileMap = checkNotNull(mapField.get(view) as? Map<*, *>) { "SSIV must initialise a tile map" }
        val baseTiles = checkNotNull(tileMap[sample] as? List<*>) { "SSIV must retain a base layer" }
        check(baseTiles.isNotEmpty()) { "SSIV must retain nonempty base tiles" }
        val bitmapField = checkNotNull(baseTiles.first()).javaClass.getDeclaredField("bitmap").apply {
            isAccessible = true
        }
        val allocations = baseTiles.map { tile ->
            val bitmap = checkNotNull(bitmapField.get(tile) as? Bitmap) { "Base output must be accepted" }
            check(!bitmap.isRecycled) { "Ready base output must remain live" }
            Allocation(bitmap.width, bitmap.height, bitmap.rowBytes, bitmap.config, bitmap.allocationByteCount.toLong())
        }
        val detailCount = tileMap.entries.filter { it.key != sample }.sumOf { entry ->
            (entry.value as List<*>).count { bitmapField.get(it) != null }
        }
        Snapshot(
            sample = sample,
            sourceWidth = view.sWidth,
            sourceHeight = view.sHeight,
            viewWidth = view.width,
            viewHeight = view.height,
            ready = view.isReady,
            imageLoaded = view.isImageLoaded,
            hdr = view.isHdr,
            allocations = allocations,
            detailCount = detailCount,
            liveOutputs = fixture.decoder.outputs.count { !it.isRecycled },
            createdOutputs = fixture.decoder.outputs.size,
        )
    }

    private fun assertBaseOnly(fixture: Fixture, snapshot: Snapshot) {
        assertTrue("Empty-viewport holder must stay ready", snapshot.ready && snapshot.imageLoaded)
        assertEquals("Source width must not be downsampled in layout", fixture.width, snapshot.sourceWidth)
        assertEquals("Source height must not be downsampled in layout", fixture.height, snapshot.sourceHeight)
        assertEquals(400, snapshot.viewWidth)
        assertEquals(400, snapshot.viewHeight)
        assertEquals("Empty viewport must not retain detail bitmaps", 0, snapshot.detailCount)
        assertEquals("Only accepted base outputs may remain live", snapshot.allocations.size, snapshot.liveOutputs)
        assertEquals(
            "Empty viewport must not allocate transient detail",
            snapshot.allocations.size,
            snapshot.createdOutputs,
        )
        assertTrue("Base allocation must be positive", snapshot.baseBytes > 0L)
        snapshot.allocations.forEach { assertEquals(fixture.config, it.config) }
        assertEquals(fixture.config == Bitmap.Config.RGBA_F16, snapshot.hdr)
    }

    private fun logAllocation(label: String, snapshot: Snapshot) {
        snapshot.allocations.forEachIndexed { index, allocation ->
            Log.i(
                TAG,
                "case=$label baseTile=$index sample=${snapshot.sample} " +
                    "size=${allocation.width}x${allocation.height} config=${allocation.config} " +
                    "rowBytes=${allocation.rowBytes} allocationBytes=${allocation.bytes} unit=B",
            )
        }
        Log.i(TAG, "case=$label baseTiles=${snapshot.allocations.size} baseBytes=${snapshot.baseBytes} unit=B")
        logProcessMemory("snapshot-$label")
    }

    private fun logProcessMemory(stage: String) {
        try {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            // MemoryInfo's total PSS may include proportional swap. It is not RSS, and
            // native allocator totals do not isolate bitmap, codec, or GPU storage.
            Log.i(
                TAG,
                "diagnostic=$stage nativeHeapAllocatedB=${Debug.getNativeHeapAllocatedSize()} " +
                    "processPSSKiB=${info.totalPss} privateDirtyKiB=${info.totalPrivateDirty} " +
                    "pssMayIncludeSwap=true attribution=process-wide",
            )
        } catch (error: Exception) {
            Log.i(TAG, "diagnostic=$stage unavailable=${error.javaClass.simpleName}")
        }
    }

    private fun recycleFixture(fixture: Fixture) {
        try {
            onMain { fixture.view.recycle() }
            instrumentation.waitForIdleSync()
            assertTrue("SSIV must recycle all caller-owned outputs", fixture.decoder.outputs.all { it.isRecycled })
        } finally {
            // A failed ownership assertion must not leak a large Android bitmap into the next case.
            onMain { fixture.decoder.outputs.filterNot { it.isRecycled }.forEach(Bitmap::recycle) }
            logProcessMemory("after-recycle-${fixture.width}x${fixture.height}-${fixture.config}")
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        // Propagate failures on the instrumentation test thread instead of crashing Main.
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private class BudgetView(context: Context, private val decoder: ImageRegionDecoder) :
        SubsamplingScaleImageView(context) {
        override fun createRegionDecoder(): ImageRegionDecoder = decoder
    }

    private class SizedRegionDecoder(
        private val width: Int,
        private val height: Int,
        private val config: Bitmap.Config,
    ) : HdrAwareRegionDecoder {
        val outputs = mutableListOf<Bitmap>()
        private var ready = false
        override val isHdr: Boolean = config == Bitmap.Config.RGBA_F16

        override fun init(context: Context, provider: InputProvider): Point {
            checkNotNull(provider.openStream()).use { }
            ready = true
            return Point(width, height)
        }

        override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
            check(ready)
            require(sampleSize >= 1 && !sRect.isEmpty)
            require(sRect.left >= 0 && sRect.top >= 0 && sRect.right <= width && sRect.bottom <= height)
            val outputWidth = ((sRect.width().toLong() + sampleSize - 1) / sampleSize).toInt()
            val outputHeight = ((sRect.height().toLong() + sampleSize - 1) / sampleSize).toInt()
            return Bitmap.createBitmap(outputWidth, outputHeight, config).also { bitmap ->
                try {
                    // Touch the pixel pages as a real decode does, rather than measuring
                    // only a newly reserved, potentially lazy-backed allocation.
                    bitmap.eraseColor(Color.WHITE)
                } catch (error: Throwable) {
                    bitmap.recycle()
                    throw error
                }
                outputs += bitmap
            }
        }

        override fun isReady(): Boolean = ready

        override fun recycle() {
            // Returned bitmaps belong to SSIV, just as with the real region decoder.
            ready = false
        }
    }

    private data class Fixture(
        val view: BudgetView,
        val decoder: SizedRegionDecoder,
        val width: Int,
        val height: Int,
        val config: Bitmap.Config,
    )

    private data class Allocation(
        val width: Int,
        val height: Int,
        val rowBytes: Int,
        val config: Bitmap.Config?,
        val bytes: Long,
    )

    private data class Snapshot(
        val sample: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val viewWidth: Int,
        val viewHeight: Int,
        val ready: Boolean,
        val imageLoaded: Boolean,
        val hdr: Boolean,
        val allocations: List<Allocation>,
        val detailCount: Int,
        val liveOutputs: Int,
        val createdOutputs: Int,
    ) {
        val baseBytes: Long get() = allocations.sumOf { it.bytes }
    }

    companion object {
        private const val TAG = "ReaderTileBudget"
        private const val SDR_BASE_BYTES = 1_048_576L
        private const val FOUR_SDR_BASE_BYTES = 4_194_304L
        private const val F16_BASE_BYTES = 2_097_152L
    }
}
