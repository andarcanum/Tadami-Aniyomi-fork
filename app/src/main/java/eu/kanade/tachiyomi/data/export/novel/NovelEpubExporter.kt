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

class NovelEpubExporter(
    private val application: Application? = runCatching { Injekt.get<Application>() }.getOrNull(),
    private val sourceManager: NovelSourceManager? = runCatching { Injekt.get<NovelSourceManager>() }.getOrNull(),
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
) {

    suspend fun export(
        novel: Novel,
        chapters: List<NovelChapter>,
        options: NovelEpubExportOptions = NovelEpubExportOptions(),
    ): File? {
        val sorted = chapters.sortedBy { it.sourceOrder }
        val selected = applyRange(sorted, options.startChapter, options.endChapter)
        if (selected.isEmpty()) return null

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

            val chapterItems = chapterPayloads.mapIndexed { index, payload ->
                val fileName = "chapter_${index + 1}.xhtml"
                val chapterId = "chapter_${index + 1}"
                val chapterTitle = payload.chapter.name.ifBlank {
                    "Chapter ${index + 1}"
                }
                val chapterBody = Jsoup.parseBodyFragment(payload.html).body().html()
                val styleBlock = options.stylesheet
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "<style type=\"text/css\">\n$it\n</style>" }
                    .orEmpty()
                val scriptBlock = options.javaScript
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        """
                        <script type="text/javascript">
                        //<![CDATA[
                        $it
                        //]]>
                        </script>
                        """.trimIndent()
                    }
                    .orEmpty()
                writeEntry(
                    zip = zip,
                    path = "OEBPS/$fileName",
                    content = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                            <head>
                                <title>${escapeXml(chapterTitle)}</title>
                                <meta charset="UTF-8"/>
                                $styleBlock
                            </head>
                            <body>
                                <h1>${escapeXml(chapterTitle)}</h1>
                                $chapterBody
                                $scriptBlock
                            </body>
                        </html>
                    """.trimIndent(),
                )
                EpubChapterItem(
                    id = chapterId,
                    fileName = fileName,
                    title = chapterTitle,
                )
            }

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
                content = buildPackageDocument(novel, chapterItems),
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
    ): String {
        val manifestItems = buildString {
            appendLine("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            appendLine("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
            chapterItems.forEach { chapter ->
                appendLine(
                    """<item id="${chapter.id}" href="${chapter.fileName}" media-type="application/xhtml+xml"/>""",
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
                    <dc:language>ru</dc:language>
                    ${novel.author?.takeIf { it.isNotBlank() }?.let { "<dc:creator>${escapeXml(it)}</dc:creator>" }.orEmpty()}
                    ${novel.description?.takeIf { it.isNotBlank() }?.let { "<dc:description>${escapeXml(it)}</dc:description>" }.orEmpty()}
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
        val bytes = content.toByteArray(Charsets.UTF_8)
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
}
