package eu.kanade.domain.shikimori.interactor

import eu.kanade.domain.anime.interactor.chooseBestPosterUrl
import eu.kanade.domain.anime.interactor.normalizeMetadataSearchQuery
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
    suspend fun await(anime: Anime): ShikimoriMetadata? {
        if (preferences.animeMetadataSource().get() != AnimeMetadataSource.SHIKIMORI) {
            return null
        }

        val cached = metadataCache.get(anime.id)
        if (cached != null && !cached.isStale()) {
            return cached
        }

        val fromTracking = getFromTracking(anime)
        if (fromTracking != null) {
            metadataCache.upsert(fromTracking)
            return fromTracking
        }

        val fromSearch = searchAndCache(anime)
        if (fromSearch != null) {
            return fromSearch
        }

        cacheNotFound(anime)
        return null
    }

    private suspend fun getFromTracking(anime: Anime): ShikimoriMetadata? {
        return withIOContext {
            try {
                val track = getAnimeTracks.await(anime.id)
                    .find { it.trackerId == shikimori.id }
                    ?: return@withIOContext null

                val entry = shikimoriApi.getAnimeById(track.remoteId)
                val apiCoverUrl = ShikimoriApi.BASE_URL + entry.image.preview
                val htmlPoster = shikimoriApi.parsePosterFromHtml(entry.id)

                ShikimoriMetadata(
                    animeId = anime.id,
                    shikimoriId = entry.id,
                    score = entry.score,
                    kind = entry.kind,
                    status = entry.status,
                    coverUrl = chooseBestPosterUrl(htmlPoster, apiCoverUrl),
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
                val originalTitle = parseOriginalTitle(anime.description)
                val searchQueries = buildList {
                    originalTitle?.let { add(normalizeMetadataSearchQuery(it)) }
                    add(normalizeMetadataSearchQuery(anime.title))
                }.distinct()

                var firstResult: AnimeTrackSearch? = null
                var usedQuery: String? = null

                for (query in searchQueries) {
                    val results = shikimoriApi.searchAnime(query)
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

                val htmlPoster = shikimoriApi.parsePosterFromHtml(firstResult.remote_id)
                val coverUrl = chooseBestPosterUrl(htmlPoster, firstResult.cover_url)

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
        metadataCache.upsert(
            ShikimoriMetadata(
                animeId = anime.id,
                shikimoriId = null,
                score = null,
                kind = null,
                status = null,
                coverUrl = null,
                searchQuery = anime.title,
                updatedAt = System.currentTimeMillis(),
                isManualMatch = false,
            ),
        )
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

    private fun parseOriginalTitle(description: String?): String? {
        if (description.isNullOrBlank()) return null

        val patterns = listOf(
            Regex("""Original:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
            Regex("""Оригинал:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
            Regex("""Original Title:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
            Regex("""Оригинальное название:\s*([^\n\r]+)""", RegexOption.IGNORE_CASE),
            Regex("""Original:\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE),
            Regex("""Оригинал:\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE),
        )

        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                val title = match.groupValues[1].trim()
                val cleaned = title.trimEnd('.', ',', '"', '\'')
                if (cleaned.isNotEmpty()) {
                    return cleaned
                }
            }
        }

        description.lines().forEach { line ->
            if (line.contains("Original", ignoreCase = true) ||
                line.contains("Оригинал", ignoreCase = true) ||
                line.contains("Romaji", ignoreCase = true) ||
                line.contains("Японское", ignoreCase = true)
            ) {
                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val afterColon = line.substring(colonIndex + 1).trim()
                    if (afterColon.isNotEmpty()) {
                        return afterColon.trimEnd('.', ',', '"', '\'')
                    }
                }
            }
            val nonCyrillic = line.filter { char -> char.code < 0x400 || char.code > 0x4FF }.trim()
            if (nonCyrillic.length > 5 && nonCyrillic.length < 200) {
                return nonCyrillic.trimEnd('.', ',', '"', '\'')
            }
        }

        return null
    }
}
