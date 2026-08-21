package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelQuoteShareFormatterTest {

    @Test
    fun `quote with note includes source and note lines`() {
        val text = NovelQuoteShareFormatter.formatQuote(
            quote = "stored snippet",
            novelTitle = "Novel",
            chapterName = "Chapter 1",
            note = "my note",
        )

        text shouldBe "«stored snippet»\n— Novel, Chapter 1\nmy note"
    }

    @Test
    fun `quote without note omits the note line`() {
        val text = NovelQuoteShareFormatter.formatQuote(
            quote = "stored snippet",
            novelTitle = "Novel",
            chapterName = "Chapter 1",
            note = null,
        )

        text shouldBe "«stored snippet»\n— Novel, Chapter 1"
    }

    @Test
    fun `document joins quotes with ornamental dividers`() {
        val text = NovelQuoteShareFormatter.formatDocument(
            novelTitle = "Novel",
            quotes = listOf(
                Triple("first", "Chapter 1", null),
                Triple("second", "Chapter 2", "note"),
            ),
        )

        text shouldBe "Novel — Quotes" +
            "\n\n❦\n\n«first»\n— Chapter 1" +
            "\n\n❦\n\n«second»\n— Chapter 2\nnote"
    }

    @Test
    fun `empty document is just the header`() {
        NovelQuoteShareFormatter.formatDocument("Novel", emptyList()) shouldBe "Novel — Quotes"
    }
}
