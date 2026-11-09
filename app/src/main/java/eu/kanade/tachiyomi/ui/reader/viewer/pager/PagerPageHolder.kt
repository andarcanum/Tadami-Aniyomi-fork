package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.tadami.aurora.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ProcessedPageImage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val page: ReaderPage,
) : ReaderPageImageView(readerThemedContext), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page

    /**
     * Loading progress bar to indicate the current progress.
     */
    private var progressIndicator: ReaderProgressIndicator? = null // = ReaderProgressIndicator(readerThemedContext)

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    private val scope = MainScope()

    /**
     * Job for loading the page and processing changes to the page's status.
     */
    private var loadJob: Job? = null

    init {
        loadJob = scope.launch { loadPageAndProcessStatus() }
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        loadJob = null
    }

    private fun initProgressIndicator() {
        if (progressIndicator == null) {
            progressIndicator = ReaderProgressIndicator(context)
            addView(progressIndicator)
        }
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus() {
        val loader = page.chapter.pageLoader ?: return

        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.QUEUE -> setQueued()
                    Page.State.LOAD_PAGE -> setLoading()
                    Page.State.DOWNLOAD_IMAGE -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progressIndicator?.setProgress(value)
                        }
                    }
                    Page.State.READY -> setImage()
                    Page.State.ERROR -> setError()
                }
            }
        }
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressIndicator?.setProgress(0)

        val streamFn = page.stream ?: return

        try {
            val prepared = withIOContext {
                when (val processed = streamFn().use { process(item, Buffer().readFrom(it)) }) {
                    is ProcessedPageImage.Decoded -> PageImageResult.Decoded(processed.bitmap)
                    is ProcessedPageImage.Encoded -> {
                        val source = processed.source
                        val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                        val background = if (!isAnimated && viewer.config.automaticBackground) {
                            ImageUtil.chooseBackground(context, source.peek().inputStream())
                        } else {
                            null
                        }
                        PageImageResult.Encoded(
                            source = source,
                            isAnimated = isAnimated,
                            background = background,
                            dimensions = ImageUtil.getImageDimensions(source),
                        )
                    }
                }
            }
            withUIContext {
                val config = Config(
                    zoomDuration = viewer.config.doubleTapAnimDuration,
                    minimumScaleType = viewer.config.imageScaleType,
                    cropBorders = viewer.config.imageCropBorders,
                    zoomStartPosition = viewer.config.imageZoomType,
                    landscapeZoom = viewer.config.landscapeZoom,
                    enablePinchToZoom = viewer.config.enablePinchToZoom,
                )
                when (prepared) {
                    is PageImageResult.Encoded -> {
                        prepared.dimensions?.let {
                            viewer.activity.viewModel.onReaderPageImageDimensionsAvailable(
                                page = page,
                                width = it.width,
                                height = it.height,
                            )
                        }
                        setImage(prepared.source, prepared.isAnimated, config)
                        if (!prepared.isAnimated) {
                            pageBackground = prepared.background
                        }
                    }
                    is PageImageResult.Decoded -> {
                        viewer.activity.viewModel.onReaderPageImageDimensionsAvailable(
                            page = page,
                            width = prepared.bitmap.width,
                            height = prepared.bitmap.height,
                        )
                        // Split/rotate results arrive pre-decoded: hand the bitmap straight to
                        // the image view instead of a JPEG q=100 re-encode + second decode.
                        // automaticBackground is not applicable (it samples the encoded stream).
                        setImage(BitmapDrawable(resources, prepared.bitmap), config)
                        pageBackground = null
                    }
                }
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                markDecodeError()
            }
        }
    }

    private sealed interface PageImageResult {
        data class Encoded(
            val source: BufferedSource,
            val isAnimated: Boolean,
            val background: android.graphics.drawable.Drawable?,
            val dimensions: ImageUtil.ImageDimensions?,
        ) : PageImageResult

        data class Decoded(val bitmap: Bitmap) : PageImageResult
    }

    private fun process(page: ReaderPage, imageSource: BufferedSource): ProcessedPageImage {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (isDoublePage && !page.isWide) {
            page.isWide = true
            if (viewer.config.joinDoublePages) {
                viewer.activity.runOnUiThread {
                    viewer.refreshAdapter()
                }
            }
        }

        if (viewer.config.dualPageRotateToFit) {
            return rotateDualPage(imageSource)
        }

        // joinDoublePages wins over dualPageSplit: the halves of a JoinedReaderPage spread are
        // raw ReaderPages that are not adapter items, so splitting them produced a duplicate of
        // the same half (and index -1 lookups in the adapter).
        if (!viewer.config.dualPageSplit || viewer.config.joinDoublePages) {
            return ProcessedPageImage.Encoded(imageSource)
        }

        if (page is InsertPage) {
            return splitInHalf(imageSource)
        }

        if (!isDoublePage) {
            return ProcessedPageImage.Encoded(imageSource)
        }

        onPageSplit(page)

        return splitInHalf(imageSource)
    }

    private fun rotateDualPage(imageSource: BufferedSource): ProcessedPageImage {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (!isDoublePage) {
            return ProcessedPageImage.Encoded(imageSource)
        }
        val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
        // peek() keeps the source intact so the stream variant remains a viable fallback.
        return ImageUtil.rotateImageBitmap(imageSource.peek(), rotation)
            ?.let { ProcessedPageImage.Decoded(it) }
            ?: ProcessedPageImage.Encoded(ImageUtil.rotateImage(imageSource, rotation))
    }

    private fun splitInHalf(imageSource: BufferedSource): ProcessedPageImage {
        var side = when {
            viewer is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.RIGHT
            viewer !is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.LEFT
            viewer is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.LEFT
            viewer !is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.RIGHT
            else -> error("We should choose a side!")
        }

        if (viewer.config.dualPageInvert) {
            side = when (side) {
                ImageUtil.Side.RIGHT -> ImageUtil.Side.LEFT
                ImageUtil.Side.LEFT -> ImageUtil.Side.RIGHT
            }
        }

        // Region-decode only the kept half and skip the JPEG q=100 re-encode; fall back to the
        // stream variant on the rare decoder failure (peek() left the source unconsumed).
        return ImageUtil.splitInHalfBitmap(imageSource.peek(), side)
            ?.let { ProcessedPageImage.Decoded(it) }
            ?: ProcessedPageImage.Encoded(ImageUtil.splitInHalf(imageSource, side))
    }

    private fun onPageSplit(page: ReaderPage) {
        val newPage = InsertPage(page)
        viewer.onPageSplit(page, newPage)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressIndicator?.hide()
        showErrorLayout()
    }

    /**
     * Decode/render failure while the page status is READY: move the status to ERROR so the
     * Retry button can re-queue it through the page loader. Showing the error layout alone left
     * the status at READY and retryPage() became a no-op (the loader skips non-QUEUE pages).
     */
    private fun markDecodeError() {
        if (page.status == Page.State.READY) {
            page.status = Page.State.ERROR
        }
        setError()
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        progressIndicator?.hide()
    }

    /**
     * Called when an image fails to decode.
     */
    override fun onImageLoadError() {
        super.onImageLoadError()
        markDecodeError()
    }

    /**
     * Called when an image is zoomed in/out.
     */
    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        viewer.activity.hideMenu()
    }

    private fun showErrorLayout(): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true)
            errorLayout?.actionRetry?.viewer = viewer
            errorLayout?.actionRetry?.setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }
        }

        val imageUrl = page.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null) {
            if (imageUrl.startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.viewer = viewer
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val intent = WebViewActivity.newIntent(context, imageUrl)
                    context.startActivity(intent)
                }
            }
        }

        errorLayout?.root?.isVisible = true
        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.root?.isVisible = false
        errorLayout = null
    }
}
