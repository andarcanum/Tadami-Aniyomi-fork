package mihon.domain.extensionrepo.novel.interactor

import eu.kanade.tachiyomi.util.lang.Hash
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import org.junit.jupiter.api.Test

class CreateNovelExtensionRepoTest {

    @Test
    fun `invalid url returns InvalidUrl`() = runTest {
        val repository = mockk<NovelExtensionRepoRepository>(relaxed = true)
        val service = mockk<ExtensionRepoService>(relaxed = true)
        val interactor = CreateNovelExtensionRepo(repository, service)

        val result = interactor.await("https://example.org/repo.json")

        result shouldBe CreateNovelExtensionRepo.Result.InvalidUrl
        coVerify(exactly = 0) { repository.insertRepo(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `valid url inserts repo`() = runTest {
        val repository = mockk<NovelExtensionRepoRepository>(relaxed = true)
        val service = mockk<ExtensionRepoService>()
        val interactor = CreateNovelExtensionRepo(repository, service)
        val repo = ExtensionRepo(
            baseUrl = "https://example.org",
            name = "Repo",
            shortName = "repo",
            website = "https://example.org",
            signingKeyFingerprint = "fingerprint",
        )
        coEvery { service.fetchRepoDetails("https://example.org") } returns repo

        val result = interactor.await("https://example.org/index.min.json")

        result shouldBe CreateNovelExtensionRepo.Result.Success
        coVerify {
            repository.insertRepo(
                repo.baseUrl,
                repo.name,
                repo.shortName,
                repo.website,
                repo.signingKeyFingerprint,
            )
        }
    }

    @Test
    fun `plugins min json url inserts legacy repo with NOFINGERPRINT`() = runTest {
        val repository = mockk<NovelExtensionRepoRepository>(relaxed = true)
        val service = mockk<ExtensionRepoService>(relaxed = true)
        val interactor = CreateNovelExtensionRepo(repository, service)

        val indexUrl = "https://example.org/.dist/plugins.min.json"
        val baseUrl = "https://example.org/.dist"
        val fingerprint = "NOFINGERPRINT-${Hash.sha256(baseUrl)}"

        val result = interactor.await(indexUrl)

        result shouldBe CreateNovelExtensionRepo.Result.Success
        coVerify {
            repository.insertRepo(
                baseUrl,
                baseUrl,
                null,
                baseUrl,
                fingerprint,
            )
        }
        coVerify(exactly = 0) { service.fetchRepoDetails(any()) }
    }
}
