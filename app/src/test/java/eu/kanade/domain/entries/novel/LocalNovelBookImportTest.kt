package eu.kanade.domain.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LocalNovelBookImportTest {

    @Test
    fun `book formats stay supported`() {
        LocalNovelBookImport.isSupportedImportFileName("book.epub") shouldBe true
        LocalNovelBookImport.isSupportedImportFileName("book.fb2") shouldBe true
        LocalNovelBookImport.isSupportedImportFileName("book.FB2") shouldBe true
    }

    @Test
    fun `text formats are importable`() {
        val names = listOf(
            "chapter.txt",
            "chapter.text",
            "note.md",
            "note.markdown",
            "page.html",
            "page.htm",
            "page.xhtml",
        )
        names.forEach { name ->
            LocalNovelBookImport.isSupportedImportFileName(name) shouldBe true
        }
    }

    @Test
    fun `archive containers are importable`() {
        val names = listOf("shelf.zip", "shelf.cbz", "shelf.rar", "shelf.cbr")
        names.forEach { name ->
            LocalNovelBookImport.isSupportedImportFileName(name) shouldBe true
        }
    }

    @Test
    fun `unsupported formats rejected`() {
        val names = listOf("doc.pdf", "doc.docx", "image.jpg", "noext")
        names.forEach { name ->
            LocalNovelBookImport.isSupportedImportFileName(name) shouldBe false
        }
    }

    @Test
    fun `title fallback strips new extensions too`() {
        LocalNovelBookImport.titleFallbackFromFileName("My Book.zip") shouldBe "My Book"
        LocalNovelBookImport.titleFallbackFromFileName("notes.md") shouldBe "notes"
        LocalNovelBookImport.titleFallbackFromFileName("My Novel.epub") shouldBe "My Novel"
        LocalNovelBookImport.titleFallbackFromFileName("untitled") shouldBe "untitled"
    }

    @Test
    fun `folder names sanitize illegal characters`() {
        LocalNovelBookImport.sanitizeFileName("a/b:c*d") shouldBe "a_b_c_d"
    }
}
