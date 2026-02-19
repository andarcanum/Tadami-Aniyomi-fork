package mihon.domain.extensionrepo.anime.interactor

import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService
import org.junit.jupiter.api.Test

class UpdateAnimeExtensionRepoTest {

    @Test
    fun `continues when repo details fetch throws`() = runTest {
        val existing = repo(
            baseUrl = "https://repo.example",
            signingKeyFingerprint = "NOFINGERPRINT:abc",
        )
        val repository = FakeAnimeExtensionRepoRepository(listOf(existing))
        val service = mockk<ExtensionRepoService>()
        coEvery { service.fetchRepoDetails("https://repo.example") } throws RuntimeException("boom")
        val interactor = UpdateAnimeExtensionRepo(repository, service)

        interactor.awaitAll()

        repository.upserted.shouldBeNull()
    }

    private fun repo(
        baseUrl: String,
        signingKeyFingerprint: String,
    ): ExtensionRepo {
        return ExtensionRepo(
            baseUrl = baseUrl,
            name = "Repo",
            shortName = null,
            website = "https://repo.example/",
            signingKeyFingerprint = signingKeyFingerprint,
        )
    }

    private class FakeAnimeExtensionRepoRepository(
        private val repos: List<ExtensionRepo>,
    ) : AnimeExtensionRepoRepository {
        var upserted: ExtensionRepo? = null

        override fun subscribeAll(): Flow<List<ExtensionRepo>> = flowOf(repos)

        override suspend fun getAll(): List<ExtensionRepo> = repos

        override suspend fun getRepo(baseUrl: String): ExtensionRepo? {
            return repos.firstOrNull { it.baseUrl == baseUrl }
        }

        override suspend fun getRepoBySigningKeyFingerprint(fingerprint: String): ExtensionRepo? {
            return repos.firstOrNull { it.signingKeyFingerprint == fingerprint }
        }

        override fun getCount(): Flow<Int> = flowOf(repos.size)

        override suspend fun insertRepo(
            baseUrl: String,
            name: String,
            shortName: String?,
            website: String,
            signingKeyFingerprint: String,
        ) {
            upserted = ExtensionRepo(
                baseUrl = baseUrl,
                name = name,
                shortName = shortName,
                website = website,
                signingKeyFingerprint = signingKeyFingerprint,
            )
        }

        override suspend fun upsertRepo(
            baseUrl: String,
            name: String,
            shortName: String?,
            website: String,
            signingKeyFingerprint: String,
        ) {
            insertRepo(baseUrl, name, shortName, website, signingKeyFingerprint)
        }

        override suspend fun replaceRepo(newRepo: ExtensionRepo) {
            upserted = newRepo
        }

        override suspend fun deleteRepo(baseUrl: String) = Unit
    }
}
