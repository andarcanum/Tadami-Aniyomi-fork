package eu.kanade.tachiyomi.data.anixart

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
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
 * Only the first page of each source is taken — for title matching the top
 * results are what matter and we must not hammer sources for hundreds of rows.
 */
class AnixartSourceSearcher(
    private val sourceManager: AnimeSourceManager,
    private val sourceIds: List<Long>,
    private val rateLimiter: AnixartSourceRateLimiter = AnixartSourceRateLimiter(),
    private val maxResultsPerSource: Int = MAX_RESULTS_PER_SOURCE,
    private val sourceTimeoutMs: Long = SOURCE_TIMEOUT_MS,
) : AnixartTitleSearcher {

    override suspend fun search(query: String): List<AnixartMatcher.SearchCandidate> {
        if (query.isBlank()) return emptyList()
        val results = ArrayList<AnixartMatcher.SearchCandidate>()
        for (sourceId in sourceIds) {
            val source = sourceManager.get(sourceId) as? AnimeCatalogueSource ?: continue
            val page = try {
                rateLimiter.withRateLimit(sourceId) {
                    withTimeout(sourceTimeoutMs) {
                        source.getSearchAnime(1, query, AnimeFilterList())
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Anixart search failed on source $sourceId for '$query'" }
                continue
            }
            for (sAnime in page.animes.take(maxResultsPerSource)) {
                val titles = buildList {
                    add(sAnime.title)
                }.filter { it.isNotBlank() }
                results += AnixartMatcher.SearchCandidate(
                    id = (sourceId.toString() + sAnime.url).hashCode().toLong(),
                    sourceId = sourceId,
                    displayTitle = sAnime.title,
                    titles = titles,
                    url = sAnime.url,
                    thumbnailUrl = sAnime.thumbnail_url,
                )
            }
        }
        return AnixartMatchingCoordinator.dedupCandidates(results)
    }

    companion object {
        const val MAX_RESULTS_PER_SOURCE = 10
        const val SOURCE_TIMEOUT_MS = 8_000L
    }
}
