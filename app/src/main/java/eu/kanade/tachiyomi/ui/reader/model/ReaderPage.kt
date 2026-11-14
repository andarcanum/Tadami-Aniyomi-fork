package eu.kanade.tachiyomi.ui.reader.model

import android.graphics.Rect
import eu.kanade.tachiyomi.source.model.Page
import tachiyomi.core.common.util.system.ImageUtil
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    var stream: (() -> InputStream)? = null,
) : Page(index, url, imageUrl, null) {

    open lateinit var chapter: ReaderChapter

    var isWide: Boolean = false

    /**
     * Pixel size of the page image, sniffed once on IO by the webtoon holder. The holder reserves
     * the page's final rendered height from it before the image is decoded, so the item does not
     * resize under the reader's finger.
     */
    var imageDimensions: ImageUtil.ImageDimensions? = null

    /**
     * Whether the image is an animated format. Animated pages change height between frames, so
     * their holders keep the viewport-sized placeholder.
     */
    var isAnimatedImage: Boolean = false

    /**
     * Cached horizontal smart-fit crop bounds keyed by the active [cropBorders] setting so
     * rebinds do not re-open the decoder repeatedly.
     */
    var smartFitBounds: Pair<Boolean, Rect>? = null

    /**
     * Whether the decoded image requires HDR window color mode.
     */
    var isHdr: Boolean = false
}
