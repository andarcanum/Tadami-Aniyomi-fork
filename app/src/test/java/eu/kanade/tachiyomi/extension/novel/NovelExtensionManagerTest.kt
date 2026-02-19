package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade
import eu.kanade.tachiyomi.novelsource.NovelSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.data.extension.novel.toInstalled
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository

class NovelExtensionManagerTest {

    @Test
    fun `install registers source and stub data`() = runTest {
        val repository = FakePluginRepository()
        val api = FakePluginApi(emptyList())
        val installer = FakePluginInstaller(repository)
        val sourceFactory = FakeSourceFactory()
        val manager = DefaultNovelExtensionManager(repository, api, installer, sourceFactory)

        val plugin = NovelPlugin.Available(
            id = "novel",
            name = "Novel Source",
            site = "https://example.org",
            lang = "en",
            version = 1,
            url = "https://example.org/novel.js",
            iconUrl = null,
            customJs = null,
            customCss = null,
            hasSettings = false,
            sha256 = "deadbeef",
            repoUrl = "https://repo.example",
        )

        manager.installPlugin(plugin)

        val sources = manager.installedSourcesFlow.first()
        sources.first().name shouldBe "Novel Source"
        manager.getSourceData(sources.first().id)?.name shouldBe "Novel Source"
        manager.getSourceData(sources.first().id)?.lang shouldBe "en"
    }

    private class FakePluginApi(
        private val plugins: List<NovelPlugin.Available>,
    ) : NovelPluginApiFacade {
        override suspend fun fetchAvailablePlugins(): List<NovelPlugin.Available> = plugins
    }

    private class FakePluginInstaller(
        private val repository: NovelPluginRepository,
    ) : NovelPluginInstallerFacade {
        override suspend fun install(plugin: NovelPlugin.Available): NovelPlugin.Installed {
            val installed = plugin.toInstalled()
            repository.upsert(installed)
            return installed
        }

        override suspend fun uninstall(pluginId: String) {
            repository.delete(pluginId)
        }
    }

    private class FakePluginRepository : NovelPluginRepository {
        private val state = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())

        override fun subscribeAll(): Flow<List<NovelPlugin.Installed>> = state

        override suspend fun getAll(): List<NovelPlugin.Installed> = state.value

        override suspend fun getById(id: String): NovelPlugin.Installed? {
            return state.value.firstOrNull { it.id == id }
        }

        override suspend fun upsert(plugin: NovelPlugin.Installed) {
            val updated = state.value.filterNot { it.id == plugin.id } + plugin
            state.value = updated
        }

        override suspend fun delete(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }
    }

    private class FakeSourceFactory : NovelPluginSourceFactory {
        override fun create(plugin: NovelPlugin.Installed): NovelSource {
            return FakeSource(sourceIdFor(plugin.id), plugin.name, plugin.lang)
        }

        fun sourceIdFor(pluginId: String): Long = NovelPluginId.toSourceId(pluginId)
    }

    private data class FakeSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
    ) : NovelSource
}
