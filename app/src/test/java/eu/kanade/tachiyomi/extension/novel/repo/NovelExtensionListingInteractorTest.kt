package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.extension.novel.NovelExtensionUpdateChecker
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import org.junit.jupiter.api.Test

class NovelExtensionListingInteractorTest {

    @Test
    fun `fetch merges installed and available entries with updates`() {
        runTest {
            val getRepos = mockk<GetNovelExtensionRepo>()
            val repoService = mockk<NovelPluginRepoServiceContract>()
            val storage = mockk<NovelPluginStorage>()

            val repo = ExtensionRepo(
            baseUrl = "https://example.org",
            name = "Example",
            shortName = null,
            website = "https://example.org",
            signingKeyFingerprint = "ABC",
            )
            coEvery { getRepos.getAll() } returns listOf(repo)

            val availableUpdate = NovelPluginRepoEntry(
            id = "source-1",
            name = "Source 1",
            site = "Example",
            lang = "en",
            version = 2,
            url = "https://example.org/source-1.js",
            iconUrl = null,
            customJsUrl = null,
            customCssUrl = null,
            hasSettings = false,
            sha256 = "aaa",
            )
            val availableNew = NovelPluginRepoEntry(
            id = "source-2",
            name = "Source 2",
            site = "Example",
            lang = "en",
            version = 1,
            url = "https://example.org/source-2.js",
            iconUrl = null,
            customJsUrl = null,
            customCssUrl = null,
            hasSettings = false,
            sha256 = "bbb",
            )
            coEvery {
            repoService.fetch("https://example.org/index.min.json")
            } returns listOf(availableUpdate, availableNew)

            val installed = NovelPluginRepoEntry(
            id = "source-1",
            name = "Source 1",
            site = "Example",
            lang = "en",
            version = 1,
            url = "https://example.org/source-1.js",
            iconUrl = null,
            customJsUrl = null,
            customCssUrl = null,
            hasSettings = false,
            sha256 = "old",
            )
            coEvery { storage.getAll() } returns listOf(
            NovelPluginPackage(
            entry = installed,
            script = byteArrayOf(),
            customJs = null,
            customCss = null,
            ),
            )

            val interactor = NovelExtensionListingInteractor(
            getExtensionRepo = getRepos,
            repoService = repoService,
            storage = storage,
            updateChecker = NovelExtensionUpdateChecker(),
            )

            val listing = interactor.fetch()

            listing.updates.shouldContainExactly(availableUpdate)
            listing.installed.shouldContainExactly(installed)
            listing.available.shouldContainExactly(availableNew)
            coVerify(exactly = 1) { repoService.fetch("https://example.org/index.min.json") }
        }
    }

        @Test
        fun `fetch returns installed even with no repos`() {
            runTest {
                val getRepos = mockk<GetNovelExtensionRepo>()
                val repoService = mockk<NovelPluginRepoServiceContract>()
                val storage = mockk<NovelPluginStorage>()

                coEvery { getRepos.getAll() } returns emptyList()
                coEvery { storage.getAll() } returns emptyList()

                val interactor = NovelExtensionListingInteractor(
                getExtensionRepo = getRepos,
                repoService = repoService,
                storage = storage,
                updateChecker = NovelExtensionUpdateChecker(),
                )

                val listing = interactor.fetch()

                listing.updates shouldBe emptyList()
                listing.installed shouldBe emptyList()
                listing.available shouldBe emptyList()
                coVerify(exactly = 0) { repoService.fetch(any<String>()) }
            }
        }
    }
