package eu.kanade.tachiyomi.data.suggestions.anime

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime

class AnimeSearchFallbackEngine {

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

                // 3. For long titles, truncate to first 4, 3, or 5 words to relax primitive search engines
                val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.size > 4) {
                    val first4 = words.take(4).joinToString(" ")
                    add(first4)

                    val first3 = words.take(3).joinToString(" ")
                    add(first3)

                    val first5 = words.take(5).joinToString(" ")
                    add(first5)
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
        val filterList = source.getFilterList()
        var authorAdded = 0
        var genreAdded = 0
        val maxAuthor = 8
        val maxGenre = 8

        logcat {
            "[AnimeSearchFallbackEngine] Starting suggestions search for '${anime.title}' (url: ${anime.url}). Candidates: ${seed.candidateTitles}, author: '${anime.displayAuthor}', genres: ${anime.displayGenre}"
        }

        for ((tierName, tierQueries) in queryTiers) {
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
                        if (synchronized(uniqueResults) { uniqueResults.size >= boundedMaxResults }) return@launch
                        try {
                            logcat { "[AnimeSearchFallbackEngine] Searching for query: '$query'" }
                            val page = source.getSearchAnime(1, query, filterList)
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
                                        thumbnailUrl = resolveThumbnail(source, sAnime),
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
        SuggestionCache.put(cacheKey, items)

        return if (items.isEmpty()) {
            AnimeFallbackOutcome.Empty(AnimeFallbackReason.SEARCH_EMPTY)
        } else {
            AnimeFallbackOutcome.Success(items)
        }
    }

    private suspend fun resolveThumbnail(
        source: AnimeCatalogueSource,
        anime: eu.kanade.tachiyomi.animesource.model.SAnime,
    ): String? {
        return anime.thumbnail_url?.takeIf { it.isNotBlank() }
            ?: runCatching { source.getAnimeDetails(anime.copy()).thumbnail_url?.takeIf { it.isNotBlank() } }
                .getOrNull()
    }
}
