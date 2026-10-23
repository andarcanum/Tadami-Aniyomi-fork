package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class AniListTrendingSourceTest {

    @Test
    fun `russian profile genres are translated to anilist english names`() {
        val variables = anilistGenreVariables(listOf("драма", "психологическое"))
        variables shouldContain "Drama"
        variables shouldContain "Psychological"
    }

    @Test
    fun `unknown genres pass through unchanged`() {
        anilistGenreVariables(listOf("экзотика-сюр")) shouldContain "экзотика-сюр"
    }

    @Test
    fun `result keeps originals and is capped`() {
        val big = (1..20).map { "genre$it" }
        anilistGenreVariables(big + "драма").size shouldBe 12
        anilistGenreVariables(listOf("Action")) shouldContain "Action"
    }

    @Test
    fun `season window resolves current and next with year rollover`() {
        resolveSeasonWindow(nowMonth = 2, nowYear = 2026, next = false) shouldBe ("WINTER" to 2026)
        resolveSeasonWindow(nowMonth = 2, nowYear = 2026, next = true) shouldBe ("SPRING" to 2026)
        resolveSeasonWindow(nowMonth = 11, nowYear = 2026, next = false) shouldBe ("FALL" to 2026)
        resolveSeasonWindow(nowMonth = 11, nowYear = 2026, next = true) shouldBe ("WINTER" to 2027)
        resolveSeasonWindow(nowMonth = 8, nowYear = 2026, next = true) shouldBe ("FALL" to 2026)
    }

    @Test
    fun `parse extracts titles and covers and skips untitled`() {
        val json = Json.parseToJsonElement(
            """
            {"data":{"Page":{"media":[
              {"id":1,"title":{"romaji":"Frieren"},"coverImage":{"large":"http://c/1.jpg"}},
              {"id":2,"title":{"romaji":null},"coverImage":{"large":"http://c/2.jpg"}},
              {"id":3,"title":{"romaji":"Overlord"},"coverImage":null}
            ]}}}
            """.trimIndent(),
        ) as JsonObject
        val items = parseTrendingPage(json)
        items.map { it.title } shouldBe listOf("Frieren", "Overlord")
        items[0].coverUrl shouldBe "http://c/1.jpg"
        items[1].coverUrl shouldBe null
        items[0].cleanTitle shouldBe "frieren"
    }

    @Test
    fun `parse on empty page returns empty list`() {
        val json = Json.parseToJsonElement("""{"data":{"Page":{"media":[]}}}""") as JsonObject
        parseTrendingPage(json) shouldBe emptyList()
    }

    @Test
    fun `parse falls back to english and native when romaji is null`() {
        val json = Json.parseToJsonElement(
            """
            {"data":{"Page":{"media":[
              {"id":1,"title":{"romaji":null,"english":"Solo Leveling","native":"나 혼자만 레벨업"},"coverImage":{"large":"http://c/1.jpg"}},
              {"id":2,"title":{"romaji":null,"english":null,"native":"葬送のフリーレン"},"coverImage":{"large":"http://c/2.jpg"}},
              {"id":3,"title":{"romaji":null,"english":null,"native":null},"coverImage":{"large":"http://c/3.jpg"}}
            ]}}}
            """.trimIndent(),
        ) as JsonObject
        val items = parseTrendingPage(json)
        items.map { it.title } shouldBe listOf("Solo Leveling", "葬送のフリーレン")
        items[0].cleanTitle shouldBe "solo leveling"
        items[1].cleanTitle shouldBe "葬送のフリーレン"
    }

    @Test
    fun `buildSeasonQuery and buildPopularQuery request genres in media body`() {
        val seasonQuery = buildSeasonQuery(hasStatuses = false)
        seasonQuery.contains("coverImage { large } genres") shouldBe true

        val popularQuery = buildPopularQuery(format = "NOVEL", hasStatuses = true)
        popularQuery.contains("coverImage { large } genres") shouldBe true
    }

    @Test
    fun `parse extracts genres list from media item`() {
        val json = Json.parseToJsonElement(
            """
            {"data":{"Page":{"media":[
              {"id":1,"title":{"romaji":"Frieren"},"coverImage":{"large":"http://c/1.jpg"},"genres":["Adventure","Fantasy"]}
            ]}}}
            """.trimIndent(),
        ) as JsonObject
        val items = parseTrendingPage(json)
        items.single().genres shouldBe listOf("Adventure", "Fantasy")
    }
}
