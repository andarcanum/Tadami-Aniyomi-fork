package mihon.domain.extensionrepo.novel.interactor

import eu.kanade.tachiyomi.util.lang.Hash
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.model.repoDisplayNameFallback
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import org.junit.jupiter.api.Test

class CreateNovelExtensionRepoTest {

    @Test
    fun `invalid url returns InvalidUrl`() = runTest {
        val repository = mockk<NovelExtensionStoreRepository>(relaxed = true)
        val interactor = CreateNovelExtensionRepo(repository)

        val result = interactor.await("not-a-url")

        result shouldBe CreateNovelExtensionRepo.Result.InvalidUrl
        coVerify(exactly = 0) { repository.upsertStore(any()) }
    }

    @Test
    fun `valid store url inserts via repository`() = runTest {
        val repository = mockk<NovelExtensionStoreRepository>(relaxed = true)
        val insertedStore = ExtensionStore(
            indexUrl = "https://example.org/index.min.json",
            name = "Store",
            badgeLabel = "store",
            signingKey = "fingerprint",
            contact = ExtensionStore.Contact(website = "https://example.org", discord = null),
            isLegacy = true,
            extensionListUrl = null,
        )
        coEvery { repository.insert("https://example.org/index.min.json") } returns Result.success(Unit)
        coEvery { repository.getAll() } returns listOf(insertedStore)
        val interactor = CreateNovelExtensionRepo(repository)

        val result = interactor.await("https://example.org/index.min.json", "Custom store")

        result shouldBe CreateNovelExtensionRepo.Result.Success
        coVerify {
            repository.setCustomName(insertedStore.indexUrl, "Custom store")
        }
    }

    @Test
    fun `plugins min json url inserts legacy store with NOFINGERPRINT`() = runTest {
        val repository = mockk<NovelExtensionStoreRepository>(relaxed = true)
        coEvery { repository.getAll() } returns emptyList()
        val interactor = CreateNovelExtensionRepo(repository)

        val indexUrl = "https://example.org/.dist/plugins.min.json"
        val baseUrl = "https://example.org/.dist"
        val fingerprint = "NOFINGERPRINT-${Hash.sha256(baseUrl)}"
        // Since 73210d327 the display name falls back to a readable label
        // (repoDisplayNameFallback), not the bare url.
        val label = baseUrl.repoDisplayNameFallback()

        val result = interactor.await(indexUrl)

        result shouldBe CreateNovelExtensionRepo.Result.Success
        coVerify {
            repository.upsertStore(
                ExtensionStore(
                    indexUrl = baseUrl,
                    name = label,
                    badgeLabel = label,
                    signingKey = fingerprint,
                    contact = ExtensionStore.Contact(website = baseUrl, discord = null),
                    isLegacy = true,
                    extensionListUrl = null,
                ),
            )
        }
    }
}
