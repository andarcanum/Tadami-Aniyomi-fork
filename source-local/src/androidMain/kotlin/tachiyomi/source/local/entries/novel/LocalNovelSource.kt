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
import mihon.core.archive.archiveReader
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
import tachiyomi.source.local.io.ArchiveManga.isSupported as isArchiveSupported

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

        var novelDirs = fileSystem.getFilesInBaseDirectory()
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

    // Chapters — TOC-based splitting for multi-chapter epubs
    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> = withIOContext {
        val allChapters = mutableListOf<SNovelChapter>()

        val chapterFiles = fileSystem.getFilesInNovelDirectory(novel.url)
            .filterNot { it.name.orEmpty().startsWith('.') }
            .filter { isChapterSupported(it) }
            .sortedWith { f1, f2 ->
                f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(f2.name.orEmpty())
            }

        val hasMultipleEpubFiles = chapterFiles.count { it.extension.equals("epub", true) } > 1
        var orderedTocChapterNumber = 0

        chapterFiles.forEach { chapterFile ->
            if (chapterFile.extension.equals("epub", true)) {
                try {
                    chapterFile.epubReader(context).use { epub ->
                        // Extract cover from first epub if not set
                        if (coverManager.find(novel.url) == null) {
                            try {
                                val cover = epub.getCoverImage()
                                if (cover != null) {
                                    epub.getInputStream(cover)?.use { stream ->
                                        coverManager.update(novel, stream)
                                    }
                                }
                            } catch (_: Exception) {
                                // Ignore cover extraction errors
                            }
                        }

                        val tocChapters = epub.getTableOfContents()

                        if (tocChapters.size > 1) {
                            tocChapters.forEach { tocEntry ->
                                orderedTocChapterNumber += 1
                                val displayName = if (hasMultipleEpubFiles) {
                                    "${chapterFile.nameWithoutExtension.orEmpty()} - ${tocEntry.title}"
                                } else {
                                    tocEntry.title
                                }

                                allChapters.add(
                                    SNovelChapter.create().apply {
                                        url = "${novel.url}/${chapterFile.name}#${tocEntry.href}"
                                        name = displayName
                                        date_upload = chapterFile.lastModified()
                                        chapter_number = orderedTocChapterNumber.toFloat()
                                    },
                                )
                            }
                        } else {
                            allChapters.add(createSimpleChapter(novel, chapterFile))
                        }
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Error reading epub ${chapterFile.name}" }
                    allChapters.add(createSimpleChapter(novel, chapterFile))
                }
            } else {
                allChapters.add(createSimpleChapter(novel, chapterFile))
            }
        }

        allChapters.sortedWith { c1, c2 ->
            when {
                c1.chapter_number != c2.chapter_number -> c2.chapter_number.compareTo(c1.chapter_number)
                else -> c2.name.compareToCaseInsensitiveNaturalOrder(c1.name)
            }
        }
    }

    private fun createSimpleChapter(novel: SNovel, chapterFile: UniFile): SNovelChapter {
        return SNovelChapter.create().apply {
            url = "${novel.url}/${chapterFile.name}"
            name = if (chapterFile.isDirectory) {
                chapterFile.name
            } else {
                chapterFile.nameWithoutExtension
            }.orEmpty()
            date_upload = chapterFile.lastModified()
            chapter_number = ChapterRecognition
                .parseChapterNumber(novel.title, this.name, this.chapter_number.toDouble())
                .toFloat()
        }
    }

    // Chapter text — supports multi-chapter epubs via #href in URL
    override suspend fun getChapterText(chapter: SNovelChapter): String = withIOContext {
        try {
            val urlParts = chapter.url.split("#", limit = 2)
            val filePath = urlParts[0]
            val chapterFragment = urlParts.getOrNull(1)

            val parts = filePath.split('/', limit = 2)
            val novelDir = parts[0]
            val chapterName = parts[1]

            val chapterFile = fileSystem.getNovelDirectory(novelDir)
                ?.findFile(chapterName)
                ?: return@withIOContext EMPTY_CHAPTER_HTML

            when {
                chapterFile.isDirectory -> {
                    chapterFile.listFiles()
                        ?.filter { isTextFile(it) }
                        ?.sortedBy { it.name }
                        ?.joinToString("\n\n") { it.openInputStream().bufferedReader().readText() }
                        ?: EMPTY_CHAPTER_HTML
                }
                chapterFile.extension.equals("epub", true) -> {
                    chapterFile.epubReader(context).use { epub ->
                        if (chapterFragment != null) {
                            epub.getChapterContent(chapterFragment)
                        } else {
                            epub.getTextContent()
                        }
                    }
                }
                isArchiveSupported(chapterFile) -> {
                    chapterFile.archiveReader(context).use { reader ->
                        reader.useEntries { entries ->
                            entries.filter { it.isFile && isTextFileName(it.name) }
                                .sortedBy { it.name }
                                .toList()
                        }.joinToString("\n\n") { entry ->
                            reader.getInputStream(entry.name)?.bufferedReader()?.readText() ?: ""
                        }
                    }
                }
                isTextFile(chapterFile) -> {
                    chapterFile.openInputStream().bufferedReader().readText()
                }
                else -> EMPTY_CHAPTER_HTML
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error fetching chapter text for ${chapter.url}" }
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

    private fun isChapterSupported(file: UniFile): Boolean {
        if (file.isDirectory) return true
        val ext = file.extension?.lowercase() ?: return false
        return ext in SUPPORTED_EXTENSIONS
    }

    private fun isTextFile(file: UniFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext in TEXT_EXTENSIONS
    }

    private fun isTextFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://aniyomi.org/help/guides/local-novel/"

        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)
        private const val EMPTY_CHAPTER_HTML = "<html><body></body></html>"

        private val SUPPORTED_EXTENSIONS = setOf(
            "txt", "text",
            "html", "htm", "xhtml",
            "epub",
            "zip", "cbz",
            "rar", "cbr",
        )

        private val TEXT_EXTENSIONS = setOf(
            "txt",
            "text",
            "html",
            "htm",
            "xhtml",
        )
    }
}

fun Novel.isLocal(): Boolean = source == LocalNovelSource.ID

fun NovelSource.isLocal(): Boolean = id == LocalNovelSource.ID
