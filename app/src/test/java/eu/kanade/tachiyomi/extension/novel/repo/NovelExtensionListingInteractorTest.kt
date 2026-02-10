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
            coEvery { repoService.fetch("https://example.org/index.min.json") } returns listOf(availableUpdate)
            coEvery { repoService.fetch("https://example.org/plugins.min.json") } returns listOf(availableNew)

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
            coVerify(exactly = 1) { repoService.fetch("https://example.org/plugins.min.json") }
        }
    }

    @Test
    fun `fetch dedupes duplicate ids across index formats to highest version`() {
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

            val duplicateFromIndex = NovelPluginRepoEntry(
                id = "source-dup",
                name = "Source Dup",
                site = "Example",
                lang = "en",
                version = 1,
                url = "https://example.org/source-dup-v1.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "dup-v1",
            )
            val duplicateFromPlugins = NovelPluginRepoEntry(
                id = "source-dup",
                name = "Source Dup",
                site = "Example",
                lang = "en",
                version = 3,
                url = "https://example.org/source-dup-v3.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "dup-v3",
            )
            val unique = NovelPluginRepoEntry(
                id = "source-unique",
                name = "Source Unique",
                site = "Example",
                lang = "en",
                version = 1,
                url = "https://example.org/source-unique.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "unique",
            )

            coEvery { repoService.fetch("https://example.org/index.min.json") } returns listOf(duplicateFromIndex, unique)
            coEvery { repoService.fetch("https://example.org/plugins.min.json") } returns listOf(duplicateFromPlugins)
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
            listing.available.shouldContainExactly(duplicateFromPlugins, unique)
        }
    }

    @Test
    fun `fetch keeps same plugin id from different repos`() {
        runTest {
            val getRepos = mockk<GetNovelExtensionRepo>()
            val repoService = mockk<NovelPluginRepoServiceContract>()
            val storage = mockk<NovelPluginStorage>()

            val firstRepo = ExtensionRepo(
                baseUrl = "https://repo-one.example",
                name = "Repo One",
                shortName = null,
                website = "https://repo-one.example",
                signingKeyFingerprint = "ONE",
            )
            val secondRepo = ExtensionRepo(
                baseUrl = "https://repo-two.example",
                name = "Repo Two",
                shortName = null,
                website = "https://repo-two.example",
                signingKeyFingerprint = "TWO",
            )
            coEvery { getRepos.getAll() } returns listOf(firstRepo, secondRepo)

            val fromFirstRepo = NovelPluginRepoEntry(
                id = "same-id",
                name = "First Mirror",
                site = "Repo One",
                lang = "en",
                version = 2,
                url = "https://repo-one.example/same-id.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "one",
            )
            val fromSecondRepo = NovelPluginRepoEntry(
                id = "same-id",
                name = "Second Mirror",
                site = "Repo Two",
                lang = "en",
                version = 5,
                url = "https://repo-two.example/same-id.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "two",
            )

            coEvery { repoService.fetch("https://repo-one.example/index.min.json") } returns listOf(fromFirstRepo)
            coEvery { repoService.fetch("https://repo-one.example/plugins.min.json") } returns emptyList()
            coEvery { repoService.fetch("https://repo-two.example/index.min.json") } returns listOf(fromSecondRepo)
            coEvery { repoService.fetch("https://repo-two.example/plugins.min.json") } returns emptyList()
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
            listing.available.shouldContainExactly(fromFirstRepo, fromSecondRepo)
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

    @Test
    fun `fetch uses repo url as is when base url already ends with index file`() {
        runTest {
            val getRepos = mockk<GetNovelExtensionRepo>()
            val repoService = mockk<NovelPluginRepoServiceContract>()
            val storage = mockk<NovelPluginStorage>()

            val repo = ExtensionRepo(
                baseUrl = "https://example.org/index.min.json",
                name = "Example",
                shortName = null,
                website = "https://example.org",
                signingKeyFingerprint = "ABC",
            )
            coEvery { getRepos.getAll() } returns listOf(repo)
            coEvery { repoService.fetch("https://example.org/index.min.json") } returns emptyList()
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
            coVerify(exactly = 1) { repoService.fetch("https://example.org/index.min.json") }
        }
    }
}
