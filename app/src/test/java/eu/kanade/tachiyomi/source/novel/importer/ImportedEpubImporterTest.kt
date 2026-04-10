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
    fun `storage writes chapter html assets and cover into novel directory`() {
        val baseDir = tempDir.toFile()
        val storage = ImportedEpubStorage(baseDir)

        val novelId = 123L
        val chapterId = 456L
        val html = "<html><body>Test chapter</body></html>"
        val asset = ImportedEpubAsset("images/cover.jpg", "cover.jpg")
        val assetBytes = "fake image data".toByteArray()

        storage.writeChapter(novelId, chapterId, html)
        storage.writeAsset(novelId, asset, assetBytes)

        val novelDir = storage.novelDirectory(novelId)
        val chapterDir = File(novelDir, chapterId.toString())
        val indexFile = File(chapterDir, "index.html")
        val assetsDir = File(novelDir, "assets")
        val assetFile = File(assetsDir, asset.targetFileName)

        indexFile.exists() shouldBe true
        indexFile.readText() shouldBe html

        assetFile.exists() shouldBe true
        assetFile.readBytes() shouldBe assetBytes
    }

    // TODO: Add importer tests with mocked repositories
}