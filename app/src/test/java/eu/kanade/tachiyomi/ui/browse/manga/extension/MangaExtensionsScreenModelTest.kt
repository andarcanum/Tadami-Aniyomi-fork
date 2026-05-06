package eu.kanade.tachiyomi.ui.browse.manga.extension

import android.app.Application
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.base.ExtensionInstallerPreference
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.extension.manga.model.MangaExtensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get
import java.util.Collections

class MangaExtensionsScreenModelTest {

    private val sourcePreferences: SourcePreferences = mockk(relaxed = true)
    private val basePreferences: BasePreferences = mockk(relaxed = true)
    private val enabledLanguages = MutableStateFlow(setOf("en"))
    private val updatesCount = MutableStateFlow(0)
    private val installer = MutableStateFlow(BasePreferences.ExtensionInstaller.PACKAGEINSTALLER)
    private val activeScreenModels = mutableListOf<MangaExtensionsScreenModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runCatching { Injekt.get<Application>() }
            .getOrElse {
                Injekt.addSingleton(fullType<Application>(), mockk(relaxed = true))
            }
        val enabledLanguagesPreference = mockk<Preference<Set<String>>>()
        every { enabledLanguagesPreference.changes() } returns enabledLanguages
        every { sourcePreferences.enabledLanguages() } returns enabledLanguagesPreference

        val updatesPreference = mockk<Preference<Int>>()
        every { updatesPreference.changes() } returns updatesCount
        every { sourcePreferences.mangaExtensionUpdatesCount() } returns updatesPreference

        val installerPreference = mockk<ExtensionInstallerPreference>()
        every { installerPreference.changes() } returns installer
        every { basePreferences.extensionInstaller() } returns installerPreference
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
    fun `deduplicates available variants by package name and keeps newest`() {
        runBlocking {
            val source = mangaSource()
            val lowerVariant = mangaAvailable(
                pkgName = "pkg.example-1",
                versionCode = 10,
                repoName = "Alpha Repo",
                repoUrl = "https://alpha.example",
                source = source,
            )
            val newestVariant = mangaAvailable(
                pkgName = "pkg.example-1",
                versionCode = 11,
                repoName = "Beta Repo",
                repoUrl = "https://beta.example",
                source = source,
            )

            val screenModel = createScreenModel(
                installed = emptyList<MangaExtension.Installed>(),
                available = listOf(lowerVariant, newestVariant),
                rawAvailable = emptyList<MangaExtension.Available>(),
            )

            waitUntilLoaded(screenModel)

            val visibleItems = screenModel.state.value.items.values.flatten()
            visibleItems.size shouldBe 1
            val visibleExtension = visibleItems.single().extension as MangaExtension.Available
            visibleExtension.versionCode shouldBe 11
            visibleExtension.repoName shouldBe "Beta Repo"
        }
    }

    @Test
    fun `installing duplicated package opens repo picker`() {
        runBlocking {
            val source = mangaSource()
            val lowerVariant = mangaAvailable(
                pkgName = "pkg.example-1",
                versionCode = 10,
                repoName = "Alpha Repo",
                repoUrl = "https://alpha.example",
                source = source,
            )
            val newestVariant = mangaAvailable(
                pkgName = "pkg.example-1",
                versionCode = 11,
                repoName = "Beta Repo",
                repoUrl = "https://beta.example",
                source = source,
            )

            val screenModel = createScreenModel(
                installed = emptyList<MangaExtension.Installed>(),
                available = listOf(lowerVariant, newestVariant),
                rawAvailable = emptyList<MangaExtension.Available>(),
            )

            waitUntilLoaded(screenModel)

            screenModel.installExtension(newestVariant)
            waitUntilRepoPickerShown(screenModel)

            screenModel.state.value.repoPickerPluginId shouldBe "pkg.example-1"
            screenModel.state.value.repoPickerOptions.map { it.versionCode } shouldBe listOf(11L, 10L)
        }
    }

    @Test
    fun `update all skips reinstall required extensions`() {
        runBlocking {
            val normalUpdate = mangaInstalled(
                pkgName = "pkg.normal",
                versionCode = 10,
            )
            val reinstallRequired = mangaInstalled(
                pkgName = "pkg.reinstall",
                versionCode = 11,
                needsReinstall = true,
                repoUrl = "https://alpha.example",
            )
            val extensionManager = mockk<MangaExtensionManager>(relaxed = true)
            val updateCalls = Collections.synchronizedList(mutableListOf<MangaExtension.Installed>())
            val screenModel = createScreenModel(
                installed = listOf(normalUpdate, reinstallRequired),
                available = emptyList<MangaExtension.Available>(),
                rawAvailable = emptyList<MangaExtension.Available>(),
                extensionManager = extensionManager,
            )
            every { extensionManager.updateExtension(any()) } answers {
                updateCalls += args[0] as MangaExtension.Installed
                flowOf(InstallStep.Installed)
            }

            waitUntilLoaded(screenModel)
            screenModel.updateAllExtensions()
            withTimeout(1_000) {
                while (updateCalls.isEmpty()) {
                    yield()
                }
            }

            updateCalls shouldBe listOf(normalUpdate)
        }
    }

    @Test
    fun `updating duplicated package opens repo picker`() {
        runBlocking {
            val source = mangaSource()
            val installed = mangaInstalled(
                pkgName = "pkg.example",
                versionCode = 9,
            )
            val lowerVariant = mangaAvailable(
                pkgName = "pkg.example",
                versionCode = 10,
                repoName = "Alpha Repo",
                repoUrl = "https://alpha.example",
                source = source,
            )
            val newestVariant = mangaAvailable(
                pkgName = "pkg.example",
                versionCode = 11,
                repoName = "Beta Repo",
                repoUrl = "https://beta.example",
                source = source,
            )

            val screenModel = createScreenModel(
                installed = listOf(installed),
                available = emptyList<MangaExtension.Available>(),
                rawAvailable = listOf(lowerVariant, newestVariant),
            )

            waitUntilLoaded(screenModel)

            screenModel.updateExtension(installed)
            waitUntilRepoPickerShown(screenModel)

            screenModel.state.value.repoPickerPluginId shouldBe "pkg.example"
            screenModel.state.value.repoPickerOptions.map { it.versionCode } shouldBe listOf(11L, 10L)
        }
    }

    private fun createScreenModel(
        installed: List<MangaExtension.Installed>,
        available: List<MangaExtension.Available>,
        rawAvailable: List<MangaExtension.Available>,
        extensionManager: MangaExtensionManager = mockk(relaxed = true),
    ): MangaExtensionsScreenModel {
        val getExtensions = mockk<GetMangaExtensionsByType>()
        every { getExtensions.subscribe() } returns MutableStateFlow(
            MangaExtensions(
                updates = installed.filter { it.hasUpdate },
                installed = installed,
                available = available,
                untrusted = emptyList(),
            ),
        )
        every { extensionManager.availableExtensionsFlow } returns MutableStateFlow(rawAvailable)
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(installed)
        every { extensionManager.untrustedExtensionsFlow } returns MutableStateFlow(emptyList())
        every { extensionManager.installExtension(any()) } returns flowOf(InstallStep.Installed)
        every { extensionManager.updateExtension(any()) } returns flowOf(InstallStep.Installed)
        coEvery { extensionManager.findAvailableExtensions() } returns Unit

        return MangaExtensionsScreenModel(
            preferences = sourcePreferences,
            basePreferences = basePreferences,
            extensionManager = extensionManager,
            getExtensions = getExtensions,
        ).also(activeScreenModels::add)
    }

    private suspend fun waitUntilLoaded(screenModel: MangaExtensionsScreenModel) {
        withTimeout(1_000) {
            while (screenModel.state.value.isLoading) {
                yield()
            }
        }
    }

    private suspend fun waitUntilRepoPickerShown(screenModel: MangaExtensionsScreenModel) {
        withTimeout(1_000) {
            while (screenModel.state.value.repoPickerPluginId == null) {
                yield()
            }
        }
    }

    private fun mangaSource() = MangaExtension.Available.MangaSource(
        id = 1,
        lang = "en",
        name = "Source",
        baseUrl = "https://example.org/source",
    )

    private fun mangaAvailable(
        pkgName: String,
        versionCode: Long,
        repoName: String,
        repoUrl: String,
        source: MangaExtension.Available.MangaSource,
    ) = MangaExtension.Available(
        name = "Source",
        pkgName = pkgName,
        versionName = versionCode.toString(),
        versionCode = versionCode,
        libVersion = 1.0,
        lang = "en",
        isNsfw = false,
        sources = listOf(source),
        apkName = "pkg.example.apk",
        iconUrl = "https://example.org/icon.png",
        repoUrl = repoUrl,
        repoName = repoName,
    )

    private fun mangaInstalled(
        pkgName: String,
        versionCode: Long,
        needsReinstall: Boolean = false,
        repoUrl: String = "https://example.org/repo",
    ) = MangaExtension.Installed(
        name = "Source",
        pkgName = pkgName,
        versionName = versionCode.toString(),
        versionCode = versionCode,
        libVersion = 1.0,
        lang = "en",
        isNsfw = false,
        pkgFactory = null,
        sources = listOf(mockk<MangaSource>(relaxed = true)),
        icon = null,
        hasUpdate = true,
        needsReinstall = needsReinstall,
        isObsolete = false,
        isShared = false,
        repoUrl = repoUrl,
    )
}
