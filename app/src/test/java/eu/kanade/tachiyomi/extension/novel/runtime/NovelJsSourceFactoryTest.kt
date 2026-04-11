package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import tachiyomi.data.extension.novel.NovelPluginStorage
import tachiyomi.domain.extension.novel.model.NovelPlugin
import java.nio.file.Files

class NovelJsSourceFactoryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val runtimeOverrides = NovelPluginRuntimeOverrides()

    @Test
    fun `create returns configurable wrapper only for plugins with settings`() {
        val storageDir = Files.createTempDirectory("novel-js-source-factory-test").toFile()
        val pluginStorage = NovelPluginStorage(storageDir)
        val runtimeFactory = mockk<NovelJsRuntimeFactory>(relaxed = true)
        val keyValueStore = InMemoryKeyValueStore()
        val factory = NovelJsSourceFactory(
            runtimeFactory = runtimeFactory,
            pluginStorage = pluginStorage,
            json = json,
            runtimeOverrides = runtimeOverrides,
            keyValueStore = keyValueStore,
            assetBindings = NovelPluginAssetBindings(pluginStorage),
        )

        pluginStorage.writePluginFiles(
            pluginId = "with-settings",
            script = "module.exports = {};".toByteArray(),
            customJs = null,
            customCss = null,
        )
        pluginStorage.writePluginFiles(
            pluginId = "without-settings",
            script = "module.exports = {};".toByteArray(),
            customJs = null,
            customCss = null,
        )

        val withSettings = factory.create(createPlugin("with-settings", hasSettings = true))
        val withoutSettings = factory.create(createPlugin("without-settings", hasSettings = false))

        withSettings.shouldBeInstanceOf<NovelCatalogueSource>()
        (withSettings is ConfigurableNovelSource) shouldBe true
        (withoutSettings is ConfigurableNovelSource) shouldBe false
        withoutSettings.shouldBeInstanceOf<NovelCatalogueSource>()
    }

    @Test
    fun `create does not eagerly discover runtime-only settings`() {
        val storageDir = Files.createTempDirectory("novel-js-source-factory-test-runtime").toFile()
        val pluginStorage = NovelPluginStorage(storageDir)
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        every { runtimeFactory.create(any()) } throws AssertionError("runtime should not be created eagerly")
        val keyValueStore = InMemoryKeyValueStore()
        val factory = NovelJsSourceFactory(
            runtimeFactory = runtimeFactory,
            pluginStorage = pluginStorage,
            json = json,
            runtimeOverrides = runtimeOverrides,
            keyValueStore = keyValueStore,
            assetBindings = NovelPluginAssetBindings(pluginStorage),
        )

        pluginStorage.writePluginFiles(
            pluginId = "runtime-settings",
            script = "module.exports = {};".toByteArray(),
            customJs = null,
            customCss = null,
        )

        val source = factory.create(createPlugin("runtime-settings", hasSettings = false))

        source.shouldBeInstanceOf<NovelCatalogueSource>()
        (source is ConfigurableNovelSource) shouldBe false
    }

    private fun createPlugin(
        id: String,
        hasSettings: Boolean,
    ): NovelPlugin.Installed {
        return NovelPlugin.Installed(
            id = id,
            name = "Test Plugin",
            site = "https://example.com",
            lang = "en",
            version = 1,
            url = "https://example.com/$id.js",
            iconUrl = null,
            customJs = null,
            customCss = null,
            hasSettings = hasSettings,
            sha256 = "",
            repoUrl = "https://repo.example/",
        )
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
