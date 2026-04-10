package eu.kanade.tachiyomi.source.novel.importer

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImportedEpubReaderContractTest {

    @Test
    fun `normalized chapter html rewrites image and stylesheet paths to stored local assets`() {
        val normalizer = ImportedEpubHtmlNormalizer()
        val assetMap = mapOf(
            "OEBPS/images/cover.jpg" to
                "file:///data/data/app/files/imported_epub_novels/123/assets/OEBPS/images/cover.jpg",
            "OEBPS/styles/main.css" to
                "file:///data/data/app/files/imported_epub_novels/123/assets/OEBPS/styles/main.css",
        )

        val rawHtml = """
            <html>
            <head>
                <link rel="stylesheet" href="styles/main.css">
            </head>
            <body>
                <img src="images/cover.jpg" alt="Cover">
                <p>Chapter content</p>
            </body>
            </html>
        """.trimIndent()

        val normalized = normalizer.normalize(
            rawHtml = rawHtml,
            chapterSourcePath = "OEBPS/chapter1.xhtml",
            chapterAssetMap = assetMap,
        )

        normalized.contains(
            "file:///data/data/app/files/imported_epub_novels/123/assets/OEBPS/styles/main.css",
        ) shouldBe
            true
        normalized.contains(
            "file:///data/data/app/files/imported_epub_novels/123/assets/OEBPS/images/cover.jpg",
        ) shouldBe
            true
        normalized.contains("<p>Chapter content</p>") shouldBe true
    }

    @Test
    fun `normalized html stays compatible with novel source chapter text contract`() {
        val normalizer = ImportedEpubHtmlNormalizer()
        val html = "<html><body><p>Test content</p></body></html>"

        val normalized = normalizer.normalize(html, "OEBPS/chapter1.xhtml", emptyMap())

        // Should remain valid HTML
        normalized.contains("<html>") shouldBe true
        normalized.contains("<body>") shouldBe true
        normalized.contains("<p>Test content</p>") shouldBe true
    }

    @Test
    fun `normalized chapter html rewrites svg image references for cover pages`() {
        val normalizer = ImportedEpubHtmlNormalizer()
        val expectedCoverAssetPath =
            "file:///data/data/app/files/imported_epub_novels/123/assets/OPS/images/cover.jpg"
        val assetMap = mapOf(
            "OPS/images/cover.jpg" to expectedCoverAssetPath,
        )

        val rawHtml = """
            <html>
            <body>
                <svg viewBox="0 0 10 10">
                    <image xlink:href="images/cover.jpg"></image>
                </svg>
            </body>
            </html>
        """.trimIndent()

        val normalized = normalizer.normalize(
            rawHtml = rawHtml,
            chapterSourcePath = "OPS/cover.xhtml",
            chapterAssetMap = assetMap,
        )

        normalized.contains(expectedCoverAssetPath) shouldBe true
    }
}
