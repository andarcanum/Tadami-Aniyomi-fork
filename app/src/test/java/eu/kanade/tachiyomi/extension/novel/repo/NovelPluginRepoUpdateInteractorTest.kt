package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.extension.novel.NovelExtensionUpdateChecker
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository

class NovelPluginRepoUpdateInteractorTest {

    @Test
    fun `findUpdates returns entries with newer version`() {
        runTest {
            val installedEntry = NovelPluginRepoEntry(
                id = "novel",
                name = "Novel Source",
                site = "https://example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/novel.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "deadbeef",
            )
            val availableEntry = installedEntry.copy(version = 2)

            val installedPlugin = NovelPlugin.Installed(
                id = installedEntry.id,
                name = installedEntry.name,
                site = installedEntry.site,
                lang = installedEntry.lang,
                versionCode = installedEntry.version,
                versionName = installedEntry.version.toString(),
                url = installedEntry.url,
                iconUrl = installedEntry.iconUrl,
                customJs = installedEntry.customJsUrl,
                customCss = installedEntry.customCssUrl,
                hasSettings = installedEntry.hasSettings,
                sha256 = installedEntry.sha256,
                repoUrl = "https://repo.example.org",
            )
            val repository = mockk<NovelPluginRepository>()
            coEvery { repository.getAll() } returns listOf(installedPlugin)

            val repoService = FakeRepoService(
                mapOf("https://repo.example.org/index.json" to listOf(availableEntry)),
            )

            val interactor = NovelPluginRepoUpdateInteractor(
                repoService = repoService,
                repository = repository,
                updateChecker = NovelExtensionUpdateChecker(),
            )

            val updates = interactor.findUpdates(listOf("https://repo.example.org/index.json"))

            updates shouldBe listOf(availableEntry)
        }
    }

    @Test
    fun `findUpdates ignores entries not installed`() {
        runTest {
            val repository = mockk<NovelPluginRepository>()
            coEvery { repository.getAll() } returns emptyList()

            val repoService = FakeRepoService(
                mapOf(
                    "https://repo.example.org/index.json" to listOf(
                        NovelPluginRepoEntry(
                            id = "novel",
                            name = "Novel Source",
                            site = "https://example.org",
                            lang = "en",
                            version = 1,
                            url = "https://example.org/novel.js",
                            iconUrl = null,
                            customJsUrl = null,
                            customCssUrl = null,
                            hasSettings = false,
                            sha256 = "deadbeef",
                        ),
                    ),
                ),
            )

            val interactor = NovelPluginRepoUpdateInteractor(
                repoService = repoService,
                repository = repository,
                updateChecker = NovelExtensionUpdateChecker(),
            )

            val updates = interactor.findUpdates(listOf("https://repo.example.org/index.json"))

            updates shouldBe emptyList()
        }
    }

    @Test
    fun `findUpdates keeps newest duplicate plugin id across repo urls`() {
        runTest {
            val installedEntry = NovelPluginRepoEntry(
                id = "novel",
                name = "Novel Source",
                site = "https://example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/novel.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "deadbeef",
            )
            val olderUpdate = installedEntry.copy(version = 2, sha256 = "beadfeed")
            val newerUpdate = installedEntry.copy(version = 3, sha256 = "cafebabe")

            val installedPlugin = NovelPlugin.Installed(
                id = installedEntry.id,
                name = installedEntry.name,
                site = installedEntry.site,
                lang = installedEntry.lang,
                versionCode = installedEntry.version,
                versionName = installedEntry.version.toString(),
                url = installedEntry.url,
                iconUrl = installedEntry.iconUrl,
                customJs = installedEntry.customJsUrl,
                customCss = installedEntry.customCssUrl,
                hasSettings = installedEntry.hasSettings,
                sha256 = installedEntry.sha256,
                repoUrl = "https://repo.example.org",
            )
            val repository = mockk<NovelPluginRepository>()
            coEvery { repository.getAll() } returns listOf(installedPlugin)

            val repoService = FakeRepoService(
                mapOf(
                    "https://repo.example.org/index.min.json" to listOf(olderUpdate),
                    "https://repo.example.org/plugins.min.json" to listOf(newerUpdate),
                ),
            )

            val interactor = NovelPluginRepoUpdateInteractor(
                repoService = repoService,
                repository = repository,
                updateChecker = NovelExtensionUpdateChecker(),
            )

            val updates = interactor.findUpdates(
                listOf(
                    "https://repo.example.org/index.min.json",
                    "https://repo.example.org/plugins.min.json",
                ),
            )

            updates shouldBe listOf(newerUpdate)
        }
    }

    @Test
    fun `findUpdates dedupes same plugin id across different repo roots to highest version`() {
        runTest {
            val installedEntry = NovelPluginRepoEntry(
                id = "novel",
                name = "Novel Source",
                site = "https://example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/novel.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "deadbeef",
            )
            val repoOneUpdate = installedEntry.copy(
                version = 2,
                site = "https://repo-one.example",
                url = "https://repo-one.example/novel.js",
                sha256 = "repo-one",
            )
            val repoTwoUpdate = installedEntry.copy(
                version = 4,
                site = "https://repo-two.example",
                url = "https://repo-two.example/novel.js",
                sha256 = "repo-two",
            )

            val installedPlugin = NovelPlugin.Installed(
                id = installedEntry.id,
                name = installedEntry.name,
                site = installedEntry.site,
                lang = installedEntry.lang,
                versionCode = installedEntry.version,
                versionName = installedEntry.version.toString(),
                url = installedEntry.url,
                iconUrl = installedEntry.iconUrl,
                customJs = installedEntry.customJsUrl,
                customCss = installedEntry.customCssUrl,
                hasSettings = installedEntry.hasSettings,
                sha256 = installedEntry.sha256,
                repoUrl = "https://repo.example.org",
            )
            val repository = mockk<NovelPluginRepository>()
            coEvery { repository.getAll() } returns listOf(installedPlugin)

            val repoService = FakeRepoService(
                mapOf(
                    "https://repo-one.example/index.min.json" to listOf(repoOneUpdate),
                    "https://repo-two.example/index.min.json" to listOf(repoTwoUpdate),
                ),
            )

            val interactor = NovelPluginRepoUpdateInteractor(
                repoService = repoService,
                repository = repository,
                updateChecker = NovelExtensionUpdateChecker(),
            )

            val updates = interactor.findUpdates(
                listOf(
                    "https://repo-one.example/index.min.json",
                    "https://repo-two.example/index.min.json",
                ),
            )

            updates shouldBe listOf(repoTwoUpdate)
        }
    }
}

private class FakeRepoService(
    private val entriesByRepo: Map<String, List<NovelPluginRepoEntry>>,
) : NovelPluginRepoServiceContract {
    override suspend fun fetch(repoUrl: String): List<NovelPluginRepoEntry> {
        return entriesByRepo[repoUrl].orEmpty()
    }
}
