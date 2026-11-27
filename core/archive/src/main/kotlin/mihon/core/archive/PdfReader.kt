package mihon.core.archive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.hippo.unifile.UniFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Wrapper over the system [PdfRenderer] to expose PDF pages as image streams.
 *
 * - Rendering is synchronized via [rendererLock] because [PdfRenderer] is not thread-safe and
 *   only allows one open page at a time.
 * - Rendered pages are persisted to a session directory (when [cacheDir] is provided) as WebP
 *   files outside [rendererLock] so scrolling back does not re-render pages and compression does
 *   not block rendering of neighboring pages. Falls back to an in-memory LRU when [cacheDir] is
 *   unavailable.
 */
class PdfReader(
    pfd: ParcelFileDescriptor,
    cacheDir: File? = null,
) : Closeable {

    private val renderer = PdfRenderer(pfd)
    private val rendererLock = Any()
    private val pageLocks = ConcurrentHashMap<Int, Any>()
    private val sessionCacheDir: File? = cacheDir?.let { base ->
        File(base, UUID.randomUUID().toString()).apply { mkdirs() }
    }
    private val fallbackCache = LruCache<Int, ByteArray>(FALLBACK_CACHE_SIZE)

    @Volatile
    private var isClosed = false

    val pageCount: Int
        get() = synchronized(rendererLock) { renderer.pageCount }

    /**
     * Renders the page at [index] to an encoded image and returns it as a stream.
     */
    fun getPageStream(index: Int): InputStream {
        val pageLock = pageLocks.computeIfAbsent(index) { Any() }
        synchronized(pageLock) {
            val cachedFile = sessionCacheDir?.let { File(it, "page_$index.webp") }
            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0L) {
                return cachedFile.inputStream()
            }

            if (cachedFile == null) {
                fallbackCache.get(index)?.let { return ByteArrayInputStream(it) }
            }

            val bitmap = renderPageBitmap(index)
            try {
                val format = webpCompressFormat()
                if (cachedFile != null && !isClosed) {
                    val tempFile = File(cachedFile.parentFile, "page_${index}_${UUID.randomUUID()}.tmp")
                    try {
                        FileOutputStream(tempFile).use { out ->
                            bitmap.compress(format, WEBP_QUALITY, out)
                            out.flush()
                        }
                        if (!tempFile.renameTo(cachedFile)) {
                            tempFile.copyTo(cachedFile, overwrite = true)
                            tempFile.delete()
                        }
                        if (cachedFile.exists() && cachedFile.length() > 0L) {
                            return cachedFile.inputStream()
                        }
                    } catch (_: Throwable) {
                        tempFile.delete()
                    }
                }

                val output = ByteArrayOutputStream()
                bitmap.compress(format, WEBP_QUALITY, output)
                val bytes = output.toByteArray()
                fallbackCache.put(index, bytes)
                return ByteArrayInputStream(bytes)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun renderPageBitmap(index: Int): Bitmap = synchronized(rendererLock) {
        check(!isClosed) { "PdfReader is closed" }
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
                return bitmap
            } catch (t: Throwable) {
                bitmap.recycle()
                throw t
            }
        }
    }

    override fun close() {
        isClosed = true
        synchronized(rendererLock) {
            fallbackCache.evictAll()
            runCatching { renderer.close() }
        }
        runCatching { sessionCacheDir?.deleteRecursively() }
    }

    private companion object {
        // ~2x FHD width: crisp on high-res screens with zoom headroom, without excessive memory use.
        const val TARGET_WIDTH_PX = 2160f
        const val MAX_DIMENSION_PX = 4096f
        const val WEBP_QUALITY = 92
        const val FALLBACK_CACHE_SIZE = 3

        @Suppress("DEPRECATION")
        fun webpCompressFormat(): Bitmap.CompressFormat {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
        }
    }
}

fun UniFile.pdfReader(context: Context): PdfReader = PdfReader(
    pfd = openFileDescriptor(context, "r"),
    cacheDir = File(context.cacheDir, "pdf_page_cache"),
)
