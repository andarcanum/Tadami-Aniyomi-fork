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
              { "id": 194938, "title": "Chapter Part 1" },
              { "id": 194939, "title": "Chapter Part 2" }
            ]
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parseChaptersArray(json, payload)

        parsed.shouldHaveSize(2)
        parsed[0].name shouldBe "Chapter Part 1"
        parsed[0].path shouldBe null
        parsed[0].chapterNumber shouldBe null
    }

    @Test
    fun `parseRulateFamilyChapterEndpoint builds chapter paths`() {
        val payload = """
            [
              { "id": 194938, "title": "Chapter Part 1" },
              { "id": 194939, "title": "Chapter Part 2" }
            ]
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parseRulateFamilyChapterEndpoint(
            json = json,
            payload = payload,
            bookId = "5558",
        )

        parsed.shouldHaveSize(2)
        parsed[0].name shouldBe "Chapter Part 1"
        parsed[0].path shouldBe "/book/5558/194938"
        parsed[0].chapterNumber.shouldNotBeNull().shouldBeExactly(1.0)
        parsed[1].path shouldBe "/book/5558/194939"
        parsed[1].chapterNumber.shouldNotBeNull().shouldBeExactly(2.0)
    }

    // Golden-plugin regression tests for parsePage behavior

    @Test
    fun `parsePage handles alphapolis-style paginated chapter list`() {
        // Alphapolis plugin uses parsePage for paginated chapter lists
        val payload = """
            {
              "chapters": [
                { "name": "第1話", "path": "/novel/123/1", "chapterNumber": 1 },
                { "name": "第2話", "path": "/novel/123/2", "chapterNumber": 2 }
              ],
              "page": 1,
              "totalPages": 5
            }
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parsePage(json, payload).shouldNotBeNull()

        parsed.chapters.shouldHaveSize(2)
        parsed.page shouldBe 1
        parsed.totalPages shouldBe 5
        parsed.chapters[0].name shouldBe "第1話"
        parsed.chapters[0].path shouldBe "/novel/123/1"
    }

    @Test
    fun `parsePage handles novelism-style page with mixed chapter fields`() {
        // Novelism plugin returns chapters with varying field names
        val payload = """
            {
              "chapters": [
                { "title": "Prologue", "url": "/ch/1", "number": 0 },
                { "name": "Chapter 1", "path": "/ch/2", "chapterNumber": 1 },
                { "chapterName": "Chapter 2", "chapterPath": "/ch/3" }
              ],
              "currentPage": 2,
              "lastPage": 10
            }
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parsePage(json, payload).shouldNotBeNull()

        parsed.chapters.shouldHaveSize(3)
        parsed.chapters[0].name shouldBe "Prologue"
        parsed.chapters[0].path shouldBe "/ch/1"
        parsed.chapters[1].name shouldBe "Chapter 1"
        parsed.chapters[1].path shouldBe "/ch/2"
        parsed.chapters[2].name shouldBe "Chapter 2"
        parsed.chapters[2].path shouldBe "/ch/3"
    }

    @Test
    fun `parsePage handles jaomix-style reverse pagination`() {
        // Jaomix plugin uses reverse pagination (oldest page = highest page number)
        val payload = """
            {
              "chapters": [
                { "name": "Old Chapter", "path": "/novel/x/99" }
              ],
              "page": 99,
              "total_pages": 99
            }
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parsePage(json, payload).shouldNotBeNull()

        parsed.chapters.shouldHaveSize(1)
        parsed.page shouldBe 99
        parsed.totalPages shouldBe 99
    }

    @Test
    fun `parsePage returns null for invalid payload`() {
        val invalidPayloads = listOf(
            "",
            "null",
            "invalid json",
            "{}",
        )

        invalidPayloads.forEach { payload ->
            val result = NovelJsPayloadParser.parsePage(json, payload)
            if (payload == "{}") {
                // Empty object returns empty chapters list, not null
                result.shouldNotBeNull()
                result.chapters.shouldHaveSize(0)
            } else {
                result shouldBe null
            }
        }
    }

    @Test
    fun `parsePage handles chapter with scanlator and release time`() {
        // Tests full chapter metadata parsing for premium plugins
        val payload = """
            {
              "chapters": [
                {
                  "name": "Chapter 1",
                  "path": "/ch/1",
                  "chapterNumber": 1,
                  "releaseTime": "2024-01-15T10:30:00Z",
                  "scanlator": "Premium Team",
                  "page": "Volume 1"
                }
              ]
            }
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parsePage(json, payload).shouldNotBeNull()

        parsed.chapters.shouldHaveSize(1)
        parsed.chapters[0].name shouldBe "Chapter 1"
        parsed.chapters[0].path shouldBe "/ch/1"
        parsed.chapters[0].chapterNumber shouldBe 1.0
        parsed.chapters[0].releaseTime shouldBe "2024-01-15T10:30:00Z"
        parsed.chapters[0].scanlator shouldBe "Premium Team"
        parsed.chapters[0].page shouldBe "Volume 1"
    }
}
