package eu.kanade.presentation.more.settings.screen.browse

import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
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
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MangaExtensionReposScreenModelTest {

    private val getExtensionRepo: GetMangaExtensionRepo = mockk()
    private val createExtensionRepo: CreateMangaExtensionRepo = mockk(relaxed = true)
    private val deleteExtensionRepo: DeleteMangaExtensionRepo = mockk(relaxed = true)
    private val replaceExtensionRepo: ReplaceMangaExtensionRepo = mockk(relaxed = true)
    private val updateExtensionRepo: UpdateMangaExtensionRepo = mockk(relaxed = true)
    private val extensionManager: MangaExtensionManager = mockk(relaxed = true)
    private val activeScreenModels = mutableListOf<MangaExtensionReposScreenModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
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
            val repo = repo()
            every { getExtensionRepo.subscribeAll() } returns flowOf(listOf(repo))

            val screenModel = MangaExtensionReposScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,
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
    fun `create repo forwards display name and refreshes available plugins`() {
        runBlocking {
            every { getExtensionRepo.subscribeAll() } returns flowOf(emptyList())
            coEvery {
                createExtensionRepo.await(
                    "https://example.org/index.min.json",
                    "Custom repo",
                )
            } returns CreateMangaExtensionRepo.Result.Success
            coEvery { extensionManager.findAvailableExtensions() } returns Unit

            val screenModel = MangaExtensionReposScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,
            ).also(activeScreenModels::add)

            screenModel.createRepo("https://example.org/index.min.json", "Custom repo")

            withTimeout(1_000) {
                while (true) {
                    runCatching {
                        coVerify(exactly = 1) {
                            createExtensionRepo.await(
                                "https://example.org/index.min.json",
                                "Custom repo",
                            )
                        }
                        coVerify(exactly = 1) { extensionManager.findAvailableExtensions() }
                    }.onSuccess { return@withTimeout }
                    yield()
                }
            }
        }
    }

    @Test
    fun `rename repo refreshes available plugins`() {
        runBlocking {
            val repo = repo(name = "Old name")
            every { getExtensionRepo.subscribeAll() } returns flowOf(listOf(repo))
            coEvery { replaceExtensionRepo.await(repo.copy(name = "New name")) } returns Unit
            coEvery { extensionManager.findAvailableExtensions() } returns Unit

            val screenModel = MangaExtensionReposScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,
            ).also(activeScreenModels::add)

            screenModel.renameRepo(repo, "New name")

            withTimeout(1_000) {
                while (true) {
                    runCatching {
                        coVerify(exactly = 1) { replaceExtensionRepo.await(repo.copy(name = "New name")) }
                        coVerify(exactly = 1) { extensionManager.findAvailableExtensions() }
                    }.onSuccess { return@withTimeout }
                    yield()
                }
            }
        }
    }

    private fun repo(
        name: String = "Repo",
    ): ExtensionRepo {
        return ExtensionRepo(
            baseUrl = "https://example.org",
            name = name,
            shortName = "repo",
            website = "https://example.org",
            signingKeyFingerprint = "fingerprint",
        )
    }
}
