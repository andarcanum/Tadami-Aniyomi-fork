package eu.kanade.tachiyomi.data.export.novel

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact
import eu.kanade.tachiyomi.data.book.novel.NovelBookBuilder
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jsoup.nodes.Document
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class NovelEpubExportOptions(
    val downloadedOnly: Boolean = true,
    val startChapter: Int? = null,
    val endChapter: Int? = null,
    val destinationTreeUri: String? = null,
    val stylesheet: String? = null,
    val javaScript: String? = null,
    val includeCover: Boolean = true,
    val failOnMissingChapters: Boolean = false,
)

data class NovelEpubExportReport(
    val totalSelected: Int,
    val includedChapters: Int,
    val skippedChapters: List<String> = emptyList(),
    val embeddedImages: Int = 0,
    val skippedImages: Int = 0,
    val warnings: List<String> = emptyList(),
    val validationStatus: String? = null,
    val outputSizeBytes: Long = 0L,
)

sealed interface NovelEpubExportResult {
    data class Success(
        val cacheFile: File,
        val destinationUri: Uri?,
        val report: NovelEpubExportReport,
    ) : NovelEpubExportResult

    data class Failure(
        val reason: NovelEpubExportFailure,
        val report: NovelEpubExportReport? = null,
    ) : NovelEpubExportResult
}

enum class NovelEpubExportFailure {
    NO_CHAPTERS_SELECTED,
    NO_READABLE_CHAPTERS,
    MISSING_SELECTED_CHAPTERS,
    DESTINATION_PERMISSION_DENIED,
    UNKNOWN,
}

sealed interface NovelEpubExportProgress {
    data class Preparing(val totalChapters: Int) : NovelEpubExportProgress

    data class ChapterProcessed(
        val current: Int,
        val total: Int,
    ) : NovelEpubExportProgress

    data object Finalizing : NovelEpubExportProgress

    data class Done(val file: File) : NovelEpubExportProgress
}

class NovelEpubExporter(
    private val application: Application? = runCatching { Injekt.get<Application>() }.getOrNull(),
    private val sourceManager: NovelSourceManager? = runCatching { Injekt.get<NovelSourceManager>() }.getOrNull(),
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
    networkHelper: NetworkHelper? = runCatching { Injekt.get<NetworkHelper>() }.getOrNull(),
    private val coverCache: NovelCoverCache? = runCatching { Injekt.get<NovelCoverCache>() }.getOrNull(),
) {
    private val assetResolver = EpubAssetResolver(networkHelper)

    suspend fun export(
        novel: Novel,
        chapters: List<NovelChapter>,
        options: NovelEpubExportOptions = NovelEpubExportOptions(),
        onProgress: (NovelEpubExportProgress) -> Unit = {},
    ): File? {
        return when (val result = exportWithResult(novel, chapters, options, onProgress)) {
            is NovelEpubExportResult.Success -> result.cacheFile
            is NovelEpubExportResult.Failure -> null
        }
    }

    suspend fun exportWithResult(
        novel: Novel,
        chapters: List<NovelChapter>,
        options: NovelEpubExportOptions = NovelEpubExportOptions(),
        onProgress: (NovelEpubExportProgress) -> Unit = {},
    ): NovelEpubExportResult {
        val sorted = sortChaptersForExport(chapters)
        val selected = applyRange(sorted, options.startChapter, options.endChapter)
        if (selected.isEmpty()) {
            return NovelEpubExportResult.Failure(
                NovelEpubExportFailure.NO_CHAPTERS_SELECTED,
                NovelEpubExportReport(totalSelected = 0, includedChapters = 0),
            )
        }
        onProgress(NovelEpubExportProgress.Preparing(totalChapters = selected.size))

        assetResolver.resetSession()
        val warnings = mutableListOf<String>()
        if (!options.javaScript.isNullOrBlank()) {
            warnings += "Custom JavaScript is experimental and may be ignored by EPUB readers."
        }
        var embeddedImages = 0
        var skippedImages = 0

        val chapterPayloads = selected.mapNotNull { chapter ->
            currentCoroutineContext().ensureActive()
            val html = loadChapterHtml(novel, chapter, options.downloadedOnly) ?: return@mapNotNull null
            ChapterPayload(
                chapter = chapter,
                html = html,
            )
        }
        val skippedChapters = selected
            .filterNot { selectedChapter -> chapterPayloads.any { it.chapter.id == selectedChapter.id } }
            .map { it.name.ifBlank { it.url } }
        if (chapterPayloads.isEmpty()) {
            return NovelEpubExportResult.Failure(
                NovelEpubExportFailure.NO_READABLE_CHAPTERS,
                NovelEpubExportReport(
                    totalSelected = selected.size,
                    includedChapters = 0,
                    skippedChapters = skippedChapters,
                    warnings = warnings,
                ),
            )
        }
        if (options.failOnMissingChapters && skippedChapters.isNotEmpty()) {
            return NovelEpubExportResult.Failure(
                NovelEpubExportFailure.MISSING_SELECTED_CHAPTERS,
                NovelEpubExportReport(
                    totalSelected = selected.size,
                    includedChapters = chapterPayloads.size,
                    skippedChapters = skippedChapters,
                    warnings = warnings,
                ),
            )
        }

        val exportDir = File(
            application?.cacheDir ?: return NovelEpubExportResult.Failure(NovelEpubExportFailure.UNKNOWN),
            "exports/novel",
        )
        exportDir.mkdirs()
        val filename = DiskUtil.buildValidFilename("${novel.displayTitle}_${System.currentTimeMillis()}.epub")
        val epubFile = File(exportDir, filename)
        val epubLanguage = resolveLanguage(novel)
        val bookId = stableBookIdentifier(novel)
        val modified = formatEpubModifiedTimestamp(Instant.now())

        ZipOutputStream(epubFile.outputStream().buffered()).use { zip ->
            writeStoredEntry(
                zip = zip,
                path = "mimetype",
                bytes = "application/epub+zip".toByteArray(Charsets.UTF_8),
            )

            writeEntry(
                zip = zip,
                path = "META-INF/container.xml",
                content = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                        <rootfiles>
                            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                        </rootfiles>
                    </container>
                """.trimIndent(),
            )

            val manifestAssets = linkedMapOf<String, EpubAssetItem>()
            val imageAssetsByHash = mutableMapOf<String, EpubAssetItem>()

            val stylesheetPath = options.stylesheet
                ?.takeIf { it.isNotBlank() }
                ?.let { stylesheet ->
                    val path = "styles/reader.css"
                    writeEntry(
                        zip = zip,
                        path = "OEBPS/$path",
                        content = stylesheet,
                    )
                    manifestAssets[path] = EpubAssetItem(
                        id = "reader_css",
                        href = path,
                        mediaType = "text/css",
                    )
                    path
                }

            val scriptPath = options.javaScript
                ?.takeIf { it.isNotBlank() }
                ?.let { script ->
                    val path = "scripts/reader.js"
                    writeEntry(
                        zip = zip,
                        path = "OEBPS/$path",
                        content = script,
                    )
                    manifestAssets[path] = EpubAssetItem(
                        id = "reader_js",
                        href = path,
                        mediaType = "application/javascript",
                    )
                    path
                }

            val resolvedCoverAsset = if (options.includeCover) {
                resolveCoverAsset(novel)
            } else {
                EpubAssetResolutionReport(asset = null)
            }
            if (options.includeCover && !novel.thumbnailUrl.isNullOrBlank() && resolvedCoverAsset.asset == null) {
                skippedImages += 1
                warnings += resolvedCoverAsset.warning ?: "Cover image could not be embedded."
            }

            val coverAsset = resolvedCoverAsset.asset?.let { cover ->
                embeddedImages += 1
                val path = "images/cover.${cover.extension}"
                writeEntryBytes(
                    zip = zip,
                    path = "OEBPS/$path",
                    bytes = cover.bytes,
                )
                EpubAssetItem(
                    id = "cover_image",
                    href = path,
                    mediaType = cover.mediaType,
                    properties = "cover-image",
                ).also { manifestAssets[path] = it }
            }

            val frontMatterItem = if (coverAsset != null) {
                val page = EpubFrontMatterItem(
                    id = "cover_page",
                    fileName = "cover.xhtml",
                    landmarkType = "cover",
                    landmarkTitle = "Cover",
                )
                writeEntry(
                    zip = zip,
                    path = "OEBPS/${page.fileName}",
                    content = buildCoverDocument(
                        title = novel.displayTitle,
                        coverHref = coverAsset.href,
                        language = epubLanguage,
                    ),
                )
                page
            } else {
                val page = EpubFrontMatterItem(
                    id = "title_page",
                    fileName = "title.xhtml",
                    landmarkType = "titlepage",
                    landmarkTitle = "Title page",
                )
                writeEntry(
                    zip = zip,
                    path = "OEBPS/${page.fileName}",
                    content = buildTitleDocument(
                        novel = novel,
                        language = epubLanguage,
                        modified = modified,
                    ),
                )
                page
            }

            val chapterItems = chapterPayloads.mapIndexed { index, payload ->
                currentCoroutineContext().ensureActive()
                val fileName = "chapter_${index + 1}.xhtml"
                val chapterId = "chapter_${index + 1}"
                val chapterTitle = payload.chapter.name.ifBlank {
                    "Chapter ${index + 1}"
                }
                val chapterDocument = EpubXhtmlSanitizer.parseBodyFragment(payload.html)
                val imageReport = embedChapterImages(
                    zip = zip,
                    chapterDocument = chapterDocument,
                    chapterIndex = index + 1,
                    chapterUrl = payload.chapter.url,
                    novelUrl = novel.url,
                    manifestAssets = manifestAssets,
                    imageAssetsByHash = imageAssetsByHash,
                )
                embeddedImages += imageReport.newAssets
                skippedImages += imageReport.skippedImages
                warnings += imageReport.warnings
                val chapterBody = EpubXhtmlSanitizer.bodyHtml(chapterDocument)
                val styleLink = stylesheetPath?.let { path ->
                    """<link rel="stylesheet" href="$path" type="text/css"/>"""
                }.orEmpty()
                val scriptTag = scriptPath?.let { path ->
                    """<script src="$path" type="text/javascript"></script>"""
                }.orEmpty()
                writeEntry(
                    zip = zip,
                    path = "OEBPS/$fileName",
                    content = buildChapterDocument(
                        title = chapterTitle,
                        body = chapterBody,
                        styleLink = styleLink,
                        scriptTag = scriptTag,
                        language = epubLanguage,
                    ),
                )
                val chapterItem = EpubChapterItem(
                    id = chapterId,
                    fileName = fileName,
                    title = chapterTitle,
                )
                onProgress(
                    NovelEpubExportProgress.ChapterProcessed(
                        current = index + 1,
                        total = chapterPayloads.size,
                    ),
                )
                chapterItem
            }

            onProgress(NovelEpubExportProgress.Finalizing)
            writeEntry(
                zip = zip,
                path = "OEBPS/nav.xhtml",
                content = buildNavDocument(novel.displayTitle, chapterItems, epubLanguage, frontMatterItem),
            )
            writeEntry(
                zip = zip,
                path = "OEBPS/toc.ncx",
                content = buildTocDocument(
                    title = novel.displayTitle,
                    chapterItems = chapterItems,
                    bookId = bookId,
                    language = epubLanguage,
                ),
            )
            writeEntry(
                zip = zip,
                path = "OEBPS/content.opf",
                content = buildPackageDocument(
                    novel = novel,
                    chapterItems = chapterItems,
                    language = epubLanguage,
                    bookId = bookId,
                    modified = modified,
                    frontMatterItem = frontMatterItem,
                    additionalAssets = manifestAssets.values.toList(),
                    hasCover = coverAsset != null,
                ),
            )
        }

        val destinationTreeUri = options.destinationTreeUri?.trim().orEmpty()
        if (destinationTreeUri.isNotBlank()) {
            val copied = copyToDestinationTree(
                epubFile = epubFile,
                destinationTreeUri = destinationTreeUri,
            )
            if (copied == null) {
                return NovelEpubExportResult.Failure(
                    NovelEpubExportFailure.DESTINATION_PERMISSION_DENIED,
                    NovelEpubExportReport(
                        totalSelected = selected.size,
                        includedChapters = chapterPayloads.size,
                        skippedChapters = skippedChapters,
                        embeddedImages = embeddedImages,
                        skippedImages = skippedImages,
                        warnings = warnings,
                        outputSizeBytes = epubFile.length(),
                    ),
                )
            }
            onProgress(NovelEpubExportProgress.Done(epubFile))
            return NovelEpubExportResult.Success(
                cacheFile = epubFile,
                destinationUri = copied,
                report = NovelEpubExportReport(
                    totalSelected = selected.size,
                    includedChapters = chapterPayloads.size,
                    skippedChapters = skippedChapters,
                    embeddedImages = embeddedImages,
                    skippedImages = skippedImages,
                    warnings = warnings,
                    outputSizeBytes = epubFile.length(),
                ),
            )
        }

        onProgress(NovelEpubExportProgress.Done(epubFile))
        return NovelEpubExportResult.Success(
            cacheFile = epubFile,
            destinationUri = null,
            report = NovelEpubExportReport(
                totalSelected = selected.size,
                includedChapters = chapterPayloads.size,
                skippedChapters = skippedChapters,
                embeddedImages = embeddedImages,
                skippedImages = skippedImages,
                warnings = warnings,
                outputSizeBytes = epubFile.length(),
            ),
        )
    }

    private suspend fun loadChapterHtml(
        novel: Novel,
        chapter: NovelChapter,
        downloadedOnly: Boolean,
    ): String? {
        val downloaded = downloadManager.getDownloadedChapterText(novel, chapter.id)
        if (downloaded != null) return downloaded
        loadArtifactChapterHtml(novel, chapter)?.let { return it }
        if (downloadedOnly) return null
        val source = sourceManager?.get(novel.source) ?: return null
        return runCatching { source.getChapterText(chapter.toSNovelChapter()) }.getOrNull()
    }

    /**
     * Reads the chapter text from the compiled book artifact when the per-chapter
     * download files are gone (for example after "delete source chapters").
     */
    private fun loadArtifactChapterHtml(
        novel: Novel,
        chapter: NovelChapter,
    ): String? {
        return runCatching {
            val directory = NovelBookArtifact.directoryFor(
                root = NovelBookBuilder.defaultRootDirectory(),
                sourceId = novel.source,
                novelId = novel.id,
            )
            if (!NovelBookArtifact.exists(directory)) return@runCatching null
            val entry = NovelBookArtifact.readIndex(directory)
                ?.chapters
                ?.firstOrNull { it.chapterId == chapter.id }
                ?: return@runCatching null
            NovelBookArtifact.readRange(directory, entry.byteStart, entry.byteLength)
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun applyRange(
        chapters: List<NovelChapter>,
        startChapter: Int?,
        endChapter: Int?,
    ): List<NovelChapter> {
        if (chapters.isEmpty()) return emptyList()
        val startIndex = (startChapter ?: 1).coerceAtLeast(1) - 1
        val endIndex = ((endChapter ?: chapters.size).coerceAtMost(chapters.size) - 1)
        if (startIndex > endIndex || startIndex >= chapters.size) return emptyList()
        return chapters.subList(startIndex, endIndex + 1)
    }

    private fun buildPackageDocument(
        novel: Novel,
        chapterItems: List<EpubChapterItem>,
        language: String,
        bookId: String,
        modified: String,
        frontMatterItem: EpubFrontMatterItem,
        additionalAssets: List<EpubAssetItem>,
        hasCover: Boolean,
    ): String {
        val manifestItems = buildString {
            appendLine("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            appendLine("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
            appendLine(
                """<item id="${frontMatterItem.id}" href="${frontMatterItem.fileName}" media-type="application/xhtml+xml"/>""",
            )
            chapterItems.forEach { chapter ->
                appendLine(
                    """<item id="${chapter.id}" href="${chapter.fileName}" media-type="application/xhtml+xml"/>""",
                )
            }
            additionalAssets.distinctBy { it.href }.forEach { asset ->
                val propertiesAttr = asset.properties?.let { """ properties="$it"""" }.orEmpty()
                appendLine(
                    """<item id="${asset.id}" href="${asset.href}" media-type="${asset.mediaType}"$propertiesAttr/>""",
                )
            }
        }.trim()
        val spineItems = chapterItems.joinToString(separator = "\n") { chapter ->
            """<itemref idref="${chapter.id}"/>"""
        }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" prefix="dcterms: http://purl.org/dc/terms/ rendition: http://www.idpf.org/vocab/rendition/#">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="bookid">${escapeXml(bookId)}</dc:identifier>
                    <dc:title>${escapeXml(novel.displayTitle)}</dc:title>
                    <dc:language>${escapeXml(language)}</dc:language>
                    <meta property="dcterms:modified">$modified</meta>
                    <meta property="rendition:layout">reflowable</meta>
                    ${if (hasCover) """<meta name="cover" content="cover_image"/>""" else ""}
                    ${novel.displayAuthor?.takeIf {
            it.isNotBlank()
        }?.let { "<dc:creator>${escapeXml(it)}</dc:creator>" }.orEmpty()}
                    ${novel.displayDescription?.takeIf {
            it.isNotBlank()
        }?.let { "<dc:description>${escapeXml(it)}</dc:description>" }.orEmpty()}
                </metadata>
                <manifest>
                    $manifestItems
                </manifest>
                <spine toc="ncx">
                    <itemref idref="${frontMatterItem.id}"/>
                    $spineItems
                </spine>
            </package>
        """.trimIndent()
    }

    private fun resolveLanguage(novel: Novel): String {
        val raw = sourceManager?.get(novel.source)?.lang?.trim().orEmpty()
        if (raw.isBlank()) return "und"
        if (raw.equals("all", ignoreCase = true)) return "und"
        return raw.replace('_', '-')
    }

    private suspend fun embedChapterImages(
        zip: ZipOutputStream,
        chapterDocument: Document,
        chapterIndex: Int,
        chapterUrl: String?,
        novelUrl: String?,
        manifestAssets: MutableMap<String, EpubAssetItem>,
        imageAssetsByHash: MutableMap<String, EpubAssetItem>,
    ): EpubImageEmbeddingReport {
        var skippedImages = 0
        var newAssets = 0
        val warnings = mutableListOf<String>()
        val baseUrls = buildList {
            chapterUrl?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            novelUrl?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        chapterDocument.select("img[src]").forEachIndexed { imageIndex, image ->
            currentCoroutineContext().ensureActive()
            val src = image.attr("src").trim()
            if (src.isBlank()) {
                skippedImages += 1
                warnings += "Image in chapter $chapterIndex skipped: empty src attribute."
                return@forEachIndexed
            }
            val resolution = assetResolver.resolveBinaryAssetWithReport(src, baseUrls)
            val resolved = resolution.asset
            if (resolved == null) {
                skippedImages += 1
                resolution.warning?.let { warnings += it }
                return@forEachIndexed
            }
            val hash = sha256Hex(resolved.bytes)
            val existing = imageAssetsByHash[hash]
            if (existing != null) {
                image.attr("src", existing.href)
                return@forEachIndexed
            }

            val path = "images/ch${chapterIndex}_${imageIndex + 1}.${resolved.extension}"
            writeEntryBytes(
                zip = zip,
                path = "OEBPS/$path",
                bytes = resolved.bytes,
            )
            val asset = EpubAssetItem(
                id = "img_${chapterIndex}_${imageIndex + 1}",
                href = path,
                mediaType = resolved.mediaType,
            )
            imageAssetsByHash[hash] = asset
            manifestAssets[path] = asset
            image.attr("src", path)
            newAssets += 1
        }
        return EpubImageEmbeddingReport(
            newAssets = newAssets,
            skippedImages = skippedImages,
            warnings = warnings,
        )
    }

    private fun resolveCoverAsset(novel: Novel): EpubAssetResolutionReport {
        // Prefer the user-set custom cover, then the locally cached cover file,
        // and only fall back to fetching the thumbnail URL from the network.
        val localCover = listOfNotNull(
            coverCache?.getCustomCoverFile(novel.id),
            coverCache?.getCoverFile(novel.thumbnailUrl),
        ).firstOrNull { it.exists() && it.length() > 0L }
        if (localCover != null) {
            val resolved = assetResolver.resolveBinaryAssetWithReport(localCover.toURI().toString(), emptyList())
            if (resolved.asset != null) return resolved
        }
        val src = novel.thumbnailUrl?.trim().orEmpty()
        if (src.isBlank()) return EpubAssetResolutionReport(asset = null)
        return assetResolver.resolveBinaryAssetWithReport(src, emptyList())
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { b -> "%02x".format(b) }
    }

    private fun buildChapterDocument(
        title: String,
        body: String,
        styleLink: String,
        scriptTag: String,
        language: String,
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="${escapeXml(language)}" xml:lang="${escapeXml(language)}">
                <head>
                    <title>${escapeXml(title)}</title>
                    <meta charset="UTF-8"/>
                    $styleLink
                </head>
                <body>
                    <h1>${escapeXml(title)}</h1>
                    $body
                    $scriptTag
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildCoverDocument(
        title: String,
        coverHref: String,
        language: String,
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="${escapeXml(language)}" xml:lang="${escapeXml(language)}">
                <head>
                    <title>${escapeXml(title)}</title>
                    <meta charset="UTF-8"/>
                </head>
                <body>
                    <section>
                        <h1>${escapeXml(title)}</h1>
                        <img src="${escapeXml(coverHref)}" alt="${escapeXml(title)} cover"/>
                    </section>
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildTitleDocument(
        novel: Novel,
        language: String,
        modified: String,
    ): String {
        val creator = novel.displayAuthor
            ?.takeIf { it.isNotBlank() }
            ?.let { "<p>${escapeXml(it)}</p>" }
            .orEmpty()
        val description = novel.displayDescription
            ?.takeIf { it.isNotBlank() }
            ?.let { "<section><h2>Description</h2><p>${escapeXml(it)}</p></section>" }
            .orEmpty()
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="${escapeXml(language)}" xml:lang="${escapeXml(language)}">
                <head>
                    <title>${escapeXml(novel.displayTitle)}</title>
                    <meta charset="UTF-8"/>
                </head>
                <body>
                    <section>
                        <h1>${escapeXml(novel.displayTitle)}</h1>
                        $creator
                        <p>Exported: ${escapeXml(modified)}</p>
                        $description
                    </section>
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildNavDocument(
        title: String,
        chapterItems: List<EpubChapterItem>,
        language: String,
        frontMatterItem: EpubFrontMatterItem,
    ): String {
        val navItems = chapterItems.joinToString(separator = "\n") { chapter ->
            """<li><a href="${chapter.fileName}">${escapeXml(chapter.title)}</a></li>"""
        }
        val bodymatterHref = chapterItems.firstOrNull()?.fileName.orEmpty()
        val landmarks = if (bodymatterHref.isNotBlank()) {
            """
                <nav epub:type="landmarks" id="landmarks">
                    <h2>Landmarks</h2>
                    <ol>
                        <li><a epub:type="${frontMatterItem.landmarkType}" href="${frontMatterItem.fileName}">${escapeXml(
                frontMatterItem.landmarkTitle,
            )}</a></li>
                        <li><a epub:type="bodymatter" href="$bodymatterHref">Start reading</a></li>
                    </ol>
                </nav>
            """.trimIndent()
        } else {
            ""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="${escapeXml(
            language,
        )}" xml:lang="${escapeXml(language)}">
                <head>
                    <title>${escapeXml(title)}</title>
                </head>
                <body>
                    <nav epub:type="toc" id="toc">
                        <h1>${escapeXml(title)}</h1>
                        <ol>
                            $navItems
                        </ol>
                    </nav>
                    $landmarks
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildTocDocument(
        title: String,
        chapterItems: List<EpubChapterItem>,
        bookId: String,
        language: String,
    ): String {
        val navPoints = chapterItems.mapIndexed { index, chapter ->
            """
                <navPoint id="${chapter.id}" playOrder="${index + 1}">
                    <navLabel>
                        <text>${escapeXml(chapter.title)}</text>
                    </navLabel>
                    <content src="${chapter.fileName}"/>
                </navPoint>
            """.trimIndent()
        }.joinToString(separator = "\n")
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1" xml:lang="${escapeXml(language)}">
                <head>
                    <meta name="dtb:uid" content="${escapeXml(bookId)}"/>
                    <meta name="dtb:depth" content="1"/>
                    <meta name="dtb:totalPageCount" content="0"/>
                    <meta name="dtb:maxPageNumber" content="0"/>
                </head>
                <docTitle>
                    <text>${escapeXml(title)}</text>
                </docTitle>
                <navMap>
                    $navPoints
                </navMap>
            </ncx>
        """.trimIndent()
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        path: String,
        content: String,
    ) {
        val normalizedContent = content.trimStart().let { trimmed ->
            if (trimmed.startsWith("<?xml")) trimmed else content
        }
        writeEntryBytes(
            zip = zip,
            path = path,
            bytes = normalizedContent.toByteArray(Charsets.UTF_8),
        )
    }

    private fun writeEntryBytes(
        zip: ZipOutputStream,
        path: String,
        bytes: ByteArray,
    ) {
        val entry = ZipEntry(path)
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeStoredEntry(
        zip: ZipOutputStream,
        path: String,
        bytes: ByteArray,
    ) {
        val crc32 = CRC32().apply { update(bytes) }
        val entry = ZipEntry(path).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = crc32.value
            extra = null
            comment = null
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sortChaptersForExport(chapters: List<NovelChapter>): List<NovelChapter> {
        // Lexicographic composition of total-order keys keeps the comparator transitive. A
        // conditional mix of chapterNumber/sourceOrder rules becomes intransitive once
        // unrecognized chapters (-1) interleave with sources whose sourceOrder contradicts the
        // numbering (oldest-first): TimSort then corrupts the book order or throws "Comparison
        // method violates its general contract!". Mirrors the reader convention: ascending
        // chapterNumber (unrecognized first), ties by oldest-first sourceOrder, then id.
        return chapters.sortedWith(
            compareBy<NovelChapter> { it.chapterNumber }
                .thenByDescending { it.sourceOrder }
                .thenBy { it.id },
        )
    }

    private fun stableBookIdentifier(novel: Novel): String {
        return "novel-${novel.id}"
    }

    private fun formatEpubModifiedTimestamp(instant: Instant): String {
        return EPUB_MODIFIED_FORMATTER.format(instant)
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Exports the novel as a single FB2 (FictionBook 2) file.
     *
     * Chapter text is taken from the compiled book artifact when it exists, otherwise from the
     * downloaded chapter files, so the export keeps working after the per-chapter files are cleaned up.
     */
    suspend fun exportAsFb2(
        novel: Novel,
        chapters: List<NovelChapter>,
        options: NovelEpubExportOptions = NovelEpubExportOptions(),
        onProgress: (NovelEpubExportProgress) -> Unit = {},
    ): NovelEpubExportResult {
        val sorted = sortChaptersForExport(chapters)
        val selected = applyRange(sorted, options.startChapter, options.endChapter)
        if (selected.isEmpty()) {
            return NovelEpubExportResult.Failure(
                NovelEpubExportFailure.NO_CHAPTERS_SELECTED,
                NovelEpubExportReport(totalSelected = 0, includedChapters = 0),
            )
        }
        onProgress(NovelEpubExportProgress.Preparing(totalChapters = selected.size))

        val fb2Chapters = mutableListOf<NovelFb2Chapter>()
        val skippedChapters = mutableListOf<String>()
        selected.forEachIndexed { index, chapter ->
            currentCoroutineContext().ensureActive()
            val html = loadChapterHtml(novel, chapter, options.downloadedOnly)
            if (html.isNullOrBlank()) {
                skippedChapters += chapter.name.ifBlank { chapter.url }
            } else {
                fb2Chapters += NovelFb2Chapter(
                    id = "chapter_${index + 1}",
                    title = chapter.name.ifBlank { "#${index + 1}" },
                    html = html,
                )
            }
            onProgress(
                NovelEpubExportProgress.ChapterProcessed(current = index + 1, total = selected.size),
            )
        }

        if (fb2Chapters.isEmpty()) {
            return NovelEpubExportResult.Failure(
                NovelEpubExportFailure.NO_READABLE_CHAPTERS,
                NovelEpubExportReport(
                    totalSelected = selected.size,
                    includedChapters = 0,
                    skippedChapters = skippedChapters,
                ),
            )
        }
        if (options.failOnMissingChapters && skippedChapters.isNotEmpty()) {
            return NovelEpubExportResult.Failure(
                NovelEpubExportFailure.MISSING_SELECTED_CHAPTERS,
                NovelEpubExportReport(
                    totalSelected = selected.size,
                    includedChapters = fb2Chapters.size,
                    skippedChapters = skippedChapters,
                ),
            )
        }

        onProgress(NovelEpubExportProgress.Finalizing)

        val exportDir = File(
            application?.cacheDir ?: return NovelEpubExportResult.Failure(NovelEpubExportFailure.UNKNOWN),
            "exports/novel",
        )
        exportDir.mkdirs()
        val filename = DiskUtil.buildValidFilename("${novel.displayTitle}_${System.currentTimeMillis()}.fb2")
        val fb2File = File(exportDir, filename)

        val writeReport = NovelFb2Writer.writeTo(
            file = fb2File,
            metadata = NovelFb2Metadata(
                title = novel.displayTitle,
                bookId = stableBookIdentifier(novel),
                author = novel.displayAuthor?.takeIf { it.isNotBlank() },
                description = novel.displayDescription?.takeIf { it.isNotBlank() },
                language = resolveLanguage(novel),
                genres = novel.displayGenre.orEmpty(),
                exportedOn = Instant.now().toString().substringBefore('T'),
            ),
            chapters = fb2Chapters,
        )

        val warnings = buildList {
            if (writeReport.skippedImages > 0) {
                add("FB2 does not support remote images: ${writeReport.skippedImages} image(s) were dropped.")
            }
        }
        val report = NovelEpubExportReport(
            totalSelected = selected.size,
            includedChapters = writeReport.chapters,
            skippedChapters = skippedChapters,
            skippedImages = writeReport.skippedImages,
            warnings = warnings,
            outputSizeBytes = fb2File.length(),
        )

        val requestedDestination = options.destinationTreeUri?.takeIf { it.isNotBlank() }
        val destinationUri = requestedDestination?.let { copyToDestinationTree(fb2File, it, FB2_MIME_TYPE) }
        if (requestedDestination != null && destinationUri == null) {
            return NovelEpubExportResult.Failure(
                NovelEpubExportFailure.DESTINATION_PERMISSION_DENIED,
                report,
            )
        }

        onProgress(NovelEpubExportProgress.Done(fb2File))
        return NovelEpubExportResult.Success(
            cacheFile = fb2File,
            destinationUri = destinationUri,
            report = report,
        )
    }

    private fun copyToDestinationTree(
        epubFile: File,
        destinationTreeUri: String,
        mimeType: String = EPUB_MIME_TYPE,
    ): Uri? {
        val context = application ?: return null
        val treeUri = runCatching { Uri.parse(destinationTreeUri) }.getOrNull() ?: return null
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val target = root.createFile(mimeType, epubFile.name) ?: return null

        val copied = runCatching {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                epubFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } != null
        }.getOrDefault(false)
        return target.uri.takeIf { copied }
    }

    private data class EpubChapterItem(
        val id: String,
        val fileName: String,
        val title: String,
    )

    private data class EpubFrontMatterItem(
        val id: String,
        val fileName: String,
        val landmarkType: String,
        val landmarkTitle: String,
    )

    private data class ChapterPayload(
        val chapter: NovelChapter,
        val html: String,
    )

    private data class EpubAssetItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String? = null,
    )

    private data class EpubImageEmbeddingReport(
        val newAssets: Int,
        val skippedImages: Int,
        val warnings: List<String> = emptyList(),
    )

    private companion object {
        const val EPUB_MIME_TYPE = "application/epub+zip"
        const val FB2_MIME_TYPE = "application/x-fictionbook+xml"

        val EPUB_MODIFIED_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
    }
}
