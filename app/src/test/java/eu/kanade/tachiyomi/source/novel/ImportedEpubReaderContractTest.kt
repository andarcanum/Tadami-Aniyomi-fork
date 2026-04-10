package eu.kanade.tachiyomi.source.novel.importer

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImportedEpubReaderContractTest {

    @Test
    fun `normalized chapter html rewrites image and stylesheet paths to stored local assets`() {
        val normalizer = ImportedEpubHtmlNormalizer()
        val assetMap = mapOf(
            "images/cover.jpg" to "assets/cover.jpg",
            "styles/main.css" to "assets/main.css"
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

        val normalized = normalizer.normalize(rawHtml, assetMap)

        normalized shouldBe """
            <html>
            <head>
                <link rel="stylesheet" href="assets/main.css">
            </head>
            <body>
                <img src="assets/cover.jpg" alt="Cover">
                <p>Chapter content</p>
            </body>
            </html>
        """.trimIndent()
    }

    @Test
    fun `normalized html stays compatible with novel source chapter text contract`() {
        val normalizer = ImportedEpubHtmlNormalizer()
        val html = "<html><body><p>Test content</p></body></html>"

        val normalized = normalizer.normalize(html, emptyMap())

        // Should remain valid HTML
        normalized.contains("<html>") shouldBe true
        normalized.contains("<body>") shouldBe true
        normalized.contains("<p>Test content</p>") shouldBe true
    }
}