package eu.kanade.tachiyomi.data.export.novel

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import eu.kanade.domain.items.novelchapter.model.toSNovelChapter
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.util.storage.DiskUtil
import org.jsoup.Jsoup
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.security.MessageDigest
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
)

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
) {

    suspend fun export(
        novel: Novel,
        chapters: List<NovelChapter>,
        options: NovelEpubExportOptions = NovelEpubExportOptions(),
        onProgress: (NovelEpubExportProgress) -> Unit = {},
    ): File? {
        val sorted = chapters.sortedBy { it.sourceOrder }
        val selected = applyRange(sorted, options.startChapter, options.endChapter)
        if (selected.isEmpty()) return null
        onProgress(NovelEpubExportProgress.Preparing(totalChapters = selected.size))

        val chapterPayloads = selected.mapNotNull { chapter ->
            val html = loadChapterHtml(novel, chapter, options.downloadedOnly) ?: return@mapNotNull null
            ChapterPayload(
                chapter = chapter,
                html = html,
            )
        }
        if (chapterPayloads.isEmpty()) return null

        val exportDir = File(application?.cacheDir ?: return null, "exports/novel")
        exportDir.mkdirs()
        val filename = DiskUtil.buildValidFilename("${novel.title}_${System.currentTimeMillis()}.epub")
        val epubFile = File(exportDir, filename)
        val epubLanguage = resolveLanguage(novel)

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

            val coverAsset = resolveCoverAsset(novel)?.let { cover ->
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

            val chapterItems = chapterPayloads.mapIndexed { index, payload ->
                val fileName = "chapter_${index + 1}.xhtml"
                val chapterId = "chapter_${index + 1}"
                val chapterTitle = payload.chapter.name.ifBlank {
                    "Chapter ${index + 1}"
                }
                val chapterDocument = Jsoup.parseBodyFragment(payload.html)
                embedChapterImages(
                    zip = zip,
                    chapterDocument = chapterDocument,
                    chapterIndex = index + 1,
                    chapterUrl = payload.chapter.url,
                    novelUrl = novel.url,
                    manifestAssets = manifestAssets,
                    imageAssetsByHash = imageAssetsByHash,
                )
                val chapterBody = chapterDocument.body().html()
                val styleLink = stylesheetPath?.let { path ->
                    """<link rel="stylesheet" href="$path" type="text/css"/>"""
                }.orEmpty()
                val scriptTag = scriptPath?.let { path ->
                    """<script src="$path" type="text/javascript"></script>"""
                }.orEmpty()
                writeEntry(
                    zip = zip,
                    path = "OEBPS/$fileName",
                    content = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                            <head>
                                <title>${escapeXml(chapterTitle)}</title>
                                <meta charset="UTF-8"/>
                                $styleLink
                            </head>
                            <body>
                                <h1>${escapeXml(chapterTitle)}</h1>
                                $chapterBody
                                $scriptTag
                            </body>
                        </html>
                    """.trimIndent(),
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
                content = buildNavDocument(novel.title, chapterItems),
            )
            writeEntry(
                zip = zip,
                path = "OEBPS/toc.ncx",
                content = buildTocDocument(novel.title, chapterItems),
            )
            writeEntry(
                zip = zip,
                path = "OEBPS/content.opf",
                content = buildPackageDocument(
                    novel = novel,
                    chapterItems = chapterItems,
                    language = epubLanguage,
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
            if (!copied) return null
        }

        onProgress(NovelEpubExportProgress.Done(epubFile))
        return epubFile.takeIf { it.exists() }
    }

    private suspend fun loadChapterHtml(
        novel: Novel,
        chapter: NovelChapter,
        downloadedOnly: Boolean,
    ): String? {
        val downloaded = downloadManager.getDownloadedChapterText(novel, chapter.id)
        if (downloaded != null) return downloaded
        if (downloadedOnly) return null
        val source = sourceManager?.get(novel.source) ?: return null
        return runCatching { source.getChapterText(chapter.toSNovelChapter()) }.getOrNull()
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
        additionalAssets: List<EpubAssetItem>,
        hasCover: Boolean,
    ): String {
        val manifestItems = buildString {
            appendLine("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            appendLine("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
            chapterItems.forEach { chapter ->
                appendLine(
                    """<item id="${chapter.id}" href="${chapter.fileName}" media-type="application/xhtml+xml"/>""",
                )
            }
            additionalAssets.forEach { asset ->
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
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="bookid">novel-${novel.id}</dc:identifier>
                    <dc:title>${escapeXml(novel.title)}</dc:title>
                    <dc:language>$language</dc:language>
                    ${if (hasCover) """<meta name="cover" content="cover_image"/>""" else ""}
                    ${novel.author?.takeIf {
            it.isNotBlank()
        }?.let { "<dc:creator>${escapeXml(it)}</dc:creator>" }.orEmpty()}
                    ${novel.description?.takeIf {
            it.isNotBlank()
        }?.let { "<dc:description>${escapeXml(it)}</dc:description>" }.orEmpty()}
                </metadata>
                <manifest>
                    $manifestItems
                </manifest>
                <spine toc="ncx">
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

    private fun embedChapterImages(
        zip: ZipOutputStream,
        chapterDocument: org.jsoup.nodes.Document,
        chapterIndex: Int,
        chapterUrl: String?,
        novelUrl: String?,
        manifestAssets: MutableMap<String, EpubAssetItem>,
        imageAssetsByHash: MutableMap<String, EpubAssetItem>,
    ) {
        val baseUrls = buildList {
            chapterUrl?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            novelUrl?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        chapterDocument.select("img[src]").forEachIndexed { imageIndex, image ->
            val src = image.attr("src").trim()
            if (src.isBlank()) return@forEachIndexed
            val resolved = resolveBinaryAsset(src, baseUrls) ?: return@forEachIndexed
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
                id = "img_${manifestAssets.size + 1}",
                href = path,
                mediaType = resolved.mediaType,
            )
            imageAssetsByHash[hash] = asset
            manifestAssets[path] = asset
            image.attr("src", path)
        }
    }

    private fun resolveCoverAsset(novel: Novel): BinaryAsset? {
        val src = novel.thumbnailUrl?.trim().orEmpty()
        if (src.isBlank()) return null
        return resolveBinaryAsset(src, emptyList())
    }

    private fun resolveBinaryAsset(
        src: String,
        baseUrls: List<String>,
    ): BinaryAsset? {
        val directUri = runCatching { URI(src) }.getOrNull()
        val isAbsolute = directUri?.isAbsolute == true
        val candidates = buildList {
            if (isAbsolute) {
                add(src)
            } else {
                baseUrls.forEach { base ->
                    runCatching {
                        val baseUri = URI(base)
                        if (baseUri.isAbsolute) {
                            add(baseUri.resolve(src).toString())
                        }
                    }
                }
            }
        }.ifEmpty { listOf(src) }

        candidates.forEach { candidate ->
            resolveAbsoluteBinaryAsset(candidate)?.let { return it }
        }
        return null
    }

    private fun resolveAbsoluteBinaryAsset(src: String): BinaryAsset? {
        val uri = runCatching { URI(src) }.getOrNull()
        return when {
            src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true) -> {
                runCatching {
                    URL(src).openConnection().getInputStream().use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.isEmpty()) return@use null
                        val mediaType = URLConnection.guessContentTypeFromStream(bytes.inputStream())
                            ?: URLConnection.guessContentTypeFromName(src)
                            ?: "image/jpeg"
                        val ext = extensionFromMediaType(mediaType)
                        BinaryAsset(bytes = bytes, mediaType = mediaType, extension = ext)
                    }
                }.getOrNull()
            }
            uri?.scheme.equals("file", ignoreCase = true) -> {
                runCatching {
                    val file = File(uri)
                    if (!file.exists()) return@runCatching null
                    val bytes = file.readBytes()
                    if (bytes.isEmpty()) return@runCatching null
                    val mediaType = URLConnection.guessContentTypeFromName(file.name) ?: "image/jpeg"
                    val ext = extensionFromMediaType(mediaType, file.extension)
                    BinaryAsset(bytes = bytes, mediaType = mediaType, extension = ext)
                }.getOrNull()
            }
            else -> null
        }
    }

    private fun extensionFromMediaType(
        mediaType: String,
        fallback: String? = null,
    ): String {
        return when (mediaType.substringBefore(';').trim().lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/svg+xml" -> "svg"
            else -> fallback?.ifBlank { null } ?: "jpg"
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { b -> "%02x".format(b) }
    }

    private fun buildNavDocument(
        title: String,
        chapterItems: List<EpubChapterItem>,
    ): String {
        val navItems = chapterItems.joinToString(separator = "\n") { chapter ->
            """<li><a href="${chapter.fileName}">${escapeXml(chapter.title)}</a></li>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <title>${escapeXml(title)}</title>
                </head>
                <body>
                    <nav epub:type="toc" xmlns:epub="http://www.idpf.org/2007/ops">
                        <h1>${escapeXml(title)}</h1>
                        <ol>
                            $navItems
                        </ol>
                    </nav>
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildTocDocument(
        title: String,
        chapterItems: List<EpubChapterItem>,
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
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <head>
                    <meta name="dtb:uid" content="$title"/>
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
        writeEntryBytes(
            zip = zip,
            path = path,
            bytes = content.toByteArray(Charsets.UTF_8),
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
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun copyToDestinationTree(
        epubFile: File,
        destinationTreeUri: String,
    ): Boolean {
        val context = application ?: return false
        val treeUri = runCatching { Uri.parse(destinationTreeUri) }.getOrNull() ?: return false
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val target = root.createFile("application/epub+zip", epubFile.name) ?: return false

        return runCatching {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                epubFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } != null
        }.getOrDefault(false)
    }

    private data class EpubChapterItem(
        val id: String,
        val fileName: String,
        val title: String,
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

    private data class BinaryAsset(
        val bytes: ByteArray,
        val mediaType: String,
        val extension: String,
    )
}
