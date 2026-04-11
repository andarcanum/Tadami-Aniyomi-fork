package eu.kanade.tachiyomi.source.novel.importer

import eu.kanade.tachiyomi.source.novel.importer.model.ImportedEpubChapter
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class ImportedEpubParserTest {

    @Test
    fun `merge keeps nav order and appends remaining spine chapters`() {
        val navChapters = listOf(
            ImportedEpubChapter(title = "Chapter 2", sourcePath = "OEBPS/chapter2.xhtml"),
            ImportedEpubChapter(title = "Chapter 1", sourcePath = "OEBPS/chapter1.xhtml"),
        )
        val spineChapters = listOf(
            ImportedEpubChapter(title = "Chapter 1", sourcePath = "OEBPS/chapter1.xhtml"),
            ImportedEpubChapter(title = "Chapter 2", sourcePath = "OEBPS/chapter2.xhtml"),
            ImportedEpubChapter(title = "Chapter 3", sourcePath = "OEBPS/chapter3.xhtml"),
        )

        mergeImportedEpubChapters(navChapters, spineChapters) shouldBe listOf(
            ImportedEpubChapter(title = "Chapter 2", sourcePath = "OEBPS/chapter2.xhtml"),
            ImportedEpubChapter(title = "Chapter 1", sourcePath = "OEBPS/chapter1.xhtml"),
            ImportedEpubChapter(title = "Chapter 3", sourcePath = "OEBPS/chapter3.xhtml"),
        )
    }

    @Test
    fun `resolve archive path preserves nested structure and strips suffixes`() {
        resolveImportedEpubArchivePath(
            basePath = "OEBPS/text",
            relativePath = "../images/cover.jpg?size=large#fragment",
        ) shouldBe "OEBPS/images/cover.jpg"
    }

    @Test
    fun `resolve archive path keeps absolute and remote paths unchanged`() {
        resolveImportedEpubArchivePath(
            basePath = "OEBPS/text",
            relativePath = "/assets/style.css",
        ) shouldBe "assets/style.css"

        resolveImportedEpubArchivePath(
            basePath = "OEBPS/text",
            relativePath = "https://example.com/book.css",
        ) shouldBe "https://example.com/book.css"
    }

    @Test
    fun `find cover path prefers manifest cover image property`() {
        val packageDoc = Jsoup.parse(
            """
            <package>
              <metadata>
                <meta name="cover" content="legacy-cover" />
              </metadata>
              <manifest>
                <item id="cover-image" href="images/real-cover.jpg" media-type="image/jpeg" properties="cover-image" />
                <item id="legacy-cover" href="images/old-cover.jpg" media-type="image/jpeg" />
              </manifest>
            </package>
            """.trimIndent(),
            "",
        )

        findImportedEpubCoverPath(
            packageBasePath = "OEBPS",
            packageDoc = packageDoc,
            manifestItems = packageDoc.select("manifest > item"),
        ) shouldBe "OEBPS/images/real-cover.jpg"
    }
}
