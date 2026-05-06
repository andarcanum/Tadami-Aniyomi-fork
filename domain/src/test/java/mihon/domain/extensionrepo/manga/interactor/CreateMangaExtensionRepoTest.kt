package mihon.domain.extensionrepo.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService
import org.junit.jupiter.api.Test

class CreateMangaExtensionRepoTest {

    @Test
    fun `valid url inserts repo using provided display name`() = runTest {
        val repository = mockk<MangaExtensionRepoRepository>(relaxed = true)
        val service = mockk<ExtensionRepoService>()
        val interactor = CreateMangaExtensionRepo(repository, service)
        val repo = ExtensionRepo(
            baseUrl = "https://example.org",
            name = "Remote repo",
            shortName = "remote",
            website = "https://example.org",
            signingKeyFingerprint = "fingerprint",
        )
        coEvery { service.fetchRepoDetails("https://example.org") } returns repo

        val result = interactor.await("https://example.org/index.min.json", "Custom repo")

        result shouldBe CreateMangaExtensionRepo.Result.Success
        coVerify {
            repository.insertRepo(
                repo.baseUrl,
                "Custom repo",
                repo.shortName,
                repo.website,
                repo.signingKeyFingerprint,
            )
        }
    }
}
