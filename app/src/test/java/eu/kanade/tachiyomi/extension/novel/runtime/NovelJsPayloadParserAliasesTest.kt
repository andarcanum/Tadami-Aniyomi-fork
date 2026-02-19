package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class NovelJsPayloadParserAliasesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `parseChaptersArray supports alternate chapter key aliases`() {
        val payload = """
            [
              { "chapterTitle": "Chapter A", "href": "/novel/demo/a", "scanlator": "Team A" },
              { "name": "Chapter B", "link": "/novel/demo/b", "translator": "Team B" }
            ]
        """.trimIndent()

        val parsed = NovelJsPayloadParser.parseChaptersArray(json, payload)

        parsed.shouldHaveSize(2)
        parsed[0].name shouldBe "Chapter A"
        parsed[0].path shouldBe "/novel/demo/a"
        parsed[0].scanlator shouldBe "Team A"
        parsed[1].name shouldBe "Chapter B"
        parsed[1].path shouldBe "/novel/demo/b"
        parsed[1].scanlator shouldBe "Team B"
    }
}
