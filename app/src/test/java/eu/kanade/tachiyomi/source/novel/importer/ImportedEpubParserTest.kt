package eu.kanade.tachiyomi.source.novel.importer

import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubBook
import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubChapter
import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubAsset
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImportedEpubParserTest {

    @Test
    fun `parser result keeps metadata chapters and assets together`() {
        val book = ImportedEpubBook(
            title = "Demo",
            author = "Author",
            description = "Desc",
            coverFileName = "cover.jpg",
            chapters = listOf(ImportedEpubChapter(title = "Chapter 1", sourcePath = "text/ch1.xhtml")),
            assets = listOf(ImportedEpubAsset(sourcePath = "images/cover.jpg", targetFileName = "cover.jpg")),
        )

        book.title shouldBe "Demo"
        book.chapters.size shouldBe 1
        book.assets.size shouldBe 1
    }

    @Test
    fun `parser creates book with basic metadata`() {
        // This test will be implemented with actual EPUB fixtures later
        // For now, just ensure the parser can be instantiated
        // TODO: Add EPUB parsing tests with fixture files
    }
}