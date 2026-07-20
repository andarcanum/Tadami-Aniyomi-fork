package mihon.core.archive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.hippo.unifile.UniFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream

/**
 * Wrapper over the system [PdfRenderer] to expose PDF pages as image streams.
 *
 * - Rendering is synchronized because [PdfRenderer] is not thread-safe.
 * - Pages are rendered lazily and a small LRU of encoded pages is kept, because
 *   the reader may open the same page stream more than once (image type detection + decode).
 */
class PdfReader(pfd: ParcelFileDescriptor) : Closeable {

    private val renderer = PdfRenderer(pfd)
    private val lock = Any()
    private val encodedPageCache = LruCache<Int, ByteArray>(CACHE_SIZE)

    val pageCount: Int
        get() = synchronized(lock) { renderer.pageCount }

    /**
     * Renders the page at [index] to an encoded image and returns it as a stream.
     */
    fun getPageStream(index: Int): InputStream = ByteArrayInputStream(renderPage(index))

    private fun renderPage(index: Int): ByteArray {
        synchronized(lock) {
            encodedPageCache.get(index)?.let { return it }

            renderer.openPage(index).use { page ->
                val pageWidth = page.width.coerceAtLeast(1)
                val pageHeight = page.height.coerceAtLeast(1)

                // PDF page size is in PostScript points (1/72"). Upscale for crisp text/lineart on
                // modern screens, but keep dimensions bounded to avoid OOM on unusually large pages.
                val scale = minOf(
                    (TARGET_WIDTH_PX / pageWidth.toFloat()).coerceAtLeast(1f),
                    MAX_DIMENSION_PX / pageWidth.toFloat(),
                    MAX_DIMENSION_PX / pageHeight.toFloat(),
                )
                val bitmapWidth = (pageWidth * scale).toInt().coerceAtLeast(1)
                val bitmapHeight = (pageHeight * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                try {
                    // PDFs can have transparent backgrounds; render on white like desktop viewers do.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val output = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                    val bytes = output.toByteArray()
                    encodedPageCache.put(index, bytes)
                    return bytes
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            encodedPageCache.evictAll()
            runCatching { renderer.close() }
        }
    }

    private companion object {
        // ~2x FHD width: crisp on high-res screens with zoom headroom, without excessive memory use.
        const val TARGET_WIDTH_PX = 2160f
        const val MAX_DIMENSION_PX = 4096f
        const val JPEG_QUALITY = 95
        const val CACHE_SIZE = 3
    }
}

fun UniFile.pdfReader(context: Context): PdfReader = PdfReader(openFileDescriptor(context, "r"))
