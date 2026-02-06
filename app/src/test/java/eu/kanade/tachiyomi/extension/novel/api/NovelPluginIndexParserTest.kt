package eu.kanade.tachiyomi.extension.novel.api

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class NovelPluginIndexParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses lnreader payload with string version and missing sha256`() {
        val payload = """
            [
              {
                "id": "RLIB",
                "name": "RanobeLib",
                "site": "https://ranobelib.me",
                "lang": "Русский",
                "version": "2.2.1",
                "url": "https://example.org/ranobelib.js",
                "iconUrl": "https://example.org/icon.png"
              }
            ]
        """.trimIndent()
        val parser = NovelPluginIndexParser(json)

        val plugins = parser.parse(payload, "https://repo.example/")

        plugins.size shouldBe 1
        plugins[0].id shouldBe "RLIB"
        plugins[0].version shouldBe 2002001
        plugins[0].sha256 shouldBe ""
    }

    @Test
    fun `parses numeric version and sha256`() {
        val payload = """
            [
              {
                "id": "FWN.com",
                "name": "Free Web Novel",
                "site": "https://freewebnovel.com/",
                "lang": "English",
                "version": 3,
                "url": "https://example.org/freewebnovel.js",
                "sha256": "abcd"
              }
            ]
        """.trimIndent()
        val parser = NovelPluginIndexParser(json)

        val plugins = parser.parse(payload, "https://repo.example/")

        plugins.size shouldBe 1
        plugins[0].version shouldBe 3
        plugins[0].sha256 shouldBe "abcd"
    }
}
