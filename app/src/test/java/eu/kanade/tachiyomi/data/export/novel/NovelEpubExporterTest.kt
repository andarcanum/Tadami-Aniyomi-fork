package eu.kanade.tachiyomi.data.export.novel

import android.app.Application
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import mihon.core.archive.ArchiveReader
import mihon.core.archive.EpubReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class NovelEpubExporterTest {

    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `export writes shared css and js assets instead of per-chapter inline blocks`() {
        runBlocking {
            val epub = exportEpub(
                chapterHtml = "<p>Hello</p>",
                sourceLang = "en",
                options = NovelEpubExportOptions(
                    stylesheet = "body { color: red; }",
                    javaScript = "console.log('test');",
                ),
            )

            val chapter = readZipText(epub, "OEBPS/chapter_1.xhtml")
            chapter.shouldContain("href=\"styles/reader.css\"")
            chapter.shouldContain("src=\"scripts/reader.js\"")
            chapter.shouldNotContain("<style type=\"text/css\">")
            chapter.shouldNotContain("<script type=\"text/javascript\">")
            readZipText(epub, "OEBPS/styles/reader.css").shouldContain("color: red")
            readZipText(epub, "OEBPS/scripts/reader.js").shouldContain("console.log('test')")
        }
    }

    @Test
    fun `export uses source language in opf metadata`() {
        runBlocking {
            val epub = exportEpub(
                chapterHtml = "<p>Hello</p>",
                sourceLang = "en",
            )

            val opf = readZipText(epub, "OEBPS/content.opf")
            opf.shouldContain("<dc:language>en</dc:language>")
            opf.shouldNotContain("<dc:language>ru</dc:language>")
        }
    }

    @Test
    fun `export falls back to und language when source language is unavailable`() {
        runBlocking {
            val epub = exportEpub(
                chapterHtml = "<p>Hello</p>",
                sourceLang = null,
            )

            val opf = readZipText(epub, "OEBPS/content.opf")
            opf.shouldContain("<dc:language>und</dc:language>")
            opf.shouldNotContain("<dc:language>ru</dc:language>")
        }
    }

    @Test
    fun `export writes epub 3 metadata and matching ncx identifier`() {
        runBlocking {
            val epub = exportEpub(
                chapterHtml = "<p>Hello</p>",
                sourceLang = "en",
            )

            val opf = readZipText(epub, "OEBPS/content.opf")
            val ncx = readZipText(epub, "OEBPS/toc.ncx")
            val nav = readZipText(epub, "OEBPS/nav.xhtml")

            opf.shouldContain("""unique-identifier="bookid"""")
            opf.shouldContain(
                """prefix="dcterms: http://purl.org/dc/terms/ rendition: http://www.idpf.org/vocab/rendition/#"""",
            )
            opf.shouldContain("""<dc:identifier id="bookid">novel-1</dc:identifier>""")
            opf.shouldContain("""<meta property="dcterms:modified">""")
            opf.shouldContain("""<meta property="rendition:layout">reflowable</meta>""")
            ncx.shouldContain("""<meta name="dtb:uid" content="novel-1"/>""")
            nav.shouldContain("""xmlns:epub="http://www.idpf.org/2007/ops"""")
            nav.shouldContain("""epub:type="landmarks"""")
            nav.shouldContain("""epub:type="bodymatter"""")
        }
    }

    @Test
    fun `export writes mimetype as first stored zip entry without extra field`() {
        runBlocking {
            val epub = exportEpub(
                chapterHtml = "<p>Hello</p>",
                sourceLang = "en",
            )

            val header = epub.readBytes()
            readLittleEndianInt(header, 0) shouldBe 0x04034b50
            readLittleEndianShort(header, 8) shouldBe 0
            val nameLength = readLittleEndianShort(header, 26)
            val extraLength = readLittleEndianShort(header, 28)
            val name = header.copyOfRange(30, 30 + nameLength).toString(Charsets.UTF_8)
            name shouldBe "mimetype"
            extraLength shouldBe 0
            val contentStart = 30 + nameLength + extraLength
            val contentEnd = contentStart + "application/epub+zip".length
            header.copyOfRange(contentStart, contentEnd).toString(Charsets.UTF_8) shouldBe "application/epub+zip"
            listZipEntries(epub).first() shouldBe "mimetype"
        }
    }

    @Test
    fun `exported xml and xhtml files are parseable as xml`() {
        runBlocking {
            val epub = exportEpub(
                chapterHtml = """<p>AT&T&nbsp;text<br><img src="missing.jpg"></p><script>alert(1)</script>""",
                sourceLang = "en",
            )

            listOf(
                "OEBPS/content.opf",
                "OEBPS/nav.xhtml",
                "OEBPS/toc.ncx",
                "OEBPS/title.xhtml",
                "OEBPS/chapter_1.xhtml",
            ).forEach { path ->
                parseXml(readZipText(epub, path))
            }
            val chapter = readZipText(epub, "OEBPS/chapter_1.xhtml")
            chapter.shouldNotContain("<script")
            chapter.shouldNotContain("&nbsp;")
        }
    }

    @Test
    fun `export writes title page when cover is unavailable`() {
        runBlocking {
            val epub = exportEpub(
                chapterHtml = "<p>Hello</p>",
                sourceLang = "en",
            )

            val opf = readZipText(epub, "OEBPS/content.opf")
            val nav = readZipText(epub, "OEBPS/nav.xhtml")
            opf.shouldContain("href=\"title.xhtml\"")
            opf.shouldContain("""<itemref idref="title_page"/>""")
            nav.shouldContain("epub:type=\"titlepage\"")
            listZipEntries(epub).any { it == "OEBPS/title.xhtml" } shouldBe true
        }
    }

    @Test
    fun `export embeds cover image and declares cover metadata`() {
        runBlocking {
            val cover = tempDir.resolve("cover.jpg").toFile().apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val novel = Novel.create().copy(
                id = 1L,
                source = 10L,
                title = "Novel",
                thumbnailUrl = cover.toURI().toString(),
            )

            val epub = exportEpub(
                chapterHtml = "<p>Hello</p>",
                sourceLang = "en",
                novel = novel,
            )

            val opf = readZipText(epub, "OEBPS/content.opf")
            val nav = readZipText(epub, "OEBPS/nav.xhtml")
            opf.shouldContain("cover-image")
            opf.shouldContain("href=\"cover.xhtml\"")
            opf.shouldContain("""<itemref idref="cover_page"/>""")
            nav.shouldContain("epub:type=\"cover\"")
            listZipEntries(epub).any { it.startsWith("OEBPS/images/cover") } shouldBe true
            listZipEntries(epub).any { it == "OEBPS/cover.xhtml" } shouldBe true
        }
    }

    @Test
    fun `export embeds webp cover and chapter images`() {
        runBlocking {
            val cover = tempDir.resolve("cover.webp").toFile().apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val illustration = tempDir.resolve("illustration.webp").toFile().apply {
                writeBytes(byteArrayOf(9, 8, 7, 6))
            }
            val novel = Novel.create().copy(
                id = 1L,
                source = 10L,
                title = "Novel",
                thumbnailUrl = cover.toURI().toString(),
            )

            val epub = exportEpub(
                chapterHtml = """<p>Hello</p><img src="${illustration.toURI()}"/>""",
                sourceLang = "en",
                novel = novel,
            )

            val opf = readZipText(epub, "OEBPS/content.opf")
            val chapter = readZipText(epub, "OEBPS/chapter_1.xhtml")
            opf.shouldContain("media-type=\"image/webp\"")
            chapter.shouldContain(".webp")
            listZipEntries(epub).any { it == "OEBPS/images/cover.webp" } shouldBe true
            listZipEntries(epub).any { it.startsWith("OEBPS/images/ch1_") && it.endsWith(".webp") } shouldBe true
        }
    }

    @Test
    fun `export embeds chapter images and rewrites image src`() {
        runBlocking {
            val image = tempDir.resolve("illustration.jpg").toFile().apply {
                writeBytes(byteArrayOf(9, 8, 7, 6))
            }
            val chapterHtml = """<p>Text</p><img src="${image.toURI()}"/>"""

            val epub = exportEpub(
                chapterHtml = chapterHtml,
                sourceLang = "en",
            )

            val chapter = readZipText(epub, "OEBPS/chapter_1.xhtml")
            chapter.shouldContain("""<img src="images/""")
            listZipEntries(epub).filter { it.startsWith("OEBPS/images/") }.shouldNotBeEmpty()
        }
    }

    @Test
    fun `export resolves relative image src using chapter url as base`() {
        runBlocking {
            val contentDir = tempDir.resolve("content").toFile().apply { mkdirs() }
            val chapterDir = File(contentDir, "chapters").apply { mkdirs() }
            val chapterFile = File(chapterDir, "index.html").apply { writeText("<html/>") }
            val relativeImage = File(contentDir, "images/illustration.jpg").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(5, 4, 3, 2))
            }
            val chapterHtml = """<p>Text</p><img src="../images/illustration.jpg"/>"""

            val epub = exportEpub(
                chapterHtml = chapterHtml,
                sourceLang = "en",
                chapterUrl = chapterFile.toURI().toString(),
            )

            val chapter = readZipText(epub, "OEBPS/chapter_1.xhtml")
            chapter.shouldContain("""<img src="images/""")
            listZipEntries(epub).any { it.startsWith("OEBPS/images/ch1_") } shouldBe true
        }
    }

    @Test
    fun `exportWithResult reports embedded and skipped images`() {
        runBlocking {
            val image = tempDir.resolve("illustration.jpg").toFile().apply {
                writeBytes(byteArrayOf(9, 8, 7, 6))
            }
            val chapterHtml = """
                <p>Text</p>
                <img src="${image.toURI()}"/>
                <img src="missing.webp"/>
            """.trimIndent()

            val result = exportEpubResult(
                chapterHtml = chapterHtml,
                sourceLang = "en",
            ) as NovelEpubExportResult.Success

            result.report.embeddedImages shouldBe 1
            result.report.skippedImages shouldBe 1
            result.report.warnings.any { it.contains("missing.webp") } shouldBe true
            val chapter = readZipText(result.cacheFile, "OEBPS/chapter_1.xhtml")
            chapter.shouldContain("""<img src="images/""")
            listZipEntries(result.cacheFile).filter { it.startsWith("OEBPS/images/") }.size shouldBe 1
        }
    }

    @Test
    fun `exportWithResult reports unsupported image format warning`() {
        runBlocking {
            val image = tempDir.resolve("illustration.bmp").toFile().apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }

            val result = exportEpubResult(
                chapterHtml = """<p>Text</p><img src="${image.toURI()}"/>""",
                sourceLang = "en",
            ) as NovelEpubExportResult.Success

            result.report.embeddedImages shouldBe 0
            result.report.skippedImages shouldBe 1
            result.report.warnings.any { it.contains("unsupported format image/bmp") } shouldBe true
        }
    }

    @Test
    fun `exportWithResult reports experimental custom javascript warning`() {
        runBlocking {
            val result = exportEpubResult(
                chapterHtml = "<p>Hello</p>",
                sourceLang = "en",
                options = NovelEpubExportOptions(javaScript = "window.test = true;"),
            ) as NovelEpubExportResult.Success

            result.report.warnings shouldBe listOf(
                "Custom JavaScript is experimental and may be ignored by EPUB readers.",
            )
        }
    }

    @Test
    fun `exportWithResult fails with report when selected downloaded chapter is missing in strict mode`() {
        runBlocking {
            val cacheDir = tempDir.resolve("cache-missing").toFile().apply { mkdirs() }
            val application = mockk<Application>()
            every { application.cacheDir } returns cacheDir

            val downloadManager = mockk<eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager>()
            every { downloadManager.getDownloadedChapterText(any(), any()) } returns null

            val exporter = NovelEpubExporter(
                application = application,
                sourceManager = null,
                downloadManager = downloadManager,
            )
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 11L,
                novelId = novel.id,
                sourceOrder = 1L,
                url = "/chapter-1",
                name = "Chapter 1",
            )

            val result = exporter.exportWithResult(
                novel = novel,
                chapters = listOf(chapter),
                options = NovelEpubExportOptions(
                    downloadedOnly = true,
                    failOnMissingChapters = true,
                ),
            )

            result shouldBe NovelEpubExportResult.Failure(
                reason = NovelEpubExportFailure.NO_READABLE_CHAPTERS,
                report = NovelEpubExportReport(
                    totalSelected = 1,
                    includedChapters = 0,
                    skippedChapters = listOf("Chapter 1"),
                ),
            )
        }
    }

    @Test
    fun `exported epub can be read back by bundled epub reader`() {
        runBlocking {
            val epub = exportEpub(
                chapterHtml = "<p>Round trip text</p>",
                sourceLang = "en",
            )

            ZipFile(epub).use { zip ->
                val archiveReader = mockk<ArchiveReader>(relaxed = true)
                every { archiveReader.getInputStream(any()) } answers {
                    val entry = zip.getEntry(firstArg<String>()) ?: return@answers null
                    zip.getInputStream(entry)
                }

                EpubReader(archiveReader).use { reader ->
                    val toc = reader.getTableOfContents()
                    toc.map { it.title } shouldBe listOf("Chapter 1")
                    reader.getSpinePageHrefs().any { it.endsWith("chapter_1.xhtml") } shouldBe true
                    reader.getChapterContent(toc.first().href).shouldContain("Round trip text")
                }
            }
        }
    }

    @Test
    fun `export sorts chapters in ascending reading order even when source returns newest first`() {
        runBlocking {
            val cacheDir = tempDir.resolve("cache-order").toFile().apply { mkdirs() }
            val application = mockk<Application>()
            every { application.cacheDir } returns cacheDir

            val downloadManager = mockk<eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager>()
            every { downloadManager.getDownloadedChapterText(any(), any()) } answers {
                val chId = secondArg<Long>()
                val chName = if (chId == 101L) "Chapter 1" else "Chapter 2"
                "<p>$chName content</p>"
            }

            val exporter = NovelEpubExporter(
                application = application,
                sourceManager = null,
                downloadManager = downloadManager,
            )

            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Test Novel")
            // Newest chapter has sourceOrder = 0, oldest has sourceOrder = 10
            val chapter1 = NovelChapter.create().copy(
                id = 101L,
                novelId = novel.id,
                chapterNumber = 1.0,
                sourceOrder = 10L,
                url = "/ch1",
                name = "Chapter 1",
            )
            val chapter2 = NovelChapter.create().copy(
                id = 102L,
                novelId = novel.id,
                chapterNumber = 2.0,
                sourceOrder = 0L,
                url = "/ch2",
                name = "Chapter 2",
            )

            val result = exporter.exportWithResult(
                novel = novel,
                // Input list in reverse order (newest first)
                chapters = listOf(chapter2, chapter1),
            ) as NovelEpubExportResult.Success

            val navXhtml = readZipText(result.cacheFile, "OEBPS/nav.xhtml")
            val indexCh1 = navXhtml.indexOf("Chapter 1")
            val indexCh2 = navXhtml.indexOf("Chapter 2")
            (indexCh1 < indexCh2) shouldBe true

            val ch1Content = readZipText(result.cacheFile, "OEBPS/chapter_1.xhtml")
            ch1Content.shouldContain("Chapter 1 content")
            val ch2Content = readZipText(result.cacheFile, "OEBPS/chapter_2.xhtml")
            ch2Content.shouldContain("Chapter 2 content")
        }
    }

    @Test
    fun `export keeps a consistent order when unrecognized chapters mix with oldest-first sources`() {
        runBlocking {
            val cacheDir = tempDir.resolve("cache-mixed-order").toFile().apply { mkdirs() }
            val application = mockk<Application>()
            every { application.cacheDir } returns cacheDir

            // Oldest-first source (e.g. Jaomix pages): chapter N carries sourceOrder = N - 1, so
            // ascending numbers contradict descending sourceOrder. A comparator that mixes the two
            // rules conditionally becomes intransitive once an unrecognized chapter interleaves:
            // it either throws "Comparison method violates its general contract!" or silently
            // buries the unrecognized chapter mid-book instead of the reader-convention position.
            val recognized = (1..33).map { number ->
                NovelChapter.create().copy(
                    id = number.toLong(),
                    novelId = 1L,
                    chapterNumber = number.toDouble(),
                    sourceOrder = (number - 1).toLong(),
                    url = "/ch$number",
                    name = "Chapter %03d".format(number),
                )
            }
            val extra = NovelChapter.create().copy(
                id = 999L,
                novelId = 1L,
                chapterNumber = -1.0,
                sourceOrder = 8L,
                url = "/extra",
                name = "Extra SS",
            )
            val titlesById = (recognized + extra).associate { it.id to it.name }

            val downloadManager = mockk<eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager>()
            every { downloadManager.getDownloadedChapterText(any(), any()) } answers {
                "<p>${titlesById[secondArg<Long>()]} content</p>"
            }

            val exporter = NovelEpubExporter(
                application = application,
                sourceManager = null,
                downloadManager = downloadManager,
            )
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Mixed Order Novel")

            val result = exporter.exportWithResult(
                novel = novel,
                chapters = listOf(extra) + recognized.shuffled(java.util.Random(0)),
            ) as NovelEpubExportResult.Success

            // Reading order: the unrecognized chapter sorts first (chapterNumber = -1, same
            // convention as the reader's novelReadingOrderComparator), then chapters 1..100.
            val nav = readZipText(result.cacheFile, "OEBPS/nav.xhtml")
            val positions = (listOf(extra) + recognized).map { nav.indexOf(it.name) }
            positions.forEach { (it >= 0) shouldBe true }
            positions shouldBe positions.sorted()
        }
    }

    @Test
    fun `export uses custom title, author, and description in metadata and filenames`() {
        runBlocking {
            val cacheDir = tempDir.resolve("cache-meta").toFile().apply { mkdirs() }
            val application = mockk<Application>()
            every { application.cacheDir } returns cacheDir

            val downloadManager = mockk<eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager>()
            every { downloadManager.getDownloadedChapterText(any(), any()) } returns "<p>Content</p>"

            val exporter = NovelEpubExporter(
                application = application,
                sourceManager = null,
                downloadManager = downloadManager,
            )

            val novel = Novel.create().copy(
                id = 1L,
                source = 10L,
                title = "Original Title",
                customTitle = "Custom Novel Title",
                author = "Orig Author",
                customAuthor = "Custom Author",
                description = "Orig Description",
                customDescription = "Custom Description",
            )
            val chapter = NovelChapter.create().copy(
                id = 1L,
                novelId = novel.id,
                chapterNumber = 1.0,
                sourceOrder = 0L,
                url = "/ch1",
                name = "Chapter 1",
            )

            val result = exporter.exportWithResult(
                novel = novel,
                chapters = listOf(chapter),
            ) as NovelEpubExportResult.Success

            result.cacheFile.name.startsWith("Custom Novel Title_") shouldBe true

            val opf = readZipText(result.cacheFile, "OEBPS/content.opf")
            opf.shouldContain("<dc:title>Custom Novel Title</dc:title>")
            opf.shouldContain("<dc:creator>Custom Author</dc:creator>")
            opf.shouldContain("<dc:description>Custom Description</dc:description>")

            val nav = readZipText(result.cacheFile, "OEBPS/nav.xhtml")
            nav.shouldContain("Custom Novel Title")
        }
    }

    @Test
    fun `export reports progress from preparing to done`() {
        runBlocking {
            val progress = mutableListOf<NovelEpubExportProgress>()

            val epub = exportEpub(
                chapterHtml = "<p>Hello</p>",
                sourceLang = "en",
                onProgress = { progress += it },
            )

            epub.exists() shouldBe true
            progress.first() shouldBe NovelEpubExportProgress.Preparing(totalChapters = 1)
            progress.any {
                it == NovelEpubExportProgress.ChapterProcessed(
                    current = 1,
                    total = 1,
                )
            } shouldBe true
            progress.any { it == NovelEpubExportProgress.Finalizing } shouldBe true
            (progress.last() is NovelEpubExportProgress.Done) shouldBe true
        }
    }

    private suspend fun exportEpub(
        chapterHtml: String,
        sourceLang: String?,
        novel: Novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel"),
        options: NovelEpubExportOptions = NovelEpubExportOptions(),
        chapterUrl: String = "/chapter-1",
        onProgress: (NovelEpubExportProgress) -> Unit = {},
    ): File {
        val result = exportEpubResult(
            chapterHtml = chapterHtml,
            sourceLang = sourceLang,
            novel = novel,
            options = options,
            chapterUrl = chapterUrl,
            onProgress = onProgress,
        )
        return (result as NovelEpubExportResult.Success).cacheFile
    }

    private suspend fun exportEpubResult(
        chapterHtml: String,
        sourceLang: String?,
        novel: Novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel"),
        options: NovelEpubExportOptions = NovelEpubExportOptions(),
        chapterUrl: String = "/chapter-1",
        onProgress: (NovelEpubExportProgress) -> Unit = {},
    ): NovelEpubExportResult {
        val cacheDir = tempDir.resolve("cache").toFile().apply { mkdirs() }
        val application = mockk<Application>()
        every { application.cacheDir } returns cacheDir

        val downloadManager = mockk<eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager>()
        every { downloadManager.getDownloadedChapterText(any(), any()) } returns chapterHtml

        val sourceManager = sourceLang?.let { lang ->
            mockk<NovelSourceManager>().also { manager ->
                every { manager.get(any()) } returns FakeNovelSource(lang)
            }
        }

        val exporter = NovelEpubExporter(
            application = application,
            sourceManager = sourceManager,
            downloadManager = downloadManager,
        )

        val chapter = NovelChapter.create().copy(
            id = 11L,
            novelId = novel.id,
            sourceOrder = 1L,
            url = chapterUrl,
            name = "Chapter 1",
        )
        return exporter.exportWithResult(
            novel = novel,
            chapters = listOf(chapter),
            options = options,
            onProgress = onProgress,
        )
    }

    private fun readZipText(epubFile: File, path: String): String {
        ZipFile(epubFile).use { zip ->
            val entry = zip.getEntry(path).shouldNotBeNull()
            zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { return it.readText() }
        }
    }

    private fun listZipEntries(epubFile: File): List<String> {
        ZipFile(epubFile).use { zip ->
            return zip.entries().asSequence().map { it.name }.toList()
        }
    }

    private fun readLittleEndianShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun parseXml(xml: String) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
    }

    private class FakeNovelSource(
        override val lang: String,
    ) : eu.kanade.tachiyomi.novelsource.NovelSource {
        override val id: Long = 10L
        override val name: String = "Test Source"

        override suspend fun getNovelDetails(
            novel: eu.kanade.tachiyomi.novelsource.model.SNovel,
        ) = novel

        override suspend fun getChapterList(
            novel: eu.kanade.tachiyomi.novelsource.model.SNovel,
        ) = emptyList<eu.kanade.tachiyomi.novelsource.model.SNovelChapter>()

        override suspend fun getChapterText(
            chapter: eu.kanade.tachiyomi.novelsource.model.SNovelChapter,
        ) = ""
    }
}
