package tachiyomi.source.local.entries.novel

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.UnmeteredSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import mihon.core.archive.epubReader
import rx.Observable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.service.ChapterRecognition
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.filter.novel.NovelOrderBy
import tachiyomi.source.local.image.novel.LocalNovelCoverManager
import tachiyomi.source.local.io.novel.LocalNovelSourceFileSystem
import java.util.concurrent.TimeUnit

actual class LocalNovelSource(
    private val context: Context,
    private val fileSystem: LocalNovelSourceFileSystem,
    private val coverManager: LocalNovelCoverManager,
) : NovelCatalogueSource, UnmeteredSource {

    @Suppress("PrivatePropertyName")
    private val PopularFilters = NovelFilterList(NovelOrderBy.Popular(context))

    @Suppress("PrivatePropertyName")
    private val LatestFilters = NovelFilterList(NovelOrderBy.Latest(context))

    override val name: String = context.stringResource(AYMR.strings.local_novel_source)

    override val id: Long = ID

    override val lang: String = "other"

    override fun toString() = name

    override val supportsLatest: Boolean = true

    // Browse
    override suspend fun getPopularNovels(page: Int) = getSearchNovels(page, "", PopularFilters)

    override suspend fun getLatestUpdates(page: Int) = getSearchNovels(page, "", LatestFilters)

    override suspend fun getSearchNovels(
        page: Int,
        query: String,
        filters: NovelFilterList,
    ): NovelsPage = withIOContext {
        val lastModifiedLimit = if (filters === LatestFilters) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }

        val baseDir = fileSystem.getBaseDirectory()
        logcat(LogPriority.DEBUG) { "LocalNovelSource: baseDir=$baseDir" }

        val allFiles = fileSystem.getFilesInBaseDirectory()
        logcat(LogPriority.DEBUG) { "LocalNovelSource: allFiles count=${allFiles.size}, names=${allFiles.map { it.name }}" }

        var novelDirs = allFiles
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }
            .filter {
                if (lastModifiedLimit == 0L && query.isBlank()) {
                    true
                } else if (lastModifiedLimit == 0L) {
                    it.name.orEmpty().contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is NovelOrderBy.Popular -> {
                    novelDirs = if (filter.state!!.ascending) {
                        novelDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    } else {
                        novelDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    }
                }
                is NovelOrderBy.Latest -> {
                    novelDirs = if (filter.state!!.ascending) {
                        novelDirs.sortedBy(UniFile::lastModified)
                    } else {
                        novelDirs.sortedByDescending(UniFile::lastModified)
                    }
                }
                else -> { /* Do nothing */ }
            }
        }

        val novels = novelDirs
            .map { novelDir ->
                async {
                    SNovel.create().apply {
                        title = novelDir.name.orEmpty()
                        url = novelDir.name.orEmpty()

                        coverManager.find(novelDir.name.orEmpty())?.let {
                            thumbnail_url = it.uri.toString()
                        }
                    }
                }
            }
            .awaitAll()

        logcat(LogPriority.DEBUG) { "LocalNovelSource: returning ${novels.size} novels" }
        NovelsPage(novels, false)
    }

    // Novel details
    override suspend fun getNovelDetails(novel: SNovel): SNovel = withIOContext {
        coverManager.find(novel.url)?.let {
            novel.thumbnail_url = it.uri.toString()
        }

        try {
            val novelDirFiles = fileSystem.getFilesInNovelDirectory(novel.url)
            val firstEpub = novelDirFiles.firstOrNull {
                it.extension.equals("epub", true)
            }

            if (firstEpub != null) {
                firstEpub.epubReader(context).use { epub ->
                    val ref = epub.getPackageHref()
                    val doc = epub.getPackageDocument(ref)

                    doc.getElementsByTag("dc:creator").first()?.text()?.let {
                        novel.author = it
                    }
                    doc.getElementsByTag("dc:description").first()?.text()?.let {
                        novel.description = it
                    }
                    doc.getElementsByTag("dc:title").first()?.text()?.let {
                        if (novel.title.isBlank()) {
                            novel.title = it
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) {
                "Error setting novel details from local metadata for ${novel.title}"
            }
        }

        return@withIOContext novel
    }

    // Chapters
    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> = withIOContext {
        val chapters = fileSystem.getFilesInNovelDirectory(novel.url)
            .filterNot { it.name.orEmpty().startsWith('.') }
            .filter { it.extension.equals("epub", true) }
            .map { chapterFile ->
                SNovelChapter.create().apply {
                    url = "${novel.url}/${chapterFile.name}"
                    name = chapterFile.nameWithoutExtension.orEmpty()
                    date_upload = chapterFile.lastModified()

                    val chapterNumber = ChapterRecognition
                        .parseChapterNumber(novel.title, this.name, this.chapter_number.toDouble())
                        .toFloat()
                    chapter_number = chapterNumber

                    try {
                        chapterFile.epubReader(context).use { epub ->
                            val ref = epub.getPackageHref()
                            val doc = epub.getPackageDocument(ref)

                            doc.getElementsByTag("dc:title").first()?.text()?.let {
                                name = it
                            }
                        }
                    } catch (_: Throwable) {
                        // Keep filename-based name
                    }
                }
            }
            .sortedWith { c1, c2 ->
                val c = c2.chapter_number.compareTo(c1.chapter_number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }

        if (novel.thumbnail_url.isNullOrBlank()) {
            chapters.lastOrNull()?.let { chapter ->
                updateCover(chapter, novel)
            }
        }

        chapters
    }

    // Chapter text
    override suspend fun getChapterText(chapter: SNovelChapter): String = withIOContext {
        try {
            val parts = chapter.url.split('/', limit = 2)
            val novelDir = parts[0]
            val chapterName = parts[1]

            val chapterFile = fileSystem.getNovelDirectory(novelDir)
                ?.findFile(chapterName)
                ?: return@withIOContext EMPTY_CHAPTER_HTML

            chapterFile.epubReader(context).use { epub ->
                val packageHref = epub.getPackageHref()
                val packageDoc = epub.getPackageDocument(packageHref)

                val manifestItems = packageDoc.select("manifest > item")
                    .filter { it.attr("media-type") == "application/xhtml+xml" }
                    .associateBy { it.attr("id") }

                val spinePages = packageDoc.select("spine > itemref")
                    .map { manifestItems[it.attr("idref")]?.attr("href") }
                    .filterNotNull()

                val basePath = packageHref.substringBeforeLast("/")
                val html = StringBuilder()

                spinePages.forEach { page ->
                    val entryPath = if (basePath.isEmpty()) page else "$basePath/$page"
                    epub.getInputStream(entryPath)?.use { stream ->
                        html.append(stream.bufferedReader().readText())
                    }
                }

                if (html.isEmpty()) {
                    EMPTY_CHAPTER_HTML
                } else {
                    html.toString()
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error reading chapter text for ${chapter.url}" }
            EMPTY_CHAPTER_HTML
        }
    }

    // Filters
    override fun getFilterList() = NovelFilterList(NovelOrderBy.Popular(context))

    // Old fetch functions (deprecated, required by NovelCatalogueSource)
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularNovels"))
    override fun fetchPopularNovels(page: Int): Observable<NovelsPage> {
        return Observable.fromCallable {
            runBlocking { getPopularNovels(page) }
        }
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> {
        return Observable.fromCallable {
            runBlocking { getLatestUpdates(page) }
        }
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchNovels"))
    override fun fetchSearchNovels(page: Int, query: String, filters: NovelFilterList): Observable<NovelsPage> {
        return runBlocking {
            Observable.just(getSearchNovels(page, query, filters))
        }
    }

    // Cover extraction from epub chapter
    private fun updateCover(chapter: SNovelChapter, novel: SNovel): UniFile? {
        return try {
            val parts = chapter.url.split('/', limit = 2)
            val novelDir = parts[0]
            val chapterName = parts[1]

            val chapterFile = fileSystem.getNovelDirectory(novelDir)
                ?.findFile(chapterName)
                ?: return null

            chapterFile.epubReader(context).use { epub ->
                val entry = epub.getImagesFromPages().firstOrNull()
                entry?.let {
                    epub.getInputStream(it)?.use { stream ->
                        coverManager.update(novel, stream)
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error updating cover for ${novel.title}" }
            null
        }
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://aniyomi.org/help/guides/local-novel/"

        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)
        private const val EMPTY_CHAPTER_HTML = "<html><body></body></html>"
    }
}

fun Novel.isLocal(): Boolean = source == LocalNovelSource.ID

fun NovelSource.isLocal(): Boolean = id == LocalNovelSource.ID
