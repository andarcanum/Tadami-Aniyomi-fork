package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Bitmap
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.image.NativeImageDecoder

/**
 * Loads lightweight page preview thumbnails while scrubbing the chapter slider.
 *
 * Fast path: returns an already-cached preview from [ReaderPreviewCache] synchronously.
 * Fallback: if the page's local/cached stream is available, decodes a compact RGB_565 thumbnail
 * via [NativeImageDecoder] on [Dispatchers.IO] and stores it back into [ReaderPreviewCache].
 */
object ReaderScrubPreviewLoader {

    suspend fun loadPreview(
        page: ReaderPage?,
        cropBorders: Boolean,
    ): Bitmap? {
        if (page == null || page is InsertPage || page.isAnimatedImage) return null
        val key = ReaderPreviewCache.key(
            chapterId = page.chapter.chapter.id,
            pageIndex = page.index,
            cropBorders = cropBorders,
        )
        if (key != null) {
            ReaderPreviewCache.get(key)?.bitmap?.takeIf { !it.isRecycled }?.let { return it }
        }
        if (page.chapter.pageLoader?.isRecycled == true) return null
        val streamFn = page.stream ?: return null

        return withContext(Dispatchers.IO) {
            try {
                NativeImageDecoder.open(streamFn(), cropBorders).use { decoder ->
                    val width = decoder.width
                    val height = decoder.height
                    if (width <= 0 || height <= 0) return@withContext null
                    val sampleSize = ReaderProgressHudLogic.computeScrubSampleSize(width, height)
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
                    if (key != null) {
                        ReaderPreviewCache.put(
                            key = key,
                            entry = ReaderPreviewCache.Entry(
                                bitmap = preview,
                                sourceWidth = width,
                                sourceHeight = height,
                            ),
                        )
                    }
                    preview
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }
    }
}
