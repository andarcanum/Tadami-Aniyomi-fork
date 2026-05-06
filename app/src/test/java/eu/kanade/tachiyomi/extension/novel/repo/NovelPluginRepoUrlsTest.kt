package eu.kanade.tachiyomi.extension.novel.repo

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelPluginRepoUrlsTest {

    @Test
    fun `base url resolves to all three candidates`() {
        resolveNovelPluginRepoIndexUrls(" https://example.org/repo/ ")
            .shouldBe(
                listOf(
                    "https://example.org/repo/index.min.json",
                    "https://example.org/repo/plugins.min.json",
                    "https://example.org/repo/plugins.json",
                ),
            )
    }

    @Test
    fun `json url kept as-is`() {
        resolveNovelPluginRepoIndexUrls(" https://example.org/repo/custom.min.json ")
            .shouldBe(listOf("https://example.org/repo/custom.min.json"))
    }
}
