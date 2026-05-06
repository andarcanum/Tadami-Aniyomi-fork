package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class NovelPluginRuntimeOverridesTest {

    @Test
    fun `forPlugin prefers highest override version for duplicate plugin id`() {
        val overrides = NovelPluginRuntimeOverrides(
            entries = listOf(
                NovelPluginRuntimeOverride(
                    pluginId = "rulate",
                    version = 1,
                    domainAliases = mapOf("https://rulate.ru" to "https://tl.rulate.ru"),
                ),
                NovelPluginRuntimeOverride(
                    pluginId = "rulate",
                    version = 2,
                    domainAliases = mapOf("https://www.rulate.ru" to "https://tl.rulate.ru"),
                ),
            ),
        )

        val selected = overrides.forPlugin("rulate")

        selected.version shouldBe 2
        selected.domainAliases shouldBe mapOf("https://www.rulate.ru" to "https://tl.rulate.ru")
    }

    @Test
    fun `fromJson falls back to version one when field is missing`() {
        val payload = """
            {
              "entries": [
                {
                  "pluginId": "rulate",
                  "domainAliases": {
                    "https://rulate.ru": "https://tl.rulate.ru"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = NovelPluginRuntimeOverrides.fromJson(
            json = Json { ignoreUnknownKeys = true },
            payload = payload,
        )

        parsed.forPlugin("rulate").version shouldBe 1
    }

    @Test
    fun `disableFallbacks defaults to true`() {
        val override = NovelPluginRuntimeOverride(pluginId = "test")
        override.disableFallbacks shouldBe true
    }

    @Test
    fun `disableScriptPatches defaults to true`() {
        val override = NovelPluginRuntimeOverride(pluginId = "test")
        override.disableScriptPatches shouldBe true
    }

    @Test
    fun `fromJson populates disableFallbacks when present`() {
        val payload = """
            {
                "entries": [
                    {
                        "pluginId": "wuxiaworld",
                        "disableFallbacks": false
                    }
                ]
            }
        """.trimIndent()

        val parsed = NovelPluginRuntimeOverrides.fromJson(
            json = Json { ignoreUnknownKeys = true },
            payload = payload,
        )

        parsed.forPlugin("wuxiaworld").disableFallbacks shouldBe false
    }

    @Test
    fun `fromJson populates disableScriptPatches when present`() {
        val payload = """
            {
                "entries": [
                    {
                        "pluginId": "royalroad",
                        "disableScriptPatches": false
                    }
                ]
            }
        """.trimIndent()

        val parsed = NovelPluginRuntimeOverrides.fromJson(
            json = Json { ignoreUnknownKeys = true },
            payload = payload,
        )

        parsed.forPlugin("royalroad").disableScriptPatches shouldBe false
    }
}
