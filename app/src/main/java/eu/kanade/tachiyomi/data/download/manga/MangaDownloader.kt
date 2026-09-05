package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import aniyomi.util.DataSaver
import aniyomi.util.DataSaver.Companion.getImage
import com.hippo.unifile.UniFile
import eu.kanade.domain.entries.manga.model.getComicInfo
import eu.kanade.domain.items.chapter.model.toSChapter
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadNetworkStatus
import eu.kanade.tachiyomi.data.download.engine.DownloadCompletionTracker
import eu.kanade.tachiyomi.data.download.engine.DownloadSection
import eu.kanade.tachiyomi.data.download.engine.DownloadTelemetryEmitter
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.toDownloadNetworkStatus
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil.NOMEDIA_FILE
import eu.kanade.tachiyomi.util.storage.saveTo
import eu.kanade.tachiyomi.util.system.activeNetworkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.core.archive.ZipWriter
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.Response
import okio.Throttler
import okio.buffer
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.renameToOrCopy
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNow
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Locale

/**
 * This class is the one in charge of downloading chapters.
 *
 * Its queue contains the list of chapters to download. In order to download them, the downloader
 * subscription must be running and the list of chapters must be sent to them by [downloadsRelay].
 *
 * The queue manipulation must be done in one thread (currently the main thread) to avoid unexpected
 * behavior, but it's safe to read it from multiple threads.
 */
class MangaDownloader(
    private val context: Context,
    private val provider: MangaDownloadProvider,
    private val cache: MangaDownloadCache,
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val chapterCache: ChapterCache = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val xml: XML = Injekt.get(),
    private val getCategories: GetMangaCategories = Injekt.get(),
    private val getMangaTracks: GetMangaTracks = Injekt.get(),
    private val achievementHandler: AchievementHandler = Injekt.get(),
    // SY -->
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // SY <--
    var telemetryEmitter: DownloadTelemetryEmitter = DownloadTelemetryEmitter.NOOP,
    var completionTracker: DownloadCompletionTracker = DownloadCompletionTracker(),
) {

    /**
     * Store for persisting downloads across restarts.
     */
    private val store = MangaDownloadStore(context)

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<MangaDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    /**
     * Notifier for the downloader state and progress.
     */
    private val notifier by lazy { MangaDownloadNotifier(context) }

    /**
     * The throttler used to control the download speed.
     */
    private val throttler = Throttler()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloaderJob: Job? = null

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    /**
     * Whether the downloader is paused
     */
    @Volatile
    var isPaused: Boolean = false

    init {
        launchNow {
            // restore() blocks on per-item DB lookups (runBlocking inside); keep that off the
            // main thread - the downloader is constructed eagerly at app start.
            val chapters = async(Dispatchers.IO) { store.restore() }
            addAllToQueue(chapters.await())
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    @Synchronized
    fun start(): Boolean {
        // Network gate: startDownloads() and its callers (queue UI, entry/updates screens, the
        // engine facade) kick the downloader in-process, bypassing MangaDownloadJob whose
        // WIFI/CONNECTED constraints are the only network policy on the worker path. With
        // "download only over Wi-Fi" on a metered network the job stays ENQUEUED - so its
        // pauseForNetwork never runs - while an ungated start() consumed mobile data; offline it
        // produced a burst of per-chapter ERRORs. The worker calls start() again once its
        // constraints are met, so gating here loses nothing.
        val networkStatus = context.activeNetworkState()
            .toDownloadNetworkStatus(downloadPreferences.downloadOnlyOverWifi().get())
        if (networkStatus != DownloadNetworkStatus.Available) {
            return false
        }

        clearCompletedDownloads()
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != MangaDownload.State.DOWNLOADED }
        if (pending.isEmpty()) {
            return false
        }
        pending.forEach { if (it.status != MangaDownload.State.QUEUE) it.status = MangaDownload.State.QUEUE }

        isPaused = false

        launchDownloaderJob()

        return true
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == MangaDownload.State.DOWNLOADING }
            .forEach { it.status = MangaDownload.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (isPaused && queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        isPaused = false

        MangaDownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == MangaDownload.State.DOWNLOADING }
            .forEach { it.status = MangaDownload.State.QUEUE }
        isPaused = true
    }

    /**
     * Pauses active downloads while the worker waits for network recovery.
     */
    fun pauseForNetwork(reason: String) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == MangaDownload.State.DOWNLOADING }
            .forEach { it.status = MangaDownload.State.QUEUE }
        notifier.onWarning(reason)
    }

    /**
     * Removes everything from the queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()

        internalClearQueue()
        notifier.dismissProgress()
    }

    fun clearCompletedDownloads() {
        removeFromQueueIf {
            it.status == MangaDownload.State.DOWNLOADED
        }
    }

    /**
     * Prepares the subscriptions to start downloading.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            val activeDownloadsFlow = queueState.transformLatest { queue ->
                while (true) {
                    val activeDownloads = queue.asSequence()
                        .filter {
                            it.status.value <= MangaDownload.State.DOWNLOADING.value
                        } // Ignore completed downloads, leave them in the queue
                        .groupBy { it.source }
                        .toList().take(5) // Concurrently download from 5 different sources
                        .map { (_, downloads) -> downloads.first() }
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break
                    // Suspend until one of the active downloads leaves the runnable states,
                    // so the scheduler can immediately pick the next queued chapter.
                    val activeDownloadsSettledFlow =
                        combine(activeDownloads.map(MangaDownload::statusFlow)) { states ->
                            states.any { it.value > MangaDownload.State.DOWNLOADING.value }
                        }.filter { it }
                    activeDownloadsSettledFlow.first()
                }

                if (areAllDownloadsFinished()) stop()
            }.distinctUntilChanged()

            // Use supervisorScope to cancel child jobs when the downloader job is cancelled
            supervisorScope {
                val downloadJobs = mutableMapOf<MangaDownload, Job>()

                activeDownloadsFlow.collectLatest { activeDownloads ->
                    val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                    downloadJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        downloadJobs.remove(download)
                    }

                    val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                    downloadsToStart.forEach { download ->
                        downloadJobs[download] = launchDownloadJob(download)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchDownloadJob(download: MangaDownload) = launchIO {
        try {
            downloadChapter(download)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            notifier.onError(e.message)
            stop()
        }
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun cancelDownloaderJob() {
        downloaderJob?.cancel()
        downloaderJob = null
    }

    /**
     * Creates a download object for every chapter and adds them to the downloads queue.
     *
     * @param manga the manga of the chapters to download.
     * @param chapters the list of chapters to download.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun queueChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean) {
        if (chapters.isEmpty()) return

        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()
        val chaptersToQueue = chapters.asSequence()
            // C-L: deduplicate the input batch itself - queueState is only updated after the
            // whole list is mapped, so duplicates within one call both passed the enqueue filter.
            .distinctBy { it.id }
            // Filter out those already downloaded.
            .filter { provider.findChapterDir(it.name, it.scanlator, manga.title, manga.id, it.id, source) == null }
            // Add chapters to queue from the start.
            .sortedByDescending { it.sourceOrder }
            // Filter out those already enqueued.
            .filter { chapter -> queueState.value.none { it.chapter.id == chapter.id } }
            // Create a download for each one.
            .map { MangaDownload(source, manga, it) }
            .toList()

        if (chaptersToQueue.isNotEmpty()) {
            addAllToQueue(chaptersToQueue)

            // Start downloader if needed
            if (autoStart && wasEmpty) {
                val queuedDownloads = queueState.value.count { it: MangaDownload -> it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                if (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                ) {
                    notifier.onWarning(
                        context.stringResource(AYMR.strings.download_queue_size_warning),
                        WARNING_NOTIF_TIMEOUT_MS,
                        NotificationHandler.openUrl(context, MangaLibraryUpdateNotifier.HELP_WARNING_URL),
                    )
                }
                MangaDownloadJob.start(context)
            }
        }
    }

    /**
     * Downloads a chapter.
     *
     * @param download the chapter to be downloaded.
     */
    private suspend fun downloadChapter(download: MangaDownload) {
        val mangaDir: UniFile
        val chapterDirname: String
        val tmpDir: UniFile
        try {
            mangaDir = provider.getMangaDir(download.manga.title, download.manga.id, download.source)

            val availSpace = DiskUtil.getAvailableStorageSpace(context, mangaDir)
            if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
                download.status = MangaDownload.State.ERROR
                notifier.onError(
                    context.stringResource(AYMR.strings.download_insufficient_space),
                    download.chapter.name,
                    download.manga.title,
                    download.manga.id,
                )
                return
            }

            chapterDirname = provider.getChapterDirName(
                download.chapter.name,
                download.chapter.scanlator,
                download.chapter.id,
            )
            tmpDir = mangaDir.createDirectory(chapterDirname + TMP_DIR_SUFFIX)!!
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // SAF failures in the preamble (unwritable root, getMangaDir throw, null
            // createDirectory behind the !!) used to escape to the scheduler's catch, which
            // stop()s the ENTIRE queue. Isolate them to this chapter instead.
            logcat(LogPriority.ERROR, error)
            download.status = MangaDownload.State.ERROR
            notifier.onError(error.message, download.chapter.name, download.manga.title, download.manga.id)
            return
        }

        try {
            // If the page list already exists, start from the file
            val pageList = download.pages ?: run {
                // Otherwise, pull page list from network and add them to download object
                val pages = download.source.getPageList(download.chapter.toSChapter())

                if (pages.isEmpty()) {
                    throw Exception(context.stringResource(MR.strings.page_list_empty_error))
                }
                // Don't trust index from source
                val reIndexedPages = pages.mapIndexed { index, page ->
                    Page(
                        index,
                        page.url,
                        page.imageUrl,
                        page.uri,
                    )
                }
                download.pages = reIndexedPages
                reIndexedPages
            }

            val dataSaver = if (sourcePreferences.dataSaverDownloader().get()) {
                DataSaver(download.source, sourcePreferences)
            } else {
                DataSaver.NoOp
            }
            val pageDownloadConcurrency = downloadPreferences.pageDownloadConcurrency().get().coerceIn(1, 10)
            val telemetryLock = Any()
            var chapterDownloadedBytes = 0L
            var lastStatBytes = 0L
            var lastStatTimestampMs = 0L

            // Delete all temporary (unfinished) files
            tmpDir.listFiles()
                ?.filter { it.extension == "tmp" }
                ?.forEach { it.delete() }

            download.status = MangaDownload.State.DOWNLOADING
            download.downloadedBytes = 0L
            download.currentSpeedBytesPerSecond = 0L

            // Start downloading images, consider we can have downloaded images already
            // Concurrently download a configurable number of pages at a time
            pageList.asFlow()
                .flatMapMerge(concurrency = pageDownloadConcurrency) { page ->
                    flow {
                        // Fetch image URL if necessary
                        if (page.imageUrl.isNullOrEmpty()) {
                            page.status = Page.State.LOAD_PAGE
                            try {
                                page.imageUrl = download.source.getImageUrl(page)
                            } catch (e: Throwable) {
                                page.status = Page.State.ERROR
                            }
                        }

                        withIOContext {
                            getOrDownloadImage(
                                page = page,
                                download = download,
                                tmpDir = tmpDir,
                                dataSaver = dataSaver,
                            ) { fileSize ->
                                synchronized(telemetryLock) {
                                    chapterDownloadedBytes += fileSize
                                    download.downloadedBytes = chapterDownloadedBytes
                                    val now = System.currentTimeMillis()
                                    if (lastStatTimestampMs > 0L && chapterDownloadedBytes >= lastStatBytes) {
                                        val elapsedMs = now - lastStatTimestampMs
                                        val deltaBytes = chapterDownloadedBytes - lastStatBytes
                                        if (elapsedMs > 0L && deltaBytes >= 0L) {
                                            download.currentSpeedBytesPerSecond = (deltaBytes * 1000L) / elapsedMs
                                        }
                                    }
                                    lastStatBytes = chapterDownloadedBytes
                                    lastStatTimestampMs = now
                                    telemetryEmitter.record(
                                        section = DownloadSection.MANGA,
                                        downloadKey = download.chapter.id.toString(),
                                        bytesDownloaded = chapterDownloadedBytes,
                                        bytesTotal = 0L,
                                        timestampMs = now,
                                    )
                                }
                            }
                        }
                        emit(page)
                    }.flowOn(Dispatchers.IO)
                }
                .collect {
                    // Do when page is downloaded.
                    notifier.onProgressChange(download)
                }

            // Do after download completes
            if (!isDownloadSuccessful(download, tmpDir)) {
                download.status = MangaDownload.State.ERROR
                return
            }

            createComicInfoFile(
                tmpDir,
                download.manga,
                download.chapter,
                download.source,
            )

            // Only rename the directory if it's downloaded
            if (downloadPreferences.saveChaptersAsCBZ().get()) {
                archiveChapter(mangaDir, chapterDirname, tmpDir)
            } else {
                val chapterDir = tmpDir.renameToOrCopy(chapterDirname)
                DiskUtil.createNoMediaFile(chapterDir, context)
            }
            cache.addChapter(chapterDirname, mangaDir, download.manga)

            download.status = MangaDownload.State.DOWNLOADED
            download.currentSpeedBytesPerSecond = 0L
            removeFromQueue(download)
            completionTracker.recordCompletion(DownloadSection.MANGA)
            achievementHandler.trackFeatureUsed(AchievementEvent.Feature.DOWNLOAD)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // If the page list threw, it will resume here
            logcat(LogPriority.ERROR, error)
            download.status = MangaDownload.State.ERROR
            notifier.onError(error.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    /**
     * Gets the image from the filesystem if it exists or downloads it otherwise.
     *
     * @param page the page to download.
     * @param download the download of the page.
     * @param tmpDir the temporary directory of the download.
     */
    private suspend fun getOrDownloadImage(
        page: Page,
        download: MangaDownload,
        tmpDir: UniFile,
        dataSaver: DataSaver,
        onBytesDownloaded: (Long) -> Unit,
    ) {
        // If the image URL is empty, do nothing
        if (page.imageUrl == null) {
            return
        }

        val digitCount = (download.pages?.size ?: 0).toString().length.coerceAtLeast(3)
        val filename = "%0${digitCount}d".format(Locale.ENGLISH, page.number)
        val tmpFile = tmpDir.findFile("$filename.tmp")

        // Delete temp file if it exists
        tmpFile?.delete()

        // Try to find the image file
        val imageFile = tmpDir.listFiles()?.firstOrNull {
            it.name!!.startsWith("$filename.") ||
                it.name!!.startsWith(
                    "${filename}__001",
                )
        }

        try {
            // If the image is already downloaded, do nothing. Otherwise download from network
            val file = when {
                imageFile != null -> imageFile
                chapterCache.isImageInCache(page.imageUrl!!) -> copyImageFromCache(
                    chapterCache.getImageFile(page.imageUrl!!),
                    tmpDir,
                    filename,
                )
                else -> downloadImage(page, download.source, tmpDir, filename, dataSaver)
            }

            // When the page is ready, set page path, progress (just in case) and status
            splitTallImageIfNeeded(page, tmpDir, digitCount)
            page.uri = file.uri
            page.progress = 100
            page.status = Page.State.READY

            // Emit byte-level telemetry after page download
            val fileSize = file.length()
            if (fileSize > 0L) {
                onBytesDownloaded(fileSize)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            // Mark this page as error and allow to download the remaining
            page.progress = 0
            page.status = Page.State.ERROR
            notifier.onError(e.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    /**
     * Downloads the image from network to a file in tmpDir.
     *
     * @param page the page to download.
     * @param source the source of the page.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private suspend fun downloadImage(
        page: Page,
        source: HttpSource,
        tmpDir: UniFile,
        filename: String,
        dataSaver: DataSaver,
    ): UniFile {
        page.status = Page.State.DOWNLOAD_IMAGE
        page.progress = 0
        return flow {
            val response = source.getImage(page, dataSaver)
            val file = tmpDir.createFile("$filename.tmp")!!
            val completedFile = try {
                throttler.apply {
                    bytesPerSecond(downloadPreferences.downloadSpeedLimit().get().toLong() * 1024)
                }
                val throttledSource = throttler.source(response.body.source()).buffer()
                throttledSource.saveTo(file.openOutputStream())
                throttledSource.close()
                val extension = getImageExtension(response, file)
                file.renameToOrCopy("$filename.$extension")
            } catch (e: Exception) {
                response.close()
                file.delete()
                throw e
            }
            emit(completedFile)
        }
            // Retry 3 times, waiting 2, 4 and 8 seconds between attempts.
            .retryWhen { _, attempt ->
                if (attempt < 3) {
                    delay((2L shl attempt.toInt()) * 1000)
                    true
                } else {
                    false
                }
            }
            .flowOn(Dispatchers.IO)
            .first()
    }

    /**
     * Copies the image from cache to file in tmpDir.
     *
     * @param cacheFile the file from cache.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private fun copyImageFromCache(cacheFile: File, tmpDir: UniFile, filename: String): UniFile {
        val tmpFile = tmpDir.createFile("$filename.tmp")!!
        cacheFile.inputStream().use { input ->
            tmpFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        val extension = ImageUtil.findImageType(cacheFile.inputStream()) ?: return tmpFile
        val imageFile = tmpFile.renameToOrCopy("$filename.${extension.extension}")
        cacheFile.delete()
        return imageFile
    }

    /**
     * Returns the extension of the downloaded image from the network response, or if it's null,
     * analyze the file. If everything fails, assume it's a jpg.
     *
     * @param response the network response of the image.
     * @param file the file where the image is already downloaded.
     */
    private fun getImageExtension(response: Response, file: UniFile): String {
        val mime = response.body.contentType()?.run { if (type == "image") "image/$subtype" else null }
        return ImageUtil.getExtensionFromMimeType(mime) { file.openInputStream() }
    }

    private fun splitTallImageIfNeeded(page: Page, tmpDir: UniFile, digitCount: Int) {
        if (!downloadPreferences.splitTallImages().get()) return

        try {
            // NEW-14: the page prefix MUST use the same digit count as saveTo ("%03d" hardcoded
            // here vs digitCount>=4 for >999-page chapters): the prefix of page 5 ("005") did not
            // match its own file ("0005.jpg") but DID match page 50's ("0050.jpg") - the split
            // ran on a foreign page, produced "005__*" files and desynced isDownloadSuccessful
            // into a permanent chapter ERROR.
            val filenamePrefix = "%0${digitCount}d".format(Locale.ENGLISH, page.number)
            val imageFile = tmpDir.listFiles()?.firstOrNull { it.name.orEmpty().startsWith(filenamePrefix) }
                ?: error(context.stringResource(MR.strings.download_notifier_split_page_not_found, page.number))

            // If the original page was previously split, then skip
            if (imageFile.name.orEmpty().startsWith("${filenamePrefix}__")) return

            ImageUtil.splitTallImage(tmpDir, imageFile, filenamePrefix)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to split downloaded image" }
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param tmpDir the directory where the download is currently stored.
     */
    private fun isDownloadSuccessful(
        download: MangaDownload,
        tmpDir: UniFile,
    ): Boolean {
        // Page list hasn't been initialized
        val downloadPageCount = download.pages?.size ?: return false
        // Ensure that all pages have been downloaded
        if (download.downloadedImages != downloadPageCount) {
            return false
        }
        // Ensure that the chapter folder has all the pages
        val countedFiles = tmpDir.listFiles().orEmpty().filter {
            val fileName = it.name.orEmpty()
            when {
                fileName in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) -> false
                fileName.endsWith(".tmp") -> false
                // Only count the first split page and not the others
                fileName.contains("__") && !fileName.endsWith("__001.jpg") -> false
                else -> true
            }
        }
        if (countedFiles.size != downloadPageCount) {
            return false
        }
        // C-M10: the count is not the content - an HTML error page served with HTTP 200 used to
        // be saved as "001.jpg", pass the name-based count and keep the chapter "downloaded"
        // forever. Validate the magic bytes of every counted file instead.
        return countedFiles.all { file ->
            runCatching {
                file.openInputStream().use { ImageUtil.findImageType(it) != null }
            }.getOrDefault(false)
        }
    }

    /**
     * Archive the chapter pages as a CBZ.
     */
    private fun archiveChapter(
        mangaDir: UniFile,
        dirname: String,
        tmpDir: UniFile,
    ) {
        val zip = mangaDir.createFile("$dirname.cbz$TMP_DIR_SUFFIX")!!
        ZipWriter(context, zip).use { writer ->
            tmpDir.listFiles()?.forEach { file ->
                writer.write(file)
            }
        }
        // C-M2: raw/SAF rename silently REPLACES an existing file (POSIX semantics): a colliding
        // dirname destroyed an already-downloaded chapter. With id-suffixed names (DECISION-6)
        // collisions are practically gone; refuse instead of clobbering if the target exists.
        if (mangaDir.findFile("$dirname.cbz") != null) {
            zip.delete()
            error("Target archive already exists: $dirname.cbz")
        }
        zip.renameToOrCopy("$dirname.cbz")
        tmpDir.delete()
    }

    /**
     * Creates a ComicInfo.xml file inside the given directory.
     */
    private suspend fun createComicInfoFile(
        dir: UniFile,
        manga: Manga,
        chapter: Chapter,
        source: HttpSource,
    ) {
        val categories = getCategories.await(manga.id).map { it.name.trim() }.takeUnless { it.isEmpty() }
        val urls = getMangaTracks.await(manga.id)
            .mapNotNull { track ->
                track.remoteUrl.takeUnless { url -> url.isBlank() }?.trim()
            }
            .plus(source.getChapterUrl(chapter.toSChapter()).trim())
            .distinct()

        val comicInfo = getComicInfo(
            manga,
            chapter,
            urls,
            categories,
            source.name,
        )

        // Remove the old file
        dir.findFile(COMIC_INFO_FILE)?.delete()
        dir.createFile(COMIC_INFO_FILE)!!.openOutputStream().use {
            val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
            it.write(comicInfoString.toByteArray())
        }
    }

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= MangaDownload.State.DOWNLOADING.value }
    }

    private fun addAllToQueue(downloads: List<MangaDownload>) {
        _queueState.update {
            downloads.forEach { download ->
                download.status = MangaDownload.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    private fun removeFromQueue(download: MangaDownload) {
        _queueState.update {
            store.remove(download)
            if (download.status == MangaDownload.State.DOWNLOADING || download.status == MangaDownload.State.QUEUE) {
                download.status = MangaDownload.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    private inline fun removeFromQueueIf(predicate: (MangaDownload) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { download ->
                if (download.status == MangaDownload.State.DOWNLOADING ||
                    download.status == MangaDownload.State.QUEUE
                ) {
                    download.status = MangaDownload.State.NOT_DOWNLOADED
                }
            }
            queue - downloads
        }
    }

    fun removeFromQueue(chapters: List<Chapter>) {
        val chapterIds = chapters.map { it.id }
        removeFromQueueIf { it.chapter.id in chapterIds }
    }

    fun removeFromQueue(manga: Manga) {
        removeFromQueueIf { it.manga.id == manga.id }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { download ->
                if (download.status == MangaDownload.State.DOWNLOADING ||
                    download.status == MangaDownload.State.QUEUE
                ) {
                    download.status = MangaDownload.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    @Synchronized
    fun updateQueue(downloads: List<MangaDownload>) {
        val wasRunning = isRunning

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 15
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 30
    }
}

// Arbitrary minimum required space to start a download: 200 MB
private const val MIN_DISK_SPACE = 200L * 1024 * 1024
