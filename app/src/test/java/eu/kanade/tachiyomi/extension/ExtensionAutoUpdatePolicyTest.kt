package eu.kanade.tachiyomi.extension

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginCapabilities
import eu.kanade.tachiyomi.novelsource.NovelSource
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.extension.novel.model.NovelPlugin

class ExtensionAutoUpdatePolicyTest {

    @Test
    fun `a privately installed extension auto-updates when the toggle is on`() {
        canAutoUpdateExtension(
            autoUpdateEnabled = true,
            installer = BasePreferences.ExtensionInstaller.PRIVATE,
            isSharedInstall = false,
        ) shouldBe true
    }

    @Test
    fun `nothing auto-updates while the toggle is off`() {
        BasePreferences.ExtensionInstaller.entries.forEach { installer ->
            canAutoUpdateExtension(
                autoUpdateEnabled = false,
                installer = installer,
                isSharedInstall = false,
            ) shouldBe false
        }
    }

    @Test
    fun `installers that need a system dialog never auto-update`() {
        BasePreferences.ExtensionInstaller.entries
            .filterNot { it == BasePreferences.ExtensionInstaller.PRIVATE }
            .forEach { installer ->
                canAutoUpdateExtension(
                    autoUpdateEnabled = true,
                    installer = installer,
                    isSharedInstall = false,
                ) shouldBe false
            }
    }

    @Test
    fun `a system installed extension is left to a manual update`() {
        canAutoUpdateExtension(
            autoUpdateEnabled = true,
            installer = BasePreferences.ExtensionInstaller.PRIVATE,
            isSharedInstall = true,
        ) shouldBe false
    }

    @Test
    fun `novel auto-update picks checksummed same-repo replacement only`() = runTest {
        val installed = novelInstalled(id = PLUGIN_ID, versionCode = 1, repoUrl = REPO_A)
        val candidates = listOf(
            novelAvailable(id = PLUGIN_ID, versionCode = 3, repoUrl = REPO_B, sha256 = "ab"), // newer other repo
            novelAvailable(id = PLUGIN_ID, versionCode = 2, repoUrl = REPO_A, sha256 = ""), // same repo, no checksum
            novelAvailable(id = PLUGIN_ID, versionCode = 2, repoUrl = REPO_A, sha256 = "cd"), // correct pick
        )
        val fake = FakeAutoUpdateNovelManager(listOf(installed), candidates)

        novelRunner().updateNovelExtensions(BasePreferences.ExtensionInstaller.PRIVATE, fake)

        fake.installedPlugins.single().let {
            it.versionCode shouldBe 2
            it.sha256 shouldBe "cd"
            it.repoUrl shouldBe REPO_A
        }
    }

    @Test
    fun `novel auto-update infers the repo of a blank-repo install`() = runTest {
        val installed = novelInstalled(id = PLUGIN_ID, versionCode = 1, repoUrl = "")
        val candidates = listOf(
            novelAvailable(id = PLUGIN_ID, versionCode = 2, repoUrl = REPO_A, sha256 = "cd"),
        )
        val fake = FakeAutoUpdateNovelManager(listOf(installed), candidates)

        novelRunner().updateNovelExtensions(BasePreferences.ExtensionInstaller.PRIVATE, fake)

        fake.installedPlugins.single().let {
            it.versionCode shouldBe 2
            it.sha256 shouldBe "cd"
            it.repoUrl shouldBe REPO_A
        }
    }

    @Test
    fun `novel auto-update skips plugins whose repo cannot be attributed`() = runTest {
        val installed = novelInstalled(id = PLUGIN_ID, versionCode = 1, repoUrl = "")
        val candidates = listOf(
            novelAvailable(id = PLUGIN_ID, versionCode = 3, repoUrl = REPO_A, sha256 = "ab"),
            novelAvailable(id = PLUGIN_ID, versionCode = 3, repoUrl = REPO_B, sha256 = "cd"),
        )
        val fake = FakeAutoUpdateNovelManager(listOf(installed), candidates)

        novelRunner().updateNovelExtensions(BasePreferences.ExtensionInstaller.PRIVATE, fake)

        fake.installedPlugins shouldBe emptyList()
    }

    @Test
    fun `novel auto-update skips system-installed kotlin extensions`() = runTest {
        val installed = novelInstalled(id = PLUGIN_ID, versionCode = 1, repoUrl = REPO_A, isShared = true)
        val candidates = listOf(
            novelAvailable(id = PLUGIN_ID, versionCode = 2, repoUrl = REPO_A, sha256 = "cd"),
        )
        val fake = FakeAutoUpdateNovelManager(listOf(installed), candidates)

        novelRunner().updateNovelExtensions(BasePreferences.ExtensionInstaller.PRIVATE, fake)

        // A private replacement next to a system copy would strand a second install (policy:
        // shared installs stay manual, same as manga/anime).
        fake.installedPlugins shouldBe emptyList()
    }

    @Test
    fun `novel auto-update badge counts classifier updates including kotlin`() = runTest {
        val jsUpdated = novelInstalled(id = "js1", versionCode = 1, repoUrl = REPO_A, isKotlinExtension = false)
        val kotlinStale = novelInstalled(id = PLUGIN_ID, versionCode = 1, repoUrl = REPO_A)
        val available = listOf(
            novelAvailable(id = "js1", versionCode = 2, repoUrl = REPO_A, sha256 = "cd", isKotlinExtension = false),
            // Same-repo newer variant without checksum: skipped by the auto-updater, still an
            // update for the screen's full-set classifier.
            novelAvailable(id = PLUGIN_ID, versionCode = 2, repoUrl = REPO_A, sha256 = ""),
        )
        val preferenceStore = FakePreferenceStore()
        SourcePreferences(preferenceStore).novelInstalledExtensionRepos().set(setOf(REPO_A))
        val fake = FakeAutoUpdateNovelManager(listOf(jsUpdated, kotlinStale), available)

        novelRunner(preferenceStore).updateNovelExtensions(BasePreferences.ExtensionInstaller.PRIVATE, fake)

        fake.installedPlugins.single().id shouldBe "js1"
        SourcePreferences(preferenceStore).novelExtensionUpdatesCount().get() shouldBe 1
    }

    private fun novelRunner(preferenceStore: PreferenceStore = FakePreferenceStore()): ExtensionAutoUpdateRunner =
        ExtensionAutoUpdateRunner(
            basePreferences = BasePreferences(
                context = mockk<Context>(relaxed = true),
                preferenceStore = preferenceStore,
            ),
            sourcePreferences = SourcePreferences(preferenceStore),
            mangaExtensionManager = mockk(relaxed = true),
            animeExtensionManager = mockk(relaxed = true),
            novelExtensionManager = ExtensionAutoUpdateRunner.NovelExtensionManagerProvider(),
        )

    private fun novelInstalled(
        id: String,
        versionCode: Int,
        repoUrl: String,
        isKotlinExtension: Boolean = true,
        isShared: Boolean = false,
    ): NovelPlugin.Installed = NovelPlugin.Installed(
        id = id,
        name = "Plugin $id",
        site = "",
        lang = "en",
        versionCode = versionCode,
        versionName = "$versionCode.0",
        url = "",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "",
        repoUrl = repoUrl,
        isKotlinExtension = isKotlinExtension,
        isShared = isShared,
    )

    private fun novelAvailable(
        id: String,
        versionCode: Int,
        repoUrl: String,
        sha256: String,
        isKotlinExtension: Boolean = true,
    ): NovelPlugin.Available = NovelPlugin.Available(
        id = id,
        name = "Plugin $id",
        site = "",
        lang = "en",
        versionCode = versionCode,
        versionName = "$versionCode.0",
        url = "",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = sha256,
        repoUrl = repoUrl,
        isKotlinExtension = isKotlinExtension,
    )

    private class FakeAutoUpdateNovelManager(
        installed: List<NovelPlugin.Installed>,
        available: List<NovelPlugin.Available>,
    ) : NovelExtensionManager {

        /** Plugins passed to [installPlugin], in call order — what the auto-updater would install. */
        val installedPlugins = mutableListOf<NovelPlugin.Installed>()

        private val installedFlow = MutableStateFlow(installed)
        override val installedSourcesFlow: Flow<List<NovelSource>> = MutableStateFlow(emptyList())
        override val installedPluginsFlow: Flow<List<NovelPlugin.Installed>> = installedFlow
        override val availablePluginsFlow: Flow<List<NovelPlugin.Available>> = MutableStateFlow(available)
        override val untrustedPluginsFlow: Flow<List<NovelPlugin.Untrusted>> = MutableStateFlow(emptyList())
        override val updatesFlow: Flow<List<NovelPlugin.Installed>> = installedFlow
        override val signatureMismatchEvents: SharedFlow<NovelExtensionManager.SignatureMismatchEvent> =
            MutableSharedFlow()
        override val repoFetchErrors: Flow<Map<String, String>> = flowOf(emptyMap())

        override fun reportSignatureMismatch(pluginId: String) = Unit

        override suspend fun refreshAvailablePlugins() = Unit

        override suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed {
            val installedPlugin = plugin.toInstalled()
            installedPlugins += installedPlugin
            // Mirror DefaultNovelExtensionManager: installing replaces the same-id record.
            installedFlow.value = installedFlow.value.map { if (it.id == installedPlugin.id) installedPlugin else it }
            return installedPlugin
        }

        override suspend fun uninstallPlugin(plugin: NovelPlugin.Installed) = Unit
        override suspend fun uninstallPlugin(plugin: NovelPlugin.Untrusted) = Unit
        override suspend fun replacePluginFromRepo(
            installed: NovelPlugin.Installed,
            replacement: NovelPlugin.Available,
        ): NovelPlugin.Installed = installed
        override suspend fun trustPlugin(plugin: NovelPlugin.Untrusted) = Unit
        override suspend fun getSourceData(id: Long) = null
        override fun getPluginIconUrlForSource(sourceId: Long): String? = null
        override fun getCapabilitiesForSource(sourceId: Long): NovelPluginCapabilities? = null
        override fun getPluginId(sourceId: Long): String? = null
        override fun getPluginIdAsFlow(sourceId: Long): Flow<String?> = MutableStateFlow(null)
        override fun isNsfwForSource(sourceId: Long): Boolean = false
        override fun isNsfwForSourceAsFlow(sourceId: Long): Flow<Boolean> = MutableStateFlow(false)

        private fun NovelPlugin.Available.toInstalled(): NovelPlugin.Installed = NovelPlugin.Installed(
            id = id,
            name = name,
            site = site,
            lang = lang,
            versionCode = versionCode,
            versionName = versionName,
            url = url,
            iconUrl = iconUrl,
            customJs = customJs,
            customCss = customCss,
            hasSettings = hasSettings,
            sha256 = sha256,
            repoUrl = repoUrl,
            repoName = repoName,
            pkgName = pkgName,
            apkUrl = apkUrl,
            isKotlinExtension = isKotlinExtension,
            isNsfw = isNsfw,
        )
    }

    private class FakePreferenceStore : PreferenceStore {
        private val booleans = mutableMapOf<String, Preference<Boolean>>()
        private val ints = mutableMapOf<String, Preference<Int>>()
        private val stringSets = mutableMapOf<String, Preference<Set<String>>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            error("Not used")

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            error("Not used")

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            ints.getOrPut(key) { FakePreference(defaultValue) }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            error("Not used")

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            booleans.getOrPut(key) { FakePreference(defaultValue) }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            stringSets.getOrPut(key) { FakePreference(defaultValue) }

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = error("Not used")

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    }

    private class FakePreference<T>(initial: T) : Preference<T> {
        private val state = MutableStateFlow(initial)
        override fun key(): String = "fake"
        override fun get(): T = state.value
        override fun set(value: T) {
            state.value = value
        }
        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = state.value
        override fun changes(): Flow<T> = state
        override fun stateIn(scope: kotlinx.coroutines.CoroutineScope) = state
    }

    private companion object {
        const val PLUGIN_ID = "p1"
        const val REPO_A = "https://repos.example/a"
        const val REPO_B = "https://repos.example/b"
    }
}
