package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.tadami.aurora.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ProcessedPageImage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPreviewCache
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.panel.GuidedPanelController
import eu.kanade.tachiyomi.ui.reader.viewer.panel.MangaPanelCache
import eu.kanade.tachiyomi.ui.reader.viewer.panel.MangaPanelDetector
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelRect
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.image.NativeImageDecoder
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import java.io.FileNotFoundException
import java.io.InputStream

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

    private var cacheEvictionRetried = false
    private var tileErrorRetried = false

    private var panelDetectJob: Job? = null
    private var detectedPanels: List<PanelRect> = emptyList()
    private val guidedPanelController = GuidedPanelController()
    private var pendingGuidedEntryFromEnd: Boolean? = null
    private var viewportVisible: Boolean = true

    init {
        loadJob = scope.launch { loadPageAndProcessStatus() }
        onHdrChanged = { isHdr ->
            page.isHdr = isHdr
            viewer.updateWindowHdrState()
        }
    }

    fun setViewportVisible(visible: Boolean) {
        viewportVisible = visible
        progressIndicator?.setInViewport(visible)
        setInViewport(visible)
    }

    override fun recycle() {
        loadJob?.cancel()
        loadJob = null
        cacheEvictionRetried = false
        tileErrorRetried = false
        panelDetectJob?.cancel()
        panelDetectJob = null
        detectedPanels = emptyList()
        guidedPanelController.reset()
        pendingGuidedEntryFromEnd = null
        viewportVisible = true
        super.recycle()
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (loadJob == null) {
            loadJob = scope.launch { loadPageAndProcessStatus() }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        loadJob = null
        panelDetectJob?.cancel()
        panelDetectJob = null
    }

    private fun initProgressIndicator() {
        if (progressIndicator == null) {
            progressIndicator = ReaderProgressIndicator(context).apply {
                setInViewport(viewportVisible)
            }
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

        val streamFn = page.stream ?: run {
            markDecodeError()
            return
        }

        try {
            val prepared = withIOContext {
                when (val processed = process(item, streamFn)) {
                    is ProcessedPageImage.Streamed -> {
                        val unmodified = !viewer.config.dualPageSplit && !viewer.config.dualPageRotateToFit
                        val reusable = page.takeIf { unmodified }?.imageDimensions
                        val meta = if (reusable != null) {
                            ImageUtil.ImageMetadata(isAnimated = false, dimensions = reusable)
                        } else {
                            streamFn().use { ImageUtil.sniffImageMetadata(it) }
                        }
                        val isAnimated = meta.isAnimated
                        if (isAnimated) {
                            val animatedSource = streamFn().use { Buffer().readFrom(it) }
                            PageImageResult.Encoded(
                                source = animatedSource,
                                isAnimated = true,
                                background = null,
                                dimensions = null,
                            )
                        } else {
                            val background = if (viewer.config.automaticBackground) {
                                streamFn().use { ImageUtil.chooseBackground(context, it) }
                            } else {
                                null
                            }
                            val dims = meta.dimensions
                            if (reusable == null && dims != null && unmodified) {
                                page.imageDimensions = dims
                            }
                            PageImageResult.Streamed(
                                streamFn = processed.streamFn,
                                isAnimated = false,
                                background = background,
                                dimensions = dims,
                            )
                        }
                    }
                    is ProcessedPageImage.Decoded -> PageImageResult.Decoded(processed.bitmap)
                    is ProcessedPageImage.Encoded -> {
                        val source = processed.source
                        val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                        val background = if (!isAnimated && viewer.config.automaticBackground) {
                            ImageUtil.chooseBackground(context, source.peek().inputStream())
                        } else {
                            null
                        }
                        val dims = if (isAnimated) null else ImageUtil.getImageDimensions(source)
                        PageImageResult.Encoded(
                            source = source,
                            isAnimated = isAnimated,
                            background = background,
                            dimensions = dims,
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
                    lockedZoomRatio = if (viewer.config.zoomLockEnabled) viewer.lockedZoomRatio else null,
                    hardwareTilesEnabled = viewer.config.hardwareTilesEnabled,
                    doubleTapZoomScale = viewer.config.doubleTapZoomScale,
                    previewKey = if (prepared is PageImageResult.Streamed && !prepared.isAnimated &&
                        page !is InsertPage
                    ) {
                        ReaderPreviewCache.key(
                            chapterId = page.chapter.chapter.id,
                            pageIndex = page.index,
                            cropBorders = viewer.config.imageCropBorders,
                        )
                    } else {
                        null
                    },
                )
                when (prepared) {
                    is PageImageResult.Streamed -> {
                        prepared.dimensions?.let {
                            viewer.activity.viewModel.onReaderPageImageDimensionsAvailable(
                                page = page,
                                width = it.width,
                                height = it.height,
                            )
                        }
                        setImage(prepared.streamFn, prepared.isAnimated, config)
                        if (!prepared.isAnimated) {
                            pageBackground = prepared.background
                        }
                    }
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
            if (e is CancellationException) {
                throw e
            }
            logcat(LogPriority.ERROR, e)
            withUIContext {
                if (e is FileNotFoundException &&
                    !cacheEvictionRetried &&
                    page.status == Page.State.READY
                ) {
                    cacheEvictionRetried = true
                    page.chapter.pageLoader?.retryPage(page)
                } else {
                    markDecodeError()
                }
            }
        }
    }

    private sealed interface PageImageResult {
        data class Streamed(
            val streamFn: () -> InputStream,
            val isAnimated: Boolean,
            val background: android.graphics.drawable.Drawable?,
            val dimensions: ImageUtil.ImageDimensions?,
        ) : PageImageResult

        data class Encoded(
            val source: BufferedSource,
            val isAnimated: Boolean,
            val background: android.graphics.drawable.Drawable?,
            val dimensions: ImageUtil.ImageDimensions?,
        ) : PageImageResult

        data class Decoded(val bitmap: Bitmap) : PageImageResult
    }

    private fun process(page: ReaderPage, streamFn: () -> InputStream): ProcessedPageImage {
        val dims = page.imageDimensions ?: streamFn().use { stream ->
            val meta = ImageUtil.sniffImageMetadata(stream)
            if (meta.isAnimated) {
                page.isAnimatedImage = true
                null
            } else {
                meta.dimensions?.also { page.imageDimensions = it }
            }
        }
        val isDoublePage = dims != null && dims.width > dims.height
        if (isDoublePage && !page.isWide) {
            page.isWide = true
            if (viewer.config.joinDoublePages) {
                viewer.activity.runOnUiThread {
                    viewer.refreshAdapter()
                }
            }
        }

        if (viewer.config.dualPageRotateToFit && isDoublePage) {
            val imageSource = streamFn().use { Buffer().readFrom(it) }
            return rotateDualPage(imageSource)
        }

        if (!viewer.config.dualPageSplit || viewer.config.joinDoublePages) {
            return ProcessedPageImage.Streamed(streamFn)
        }

        if (page is InsertPage) {
            val imageSource = streamFn().use { Buffer().readFrom(it) }
            return splitInHalf(imageSource)
        }

        if (!isDoublePage) {
            return ProcessedPageImage.Streamed(streamFn)
        }

        onPageSplit(page)

        val imageSource = streamFn().use { Buffer().readFrom(it) }
        return splitInHalf(imageSource)
    }

    private fun rotateDualPage(imageSource: BufferedSource): ProcessedPageImage {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (!isDoublePage) {
            return ProcessedPageImage.Encoded(imageSource)
        }
        val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
        // peek() keeps the source intact so the stream variant remains a viable fallback.
        val rotatedBitmap = ImageUtil.rotateImageBitmap(imageSource.peek(), rotation)
        if (rotatedBitmap != null) {
            if (maxOf(rotatedBitmap.width, rotatedBitmap.height) <= ImageUtil.hardwareBitmapThreshold) {
                return ProcessedPageImage.Decoded(rotatedBitmap)
            }
            rotatedBitmap.recycle()
        }
        return ProcessedPageImage.Encoded(ImageUtil.rotateImage(imageSource, rotation))
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
        // stream variant on the rare decoder failure or oversized texture (peek() left the
        // source unconsumed).
        val splitBitmap = ImageUtil.splitInHalfBitmap(imageSource.peek(), side, viewer.config.dualPageSmartGutter)
        if (splitBitmap != null) {
            if (maxOf(splitBitmap.width, splitBitmap.height) <= ImageUtil.hardwareBitmapThreshold) {
                return ProcessedPageImage.Decoded(splitBitmap)
            }
            splitBitmap.recycle()
        }
        return ProcessedPageImage.Encoded(ImageUtil.splitInHalf(imageSource, side))
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
        if (page.status == Page.State.ERROR) {
            setError()
        }
    }

    override fun onPageSelected(forward: Boolean) {
        super.onPageSelected(forward)
        if (!viewer.config.guidedPanelMode || page is InsertPage || page.isAnimatedImage) {
            guidedPanelController.reset()
            pendingGuidedEntryFromEnd = null
            return
        }
        val fromEnd = !forward
        if (detectedPanels.size >= 2) {
            pendingGuidedEntryFromEnd = null
            guidedPanelController.enterPage(detectedPanels, fromEnd = fromEnd)?.let { focus ->
                animateToPanel(focus.panel)
            }
        } else {
            pendingGuidedEntryFromEnd = fromEnd
            ensurePanelsDetected()
        }
    }

    /**
     * Steps to the next or previous detected panel on this page.
     * Returns true if the viewport animated to another panel, or false when the pager should flip
     * to the adjacent page.
     */
    internal fun stepGuidedPanel(forward: Boolean): Boolean {
        if (!viewer.config.guidedPanelMode || detectedPanels.size < 2) return false
        val result = if (forward) {
            guidedPanelController.stepForward(detectedPanels)
        } else {
            guidedPanelController.stepBackward(detectedPanels)
        }
        return when (result) {
            is GuidedPanelController.StepResult.Focus -> animateToPanel(result.panel)
            GuidedPanelController.StepResult.NextPage,
            GuidedPanelController.StepResult.PrevPage,
            -> false
        }
    }

    private fun ensurePanelsDetected() {
        if (!viewer.config.guidedPanelMode || page is InsertPage || page.isAnimatedImage) return
        val isRtl = viewer is R2LPagerViewer
        val cropBorders = viewer.config.imageCropBorders
        val cacheKey = MangaPanelCache.key(
            chapterId = page.chapter.chapter.id,
            pageIndex = page.index,
            isRtl = isRtl,
            cropBorders = cropBorders,
        )
        if (cacheKey != null) {
            val cached = MangaPanelCache.get(cacheKey)
            if (cached != null) {
                applyDetectedPanels(cached)
                return
            }
        }
        if (panelDetectJob?.isActive == true) return
        val previewKey = ReaderPreviewCache.key(
            chapterId = page.chapter.chapter.id,
            pageIndex = page.index,
            cropBorders = cropBorders,
        )
        val cachedPreview = previewKey?.let(ReaderPreviewCache::get)?.bitmap?.takeIf { !it.isRecycled }
        val streamFn = page.stream
        if (cachedPreview == null && streamFn == null) return

        panelDetectJob = scope.launch(Dispatchers.Default) {
            val panels = try {
                val preview =
                    cachedPreview ?: previewKey?.let(ReaderPreviewCache::get)?.bitmap?.takeIf { !it.isRecycled }
                if (preview != null) {
                    MangaPanelDetector.detectFromBitmap(preview, isRtl = isRtl)
                } else if (streamFn != null) {
                    withIOContext {
                        decodePanelAnalysisBitmap(streamFn, cropBorders)?.let { bmp ->
                            try {
                                MangaPanelDetector.detectFromBitmap(bmp, isRtl = isRtl)
                            } finally {
                                if (!bmp.isRecycled) bmp.recycle()
                            }
                        }
                    }
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (panels != null) {
                if (cacheKey != null) {
                    MangaPanelCache.put(cacheKey, panels)
                }
                withUIContext {
                    applyDetectedPanels(panels)
                }
            }
        }
    }

    private fun applyDetectedPanels(panels: List<PanelRect>) {
        detectedPanels = panels
        val fromEnd = pendingGuidedEntryFromEnd
        if (fromEnd != null && viewer.config.guidedPanelMode && panels.size >= 2) {
            pendingGuidedEntryFromEnd = null
            guidedPanelController.enterPage(panels, fromEnd = fromEnd)?.let { focus ->
                animateToPanel(focus.panel)
            }
        }
    }

    private fun decodePanelAnalysisBitmap(
        streamFn: () -> InputStream,
        cropBorders: Boolean,
    ): Bitmap? {
        return try {
            NativeImageDecoder.open(streamFn(), cropBorders).use { decoder ->
                val w = decoder.width
                val h = decoder.height
                if (w <= 0 || h <= 0) return null
                var sampleSize = 1
                while ((w / (sampleSize * 2)) >= 280) {
                    sampleSize *= 2
                }
                decoder.decode(region = null, sampleSize = sampleSize)
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        cacheEvictionRetried = false
        progressIndicator?.hide()
        if (viewer.config.guidedPanelMode) {
            ensurePanelsDetected()
        }
    }

    /**
     * Called when an image fails to decode.
     */
    override fun onImageLoadError() {
        super.onImageLoadError()
        markDecodeError()
    }

    override fun onTileLoadError(e: Exception) {
        super.onTileLoadError(e)
        if (!tileErrorRetried && page.status == Page.State.READY) {
            tileErrorRetried = true
            page.chapter.pageLoader?.retryPage(page)
        }
    }

    /**
     * Called when an image is zoomed in/out.
     */
    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        if (viewer.config.zoomLockEnabled) {
            val ssiv = getImageView() as? SubsamplingScaleImageView
            if (ssiv != null && ssiv.isReady && ssiv.minScale > 0f) {
                val ratio = newScale / ssiv.minScale
                viewer.lockedZoomRatio = if (ratio > 1.01f) ratio else null
            }
        }
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
