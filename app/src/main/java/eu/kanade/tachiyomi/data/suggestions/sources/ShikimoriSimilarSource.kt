package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.data.discovery.ExternalApiThrottle
import eu.kanade.tachiyomi.data.discovery.ShikimoriMediaItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

/**
 * Похожие тайтлы Shikimori: GET /api/{animes|mangas|ranobe}/:id/similar.
 *
 * Единственный внешний провайдер с первоклассными лайт-новеллами (ranobe) —
 * закрывает дыру novel-рекомендаций и заменяет хрупкий HTML-скрейпинг
 * NovelUpdates (Cloudflare 403, ToS-риск). Лимиты: 5 rps / 90 rpm
 * ([ExternalApiThrottle]), описательный User-Agent обязателен.
 */
class ShikimoriSimilarSource(
    override val mediaType: SuggestionMediaType,
    private val isRussianLocaleProvider: () -> Boolean = {
        java.util.Locale.getDefault().language.equals("ru", ignoreCase = true)
    },
    private val nsfwFilterProvider: () -> Boolean = {
        eu.kanade.tachiyomi.data.discovery.discoveryNsfwFilterEnabled()
    },
) : RecommendationPagingSource() {

    override val name: String = "Shikimori"

    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json by lazy { Injekt.get<Json>() }

    private val headers = Headers.Builder()
        .add("User-Agent", "Tadami/1.0 (Android; discovery-suggestions)")
        .build()

    private val endpoint = when (mediaType) {
        SuggestionMediaType.ANIME -> "animes"
        SuggestionMediaType.MANGA -> "mangas"
        SuggestionMediaType.NOVEL -> "ranobe"
    }

    override suspend fun fetchSuggestions(seed: SuggestionSeed): List<SuggestionItem> = coroutineScope {
        val cacheKey = SuggestionCache.makeKey(
            name,
            seed.primaryTitle,
            mediaType.name,
            seed.candidateTitles,
            seed.description,
            seed.author,
        )
        SuggestionCache.get(cacheKey)?.let {
            logcat { "[ShikimoriSimilar] CACHE HIT for '${seed.primaryTitle}'" }
            return@coroutineScope it
        }
        logcat { "[ShikimoriSimilar] START '${seed.primaryTitle}', candidates=${seed.candidateTitles}" }

        val suggestions = try {
            val best = findBestMatch(seed.candidateTitles)
            if (best == null) {
                logcat { "[ShikimoriSimilar] no base match for '${seed.primaryTitle}'" }
                return@coroutineScope emptyList()
            }
            matchedBase = true
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
            val response = client.newCall(
                GET("https://shikimori.one/api/$endpoint/${best.id}/similar", headers = headers),
            )
                .awaitSuccess()
                .parseAs<List<ShikimoriMediaItem>>(json)
            val isRu = isRussianLocaleProvider()
            val visible = if (nsfwFilterProvider()) filterCensored(endpoint, response) else response
            visible.take(12).mapNotNull { shikimoriSimilarToSuggestion(it, mediaType, isRu) }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[ShikimoriSimilar] ERROR for '${seed.primaryTitle}': ${e.message}" }
            emptyList()
        }

        logcat { "[ShikimoriSimilar] END '${seed.primaryTitle}': ${suggestions.size} suggestions" }
        if (suggestions.isNotEmpty()) {
            SuggestionCache.put(cacheKey, suggestions)
        }
        suggestions
    }

    /**
     * NSFW-постфильтр similar-выдачи: список-эндпоинт с `censored=true&ids=…`
     * возвращает только цензурные тайтлы. Fail-closed: если запрос фильтра
     * упал — взрослая выдача не показывается вовсе.
     */
    private suspend fun filterCensored(
        endpoint: String,
        items: List<ShikimoriMediaItem>,
    ): List<ShikimoriMediaItem> {
        if (items.isEmpty()) return items
        return try {
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
            val ids = items.joinToString(",") { it.id.toString() }
            val allowed = client.newCall(
                GET("https://shikimori.one/api/$endpoint?ids=$ids&limit=50&censored=true", headers = headers),
            )
                .awaitSuccess()
                .parseAs<List<ShikimoriMediaItem>>(json)
            val allowedIds = allowed.mapTo(HashSet()) { it.id }
            items.filter { it.id in allowedIds }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[ShikimoriSimilar] censored filter FAILED: ${e.message}" }
            emptyList()
        }
    }

    private suspend fun findBestMatch(candidates: List<String>): ShikimoriMediaItem? {
        val results = mutableListOf<ShikimoriMediaItem>()
        for (candidate in candidates.take(3)) {
            try {
                ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
                val url = "https://shikimori.one/api/$endpoint" +
                    "?search=${URLEncoder.encode(candidate, "UTF-8")}&limit=5"
                val page = client.newCall(GET(url, headers = headers))
                    .awaitSuccess()
                    .parseAs<List<ShikimoriMediaItem>>(json)
                results += page
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat { "[ShikimoriSimilar] search failed for '$candidate': ${e.message}" }
            }
        }
        return selectShikimoriBestMatch(results.distinctBy { it.id }, candidates)
    }
}

/** Лучший базовый тайтл из поиска Shikimori: максимум scoreMatch по name/russian, порог > 0. */
internal fun selectShikimoriBestMatch(
    results: List<ShikimoriMediaItem>,
    candidates: List<String>,
): ShikimoriMediaItem? = results
    .mapNotNull { item ->
        val score = candidates.maxOfOrNull { candidate ->
            maxOf(
                SuggestionTitleResolver.scoreMatch(candidate, item.name),
                item.russian?.let { SuggestionTitleResolver.scoreMatch(candidate, it) } ?: 0,
            )
        } ?: 0
        if (score > 0) item to score else null
    }
    .maxByOrNull { it.second }
    ?.first

internal fun shikimoriSimilarToSuggestion(
    item: ShikimoriMediaItem,
    mediaType: SuggestionMediaType,
    isRussianLocale: Boolean,
): SuggestionItem? {
    val title = if (isRussianLocale && !item.russian.isNullOrBlank()) item.russian else item.name
    if (title.isBlank()) return null
    val cover = item.image?.original ?: item.image?.preview
    return SuggestionItem(
        title = title,
        searchQueries = listOf(title),
        thumbnailUrl = cover?.let { "https://shikimori.one$it" },
        providerName = "Shikimori",
        providerUrl = item.url?.let { "https://shikimori.one$it" } ?: "",
        providerId = null,
        mediaType = mediaType,
        reason = SuggestionReason.EXTERNAL_SHIKIMORI,
        // Статус выпуска («ongoing»/«released»/…) — для строгого пост-фильтра ленты.
        releaseStatus = item.status,
        genres = item.genres?.mapNotNull { it.russian ?: it.name.takeIf(String::isNotBlank) }.orEmpty(),
    )
}
