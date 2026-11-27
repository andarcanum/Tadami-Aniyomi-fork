package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class AniSkipApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `parseSkipResponse parses mixed-ed alongside standard skip types`() {
        val payload = """
            {
              "found": true,
              "results": [
                {
                  "interval": { "startTime": 10.0, "endTime": 100.0 },
                  "skipType": "op"
                },
                {
                  "interval": { "startTime": 1200.5, "endTime": 1290.5 },
                  "skipType": "mixed-ed"
                },
                {
                  "interval": { "startTime": 1300.0, "endTime": 1380.0 },
                  "skipType": "ed"
                }
              ]
            }
        """.trimIndent()

        val stamps = AniSkipApi.parseSkipResponse(payload, json)
        stamps shouldBe listOf(
            TimeStamp(
                start = 10.0,
                end = 100.0,
                name = "Opening",
                type = ChapterType.Opening,
            ),
            TimeStamp(
                start = 1200.5,
                end = 1290.5,
                name = "Mixed-ed",
                type = ChapterType.Ending,
            ),
            TimeStamp(
                start = 1300.0,
                end = 1380.0,
                name = "Ending",
                type = ChapterType.Ending,
            ),
        )
        stamps!! shouldHaveSize 3
    }

    @Test
    fun `parseSkipResponse returns null when found is false`() {
        val payload = """{"found": false, "results": []}"""
        AniSkipApi.parseSkipResponse(payload, json) shouldBe null
    }

    @Test
    fun `parseAniListMalId extracts numeric idMal and handles null`() {
        AniSkipApi.parseAniListMalId("""{"data":{"Media":{"idMal":5114}}}""") shouldBe 5114L
        AniSkipApi.parseAniListMalId("""{"data":{"Media":{"idMal":null}}}""") shouldBe 0L
        AniSkipApi.parseAniListMalId("""{"errors":[]}""") shouldBe 0L
    }
}
