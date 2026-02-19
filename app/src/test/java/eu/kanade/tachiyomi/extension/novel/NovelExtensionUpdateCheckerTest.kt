package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoEntry
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelExtensionUpdateCheckerTest {

    @Test
    fun `update checker returns entries with newer version`() {
        val installed = listOf(
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
                sha256 = "a",
            ),
        )
        val available = listOf(
            installed.first().copy(version = 2),
            NovelPluginRepoEntry(
                id = "other",
                name = "Other",
                site = "https://example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/other.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "b",
            ),
        )

        val updates = NovelExtensionUpdateChecker().findUpdates(installed, available)

        updates.size shouldBe 1
        updates.first().id shouldBe "novel"
        updates.first().version shouldBe 2
    }
}
