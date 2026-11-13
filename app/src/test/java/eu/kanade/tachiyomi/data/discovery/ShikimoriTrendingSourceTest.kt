package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test

class ShikimoriTrendingSourceTest {

    @Test
    fun `parseShikimoriItems uses russian title when in russian locale`() {
        val items = listOf(
            ShikimoriMediaItem(
                id = 5114L,
                name = "Fullmetal Alchemist: Brotherhood",
                russian = "Стальной алхимик: Братство",
                image = ShikimoriCover(original = "/system/animes/original/5114.jpg"),
                score = 9.1,
                genres = listOf(ShikimoriGenre(1L, "Action", "Экшен")),
            ),
        )

        val parsedRu = parseShikimoriItems(items, seasonLabel = "current", isRussianLocale = true)
        parsedRu.size shouldBe 1
        parsedRu.first().title shouldBe "Стальной алхимик: Братство"
        parsedRu.first().cleanTitle shouldBe "стальной алхимик братство"
        parsedRu.first().coverUrl shouldBe "https://shikimori.one/system/animes/original/5114.jpg"
        parsedRu.first().genres shouldBe listOf("Экшен")
        parsedRu.first().provider shouldBe "shikimori_trend"

        val parsedEn = parseShikimoriItems(items, seasonLabel = "current", isRussianLocale = false)
        parsedEn.first().title shouldBe "Fullmetal Alchemist: Brotherhood"
        parsedEn.first().cleanTitle shouldBe "fullmetal alchemist brotherhood"
    }

    @Test
    fun `parseShikimoriItems skips blank titles`() {
        val items = listOf(
            ShikimoriMediaItem(id = 1L, name = "", russian = null),
            ShikimoriMediaItem(id = 2L, name = "Valid Anime", russian = null),
        )
        val parsed = parseShikimoriItems(items)
        parsed.size shouldBe 1
        parsed.first().title shouldBe "Valid Anime"
    }

    @Test
    fun `mapGenresToIds matches by english and russian names within kind`() {
        val catalog = listOf(
            ShikimoriGenreDto(1L, "Action", "Экшен", "anime"),
            ShikimoriGenreDto(2L, "Fantasy", "Фэнтези", "anime"),
            ShikimoriGenreDto(3L, "Fantasy", "Фэнтези", "manga"),
            ShikimoriGenreDto(4L, "Samurai", "Самураи", "anime"),
        )
        mapGenresToIds(listOf("Фэнтези"), catalog, "anime") shouldBe listOf(2L)
        mapGenresToIds(listOf("Fantasy"), catalog, "manga") shouldBe listOf(3L)
        mapGenresToIds(listOf("Экшен", "Драма"), catalog, "anime") shouldBe listOf(1L)
        mapGenresToIds(listOf("Комедия"), catalog, "anime") shouldBe emptyList()
    }

    @Test
    fun `list url includes status, genres and censored params`() {
        val url = shikimoriListUrl(
            endpoint = "animes",
            page = 2,
            order = "popularity",
            status = "ongoing",
            genreIds = listOf(1L, 2L),
            censored = true,
        )
        url.contains("https://shikimori.one/api/animes?") shouldBe true
        url.contains("page=2") shouldBe true
        url.contains("status=ongoing") shouldBe true
        url.contains("genre=1,2") shouldBe true
        url.contains("censored=true") shouldBe true

        val plain = shikimoriListUrl(endpoint = "mangas", page = 1, order = "ranked", censored = false)
        plain.contains("censored=false") shouldBe true
        plain.contains("genre=") shouldBe false
        plain.contains("status=") shouldBe false
    }

    @Test
    fun `resolveShikimoriStatusFilter joins multiple statuses instead of falling back`() {
        val (serverStatus, clientFilter) = resolveShikimoriStatusFilter(
            seasonStatus = "ongoing",
            releaseStatuses = setOf(
                tachiyomi.domain.discovery.model.DiscoveryReleaseStatus.FINISHED,
                tachiyomi.domain.discovery.model.DiscoveryReleaseStatus.PAUSED,
            ),
        )
        serverStatus shouldBe "on_hiatus,released"
        clientFilter shouldBe setOf("released", "on_hiatus")
    }
}
