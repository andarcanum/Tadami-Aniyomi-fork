package mihon.domain.extensionrepo.anime.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService
import org.junit.jupiter.api.Test

class CreateAnimeExtensionRepoTest {

    @Test
    fun `valid url inserts repo using provided display name`() = runTest {
        val repository = mockk<AnimeExtensionRepoRepository>(relaxed = true)
        val service = mockk<ExtensionRepoService>()
        val interactor = CreateAnimeExtensionRepo(repository, service)
        val repo = ExtensionRepo(
            baseUrl = "https://example.org",
            name = "Remote repo",
            shortName = "remote",
            website = "https://example.org",
            signingKeyFingerprint = "fingerprint",
        )
        coEvery { service.fetchRepoDetails("https://example.org") } returns repo

        val result = interactor.await("https://example.org/index.min.json", "Custom repo")

        result shouldBe CreateAnimeExtensionRepo.Result.Success
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
