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
        plugins[0].versionCode shouldBe 2002001
        plugins[0].versionName shouldBe "2.2.1"
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
        plugins[0].versionCode shouldBe 3
        plugins[0].versionName shouldBe "3"
        plugins[0].sha256 shouldBe "abcd"
    }

    @Test
    fun `parses tachiyomi kotlin novel extension index payload`() {
        val payload = """
            [
              {
                "name": "NovelApp: Shosetsu",
                "pkg": "eu.kanade.tachiyomi.novelextension.all.shosetsu",
                "apk": "tsundoku-all.shosetsu-v1.4.8.apk",
                "lang": "all",
                "code": 8,
                "version": "1.4.8",
                "nsfw": 0,
                "isNovel": true,
                "sources": []
              }
            ]
        """.trimIndent()
        val parser = NovelPluginIndexParser(json)

        val plugins = parser.parse(payload, "https://raw.githubusercontent.com/wasu-code/novel-compat-shosetsu/repo")

        plugins.size shouldBe 1
        plugins[0].id shouldBe "eu.kanade.tachiyomi.novelextension.all.shosetsu"
        plugins[0].name shouldBe "Shosetsu"
        plugins[0].versionCode shouldBe 8
        plugins[0].versionName shouldBe "1.4.8"
        plugins[0].apkUrl shouldBe
            "https://raw.githubusercontent.com/wasu-code/novel-compat-shosetsu/repo/apk/tsundoku-all.shosetsu-v1.4.8.apk"
        plugins[0].isKotlinExtension shouldBe true
        plugins[0].isNsfw shouldBe false
    }

    @Test
    fun `parses tachiyomi kotlin novel extension index with hasSettings`() {
        val payload = """
            [
              {
                "name": "NovelApp: NovelUpdates",
                "pkg": "eu.kanade.tachiyomi.novelextension.en.novelupdates",
                "apk": "tsundoku-en.novelupdates-v1.4.1.apk",
                "lang": "en",
                "code": 1,
                "version": "1.4.1",
                "nsfw": 0,
                "hasSettings": true,
                "isNovel": true,
                "sources": [
                  {
                    "name": "Novel Updates",
                    "baseUrl": "https://www.novelupdates.com"
                  }
                ]
              }
            ]
        """.trimIndent()
        val parser = NovelPluginIndexParser(json)

        val plugins = parser.parse(payload, "https://repo.example/")

        plugins.size shouldBe 1
        plugins[0].id shouldBe "eu.kanade.tachiyomi.novelextension.en.novelupdates"
        plugins[0].hasSettings shouldBe true
        plugins[0].site shouldBe "https://www.novelupdates.com"
    }

    @Test
    fun `parses nsfw flag from js plugin index`() {
        val payload = """
            [
              {
                "id": "NSFW",
                "name": "Adult Source",
                "site": "https://example.org",
                "lang": "en",
                "version": 1,
                "url": "https://example.org/plugin.js",
                "nsfw": 1
              }
            ]
        """.trimIndent()
        val parser = NovelPluginIndexParser(json)

        val plugins = parser.parse(payload, "https://repo.example/")

        plugins.single().isNsfw shouldBe true
    }
}
