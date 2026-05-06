package eu.kanade.presentation.more.settings.screen.browse

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.CreateNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.DeleteNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.ReplaceNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NovelExtensionReposScreenModelTest {

    private val getExtensionRepo: GetNovelExtensionRepo = mockk()
    private val createExtensionRepo: CreateNovelExtensionRepo = mockk(relaxed = true)
    private val deleteExtensionRepo: DeleteNovelExtensionRepo = mockk(relaxed = true)
    private val replaceExtensionRepo: ReplaceNovelExtensionRepo = mockk(relaxed = true)
    private val updateExtensionRepo: UpdateNovelExtensionRepo = mockk(relaxed = true)
    private val extensionManager: NovelExtensionManager = mockk(relaxed = true)
    private val application: Application = mockk()
    private val migrationPrefs: SharedPreferences = mockk()
    private val activeScreenModels = mutableListOf<NovelExtensionReposScreenModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        every { application.getSharedPreferences("novel_extension_repo_prefs", 0) } returns migrationPrefs
        every { migrationPrefs.getBoolean(CreateNovelExtensionRepo.MIGRATION_DONE_KEY, false) } returns true
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
    fun `loads repos into success state`() {
        runBlocking {
            val repo = ExtensionRepo(
                baseUrl = "https://example.org",
                name = "Repo",
                shortName = "repo",
                website = "https://example.org",
                signingKeyFingerprint = "fingerprint",
            )
            every { getExtensionRepo.subscribeAll() } returns flowOf(listOf(repo))

            val screenModel = NovelExtensionReposScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,
                application = application,
            ).also(activeScreenModels::add)

            withTimeout(1_000) {
                while (screenModel.state.value !is RepoScreenState.Success) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<RepoScreenState.Success>()
            (state as RepoScreenState.Success).repos.first() shouldBe repo
        }
    }

    @Test
    fun `create repo refreshes available plugins`() {
        runBlocking {
            every { getExtensionRepo.subscribeAll() } returns flowOf(emptyList())
            coEvery { createExtensionRepo.await("https://example.org/plugins.min.json") } returns
                CreateNovelExtensionRepo.Result.Success
            coEvery { extensionManager.refreshAvailablePlugins() } returns Unit

            val screenModel = NovelExtensionReposScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,
                application = application,
            ).also(activeScreenModels::add)

            screenModel.createRepo("https://example.org/plugins.min.json")

            withTimeout(1_000) {
                while (true) {
                    runCatching {
                        coVerify(exactly = 1) { extensionManager.refreshAvailablePlugins() }
                    }.onSuccess { return@withTimeout }
                    yield()
                }
            }
        }
    }

    @Test
    fun `rename repo refreshes available plugins`() {
        runBlocking {
            val repo = ExtensionRepo(
                baseUrl = "https://example.org",
                name = "Old name",
                shortName = "repo",
                website = "https://example.org",
                signingKeyFingerprint = "fingerprint",
            )
            every { getExtensionRepo.subscribeAll() } returns flowOf(listOf(repo))
            coEvery { replaceExtensionRepo.await(repo.copy(name = "New name")) } returns Unit
            coEvery { extensionManager.refreshAvailablePlugins() } returns Unit

            val screenModel = NovelExtensionReposScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,
                application = application,
            ).also(activeScreenModels::add)

            screenModel.renameRepo(repo, "New name")

            withTimeout(1_000) {
                while (true) {
                    runCatching {
                        coVerify(exactly = 1) { replaceExtensionRepo.await(repo.copy(name = "New name")) }
                        coVerify(exactly = 1) { extensionManager.refreshAvailablePlugins() }
                    }.onSuccess { return@withTimeout }
                    yield()
                }
            }
        }
    }
}
