package eu.kanade.tachiyomi.ui.browse.novel.extension

import android.app.Application
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.ApkInstallRequest
import eu.kanade.tachiyomi.extension.installer.ApkInstallResult
import eu.kanade.tachiyomi.extension.installer.ApkUninstallRequest
import eu.kanade.tachiyomi.extension.installer.UnifiedApkExtensionInstaller
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginCapabilities
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginIdentitySource
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginSettingsSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.data.extension.novel.toInstalled
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.novel.model.StubNovelSource
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class NovelExtensionsScreenModelTest {

    private val sourcePreferences: SourcePreferences = mockk(relaxed = true)
    private val basePreferences: BasePreferences = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)
    private val enabledLanguages = MutableStateFlow(setOf("en"))
    private val showNsfwSources = MutableStateFlow(true)
    private val activeScreenModels = mutableListOf<NovelExtensionsScreenModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val enabledLanguagesPreference = mockk<Preference<Set<String>>>()
        every { enabledLanguagesPreference.changes() } returns enabledLanguages
        every { sourcePreferences.enabledLanguages() } returns enabledLanguagesPreference
        val showNsfwPreference = mockk<Preference<Boolean>>()
        every { showNsfwPreference.changes() } returns showNsfwSources
        every { sourcePreferences.showNsfwSource() } returns showNsfwPreference
    }

    private fun createScreenModel(
        extensionManager: NovelExtensionManager,
        installCoordinator: UnifiedApkExtensionInstaller = FakeUnifiedApkExtensionInstaller(),
    ): NovelExtensionsScreenModel {
        return NovelExtensionsScreenModel(
            extensionManager = extensionManager,
            sourcePreferences = sourcePreferences,
            context = application,
            basePreferences = basePreferences,
            installCoordinator = installCoordinator,
        ).also(activeScreenModels::add)
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
            val available = listOf(
                pluginAvailable("id-1", 2),
                pluginAvailable("id-2", 1),
            )

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = listOf(installed),
                    available = available,
                    updates = updates,
                ),
            )

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
    fun `deduplicates available plugins by id`() {
        runBlocking {
            val duplicate = pluginAvailable("id-dup", 1)

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = emptyList(),
                    available = listOf(duplicate, duplicate),
                    updates = emptyList(),
                ),
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            screenModel.state.value.items.count { it.plugin.id == "id-dup" } shouldBe 1
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
            val available = listOf(
                pluginAvailable("id-1", 2),
                pluginAvailable("id-2", 2),
            )

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = updates,
                    available = available,
                    updates = updates,
                ),
            )

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

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = emptyList(),
                    available = listOf(available),
                    updates = emptyList(),
                ),
            )

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
    fun `nsfw plugins are hidden while show nsfw sources is off`() {
        runBlocking {
            showNsfwSources.value = false
            val safe = pluginAvailable("id-safe", 1)
            val adult = pluginAvailable("id-adult", 1).copy(isNsfw = true)

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = emptyList(),
                    available = listOf(safe, adult),
                    updates = emptyList(),
                ),
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            screenModel.state.value.items.map { it.plugin.id } shouldBe listOf("id-safe")
        }
    }

    @Test
    fun `available plugins are filtered by enabled language`() {
        runBlocking {
            val english = pluginAvailable("id-en", 1).copy(lang = "en")
            val russian = pluginAvailable("id-ru", 1).copy(lang = "ru")

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = emptyList(),
                    available = listOf(english, russian),
                    updates = emptyList(),
                ),
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            screenModel.state.value.items.map { it.plugin.id } shouldBe listOf("id-en")
        }
    }

    @Test
    fun `untrusted plugins are listed as installed items without sources`() {
        runBlocking {
            val untrusted = pluginUntrusted("pkg.untrusted")

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = emptyList(),
                    available = emptyList(),
                    updates = emptyList(),
                    untrusted = listOf(untrusted),
                ),
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            val item = screenModel.state.value.items.single()
            item.plugin shouldBe untrusted
            item.status shouldBe NovelExtensionItem.Status.Untrusted
            item.settingsSourceId shouldBe null
        }
    }

    @Test
    fun `trust delegates to extension manager`() {
        runBlocking {
            val untrusted = pluginUntrusted("pkg.trust")
            val extensionManager = FakeNovelExtensionManager(
                installed = emptyList(),
                available = emptyList(),
                updates = emptyList(),
                untrusted = listOf(untrusted),
            )
            val screenModel = createScreenModel(extensionManager)

            screenModel.trust(untrusted)

            withTimeout(1_000) {
                while (extensionManager.trustedPlugin == null) {
                    yield()
                }
            }

            extensionManager.trustedPlugin shouldBe untrusted
        }
    }

    @Test
    fun `failed install marks plugin as error instead of crashing`() {
        runBlocking {
            val available = pluginAvailable("id-timeout", 1)

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = emptyList(),
                    available = listOf(available),
                    updates = emptyList(),
                    installFailure = IOException("timeout"),
                ),
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            screenModel.installExtension(available)

            withTimeout(1_000) {
                while (
                    screenModel.state.value.items
                        .first { it.plugin.id == "id-timeout" }
                        .installStep != InstallStep.Error
                ) {
                    yield()
                }
            }

            screenModel.state.value.items
                .first { it.plugin.id == "id-timeout" }
                .installStep shouldBe InstallStep.Error
        }
    }

    @Test
    fun `installed plugin row gets settings from installed source discovery`() {
        runBlocking {
            val installed = pluginInstalled("komga", 1)

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = listOf(installed),
                    installedSources = listOf(
                        FakeNovelPluginSource(
                            pluginId = "komga",
                            hasSettings = true,
                        ),
                    ),
                    available = emptyList(),
                    updates = emptyList(),
                ),
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            val item = screenModel.state.value.items.first { it.plugin.id == "komga" }
            item.hasSettings shouldBe true
            item.settingsSourceId shouldBe 1L
        }
    }

    @Test
    fun `installed kotlin plugin settings uses actual source id instead of plugin hash`() {
        runBlocking {
            val installed = pluginInstalled("eu.kanade.tachiyomi.novelextension.all.shosetsu", 1)

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = listOf(installed),
                    installedSources = listOf(
                        FakeNovelPluginSource(
                            id = 42L,
                            pluginId = "eu.kanade.tachiyomi.novelextension.all.shosetsu",
                            hasSettings = true,
                        ),
                    ),
                    available = emptyList(),
                    updates = emptyList(),
                ),
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            val item = screenModel.state.value.items.first {
                it.plugin.id == "eu.kanade.tachiyomi.novelextension.all.shosetsu"
            }
            item.settingsSourceId shouldBe 42L
        }
    }

    @Test
    fun `update all extensions waits for each install to finish`() {
        runBlocking {
            val installed = listOf(
                pluginInstalled("id-1", 1),
                pluginInstalled("id-2", 1),
            )
            val available = listOf(
                pluginAvailable("id-1", 2),
                pluginAvailable("id-2", 2),
            )
            val installStarted = AtomicInteger(0)
            val firstInstallStarted = CompletableDeferred<Unit>()
            val releaseInstall = CompletableDeferred<Unit>()

            val extensionManager = mockk<NovelExtensionManager>(relaxed = true)
            every { extensionManager.installedSourcesFlow } returns MutableStateFlow(emptyList())
            every { extensionManager.installedPluginsFlow } returns MutableStateFlow(installed)
            every { extensionManager.availablePluginsFlow } returns MutableStateFlow(available)
            every { extensionManager.untrustedPluginsFlow } returns MutableStateFlow(emptyList())
            every { extensionManager.updatesFlow } returns MutableStateFlow(installed)
            coEvery { extensionManager.refreshAvailablePlugins() } returns Unit
            coEvery { extensionManager.installPlugin(available[0]) } coAnswers {
                if (installStarted.incrementAndGet() == 1) {
                    firstInstallStarted.complete(Unit)
                }
                releaseInstall.await()
                available[0].toInstalled()
            }
            coEvery { extensionManager.installPlugin(available[1]) } coAnswers {
                installStarted.incrementAndGet()
                releaseInstall.await()
                available[1].toInstalled()
            }

            val screenModel = createScreenModel(extensionManager)

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            screenModel.updateAllExtensions()

            withTimeout(1_000) {
                firstInstallStarted.await()
            }

            repeat(5) {
                yield()
            }

            installStarted.get() shouldBe 1

            releaseInstall.complete(Unit)

            withTimeout(1_000) {
                while (installStarted.get() < 2) {
                    yield()
                }
            }

            installStarted.get() shouldBe 2
        }
    }

    @Test
    fun `untracked install mirrored into currentDownloads clears on terminal step`() {
        runBlocking {
            val installed = pluginInstalled("kid", 1).copy(pkgName = "org.kid")
            val installer = FakeUnifiedApkExtensionInstaller()

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = listOf(installed),
                    available = emptyList(),
                    updates = emptyList(),
                ),
                installCoordinator = installer,
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            // Install started outside this model: only the coordinator store knows about it.
            installer.emit("org.kid", InstallStep.Installing)

            withTimeout(1_000) {
                while (
                    screenModel.state.value.items
                        .first { it.plugin.id == "kid" }
                        .installStep != InstallStep.Installing
                ) {
                    yield()
                }
            }

            screenModel.state.value.items
                .first { it.plugin.id == "kid" }
                .installStep shouldBe InstallStep.Installing

            installer.emit("org.kid", InstallStep.Error)

            withTimeout(1_000) {
                while (
                    screenModel.state.value.items
                        .first { it.plugin.id == "kid" }
                        .installStep != InstallStep.Idle
                ) {
                    yield()
                }
            }

            screenModel.state.value.items
                .first { it.plugin.id == "kid" }
                .installStep shouldBe InstallStep.Idle
        }
    }

    @Test
    fun `terminal store step keeps deliberate error row with diagnostics`() {
        runBlocking {
            val installed = pluginInstalled("kid", 1).copy(pkgName = "org.kid")
            val replacement = pluginAvailable("kid", 2)
            val installer = FakeUnifiedApkExtensionInstaller()

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = listOf(installed),
                    available = listOf(replacement),
                    updates = emptyList(),
                    installFailure = IOException("reinstall failed"),
                ),
                installCoordinator = installer,
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            installer.emit("org.kid", InstallStep.Installing)

            withTimeout(1_000) {
                while (
                    screenModel.state.value.items
                        .first { it.plugin.id == "kid" }
                        .installStep != InstallStep.Installing
                ) {
                    yield()
                }
            }

            // A tracked reinstall failure creates a deliberate Error display plus a diagnostic.
            screenModel.reinstallFromRepo(installed, replacement)

            withTimeout(1_000) {
                while (
                    screenModel.state.value.items
                        .first { it.plugin.id == "kid" }
                        .installStep != InstallStep.Error
                ) {
                    yield()
                }
            }

            // The store reaching a completed step must not clobber the deliberate Error row.
            installer.emit("org.kid", InstallStep.Error)
            delay(200)

            screenModel.state.value.items
                .first { it.plugin.id == "kid" }
                .installStep shouldBe InstallStep.Error
        }
    }

    @Test
    fun `untracked mirror step cannot flip diagnostic owned row or strand non-terminal state`() {
        runBlocking {
            val installed = pluginInstalled("kid", 1).copy(pkgName = "org.kid")
            val replacement = pluginAvailable("kid", 2)
            val installer = FakeUnifiedApkExtensionInstaller()

            val screenModel = createScreenModel(
                FakeNovelExtensionManager(
                    installed = listOf(installed),
                    available = listOf(replacement),
                    updates = emptyList(),
                    installFailure = IOException("reinstall failed"),
                ),
                installCoordinator = installer,
            )

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            // Untracked install mirrored before any diagnostic exists: this observer owns the row.
            installer.emit("org.kid", InstallStep.Installing)

            withTimeout(1_000) {
                while (
                    screenModel.state.value.items
                        .first { it.plugin.id == "kid" }
                        .installStep != InstallStep.Installing
                ) {
                    yield()
                }
            }

            // A tracked reinstall failure creates a deliberate Error display plus a diagnostic,
            // while the earlier mirror still holds ownership of the same row.
            screenModel.reinstallFromRepo(installed, replacement)

            withTimeout(1_000) {
                while (
                    screenModel.state.value.items
                        .first { it.plugin.id == "kid" }
                        .installStep != InstallStep.Error
                ) {
                    yield()
                }
            }

            // A later untracked store-driven progress step (a DISTINCT step value, so the store
            // flow actually re-emits) must not flip the diagnostics-owned row.
            installer.emit("org.kid", InstallStep.Downloading)
            delay(200)

            screenModel.state.value.items
                .first { it.plugin.id == "kid" }
                .installStep shouldBe InstallStep.Error

            // On the terminal step no non-terminal entry may remain anywhere: ownership is kept
            // for the diagnostics-owned key so cleanup is retried, never stranded.
            installer.emit("org.kid", InstallStep.Error)
            delay(200)

            val nonTerminal = setOf(
                InstallStep.Pending,
                InstallStep.Downloading,
                InstallStep.Installing,
            )
            screenModel.state.value.items.none { it.installStep in nonTerminal } shouldBe true
            screenModel.state.value.items
                .first { it.plugin.id == "kid" }
                .installStep shouldBe InstallStep.Error
        }
    }

    @Test
    fun `signature-mismatch reinstall failure surfaces row error instead of crashing`() {
        runBlocking {
            val installed = pluginInstalled("kid", 2)
            val candidate = pluginAvailable("kid", 3)

            val extensionManager = FakeNovelExtensionManager(
                installed = listOf(installed),
                available = listOf(candidate),
                updates = emptyList(),
            )
            extensionManager.nextReplaceError = IllegalStateException("Checksum mismatch")

            val screenModel = createScreenModel(extensionManager)

            withTimeout(1_000) {
                while (screenModel.state.value.isLoading) {
                    yield()
                }
            }

            // Arranged through the same flow the model's collector reads: no test-only hook.
            extensionManager.emitSignatureMismatch(
                NovelExtensionManager.SignatureMismatchEvent(
                    pluginId = "kid",
                    displayName = installed.name,
                    candidate = candidate,
                ),
            )
            withTimeout(1_000) {
                while (screenModel.state.value.signatureMismatchEvent == null) {
                    yield()
                }
            }

            screenModel.reinstallAfterSignatureMismatch()

            // Terminal state reached without an exception escaping the coroutine
            // (before the fix the bare launch crashes and the row never shows Error).
            withTimeout(1_000) {
                while (
                    screenModel.state.value.items
                        .first { it.plugin.id == "kid" }
                        .installStep != InstallStep.Error
                ) {
                    yield()
                }
            }

            screenModel.state.value.items
                .first { it.plugin.id == "kid" }
                .installStep shouldBe InstallStep.Error
            screenModel.state.value.signatureMismatchEvent shouldBe null
        }
    }

    @Test
    fun `second install request for same plugin is ignored while first runs`() {
        runBlocking {
            val available = pluginAvailable("pid", 1)
            val extensionManager = FakeNovelExtensionManager(
                installed = emptyList(),
                available = listOf(available),
                updates = emptyList(),
            )
            extensionManager.installGate = CompletableDeferred()
            val screenModel = createScreenModel(extensionManager)
            awaitWithin { !screenModel.state.value.isLoading }

            screenModel.installExtension(available)
            awaitWithin { extensionManager.installStarts == 1 }

            // Duplicate tap while the first install is still in flight.
            screenModel.installExtension(available)

            extensionManager.installStarts shouldBe 1

            // Once the first finishes, a fresh request is allowed again.
            extensionManager.installGate?.complete(Unit)
            awaitWithin {
                screenModel.state.value.items.none { it.plugin.id == "pid" && !it.installStep.isCompleted() }
            }
            screenModel.installExtension(available)
            awaitWithin { extensionManager.installStarts == 2 }
        }
    }

    @Test
    fun `cancelInstall stops the tracked install and notifies manager`() {
        runBlocking {
            val available = pluginAvailable("pid", 1)
            val extensionManager = FakeNovelExtensionManager(
                installed = emptyList(),
                available = listOf(available),
                updates = emptyList(),
            )
            extensionManager.installGate = CompletableDeferred()
            val screenModel = createScreenModel(extensionManager)
            awaitWithin { !screenModel.state.value.isLoading }

            screenModel.installExtension(available)
            awaitWithin { extensionManager.installStarts == 1 }

            screenModel.cancelInstall(available)

            awaitWithin {
                screenModel.state.value.items.none { it.plugin.id == "pid" && !it.installStep.isCompleted() }
            }
            extensionManager.cancelledIds shouldBe listOf("pid")

            // The cancelled job must not report any further completion work.
            extensionManager.installGate?.complete(Unit)
            delay(100)
            extensionManager.installStarts shouldBe 1
        }
    }

    @Test
    fun `update-all applies updates hidden by search filters`() {
        runBlocking {
            val installed = pluginInstalled("kid", 1)
            val newer = pluginAvailable("kid", 2)
            val extensionManager = FakeNovelExtensionManager(
                installed = listOf(installed),
                available = listOf(newer),
                updates = listOf(installed),
            )
            val screenModel = createScreenModel(extensionManager)
            awaitWithin { !screenModel.state.value.isLoading }

            screenModel.search("zzz-no-match")
            awaitWithin { screenModel.state.value.items.isEmpty() }

            screenModel.updateAllExtensions()

            awaitWithin { extensionManager.installStarts == 1 }
        }
    }

    @Test
    fun `update-all waits while signature mismatch dialog is open`() {
        runBlocking {
            val firstInstalled = pluginInstalled("kid-a", 1)
            val secondInstalled = pluginInstalled("kid-b", 1)
            val extensionManager = FakeNovelExtensionManager(
                installed = listOf(firstInstalled, secondInstalled),
                available = listOf(pluginAvailable("kid-a", 2), pluginAvailable("kid-b", 2)),
                updates = listOf(firstInstalled, secondInstalled),
            )
            extensionManager.installGate = CompletableDeferred()
            val screenModel = createScreenModel(extensionManager)
            awaitWithin { !screenModel.state.value.isLoading }

            screenModel.updateAllExtensions()
            awaitWithin { extensionManager.installStarts == 1 } // kid-a installing, held by gate

            extensionManager.emitSignatureMismatch(
                NovelExtensionManager.SignatureMismatchEvent(
                    pluginId = "kid-b",
                    displayName = secondInstalled.name,
                    candidate = pluginAvailable("kid-b", 2),
                ),
            )
            awaitWithin { screenModel.state.value.signatureMismatchEvent != null }

            // kid-a finishes; the queue must suspend on the open mismatch dialog, not drain on.
            extensionManager.installGate?.complete(Unit)
            delay(250)
            extensionManager.installStarts shouldBe 1

            screenModel.dismissSignatureMismatch()
            awaitWithin { extensionManager.installStarts == 2 }
            screenModel.state.value.signatureMismatchEvent shouldBe null
        }
    }

    @Test
    fun `update-all pauses on a reinstall-needing plugin and applies the chosen candidate`() {
        runBlocking {
            val installed = pluginInstalled("kid", 1)
            val crossRepo = pluginAvailable("kid", 2).copy(
                repoUrl = "https://other.example/index.json",
                repoName = "Other",
            )
            val extensionManager = FakeNovelExtensionManager(
                installed = listOf(installed),
                available = listOf(crossRepo),
                updates = listOf(installed),
            )
            val screenModel = createScreenModel(extensionManager)
            awaitWithin { !screenModel.state.value.isLoading }

            screenModel.updateAllExtensions()

            // B5 (manga/anime parity): the queue suspends on the reinstall dialog instead of
            // silently skipping the plugin, and nothing installs until the user decides.
            awaitWithin { screenModel.state.value.queuedReinstallPlugin?.id == "kid" }
            extensionManager.installStarts shouldBe 0

            screenModel.resolveQueuedReinstall(crossRepo)

            awaitWithin { extensionManager.installStarts == 1 }
            screenModel.state.value.queuedReinstallPlugin shouldBe null
        }
    }

    @Test
    fun `update-all skips a reinstall-needing plugin when the queued dialog is dismissed`() {
        runBlocking {
            val installed = pluginInstalled("kid", 1)
            val crossRepo = pluginAvailable("kid", 2).copy(
                repoUrl = "https://other.example/index.json",
                repoName = "Other",
            )
            val extensionManager = FakeNovelExtensionManager(
                installed = listOf(installed),
                available = listOf(crossRepo),
                updates = listOf(installed),
            )
            val screenModel = createScreenModel(extensionManager)
            awaitWithin { !screenModel.state.value.isLoading }

            screenModel.updateAllExtensions()
            awaitWithin { screenModel.state.value.queuedReinstallPlugin?.id == "kid" }

            screenModel.resolveQueuedReinstall(null)

            awaitWithin { screenModel.state.value.queuedReinstallPlugin == null }
            delay(100)
            extensionManager.installStarts shouldBe 0
        }
    }

    @Test
    fun `installed repo label follows a store rename`() {
        runBlocking {
            val installed = pluginInstalled("kid", 1).copy(repoName = "Old Store Name")
            // Same repoUrl as the installed snapshot, published under the store's new name.
            val renamedVariant = pluginAvailable("kid", 1).copy(repoName = "My Shop")
            val extensionManager = FakeNovelExtensionManager(
                installed = listOf(installed),
                available = listOf(renamedVariant),
                updates = emptyList(),
            )
            val screenModel = createScreenModel(extensionManager)
            awaitWithin { !screenModel.state.value.isLoading }

            val item = screenModel.state.value.items.first { it.plugin.id == "kid" }
            item.repoDisplayName shouldBe "My Shop"
        }
    }

    private suspend fun awaitWithin(millis: Long = 1_000, condition: () -> Boolean) {
        withTimeout(millis) {
            while (!condition()) yield()
        }
    }

    private fun pluginUntrusted(id: String) = NovelPlugin.Untrusted(
        id = id,
        name = "Source $id",
        site = "",
        lang = "",
        versionCode = 1,
        versionName = "1",
        url = "",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "",
        repoUrl = "",
        pkgName = id,
        signatureHash = "signature",
    )

    private fun pluginAvailable(id: String, version: Int) = NovelPlugin.Available(
        id = id,
        name = "Source $id",
        site = "Example",
        lang = "en",
        versionCode = version,
        versionName = version.toString(),
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
        versionCode = version,
        versionName = version.toString(),
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
        installedSources: List<NovelSource> = emptyList(),
        available: List<NovelPlugin.Available>,
        updates: List<NovelPlugin.Installed>,
        untrusted: List<NovelPlugin.Untrusted> = emptyList(),
        private val installFailure: Throwable? = null,
    ) : NovelExtensionManager {
        var trustedPlugin: NovelPlugin.Untrusted? = null
            private set

        /** When set, the next [replacePluginFromRepo] call throws it once, then clears. */
        var nextReplaceError: Throwable? = null

        /** When set, [installPlugin] suspends until it is completed (single-flight tests). */
        var installGate: CompletableDeferred<Unit>? = null

        var installStarts: Int = 0
            private set

        val cancelledIds = mutableListOf<String>()

        private val signatureMismatchEventsMutable =
            MutableSharedFlow<NovelExtensionManager.SignatureMismatchEvent>(replay = 1)

        /** Test arrangement helper: the model's collector copies this into state. */
        suspend fun emitSignatureMismatch(event: NovelExtensionManager.SignatureMismatchEvent) {
            signatureMismatchEventsMutable.emit(event)
        }

        override val installedSourcesFlow: Flow<List<NovelSource>> =
            MutableStateFlow(installedSources)
        override val installedPluginsFlow: Flow<List<NovelPlugin.Installed>> =
            MutableStateFlow(installed)
        override val availablePluginsFlow: Flow<List<NovelPlugin.Available>> =
            MutableStateFlow(available)
        override val untrustedPluginsFlow: Flow<List<NovelPlugin.Untrusted>> =
            MutableStateFlow(untrusted)
        override val updatesFlow: Flow<List<NovelPlugin.Installed>> =
            MutableStateFlow(updates)
        override val signatureMismatchEvents: SharedFlow<NovelExtensionManager.SignatureMismatchEvent> =
            signatureMismatchEventsMutable
        override fun reportSignatureMismatch(pluginId: String) = Unit
        override val repoFetchErrors: Flow<Map<String, String>> = flowOf(emptyMap())

        override suspend fun refreshAvailablePlugins() = Unit

        override suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed {
            installStarts++
            installGate?.await()
            installFailure?.let { throw it }
            return plugin.toInstalled()
        }

        override fun cancelPluginInstall(plugin: NovelPlugin.Available) {
            cancelledIds += plugin.id
        }

        override fun cancelPluginInstall(plugin: NovelPlugin.Installed) {
            cancelledIds += plugin.id
        }

        override suspend fun uninstallPlugin(plugin: NovelPlugin.Installed) = Unit

        override suspend fun uninstallPlugin(plugin: NovelPlugin.Untrusted) = Unit

        override suspend fun trustPlugin(plugin: NovelPlugin.Untrusted) {
            trustedPlugin = plugin
        }

        override suspend fun replacePluginFromRepo(
            installed: NovelPlugin.Installed,
            replacement: NovelPlugin.Available,
        ): NovelPlugin.Installed {
            nextReplaceError?.let { error ->
                nextReplaceError = null
                throw error
            }
            uninstallPlugin(installed)
            return installPlugin(replacement)
        }

        override suspend fun getSourceData(id: Long): StubNovelSource? = null

        override fun getPluginIconUrlForSource(sourceId: Long): String? = null

        override fun getCapabilitiesForSource(sourceId: Long): NovelPluginCapabilities? = null

        override fun getPluginId(sourceId: Long): String? = null

        override fun getPluginIdAsFlow(sourceId: Long): Flow<String?> = MutableStateFlow(null)

        override fun isNsfwForSource(sourceId: Long): Boolean = false

        override fun isNsfwForSourceAsFlow(sourceId: Long): Flow<Boolean> = MutableStateFlow(false)
    }

    private class FakeNovelPluginSource(
        override val id: Long = 1L,
        override val name: String = "Plugin source",
        override val lang: String = "en",
        override val pluginId: String,
        private val hasSettings: Boolean,
    ) : NovelSource, NovelPluginIdentitySource, NovelPluginSettingsSource {
        override fun hasPluginSettings(discoverRuntime: Boolean): Boolean = hasSettings
    }

    /** Scripts installer-store steps by package so tests can drive [installCoordinator.observe]. */
    private class FakeUnifiedApkExtensionInstaller : UnifiedApkExtensionInstaller {
        val stepsByPkgName = MutableStateFlow<Map<String, InstallStep>>(emptyMap())

        fun emit(pkgName: String, step: InstallStep) {
            stepsByPkgName.update { it + (pkgName to step) }
        }

        override fun install(request: ApkInstallRequest): Flow<InstallStep> = error("not used")

        override fun observe(packageName: String): Flow<InstallStep> =
            stepsByPkgName.map { it[packageName] ?: InstallStep.Idle }

        override fun cancel(packageName: String) = Unit

        override suspend fun uninstall(request: ApkUninstallRequest): ApkInstallResult =
            ApkInstallResult.Installed
    }
}
