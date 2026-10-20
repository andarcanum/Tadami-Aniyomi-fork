package eu.kanade.tachiyomi.data.suggestions.anime

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.ExtensionInterop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AnimeSearchFallbackEngine {

    private companion object {
        const val TAG = "AnimeSearchFallbackEngine"

        /** Max parallel search requests against a single source. */
        const val MAX_CONCURRENT_QUERIES = 3

        /** Max extra detail requests per fetch, used only to backfill missing thumbnails. */
        const val MAX_THUMBNAIL_DETAIL_LOOKUPS = 6
    }

    suspend fun fetchSearchFallback(
        anime: Anime,
        source: AnimeCatalogueSource,
        seed: SuggestionSeed,
        maxResults: Int = 40,
        onProgress: ((List<SuggestionItem>) -> Unit)? = null,
    ): AnimeFallbackOutcome {
        val boundedMaxResults = maxResults.coerceIn(1, 100)
        val cacheKey = SuggestionCache.makeKey(
            "search:${source.id}:limit:$boundedMaxResults",
            anime.url,
            "ANIME",
            seed.candidateTitles,
        )
        val cached = SuggestionCache.get(cacheKey)
        if (cached != null) {
            logcat { "[AnimeSearchFallbackEngine] Cache HIT for key $cacheKey, count=${cached.size}" }
            return if (cached.isEmpty()) {
                AnimeFallbackOutcome.Empty(AnimeFallbackReason.SEARCH_EMPTY)
            } else {
                AnimeFallbackOutcome.Success(cached)
            }
        }

        logcat { "[AnimeSearchFallbackEngine] Cache MISS. Running tiered search fallback for '${anime.title}'" }

        val rawAuthorParts = buildList {
            val author = anime.displayAuthor
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
            val artist = anime.displayArtist
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
            val genres = anime.displayGenre
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
            eu.kanade.domain.metadata.interactor.parseOriginalTitle(anime.description)?.let { add(it) }
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
            ?: AnimeFilterList()
        val searchSemaphore = Semaphore(MAX_CONCURRENT_QUERIES)
        val thumbnailDetailBudget = AtomicInteger(MAX_THUMBNAIL_DETAIL_LOOKUPS)
        var authorAdded = 0
        var genreAdded = 0
        val maxAuthor = 8
        val maxGenre = 8

        logcat {
            "[AnimeSearchFallbackEngine] Starting suggestions search for '${anime.title}' (url: ${anime.url}). Candidates: ${seed.candidateTitles}, author: '${anime.displayAuthor}', genres: ${anime.displayGenre}"
        }

        for ((tierName, tierQueries) in queryTiers) {
            if (oomAborted.get()) {
                logcat(LogPriority.WARN) {
                    "[AnimeSearchFallbackEngine] Skipping $tierName after the source ran out of memory. Returning collected results."
                }
                break
            }
            if (synchronized(uniqueResults) { uniqueResults.size >= boundedMaxResults }) {
                logcat {
                    "[AnimeSearchFallbackEngine] Reached target results limit ($boundedMaxResults) before processing all tiers. Stopping early."
                }
                break
            }
            if (tierQueries.isEmpty()) continue
            logcat { "[AnimeSearchFallbackEngine] Processing $tierName with queries: $tierQueries" }

            coroutineScope {
                tierQueries.forEach { query ->
                    launch {
                        if (oomAborted.get()) return@launch
                        if (synchronized(uniqueResults) { uniqueResults.size >= boundedMaxResults }) return@launch
                        try {
                            logcat { "[AnimeSearchFallbackEngine] Searching for query: '$query'" }
                            val page = searchSemaphore.withPermit {
                                source.getSearchAnime(1, query, filterList)
                            }
                            if (page.animes.isEmpty()) {
                                logcat {
                                    "[AnimeSearchFallbackEngine] Query '$query' returned 0 results from source '${source.name}'"
                                }
                                return@launch
                            } else {
                                logcat {
                                    "[AnimeSearchFallbackEngine] Query '$query' returned ${page.animes.size} raw results from source '${source.name}'"
                                }
                            }

                            val isAuthorQuery = authorParts.any { it.equals(query, ignoreCase = true) }
                            val isGenreQuery = genreParts.any { it.equals(query, ignoreCase = true) }
                            val isTitleQuery = !isAuthorQuery && !isGenreQuery

                            val scoredItems = page.animes.mapNotNull { sAnime ->
                                if (sAnime.url == anime.url) {
                                    logcat { "[AnimeSearchFallbackEngine] Excluding self reference: '${sAnime.title}'" }
                                    return@mapNotNull null
                                }

                                if (SuggestionTitleResolver.isFranchiseDuplicate(sAnime.title, anime.title)) {
                                    logcat {
                                        "[AnimeSearchFallbackEngine] Excluding franchise duplicate: '${sAnime.title}' against '${anime.title}'"
                                    }
                                    return@mapNotNull null
                                }

                                if (synchronized(uniqueResults) { uniqueResults.containsKey(sAnime.url) }) {
                                    return@mapNotNull null
                                }

                                val bestScore = candidatesToScore.maxOfOrNull { candidate ->
                                    SuggestionTitleResolver.scoreMatch(candidate, sAnime.title)
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
                                    "[AnimeSearchFallbackEngine] '${sAnime.title}' score=$finalScore " +
                                        "(bestScore=$bestScore, isTitleQuery=$isTitleQuery, isAuthorQuery=$isAuthorQuery, isGenreQuery=$isGenreQuery)"
                                }

                                if (finalScore >= 30) {
                                    val itemReason = when {
                                        isAuthorQuery -> SuggestionReason.SEARCH_AUTHOR
                                        isGenreQuery -> SuggestionReason.SEARCH_GENRE
                                        else -> SuggestionReason.SEARCH_TITLE
                                    }
                                    val item = SuggestionItem(
                                        title = sAnime.title,
                                        searchQueries = listOf(sAnime.title),
                                        thumbnailUrl = resolveThumbnail(source, sAnime, thumbnailDetailBudget),
                                        providerName = source.name,
                                        reason = itemReason,
                                        providerUrl = sAnime.url,
                                        providerId = "${source.id}:${sAnime.url}",
                                        mediaType = SuggestionMediaType.ANIME,
                                    )
                                    Pair(item, finalScore)
                                } else {
                                    logcat {
                                        "[AnimeSearchFallbackEngine] Rejecting '${sAnime.title}': score $finalScore below threshold (30)"
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
                                "[AnimeSearchFallbackEngine] Incompatible extension ABI for query '$query': ${e.message}"
                            }
                        } catch (e: OutOfMemoryError) {
                            // Extensions allocate inside getSearchAnime (in-memory catalog indexes,
                            // multi-MB JSON decoding). OOM is an Error, so the Exception catch below
                            // never sees it and the whole process dies.
                            oomAborted.set(true)
                            logcat(LogPriority.WARN) {
                                "[AnimeSearchFallbackEngine] Source '${source.name}' exhausted the heap for query " +
                                    "'$query': ${e.message}. Stopping further search tiers."
                            }
                        } catch (e: Exception) {
                            logcat { "[AnimeSearchFallbackEngine] Search failed for query '$query': ${e.message}" }
                        }
                    }
                }
            }
        }

        val items = uniqueResults.values.toList()
        if (items.isEmpty()) {
            logcat {
                "[AnimeSearchFallbackEngine] Total 0 similar items found for anime '${anime.title}'. Check source connectivity or query matching strictness."
            }
        } else {
            logcat {
                "[AnimeSearchFallbackEngine] Fallback finished, found ${items.size} matching items: ${items.map {
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
            AnimeFallbackOutcome.Empty(AnimeFallbackReason.SEARCH_EMPTY)
        } else {
            AnimeFallbackOutcome.Success(items)
        }
    }

    /**
     * Resolves a thumbnail for a search result. A full details request is issued
     * only while [detailBudget] allows it, so sources that omit thumbnails in
     * search results can no longer trigger an unbounded "one details request per
     * result" burst.
     */
    private suspend fun resolveThumbnail(
        source: AnimeCatalogueSource,
        anime: eu.kanade.tachiyomi.animesource.model.SAnime,
        detailBudget: AtomicInteger,
    ): String? {
        anime.thumbnail_url?.takeIf { it.isNotBlank() }?.let { return it }
        if (detailBudget.getAndDecrement() <= 0) return null
        return ExtensionInterop.runInterop(TAG, "getAnimeDetails(thumbnail)") {
            source.getAnimeDetails(anime.copy()).thumbnail_url?.takeIf { it.isNotBlank() }
        }
    }
}
