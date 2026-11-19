package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Bitmap
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.image.NativeImageDecoder
import tachiyomi.core.common.util.system.ImageUtil
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Low-concurrency background prewarmer for upcoming pages:
 * 1. Sniffs dimensions for local/downloaded/archive pages before RecyclerView binds them so
 *    webtoon placeholders reserve exact height on frame 0 without layout jumps.
 * 2. Decodes a compact preview bitmap (<= 262,144 px, RGB_565 for opaque scans) into
 *    [ReaderPreviewCache] so page turns and fast flings render immediately while full tiles decode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ReaderPagePrewarmer(
    private val scope: CoroutineScope,
    private val onDimensionsSniffed: (page: ReaderPage, ratio: Float) -> Unit,
) {
    // Single-lane IO dispatcher preserves strict reading-direction priority (+1 before +2 before -1)
    // and avoids contending on sequential archive/PDF readers or starving foreground tile decodes.
    private val prewarmDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()
    private var surroundingJob: Job? = null
    private var chapterLeadJob: Job? = null

    fun prewarmAround(
        currentPage: ReaderPage,
        currentPages: List<ReaderPage>,
        nextChapterPages: List<ReaderPage>?,
        cropBorders: Boolean,
    ) {
        val currentIdx = currentPages.indexOf(currentPage)
            .takeIf { it >= 0 }
            ?: currentPages.indexOfFirst { it.index == currentPage.index }
        if (currentIdx < 0) return

        val targets = ArrayList<ReaderPage>(5)
        currentPages.getOrNull(currentIdx + 1)?.let(targets::add)
        currentPages.getOrNull(currentIdx + 2)?.let(targets::add)
        currentPages.getOrNull(currentIdx - 1)?.let(targets::add)
        if (nextChapterPages != null && currentPages.size - 1 - currentIdx <= NEXT_CHAPTER_PREWARM_THRESHOLD) {
            nextChapterPages.getOrNull(0)?.let(targets::add)
            nextChapterPages.getOrNull(1)?.let(targets::add)
        }
        if (targets.isEmpty()) return

        surroundingJob?.cancel()
        surroundingJob = scope.launch(prewarmDispatcher) {
            for (page in targets) {
                coroutineContext.ensureActive()
                prewarmPage(page, cropBorders)
            }
        }
    }

    fun prewarmChapterLead(
        pages: List<ReaderPage>?,
        cropBorders: Boolean,
    ) {
        if (pages.isNullOrEmpty()) return
        val leadPages = pages.take(CHAPTER_LEAD_PAGES)
        chapterLeadJob?.cancel()
        chapterLeadJob = scope.launch(prewarmDispatcher) {
            for (page in leadPages) {
                coroutineContext.ensureActive()
                prewarmPage(page, cropBorders)
            }
        }
    }

    fun cancel() {
        surroundingJob?.cancel()
        surroundingJob = null
        chapterLeadJob?.cancel()
        chapterLeadJob = null
    }

    private suspend fun prewarmPage(page: ReaderPage, cropBorders: Boolean) {
        if (page is InsertPage || page.isAnimatedImage) return
        if (page.chapter.pageLoader?.isRecycled == true) return
        val streamFn = page.stream ?: return

        if (page.imageDimensions == null && !page.isAnimatedImage) {
            val meta = try {
                streamFn().use { ImageUtil.sniffImageMetadata(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (meta != null) {
                if (meta.isAnimated) {
                    page.isAnimatedImage = true
                    return
                }
                val dims = meta.dimensions
                if (dims != null && dims.width > 0 && dims.height > 0) {
                    page.imageDimensions = dims
                    val ratio = dims.height.toFloat() / dims.width.toFloat()
                    withContext(Dispatchers.Main.immediate) {
                        onDimensionsSniffed(page, ratio)
                    }
                }
            }
        }

        coroutineContext.ensureActive()

        val key = ReaderPreviewCache.key(
            chapterId = page.chapter.chapter.id,
            pageIndex = page.index,
            cropBorders = cropBorders,
        ) ?: return
        if (ReaderPreviewCache.get(key) != null) return
        if (!inFlightKeys.add(key)) return
        try {
            if (page.chapter.pageLoader?.isRecycled == true) return
            decodePreview(streamFn, cropBorders)?.let { entry ->
                ReaderPreviewCache.put(key, entry)
            }
        } finally {
            inFlightKeys.remove(key)
        }
    }

    private fun decodePreview(
        streamFn: () -> InputStream,
        cropBorders: Boolean,
    ): ReaderPreviewCache.Entry? {
        return try {
            NativeImageDecoder.open(streamFn(), cropBorders).use { decoder ->
                if (decoder.isHdr || decoder.hasGainmap) {
                    // Preserve full-fidelity HDR / gainmap pipeline without SDR preview flash.
                    return null
                }
                val width = decoder.width
                val height = decoder.height
                if (width <= 0 || height <= 0) {
                    return null
                }
                val sampleSize = computePreviewSampleSize(width, height)
                val decoded = decoder.decode(region = null, sampleSize = sampleSize)
                val preview = if (!decoded.hasAlpha() && decoded.config != Bitmap.Config.RGB_565) {
                    val converted = decoded.copy(Bitmap.Config.RGB_565, false)
                    if (converted != null) {
                        decoded.recycle()
                        converted
                    } else {
                        decoded
                    }
                } else {
                    decoded
                }
                preview.setHasMipMap(true)
                ReaderPreviewCache.Entry(
                    bitmap = preview,
                    sourceWidth = width,
                    sourceHeight = height,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val PREVIEW_MAX_PIXELS = 262_144L
        private const val NEXT_CHAPTER_PREWARM_THRESHOLD = 3
        private const val CHAPTER_LEAD_PAGES = 2

        internal fun computePreviewSampleSize(width: Int, height: Int): Int {
            if (width <= 0 || height <= 0) return 1
            var sample = 1
            while (
                ((width.toLong() + sample - 1L) / sample) *
                ((height.toLong() + sample - 1L) / sample) > PREVIEW_MAX_PIXELS
            ) {
                sample *= 2
            }
            return sample
        }
    }
}
