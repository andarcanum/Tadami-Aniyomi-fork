package eu.kanade.presentation.reader.novel

import android.webkit.WebView
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState

/**
 * The single scrollable thing auto-scroll drives.
 *
 * The auto-scroll loop in [NovelReaderScreen] branched four times - book surface, chapter WebView,
 * page reader and native lazy list - and every branch repeated the same frame bookkeeping around
 * three genuinely different operations: "is this paginated", "move by N pixels and tell me what was
 * consumed", "can it still move forward". This interface is that seam, so the loop can stay one
 * body and each surface only describes itself.
 *
 * Implementations deliberately reproduce the exact semantics of the branch they replace, including
 * the places where the old code reported the *requested* pixels instead of the consumed ones.
 */
internal interface NovelAutoScrollTarget {

    /** True while the surface advances by discrete pages instead of a continuous scroll. */
    fun isPaginated(): Boolean

    /** True while auto-scroll still has somewhere to go on this surface. */
    fun canScrollForward(): Boolean

    /** Scrolls by [distancePx] and returns the pixels the surface reports as consumed. */
    suspend fun scrollBy(distancePx: Int): Float

    /** Steps one page. Only called while [isPaginated] is true. */
    suspend fun stepPage(forward: Boolean)

    /**
     * Characters on the page currently shown, for the adaptive page delay.
     *
     * Only the page reader has a per-page character count; continuous surfaces pace themselves by
     * pixels per frame and return 0, which makes the adaptive delay fall back to the base interval.
     */
    fun pageDelayCharacterCount(): Int = 0

    /**
     * Syncs continuous in-document auto-scroll speed.
     *
     * Returns true when the target owns continuous in-document movement (e.g. scrolled book WebView).
     */
    fun syncAutoScrollSpeed(physicalPixelsPerSecond: Float): Boolean = false
}

/**
 * [NovelAutoScrollTarget] over the mounted book renderer.
 *
 * A scrolled book stitches the next section in at its boundary, so the surface answering "cannot
 * scroll further" is not the end of the book while [hasSectionsLeft] is still true. Losing that
 * distinction is what used to stop auto-scroll mid-book.
 */
internal class BookSurfaceAutoScrollTarget(
    private val surface: NovelBookScrollSurface,
    private val hasSectionsLeft: () -> Boolean,
) : NovelAutoScrollTarget {

    override fun isPaginated(): Boolean = surface.isPaginated()

    override fun canScrollForward(): Boolean = surface.canScrollForward() || hasSectionsLeft()

    override suspend fun scrollBy(distancePx: Int): Float = surface.scrollBy(distancePx).toFloat()

    override suspend fun stepPage(forward: Boolean) = surface.step(forward)

    override fun syncAutoScrollSpeed(physicalPixelsPerSecond: Float): Boolean =
        surface.syncAutoScrollSpeed(physicalPixelsPerSecond)
}

/**
 * [NovelAutoScrollTarget] over the chapter WebView.
 *
 * [reachedProgressThreshold] is the reported reading progress reaching the end of the document,
 * which arrives before the WebView itself runs out of scrollable pixels. The old branch spelled the
 * same condition as `canScrollVertically(1) && !(nearEnd || !canScrollVertically(1))`, which reduces
 * to the expression below.
 */
internal class WebViewAutoScrollTarget(
    private val webView: WebView,
    private val reachedProgressThreshold: () -> Boolean,
) : NovelAutoScrollTarget {

    override fun isPaginated(): Boolean = false

    override fun canScrollForward(): Boolean =
        webView.canScrollVertically(1) && !reachedProgressThreshold()

    /**
     * `WebView.scrollBy` reports nothing, so a scroll that was attempted counts as fully consumed
     * and a scroll that was not attempted counts as zero. End detection therefore rests on
     * [canScrollForward] here, exactly as it did before.
     */
    override suspend fun scrollBy(distancePx: Int): Float {
        if (!webView.canScrollVertically(1)) return 0f
        webView.scrollBy(0, distancePx)
        return distancePx.toFloat()
    }

    override suspend fun stepPage(forward: Boolean) = Unit
}

/**
 * [NovelAutoScrollTarget] over the page reader.
 *
 * Pixel scrolling is meaningless here: the reader turns pages, so [scrollBy] consumes nothing and
 * the loop is expected to pace itself with the page delay instead of frame steps.
 */
internal class PageReaderAutoScrollTarget(
    private val currentPageIndex: () -> Int,
    private val pageCount: () -> Int,
    private val characterCount: () -> Int,
    private val onStepPage: suspend (Boolean) -> Unit,
) : NovelAutoScrollTarget {

    override fun isPaginated(): Boolean = true

    override fun canScrollForward(): Boolean = currentPageIndex() < pageCount() - 1

    override suspend fun scrollBy(distancePx: Int): Float = 0f

    override suspend fun stepPage(forward: Boolean) = onStepPage(forward)

    override fun pageDelayCharacterCount(): Int = characterCount()
}

/**
 * [NovelAutoScrollTarget] over the native scroll list, which also hosts book sections when the
 * native book renderer publishes no surface of its own.
 *
 * [nearConfiguredEnd] carries the user's auto-scroll end offset: the list is treated as finished a
 * configurable distance before its real bottom.
 */
internal class LazyListAutoScrollTarget(
    private val listState: LazyListState,
    private val nearConfiguredEnd: () -> Boolean,
) : NovelAutoScrollTarget {

    override fun isPaginated(): Boolean = false

    override fun canScrollForward(): Boolean = listState.canScrollForward && !nearConfiguredEnd()

    override suspend fun scrollBy(distancePx: Int): Float = listState.scrollBy(distancePx.toFloat())

    override suspend fun stepPage(forward: Boolean) = Unit
}
