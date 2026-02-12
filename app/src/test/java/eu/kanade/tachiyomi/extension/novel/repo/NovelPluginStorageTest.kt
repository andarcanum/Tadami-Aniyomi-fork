package eu.kanade.tachiyomi.extension.novel.repo

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NovelPluginStorageTest {

    @Test
    fun `save stores and returns package`() {
        runTest {
            val storage = InMemoryNovelPluginStorage()
            val entry = NovelPluginRepoEntry(
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
            val pkg = NovelPluginPackage(
                entry = entry,
                script = "main".toByteArray(),
                customJs = null,
                customCss = null,
            )

            storage.save(pkg)

            storage.get(entry.id) shouldBe pkg
        }
    }

    @Test
    fun `getAll returns stored packages`() {
        runTest {
            val storage = InMemoryNovelPluginStorage()
            val entry = NovelPluginRepoEntry(
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
            val pkg = NovelPluginPackage(
                entry = entry,
                script = "main".toByteArray(),
                customJs = null,
                customCss = null,
            )

            storage.save(pkg)

            storage.getAll() shouldBe listOf(pkg)
        }
    }
}
