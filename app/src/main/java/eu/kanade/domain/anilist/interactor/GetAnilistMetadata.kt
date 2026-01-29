package eu.kanade.domain.anilist.interactor

import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AnimeMetadataSource
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.anilist.AnilistMetadataCache
import tachiyomi.domain.anilist.model.AnilistMetadata
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks

class GetAnilistMetadata(
    private val metadataCache: AnilistMetadataCache,
    private val anilistApi: AnilistApi,
    private val getAnimeTracks: GetAnimeTracks,
    private val preferences: UiPreferences,
) {

    /**
     * Normalize anime title for Anilist search.
     * Removes common suffixes like "Season", "TV", etc.
     */
    private fun normalizeSearchQuery(title: String): String {
        var normalized = title.trim()

        // Remove common suffixes (case-insensitive)
        val suffixesToRemove = listOf(
            "\\s+Сезон\\s*\\d*",        // Russian: "Сезон", "Сезон 2", etc.
            "\\s+сезон\\s*\\d*",        // Russian lowercase
            "\\s+Season\\s*\\d*",       // English: "Season", "Season 2", etc.
            "\\s+season\\s*\\d*",       // English lowercase
            "\\s+TV\\b",                // "TV" as word boundary
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

    suspend fun await(anime: Anime): AnilistMetadata? {
        // Check if disabled via settings
        val metadataSource = preferences.animeMetadataSource().get()
        if (metadataSource != AnimeMetadataSource.ANILIST) {
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

    private suspend fun getFromTracking(anime: Anime): AnilistMetadata? {
        return withIOContext {
            try {
                // Get Anilist track for this anime
                val track = getAnimeTracks.await(anime.id)
                    .find { it.trackerId == eu.kanade.tachiyomi.data.track.TrackerManager.ANILIST }
                    ?: return@withIOContext null

                // Fetch full anime data from Anilist
                val alAnime = anilistApi.searchAnimeById(track.remoteId)

                if (alAnime != null) {
                    AnilistMetadata(
                        animeId = anime.id,
                        anilistId = alAnime.remoteId,
                        score = alAnime.averageScore.toDouble().takeIf { it > 0 },
                        format = alAnime.format,
                        status = alAnime.publishingStatus,
                        coverUrl = alAnime.largeImageUrl,
                        coverUrlFallback = alAnime.imageUrl, // medium as fallback
                        searchQuery = "tracking:${track.remoteId}",
                        updatedAt = System.currentTimeMillis(),
                        isManualMatch = true,
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                if (isNotAuthenticated(e)) {
                    throw e
                }
                logcat(LogPriority.ERROR) { "Failed to get Anilist data from tracking: ${e.message}" }
                null
            }
        }
    }

    private suspend fun searchAndCache(anime: Anime): AnilistMetadata? {
        return withIOContext {
            try {
                // Log all anime fields for debugging
                logcat(LogPriority.DEBUG) {
                    """
                    Anime fields:
                    - title: ${anime.title}
                    - description: ${anime.description}
                    - genre: ${anime.genre}
                    - author: ${anime.author}
                    - artist: ${anime.artist}
                    - url: ${anime.url}
                    - source: ${anime.source}
                    """.trimIndent()
                }

                // Try to get original title from description for better search results
                val originalTitle = parseOriginalTitle(anime.description)

                logcat(LogPriority.DEBUG) {
                    "Parse original title: description=${anime.description?.take(100)}, originalTitle=$originalTitle"
                }

                // Prepare search queries - try original title first, then main title
                val searchQueries = buildList {
                    originalTitle?.let { add(normalizeSearchQuery(it)) }
                    add(normalizeSearchQuery(anime.title))
                }.distinct()

                logcat(LogPriority.DEBUG) {
                    "Searching Anilist for: ${anime.title}" +
                    (if (originalTitle != null) " (original: $originalTitle)" else "")
                }

                logcat(LogPriority.DEBUG) { "Search queries: $searchQueries" }

                var firstResult: AnimeTrackSearch? = null
                var usedQuery: String? = null

                // Try each search query in order
                for (query in searchQueries) {
                    logcat(LogPriority.DEBUG) { "Trying Anilist search query: $query" }
                    val results = anilistApi.searchAnime(query)
                    logcat(LogPriority.DEBUG) { "Anilist search returned ${results.size} results" }

                    if (results.isNotEmpty()) {
                        firstResult = results.first()
                        usedQuery = query
                        break
                    }
                }

                firstResult ?: run {
                    logcat(LogPriority.WARN) { "No Anilist results for: ${anime.title}" }
                    return@withIOContext null
                }

                logcat(LogPriority.INFO) {
                    "Anilist match: id=${firstResult!!.remote_id}, score=${firstResult.score}, " +
                    "type=${firstResult.publishing_type}, cover=${firstResult.cover_url}, query=$usedQuery"
                }

                val metadata = AnilistMetadata(
                    animeId = anime.id,
                    anilistId = firstResult.remote_id,
                    score = firstResult.score.takeIf { it > 0 },
                    format = firstResult.publishing_type,
                    status = firstResult.publishing_status,
                    coverUrl = firstResult.cover_url, // large
                    coverUrlFallback = firstResult.cover_url.replace("/large/", "/medium/"), // medium fallback
                    searchQuery = usedQuery ?: anime.title,
                    updatedAt = System.currentTimeMillis(),
                    isManualMatch = false,
                )

                metadataCache.upsert(metadata)
                logcat(LogPriority.INFO) { "Anilist metadata cached for anime ${anime.id}" }
                metadata
            } catch (e: Exception) {
                if (isNotAuthenticated(e)) {
                    throw e
                }
                logcat(LogPriority.ERROR) { "Failed to search Anilist: ${e.message}" }
                null
            }
        }
    }

    /**
     * Check if the error is a "Not authenticated" error.
     */
    private fun isNotAuthenticated(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty()
            if (message.contains("Not authenticated", ignoreCase = true) &&
                message.contains("Anilist", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }

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
                line.contains("Японское", ignoreCase = true)) {
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
            val nonCyrillic = line.filter {
                char -> char.code < 0x400 || char.code > 0x4FF
            }.trim()
            if (nonCyrillic.length > 5 && nonCyrillic.length < 200) {
                return nonCyrillic.trimEnd('.', ',', '"', '\'')
            }
        }

        return null
    }

    private suspend fun cacheNotFound(anime: Anime) {
        val notFound = AnilistMetadata(
            animeId = anime.id,
            anilistId = null,
            score = null,
            format = null,
            status = null,
            coverUrl = null,
            coverUrlFallback = null,
            searchQuery = anime.title,
            updatedAt = System.currentTimeMillis(),
            isManualMatch = false,
        )
        metadataCache.upsert(notFound)
    }
}
