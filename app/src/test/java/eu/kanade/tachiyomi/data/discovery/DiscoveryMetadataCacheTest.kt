package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get
import java.util.concurrent.CopyOnWriteArrayList

class DiscoveryMetadataCacheTest {

    @Test
    fun `same title metadata is resolved separately for anime and manga`(): Unit = runBlocking {
        val requestedTypes = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val buffer = Buffer()
                chain.request().body!!.writeTo(buffer)
                val payload = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                val type = payload.getValue("variables").jsonObject.getValue("type").jsonPrimitive.content
                requestedTypes += type
                val body = when (type) {
                    "ANIME" -> """
                        {"data":{"Page":{"media":[{
                          "description":"Anime-only description",
                          "genres":["Action"],
                          "coverImage":{"large":"https://fixture.invalid/anime.jpg"},
                          "title":{"romaji":"Same Title Cache Regression","english":"Anime alias","native":null}
                        }]}}}
                    """.trimIndent()
                    "MANGA" -> """
                        {"data":{"Page":{"media":[{
                          "description":"Manga-only description",
                          "genres":["Romance"],
                          "coverImage":{"large":"https://fixture.invalid/manga.jpg"},
                          "title":{"romaji":"Same Title Cache Regression","english":"Manga alias","native":null}
                        }]}}}
                    """.trimIndent()
                    else -> "{\"data\":{\"Page\":{\"media\":[]}}}"
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
            val source = AniListTrendingSource(
                clientProvider = { client },
                jsonProvider = { Json },
                nsfwFilterProvider = { true },
            )
            withTimeout(5_000L) {
                source.fetchMeta("Same Title Cache Regression", DiscoveryMediaType.ANIME) shouldBe DiscoveryMeta(
                    description = "Anime-only description",
                    genres = listOf("Action"),
                    altTitle = "Anime alias",
                    coverUrl = "https://fixture.invalid/anime.jpg",
                )
                source.fetchMeta("Same Title Cache Regression", DiscoveryMediaType.MANGA) shouldBe DiscoveryMeta(
                    description = "Manga-only description",
                    genres = listOf("Romance"),
                    altTitle = "Manga alias",
                    coverUrl = "https://fixture.invalid/manga.jpg",
                )
                source.fetchMeta("Same Title Cache Regression", DiscoveryMediaType.ANIME) shouldBe DiscoveryMeta(
                    description = "Anime-only description",
                    genres = listOf("Action"),
                    altTitle = "Anime alias",
                    coverUrl = "https://fixture.invalid/anime.jpg",
                )
                source.fetchMeta("Same Title Cache Regression", DiscoveryMediaType.MANGA) shouldBe DiscoveryMeta(
                    description = "Manga-only description",
                    genres = listOf("Romance"),
                    altTitle = "Manga alias",
                    coverUrl = "https://fixture.invalid/manga.jpg",
                )
                requestedTypes.toList() shouldBe listOf("ANIME", "MANGA")
            }
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun `enabling adult filtering does not return previously unfiltered AniList metadata`(): Unit = runBlocking {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.filterNsfw().set(false)
        val requestedAdultFilters = CopyOnWriteArrayList<Boolean?>()
        val title = "AniList Adult Cache Regression"
        val client = adultMetadataClient(title, requestedAdultFilters)
        try {
            val source = AniListTrendingSource(
                clientProvider = { client },
                jsonProvider = { Json },
                nsfwFilterProvider = { preferences.filterNsfw().get() },
            )
            withTimeout(5_000L) {
                source.fetchMeta(title, DiscoveryMediaType.NOVEL) shouldBe DiscoveryMeta(
                    description = "Adult-only description",
                    genres = listOf("Hentai"),
                    altTitle = "Adult alias",
                    coverUrl = "https://fixture.invalid/adult.jpg",
                )
                preferences.filterNsfw().set(true)
                source.fetchMeta(title, DiscoveryMediaType.NOVEL) shouldBe null
                requestedAdultFilters.toList() shouldBe listOf(null, false)
            }
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun `new composite cannot reuse adult metadata cached before filtering was enabled`(): Unit = runBlocking {
        Injekt.addSingleton(DiscoveryPreferences(PersistingPreferenceStore()))
        val preferences = Injekt.get<DiscoveryPreferences>()
        preferences.filterNsfw().set(false)
        val requestedAdultFilters = CopyOnWriteArrayList<Boolean?>()
        val title = "Composite Adult Cache Regression"
        val client = adultMetadataClient(title, requestedAdultFilters)
        try {
            val anilist = AniListTrendingSource(
                clientProvider = { client },
                jsonProvider = { Json },
                nsfwFilterProvider = { preferences.filterNsfw().get() },
            )
            val unfiltered = CompositeTrendingSource(anilist = anilist, isRussianLocaleProvider = { false })
            withTimeout(5_000L) {
                unfiltered.fetchMeta(title, DiscoveryMediaType.NOVEL) shouldBe DiscoveryMeta(
                    description = "Adult-only description",
                    genres = listOf("Hentai"),
                    altTitle = "Adult alias",
                    coverUrl = "https://fixture.invalid/adult.jpg",
                )
                preferences.filterNsfw().set(true)
                CompositeTrendingSource.getCachedMeta(title, DiscoveryMediaType.NOVEL) shouldBe null
                val filtered = CompositeTrendingSource(anilist = anilist, isRussianLocaleProvider = { false })
                filtered.fetchMeta(title, DiscoveryMediaType.NOVEL) shouldBe null
                requestedAdultFilters.toList() shouldBe listOf(null, false)
            }
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun `adult filtering enabled during a response prevents stale metadata publication`(): Unit = runBlocking {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.filterNsfw().set(false)
        val requestedAdultFilters = CopyOnWriteArrayList<Boolean?>()
        val title = "In-flight Adult Cache Regression"
        val client = adultMetadataClient(title, requestedAdultFilters) { preferences.filterNsfw().set(true) }
        try {
            val source = AniListTrendingSource(
                clientProvider = { client },
                jsonProvider = { Json },
                nsfwFilterProvider = { preferences.filterNsfw().get() },
            )
            withTimeout(5_000L) {
                source.fetchMeta(title, DiscoveryMediaType.NOVEL) shouldBe null
                source.fetchMeta(title, DiscoveryMediaType.NOVEL) shouldBe null
                requestedAdultFilters.toList() shouldBe listOf(null, false)
            }
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun `manga and novel adaptations keep separate metadata despite the same AniList media type`(): Unit = runBlocking {
        val requestedNovelFormats = CopyOnWriteArrayList<Boolean>()
        val requestedMediaTypes = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer()
            chain.request().body!!.writeTo(buffer)
            val payload = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            val isNovel = "format: NOVEL" in payload.getValue("query").jsonPrimitive.content
            requestedMediaTypes += payload.getValue("variables").jsonObject.getValue("type").jsonPrimitive.content
            requestedNovelFormats += isNovel
            val body = if (isNovel) {
                """
                    {"data":{"Page":{"media":[{
                      "description":"Novel-only description","genres":["Mystery"],
                      "coverImage":{"large":"https://fixture.invalid/novel.jpg"},
                      "title":{"romaji":"Same Adaptation Cache Title","english":"Novel alias","native":null}
                    }]}}}
                """.trimIndent()
            } else {
                """
                    {"data":{"Page":{"media":[{
                      "description":"Manga-only description","genres":["Action"],
                      "coverImage":{"large":"https://fixture.invalid/manga.jpg"},
                      "title":{"romaji":"Same Adaptation Cache Title","english":"Manga alias","native":null}
                    }]}}}
                """.trimIndent()
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val source = AniListTrendingSource({ client }, { Json }, { true })
            withTimeout(5_000L) {
                val manga = DiscoveryMeta(
                    "Manga-only description",
                    listOf("Action"),
                    "Manga alias",
                    "https://fixture.invalid/manga.jpg",
                )
                val novel = DiscoveryMeta(
                    "Novel-only description",
                    listOf("Mystery"),
                    "Novel alias",
                    "https://fixture.invalid/novel.jpg",
                )
                source.fetchMeta("Same Adaptation Cache Title", DiscoveryMediaType.MANGA) shouldBe manga
                source.fetchMeta("Same Adaptation Cache Title", DiscoveryMediaType.NOVEL) shouldBe novel
                source.fetchMeta("Same Adaptation Cache Title", DiscoveryMediaType.MANGA) shouldBe manga
                source.fetchMeta("Same Adaptation Cache Title", DiscoveryMediaType.NOVEL) shouldBe novel
                requestedNovelFormats.toList() shouldBe listOf(false, true)
                requestedMediaTypes.toList() shouldBe listOf("MANGA", "MANGA")
            }
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    private fun adultMetadataClient(
        title: String,
        requestedAdultFilters: MutableList<Boolean?>,
        onUnfilteredResponse: () -> Unit = {},
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val buffer = Buffer()
            chain.request().body!!.writeTo(buffer)
            val variables = Json.parseToJsonElement(buffer.readUtf8()).jsonObject.getValue("variables").jsonObject
            val adultFilter = variables.getValue("isAdult").jsonPrimitive.booleanOrNull
            requestedAdultFilters += adultFilter
            val body = if (adultFilter == false) {
                "{\"data\":{\"Page\":{\"media\":[]}}}"
            } else {
                onUnfilteredResponse()
                """
                    {"data":{"Page":{"media":[{
                      "description":"Adult-only description",
                      "genres":["Hentai"],
                      "coverImage":{"large":"https://fixture.invalid/adult.jpg"},
                      "title":{"romaji":"$title","english":"Adult alias","native":null}
                    }]}}}
                """.trimIndent()
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
}
