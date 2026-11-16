package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus

/**
 * Общий интерфейс провайдера трендовых тайтлов, сезонных подборок
 * и метаданных для экрана «Для тебя» и превью-шторки.
 *
 * [releaseStatuses] (V1): пусто — без фильтра; провайдеры применяют статус
 * серверно (AniList `status_in`, Shikimori `&status=` при одиночном статусе,
 * иначе пост-фильтр по выдаче).
 */
interface DiscoveryTrendingSource {

    suspend fun fetch(
        mediaType: DiscoveryMediaType,
        season: TrendSeason = TrendSeason.CURRENT,
        sort: TrendSort = TrendSort.POPULARITY,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryTrendingItem>

    suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort = TrendSort.POPULARITY,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryTrendingItem>

    suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort = TrendSort.POPULARITY,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
        matchMode: RequiredMatchMode,
    ): List<DiscoveryTrendingItem> = fetchByGenres(mediaType, genres, sort, page, releaseStatuses)

    suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta?
}

internal data class DiscoveryMetadataCacheKey(
    val mediaType: DiscoveryMediaType,
    val title: String,
    val filterNsfw: Boolean,
)

/**
 * fetchMeta ищет тайтл по имени и раньше брал ПЕРВЫЙ результат поиска — при
 * коллизиях названий в превью попадал чужой синопсис. Проверяем совпадение:
 * scoreMatch ≥ [META_MATCH_THRESHOLD] (exact/prefix/contains) по любому из
 * вариантов названия. Нет совпадения → меты нет (лучше пусто, чем чужое).
 */
internal const val META_MATCH_THRESHOLD = 50

internal fun metaMatchesTitle(queryTitle: String, candidateTitles: List<String?>): Boolean =
    candidateTitles.any { candidate ->
        !candidate.isNullOrBlank() &&
            SuggestionTitleResolver.scoreMatch(queryTitle, candidate) >= META_MATCH_THRESHOLD
    }
