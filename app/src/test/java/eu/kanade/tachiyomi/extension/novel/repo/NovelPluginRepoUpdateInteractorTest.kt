package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.extension.novel.NovelExtensionUpdateChecker
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

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
            val storage = InMemoryNovelPluginStorage().apply {
            save(
            NovelPluginPackage(
            entry = installedEntry,
            script = "main".toByteArray(),
            customJs = null,
            customCss = null,
            ),
            )
            }
            val repoService = FakeRepoService(
            mapOf("https://repo.example.org/index.json" to listOf(availableEntry)),
            )

            val interactor = NovelPluginRepoUpdateInteractor(
            repoService = repoService,
            storage = storage,
            updateChecker = NovelExtensionUpdateChecker(),
            )

            val updates = interactor.findUpdates(listOf("https://repo.example.org/index.json"))

            updates shouldBe listOf(availableEntry)
        }
    }

        @Test
        fun `findUpdates ignores entries not installed`() {
            runTest {
                val storage = InMemoryNovelPluginStorage()
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
                storage = storage,
                updateChecker = NovelExtensionUpdateChecker(),
                )

                val updates = interactor.findUpdates(listOf("https://repo.example.org/index.json"))

                updates shouldBe emptyList()
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
