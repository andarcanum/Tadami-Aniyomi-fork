package eu.kanade.tachiyomi.data.suggestions.novel

import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionSourceWeight
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import eu.kanade.tachiyomi.data.suggestions.util.dedupeByCleanTitle
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel

class NovelSearchFallbackEngine {

    private data class NovelFallbackPolicy(
        val genreFillEnabled: Boolean = true,
        val genrePickCount: Int = 4,
        val popularBackfillCap: Int = 8,
    )

    private val fallbackPolicy = NovelFallbackPolicy()

    suspend fun fetchSearchFallback(
        novel: Novel,
        source: NovelCatalogueSource,
        seed: SuggestionSeed,
        maxResults: Int = 40,
        onProgress: ((List<SuggestionItem>) -> Unit)? = null,
    ): NovelFallbackOutcome {
        val boundedMaxResults = maxResults.coerceIn(1, 100)
        if (maxResults <= 0) {
            return NovelFallbackOutcome.Empty(NovelFallbackReason.SEARCH_EMPTY)
        }

        val cacheKey = SuggestionCache.makeKey(
            "search:${source.id}:limit:$boundedMaxResults",
            novel.url,
            "NOVEL",
            seed.candidateTitles,
        )
        val cached = SuggestionCache.get(cacheKey)
        if (cached != null) {
            logcat { "[NovelSearchFallbackEngine] Cache HIT for key $cacheKey, count=${cached.size}" }
            return if (cached.isEmpty()) {
                NovelFallbackOutcome.Empty(NovelFallbackReason.SEARCH_EMPTY)
            } else {
                NovelFallbackOutcome.Success(cached)
            }
        }

        logcat { "[NovelSearchFallbackEngine] Cache MISS. Running tiered search fallback for '${novel.title}'" }

        val rawAuthorParts = buildList {
            val author = novel.displayAuthor
            if (!author.isNullOrBlank()) {
                val garbage = setOf(
                    "null", "undefined", "unknown", "none", "no author", "n/a",
                    "нет", "неизвестен", "неизвестный", "неизвестно",
                )
                addAll(
                    author.split(Regex("[,;/&]"))
                        .map { it.trim() }
                        .filter { it.length >= 2 && it.lowercase() !in garbage },
                )
            }
        }.distinct()

        val authorParts = rawAuthorParts

        val freshFilterList = try {
            source.getFilterList()
        } catch (e: Exception) {
            NovelFilterList()
        }
        val rawGenreParts = novel.displayGenre
            .orEmpty()
            .mapNotNull(::normalizeGenreToken)
            .distinct()
            .sortedBy { if (it in broadGenres) 1 else 0 }
            .take(fallbackPolicy.genrePickCount)

        val genreParts = buildList {
            val genreGroups = rawGenreParts.map { rawGenre ->
                val allVariants = (
                    listOf(rawGenre) +
                        eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.getGenreTranslations(rawGenre)
                    ).distinct()
                val (supported, unsupported) = allVariants.partition { isGenreSupported(freshFilterList, it) }
                if (supported.isNotEmpty()) {
                    supported to unsupported
                } else {
                    listOf(rawGenre) to allVariants.filter { it != rawGenre }
                }
            }

            // Phase 1: Add the primary supported/fallback term for each raw genre first, to guarantee cross-genre diversity!
            genreGroups.forEach { (primary, _) ->
                primary.firstOrNull()?.let { add(it) }
            }

            // Phase 2: Add other translations/synonyms for each raw genre as secondary fallbacks
            genreGroups.forEach { (primary, secondary) ->
                primary.drop(1).forEach { add(it) }
                secondary.forEach { add(it) }
            }
        }.distinct()

        val mainTitle = seed.primaryTitle
        val titlesToProcess = listOf(mainTitle)
        val isCyrillicEntry = eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.containsCyrillic(mainTitle)

        // Tier 1: Exact titles
        val tier1Queries = buildList {
            addAll(titlesToProcess)
            val desc = novel.displayDescription
            eu.kanade.domain.metadata.interactor.parseOriginalTitle(desc)?.let { add(it) }
            addAll(seed.candidateTitles)
        }.map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        // Tier 2: Relaxed title queries (e.g. remove volume/season suffixes, split by punctuation, or truncate long titles)
        // F2.5: Tightened the contract — only the first 3 words of the cleaned
        // title, queries shorter than 6 chars are dropped, and we dedup
        // aggressively to keep the per-novel request count bounded.
        val tier2Queries = buildList {
            titlesToProcess.forEach { title ->
                // 1. Split by common separators: :, -, (, [, comma, semicolon
                val separators = listOf(":", "-", "(", "[", ",", ";")
                separators.forEach { sep ->
                    val part = title.substringBefore(sep).trim()
                    if (part.isNotEmpty() && part != title && part.length >= 6) {
                        add(part)
                    }
                }

                // 2. Cleaned title (removes volumes, chapters, seasons)
                val cleaned = eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver.cleanTitle(title)
                if (cleaned.isNotEmpty() && cleaned != title && cleaned.length >= 6) {
                    add(cleaned)
                }

                // 3. For any title with 3 or more words, generate the first-3-words
                //    prefix only. Earlier code emitted 2/3/4 word variants which
                //    multiplied requests without improving precision noticeably.
                val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.size >= 3) {
                    val first3 = words.take(3).joinToString(" ")
                    if (first3.length >= 6) add(first3)
                }
            }
        }.map { it.trim() }
            .filter { it.length >= 6 }
            .distinct()

        // Tier 3: Author queries
        val tier3Queries = authorParts.map { it.trim() }.filter { it.length >= 2 }.distinct()

        val queryTiers = listOf(
            Pair("Tier 1 (Exact Title)", tier1Queries),
            Pair("Tier 2 (Relaxed Title)", tier2Queries),
            Pair("Tier 3 (Author)", tier3Queries),
        )

        val candidatesToScore = seed.candidateTitles.distinct()

        val uniqueResults = LinkedHashMap<String, SuggestionItem>() // key: providerUrl
        val filterList = source.getFilterList()
        var authorAdded = 0
        var genreAdded = 0
        val maxAuthor = 8
        val maxGenre = 8

        logcat {
            "[NovelSearchFallbackEngine] Starting suggestions search for '${novel.title}' (url: ${novel.url}). Candidates: ${seed.candidateTitles}, author: '${novel.displayAuthor}', genres: ${novel.displayGenre}"
        }

        for ((tierName, tierQueries) in queryTiers) {
            if (synchronized(uniqueResults) { uniqueResults.size >= boundedMaxResults }) {
                logcat {
                    "[NovelSearchFallbackEngine] Reached target results limit ($boundedMaxResults) before processing all tiers. Stopping early."
                }
                break
            }
            if (tierQueries.isEmpty()) continue
            logcat { "[NovelSearchFallbackEngine] Processing $tierName with queries: $tierQueries" }

            coroutineScope {
                tierQueries.forEach { query ->
                    launch {
                        if (synchronized(uniqueResults) { uniqueResults.size >= boundedMaxResults }) return@launch
                        try {
                            logcat { "[NovelSearchFallbackEngine] Searching for query: '$query'" }
                            val page = source.getSearchNovels(1, query, filterList)
                            if (page.novels.isEmpty()) {
                                logcat {
                                    "[NovelSearchFallbackEngine] Query '$query' returned 0 results from source '${source.name}'"
                                }
                                return@launch
                            } else {
                                logcat {
                                    "[NovelSearchFallbackEngine] Query '$query' returned ${page.novels.size} raw results from source '${source.name}'"
                                }
                            }

                            val isAuthorQuery = authorParts.any { it.equals(query, ignoreCase = true) }
                            val isGenreQuery = genreParts.any { it.equals(query, ignoreCase = true) }
                            val isTitleQuery = !isAuthorQuery && !isGenreQuery

                            val scoredItems = page.novels.mapNotNull { sNovel ->
                                if (sNovel.url == novel.url) {
                                    logcat { "[NovelSearchFallbackEngine] Excluding self reference: '${sNovel.title}'" }
                                    return@mapNotNull null
                                }

                                if (SuggestionTitleResolver.isFranchiseDuplicate(sNovel.title, novel.title)) {
                                    logcat {
                                        "[NovelSearchFallbackEngine] Excluding franchise duplicate: '${sNovel.title}' against '${novel.title}'"
                                    }
                                    return@mapNotNull null
                                }

                                if (synchronized(uniqueResults) { uniqueResults.containsKey(sNovel.url) }) {
                                    return@mapNotNull null
                                }

                                val bestScore = candidatesToScore.maxOfOrNull { candidate ->
                                    SuggestionTitleResolver.scoreMatch(candidate, sNovel.title)
                                } ?: 0

                                val finalScore = when {
                                    bestScore >= 20 -> bestScore
                                    isTitleQuery -> 0
                                    isAuthorQuery -> {
                                        val overlapBonus = minOf(bestScore / 10, 10)
                                        40 + overlapBonus
                                    }
                                    isGenreQuery -> 20
                                    else -> 0
                                }

                                logcat {
                                    "[NovelSearchFallbackEngine] '${sNovel.title}' score=$finalScore " +
                                        "(bestScore=$bestScore, isTitleQuery=$isTitleQuery, isAuthorQuery=$isAuthorQuery, isGenreQuery=$isGenreQuery)"
                                }

                                if (finalScore >= 20) {
                                    val itemReason = when {
                                        isAuthorQuery -> SuggestionReason.SEARCH_AUTHOR
                                        isGenreQuery -> SuggestionReason.SEARCH_GENRE
                                        else -> SuggestionReason.SEARCH_TITLE
                                    }
                                    val item = SuggestionItem(
                                        title = sNovel.title,
                                        searchQueries = listOf(sNovel.title),
                                        thumbnailUrl = resolveThumbnail(source, sNovel),
                                        providerName = source.name,
                                        providerUrl = sNovel.url,
                                        providerId = "${source.id}:${sNovel.url}",
                                        mediaType = SuggestionMediaType.NOVEL,
                                        reason = itemReason,
                                    )
                                    Pair(item, finalScore)
                                } else {
                                    logcat {
                                        "[NovelSearchFallbackEngine] Rejecting '${sNovel.title}': score $finalScore below threshold (20)"
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
                            logcat { "[NovelSearchFallbackEngine] Search failed for query '$query': ${e.message}" }
                        }
                    }
                }
            }
        }

        if (fallbackPolicy.genreFillEnabled &&
            synchronized(uniqueResults) { uniqueResults.size < boundedMaxResults } &&
            genreParts.isNotEmpty()
        ) {
            backfillFromGenreQueries(
                source = source,
                novel = novel,
                selectedGenres = genreParts,
                maxResults = boundedMaxResults,
                uniqueResults = uniqueResults,
                onProgress = onProgress,
            )
        }

        if (synchronized(uniqueResults) { uniqueResults.size < boundedMaxResults }) {
            backfillFromPopularNovels(
                source = source,
                novel = novel,
                maxResults = boundedMaxResults,
                uniqueResults = uniqueResults,
                popularBackfillCap = fallbackPolicy.popularBackfillCap,
                onProgress = onProgress,
            )
        }

        // F2.2 — final pass: dedupe by cleaned title across the entire
        // search-fallback output (some plugins return the same novel under
        // a slightly different title or URL).
        val items = uniqueResults.values.toList()
            .dedupeByCleanTitle(seed)
            .sortedByDescending { SuggestionSourceWeight.finalScore(it.reason, it.bestMatchScoreFor(seed)) }
        if (items.isEmpty()) {
            logcat {
                "[NovelSearchFallbackEngine] Total 0 similar items found for novel '${novel.title}'. Check source connectivity or query matching strictness."
            }
        } else {
            logcat {
                "[NovelSearchFallbackEngine] Fallback finished, found ${items.size} matching items: ${items.map {
                    it.title
                }}"
            }
        }
        SuggestionCache.put(cacheKey, items)

        return if (items.isEmpty()) {
            NovelFallbackOutcome.Empty(NovelFallbackReason.SEARCH_EMPTY)
        } else {
            NovelFallbackOutcome.Success(items)
        }
    }

    private suspend fun backfillFromGenreQueries(
        source: NovelCatalogueSource,
        novel: Novel,
        selectedGenres: List<String>,
        maxResults: Int,
        uniqueResults: LinkedHashMap<String, SuggestionItem>,
        onProgress: ((List<SuggestionItem>) -> Unit)?,
    ) {
        val targetGenres = selectedGenres.take(4)
        if (targetGenres.isEmpty()) return

        val genreMatchesByUrl = LinkedHashMap<String, MutableSet<String>>()
        val candidateMetadata = LinkedHashMap<String, SuggestionItem>()

        targetGenres.forEach { genre ->
            val filterList = source.getFilterList()
            applyGenreFilter(filterList, listOf(genre))

            var page = try {
                source.getPopularNovels(1, filterList)
            } catch (e: Exception) {
                logcat {
                    "[NovelSearchFallbackEngine] Genre filter search via getPopularNovels failed for '$genre': ${e.message}"
                }
                null
            }

            if (page == null || page.novels.isEmpty()) {
                logcat {
                    "[NovelSearchFallbackEngine] Genre filter search via getPopularNovels returned 0 results for '$genre'. Trying searchNovels with empty query."
                }
                page = try {
                    source.getSearchNovels(1, "", filterList)
                } catch (e: Exception) {
                    logcat {
                        "[NovelSearchFallbackEngine] Genre filter search with empty query failed for '$genre': ${e.message}"
                    }
                    null
                }
            }

            if (page == null || page.novels.isEmpty()) {
                logcat {
                    "[NovelSearchFallbackEngine] Genre filter search with empty query returned 0 results. Trying keyword search."
                }
                page = try {
                    source.getSearchNovels(1, genre, source.getFilterList())
                } catch (e: Exception) {
                    logcat { "[NovelSearchFallbackEngine] Genre keyword search failed for '$genre': ${e.message}" }
                    null
                }
            }

            if (page == null || page.novels.isEmpty()) return@forEach

            page.novels.forEach { sNovel ->
                if (sNovel.url == novel.url) return@forEach
                if (SuggestionTitleResolver.isFranchiseDuplicate(sNovel.title, novel.title)) return@forEach

                genreMatchesByUrl.getOrPut(sNovel.url) { linkedSetOf() }.add(genre)
                if (!uniqueResults.containsKey(sNovel.url)) {
                    candidateMetadata[sNovel.url] = SuggestionItem(
                        title = sNovel.title,
                        searchQueries = listOf(sNovel.title),
                        thumbnailUrl = resolveThumbnail(source, sNovel),
                        providerName = source.name,
                        providerUrl = sNovel.url,
                        providerId = "${source.id}:${sNovel.url}",
                        mediaType = SuggestionMediaType.NOVEL,
                        reason = SuggestionReason.SEARCH_GENRE,
                    )
                }
            }
        }

        val rankedGenreItems = candidateMetadata.values
            .sortedWith(
                compareByDescending<SuggestionItem> { genreMatchesByUrl[it.providerUrl]?.size ?: 0 }
                    .thenBy { it.title.lowercase() },
            )

        var addedAny = false
        rankedGenreItems.forEach { item ->
            if (uniqueResults.size < maxResults && !uniqueResults.containsKey(item.providerUrl)) {
                uniqueResults[item.providerUrl] = item
                addedAny = true
            }
        }

        if (addedAny) {
            onProgress?.invoke(uniqueResults.values.toList())
        }
    }

    private suspend fun backfillFromPopularNovels(
        source: NovelCatalogueSource,
        novel: Novel,
        maxResults: Int,
        uniqueResults: LinkedHashMap<String, SuggestionItem>,
        popularBackfillCap: Int,
        onProgress: ((List<SuggestionItem>) -> Unit)?,
    ) {
        val currentCount = synchronized(uniqueResults) { uniqueResults.size }
        val remaining = maxResults - currentCount
        if (remaining <= 0) return
        val cappedRemaining = minOf(remaining, popularBackfillCap.coerceAtLeast(0))
        if (cappedRemaining <= 0) return

        val targetCount = currentCount + cappedRemaining
        var page = 1
        var hasNextPage = true
        while (hasNextPage && synchronized(uniqueResults) { uniqueResults.size < targetCount }) {
            val novelsPage = try {
                source.getPopularNovels(page)
            } catch (e: Exception) {
                logcat { "[NovelSearchFallbackEngine] Popular backfill failed on page $page: ${e.message}" }
                return
            }
            if (novelsPage.novels.isEmpty()) return

            var addedAny = false
            novelsPage.novels.forEach { sNovel ->
                if (uniqueResults.size >= targetCount) return@forEach
                if (sNovel.url == novel.url) return@forEach
                if (SuggestionTitleResolver.isFranchiseDuplicate(sNovel.title, novel.title)) return@forEach
                if (uniqueResults.containsKey(sNovel.url)) return@forEach

                uniqueResults[sNovel.url] = SuggestionItem(
                    title = sNovel.title,
                    searchQueries = listOf(sNovel.title),
                    thumbnailUrl = resolveThumbnail(source, sNovel),
                    providerName = source.name,
                    providerUrl = sNovel.url,
                    providerId = "${source.id}:${sNovel.url}",
                    mediaType = SuggestionMediaType.NOVEL,
                    reason = SuggestionReason.POPULAR_BACKFILL,
                )
                addedAny = true
            }
            val currentProgress = if (addedAny) uniqueResults.values.toList() else null
            if (currentProgress != null) {
                onProgress?.invoke(currentProgress)
            }

            hasNextPage = novelsPage.hasNextPage
            page++
        }
    }

    private fun normalizeGenreToken(raw: String): String? {
        val normalized = raw.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
        if (normalized.length < 2) return null
        if (normalized in genreNoiseTokens) return null
        return normalized
    }

    private fun isGenreSupported(filterList: NovelFilterList, genre: String): Boolean {
        var matched = false
        val normalizedTarget = genre.trim().lowercase()
        fun traverse(filters: List<NovelFilter<*>>) {
            for (filter in filters) {
                if (matched) return
                when (filter) {
                    is NovelFilter.Group<*> -> {
                        val groupList = filter.state
                        @Suppress("UNCHECKED_CAST")
                        traverse(groupList as List<NovelFilter<*>>)
                    }
                    is NovelFilter.CheckBox -> {
                        val nameLower = filter.name.lowercase()
                        if (nameLower == normalizedTarget || nameLower.contains(normalizedTarget)) {
                            matched = true
                        }
                    }
                    is NovelFilter.TriState -> {
                        val nameLower = filter.name.lowercase()
                        if (nameLower == normalizedTarget || nameLower.contains(normalizedTarget)) {
                            matched = true
                        }
                    }
                    else -> {}
                }
            }
        }
        traverse(filterList)
        return matched
    }

    private fun applyGenreFilter(filterList: NovelFilterList, targetGenres: List<String>) {
        val normalizedTargets = targetGenres.map { it.trim().lowercase() }
        if (normalizedTargets.isEmpty()) return

        fun traverse(filters: List<NovelFilter<*>>) {
            for (filter in filters) {
                when (filter) {
                    is NovelFilter.Group<*> -> {
                        val groupList = filter.state
                        @Suppress("UNCHECKED_CAST")
                        traverse(groupList as List<NovelFilter<*>>)
                    }
                    is NovelFilter.CheckBox -> {
                        val nameLower = filter.name.lowercase()
                        if (normalizedTargets.any { target -> nameLower == target || nameLower.contains(target) }) {
                            filter.state = true
                        }
                    }
                    is NovelFilter.TriState -> {
                        val nameLower = filter.name.lowercase()
                        if (normalizedTargets.any { target -> nameLower == target || nameLower.contains(target) }) {
                            filter.state = NovelFilter.TriState.STATE_INCLUDE
                        }
                    }
                    else -> {}
                }
            }
        }

        traverse(filterList)
    }

    private suspend fun resolveThumbnail(
        source: NovelCatalogueSource,
        novel: eu.kanade.tachiyomi.novelsource.model.SNovel,
    ): String? {
        return novel.thumbnail_url?.takeIf { it.isNotBlank() }
            ?: runCatching { source.getNovelDetails(novel.copy()).thumbnail_url?.takeIf { it.isNotBlank() } }
                .getOrNull()
    }

    private companion object {
        val broadGenres = setOf(
            "фэнтези", "fantasy",
            "приключения", "adventure",
            "романтика", "romance",
            "комедия", "comedy",
            "боевик", "action",
        )

        val genreNoiseTokens = setOf(
            "none",
            "unknown",
            "other",
            "misc",
            "n/a",
            "null",
            "undefined",
            "-",
        )
    }
}
