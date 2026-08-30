package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaCover
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItemTitle
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaListEntry
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaPublishData
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaScaledCover
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.ZoneId

class MangaBakaTest {

    @Test
    fun `primary english title wins over native romanization`() {
        val item = item(
            titles = listOf(
                title(language = "ja-Latn", title = "Sword Art Online", traits = listOf("native")),
                title(language = "en", title = "Sword Art Online", traits = listOf("official"), isPrimary = true),
            ),
        )

        item.chooseBestTitle() shouldBe "Sword Art Online"
    }

    @Test
    fun `official title wins over plain title within a language`() {
        val item = item(
            titles = listOf(
                title(language = "en", title = "SAO"),
                title(language = "en", title = "Sword Art Online", traits = listOf("official")),
            ),
        )

        item.chooseBestTitle() shouldBe "Sword Art Online"
    }

    @Test
    fun `falls back to first title when no priority language matches`() {
        val item = item(
            titles = listOf(
                title(language = "ru", title = "Мастера Меча Онлайн"),
            ),
        )

        item.chooseBestTitle() shouldBe "Мастера Меча Онлайн"
    }

    @Test
    fun `null titles fall back to id placeholder`() {
        val item = item(titles = null)

        item.chooseBestTitle() shouldBe "ID: 85378 - Could not find name! (report on the MangaBaka Discord)"
    }

    @Test
    fun `statuses map to api states and back`() {
        val pairs = listOf(
            MangaBaka.READING to "reading",
            MangaBaka.COMPLETED to "completed",
            MangaBaka.PAUSED to "paused",
            MangaBaka.DROPPED to "dropped",
            MangaBaka.PLAN_TO_READ to "plan_to_read",
            MangaBaka.REREADING to "rereading",
            MangaBaka.CONSIDERING to "considering",
        )

        pairs.forEach { (status, state) ->
            val track = MangaTrack.create(12L).apply { this.status = status }
            track.toApiStatus() shouldBe state

            MangaBakaListEntry(
                state = state,
                startDate = null,
                finishDate = null,
                isPrivate = false,
                progressChapter = null,
                rating = null,
            ).getStatus() shouldBe status
        }
    }

    @Test
    fun `score ranges match step sizes`() {
        MangaBaka.scoreRange(MangaBaka.STEP_1).toList() shouldBe (0..100).map(Int::toString).map(String::toInt)
        MangaBaka.scoreRange(MangaBaka.STEP_5).toList().size shouldBe 21
        MangaBaka.scoreRange(MangaBaka.STEP_10).toList().size shouldBe 11
        MangaBaka.scoreRange(MangaBaka.STEP_20).toList().size shouldBe 6
        MangaBaka.scoreRange(MangaBaka.STEP_25).toList() shouldBe listOf(0, 25, 50, 75, 100)
    }

    @Test
    fun `unknown score step throws`() {
        assertThrows<Exception> { MangaBaka.scoreRange("STEP_2") }
    }

    @Test
    fun `iso dates are parsed as local start of day`() {
        val newYork = ZoneId.of("America/New_York")
        val expected = Instant.parse("2026-01-05T05:00:00Z").toEpochMilli()

        parseIsoDateAsLocalStartOfDay("2026-01-05T00:00:00Z", newYork) shouldBe expected
        parseIsoDateAsLocalStartOfDay("2026-01-05", newYork) shouldBe expected
    }

    @Test
    fun `missing date parses to null`() {
        parseIsoDateAsLocalStartOfDay(null, ZoneId.of("America/New_York")).shouldBeNull()
    }

    private fun item(titles: List<MangaBakaItemTitle>?) = MangaBakaItem(
        id = 85378L,
        cover = MangaBakaCover(MangaBakaScaledCover("https://example.test/cover.webp")),
        authors = listOf("Reki Kawahara"),
        artists = emptyList(),
        description = "desc",
        published = MangaBakaPublishData("2010-04-10"),
        status = "releasing",
        type = "novel",
        rating = 80.11,
        titles = titles,
    )

    private fun title(
        language: String,
        title: String,
        traits: List<String> = emptyList(),
        isPrimary: Boolean = false,
    ) = MangaBakaItemTitle(
        language = language,
        traits = traits,
        title = title,
        isPrimary = isPrimary,
    )
}
