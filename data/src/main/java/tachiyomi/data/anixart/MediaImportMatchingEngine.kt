package tachiyomi.data.anixart

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared catalogue matching loop for Anixart and Shikimori import wizards.
 */
object MediaImportMatchingEngine {

    const val DEFAULT_CONCURRENCY = 2
    const val LARGE_IMPORT_THRESHOLD = 100

    data class RowInput(
        val candidateTitles: List<String>,
        val searchQueries: List<String>,
    )

    data class RowResult<R>(
        val row: R,
        val result: AnixartMatcher.MatchResult,
        val matchedQuery: String?,
        val matchedSourceName: String?,
    )

    suspend fun <R> matchRows(
        rows: List<R>,
        toInput: (R) -> RowInput,
        search: suspend (String) -> List<AnixartMatcher.SearchCandidate>,
        sourceNames: Map<Long, String>,
        concurrency: Int = DEFAULT_CONCURRENCY,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Pair<List<RowResult<R>>, AnixartMatchingCoordinator.MatchingReport> {
        if (rows.isEmpty()) {
            return emptyList<RowResult<R>>() to AnixartMatchingCoordinator.MatchingReport(0, 0, 0, 0)
        }

        return coroutineScope {
            // Deferred-based cache: concurrent rows that share a query await a
            // single in-flight search instead of firing duplicate requests
            // (a plain getOrPut with a suspend body is not atomic).
            val searchCache = ConcurrentHashMap<String, Deferred<List<AnixartMatcher.SearchCandidate>>>()
            val semaphore = Semaphore(concurrency)
            val matchedCount = AtomicInteger(0)
            val total = rows.size

            suspend fun cachedSearch(query: String) =
                searchCache.computeIfAbsent(query) {
                    async(start = CoroutineStart.LAZY) { search(query) }
                }.await()

            val results = rows.map { row ->
                async {
                    semaphore.withPermit {
                        val input = toInput(row)
                        val rowMatch = AnixartMatchingCoordinator.matchTitles(
                            candidateTitles = input.candidateTitles,
                            searchQueries = input.searchQueries,
                            search = { cachedSearch(it) },
                        )
                        val current = matchedCount.incrementAndGet()
                        onProgress(current, total)
                        val sourceName = rowMatch.result.best?.candidate?.sourceId?.let { sourceNames[it] }
                        RowResult(
                            row = row,
                            result = rowMatch.result,
                            matchedQuery = rowMatch.matchedQuery,
                            matchedSourceName = sourceName,
                        )
                    }
                }
            }.awaitAll()

            val report = AnixartMatchingCoordinator.summarize(
                results.map { AnixartMatchingCoordinator.RowMatch(it.result, it.matchedQuery) },
            )
            results to report
        }
    }
}
