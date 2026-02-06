package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.util.lang.Hash
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class NovelPluginRepoParserTest {

    private val parser = NovelPluginRepoParser(
        Json { ignoreUnknownKeys = true },
    )

    @Test
    fun `parse repo json into entries`() {
        val json = """
            [
              {
                "id": "novel",
                "name": "Novel Source",
                "site": "https://example.org",
                "lang": "en",
                "version": 3,
                "url": "https://example.org/novel.js",
                "iconUrl": "https://example.org/icon.png",
                "customJS": "https://example.org/custom.js",
                "customCSS": "https://example.org/custom.css",
                "hasSettings": true,
                "sha256": "deadbeef"
              }
            ]
        """.trimIndent()

        val entries = parser.parse(json)

        entries.size shouldBe 1
        val entry = entries.first()
        entry.id shouldBe "novel"
        entry.name shouldBe "Novel Source"
        entry.site shouldBe "https://example.org"
        entry.lang shouldBe "en"
        entry.version shouldBe 3
        entry.url shouldBe "https://example.org/novel.js"
        entry.iconUrl shouldBe "https://example.org/icon.png"
        entry.customJsUrl shouldBe "https://example.org/custom.js"
        entry.customCssUrl shouldBe "https://example.org/custom.css"
        entry.hasSettings shouldBe true
        entry.sha256 shouldBe "deadbeef"
    }

    @Test
    fun `verify sha256 handles case and mismatch`() {
        val bytes = "content".toByteArray()
        val expected = Hash.sha256(bytes)

        NovelPluginChecksum.verifySha256(expected, bytes) shouldBe true
        NovelPluginChecksum.verifySha256(expected.uppercase(), bytes) shouldBe true
        NovelPluginChecksum.verifySha256("deadbeef", bytes) shouldBe false
    }
}
