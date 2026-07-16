package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import mihon.core.archive.PdfReader
import java.io.IOException

/**
 * Loader used to load a chapter from a .pdf file. Pages are rendered lazily via the
 * system PdfRenderer when the reader requests their stream.
 */
internal class PdfPageLoader(private val reader: PdfReader) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val pageCount = reader.pageCount
        return (0 until pageCount).map { i ->
            ReaderPage(i).apply {
                stream = {
                    if (isRecycled) throw IOException("PDF reader is no longer available")
                    reader.getPageStream(i)
                }
                status = Page.State.READY
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        if (isRecycled) return
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
