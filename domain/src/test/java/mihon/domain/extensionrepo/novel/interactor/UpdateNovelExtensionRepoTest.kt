package mihon.domain.extensionrepo.novel.interactor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import org.junit.jupiter.api.Test

class UpdateNovelExtensionRepoTest {

    @Test
    fun `updates repo when fingerprint matches`() = runTest {
        val repository = mockk<NovelExtensionRepoRepository>(relaxed = true)
        val service = mockk<ExtensionRepoService>()
        val interactor = UpdateNovelExtensionRepo(repository, service)
        val repo = ExtensionRepo(
            baseUrl = "https://example.org",
            name = "Repo",
            shortName = "repo",
            website = "https://example.org",
            signingKeyFingerprint = "fingerprint",
        )
        coEvery { service.fetchRepoDetails(repo.baseUrl) } returns repo

        interactor.await(repo)

        coVerify { repository.upsertRepo(repo) }
    }

    @Test
    fun `does not update when fingerprint differs`() = runTest {
        val repository = mockk<NovelExtensionRepoRepository>(relaxed = true)
        val service = mockk<ExtensionRepoService>()
        val interactor = UpdateNovelExtensionRepo(repository, service)
        val repo = ExtensionRepo(
            baseUrl = "https://example.org",
            name = "Repo",
            shortName = "repo",
            website = "https://example.org",
            signingKeyFingerprint = "fingerprint",
        )
        val updated = repo.copy(signingKeyFingerprint = "other")
        coEvery { service.fetchRepoDetails(repo.baseUrl) } returns updated

        interactor.await(repo)

        coVerify(exactly = 0) { repository.upsertRepo(any()) }
    }
}
