package eu.kanade.tachiyomi.data.suggestions.manga

import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.ExtensionInterop
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MangaSearchFallbackEngine {

    private companion object {
        const val TAG = "MangaSearchFallbackEngine"

        /** Max parallel search requests against a single source. */
        const val MAX_CONCURRENT_QUERIES = 3

        /** Max extra detail requests per fetch, used only to backfill missing thumbnails. */
        const val MAX_THUMBNAIL_DETAIL_LOOKUPS = 6
    }

    suspend fun fetchSearchFallback(
        manga: Manga,
        source: CatalogueSource,
        seed: SuggestionSeed,
        maxResults: Int = 40,
        onProgress: ((List<SuggestionItem>) -> Unit)? = null,
    ): MangaFallbackOutcome {
        val boundedMaxResults = maxResults.coerceIn(1, 100)
        val cacheKey = SuggestionCache.makeKey(
            "search:${source.id}:limit:$boundedMaxResults",
            manga.url,
            "MANGA",
            seed.candidateTitles,
        )
        val cached = SuggestionCache.get(cacheKey)
        if (cached != null) {
            logcat { "[MangaSearchFallbackEngine] Cache HIT for key $cacheKey, count=${cached.size}" }
            return if (cached.isEmpty()) {
                MangaFallbackOutcome.Empty(MangaFallbackReason.SEARCH_EMPTY)
            } else {
                MangaFallbackOutcome.Success(cached)
            }
        }

        logcat { "[MangaSearchFallbackEngine] Cache MISS. Running tiered search fallback for '${manga.title}'" }

        val rawAuthorParts = buildList {
            val author = manga.author
            val garbage = setOf(
                "null", "undefined", "unknown", "none", "no author", "n/a",
                "нет", "неизвестен", "неизвестный", "неизвестно",
            )
            if (!author.isNullOrBlank()) {
                addAll(
                    author.split(Regex("[,;/&]"))
                        .map { it.trim() }
                        .filter { it.length >= 2 && it.lowercase() !in garbage },
                )
            }
            val artist = manga.artist
            if (!artist.isNullOrBlank() && artist != author) {
                addAll(
                    artist.split(Regex("[,;/&]"))
                        .map { it.trim() }
                        .filter { it.length >= 2 && it.lowercase() !in garbage },
                )
            }
        }.distinct()

        val authorParts = rawAuthorParts

        val rawGenreParts = buildList {
            val genres = manga.genre
            if (!genres.isNullOrEmpty()) {
                addAll(genres.take(3).map { it.trim() }.filter { it.length >= 2 })
            }
        }.distinct()

        val genreParts = buildList {
            rawGenreParts.forEach { genre ->
                add(genre)
                addAll(eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.getGenreTranslations(genre))
            }
        }.distinct()

        val mainTitle = seed.primaryTitle
        val titlesToProcess = listOf(mainTitle)
        val isCyrillicEntry = eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.containsCyrillic(mainTitle)

        // Tier 1: Exact titles
        val tier1Queries = buildList {
            addAll(titlesToProcess)
            eu.kanade.domain.metadata.interactor.parseOriginalTitle(manga.description)?.let { add(it) }
            addAll(seed.candidateTitles)
        }.map { it.trim() }
            .filter { it.length >= 2 }
            .filter {
                !isCyrillicEntry ||
                    eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.containsCyrillic(it)
            }
            .distinct()

        // Tier 2: Relaxed title queries (e.g. remove volume/season suffixes, split by punctuation, or truncate long titles)
        val tier2Queries = buildList {
            titlesToProcess.forEach { title ->
                // 1. Split by common separators: :, -, (, [, comma, semicolon
                val separators = listOf(":", "-", "(", "[", ",", ";")
                separators.forEach { sep ->
                    val part = title.substringBefore(sep).trim()
                    if (part.isNotEmpty() && part != title && part.length >= 3) {
                        add(part)
                    }
                }

                // 2. Cleaned title (removes volumes, chapters, seasons)
                val cleaned = eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver.cleanTitle(title)
                if (cleaned.isNotEmpty() && cleaned != title && cleaned.length >= 3) {
                    add(cleaned)
                }

                // 3. For long titles, emit a single 4-word prefix. The previous
                //    3/4/5-word variants tripled the request count while returning
                //    nearly identical result sets.
                val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.size > 4) {
                    add(words.take(4).joinToString(" "))
                }
            }
        }.map { it.trim() }
            .filter { it.length >= 2 }
            .filter {
                !isCyrillicEntry ||
                    eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.containsCyrillic(it)
            }
            .distinct()

        // Tier 3: Author queries
        val tier3Queries = authorParts.map { it.trim() }.filter { it.length >= 2 }.distinct()

        // Tier 4: Genre queries
        val tier4Queries = genreParts.map { it.trim() }.filter { it.length >= 2 }.distinct()

        val queryTiers = listOf(
            Pair("Tier 1 (Exact Title)", tier1Queries),
            Pair("Tier 2 (Relaxed Title)", tier2Queries),
            Pair("Tier 3 (Author)", tier3Queries),
            Pair("Tier 4 (Genre)", tier4Queries),
        )

        val candidatesToScore = seed.candidateTitles.distinct()

        val uniqueResults = LinkedHashMap<String, SuggestionItem>() // key: providerUrl
        // Set when the extension exhausts the heap while handling a query. The buffers it
        // allocated become garbage when its stack unwinds, but the heap is fragile at that
        // point: issuing more source requests would risk a second, fatal OOM.
        val oomAborted = AtomicBoolean(false)
        // getFilterList() executes extension code: it is exactly where a source built
        // against a different library ABI blows up with a LinkageError (e.g.
        // NoSuchMethodError: BuildersKt.runBlockingK). Degrade to an empty filter
        // list instead of crashing the app.
        val filterList = ExtensionInterop.runInterop(TAG, "getFilterList") { source.getFilterList() }
            ?: FilterList()
        val searchSemaphore = Semaphore(MAX_CONCURRENT_QUERIES)
        val thumbnailDetailBudget = AtomicInteger(MAX_THUMBNAIL_DETAIL_LOOKUPS)
        var authorAdded = 0
        var genreAdded = 0
        val maxAuthor = 8
        val maxGenre = 8

        logcat {
            "[MangaSearchFallbackEngine] Starting suggestions search for '${manga.title}' (url: ${manga.url}). Candidates: ${seed.candidateTitles}, author: '${manga.author}', genres: ${manga.genre}"
        }

        for ((tierName, tierQueries) in queryTiers) {
            if (oomAborted.get()) {
                logcat(LogPriority.WARN) {
                    "[MangaSearchFallbackEngine] Skipping $tierName after the source ran out of memory. Returning collected results."
                }
                break
            }
            if (synchronized(uniqueResults) { uniqueResults.size >= boundedMaxResults }) {
                logcat {
                    "[MangaSearchFallbackEngine] Reached target results limit ($boundedMaxResults) before processing all tiers. Stopping early."
                }
                break
            }
            if (tierQueries.isEmpty()) continue
            logcat { "[MangaSearchFallbackEngine] Processing $tierName with queries: $tierQueries" }

            coroutineScope {
                tierQueries.forEach { query ->
                    launch {
                        if (oomAborted.get()) return@launch
                        if (synchronized(uniqueResults) { uniqueResults.size >= boundedMaxResults }) return@launch
                        try {
                            logcat { "[MangaSearchFallbackEngine] Searching for query: '$query'" }
                            val page = searchSemaphore.withPermit {
                                source.getSearchManga(1, query, filterList)
                            }
                            if (page.mangas.isEmpty()) {
                                logcat {
                                    "[MangaSearchFallbackEngine] Query '$query' returned 0 results from source '${source.name}'"
                                }
                                return@launch
                            } else {
                                logcat {
                                    "[MangaSearchFallbackEngine] Query '$query' returned ${page.mangas.size} raw results from source '${source.name}'"
                                }
                            }

                            val isAuthorQuery = authorParts.any { it.equals(query, ignoreCase = true) }
                            val isGenreQuery = genreParts.any { it.equals(query, ignoreCase = true) }
                            val isTitleQuery = !isAuthorQuery && !isGenreQuery

                            val scoredItems = page.mangas.mapNotNull { sManga ->
                                if (sManga.url == manga.url) {
                                    logcat { "[MangaSearchFallbackEngine] Excluding self reference: '${sManga.title}'" }
                                    return@mapNotNull null
                                }

                                if (SuggestionTitleResolver.isFranchiseDuplicate(sManga.title, manga.title)) {
                                    logcat {
                                        "[MangaSearchFallbackEngine] Excluding franchise duplicate: '${sManga.title}' against '${manga.title}'"
                                    }
                                    return@mapNotNull null
                                }

                                if (synchronized(uniqueResults) { uniqueResults.containsKey(sManga.url) }) {
                                    return@mapNotNull null
                                }

                                val bestScore = candidatesToScore.maxOfOrNull { candidate ->
                                    SuggestionTitleResolver.scoreMatch(candidate, sManga.title)
                                } ?: 0

                                val finalScore = when {
                                    bestScore >= 30 -> bestScore
                                    isTitleQuery -> 0
                                    isAuthorQuery -> {
                                        val overlapBonus = minOf(bestScore / 10, 10)
                                        40 + overlapBonus
                                    }
                                    isGenreQuery -> 30
                                    else -> 0
                                }

                                logcat {
                                    "[MangaSearchFallbackEngine] '${sManga.title}' score=$finalScore " +
                                        "(bestScore=$bestScore, isTitleQuery=$isTitleQuery, isAuthorQuery=$isAuthorQuery, isGenreQuery=$isGenreQuery)"
                                }

                                if (finalScore >= 30) {
                                    val itemReason = when {
                                        isAuthorQuery -> SuggestionReason.SEARCH_AUTHOR
                                        isGenreQuery -> SuggestionReason.SEARCH_GENRE
                                        else -> SuggestionReason.SEARCH_TITLE
                                    }
                                    val item = SuggestionItem(
                                        title = sManga.title,
                                        searchQueries = listOf(sManga.title),
                                        thumbnailUrl = resolveThumbnail(source, sManga, thumbnailDetailBudget),
                                        providerName = source.name,
                                        reason = itemReason,
                                        providerUrl = sManga.url,
                                        providerId = "${source.id}:${sManga.url}",
                                        mediaType = SuggestionMediaType.MANGA,
                                    )
                                    Pair(item, finalScore)
                                } else {
                                    logcat {
                                        "[MangaSearchFallbackEngine] Rejecting '${sManga.title}': score $finalScore below threshold (30)"
                                    }
                                    null
                                }
                            }

                            var addedAny = false
                            val currentProgress = synchronized(uniqueResults) {
                                if (isGenreQuery && genreAdded >= maxGenre) return@launch
                                if (isAuthorQuery && authorAdded >= maxAuthor) return@launch
                                scoredItems.sortedByDescending { it.second }.forEach { (item, _) ->
                                    if (!uniqueResults.containsKey(item.providerUrl) &&
                                        uniqueResults.size < boundedMaxResults
                                    ) {
                                        if ((isGenreQuery && genreAdded >= maxGenre) ||
                                            (isAuthorQuery && authorAdded >= maxAuthor)
                                        ) {
                                            return@forEach
                                        }
                                        uniqueResults[item.providerUrl] = item
                                        addedAny = true
                                        if (isGenreQuery) genreAdded++
                                        if (isAuthorQuery) authorAdded++
                                    }
                                }
                                if (addedAny) {
                                    uniqueResults.values.toList()
                                } else {
                                    null
                                }
                            }
                            if (currentProgress != null) {
                                onProgress?.invoke(currentProgress)
                            }
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                            throw e
                        } catch (e: LinkageError) {
                            logcat {
                                "[MangaSearchFallbackEngine] Incompatible extension ABI for query '$query': ${e.message}"
                            }
                        } catch (e: OutOfMemoryError) {
                            // Extensions allocate inside getSearchManga (in-memory catalog indexes,
                            // multi-MB JSON decoding). OOM is an Error, so the Exception catch below
                            // never sees it and the whole process dies, as in the 0.62.9 crash logs.
                            oomAborted.set(true)
                            logcat(LogPriority.WARN) {
                                "[MangaSearchFallbackEngine] Source '${source.name}' exhausted the heap for query " +
                                    "'$query': ${e.message}. Stopping further search tiers."
                            }
                        } catch (e: Exception) {
                            logcat { "[MangaSearchFallbackEngine] Search failed for query '$query': ${e.message}" }
                        }
                    }
                }
            }
        }

        val items = uniqueResults.values.toList()
        if (items.isEmpty()) {
            logcat {
                "[MangaSearchFallbackEngine] Total 0 similar items found for manga '${manga.title}'. Check source connectivity or query matching strictness."
            }
        } else {
            logcat {
                "[MangaSearchFallbackEngine] Fallback finished, found ${items.size} matching items: ${items.map {
                    it.title
                }}"
            }
        }
        // An out-of-memory run is incomplete: caching it would hide the source behind a
        // 24h negative entry instead of letting the next visit retry.
        if (!oomAborted.get()) {
            SuggestionCache.put(cacheKey, items)
        }

        return if (items.isEmpty()) {
            MangaFallbackOutcome.Empty(MangaFallbackReason.SEARCH_EMPTY)
        } else {
            MangaFallbackOutcome.Success(items)
        }
    }

    /**
     * Resolves a thumbnail for a search result. A full details request is issued
     * only while [detailBudget] allows it, so sources that omit thumbnails in
     * search results can no longer trigger an unbounded "one details request per
     * result" burst.
     */
    private suspend fun resolveThumbnail(
        source: CatalogueSource,
        manga: eu.kanade.tachiyomi.source.model.SManga,
        detailBudget: AtomicInteger,
    ): String? {
        manga.thumbnail_url?.takeIf { it.isNotBlank() }?.let { return it }
        if (detailBudget.getAndDecrement() <= 0) return null
        return ExtensionInterop.runInterop(TAG, "getMangaDetails(thumbnail)") {
            source.getMangaDetails(manga.copy()).thumbnail_url?.takeIf { it.isNotBlank() }
        }
    }
}
