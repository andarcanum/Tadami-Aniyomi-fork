package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

/** Варианты названия MangaDex-айтема (title map + altTitles en/ru) для проверки совпадения в fetchMeta. */
internal fun mangaDexTitleCandidates(item: JsonObject): List<String?> {
    val attributes = runCatching { item["attributes"]?.jsonObject }.getOrNull() ?: return emptyList()
    val titleObj = runCatching { attributes["title"]?.jsonObject }.getOrNull()
    val altTitles = runCatching { attributes["altTitles"]?.jsonArray?.mapNotNull { it.jsonObject } }.getOrNull()
    return buildList {
        titleObj?.values?.forEach { add(runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()) }
        altTitles?.forEach { alt ->
            add(runCatching { alt["en"]?.jsonPrimitive?.contentOrNull }.getOrNull())
            add(runCatching { alt["ru"]?.jsonPrimitive?.contentOrNull }.getOrNull())
        }
    }
}

internal fun parseMangaDexData(
    root: JsonObject,
    isRussianLocale: Boolean = false,
): List<DiscoveryTrendingItem> {
    val data = root["data"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() } ?: return emptyList()
    return data.mapNotNull { item ->
        val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val attributes = runCatching { item["attributes"]?.jsonObject }.getOrNull() ?: return@mapNotNull null
        val titleObj = runCatching { attributes["title"]?.jsonObject }.getOrNull()
        val enTitle = titleObj?.get("en")?.jsonPrimitive?.contentOrNull
        val jaTitle =
            titleObj?.get("ja-ro")?.jsonPrimitive?.contentOrNull ?: titleObj?.get("ja")?.jsonPrimitive?.contentOrNull
        val firstTitle = titleObj?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull

        val altTitles = runCatching { attributes["altTitles"]?.jsonArray?.mapNotNull { it.jsonObject } }.getOrNull()
        val ruTitle = altTitles?.firstNotNullOfOrNull { it["ru"]?.jsonPrimitive?.contentOrNull }

        val primaryTitle = if (isRussianLocale && !ruTitle.isNullOrBlank()) {
            ruTitle
        } else {
            enTitle ?: jaTitle ?: firstTitle ?: ruTitle ?: return@mapNotNull null
        }

        val relationships = runCatching { item["relationships"]?.jsonArray?.mapNotNull { it.jsonObject } }.getOrNull()
        val coverArt = relationships?.find { it["type"]?.jsonPrimitive?.contentOrNull == "cover_art" }
        val fileName = runCatching {
            coverArt?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val coverUrl = if (fileName != null) {
            "https://uploads.mangadex.org/covers/$id/$fileName.512.jpg"
        } else {
            null
        }

        val tags = runCatching {
            attributes["tags"]?.jsonArray?.mapNotNull { tag ->
                tag.jsonObject["attributes"]?.jsonObject?.get(
                    "name",
                )?.jsonObject?.get("en")?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull().orEmpty()

        val rawStatus = runCatching { attributes["status"]?.jsonPrimitive?.contentOrNull }.getOrNull()

        val altTitle = if (primaryTitle == ruTitle) {
            enTitle ?: jaTitle ?: firstTitle
        } else {
            ruTitle ?: altTitles?.firstNotNullOfOrNull {
                it["en"]?.jsonPrimitive?.contentOrNull
                    ?: it["ru"]?.jsonPrimitive?.contentOrNull
            }
        }

        DiscoveryTrendingItem(
            title = primaryTitle,
            cleanTitle = normalizeDiscoveryTitle(primaryTitle),
            altTitle = altTitle,
            coverUrl = coverUrl,
            anilistId = 0L,
            seasonLabel = null,
            genres = tags,
            provider = "mangadex_trend",
            releaseStatus = rawStatus,
        )
    }
}

/**
 * URL списка MangaDex с явным набором contentRating: NSFW-фильтр выключен —
 * добавляем erotica/pornographic (MangaDex — единственный провайдер с честным
 * серверным рейтингом контента).
 */
internal fun mangadexListUrl(orderParam: String, offset: Int, nsfwAllowed: Boolean): String {
    val ratings = if (nsfwAllowed) {
        "contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica&contentRating[]=pornographic"
    } else {
        "contentRating[]=safe&contentRating[]=suggestive"
    }
    return "https://api.mangadex.org/manga?limit=30&offset=$offset&$orderParam&includes[]=cover_art&$ratings"
}

/** V1: серверный статус-фильтр MangaDex — status[]=ongoing&status[]=completed&… (пустой фильтр — без параметров). */
internal fun mangadexStatusParam(releaseStatuses: Set<DiscoveryReleaseStatus>): String =
    releaseStatuses.joinToString("") { status ->
        when (status) {
            DiscoveryReleaseStatus.ONGOING -> "&status[]=ongoing"
            DiscoveryReleaseStatus.FINISHED -> "&status[]=completed"
            DiscoveryReleaseStatus.ANONS -> "" // у MangaDex нет статуса «анонс» в списочном фильтре
            DiscoveryReleaseStatus.PAUSED -> "&status[]=hiatus&status[]=cancelled"
        }
    }

internal fun filterMangaDexByStatus(
    items: List<DiscoveryTrendingItem>,
    releaseStatuses: Set<DiscoveryReleaseStatus>,
): List<DiscoveryTrendingItem> {
    if (releaseStatuses.isEmpty()) return items
    return items.filter { item ->
        val itemStatus = item.releaseStatus?.lowercase() ?: return@filter true
        val status = when (itemStatus) {
            "ongoing" -> DiscoveryReleaseStatus.ONGOING
            "completed" -> DiscoveryReleaseStatus.FINISHED
            "hiatus", "cancelled" -> DiscoveryReleaseStatus.PAUSED
            else -> null
        }
        status == null || status in releaseStatuses
    }
}

open class MangaDexTrendingSource(
    private val clientProvider: () -> OkHttpClient = { Injekt.get<NetworkHelper>().client },
    private val jsonProvider: () -> Json = { Injekt.get() },
    private val isRussianLocaleProvider: () -> Boolean = {
        java.util.Locale.getDefault().language == "ru"
    },
    private val nsfwAllowedProvider: () -> Boolean = { !discoveryNsfwFilterEnabled() },
) : DiscoveryTrendingSource {

    private val metaCache = DiscoveryLruCache<String, DiscoveryMeta>(200)

    private val headers = Headers.Builder()
        .add("User-Agent", "Tadami/1.0 (Android)")
        .build()

    override suspend fun fetch(
        mediaType: DiscoveryMediaType,
        season: TrendSeason,
        sort: TrendSort,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryTrendingItem> {
        if (mediaType != DiscoveryMediaType.MANGA) return emptyList()

        return try {
            val offset = (page - 1).coerceAtLeast(0) * 30
            val orderParam = when (sort) {
                TrendSort.TRENDING -> "order[latestUploadedChapter]=desc"
                TrendSort.SCORE -> "order[rating]=desc"
                TrendSort.POPULARITY -> "order[followedCount]=desc"
            }
            // V1: MangaDex поддерживает серверный статус-фильтр status[]=…
            val url = mangadexListUrl(orderParam, offset, nsfwAllowedProvider()) + mangadexStatusParam(releaseStatuses)

            val response = clientProvider().newCall(GET(url, headers = headers))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())

            val items = parseMangaDexData(response, isRussianLocaleProvider())
            filterMangaDexByStatus(items, releaseStatuses)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[MangaDexTrending] fetch FAILED: ${e.message}" }
            throw e
        }
    }

    override suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryTrendingItem> {
        if (mediaType != DiscoveryMediaType.MANGA || genres.isEmpty()) return emptyList()
        return try {
            val offset = (page - 1).coerceAtLeast(0) * 30
            val orderParam = when (sort) {
                TrendSort.SCORE -> "order[rating]=desc"
                else -> "order[followedCount]=desc"
            }
            val url = mangadexListUrl(orderParam, offset, nsfwAllowedProvider()) + mangadexStatusParam(releaseStatuses)

            val response = clientProvider().newCall(GET(url, headers = headers))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())

            val items = parseMangaDexData(response, isRussianLocaleProvider())
            filterMangaDexByStatus(items, releaseStatuses)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[MangaDexTrending] fetchByGenres FAILED: ${e.message}" }
            emptyList()
        }
    }

    override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
        if (mediaType != DiscoveryMediaType.MANGA) return null
        metaCache.get(title)?.let { return it }

        return try {
            val url = "https://api.mangadex.org/manga?title=${URLEncoder.encode(
                title,
                "UTF-8",
            )}&limit=5&includes[]=cover_art"
            val response = clientProvider().newCall(GET(url, headers = headers))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())

            val items = response["data"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            val first = items?.firstOrNull { item -> metaMatchesTitle(title, mangaDexTitleCandidates(item)) }
                ?: return null
            val attributes = first["attributes"]?.jsonObject ?: return null

            val descObj = attributes["description"]?.jsonObject
            val desc = descObj?.get("ru")?.jsonPrimitive?.contentOrNull
                ?: descObj?.get("en")?.jsonPrimitive?.contentOrNull
                ?: descObj?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull

            val tags = attributes["tags"]?.jsonArray?.mapNotNull { tag ->
                tag.jsonObject["attributes"]?.jsonObject?.get(
                    "name",
                )?.jsonObject?.get("en")?.jsonPrimitive?.contentOrNull
            }.orEmpty()

            val altTitles = attributes["altTitles"]?.jsonArray?.mapNotNull { it.jsonObject }
            val altTitle = altTitles?.firstNotNullOfOrNull {
                it["en"]?.jsonPrimitive?.contentOrNull
                    ?: it["ru"]?.jsonPrimitive?.contentOrNull
            }

            val meta = DiscoveryMeta(
                description = desc?.trim()?.takeIf { it.isNotEmpty() },
                genres = tags,
                altTitle = altTitle,
            )
            metaCache.put(title, meta)
            meta
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[MangaDexTrending] fetchMeta FAILED for '$title': ${e.message}" }
            null
        }
    }
}
