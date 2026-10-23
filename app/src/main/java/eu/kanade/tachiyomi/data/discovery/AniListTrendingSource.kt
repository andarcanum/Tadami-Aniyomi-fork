package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.sources.AniListRequestGuard
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

data class DiscoveryTrendingItem(
    val title: String,
    val cleanTitle: String,
    val coverUrl: String?,
    val anilistId: Long = 0L,
    val seasonLabel: String?,
    val genres: List<String> = emptyList(),
    val provider: String = "anilist_trend",
    /** V1: сырой статус провайдера (Shikimori «ongoing/released/…»), для пост-фильтра. */
    val releaseStatus: String? = null,
    val altTitle: String? = null,
)

data class DiscoveryMeta(
    val description: String?,
    val genres: List<String>,
    val altTitle: String?,
    /** Обложка провайдера — для lazy cover recovery карточек без thumbnail (NU). */
    val coverUrl: String? = null,
)

enum class TrendSeason { CURRENT, NEXT, BOTH }

enum class TrendSort(val anilist: String) {
    POPULARITY("POPULARITY_DESC"),
    SCORE("SCORE_DESC"),
    TRENDING("TRENDING_DESC"),
}

/**
 * AniList genre_in матчится со своей фиксированной англ. таксономией: жанры профиля
 * (часто русские) нужно переводить. Резолв через GenreOntology (любой язык → canonical
 * key → English display name как у AniList), raw-жанры проходят как есть.
 */
internal fun anilistGenreVariables(genres: List<String>): List<String> {
    val out = LinkedHashSet<String>()
    genres.forEach { genre ->
        out += genre
        GenreMatcher.resolve(genre).forEach { canonical ->
            GenreOntology.displayNames[canonical]?.first?.let { out += it }
        }
    }
    return out.toList().take(12)
}

internal fun resolveSeasonWindow(nowMonth: Int, nowYear: Int, next: Boolean): Pair<String, Int> {
    val order = listOf("WINTER", "SPRING", "SUMMER", "FALL")
    val idx = ((nowMonth - 1) / 3).coerceIn(0, 3)
    val target = if (next) idx + 1 else idx
    return if (target > 3) order[0] to (nowYear + 1) else order[target] to nowYear
}

internal fun buildSeasonQuery(hasStatuses: Boolean): String {
    val statusArg = if (hasStatuses) ", status_in: \$statuses" else ""
    return """
        query (${'$'}season: MediaSeason, ${'$'}year: Int, ${'$'}sort: [MediaSort], ${'$'}page: Int, ${'$'}isAdult: Boolean${if (hasStatuses) ", ${'$'}statuses: [MediaStatus]" else ""}) {
          Page(page: ${'$'}page, perPage: 50) {
            media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}year, sort: ${'$'}sort, isAdult: ${'$'}isAdult$statusArg) {
              id title { romaji english native } coverImage { large } genres
            }
          }
        }
    """.trimIndent()
}

internal fun buildPopularQuery(format: String?, hasStatuses: Boolean): String {
    val formatArg = if (format != null) ", format: \$format" else ""
    val statusArg = if (hasStatuses) ", status_in: \$statuses" else ""
    return """
        query (${'$'}type: MediaType, ${'$'}sort: [MediaSort], ${'$'}page: Int, ${'$'}isAdult: Boolean${if (format != null) ", ${'$'}format: MediaFormat" else ""}${if (hasStatuses) ", ${'$'}statuses: [MediaStatus]" else ""}) {
          Page(page: ${'$'}page, perPage: 50) {
            media(type: ${'$'}type, sort: ${'$'}sort, isAdult: ${'$'}isAdult$formatArg$statusArg) {
              id title { romaji english native } coverImage { large } genres
            }
          }
        }
    """.trimIndent()
}

internal fun parseTrendingPage(page: JsonObject, seasonLabel: String? = null): List<DiscoveryTrendingItem> =
    (((page["data"] as? JsonObject)?.get("Page") as? JsonObject)?.get("media") as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.mapNotNull { media ->
            val titleObj = media["title"] as? JsonObject
            val romaji = titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
            val english = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
            val native = titleObj?.get("native")?.jsonPrimitive?.contentOrNull
            val title = romaji ?: english ?: native ?: return@mapNotNull null
            val altTitle = if (title == romaji) {
                english ?: native
            } else if (title == english) {
                romaji ?: native
            } else {
                romaji ?: english
            }
            DiscoveryTrendingItem(
                title = title,
                cleanTitle = normalizeDiscoveryTitle(title),
                coverUrl = (media["coverImage"] as? JsonObject)?.get("large")?.jsonPrimitive?.contentOrNull,
                anilistId = media["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                seasonLabel = seasonLabel,
                genres = (media["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                provider = "anilist_trend",
                altTitle = altTitle,
            )
        }
        .orEmpty()

open class AniListTrendingSource(
    private val clientProvider: () -> okhttp3.OkHttpClient = { Injekt.get<NetworkHelper>().client },
    private val jsonProvider: () -> Json = { Injekt.get() }, // lazy: конструктор не требует Injekt-реестр (юнит-тесты)
    private val nsfwFilterProvider: () -> Boolean = { discoveryNsfwFilterEnabled() },
) : DiscoveryTrendingSource {

    private val metaCache = DiscoveryLruCache<String, DiscoveryMeta>(200)

    /**
     * isAdult=false исключает взрослый контент серверной фильтрацией AniList;
     * null (фильтр выключен) — без ограничения.
     */
    private fun adultVariableValue(): Boolean? = if (nsfwFilterProvider()) false else null

    override suspend fun fetch(
        mediaType: DiscoveryMediaType,
        season: TrendSeason,
        sort: TrendSort,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryTrendingItem> {
        return try {
            when (mediaType) {
                DiscoveryMediaType.ANIME -> {
                    val now = LocalDate.now()
                    val windows = when (season) {
                        TrendSeason.CURRENT -> listOf(false)
                        TrendSeason.NEXT -> listOf(true)
                        TrendSeason.BOTH -> listOf(false, true)
                    }
                    windows.flatMap { next ->
                        val (s, year) = resolveSeasonWindow(now.monthValue, now.year, next)
                        querySeason(
                            s,
                            year,
                            sort,
                            if (next) "next" else "current",
                            page = page,
                            releaseStatuses = releaseStatuses,
                        )
                    }
                }
                DiscoveryMediaType.MANGA -> queryPopular(
                    type = "MANGA",
                    format = null,
                    sort = sort,
                    page = page,
                    releaseStatuses = releaseStatuses,
                )
                DiscoveryMediaType.NOVEL -> queryPopular(
                    type = "MANGA",
                    format = "NOVEL",
                    sort = sort,
                    page = page,
                    releaseStatuses = releaseStatuses,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Пробрасываем: координатор пометит ряд failed и НЕ затрёт кэш.
            logcat { "[DiscoveryTrending] FAILED: ${e.message}" }
            throw e
        }
    }

    private suspend fun querySeason(
        season: String,
        year: Int,
        sort: TrendSort,
        label: String,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryTrendingItem> {
        val query = buildSeasonQuery(releaseStatuses.isNotEmpty())
        val payload = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    put("season", season)
                    put("year", year)
                    put("page", page)
                    put("isAdult", adultVariableValue())
                    put(
                        "sort",
                        buildJsonArray {
                            add(JsonPrimitive(sort.anilist))
                        },
                    )
                    if (releaseStatuses.isNotEmpty()) {
                        put(
                            "statuses",
                            buildJsonArray {
                                releaseStatuses.forEach { add(JsonPrimitive(it.anilistValue)) }
                            },
                        )
                    }
                },
            )
        }
        return post(payload).let { parseTrendingPage(it, label) }
    }

    private suspend fun queryPopular(
        type: String,
        format: String?,
        sort: TrendSort,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryTrendingItem> {
        val query = buildPopularQuery(format, releaseStatuses.isNotEmpty())
        val payload = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    put("type", type)
                    put("page", page)
                    put("isAdult", adultVariableValue())
                    put(
                        "sort",
                        buildJsonArray {
                            add(JsonPrimitive(sort.anilist))
                        },
                    )
                    if (format != null) put("format", format)
                    if (releaseStatuses.isNotEmpty()) {
                        put(
                            "statuses",
                            buildJsonArray {
                                releaseStatuses.forEach { add(JsonPrimitive(it.anilistValue)) }
                            },
                        )
                    }
                },
            )
        }
        return post(payload).let { parseTrendingPage(it, null) }
    }

    /** Кандидаты ряда «Твой вкус»: AniList `genre_in` по топ-жанрам профиля. */
    override suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryTrendingItem> {
        if (genres.isEmpty()) return emptyList()
        return try {
            val (type, format) = when (mediaType) {
                DiscoveryMediaType.ANIME -> "ANIME" to null
                DiscoveryMediaType.MANGA -> "MANGA" to null
                DiscoveryMediaType.NOVEL -> "MANGA" to "NOVEL"
            }
            queryByGenres(type, format, genres, sort, page = page, releaseStatuses = releaseStatuses)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[DiscoveryTrending] genre query FAILED: ${e.message}" }
            throw e
        }
    }

    private suspend fun queryByGenres(
        type: String,
        format: String?,
        genres: List<String>,
        sort: TrendSort,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryTrendingItem> {
        val formatArg = if (format != null) ", format: ${'$'}format" else ""
        val statusArg = if (releaseStatuses.isNotEmpty()) ", status_in: ${'$'}statuses" else ""
        val query = """
            query (${'$'}type: MediaType, ${'$'}genres: [String], ${'$'}sort: [MediaSort], ${'$'}page: Int, ${'$'}isAdult: Boolean${if (format != null) ", ${'$'}format: MediaFormat" else ""}${if (releaseStatuses.isNotEmpty()) ", ${'$'}statuses: [MediaStatus]" else ""}) {
              Page(page: ${'$'}page, perPage: 50) {
                media(type: ${'$'}type, genre_in: ${'$'}genres, sort: ${'$'}sort, isAdult: ${'$'}isAdult$formatArg$statusArg) {
                  id title { romaji english native } coverImage { large } genres
                }
              }
            }
        """.trimIndent()
        val payload = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    put("type", type)
                    put("genres", buildJsonArray { anilistGenreVariables(genres).forEach { add(JsonPrimitive(it)) } })
                    put("page", page)
                    put("isAdult", adultVariableValue())
                    put(
                        "sort",
                        buildJsonArray {
                            add(JsonPrimitive(sort.anilist))
                        },
                    )
                    if (format != null) put("format", format)
                    if (releaseStatuses.isNotEmpty()) {
                        put(
                            "statuses",
                            buildJsonArray {
                                releaseStatuses.forEach { add(JsonPrimitive(it.anilistValue)) }
                            },
                        )
                    }
                },
            )
        }
        return post(payload).let { parseTrendingPage(it, null) }
    }

    /** Лёгкие метаданные для превью-листа: описание/жанры/альт-тайтл по тайтлу (кэш в памяти). */
    override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
        metaCache.get(title)?.let { return it }
        return try {
            val type = when (mediaType) {
                DiscoveryMediaType.ANIME -> "ANIME"
                DiscoveryMediaType.MANGA -> "MANGA"
                DiscoveryMediaType.NOVEL -> "MANGA"
            }
            val formatArg = if (mediaType == DiscoveryMediaType.NOVEL) ", format: NOVEL" else ""
            // Независимый NSFW-фильтр действует и в meta-поиске: прежний пропуск
            // мог показать 18+ описание в шторке предпросмотра (null = без ограничения).
            val query = """
                query (${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}isAdult: Boolean) {
                  Page(page: 1, perPage: 5) {
                    media(search: ${'$'}search, type: ${'$'}type$formatArg, isAdult: ${'$'}isAdult) {
                      description(asHtml: false)
                      genres
                      coverImage { large }
                      title { romaji english native }
                    }
                  }
                }
            """.trimIndent()
            val payload = buildJsonObject {
                put("query", query)
                put(
                    "variables",
                    buildJsonObject {
                        put("search", title)
                        put("type", type)
                        put("isAdult", adultVariableValue())
                    },
                )
            }
            val page = post(payload)
            val mediaList = (
                ((page["data"] as? JsonObject)?.get("Page") as? JsonObject)
                    ?.get("media") as? JsonArray
                )?.mapNotNull { it as? JsonObject }.orEmpty()
            val media = mediaList.firstOrNull { candidate ->
                val t = candidate["title"] as? JsonObject
                metaMatchesTitle(
                    title,
                    listOf(
                        t?.get("romaji")?.jsonPrimitive?.contentOrNull,
                        t?.get("english")?.jsonPrimitive?.contentOrNull,
                        t?.get("native")?.jsonPrimitive?.contentOrNull,
                    ),
                )
            } ?: return null
            val meta = DiscoveryMeta(
                description = media["description"]?.jsonPrimitive?.contentOrNull
                    ?.replace(Regex("<[^>]*>"), "")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                genres = (media["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                altTitle = (media["title"] as? JsonObject)?.let { t ->
                    t["english"]?.jsonPrimitive?.contentOrNull ?: t["native"]?.jsonPrimitive?.contentOrNull
                },
                coverUrl = (media["coverImage"] as? JsonObject)?.get("large")?.jsonPrimitive?.contentOrNull,
            )
            metaCache.put(title, meta)
            meta
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[DiscoveryTrending] meta FAILED for '$title': ${e.message}" }
            null
        }
    }

    private suspend fun post(payload: JsonObject): JsonObject {
        AniListRequestGuard.ensureClosed()
        AniListRequestGuard.acquire()
        val body = payload.toString().toRequestBody(jsonMime)
        return try {
            clientProvider()
                .newCall(POST("https://graphql.anilist.co/", headers = AniListRequestGuard.headers, body = body))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.contains("403") == true) AniListRequestGuard.reportForbidden()
            throw e
        }
    }
}
