package eu.kanade.tachiyomi.source.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelPluginImageUrlParserTest {

    @Test
    fun `parses query ref from novelimg url`() {
        val parsed = NovelPluginImageUrlParser.parse(
            "novelimg://hexnovels?ref=chapter%2Fimg-1",
        )

        parsed?.pluginId shouldBe "hexnovels"
        parsed?.imageRef shouldBe "chapter/img-1"
    }

    @Test
    fun `parses path ref when query is absent`() {
        val parsed = NovelPluginImageUrlParser.parse(
            "heximg://hexnovels/chapter%2Fimg-2",
        )

        parsed?.pluginId shouldBe "hexnovels"
        parsed?.imageRef shouldBe "chapter/img-2"
    }

    @Test
    fun `parses encoded json ref payload`() {
        val rawRef =
            "{\"imageUrl\":\"https://static.inuko.me/rich/12345678-1234-s234-1234-123456789012.webp?token=a%2Bb\"," +
                "\"secretKey\":\"k\",\"cacheKey\":\"chapter-0\"}"
        val url = "novelimg://hexnovels?ref=${java.net.URLEncoder.encode(rawRef, Charsets.UTF_8.name())}"

        val parsed = NovelPluginImageUrlParser.parse(url)

        parsed?.pluginId shouldBe "hexnovels"
        parsed?.imageRef shouldBe rawRef
    }

    @Test
    fun `rejects unsupported scheme`() {
        NovelPluginImageUrlParser.parse("https://example.org/image.png") shouldBe null
    }
}
