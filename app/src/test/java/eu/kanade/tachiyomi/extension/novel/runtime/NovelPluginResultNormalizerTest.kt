package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelPluginResultNormalizerTest {

    @Test
    fun `fills missing chapter names and removes duplicate paths`() {
        val normalizer = NovelPluginResultNormalizer()
        val policy = NovelChapterFallbackPolicy(
            fillMissingChapterNames = true,
            dropDuplicateChapterPaths = true,
            chapterNamePrefix = "Chapter",
        )

        val chapters = listOf(
            ParsedPluginChapter(name = null, path = "/ch-1"),
            ParsedPluginChapter(name = "", path = "/ch-1"),
            ParsedPluginChapter(name = "Second", path = "/ch-2"),
        )

        val normalized = normalizer.normalize(
            pluginId = "plugin.demo",
            chapters = chapters,
            policy = policy,
        )

        normalized.size shouldBe 2
        normalized.map { it.path } shouldContainExactly listOf("/ch-1", "/ch-2")
        normalized.first().name shouldBe "Chapter 1"
    }

    @Test
    fun `keeps missing name when fallback disabled`() {
        val normalizer = NovelPluginResultNormalizer()
        val policy = NovelChapterFallbackPolicy(fillMissingChapterNames = false)
        val chapters = listOf(ParsedPluginChapter(name = null, path = "/ch-1"))

        val normalized = normalizer.normalize(
            pluginId = "plugin.demo",
            chapters = chapters,
            policy = policy,
        )

        normalized.single().name shouldBe null
    }

    @Test
    fun `reorders scribblehub chapters from newest first to oldest first`() {
        val normalizer = NovelPluginResultNormalizer()
        val policy = NovelChapterFallbackPolicy()
        val chapters = listOf(
            ParsedPluginChapter(name = "Chapter 3", path = "/read/100/300/"),
            ParsedPluginChapter(name = "Chapter 2", path = "/read/100/200/"),
            ParsedPluginChapter(name = "Chapter 1", path = "/read/100/100/"),
        )

        val normalized = normalizer.normalize(
            pluginId = "scribblehub",
            chapters = chapters,
            policy = policy,
        )

        normalized.mapNotNull { it.path } shouldContainExactly listOf(
            "/read/100/100/",
            "/read/100/200/",
            "/read/100/300/",
        )
    }

    @Test
    fun `does not reorder non scribblehub chapters`() {
        val normalizer = NovelPluginResultNormalizer()
        val policy = NovelChapterFallbackPolicy()
        val chapters = listOf(
            ParsedPluginChapter(name = "Three", path = "/read/100/300/"),
            ParsedPluginChapter(name = "Two", path = "/read/100/200/"),
            ParsedPluginChapter(name = "One", path = "/read/100/100/"),
        )

        val normalized = normalizer.normalize(
            pluginId = "other-plugin",
            chapters = chapters,
            policy = policy,
        )

        normalized.mapNotNull { it.path } shouldContainExactly listOf(
            "/read/100/300/",
            "/read/100/200/",
            "/read/100/100/",
        )
    }
}
