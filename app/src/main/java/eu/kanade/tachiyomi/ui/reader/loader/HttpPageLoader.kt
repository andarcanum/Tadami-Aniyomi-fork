package eu.kanade.tachiyomi.ui.reader.loader

import aniyomi.util.DataSaver
import aniyomi.util.DataSaver.Companion.getImage
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.manga.toDomainChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.ReaderPreloadManager
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Loader used to load chapters from an online source.
 */
internal class HttpPageLoader(
    private val chapter: ReaderChapter,
    private val source: HttpSource,
    private val chapterCache: ChapterCache = Injekt.get(),
    // SY -->
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // SY <--
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    private val loadPageTimeoutMs: Long = DEFAULT_LOAD_PAGE_TIMEOUT_MS,
    private val downloadStallTimeoutMs: Long = DEFAULT_DOWNLOAD_STALL_TIMEOUT_MS,
    private val stallRetryThresholdMs: Long = DEFAULT_STALL_RETRY_THRESHOLD_MS,
) : PageLoader() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A queue used to manage requests one by one while allowing priorities.
     */
    private val queue = PriorityBlockingQueue<PriorityPage>()

    /**
     * Pages that are already queued for this chapter.
     *
     * A single visible page can ask to warm several neighbours, and neighbouring holders can ask
     * for the same pages again before the worker has a chance to update their state. Keeping this
     * map bounded to queued items prevents duplicate cache checks and network attempts while still
     * allowing a visible page request to raise the priority of a preloaded page.
     */
    private val queuedPages = mutableMapOf<ReaderPage, PriorityPage>()

    /**
     * Active per-page download jobs keyed by page, paired with their last observed activity
     * timestamp so stalled downloads can be detected and cancelled on retry without killing
     * the shared worker pool.
     */
    private val inFlightEntries = ConcurrentHashMap<ReaderPage, InFlightEntry>()

    private val preloadPagesBefore: Int
        get() = readerPreferences.preloadPagesBefore().get()

    private val preloadPagesAfter: Int
        get() = ReaderPreloadManager.dynamicPreloadPagesAfter

    // SY -->
    private val dataSaver = DataSaver(source, sourcePreferences)
    // SY <--

    init {
        // Bounded parallelism: a single sequential worker let one slow or hung image block the
        // whole chapter queue - the visible page could not overtake in-flight preloads, and a
        // stalled connection froze everything until the (30h) call timeout. Workers share the
        // priority queue, so ordering semantics are unchanged; 3 keeps connection pressure
        // modest while a stalled worker no longer starves the rest.
        repeat(PAGE_DOWNLOAD_WORKERS) {
            scope.launchIO {
                while (isActive) {
                    val queuedPage = runInterruptible { queue.take() }
                    val page = queuedPage.page
                    synchronized(queuedPages) {
                        queuedPage.dequeued = true
                    }
                    var entry: InFlightEntry? = null
                    try {
                        if (page.status == Page.State.QUEUE) {
                            val activeEntry = InFlightEntry(
                                lastActivityMs = System.currentTimeMillis(),
                            )
                            val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                                internalLoadPage(page, activeEntry)
                            }
                            activeEntry.job = job
                            entry = activeEntry
                            inFlightEntries[page] = activeEntry
                            job.join()
                        }
                    } finally {
                        entry?.let { inFlightEntries.remove(page, it) }
                        removeQueuedPage(queuedPage)
                    }
                }
            }
        }
    }

    override var isLocal: Boolean = false

    /**
     * Returns the page list for a chapter. It tries to return the page list from the local cache,
     * otherwise fallbacks to network.
     */
    override suspend fun getPages(): List<ReaderPage> {
        val domainChapter = chapter.chapter.toDomainChapter()!!
        val cachedPages = chapterCache.getPageListFromCache(domainChapter)
        // SY -->
        val pages = if (cachedPages.isNotEmpty() && cachedPages.none { hasSignedQueryToken(it.imageUrl) }) {
            cachedPages
        } else {
            source.getPageList(chapter.chapter).also { fetched ->
                runCatching {
                    val pagesToSave = fetched.map { Page(it.index, it.url, it.imageUrl) }
                    chapterCache.putPageListToCache(domainChapter, pagesToSave)
                }
            }
        }
        // SY <--
        return pages.mapIndexed { index, page ->
            // Don't trust sources and use our own indexing
            ReaderPage(index, page.url, page.imageUrl)
        }
    }

    /**
     * Loads a page through the queue. Handles re-enqueueing pages if they were evicted from the cache.
     */
    override suspend fun loadPage(page: ReaderPage) = withIOContext {
        val imageUrl = page.imageUrl

        // Check if the image has been deleted
        if (page.status == Page.State.READY &&
            imageUrl != null &&
            !chapterCache.isImageInCache(
                imageUrl,
            )
        ) {
            page.status = Page.State.QUEUE
        }

        // Automatically retry failed pages when subscribed to this page; evict any broken
        // cached copy so decode failures fetch a clean image instead of looping.
        if (page.status == Page.State.ERROR) {
            clearPageCacheAndMetadata(page)
            page.status = Page.State.QUEUE
        }

        val offeredPages = mutableListOf<PriorityPage>()
        if (page.status == Page.State.QUEUE) {
            offerPage(page, 1)?.let { offeredPages += it }
        }
        offeredPages += preloadAroundPage(page)

        suspendCancellableCoroutine<Nothing> { continuation ->
            continuation.invokeOnCancellation {
                offeredPages.forEach {
                    if (it.page.status == Page.State.QUEUE) {
                        cancelQueuedPage(it)
                    }
                }
            }
        }
    }

    /**
     * Retries a page. This method is only called from user interaction on the viewer.
     */
    override fun retryPage(page: ReaderPage) {
        if (isRecycled) return

        // READY is included on purpose: a cached file evicted under a READY page, or a decode
        // failure the holder surfaced while the status stayed READY, both left the worker
        // skipping the re-offered page (it only processes QUEUE) - Retry was a dead button.
        // Orphaned QUEUE pages (dropped from the queue while a holder still waited) and
        // stalled LOAD_PAGE/DOWNLOAD_IMAGE requests are also recoverable.
        val shouldRetry = when (page.status) {
            Page.State.ERROR, Page.State.READY -> true
            Page.State.QUEUE -> {
                val isQueued = synchronized(queuedPages) { queuedPages.containsKey(page) }
                !isQueued && !inFlightEntries.containsKey(page)
            }
            Page.State.LOAD_PAGE, Page.State.DOWNLOAD_IMAGE -> {
                val active = inFlightEntries[page]
                if (active == null) {
                    true
                } else {
                    val idleMs = System.currentTimeMillis() - active.lastActivityMs
                    if (idleMs >= stallRetryThresholdMs) {
                        inFlightEntries.remove(page, active)
                        active.job?.cancel(PageRetryCancellationException())
                        synchronized(queuedPages) {
                            if (queuedPages[page]?.dequeued == true) {
                                queuedPages.remove(page)
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
            }
        }
        if (!shouldRetry) return

        clearPageCacheAndMetadata(page)
        page.status = Page.State.QUEUE
        offerPage(page, 2)
    }

    private fun clearPageCacheAndMetadata(page: ReaderPage) {
        page.imageUrl?.let { chapterCache.removeImageFromCache(it) }
        page.imageDimensions = null
        page.smartFitBounds = null
        page.isAnimatedImage = false
        page.progress = 0
    }

    override fun recycle() {
        super.recycle()
        scope.cancel()
        inFlightEntries.clear()
        queue.clear()
        synchronized(queuedPages) {
            queuedPages.clear()
        }

        // Cache current page list progress for online chapters to allow a faster reopen
        chapter.pages?.let { pages ->
            launchIO {
                try {
                    // Convert to pages without reader information
                    val pagesToSave = pages.map { Page(it.index, it.url, it.imageUrl) }
                    chapterCache.putPageListToCache(
                        chapter.chapter.toDomainChapter()!!,
                        pagesToSave,
                    )
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        throw e
                    }
                }
            }
        }
    }

    /**
     * Preloads surrounding pages with a lower priority so both paged readers and long strip
     * readers have nearby images ready before the user reaches them.
     *
     * @return a list of [PriorityPage] that were added to the [queue]
     */
    private fun preloadAroundPage(currentPage: ReaderPage): List<PriorityPage> {
        val pages = currentPage.chapter.pages ?: return emptyList()

        return ImagePreloadPlanner.pagesAround(
            currentPage = currentPage,
            pages = pages,
            pagesBefore = preloadPagesBefore,
            pagesAfter = preloadPagesAfter,
        )
            .mapNotNull {
                if (it.status == Page.State.QUEUE) {
                    offerPage(it, 0)
                } else {
                    null
                }
            }
    }

    private fun offerPage(page: ReaderPage, priority: Int): PriorityPage? {
        return synchronized(queuedPages) {
            val existing = queuedPages[page]
            if (existing != null) {
                if (existing.dequeued) {
                    return@synchronized null
                }
                if (existing.priority >= priority) {
                    existing.subscribers++
                    return@synchronized existing
                }
                if (!queue.remove(existing)) {
                    return@synchronized null
                }
            }

            PriorityPage(page, priority).also {
                queuedPages[page] = it
                queue.offer(it)
            }
        }
    }

    private fun cancelQueuedPage(priorityPage: PriorityPage) {
        synchronized(queuedPages) {
            if (queuedPages[priorityPage.page] === priorityPage && !priorityPage.dequeued) {
                priorityPage.subscribers--
                if (priorityPage.subscribers <= 0 && queue.remove(priorityPage)) {
                    queuedPages.remove(priorityPage.page)
                }
            }
        }
    }

    private fun removeQueuedPage(priorityPage: PriorityPage) {
        synchronized(queuedPages) {
            if (queuedPages[priorityPage.page] === priorityPage) {
                queuedPages.remove(priorityPage.page)
            }
        }
    }

    /**
     * Loads the page, retrieving the image URL and downloading the image if necessary.
     * Downloaded images are stored in the chapter cache.
     *
     * @param page the page whose source image has to be downloaded.
     */
    private suspend fun internalLoadPage(page: ReaderPage, entry: InFlightEntry) {
        try {
            if (page.imageUrl.isNullOrEmpty()) {
                page.status = Page.State.LOAD_PAGE
                entry.lastActivityMs = System.currentTimeMillis()
                page.imageUrl = withTimeout(loadPageTimeoutMs) {
                    source.getImageUrl(page)
                }
                entry.lastActivityMs = System.currentTimeMillis()
            }
            val imageUrl = page.imageUrl!!

            if (!chapterCache.isImageInCache(imageUrl)) {
                page.status = Page.State.DOWNLOAD_IMAGE
                page.progress = 0
                entry.lastActivityMs = System.currentTimeMillis()
                downloadImageWithStallWatchdog(page, imageUrl, entry)
            }

            // Sniff the pixel size while the file is still hot in the page cache: preloaded
            // pages then bind with a correct placeholder height and do not relayout on decode.
            // Animated pages are skipped: their height comes from the decoded drawable, and a
            // pre-set size would route them into the non-animated image path.
            var isAnimated = false
            if (page.imageDimensions == null) {
                page.imageDimensions = runCatching {
                    chapterCache.openImageStream(imageUrl).use { stream ->
                        val metadata = ImageUtil.sniffImageMetadata(stream)
                        isAnimated = metadata.isAnimated
                        if (isAnimated) {
                            page.isAnimatedImage = true
                            null
                        } else {
                            metadata.dimensions
                        }
                    }
                }.getOrNull()
            }

            if (!isAnimated && !page.isAnimatedImage && page.smartFitBounds == null &&
                readerPreferences.webtoonSmartFit().get()
            ) {
                val cropBorders = readerPreferences.cropBordersWebtoon().get()
                runCatching {
                    chapterCache.openImageStream(imageUrl).use { stream ->
                        eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonBorderDetector.detectContentBounds(
                            stream,
                            cropBorders,
                        )
                    }
                }.getOrNull()?.takeIf { !it.isEmpty }?.let { bounds ->
                    page.smartFitBounds = cropBorders to bounds
                }
            }

            page.stream = { chapterCache.openImageStream(imageUrl) }
            page.status = Page.State.READY
        } catch (_: PageRetryCancellationException) {
            // Superseded by an explicit retryPage() call: keep the freshly queued state
            // intact and let the worker continue serving the queue.
        } catch (_: PageStallTimeoutException) {
            page.status = Page.State.ERROR
        } catch (_: TimeoutCancellationException) {
            page.status = Page.State.ERROR
        } catch (e: CancellationException) {
            if (!isRecycled && scope.isActive) {
                page.status = Page.State.ERROR
            }
            throw e
        } catch (_: Throwable) {
            page.status = Page.State.ERROR
        }
    }

    private suspend fun downloadImageWithStallWatchdog(
        page: ReaderPage,
        imageUrl: String,
        entry: InFlightEntry,
    ) {
        var stallException: PageStallTimeoutException? = null
        coroutineScope {
            val downloadJob = launch {
                val imageResponse = source.getImage(page, dataSaver)
                entry.lastActivityMs = System.currentTimeMillis()
                chapterCache.putImageToCache(imageUrl, imageResponse)
                entry.lastActivityMs = System.currentTimeMillis()
            }
            val progressObserver = launch {
                page.progressFlow.collect {
                    entry.lastActivityMs = System.currentTimeMillis()
                }
            }
            val pollIntervalMs = (downloadStallTimeoutMs / 4L).coerceIn(10L, 2_000L)
            val watchdog = launch {
                while (downloadJob.isActive) {
                    delay(pollIntervalMs)
                    val idleMs = System.currentTimeMillis() - entry.lastActivityMs
                    if (idleMs >= downloadStallTimeoutMs) {
                        val timeout = PageStallTimeoutException(
                            "Image download stalled for ${idleMs}ms: $imageUrl",
                        )
                        stallException = timeout
                        downloadJob.cancel(timeout)
                        break
                    }
                }
            }
            try {
                downloadJob.join()
            } finally {
                watchdog.cancel()
                progressObserver.cancel()
            }
        }
        stallException?.let { throw it }
    }
}

/**
 * Data class used to keep ordering of pages in order to maintain priority.
 */
private class InFlightEntry(
    @Volatile var job: Job? = null,
    @Volatile var lastActivityMs: Long,
)

private class PageRetryCancellationException : CancellationException("Page retry requested")

private class PageStallTimeoutException(message: String) : CancellationException(message)

private class PriorityPage(
    val page: ReaderPage,
    val priority: Int,
    var subscribers: Int = 1,
    var dequeued: Boolean = false,
) : Comparable<PriorityPage> {
    companion object {
        private val idGenerator = AtomicInteger()
    }

    private val identifier = idGenerator.incrementAndGet()

    override fun compareTo(other: PriorityPage): Int {
        val p = other.priority.compareTo(priority)
        return if (p != 0) p else identifier.compareTo(other.identifier)
    }
}

private val SIGNED_URL_PARAM_REGEX =
    Regex("(^|&)(t|exp|expires|token|sig|signature|hdnea|x-amz-[a-z0-9-]+)=", RegexOption.IGNORE_CASE)

internal fun hasSignedQueryToken(url: String?): Boolean {
    val query = url?.substringAfter('?', "") ?: return false
    if (query.isEmpty()) return false
    return SIGNED_URL_PARAM_REGEX.containsMatchIn(query)
}

/**
 * Concurrent page-download workers per chapter loader. 3 balances visible-page latency
 * (a stalled worker no longer starves the queue) against connection pressure on sources.
 */
private const val PAGE_DOWNLOAD_WORKERS = 3
private const val DEFAULT_LOAD_PAGE_TIMEOUT_MS = 30_000L
private const val DEFAULT_DOWNLOAD_STALL_TIMEOUT_MS = 45_000L
private const val DEFAULT_STALL_RETRY_THRESHOLD_MS = 10_000L
