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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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

    private val preloadPagesBefore: Int
        get() = readerPreferences.preloadPagesBefore().get()

    private val preloadPagesAfter: Int
        get() = ReaderPreloadManager.dynamicPreloadPagesAfter

    // SY -->
    private val dataSaver = DataSaver(source, sourcePreferences)
    // SY <--

    init {
        scope.launchIO {
            while (true) {
                val queuedPage = runInterruptible { queue.take() }
                try {
                    if (queuedPage.page.status == Page.State.QUEUE) {
                        internalLoadPage(queuedPage.page)
                    }
                } finally {
                    removeQueuedPage(queuedPage)
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
        val pages = if (cachedPages.isNotEmpty()) {
            if (cachedPages.any { it.imageUrl?.contains("?t=") == true }) {
                // Signed URLs may have stale HMAC tokens; re-fetch from server
                source.getPageList(chapter.chapter)
            } else {
                cachedPages
            }
        } else {
            source.getPageList(chapter.chapter)
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

        // Automatically retry failed pages when subscribed to this page
        if (page.status == Page.State.ERROR) {
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
        if (page.status == Page.State.ERROR) {
            page.status = Page.State.QUEUE
        }
        offerPage(page, 2)
    }

    override fun recycle() {
        super.recycle()
        scope.cancel()
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
                if (existing.priority >= priority) {
                    return@synchronized null
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
            if (queuedPages[priorityPage.page] == priorityPage && queue.remove(priorityPage)) {
                queuedPages.remove(priorityPage.page)
            }
        }
    }

    private fun removeQueuedPage(priorityPage: PriorityPage) {
        synchronized(queuedPages) {
            if (queuedPages[priorityPage.page] == priorityPage) {
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
    private suspend fun internalLoadPage(page: ReaderPage) {
        try {
            if (page.imageUrl.isNullOrEmpty()) {
                page.status = Page.State.LOAD_PAGE
                page.imageUrl = source.getImageUrl(page)
            }
            val imageUrl = page.imageUrl!!

            if (!chapterCache.isImageInCache(imageUrl)) {
                page.status = Page.State.DOWNLOAD_IMAGE
                val imageResponse = source.getImage(page, dataSaver)
                chapterCache.putImageToCache(imageUrl, imageResponse)
            }

            page.stream = { chapterCache.getImageFile(imageUrl).inputStream() }
            page.status = Page.State.READY
        } catch (e: Throwable) {
            page.status = Page.State.ERROR
            if (e is CancellationException) {
                throw e
            }
        }
    }
}

/**
 * Data class used to keep ordering of pages in order to maintain priority.
 */
private class PriorityPage(
    val page: ReaderPage,
    val priority: Int,
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
