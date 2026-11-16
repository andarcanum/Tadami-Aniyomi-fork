package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import java.util.concurrent.CopyOnWriteArrayList

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
    fun `same Shikimori title metadata is resolved separately for anime and manga`(): Unit = runBlocking {
        val requestedPaths = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                requestedPaths += path
                val body = when (path) {
                    "/api/animes" -> """
                        [{"id":101,"name":"Same Shikimori Cache Title","russian":"Anime alias",
                          "image":null,"score":8.0,"status":"released","kind":"tv",
                          "description":null,"genres":null,"url":"/animes/101"}]
                    """.trimIndent()
                    "/api/mangas" -> """
                        [{"id":202,"name":"Same Shikimori Cache Title","russian":"Manga alias",
                          "image":null,"score":8.0,"status":"released","kind":"manga",
                          "description":null,"genres":null,"url":"/mangas/202"}]
                    """.trimIndent()
                    "/api/animes/101" -> """
                        {"id":101,"name":"Same Shikimori Cache Title","russian":"Anime alias",
                         "image":null,"score":8.0,"status":"released","kind":"tv",
                         "description":"Anime-only synopsis",
                         "genres":[{"id":1,"name":"Action","russian":"Экшен"}],"url":"/animes/101"}
                    """.trimIndent()
                    "/api/mangas/202" -> """
                        {"id":202,"name":"Same Shikimori Cache Title","russian":"Manga alias",
                         "image":null,"score":8.0,"status":"released","kind":"manga",
                         "description":"Manga-only synopsis",
                         "genres":[{"id":2,"name":"Romance","russian":"Романтика"}],"url":"/mangas/202"}
                    """.trimIndent()
                    else -> "[]"
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        try {
            val source = ShikimoriTrendingSource(
                clientProvider = { client },
                jsonProvider = { Json },
                isRussianLocaleProvider = { false },
                nsfwFilterProvider = { true },
            )
            withTimeout(10_000L) {
                val anime = DiscoveryMeta("Anime-only synopsis", listOf("Action"), "Anime alias")
                val manga = DiscoveryMeta("Manga-only synopsis", listOf("Romance"), "Manga alias")
                source.fetchMeta("Same Shikimori Cache Title", DiscoveryMediaType.ANIME) shouldBe anime
                source.fetchMeta("Same Shikimori Cache Title", DiscoveryMediaType.MANGA) shouldBe manga
                source.fetchMeta("Same Shikimori Cache Title", DiscoveryMediaType.ANIME) shouldBe anime
                source.fetchMeta("Same Shikimori Cache Title", DiscoveryMediaType.MANGA) shouldBe manga
                source.fetchMeta("Same Shikimori Cache Title", DiscoveryMediaType.NOVEL) shouldBe null
                requestedPaths.toList() shouldBe listOf(
                    "/api/animes",
                    "/api/animes/101",
                    "/api/mangas",
                    "/api/mangas/202",
                )
            }
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
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
