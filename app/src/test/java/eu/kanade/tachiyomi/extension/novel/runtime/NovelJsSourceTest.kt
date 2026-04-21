package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import tachiyomi.domain.extension.novel.model.NovelPlugin
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun `getChapterList falls back to parsePage when parseNovel fails`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>()
        val parsePageCalls = AtomicInteger(0)

        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers {
            evaluateChapterFallbackScript(firstArg(), parsePageCalls)
        }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
        )
        val novel = eu.kanade.tachiyomi.novelsource.model.SNovel.create().apply {
            url = "261335--pyeonjibjaui-saengjonsuchig"
            title = "Novel"
        }

        val chapters = kotlinx.coroutines.runBlocking {
            source.getChapterList(novel)
        }

        chapters.size shouldBe 2
        chapters[0].name shouldBe "Ch 1"
        chapters[1].name shouldBe "Ch 2"
    }

    @Test
    fun `getChapterListPage falls back to parsePage when parseNovel fails`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>()
        val parsePageCalls = AtomicInteger(0)

        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers {
            evaluateChapterFallbackScript(firstArg(), parsePageCalls)
        }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
        )
        val novel = eu.kanade.tachiyomi.novelsource.model.SNovel.create().apply {
            url = "261335--pyeonjibjaui-saengjonsuchig"
            title = "Novel"
        }

        val page = kotlinx.coroutines.runBlocking {
            source.getChapterListPage(novel, page = 2)
        }

        val chapterPage = requireNotNull(page)
        chapterPage.page shouldBe 2
        chapterPage.totalPages shouldBe 2
        chapterPage.chapters.size shouldBe 1
        chapterPage.chapters[0].name shouldBe "Ch 2"
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

    private fun evaluateChapterFallbackScript(
        script: String,
        parsePageCalls: AtomicInteger,
    ): Any? {
        return when {
            script.contains("Array.isArray(__plugin && __plugin.settings)") -> false
            script.contains("JSON.stringify(__plugin.settings || [])") -> "[]"
            script.contains("typeof __plugin.parsePage") -> true
            script.contains("typeof __plugin.resolveUrl") -> false
            script.contains("typeof __plugin.fetchImage") -> false
            script.contains("__plugin.parseNovel") -> throw RuntimeException("parseNovel boom")
            script.contains("__plugin.parsePage") -> {
                val nextCall = parsePageCalls.incrementAndGet()
                if (nextCall == 1) {
                    """
                        {
                            "totalPages": 2,
                            "chapters": [
                                {
                                    "name": "Ch 1",
                                    "path": "/c1",
                                    "chapterNumber": 1
                                }
                            ]
                        }
                    """.trimIndent()
                } else {
                    """
                        {
                            "chapters": [
                                {
                                    "name": "Ch 2",
                                    "path": "/c2",
                                    "chapterNumber": 2
                                }
                            ]
                        }
                    """.trimIndent()
                }
            }
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
