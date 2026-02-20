package eu.kanade.domain.shikimori.interactor

import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AnimeMetadataSource
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.shikimori.ShikimoriMetadataCache
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.shikimori.model.ShikimoriMetadata
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks

class GetShikimoriMetadata(
    private val metadataCache: ShikimoriMetadataCache,
    private val shikimori: Shikimori,
    private val shikimoriApi: ShikimoriApi,
    private val getAnimeTracks: GetAnimeTracks,
    private val preferences: UiPreferences,
) {

    companion object {
        /**
         * Parse the original title from the description field.
         * Looks for patterns like "Original: Title", "Оригинал: Title", etc.
         * Also tries to find lines with Japanese/Latin characters.
         */
        private fun parseOriginalTitle(description: String?): String? {
            if (description.isNullOrBlank()) return null

            // First try explicit patterns with labels
            val patterns = listOf(
                // "Original: Title" or "Оригинал: Название"
                Regex("""Original:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
                Regex("""Оригинал:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
                // "Original Title:" or "Оригинальное название:"
                Regex("""Original Title:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
                Regex("""Оригинальное название:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
                // "Original:" followed by Japanese/English text in parentheses
                Regex("""Original:\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE),
                Regex("""Оригинал:\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE),
            )

            for (pattern in patterns) {
                val match = pattern.find(description)
                if (match != null) {
                    val title = match.groupValues[1].trim()
                    // Remove trailing quotes, periods, etc.
                    val cleaned = title.trimEnd('.', ',', '"', '\'')
                    if (cleaned.isNotEmpty()) {
                        return cleaned
                    }
                }
            }

            // Fallback: look for lines with Japanese/Latin characters (non-Cyrillic)
            description.lines().forEach { line ->
                if (line.contains("Original", ignoreCase = true) ||
                    line.contains("Оригинал", ignoreCase = true) ||
                    line.contains("Romaji", ignoreCase = true) ||
                    line.contains("Японское", ignoreCase = true)
                ) {
                    // Extract title after the colon
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val afterColon = line.substring(colonIndex + 1).trim()
                        if (afterColon.isNotEmpty()) {
                            return afterColon.trimEnd('.', ',', '"', '\'')
                        }
                    }
                }
                // Check if line contains mostly non-Cyrillic characters (Japanese/Latin)
                val nonCyrillic = line.filter { char ->
                    char.code < 0x400 || char.code > 0x4FF
                }.trim()
                if (nonCyrillic.length > 5 && nonCyrillic.length < 200) {
                    return nonCyrillic.trimEnd('.', ',', '"', '\'')
                }
            }

            return null
        }

        /**
         * Normalize anime title for Shikimori search.
         * Removes common suffixes like "Season", "Сезон", "TV", etc.
         *
         * Examples:
         * - "Медалистка 2 сезон" -> "Медалистка 2"
         * - "One Piece Season 20" -> "One Piece 20"
         * - "Anime TV" -> "Anime"
         */
        private fun normalizeSearchQuery(title: String): String {
            var normalized = title.trim()

            // Remove common suffixes (case-insensitive)
            val suffixesToRemove = listOf(
                "\\s+Сезон\\s*\\d*", // Russian: "Сезон", "Сезон 2", etc.
                "\\s+сезон\\s*\\d*", // Russian lowercase
                "\\s+Season\\s*\\d*", // English: "Season", "Season 2", etc.
                "\\s+season\\s*\\d*", // English lowercase
                "\\s+TV\\b", // "TV" as word boundary
                "\\s+tv\\b",
                "\\s+Special\\b",
                "\\s+special\\b",
                "\\s+OVA\\b",
                "\\s+ova\\b",
                "\\s+ONA\\b",
                "\\s+ona\\b",
                "\\s+Movie\\b",
                "\\s+movie\\b",
            )

            suffixesToRemove.forEach { suffix ->
                normalized = normalized.replace(Regex(suffix, RegexOption.IGNORE_CASE), "")
            }

            // Clean up extra whitespace
            return normalized.trim().replace(Regex("\\s+"), " ")
        }
    }

    suspend fun await(anime: Anime): ShikimoriMetadata? {
        // Check if disabled via settings
        if (preferences.animeMetadataSource().get() != AnimeMetadataSource.SHIKIMORI) {
            return null
        }

        // Check cache first
        val cached = metadataCache.get(anime.id)
        if (cached != null && !cached.isStale()) {
            return cached
        }

        // Try to get from tracking
        val fromTracking = getFromTracking(anime)
        if (fromTracking != null) {
            metadataCache.upsert(fromTracking)
            return fromTracking
        }

        // Auto-search by title
        val fromSearch = searchAndCache(anime)
        if (fromSearch != null) {
            return fromSearch
        }

        // Cache "not found" to avoid re-searching
        cacheNotFound(anime)
        return null
    }

    private suspend fun getFromTracking(anime: Anime): ShikimoriMetadata? {
        return withIOContext {
            try {
                // Get Shikimori track for this anime
                val track = getAnimeTracks.await(anime.id)
                    .find { it.trackerId == shikimori.id }
                    ?: return@withIOContext null

                // Fetch full anime data from Shikimori
                val entry = shikimoriApi.getAnimeById(track.remoteId)

                // Try to parse poster from HTML first (more reliable than API)
                val apiCoverUrl = ShikimoriApi.BASE_URL + entry.image.preview
                val coverUrl = if (apiCoverUrl.contains("missing_")) {
                    logcat(LogPriority.DEBUG) { "API returned placeholder for tracking, parsing HTML..." }
                    shikimoriApi.parsePosterFromHtml(entry.id) ?: apiCoverUrl
                } else {
                    // Still try HTML first for better quality
                    logcat(LogPriority.DEBUG) { "Parsing HTML for tracking poster..." }
                    shikimoriApi.parsePosterFromHtml(entry.id) ?: apiCoverUrl
                }

                ShikimoriMetadata(
                    animeId = anime.id,
                    shikimoriId = entry.id,
                    score = entry.score,
                    kind = entry.kind,
                    status = entry.status,
                    coverUrl = coverUrl,
                    searchQuery = "tracking:${track.remoteId}",
                    updatedAt = System.currentTimeMillis(),
                    isManualMatch = true,
                )
            } catch (e: Exception) {
                if (isNotAuthenticated(e)) {
                    throw e
                }
                logcat(LogPriority.ERROR) { "Failed to get Shikimori data from tracking: ${e.message}" }
                null
            }
        }
    }

    private suspend fun searchAndCache(anime: Anime): ShikimoriMetadata? {
        return withIOContext {
            try {
                // Try to get original title from description for better search results
                val originalTitle = parseOriginalTitle(anime.description)

                // Prepare search queries - try original title first, then main title
                val searchQueries = buildList {
                    originalTitle?.let { add(normalizeSearchQuery(it)) }
                    add(normalizeSearchQuery(anime.title))
                }.distinct()

                logcat(LogPriority.DEBUG) {
                    "Searching Shikimori for: ${anime.title}" +
                        (if (originalTitle != null) " (original: $originalTitle)" else "")
                }

                var firstResult: AnimeTrackSearch? = null
                var usedQuery: String? = null

                // Try each search query in order
                for (query in searchQueries) {
                    logcat(LogPriority.DEBUG) { "Trying search query: $query" }
                    val results = shikimoriApi.searchAnime(query)
                    logcat(LogPriority.DEBUG) { "Shikimori search returned ${results.size} results" }

                    if (results.isNotEmpty()) {
                        firstResult = results.first()
                        usedQuery = query
                        break
                    }
                }

                firstResult ?: run {
                    logcat(LogPriority.WARN) { "No Shikimori results for: ${anime.title}" }
                    return@withIOContext null
                }

                logcat(LogPriority.DEBUG) {
                    "Shikimori match: id=${firstResult.remote_id}, score=${firstResult.score}, " +
                        "type=${firstResult.publishing_type}, cover=${firstResult.cover_url}, query=$usedQuery"
                }

                // Try to parse poster from HTML first (more reliable than API)
                logcat(LogPriority.DEBUG) { "Parsing poster from HTML for id=${firstResult.remote_id}..." }
                val htmlPoster = shikimoriApi.parsePosterFromHtml(firstResult.remote_id)

                val coverUrl = when {
                    // HTML parsing succeeded - use it
                    htmlPoster != null -> {
                        logcat(LogPriority.INFO) { "Using poster from HTML: $htmlPoster" }
                        htmlPoster
                    }
                    // HTML failed, try API cover (if not placeholder)
                    !firstResult.cover_url.contains("missing_") -> {
                        logcat(LogPriority.DEBUG) { "Using API cover: ${firstResult.cover_url}" }
                        firstResult.cover_url
                    }
                    // Both failed - will fallback to anime.thumbnailUrl
                    else -> {
                        logcat(LogPriority.WARN) {
                            "HTML parsing failed and API returned placeholder, using local thumbnail"
                        }
                        null
                    }
                }

                val metadata = ShikimoriMetadata(
                    animeId = anime.id,
                    shikimoriId = firstResult.remote_id,
                    score = firstResult.score,
                    kind = firstResult.publishing_type,
                    status = firstResult.publishing_status,
                    coverUrl = coverUrl,
                    searchQuery = usedQuery ?: anime.title,
                    updatedAt = System.currentTimeMillis(),
                    isManualMatch = false,
                )

                metadataCache.upsert(metadata)
                logcat(LogPriority.INFO) { "Shikimori metadata cached for anime ${anime.id}" }
                metadata
            } catch (e: Exception) {
                if (isNotAuthenticated(e)) {
                    throw e
                }
                logcat(LogPriority.ERROR) { "Failed to search Shikimori: ${e.message}" }
                null
            }
        }
    }

    private suspend fun cacheNotFound(anime: Anime) {
        val notFound = ShikimoriMetadata(
            animeId = anime.id,
            shikimoriId = null,
            score = null,
            kind = null,
            status = null,
            coverUrl = null,
            searchQuery = anime.title,
            updatedAt = System.currentTimeMillis(),
            isManualMatch = false,
        )
        metadataCache.upsert(notFound)
    }

    private fun isNotAuthenticated(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty()
            if (message.contains("Not authenticated", ignoreCase = true) &&
                message.contains("Shikimori", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
