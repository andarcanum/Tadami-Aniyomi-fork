package eu.kanade.tachiyomi.ui.browse.novel.extension

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.novel.model.StubNovelSource

class NovelExtensionsScreenModelTest {

    private val sourcePreferences: SourcePreferences = mockk(relaxed = true)
    private val enabledLanguages = MutableStateFlow(setOf("en"))
    private val activeScreenModels = mutableListOf<NovelExtensionsScreenModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val enabledLanguagesPreference = mockk<Preference<Set<String>>>()
        every { enabledLanguagesPreference.changes() } returns enabledLanguages
        every { sourcePreferences.enabledLanguages() } returns enabledLanguagesPreference
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        activeScreenModels.clear()
        runBlocking {
            repeat(5) { yield() }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `loads listing into state`() {
        runBlocking {
            val installed = pluginInstalled("id-1", 1)
            val updates = listOf(installed)
            val available = listOf(pluginAvailable("id-2", 1))

            val screenModel = NovelExtensionsScreenModel(
                extensionManager = FakeNovelExtensionManager(
                    installed = listOf(installed),
                    available = available,
                    updates = updates,
                ),
                sourcePreferences = sourcePreferences,
            ).also(activeScreenModels::add)

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.isLoading shouldBe false
            state.items.size shouldBe 2
            state.updates shouldBe 1
            state.items.first().status.shouldBeInstanceOf<NovelExtensionItem.Status>()
        }
    }

    @Test
    fun `syncs update count into preferences`() {
        runBlocking {
            val updatesPreference = mockk<Preference<Int>>(relaxed = true)
            every { sourcePreferences.novelExtensionUpdatesCount() } returns updatesPreference

            val updates = listOf(
                pluginInstalled("id-1", 1),
                pluginInstalled("id-2", 1),
            )

            val screenModel = NovelExtensionsScreenModel(
                extensionManager = FakeNovelExtensionManager(
                    installed = updates,
                    available = emptyList(),
                    updates = updates,
                ),
                sourcePreferences = sourcePreferences,
            ).also(activeScreenModels::add)

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            verify { updatesPreference.set(2) }
        }
    }

    @Test
    fun `search matches plugin site`() {
        runBlocking {
            val available = pluginAvailable("id-1", 1).copy(site = "ExampleSite")

            val screenModel = NovelExtensionsScreenModel(
                extensionManager = FakeNovelExtensionManager(
                    installed = emptyList(),
                    available = listOf(available),
                    updates = emptyList(),
                ),
                sourcePreferences = sourcePreferences,
            ).also(activeScreenModels::add)

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            screenModel.search("ExampleSite")
            delay(SEARCH_DEBOUNCE_MILLIS + 50)

            screenModel.state.value.items.any { it.plugin.id == "id-1" } shouldBe true
        }
    }

    @Test
    fun `available plugins are filtered by enabled language`() {
        runBlocking {
            val english = pluginAvailable("id-en", 1).copy(lang = "en")
            val russian = pluginAvailable("id-ru", 1).copy(lang = "ru")

            val screenModel = NovelExtensionsScreenModel(
                extensionManager = FakeNovelExtensionManager(
                    installed = emptyList(),
                    available = listOf(english, russian),
                    updates = emptyList(),
                ),
                sourcePreferences = sourcePreferences,
            ).also(activeScreenModels::add)

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            screenModel.state.value.items.map { it.plugin.id } shouldBe listOf("id-en")
        }
    }

    private fun pluginAvailable(id: String, version: Int) = NovelPlugin.Available(
        id = id,
        name = "Source $id",
        site = "Example",
        lang = "en",
        version = version,
        url = "https://example.org/$id.js",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "deadbeef",
        repoUrl = "https://example.org/index.min.json",
    )

    private fun pluginInstalled(id: String, version: Int) = NovelPlugin.Installed(
        id = id,
        name = "Source $id",
        site = "Example",
        lang = "en",
        version = version,
        url = "https://example.org/$id.js",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "deadbeef",
        repoUrl = "https://example.org/index.min.json",
    )

    private class FakeNovelExtensionManager(
        installed: List<NovelPlugin.Installed>,
        available: List<NovelPlugin.Available>,
        updates: List<NovelPlugin.Installed>,
    ) : NovelExtensionManager {
        override val installedSourcesFlow: Flow<List<NovelSource>> =
            MutableStateFlow(emptyList())
        override val installedPluginsFlow: Flow<List<NovelPlugin.Installed>> =
            MutableStateFlow(installed)
        override val availablePluginsFlow: Flow<List<NovelPlugin.Available>> =
            MutableStateFlow(available)
        override val updatesFlow: Flow<List<NovelPlugin.Installed>> =
            MutableStateFlow(updates)

        override suspend fun refreshAvailablePlugins() = Unit

        override suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed {
            throw NotImplementedError("Not needed for test")
        }

        override suspend fun uninstallPlugin(plugin: NovelPlugin.Installed) = Unit

        override suspend fun getSourceData(id: Long): StubNovelSource? = null

        override fun getPluginIconUrlForSource(sourceId: Long): String? = null
    }
}
