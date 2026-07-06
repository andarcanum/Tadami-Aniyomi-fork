package tachiyomi.data.anixart

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnixartCsvParserTest {

    private val header =
        "#,Русское название,Оригинальное название,Альтернативные названия," +
            "Добавлено в избранное,Статус просмотра,Моя оценка"

    @Test
    fun `parses a well-formed export row`() {
        val csv = header + "\n" +
            "1,Re:Жизнь в альтернативном мире,Re:Zero kara Hajimeru Isekai Seikatsu," +
            "ReZero,Добавлено,Смотрю,4 из 5"

        val rows = AnixartCsvParser.parse(csv)

        rows.size shouldBe 1
        val row = rows.first()
        row.index shouldBe 1
        row.originalTitle shouldBe "Re:Zero kara Hajimeru Isekai Seikatsu"
        row.status shouldBe AnixartStatus.WATCHING
        row.ratingOutOfTen shouldBe 8
        row.isFavorite shouldBe true
    }

    @Test
    fun `strips UTF-8 BOM from the first header column`() {
        val csv = "\uFEFF" + header + "\n" +
            "1,Наруто,Naruto,,,Просмотрено,5 из 5"

        val rows = AnixartCsvParser.parse(csv)

        rows.size shouldBe 1
        rows.first().status shouldBe AnixartStatus.COMPLETED
        rows.first().ratingOutOfTen shouldBe 10
    }

    @Test
    fun `handles quoted titles containing commas`() {
        val csv = header + "\n" +
            "1,\"Стальной алхимик, Братство\",\"Fullmetal Alchemist, Brotherhood\",,,В планах,"

        val rows = AnixartCsvParser.parse(csv)

        rows.first().russianTitle shouldBe "Стальной алхимик, Братство"
        rows.first().originalTitle shouldBe "Fullmetal Alchemist, Brotherhood"
        rows.first().status shouldBe AnixartStatus.PLAN_TO_WATCH
        rows.first().ratingOutOfTen shouldBe null
    }

    @Test
    fun `handles CRLF line endings and escaped quotes`() {
        val csv = header + "\r\n" +
            "1,\"Тест \"\"кавычки\"\"\",Test,,,Брошено,1 из 5\r\n"

        val rows = AnixartCsvParser.parse(csv)

        rows.size shouldBe 1
        rows.first().russianTitle shouldBe "Тест \"кавычки\""
        rows.first().status shouldBe AnixartStatus.DROPPED
        rows.first().ratingOutOfTen shouldBe 2
    }

    @Test
    fun `candidateTitles prioritizes original then russian then alternatives and dedups`() {
        val row = AnixartRow(
            index = 1,
            russianTitle = "Блич",
            originalTitle = "Bleach",
            alternativeTitles = "Bleach, BLICH, Блич",
            favoriteRaw = "",
            statusRaw = "Смотрю",
            ratingRaw = "",
        )

        row.candidateTitles() shouldContainExactly listOf("Bleach", "Блич", "BLICH")
    }

    @Test
    fun `candidateTitles drops placeholder and empty values`() {
        val row = AnixartRow(
            index = 1,
            russianTitle = "не указано",
            originalTitle = "[удалено]",
            alternativeTitles = "дубль удалить, , Real Title",
            favoriteRaw = "",
            statusRaw = "",
            ratingRaw = "",
        )

        row.candidateTitles() shouldContainExactly listOf("Real Title")
    }

    @Test
    fun `unknown or blank status maps to null and is not favorite`() {
        val row = AnixartRow(1, "А", "A", "", "", "Неизвестно", "")
        row.status shouldBe null
        row.isFavorite shouldBe false
    }

    @Test
    fun `finds columns by name regardless of order`() {
        val reordered = "Моя оценка,Статус просмотра,Альтернативные названия," +
            "Оригинальное название,Русское название,#,Добавлено в избранное"
        val csv = reordered + "\n" +
            "3 из 5,Смотрю,Alt,Original,Русское,7,Добавлено"

        val rows = AnixartCsvParser.parse(csv)

        val row = rows.first()
        row.index shouldBe 7
        row.originalTitle shouldBe "Original"
        row.russianTitle shouldBe "Русское"
        row.status shouldBe AnixartStatus.WATCHING
        row.ratingOutOfTen shouldBe 6
        row.isFavorite shouldBe true
    }

    @Test
    fun `rejects a file that is not an Anixart export`() {
        val csv = "name,email\nfoo,bar"
        try {
            AnixartCsvParser.parse(csv)
            throw AssertionError("Expected InvalidAnixartCsvException")
        } catch (e: AnixartCsvParser.InvalidAnixartCsvException) {
            (e.message?.contains("Missing columns") == true) shouldBe true
        }
    }

    @Test
    fun `skips blank lines and falls back to sequential index`() {
        val csv = header + "\n" +
            "\n" +
            ",Без номера,No Number,,,Смотрю,\n"

        val rows = AnixartCsvParser.parse(csv)

        rows.size shouldBe 1
        rows.first().index shouldBe 1
        rows.first().originalTitle shouldBe "No Number"
    }

    @Test
    fun `parses when optional columns like original title are missing`() {
        val minimalHeader = "Русское название,Статус просмотра"
        val csv = minimalHeader + "\n" +
            "Re:Жизнь в альтернативном мире,Смотрю"

        val rows = AnixartCsvParser.parse(csv)

        rows.size shouldBe 1
        val row = rows.first()
        row.russianTitle shouldBe "Re:Жизнь в альтернативном мире"
        row.originalTitle shouldBe ""
        row.status shouldBe AnixartStatus.WATCHING
        row.isFavorite shouldBe false
        row.ratingOutOfTen shouldBe null
    }

    @Test
    fun `cleanAnimeTitle and searchQueries handles various anime suffixes`() {
        AnixartRow.cleanAnimeTitle("Клинок, рассекающий демонов (ТВ-1)") shouldBe "Клинок, рассекающий демонов"
        AnixartRow.cleanAnimeTitle("Наруто: Ураганные хроники [ТВ-2]") shouldBe "Наруто: Ураганные хроники"
        AnixartRow.cleanAnimeTitle("Моя геройская академия 5 сезон") shouldBe "Моя геройская академия"
        AnixartRow.cleanAnimeTitle("Boku no Hero Academia (2021)") shouldBe "Boku no Hero Academia"
        AnixartRow.cleanAnimeTitle("Sword Art Online [ТВ]") shouldBe "Sword Art Online"
        // Latin suffixes without brackets (EN catalogue / romanized titles)
        AnixartRow.cleanAnimeTitle("Kimetsu no Yaiba TV-1") shouldBe "Kimetsu no Yaiba"
        AnixartRow.cleanAnimeTitle("Attack on Titan Season 2") shouldBe "Attack on Titan"
        AnixartRow.cleanAnimeTitle("One Piece Movie") shouldBe "One Piece"
        AnixartRow.cleanAnimeTitle("Naruto Shippuden Special") shouldBe "Naruto Shippuden"
        AnixartRow.cleanAnimeTitle("Bleach 2nd Season") shouldBe "Bleach"

        val row = AnixartRow(
            index = 1,
            russianTitle = "Клинок, рассекающий демонов (ТВ-1)",
            originalTitle = "Kimetsu no Yaiba (TV-1)",
            alternativeTitles = "Blade of Demon Destruction, Kimetsu no Yaiba [TV]",
            favoriteRaw = "",
            statusRaw = "Смотрю",
            ratingRaw = "",
        )

        row.searchQueries() shouldContainExactly listOf(
            "Kimetsu no Yaiba",
            "Клинок, рассекающий демонов",
            "Blade of Demon Destruction",
        )
    }
}
