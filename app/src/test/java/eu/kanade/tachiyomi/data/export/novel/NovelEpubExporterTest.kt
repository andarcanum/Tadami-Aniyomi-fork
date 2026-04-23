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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile

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
            opf.shouldContain("cover-image")
            listZipEntries(epub).any { it.startsWith("OEBPS/images/cover") } shouldBe true
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
        return exporter.export(
            novel = novel,
            chapters = listOf(chapter),
            options = options,
            onProgress = onProgress,
        ).shouldNotBeNull()
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
