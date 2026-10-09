package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

@Serializable
internal data class ShikimoriMediaItem(
    val id: Long,
    val name: String,
    val russian: String? = null,
    val image: ShikimoriCover? = null,
    val score: Double = 0.0,
    val status: String? = null,
    val kind: String? = null,
    val description: String? = null,
    val genres: List<ShikimoriGenre>? = null,
    val url: String? = null,
)

@Serializable
internal data class ShikimoriCover(
    val original: String? = null,
    val preview: String? = null,
)

@Serializable
internal data class ShikimoriGenre(
    val id: Long = 0L,
    val name: String = "",
    val russian: String? = null,
)

@Serializable
internal data class ShikimoriGenreDto(
    val id: Long = 0L,
    val name: String = "",
    val russian: String? = null,
    val kind: String? = null,
)

/** Жанры профиля → id жанров Shikimori: матчинг эвристикой GenreMatcher (name/russian, любые формы). */
internal fun mapGenresToIds(
    profileGenres: List<String>,
    catalog: List<ShikimoriGenreDto>,
    mediaKind: String,
): List<Long> = catalog.asSequence()
    .filter { it.kind == null || it.kind == mediaKind }
    .filter { g -> profileGenres.any { genre -> matchesCatalogGenre(genre, g) } }
    .map { it.id }
    .distinct()
    .toList()

private fun matchesCatalogGenre(profileGenre: String, entry: ShikimoriGenreDto): Boolean {
    if (entry.name.isNotBlank() && GenreMatcher.matches(profileGenre, entry.name)) return true
    val russian = entry.russian ?: return false
    return russian.isNotBlank() && GenreMatcher.matches(profileGenre, russian)
}

internal fun parseShikimoriItems(
    items: List<ShikimoriMediaItem>,
    seasonLabel: String? = null,
    isRussianLocale: Boolean = false,
): List<DiscoveryTrendingItem> = items.mapNotNull { item ->
    val title = if (isRussianLocale && !item.russian.isNullOrBlank()) {
        item.russian
    } else {
        item.name
    }
    if (title.isBlank()) return@mapNotNull null
    val coverUrl = item.image?.original?.let { "https://shikimori.one$it" }
        ?: item.image?.preview?.let { "https://shikimori.one$it" }
    val genreList = item.genres?.map { it.russian?.takeIf { r -> r.isNotBlank() } ?: it.name }.orEmpty()

    val altTitle = if (title == item.russian) item.name else item.russian

    DiscoveryTrendingItem(
        title = title,
        cleanTitle = normalizeDiscoveryTitle(title),
        coverUrl = coverUrl,
        anilistId = item.id,
        seasonLabel = seasonLabel,
        genres = genreList,
        provider = "shikimori_trend",
        // V1: сырой статус Shikimori — для пост-фильтра при множественном выборе.
        releaseStatus = item.status,
        altTitle = altTitle,
    )
}

/**
 * V1: стратегия статус-фильтра для Shikimori.
 * Возвращает (статус для сервера, множество статусов для пост-фильтра):
 * - фильтр пуст → серверный статус не меняется (сезонный/отсутствующий), пост-фильтра нет;
 * - выбран РОВНО ОДИН статус → он уходит на сервер (&status=), пост-фильтра нет;
 * - выбрано несколько → сервер не трогаем, режем выдачу по полю status.
 * [seasonStatus] — сезонный статус anime-витрины (ongoing/anons); при пустом
 * фильтре он и остаётся на сервере.
 */
internal fun resolveShikimoriStatusFilter(
    seasonStatus: String?,
    releaseStatuses: Set<DiscoveryReleaseStatus>,
): Pair<String?, Set<String>> {
    if (releaseStatuses.isEmpty()) return seasonStatus to emptySet()
    if (releaseStatuses.size == 1) {
        return releaseStatuses.first().shikimoriValue to emptySet()
    }
    return seasonStatus to releaseStatuses.map { it.shikimoriValue }.toSet()
}

/** V1: пост-фильтр выдачи по сырому статусу Shikimori (пустой статус не режем — best-effort). */
internal fun filterByShikimoriStatus(
    items: List<DiscoveryTrendingItem>,
    shikimoriStatuses: Set<String>,
): List<DiscoveryTrendingItem> {
    if (shikimoriStatuses.isEmpty()) return items
    return items.filter { item ->
        val raw = item.releaseStatus ?: return@filter true // без статуса — не режем
        raw in shikimoriStatuses
    }
}

/**
 * URL списка Shikimori. `censored=true` скрывает hentai/yaoi/yuri (официальный
 * параметр API) — используем для независимого NSFW-фильтра подборок.
 */
internal fun shikimoriListUrl(
    endpoint: String,
    page: Int,
    order: String,
    status: String? = null,
    genreIds: List<Long>? = null,
    censored: Boolean? = null,
    limit: Int = 30,
): String = buildString {
    append("https://shikimori.one/api/$endpoint?limit=$limit&page=$page&order=$order")
    if (status != null) append("&status=$status")
    if (!genreIds.isNullOrEmpty()) append("&genre=${genreIds.joinToString(",")}")
    if (censored != null) append("&censored=$censored")
}

open class ShikimoriTrendingSource(
    private val clientProvider: () -> OkHttpClient = { Injekt.get<NetworkHelper>().client },
    private val jsonProvider: () -> Json = { Injekt.get() },
    private val isRussianLocaleProvider: () -> Boolean = {
        java.util.Locale.getDefault().language == "ru"
    },
    private val nsfwFilterProvider: () -> Boolean = { discoveryNsfwFilterEnabled() },
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
        if (mediaType == DiscoveryMediaType.NOVEL) return emptyList()

        return try {
            when (mediaType) {
                DiscoveryMediaType.ANIME -> fetchAnime(season, sort, page, releaseStatuses)
                DiscoveryMediaType.MANGA -> fetchManga(sort, page, releaseStatuses)
                DiscoveryMediaType.NOVEL -> emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[ShikimoriTrending] fetch FAILED for $mediaType: ${e.message}" }
            throw e
        }
    }

    private suspend fun fetchAnime(
        season: TrendSeason,
        sort: TrendSort,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryTrendingItem> {
        val order = if (sort == TrendSort.SCORE) "ranked" else "popularity"
        // Статус сезона (current=ongoing, next=anons) — позиция сезона по определению.
        // V1: пользовательский статус-фильтр сужает/заменяет сезонный статус: одиночный
        // уходит на сервер (&status=), множественный — пост-фильтр по выдаче (status поля).
        val seasonStatus = when (season) {
            TrendSeason.CURRENT -> "ongoing"
            TrendSeason.NEXT -> "anons"
            TrendSeason.BOTH -> "ongoing"
        }
        val seasonLabel = when (season) {
            TrendSeason.CURRENT -> "current"
            TrendSeason.NEXT -> "next"
            TrendSeason.BOTH -> "current"
        }
        val (serverStatus, clientFilter) = resolveShikimoriStatusFilter(seasonStatus, releaseStatuses)
        val url = shikimoriListUrl(
            endpoint = "animes",
            page = page,
            order = order,
            status = serverStatus,
            censored = nsfwFilterProvider(),
        )
        ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
        val response = clientProvider().newCall(GET(url, headers = headers))
            .awaitSuccess()
            .parseAs<List<ShikimoriMediaItem>>(jsonProvider())
        return parseShikimoriItems(response, seasonLabel, isRussianLocaleProvider())
            .let { filterByShikimoriStatus(it, clientFilter) }
    }

    private suspend fun fetchManga(
        sort: TrendSort,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryTrendingItem> {
        val order = if (sort == TrendSort.SCORE) "ranked" else "popularity"
        val (serverStatus, clientFilter) = resolveShikimoriStatusFilter(null, releaseStatuses)
        val url = shikimoriListUrl(
            endpoint = "mangas",
            page = page,
            order = order,
            status = serverStatus,
            censored = nsfwFilterProvider(),
        )
        ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
        val response = clientProvider().newCall(GET(url, headers = headers))
            .awaitSuccess()
            .parseAs<List<ShikimoriMediaItem>>(jsonProvider())
        return parseShikimoriItems(response, null, isRussianLocaleProvider())
            .let { filterByShikimoriStatus(it, clientFilter) }
    }

    override suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryTrendingItem> {
        if (mediaType == DiscoveryMediaType.NOVEL || genres.isEmpty()) return emptyList()
        return try {
            val endpoint = if (mediaType == DiscoveryMediaType.ANIME) "animes" else "mangas"
            val kind = if (mediaType == DiscoveryMediaType.ANIME) "anime" else "manga"
            val genreIds = mapGenresToIds(genres, genresCatalog(), kind)
            // Нет ids — честный пустой результат (Composite фолбэчится на AniList genre_in),
            // а не popularity-выдача без фильтра под видом «твоего вкуса».
            if (genreIds.isEmpty()) return emptyList()
            val order = if (sort == TrendSort.SCORE) "ranked" else "popularity"
            val (serverStatus, clientFilter) = resolveShikimoriStatusFilter(null, releaseStatuses)
            val url = shikimoriListUrl(
                endpoint = endpoint,
                page = page,
                order = order,
                genreIds = genreIds,
                status = serverStatus,
                censored = nsfwFilterProvider(),
            )
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
            val response = clientProvider().newCall(GET(url, headers = headers))
                .awaitSuccess()
                .parseAs<List<ShikimoriMediaItem>>(jsonProvider())
            parseShikimoriItems(response, null, isRussianLocaleProvider())
                .let { filterByShikimoriStatus(it, clientFilter) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[ShikimoriTrending] fetchByGenres FAILED for $mediaType: ${e.message}" }
            emptyList()
        }
    }

    private suspend fun genresCatalog(): List<ShikimoriGenreDto> {
        val now = System.currentTimeMillis()
        genresCache?.let { (at, cached) -> if (now - at < GENRES_TTL_MS) return cached }
        ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
        val fresh = clientProvider()
            .newCall(GET("https://shikimori.one/api/genres", headers = headers))
            .awaitSuccess()
            .parseAs<List<ShikimoriGenreDto>>(jsonProvider())
        genresCache = now to fresh
        return fresh
    }

    private companion object {
        const val GENRES_TTL_MS = 24 * 60 * 60 * 1000L

        @Volatile
        var genresCache: Pair<Long, List<ShikimoriGenreDto>>? = null
    }

    override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
        if (mediaType == DiscoveryMediaType.NOVEL) return null
        metaCache.get(title)?.let { return it }

        return try {
            val endpoint = if (mediaType == DiscoveryMediaType.ANIME) "animes" else "mangas"
            val searchUrl = "https://shikimori.one/api/$endpoint?search=${URLEncoder.encode(title, "UTF-8")}&limit=5"
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
            val searchResults = clientProvider().newCall(GET(searchUrl, headers = headers))
                .awaitSuccess()
                .parseAs<List<ShikimoriMediaItem>>(jsonProvider())

            val first = searchResults.firstOrNull { metaMatchesTitle(title, listOf(it.name, it.russian)) }
                ?: return null
            val detailUrl = "https://shikimori.one/api/$endpoint/${first.id}"
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
            val detail = clientProvider().newCall(GET(detailUrl, headers = headers))
                .awaitSuccess()
                .parseAs<ShikimoriMediaItem>(jsonProvider())

            val desc = detail.description
                ?.replace(Regex("\\[[^\\]]*]"), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val genreNames = detail.genres?.map {
                if (isRussianLocaleProvider() && !it.russian.isNullOrBlank()) it.russian else it.name
            }.orEmpty()
            val altTitle = if (title == detail.russian) detail.name else detail.russian

            val meta = DiscoveryMeta(
                description = desc,
                genres = genreNames,
                altTitle = altTitle,
            )
            metaCache.put(title, meta)
            meta
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[ShikimoriTrending] fetchMeta FAILED for '$title': ${e.message}" }
            null
        }
    }
}
