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
import org.jsoup.nodes.Entities
import rx.Observable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
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

        var novelEntries = fileSystem.getFilesInBaseDirectory()
            .filterNot { it.name.orEmpty().startsWith('.') }
            .filter { it.isDirectory || isChapterSupported(it) }
            .distinctBy { it.name.orEmpty() }
            .filter {
                val displayTitle = if (it.isDirectory) it.name.orEmpty() else it.nameWithoutExtension.orEmpty()
                if (lastModifiedLimit == 0L && query.isBlank()) {
                    true
                } else if (lastModifiedLimit == 0L) {
                    displayTitle.contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is NovelOrderBy.Popular -> {
                    novelEntries = if (filter.state!!.ascending) {
                        novelEntries.sortedWith(
                            compareBy(String.CASE_INSENSITIVE_ORDER) {
                                if (it.isDirectory) it.name.orEmpty() else it.nameWithoutExtension.orEmpty()
                            },
                        )
                    } else {
                        novelEntries.sortedWith(
                            compareByDescending(String.CASE_INSENSITIVE_ORDER) {
                                if (it.isDirectory) it.name.orEmpty() else it.nameWithoutExtension.orEmpty()
                            },
                        )
                    }
                }
                is NovelOrderBy.Latest -> {
                    novelEntries = if (filter.state!!.ascending) {
                        novelEntries.sortedBy(UniFile::lastModified)
                    } else {
                        novelEntries.sortedByDescending(UniFile::lastModified)
                    }
                }
                else -> { /* Do nothing */ }
            }
        }

        val novels = novelEntries
            .map { novelEntry ->
                async {
                    SNovel.create().apply {
                        title = if (novelEntry.isDirectory) {
                            novelEntry.name.orEmpty()
                        } else {
                            novelEntry.nameWithoutExtension.orEmpty()
                        }
                        // Keep URL as the exact entry name so both directories and files can resolve
                        url = novelEntry.name.orEmpty()

                        coverManager.find(url)?.let {
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
            val novelEntry = resolveNovelEntry(novel.url) ?: error("${novel.url} is not a valid local novel entry")

            if (novelEntry.isDirectory) {
                val novelDirFiles = novelEntry.listFiles().orEmpty()
                val firstEpub = novelDirFiles.firstOrNull {
                    it.extension?.equals("epub", ignoreCase = true) == true
                }

                if (firstEpub != null) {
                    fillNovelMetadataFromEpub(firstEpub, novel)
                    extractEmbeddedCoverIfMissing(novel, firstEpub)
                }
            } else if (novelEntry.extension?.equals("epub", ignoreCase = true) == true) {
                fillNovelMetadataFromEpub(novelEntry, novel)
                extractEmbeddedCoverIfMissing(novel, novelEntry)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) {
                "Error setting novel details from local metadata for ${novel.title}"
            }
        }

        return@withIOContext novel
    }

    private fun fillNovelMetadataFromEpub(epubFile: UniFile, novel: SNovel) {
        epubFile.epubReader(context).use { epub ->
            val ref = epub.getPackageHref()
            val doc = epub.getPackageDocument(ref)

            val title = firstNonBlank(
                doc.getElementsByTag("dc:title").firstOrNull()?.text(),
                doc.select("dc\\:title").firstOrNull()?.text(),
                doc.select("docTitle").firstOrNull()?.text(),
                doc.select("meta[name=title]").firstOrNull()?.attr("content"),
                doc.select("meta[property=title]").firstOrNull()?.attr("content"),
            )

            val creators = (doc.getElementsByTag("dc:creator") + doc.select("dc\\:creator"))
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val description = firstNonBlank(
                doc.getElementsByTag("dc:description").firstOrNull()?.text(),
                doc.select("dc\\:description").firstOrNull()?.text(),
                doc.select("meta[name=description]").firstOrNull()?.attr("content"),
                doc.select("meta[property=description]").firstOrNull()?.attr("content"),
            )

            val subjects = (doc.getElementsByTag("dc:subject") + doc.select("dc\\:subject"))
                .flatMap { it.text().split(',', ';') }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            title?.let { novel.title = it }
            if (creators.isNotEmpty()) {
                novel.author = creators.joinToString(", ")
            }
            description?.let { novel.description = it }

            if (subjects.isNotEmpty()) {
                val currentGenres =
                    novel.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val allGenres = (currentGenres + subjects).distinct()
                novel.genre = allGenres.joinToString(", ")
            }
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun extractEmbeddedCoverIfMissing(novel: SNovel, epubFile: UniFile) {
        if (coverManager.find(novel.url) != null) return

        runCatching {
            epubFile.epubReader(context).use { epub ->
                val cover = epub.getCoverImage() ?: return
                epub.getInputStream(cover)?.use { stream ->
                    coverManager.update(novel, stream)
                }
            }
        }.onFailure { e ->
            logcat(LogPriority.WARN, e) { "Unable to extract embedded cover from ${epubFile.name}" }
        }
    }

    // Chapters — TOC-based splitting for multi-chapter epubs
    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> = withIOContext {
        val allChapters = mutableListOf<SNovelChapter>()

        val novelEntry = resolveNovelEntry(novel.url) ?: return@withIOContext emptyList()

        val chapterFiles = collectChapterFiles(novelEntry)

        val hasMultipleEpubFiles = chapterFiles
            .count { it.file.extension?.equals("epub", ignoreCase = true) == true } > 1

        chapterFiles.forEachIndexed { index, chapterEntry ->
            val chapterFile = chapterEntry.file
            if (chapterFile.extension?.equals("epub", ignoreCase = true) == true) {
                try {
                    chapterFile.epubReader(context).use { epub ->
                        extractEmbeddedCoverIfMissing(novel, chapterFile)

                        val tocChapters = epub.getNormalizedTableOfContents()
                        val spinePages = epub.getSpinePageHrefs()

                        if (tocChapters.isNotEmpty() || spinePages.isNotEmpty()) {
                            allChapters.addAll(
                                buildEpubChaptersFromToc(
                                    mangaUrl = novel.url,
                                    chapterFileName = chapterEntry.relativePath,
                                    chapterFileNameWithoutExtension = chapterEntry.displayName,
                                    chapterLastModified = chapterFile.lastModified(),
                                    tocChapters = tocChapters,
                                    spinePageHrefs = spinePages,
                                    hasMultipleEpubFiles = hasMultipleEpubFiles,
                                    chapterNumberOffset = allChapters.size.toFloat(),
                                ),
                            )
                        } else {
                            allChapters.add(createSimpleChapter(novel, chapterEntry, index + 1))
                        }
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Error reading epub ${chapterFile.name}" }
                    allChapters.add(createSimpleChapter(novel, chapterEntry, index + 1))
                }
            } else {
                allChapters.add(createSimpleChapter(novel, chapterEntry, index + 1))
            }
        }

        allChapters.sortedWith { c1, c2 ->
            when {
                c1.chapter_number != c2.chapter_number -> c1.chapter_number.compareTo(c2.chapter_number)
                else -> c1.name.compareToCaseInsensitiveNaturalOrder(c2.name)
            }
        }
    }

    private fun createSimpleChapter(
        novel: SNovel,
        chapterEntry: LocalChapterFile,
        fallbackChapterNumber: Int,
    ): SNovelChapter {
        return SNovelChapter.create().apply {
            url = "${novel.url}/${chapterEntry.relativePath}"
            name = chapterEntry.displayName
            date_upload = chapterEntry.file.lastModified()
            chapter_number = fallbackChapterNumber.toFloat()
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
            val chapterName = parts.getOrNull(1)

            val chapterFile = if (chapterName != null) {
                fileSystem.getNovelDirectory(novelDir)?.let { resolveRelativeFile(it, chapterName) }
                    ?: fileSystem.getBaseDirectory()?.findFile(novelDir)?.takeIf {
                        !it.isDirectory &&
                            it.name == chapterName
                    }
            } else {
                fileSystem.getBaseDirectory()?.findFile(novelDir)?.takeIf { !it.isDirectory }
            } ?: return@withIOContext EMPTY_CHAPTER_HTML

            when {
                chapterFile.isDirectory -> {
                    collectTextFiles(chapterFile)
                        .joinToString("\n\n") { readTextFileContent(it) }
                        .ifBlank { EMPTY_CHAPTER_HTML }
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
                                .toList()
                                .sortedWith { e1, e2 ->
                                    e1.name.compareToCaseInsensitiveNaturalOrder(e2.name)
                                }
                        }.joinToString("\n\n") { entry ->
                            reader.getInputStream(entry.name)?.use { stream ->
                                val rawText = stream.bufferedReader().readText()
                                if (isHtmlFileName(entry.name)) rawText else plainTextToHtml(rawText)
                            } ?: ""
                        }
                    }
                }
                isTextFile(chapterFile) -> {
                    readTextFileContent(chapterFile)
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

    private data class LocalChapterFile(
        val file: UniFile,
        val relativePath: String,
        val displayName: String,
    )

    private fun collectChapterFiles(novelEntry: UniFile): List<LocalChapterFile> {
        if (!novelEntry.isDirectory) {
            return listOf(
                LocalChapterFile(
                    file = novelEntry,
                    relativePath = novelEntry.name.orEmpty(),
                    displayName = novelEntry.nameWithoutExtension.orEmpty(),
                ),
            )
        }

        return collectSupportedFilesRecursively(novelEntry)
            .sortedWith { c1, c2 ->
                c1.relativePath.compareToCaseInsensitiveNaturalOrder(c2.relativePath)
            }
    }

    private fun collectSupportedFilesRecursively(
        directory: UniFile,
        relativePrefix: String = "",
    ): List<LocalChapterFile> {
        return directory.listFiles().orEmpty()
            .filterNot { it.name.orEmpty().startsWith('.') }
            .flatMap { child ->
                val childName = child.name.orEmpty()
                val relativePath = if (relativePrefix.isBlank()) {
                    childName
                } else {
                    "$relativePrefix/$childName"
                }

                when {
                    child.isDirectory -> collectSupportedFilesRecursively(child, relativePath)
                    isChapterSupported(child) -> listOf(
                        LocalChapterFile(
                            file = child,
                            relativePath = relativePath,
                            displayName = relativePath.substringBeforeLast('.')
                                .replace('/', ' ')
                                .replace('\\', ' ')
                                .replace('_', ' ')
                                .trim(),
                        ),
                    )
                    else -> emptyList()
                }
            }
    }

    private fun resolveRelativeFile(root: UniFile, relativePath: String): UniFile? {
        return relativePath.split('/')
            .filter { it.isNotBlank() }
            .fold(root as UniFile?) { current, segment -> current?.findFile(segment) }
    }

    private fun collectTextFiles(directory: UniFile): List<UniFile> {
        return directory.listFiles().orEmpty()
            .filterNot { it.name.orEmpty().startsWith('.') }
            .flatMap { child ->
                when {
                    child.isDirectory -> collectTextFiles(child)
                    isTextFile(child) -> listOf(child)
                    else -> emptyList()
                }
            }
            .sortedWith { f1, f2 ->
                f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(f2.name.orEmpty())
            }
    }

    private fun readTextFileContent(file: UniFile): String {
        val rawText = file.openInputStream().bufferedReader().readText()
        return if (isHtmlFileName(file.name.orEmpty())) {
            rawText
        } else {
            plainTextToHtml(rawText)
        }
    }

    private fun plainTextToHtml(text: String): String {
        return "<html><body><pre style=\"white-space: pre-wrap; font-family: inherit;\">" +
            Entities.escape(text) +
            "</pre></body></html>"
    }

    private fun isHtmlFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in HTML_EXTENSIONS
    }

    private fun isTextFile(file: UniFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext in TEXT_EXTENSIONS
    }

    private fun isTextFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    private fun resolveNovelEntry(url: String): UniFile? {
        val base = fileSystem.getBaseDirectory() ?: return null
        return base.findFile(url)
            ?: base.listFiles().orEmpty().firstOrNull {
                !it.isDirectory && it.nameWithoutExtension.orEmpty().equals(url, ignoreCase = true)
            }
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://aniyomi.org/help/guides/local-novel/"

        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)
        private const val EMPTY_CHAPTER_HTML = "<html><body></body></html>"

        private val SUPPORTED_EXTENSIONS = setOf(
            "txt", "text",
            "md", "markdown",
            "html", "htm", "xhtml",
            "epub",
            "zip", "cbz",
            "rar", "cbr",
        )

        private val TEXT_EXTENSIONS = setOf(
            "txt",
            "text",
            "md",
            "markdown",
            "html",
            "htm",
            "xhtml",
        )

        private val HTML_EXTENSIONS = setOf(
            "html",
            "htm",
            "xhtml",
        )
    }
}

fun Novel.isLocal(): Boolean = source == LocalNovelSource.ID

fun NovelSource.isLocal(): Boolean = id == LocalNovelSource.ID
