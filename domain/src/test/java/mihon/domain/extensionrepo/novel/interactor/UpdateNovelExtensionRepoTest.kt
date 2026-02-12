package mihon.domain.extensionrepo.novel.interactor

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import org.junit.jupiter.api.Test

class UpdateNovelExtensionRepoTest {

    @Test
    fun `updates metadata for base url repositories`() = runTest {
        val existing = repo(
            baseUrl = "https://repo.example",
            signingKeyFingerprint = "fingerprint-1",
        )
        val fetched = repo(
            baseUrl = "https://repo.example",
            name = "Updated",
            signingKeyFingerprint = "fingerprint-1",
        )
        val repository = FakeNovelExtensionRepoRepository(listOf(existing))
        val service = mockk<ExtensionRepoService>()
        coEvery { service.fetchRepoDetails("https://repo.example") } returns fetched
        val interactor = UpdateNovelExtensionRepo(repository, service)

        interactor.awaitAll()

        repository.upserted shouldBe fetched
    }

    @Test
    fun `normalizes index min url before fetching repository details`() = runTest {
        val existing = repo(
            baseUrl = "https://repo.example/index.min.json",
            signingKeyFingerprint = "fingerprint-1",
        )
        val fetched = repo(
            baseUrl = "https://repo.example",
            signingKeyFingerprint = "fingerprint-1",
        )
        val repository = FakeNovelExtensionRepoRepository(listOf(existing))
        val service = mockk<ExtensionRepoService>()
        coEvery { service.fetchRepoDetails("https://repo.example") } returns fetched
        val interactor = UpdateNovelExtensionRepo(repository, service)

        interactor.awaitAll()

        coVerify(exactly = 1) { service.fetchRepoDetails("https://repo.example") }
        repository.upserted shouldBe fetched
    }

    @Test
    fun `skips plugin json index repositories during metadata refresh`() = runTest {
        val existing = repo(
            baseUrl = "https://repo.example/plugins.min.json",
            signingKeyFingerprint = "NOFINGERPRINT:abc",
        )
        val repository = FakeNovelExtensionRepoRepository(listOf(existing))
        val service = mockk<ExtensionRepoService>()
        val interactor = UpdateNovelExtensionRepo(repository, service)

        interactor.awaitAll()

        coVerify(exactly = 0) { service.fetchRepoDetails(any()) }
        repository.upserted.shouldBeNull()
    }

    @Test
    fun `does not upsert when fingerprint changes for trusted repos`() = runTest {
        val existing = repo(
            baseUrl = "https://repo.example",
            signingKeyFingerprint = "fingerprint-1",
        )
        val fetched = repo(
            baseUrl = "https://repo.example",
            signingKeyFingerprint = "fingerprint-2",
        )
        val repository = FakeNovelExtensionRepoRepository(listOf(existing))
        val service = mockk<ExtensionRepoService>()
        coEvery { service.fetchRepoDetails("https://repo.example") } returns fetched
        val interactor = UpdateNovelExtensionRepo(repository, service)

        interactor.awaitAll()

        repository.upserted.shouldBeNull()
    }

    @Test
    fun `upserts when fingerprint changes for nofingerprint repos`() = runTest {
        val existing = repo(
            baseUrl = "https://repo.example",
            signingKeyFingerprint = "NOFINGERPRINT:abc",
        )
        val fetched = repo(
            baseUrl = "https://repo.example",
            signingKeyFingerprint = "fingerprint-2",
        )
        val repository = FakeNovelExtensionRepoRepository(listOf(existing))
        val service = mockk<ExtensionRepoService>()
        coEvery { service.fetchRepoDetails("https://repo.example") } returns fetched
        val interactor = UpdateNovelExtensionRepo(repository, service)

        interactor.awaitAll()

        repository.upserted shouldBe fetched
    }

    @Test
    fun `continues when repo details fetch throws`() = runTest {
        val existing = repo(
            baseUrl = "https://repo.example",
            signingKeyFingerprint = "NOFINGERPRINT:abc",
        )
        val repository = FakeNovelExtensionRepoRepository(listOf(existing))
        val service = mockk<ExtensionRepoService>()
        coEvery { service.fetchRepoDetails("https://repo.example") } throws RuntimeException("boom")
        val interactor = UpdateNovelExtensionRepo(repository, service)

        interactor.awaitAll()

        repository.upserted.shouldBeNull()
    }

    private fun repo(
        baseUrl: String,
        name: String = "Repo",
        signingKeyFingerprint: String,
    ): ExtensionRepo {
        return ExtensionRepo(
            baseUrl = baseUrl,
            name = name,
            shortName = null,
            website = "https://repo.example/",
            signingKeyFingerprint = signingKeyFingerprint,
        )
    }

    private class FakeNovelExtensionRepoRepository(
        private val repos: List<ExtensionRepo>,
    ) : NovelExtensionRepoRepository {
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
