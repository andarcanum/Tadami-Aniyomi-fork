package eu.kanade.tachiyomi.extension.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.novelsource.NovelSource
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.data.extension.novel.toInstalled
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade
import tachiyomi.domain.source.novel.model.StubNovelSource

class DefaultNovelExtensionManagerTest {

    @Test
    fun `refreshAvailablePlugins updates available flow`() = runTest {
        val repo = FakePluginRepository()
        val api = FakePluginApi(
            listOf(
                NovelPlugin.Available(
                    id = "one",
                    name = "One",
                    site = "https://one.example",
                    lang = "en",
                    version = 1,
                    url = "https://one.example/plugin.js",
                    iconUrl = null,
                    customJs = null,
                    customCss = null,
                    hasSettings = false,
                    sha256 = "aaa",
                    repoUrl = "https://repo.one",
                ),
            ),
        )
        val installer = FakePluginInstaller(repo)
        val sourceFactory = FakeSourceFactory()

        val manager = DefaultNovelExtensionManager(repo, api, installer, sourceFactory)

        manager.refreshAvailablePlugins()

        val available = manager.availablePluginsFlow.first { it.isNotEmpty() }
        available.size shouldBe 1
        available.first().id shouldBe "one"
    }

    @Test
    fun `installPlugin updates installed flow`() = runTest {
        val repo = FakePluginRepository()
        val api = FakePluginApi(emptyList())
        val installer = FakePluginInstaller(repo)
        val sourceFactory = FakeSourceFactory()
        val manager = DefaultNovelExtensionManager(repo, api, installer, sourceFactory)

        val plugin = NovelPlugin.Available(
            id = "two",
            name = "Two",
            site = "https://two.example",
            lang = "ru",
            version = 2,
            url = "https://two.example/plugin.js",
            iconUrl = null,
            customJs = null,
            customCss = null,
            hasSettings = false,
            sha256 = "bbb",
            repoUrl = "https://repo.two",
        )

        manager.installPlugin(plugin)

        val installed = manager.installedPluginsFlow.first { it.isNotEmpty() }
        installed.first().id shouldBe "two"
    }

    @Test
    fun `refreshAvailablePlugins updates updates flow`() = runTest {
        val repo = FakePluginRepository().apply {
            upsert(
                NovelPlugin.Installed(
                    id = "three",
                    name = "Three",
                    site = "https://three.example",
                    lang = "en",
                    version = 1,
                    url = "https://three.example/plugin.js",
                    iconUrl = null,
                    customJs = null,
                    customCss = null,
                    hasSettings = false,
                    sha256 = "ccc",
                    repoUrl = "https://repo.three",
                ),
            )
        }
        val api = FakePluginApi(
            listOf(
                NovelPlugin.Available(
                    id = "three",
                    name = "Three",
                    site = "https://three.example",
                    lang = "en",
                    version = 2,
                    url = "https://three.example/plugin.js",
                    iconUrl = null,
                    customJs = null,
                    customCss = null,
                    hasSettings = false,
                    sha256 = "ccc",
                    repoUrl = "https://repo.three",
                ),
            ),
        )
        val installer = FakePluginInstaller(repo)
        val sourceFactory = FakeSourceFactory()
        val manager = DefaultNovelExtensionManager(repo, api, installer, sourceFactory)

        manager.refreshAvailablePlugins()

        val updates = manager.updatesFlow.first { it.isNotEmpty() }
        updates.size shouldBe 1
        updates.first().id shouldBe "three"
    }

    @Test
    fun `installed plugins are mapped into sources`() = runTest {
        val repo = FakePluginRepository()
        val api = FakePluginApi(emptyList())
        val installer = FakePluginInstaller(repo)
        val sourceFactory = FakeSourceFactory()
        val manager = DefaultNovelExtensionManager(repo, api, installer, sourceFactory)

        repo.upsert(
            NovelPlugin.Installed(
                id = "source-one",
                name = "Source One",
                site = "https://one.example",
                lang = "en",
                version = 1,
                url = "https://one.example/plugin.js",
                iconUrl = null,
                customJs = null,
                customCss = null,
                hasSettings = false,
                sha256 = "aaa",
                repoUrl = "https://repo.one",
            ),
        )

        val sources = manager.installedSourcesFlow.first { it.isNotEmpty() }
        sources.size shouldBe 1
        sources.first().id shouldBe sourceFactory.sourceIdFor("source-one")
    }

    @Test
    fun `getSourceData returns stub for installed plugin`() = runTest {
        val repo = FakePluginRepository()
        val api = FakePluginApi(emptyList())
        val installer = FakePluginInstaller(repo)
        val sourceFactory = FakeSourceFactory()
        val manager = DefaultNovelExtensionManager(repo, api, installer, sourceFactory)

        val installed = NovelPlugin.Installed(
            id = "source-two",
            name = "Source Two",
            site = "https://two.example",
            lang = "ru",
            version = 1,
            url = "https://two.example/plugin.js",
            iconUrl = null,
            customJs = null,
            customCss = null,
            hasSettings = false,
            sha256 = "bbb",
            repoUrl = "https://repo.two",
        )
        repo.upsert(installed)

        val stub = manager.getSourceData(sourceFactory.sourceIdFor(installed.id))

        stub?.id shouldBe sourceFactory.sourceIdFor(installed.id)
        stub?.lang shouldBe installed.lang
        stub?.name shouldBe installed.name
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
