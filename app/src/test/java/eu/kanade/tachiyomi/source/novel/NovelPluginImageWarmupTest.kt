package eu.kanade.tachiyomi.source.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.Collections

class NovelPluginImageWarmupTest {

    @Test
    fun `collectTargets filters non plugin urls and caps first window`() {
        val urls = buildList {
            add("https://example.com/cover-0.png")
            repeat(15) { index ->
                add("novelimg://plugin-$index?ref=cover-$index")
            }
            add("novelimg://plugin-3?ref=cover-3")
            add(
                "heximg://hexnovels?ref=%7B%22imageUrl%22%3A%22https%3A%2F%2Fcdn.example%2Fcover.png%22%2C%22secretKey%22%3A%22abc%22%7D",
            )
            add("https://example.com/cover-1.png")
        }

        val targets = NovelPluginImageWarmup.collectTargets(urls)

        targets shouldBe listOf(
            "novelimg://plugin-0?ref=cover-0",
            "novelimg://plugin-1?ref=cover-1",
            "novelimg://plugin-2?ref=cover-2",
            "novelimg://plugin-3?ref=cover-3",
            "novelimg://plugin-4?ref=cover-4",
            "novelimg://plugin-5?ref=cover-5",
            "novelimg://plugin-6?ref=cover-6",
            "novelimg://plugin-7?ref=cover-7",
            "novelimg://plugin-8?ref=cover-8",
            "novelimg://plugin-9?ref=cover-9",
            "novelimg://plugin-10?ref=cover-10",
            "novelimg://plugin-11?ref=cover-11",
        )
    }

    @Test
    fun `warmup resolves filtered urls only once`() = runBlocking {
        val resolved = Collections.synchronizedList(mutableListOf<String>())
        val urls = listOf(
            "https://example.com/cover.png",
            "novelimg://plugin-a?ref=cover-a",
            "novelimg://plugin-a?ref=cover-a",
            "novelimg://plugin-b?ref=cover-b",
            "novelimg://plugin-c?ref=cover-c",
        )

        NovelPluginImageWarmup.warmup(urls) { url ->
            resolved.add(url)
        }

        resolved.toSet() shouldBe setOf(
            "novelimg://plugin-a?ref=cover-a",
            "novelimg://plugin-b?ref=cover-b",
            "novelimg://plugin-c?ref=cover-c",
        )
        resolved.size shouldBe 3
    }
}
