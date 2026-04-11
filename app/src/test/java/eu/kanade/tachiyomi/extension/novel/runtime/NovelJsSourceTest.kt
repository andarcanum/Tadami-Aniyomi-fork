package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelJsSourceTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val keyValueStore = InMemoryKeyValueStore()

    @Test
    fun `hasPluginSettings reflects plugin metadata before runtime initialization`() {
        val source = createSource(hasSettings = true)

        source.hasPluginSettings() shouldBe true
    }

    @Test
    fun `hasPluginSettings is false for plugins without settings before runtime initialization`() {
        val source = createSource(hasSettings = false)

        source.hasPluginSettings() shouldBe false
    }

    @Test
    fun `hasPluginSettings discoverRuntime can rediscover settings after cache clear`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime1 = mockk<NovelJsRuntime>(relaxed = true)
        val runtime2 = mockk<NovelJsRuntime>(relaxed = true)
        every { runtimeFactory.create(any()) } returns runtime1 andThen runtime2
        every { runtime1.evaluate(any(), any(), any()) } answers { evaluateSettingsScript(firstArg()) }
        every { runtime2.evaluate(any(), any(), any()) } answers { evaluateSettingsScript(firstArg()) }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
        )

        source.hasPluginSettings(discoverRuntime = true) shouldBe true

        source.clearInMemoryCaches()

        source.hasPluginSettings(discoverRuntime = true) shouldBe true

        verify(exactly = 2) { runtimeFactory.create("test-plugin") }
    }

    private fun createSource(
        hasSettings: Boolean,
        runtimeFactory: NovelJsRuntimeFactory = mockk(relaxed = true),
    ): NovelJsSource {
        val plugin = NovelPlugin.Installed(
            id = "test-plugin",
            name = "Test Plugin",
            site = "https://example.com",
            lang = "en",
            version = 1,
            url = "https://example.com/plugin.js",
            iconUrl = null,
            customJs = null,
            customCss = null,
            hasSettings = hasSettings,
            sha256 = "",
            repoUrl = "https://repo.example/",
        )

        return NovelJsSource(
            plugin = plugin,
            script = "module.exports = {};",
            runtimeFactory = runtimeFactory,
            json = json,
            scriptBuilder = NovelPluginScriptBuilder(),
            filterMapper = NovelPluginFilterMapper(json),
            resultNormalizer = NovelPluginResultNormalizer(),
            runtimeOverride = NovelPluginRuntimeOverride(pluginId = plugin.id),
            settingsBridge = NovelPluginSettingsBridge(
                pluginId = plugin.id,
                keyValueStore = keyValueStore,
                json = json,
            ),
        )
    }

    private fun evaluateSettingsScript(script: String): Any? {
        return when {
            script.contains("Array.isArray(__plugin && __plugin.settings)") -> true
            script.contains("JSON.stringify(__plugin.settings || [])") -> """
                [
                    {
                        "key": "apiKey",
                        "type": "Text",
                        "title": "API Key",
                        "default": ""
                    }
                ]
            """.trimIndent()
            script.contains("typeof __plugin.parsePage") -> false
            script.contains("typeof __plugin.resolveUrl") -> false
            script.contains("typeof __plugin.fetchImage") -> false
            else -> null
        }
    }

    private class InMemoryKeyValueStore : NovelPluginKeyValueStore {
        private val store = mutableMapOf<String, MutableMap<String, String>>()

        override fun get(pluginId: String, key: String): String? {
            return store[pluginId]?.get(key)
        }

        override fun set(pluginId: String, key: String, value: String) {
            store.getOrPut(pluginId) { mutableMapOf() }[key] = value
        }

        override fun remove(pluginId: String, key: String) {
            store[pluginId]?.remove(key)
        }

        override fun clear(pluginId: String) {
            store.remove(pluginId)
        }

        override fun clearAll() {
            store.clear()
        }

        override fun keys(pluginId: String): Set<String> {
            return store[pluginId]?.keys ?: emptySet()
        }
    }
}
