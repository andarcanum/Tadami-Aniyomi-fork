package eu.kanade.tachiyomi.source.novel.importer

import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubAsset
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ImportedEpubImporterTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `storage writes chapter html and nested assets into novel directory`() {
        val baseDir = tempDir.toFile()
        val storage = ImportedEpubStorage(baseDir)

        val novelId = 123L
        val chapterId = 456L
        val html = "<html><body>Test chapter</body></html>"
        val asset = ImportedEpubAsset("OEBPS/images/cover.jpg", "OEBPS/images/cover.jpg")
        val assetBytes = "fake image data".toByteArray()

        storage.writeChapter(novelId, chapterId, html)
        val storedAssetFile = storage.writeAsset(novelId, asset, assetBytes)

        val novelDir = storage.novelDirectory(novelId)
        val chapterDir = File(novelDir, chapterId.toString())
        val indexFile = File(chapterDir, "index.html")
        val assetFile = File(novelDir, "assets/OEBPS/images/cover.jpg")

        indexFile.exists() shouldBe true
        indexFile.readText() shouldBe html
        storage.readChapterHtml(novelId, chapterId) shouldBe html

        assetFile.exists() shouldBe true
        assetFile.readBytes() shouldBe assetBytes
        storedAssetFile shouldBe assetFile
    }

    @Test
    fun `import skips cover and toc front matter chapters when book has real content`() {
        shouldImportEpubChapter(
            sourcePath = "OPS/cover.xhtml",
            rawHtml = "<html><body><svg><image xlink:href=\"images/cover.jpg\" /></svg></body></html>",
            totalChapterCount = 3,
        ) shouldBe false

        shouldImportEpubChapter(
            sourcePath = "OPS/toc.xhtml",
            rawHtml = "<html><body><p>Contents</p></body></html>",
            totalChapterCount = 3,
        ) shouldBe false

        shouldImportEpubChapter(
            sourcePath = "OPS/chapter-001.xhtml",
            rawHtml = "<html><body><p>Actual story text</p></body></html>",
            totalChapterCount = 3,
        ) shouldBe true
    }
}
