package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class NovelJsPayloadParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `parseNovel tolerates mixed chapter shapes`() {
        val payload = """
            {
              "title": "Demo novel",
              "url": "/novel/demo",
              "totalPages": "3",
              "chapters": [
                { "name": "Chapter 1", "path": "/novel/demo/ch1", "chapterNumber": 1 },
                { "title": "Chapter 2", "url": "/novel/demo/ch2", "chapterNumber": "2.5" },
                { "name": "Broken chapter without path" }
              ]
            }
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parseNovel(json, payload).shouldNotBeNull()
        parsed.name shouldBe "Demo novel"
        parsed.path shouldBe "/novel/demo"
        parsed.totalPages shouldBe 3
        parsed.chapters.shouldNotBeNull().shouldHaveSize(3)
        parsed.chapters!![0].chapterNumber.shouldNotBeNull().shouldBeExactly(1.0)
        parsed.chapters!![1].name shouldBe "Chapter 2"
        parsed.chapters!![1].path shouldBe "/novel/demo/ch2"
        parsed.chapters!![1].chapterNumber.shouldNotBeNull().shouldBeExactly(2.5)
    }

    @Test
    fun `parsePage supports both array and wrapped object payloads`() {
        val arrayPayload = """
            [
              { "name": "One", "path": "/one" },
              { "title": "Two", "url": "/two" }
            ]
        """.trimIndent()
        val wrappedPayload = """
            {
              "chapters": [
                { "name": "Three", "path": "/three" }
              ]
            }
        """.trimIndent()

        val parsedArray = NovelJsPayloadParser.parsePage(json, arrayPayload).shouldNotBeNull()
        val parsedWrapped = NovelJsPayloadParser.parsePage(json, wrappedPayload).shouldNotBeNull()

        parsedArray.chapters.shouldHaveSize(2)
        parsedArray.chapters[1].name shouldBe "Two"
        parsedArray.chapters[1].path shouldBe "/two"
        parsedWrapped.chapters.shouldHaveSize(1)
        parsedWrapped.chapters[0].name shouldBe "Three"
    }

    @Test
    fun `parseChaptersArray supports chapter endpoint payload`() {
        val payload = """
            [
              { "id": 194938, "title": "–ö. –ß–∞—Å—Ç—å 1" },
              { "id": 194939, "title": "–ö. –ß–∞—Å—Ç—å 2" }
            ]
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parseChaptersArray(json, payload)

        parsed.shouldHaveSize(2)
        parsed[0].name shouldBe "–ö. –ß–∞—Å—Ç—å 1"
        parsed[0].path shouldBe null
        parsed[0].chapterNumber shouldBe null
    }
    @Test
    fun `parseRulateFamilyChapterEndpoint builds chapter paths`() {
        val payload = """
            [
              { "id": 194938, "title": " . ◊‡ÒÚ¸ 1" },
              { "id": 194939, "title": " . ◊‡ÒÚ¸ 2" }
            ]
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parseRulateFamilyChapterEndpoint(
            json = json,
            payload = payload,
            bookId = "5558",
        )

        parsed.shouldHaveSize(2)
        parsed[0].name shouldBe " . ◊‡ÒÚ¸ 1"
        parsed[0].path shouldBe "/book/5558/194938"
        parsed[0].chapterNumber.shouldNotBeNull().shouldBeExactly(1.0)
        parsed[1].path shouldBe "/book/5558/194939"
        parsed[1].chapterNumber.shouldNotBeNull().shouldBeExactly(2.0)
    }
}
