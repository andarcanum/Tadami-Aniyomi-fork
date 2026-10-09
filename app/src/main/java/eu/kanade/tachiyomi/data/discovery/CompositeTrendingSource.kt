package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CompositeTrendingSource(
    private val shikimori: DiscoveryTrendingSource = ShikimoriTrendingSource(),
    private val mangadex: DiscoveryTrendingSource = MangaDexTrendingSource(),
    private val jikan: DiscoveryTrendingSource = JikanTrendingSource(),
    private val anilist: DiscoveryTrendingSource = AniListTrendingSource(),
    private val isRussianLocaleProvider: () -> Boolean = {
        java.util.Locale.getDefault().language.equals("ru", ignoreCase = true)
    },
    private val translationServiceProvider: () -> GoogleTranslationService? = {
        try {
            Injekt.get<GoogleTranslationService>()
        } catch (_: Throwable) {
            null
        }
    },
) : DiscoveryTrendingSource {

    override suspend fun fetch(
        mediaType: DiscoveryMediaType,
        season: TrendSeason,
        sort: TrendSort,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryTrendingItem> {
        val providers: List<Pair<String, suspend () -> List<DiscoveryTrendingItem>>> = when (mediaType) {
            DiscoveryMediaType.ANIME -> listOf(
                "shikimori" to suspend { shikimori.fetch(mediaType, season, sort, page, releaseStatuses) },
                "jikan" to suspend { jikan.fetch(mediaType, season, sort, page, releaseStatuses) },
                "anilist" to suspend { anilist.fetch(mediaType, season, sort, page, releaseStatuses) },
            )
            DiscoveryMediaType.MANGA -> listOf(
                "mangadex" to suspend { mangadex.fetch(mediaType, season, sort, page, releaseStatuses) },
                "shikimori" to suspend { shikimori.fetch(mediaType, season, sort, page, releaseStatuses) },
                "anilist" to suspend { anilist.fetch(mediaType, season, sort, page, releaseStatuses) },
            )
            DiscoveryMediaType.NOVEL -> listOf(
                "anilist" to suspend { anilist.fetch(mediaType, season, sort, page, releaseStatuses) },
            )
        }
        val merged = fetchMerged(providers.take(2), mediaType)
        if (merged.isNotEmpty()) return merged
        return fetchWithFallback(providers.drop(2), mediaType)
    }

    /** Мерж топ-2 провайдеров: primary-first, дедуп по cleanTitle, кап 50; ошибки — пустой вклад. */
    private suspend fun fetchMerged(
        providers: List<Pair<String, suspend () -> List<DiscoveryTrendingItem>>>,
        mediaType: DiscoveryMediaType,
    ): List<DiscoveryTrendingItem> = coroutineScope {
        val jobs = providers.map { (name, action) ->
            async {
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat {
                        "[CompositeTrending] failed: provider=" + name + " media=" + mediaType + " error=" +
                            e.message
                    }
                    emptyList()
                }
            }
        }
        val results = jobs.awaitAll()
        val seen = mutableSetOf<String>()
        results.flatten().filter { it.cleanTitle.isNotBlank() && seen.add(it.cleanTitle) }.take(50)
    }

    private suspend fun fetchWithFallback(
        providers: List<Pair<String, suspend () -> List<DiscoveryTrendingItem>>>,
        mediaType: DiscoveryMediaType,
    ): List<DiscoveryTrendingItem> {
        for ((name, action) in providers) {
            try {
                val results = action()
                if (results.isNotEmpty()) {
                    logcat { "[CompositeTrending] $mediaType: provider '$name' returned ${results.size} items" }
                    return results
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat { "[CompositeTrending] $mediaType: provider '$name' failed: ${e.message}" }
            }
        }
        return emptyList()
    }

    override suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryTrendingItem> {
        val providers: List<Pair<String, suspend () -> List<DiscoveryTrendingItem>>> = when (mediaType) {
            DiscoveryMediaType.ANIME -> listOf(
                "shikimori" to suspend { shikimori.fetchByGenres(mediaType, genres, sort, page, releaseStatuses) },
                "anilist" to suspend { anilist.fetchByGenres(mediaType, genres, sort, page, releaseStatuses) },
            )
            // Жанровой цепочкой MangaDex не владеет (fetchByGenres игнорировал жанры и
            // возвращал популярность) — только реально фильтрующие провайдеры.
            DiscoveryMediaType.MANGA -> listOf(
                "shikimori" to suspend { shikimori.fetchByGenres(mediaType, genres, sort, page, releaseStatuses) },
                "anilist" to suspend { anilist.fetchByGenres(mediaType, genres, sort, page, releaseStatuses) },
            )
            DiscoveryMediaType.NOVEL -> listOf(
                "anilist" to suspend { anilist.fetchByGenres(mediaType, genres, sort, page, releaseStatuses) },
            )
        }
        return fetchWithFallback(providers, mediaType)
    }

    override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
        val cacheKey = "${mediaType.key}:${normalizeDiscoveryTitle(title)}"
        globalMetaCache.get(cacheKey)?.let { return it }

        val isRu = isRussianLocaleProvider()
        val providers: List<Pair<String, suspend () -> DiscoveryMeta?>> = when (mediaType) {
            DiscoveryMediaType.ANIME -> listOf(
                "shikimori" to suspend { shikimori.fetchMeta(title, mediaType) },
                "anilist" to suspend { anilist.fetchMeta(title, mediaType) },
                "jikan" to suspend { jikan.fetchMeta(title, mediaType) },
            )
            DiscoveryMediaType.MANGA -> if (isRu) {
                listOf(
                    "shikimori" to suspend { shikimori.fetchMeta(title, mediaType) },
                    "mangadex" to suspend { mangadex.fetchMeta(title, mediaType) },
                    "anilist" to suspend { anilist.fetchMeta(title, mediaType) },
                )
            } else {
                listOf(
                    "mangadex" to suspend { mangadex.fetchMeta(title, mediaType) },
                    "shikimori" to suspend { shikimori.fetchMeta(title, mediaType) },
                    "anilist" to suspend { anilist.fetchMeta(title, mediaType) },
                )
            }
            // Shikimori для NOVEL всегда null (нет ranobe в meta-поиске) — не тратим вызов.
            DiscoveryMediaType.NOVEL -> listOf(
                "anilist" to suspend { anilist.fetchMeta(title, mediaType) },
            )
        }
        for ((name, action) in providers) {
            try {
                val meta = action()
                if (meta != null && (!meta.description.isNullOrBlank() || meta.genres.isNotEmpty())) {
                    val resolvedMeta = if (isRu && !meta.description.isNullOrBlank() &&
                        !hasCyrillic(meta.description)
                    ) {
                        val translatedDesc = runCatching {
                            translationServiceProvider()?.translateSingle(meta.description, "auto", "ru")
                        }.getOrNull()
                        if (!translatedDesc.isNullOrBlank()) {
                            meta.copy(description = translatedDesc)
                        } else {
                            meta
                        }
                    } else {
                        meta
                    }
                    globalMetaCache.put(cacheKey, resolvedMeta)
                    return resolvedMeta
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat { "[CompositeTrending] fetchMeta '$title' ($mediaType) failed on '$name': ${e.message}" }
            }
        }
        return null
    }

    private fun hasCyrillic(text: String): Boolean = cyrillicRegex.containsMatchIn(text)

    companion object {
        private val globalMetaCache = DiscoveryLruCache<String, DiscoveryMeta>(100)
        private val cyrillicRegex = Regex("[а-яА-ЯёЁ]")

        fun getCachedMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
            val cacheKey = "${mediaType.key}:${normalizeDiscoveryTitle(title)}"
            return globalMetaCache.get(cacheKey)
        }
    }
}
