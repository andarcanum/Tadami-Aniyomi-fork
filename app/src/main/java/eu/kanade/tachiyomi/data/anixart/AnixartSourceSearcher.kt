package eu.kanade.tachiyomi.data.anixart

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.data.shikimori.CatalogueExtensionSearch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartMatchingCoordinator
import tachiyomi.data.anixart.AnixartTitleSearcher
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * [AnixartTitleSearcher] implementation backed by installed anime sources.
 *
 * Anixart exports have no source/url, so we run the title query against a
 * user-selected set of catalogue sources and turn each [eu.kanade.tachiyomi.animesource.model.SAnime]
 * into a [AnixartMatcher.SearchCandidate] for the pure matcher to score.
 *
 * Sources are queried in parallel (each source is still individually rate
 * limited by [AnixartSourceRateLimiter]), so one slow source does not stall
 * the others. Only the first page of each source is taken — for title
 * matching the top results are what matter and we must not hammer sources
 * for hundreds of rows.
 */
class AnixartSourceSearcher(
    private val sourceManager: AnimeSourceManager,
    private val sourceIds: List<Long>,
    private val rateLimiter: AnixartSourceRateLimiter = AnixartSourceRateLimiter(),
    private val maxResultsPerSource: Int = MAX_RESULTS_PER_SOURCE,
    private val sourceTimeoutMs: Long = CatalogueExtensionSearch.SOURCE_TIMEOUT_MS,
) : AnixartTitleSearcher {

    override suspend fun search(query: String): List<AnixartMatcher.SearchCandidate> {
        if (query.isBlank()) return emptyList()
        val results = coroutineScope {
            sourceIds
                .map { sourceId -> async { searchOnSource(sourceId, query) } }
                .awaitAll()
                .flatten()
        }
        return AnixartMatchingCoordinator.dedupCandidates(results)
    }

    private suspend fun searchOnSource(
        sourceId: Long,
        query: String,
    ): List<AnixartMatcher.SearchCandidate> {
        val source = sourceManager.get(sourceId) as? AnimeCatalogueSource ?: return emptyList()
        val page = try {
            rateLimiter.withRateLimit(sourceId) {
                CatalogueExtensionSearch.onExtensionThread {
                    withTimeout(sourceTimeoutMs) {
                        val filters = CatalogueExtensionSearch.safeAnimeFilterList { source.getFilterList() }
                        source.getSearchAnime(1, query, filters)
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Anixart search failed on source $sourceId for '$query'" }
            return emptyList()
        }
        return page.animes.take(maxResultsPerSource).map { sAnime ->
            AnixartMatcher.SearchCandidate(
                id = (sourceId.toString() + sAnime.url).hashCode().toLong(),
                sourceId = sourceId,
                displayTitle = sAnime.title,
                titles = listOf(sAnime.title).filter { it.isNotBlank() },
                url = sAnime.url,
                thumbnailUrl = sAnime.thumbnail_url,
            )
        }
    }

    companion object {
        const val MAX_RESULTS_PER_SOURCE = 10
    }
}
