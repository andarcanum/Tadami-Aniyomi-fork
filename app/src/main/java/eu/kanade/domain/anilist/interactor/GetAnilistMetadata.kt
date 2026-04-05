package eu.kanade.domain.anilist.interactor

import eu.kanade.domain.anime.interactor.buildMediumPosterFallback
import eu.kanade.domain.anime.interactor.chooseBestPosterUrl
import eu.kanade.domain.anime.interactor.normalizeMetadataSearchQuery
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AnimeMetadataSource
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
    suspend fun await(anime: Anime): AnilistMetadata? {
        if (preferences.animeMetadataSource().get() != AnimeMetadataSource.ANILIST) {
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

    private suspend fun getFromTracking(anime: Anime): AnilistMetadata? {
        return withIOContext {
            try {
                val track = getAnimeTracks.await(anime.id)
                    .find { it.trackerId == eu.kanade.tachiyomi.data.track.TrackerManager.ANILIST }
                    ?: return@withIOContext null

                val alAnime = anilistApi.searchAnimeById(track.remoteId)
                    ?: return@withIOContext null

                AnilistMetadata(
                    animeId = anime.id,
                    anilistId = alAnime.remoteId,
                    score = alAnime.averageScore.toDouble().takeIf { it > 0 },
                    format = alAnime.format,
                    status = alAnime.publishingStatus,
                    coverUrl = chooseBestPosterUrl(
                        alAnime.largeImageUrl,
                        buildMediumPosterFallback(alAnime.largeImageUrl),
                    ),
                    coverUrlFallback = buildMediumPosterFallback(alAnime.largeImageUrl),
                    searchQuery = "tracking:${track.remoteId}",
                    updatedAt = System.currentTimeMillis(),
                    isManualMatch = true,
                )
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
                val originalTitle = parseOriginalTitle(anime.description)

                val searchQueries = buildList {
                    originalTitle?.let { add(normalizeMetadataSearchQuery(it)) }
                    add(normalizeMetadataSearchQuery(anime.title))
                }.distinct()

                var firstResult: AnimeTrackSearch? = null
                var usedQuery: String? = null

                for (query in searchQueries) {
                    val results = anilistApi.searchAnime(query)
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

                val metadata = AnilistMetadata(
                    animeId = anime.id,
                    anilistId = firstResult.remote_id,
                    score = firstResult.score.takeIf { it > 0 },
                    format = firstResult.publishing_type,
                    status = firstResult.publishing_status,
                    coverUrl = chooseBestPosterUrl(
                        firstResult.cover_url,
                        buildMediumPosterFallback(firstResult.cover_url),
                    ),
                    coverUrlFallback = buildMediumPosterFallback(firstResult.cover_url),
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
                logcat(LogPriority.ERROR) { "Failed to search Anilist: ${e.message}" }
                null
            }
        }
    }

    private fun isNotAuthenticated(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty()
            if (message.contains("Not authenticated", ignoreCase = true) &&
                message.contains("Anilist", ignoreCase = true)
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

    private suspend fun cacheNotFound(anime: Anime) {
        metadataCache.upsert(
            AnilistMetadata(
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
            ),
        )
    }
}
