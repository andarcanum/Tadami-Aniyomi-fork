package eu.kanade.presentation.more.settings.screen.browse

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
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
            )

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
    }
