package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.decodeStoredChapterProgress
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.shouldRestoreSavedProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.core.archive.archiveReader
import mihon.core.archive.epubReader
import mihon.core.archive.pdfReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.i18n.MR
import tachiyomi.source.local.entries.manga.LocalMangaSource
import tachiyomi.source.local.io.Format
import uy.kohesive.injekt.injectLazy

/**
 * Loader used to retrieve the [PageLoader] for a given chapter.
 */
class ChapterLoader(
    private val context: Context,
    private val downloadManager: MangaDownloadManager,
    private val downloadProvider: MangaDownloadProvider,
    private val manga: Manga,
    private val source: MangaSource,
    private val pageLoaderFactory: ((ReaderChapter) -> PageLoader)? = null,
) {

    private val readerPreferences: ReaderPreferences by injectLazy()

    /**
     * Serializes the check-and-set of [ReaderChapter.State.Loading]: preload (launched per
     * page-select) and loadNewChapter/loadAdjacent could both pass the non-atomic check, create
     * two page loaders and race the network page-list fetch.
     */
    private val loadMutex = Mutex()

    /**
     * Assigns the chapter's page loader and loads the its pages. Returns immediately if the chapter
     * is already loaded. If another caller is already loading the chapter, suspends until that
     * load settles instead of returning with a page-less chapter (which used to make the viewer
     * rebuild with empty pages - blank screen until the next interaction).
     */
    suspend fun loadChapter(chapter: ReaderChapter) {
        if (chapterIsReady(chapter)) {
            return
        }

        val startedHere = loadMutex.withLock {
            when {
                chapterIsReady(chapter) -> false
                // A-LOW (orphaned loader): concurrent loadChapter calls for the same chapter
                // (navigating onto a chapter that is already being preloaded) each created a
                // page loader; the second assignment overwrote the first, which was never
                // recycled and whose HTTP worker kept an IO thread blocked forever.
                chapter.state is ReaderChapter.State.Loading -> false
                else -> {
                    chapter.state = ReaderChapter.State.Loading
                    true
                }
            }
        }
        if (!startedHere) {
            // Someone else is loading this chapter right now: wait for their result so the
            // caller never proceeds with a page-less Loading chapter.
            chapter.stateFlow.first { it !is ReaderChapter.State.Loading }
            return
        }

        try {
            loadPages(chapter)
        } catch (e: CancellationException) {
            // Cancelled between setting State.Loading and entering the IO block: the inner
            // try/catch never ran, so without this the state stays Loading forever and every
            // awaiter (loadChapter waiters, preload, transition retry) hangs.
            if (chapter.state is ReaderChapter.State.Loading) {
                chapter.state = ReaderChapter.State.Error(e)
            }
            throw e
        }
    }

    private suspend fun loadPages(chapter: ReaderChapter) {
        withIOContext {
            logcat { "Loading pages for ${chapter.chapter.name}" }
            var loader: PageLoader? = null
            try {
                loader = getPageLoader(chapter)
                // Defense in depth: never orphan an already-assigned loader.
                chapter.pageLoader?.takeIf { it !== loader }?.recycle()
                chapter.pageLoader = loader

                val pages = loader.getPages()
                    .onEach { it.chapter = chapter }
                if (pages.isEmpty()) {
                    throw Exception(context.stringResource(MR.strings.page_list_empty_error))
                }

                // If the chapter is partially read, set the starting page to the last the user read
                // otherwise use the requested page.
                if (shouldRestoreSavedProgress(chapter, readerPreferences.preserveReadingPosition().get())) {
                    // ReaderViewModel can precompute a more accurate resume target (including long-page cache).
                    // Don't override it once it's already set.
                    if (chapter.requestedPage == 0 &&
                        chapter.requestedPageOffset == 0 &&
                        chapter.requestedPageOffsetRatioPpm == null
                    ) {
                        val savedProgress = decodeStoredChapterProgress(
                            value = chapter.chapter.last_page_read,
                            restoreOffset = readerPreferences.saveLongPagePosition().get(),
                        )
                        chapter.requestedPage = savedProgress.index
                        chapter.requestedPageOffset = savedProgress.offsetPx
                    }
                }

                chapter.state = ReaderChapter.State.Loaded(pages)
            } catch (e: Throwable) {
                if (loader != null) {
                    if (chapter.pageLoader === loader) {
                        chapter.pageLoader = null
                    }
                    loader.recycle()
                }
                chapter.state = ReaderChapter.State.Error(e)
                throw e
            }
        }
    }

    /**
     * Checks [chapter] to be loaded based on present pages and loader in addition to state.
     */
    private fun chapterIsReady(chapter: ReaderChapter): Boolean {
        return chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null
    }

    /**
     * Returns the page loader to use for this [chapter].
     */
    private fun getPageLoader(chapter: ReaderChapter): PageLoader {
        pageLoaderFactory?.let { return it(chapter) }
        val dbChapter = chapter.chapter
        val isDownloaded = downloadManager.isChapterDownloaded(
            dbChapter.name,
            dbChapter.scanlator,
            manga.title,
            manga.source,
            skipCache = true,
            mangaId = manga.id,
            chapterId = dbChapter.id,
        )
        return when {
            isDownloaded -> DownloadPageLoader(
                chapter,
                manga,
                source,
                downloadManager,
                downloadProvider,
            )
            source is LocalMangaSource -> source.getFormat(chapter.chapter).let { format ->
                when (format) {
                    is Format.Directory -> DirectoryPageLoader(format.file)
                    is Format.Archive -> ArchivePageLoader(format.file.archiveReader(context))
                    is Format.Epub -> EpubPageLoader(format.file.epubReader(context))
                    is Format.Pdf -> PdfPageLoader(format.file.pdfReader(context))
                    is Format.Text, is Format.Html -> error(
                        context.stringResource(MR.strings.loader_not_implemented_error),
                    )
                }
            }
            source is HttpSource -> HttpPageLoader(chapter, source)
            source is StubMangaSource -> error(
                context.stringResource(MR.strings.source_not_installed, source.toString()),
            )
            else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
        }
    }
}
